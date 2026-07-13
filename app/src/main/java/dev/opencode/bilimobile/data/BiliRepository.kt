package dev.opencode.bilimobile.data

import android.content.Context
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.zip.GZIPInputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

class AmbiguousWriteException(message: String, cause: Throwable) : IOException(message, cause)
class ApiBusinessException(val code: Int, message: String) : IllegalStateException(message)

class BiliRepository(context: Context) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val cookieJar = PersistentCookieJar(context)
    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            val builder = request.newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Mobile Safari/537.36")
            if (request.header("Referer") == null) builder.header("Referer", "https://www.bilibili.com/")
            chain.proceed(builder.build())
        }.build()

    suspend fun popular(page: Int = 1): List<Video> = get<ApiResponse<PopularData>>(
        "https://api.bilibili.com/x/web-interface/popular?ps=30&pn=$page"
    ).requireData().list.filter { it.isPlayable }

    suspend fun channel(channel: Channel, page: Int = 1): List<Video> {
        if (channel.short) return shortVideos()
        if (channel.popular) return popular(page).filterNot(Video::isShort)
        // The anonymous recommendation endpoint frequently rejects otherwise valid requests.
        // Popular is a stable, non-empty recommendation baseline with a distinct UI label.
        if (channel.tid == null) return popular(page).filterNot(Video::isShort)
        return get<ApiResponse<RankingData>>("https://api.bilibili.com/x/web-interface/ranking/v2?rid=${channel.tid}&type=all")
            .requireData().list.filter { it.isPlayable && !it.isShort }
    }

    suspend fun shortVideos(): List<Video> = coroutineScope {
        val recommendation = async {
            runCatching {
                val params = linkedMapOf("fresh_type" to "3", "version" to "1", "ps" to "30")
                val nav = profile()
                val signed = if (nav.wbi_img.img_url.isNotBlank()) signWbi(params, nav.wbi_img) else params
                val builder = "https://api.bilibili.com/x/web-interface/wbi/index/top/feed/rcmd".toHttpUrl().newBuilder()
                signed.forEach { (key, value) -> builder.addQueryParameter(key, value) }
                get<ApiResponse<RecommendData>>(builder.build().toString()).requireData().item
            }.getOrDefault(emptyList())
        }
        val popularPages = (1..3).map { page -> async { runCatching { popular(page) }.getOrDefault(emptyList()) } }
        (recommendation.await() + popularPages.awaitAll().flatten())
            .filter { it.isPlayable && it.isShort }
            .distinctBy(Video::bvid)
    }

    suspend fun liveRooms(): List<LiveRoomSummary> {
        val root = get<JsonElement>("https://api.live.bilibili.com/room/v3/area/getRoomList?platform=web&parent_area_id=0&area_id=0&page=1&page_size=20").apiData()
        return root.jsonObject["list"]?.jsonArray.orEmpty().take(20).mapNotNull { value -> runCatching {
            val item = value.jsonObject
            LiveRoomSummary(item.long("roomid", "room_id"), item.string("title"), item.string("cover", "user_cover"),
                item.string("uname"), item.string("area_name", "parent_name"), item.long("online"))
        }.getOrNull()?.takeIf { it.roomId > 0 } }
    }

    suspend fun liveDetail(roomId: Long): LiveRoomDetail {
        val init = get<JsonElement>("https://api.live.bilibili.com/room/v1/Room/room_init?id=$roomId").apiData().jsonObject
        val realId = init.long("room_id").takeIf { it > 0 } ?: roomId
        val info = get<JsonElement>("https://api.live.bilibili.com/room/v1/Room/get_info?room_id=$realId").apiData().jsonObject
        return LiveRoomDetail(realId, info.string("title"), info.string("user_cover", "keyframe"),
            info.string("uname"), info.string("area_name", "parent_area_name"), info.long("online"))
    }

    suspend fun livePlayInfo(roomId: Long, quality: Int = 10000): LivePlayInfo {
        val url = "https://api.live.bilibili.com/xlive/web-room/v2/index/getRoomPlayInfo".toHttpUrl().newBuilder()
            .addQueryParameter("room_id", roomId.toString()).addQueryParameter("protocol", "1")
            .addQueryParameter("format", "1,2").addQueryParameter("codec", "0")
            .addQueryParameter("qn", quality.toString()).addQueryParameter("platform", "web")
            .addQueryParameter("ptype", "8").build().toString()
        val data = get<JsonElement>(url).apiData().jsonObject
        val playurl = data["playurl_info"]?.jsonObject?.get("playurl")?.jsonObject
        val descriptions = playurl?.get("g_qn_desc")?.jsonArray.orEmpty().associate { q ->
            val o = q.jsonObject; o.long("qn").toInt() to o.string("desc")
        }
        val streams = playurl?.get("stream")?.jsonArray.orEmpty()
        val results = mutableListOf<LiveQuality>()
        streams.forEach { stream -> stream.jsonObject["format"]?.jsonArray.orEmpty().forEach { format ->
            val formatObj = format.jsonObject
            val formatName = formatObj.string("format_name")
            val hlsFormat = formatName.equals("ts", true) || formatName.equals("fmp4", true) || formatName.contains("hls", true)
            if (!hlsFormat) return@forEach
            formatObj["codec"]?.jsonArray.orEmpty().forEach { codec ->
                val c = codec.jsonObject
                if (c.string("codec_name").contains("avc", true)) {
                    val base = c.string("base_url")
                    c["url_info"]?.jsonArray.orEmpty().forEach { address ->
                        val a = address.jsonObject; val qn = c.long("current_qn").toInt()
                        val full = a.string("host") + base + a.string("extra")
                        if (full.startsWith("http")) results += LiveQuality(qn, descriptions[qn] ?: "清晰度 $qn", full, formatName)
                    }
                }
            }
        } }
        return LivePlayInfo(results.distinctBy { it.url }.sortedWith(
            compareBy<LiveQuality> { if (it.format.equals("fmp4", true)) 0 else 1 }
                .thenByDescending { it.quality }
        ))
    }

    suspend fun liveHistory(roomId: Long): List<LiveMessage> {
        val data = get<JsonElement>("https://api.live.bilibili.com/xlive/web-room/v1/dM/gethistory?roomid=$roomId").apiData().jsonObject
        return (data["room"]?.jsonArray.orEmpty() + data["admin"]?.jsonArray.orEmpty()).takeLast(100).mapIndexedNotNull { index, value -> runCatching {
            val item = value.jsonObject; val text = item.string("text").take(100)
            val timeline = item.string("timeline")
            LiveMessage(item.string("id_str").ifBlank { "$timeline:${item.string("uid")}:$text" }, item.string("nickname"), text, System.currentTimeMillis())
        }.getOrNull()?.takeIf { it.text.isNotBlank() } }
    }

    suspend fun sendLiveMessage(roomId: Long, message: String) = post("https://api.live.bilibili.com/msg/send",
        mapOf("roomid" to roomId.toString(), "msg" to message, "rnd" to (System.currentTimeMillis() / 1000).toString(),
            "fontsize" to "25", "color" to "16777215", "mode" to "1", "bubble" to "0", "room_type" to "0"),
        origin = "https://live.bilibili.com", referer = "https://live.bilibili.com/$roomId")

    suspend fun dynamics(forceRefresh: Boolean = false): List<DynamicVideo> {
        val url = "https://api.bilibili.com/x/polymer/web-dynamic/v1/feed/all".toHttpUrl().newBuilder()
            .addQueryParameter("timezone_offset", "-480")
            .apply { if (forceRefresh) addQueryParameter("refresh", System.currentTimeMillis().toString()) }
            .build().toString()
        val root = get<JsonElement>(url)
        val response = root.jsonObject
        if (response["code"]?.jsonPrimitive?.intOrNull != 0) error(apiError(response["code"]?.jsonPrimitive?.intOrNull ?: -1))
        return parseDynamics(response["data"]?.jsonObject?.get("items")?.jsonArray.orEmpty())
    }

    suspend fun upDynamics(mid: Long, offset: String = ""): DynamicPage {
        val builder = "https://api.bilibili.com/x/polymer/web-dynamic/v1/feed/space".toHttpUrl().newBuilder()
            .addQueryParameter("host_mid", mid.toString())
            .addQueryParameter("features", "itemOpusStyle,listOnlyfans,opusBigCover,onlyfansVote")
        if (offset.isNotBlank()) builder.addQueryParameter("offset", offset)
        val data = get<JsonElement>(builder.build().toString()).apiData().jsonObject
        return DynamicPage(
            parseDynamics(data["items"]?.jsonArray.orEmpty()),
            data["offset"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            data["has_more"]?.jsonPrimitive?.booleanOrNull == true
        )
    }

    private fun parseDynamics(items: List<JsonElement>): List<DynamicVideo> = items.mapNotNull { element ->
            runCatching {
                val item = element.jsonObject
                val archive = item["modules"]?.jsonObject?.get("module_dynamic")?.jsonObject
                    ?.get("major")?.jsonObject?.get("archive")?.jsonObject ?: return@runCatching null
                val bvid = archive["bvid"]?.jsonPrimitive?.contentOrNull.orEmpty()
                if (bvid.isBlank()) return@runCatching null
                val stat = archive["stat"]?.jsonObject
                val author = item["modules"]?.jsonObject?.get("module_author")?.jsonObject
                DynamicVideo(
                    item["id_str"]?.jsonPrimitive?.contentOrNull ?: bvid,
                    Video(bvid = bvid, title = archive["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        pic = archive["cover"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        duration = archive["duration_text"]?.jsonPrimitive?.contentOrNull.orEmpty().parseDuration(),
                        author = author?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty(),
                        play = stat?.get("play")?.jsonPrimitive?.contentOrNull.parseCount()),
                    archive["desc"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    author?.get("pub_time")?.jsonPrimitive?.contentOrNull.orEmpty(),
                    author?.get("face")?.jsonPrimitive?.contentOrNull.orEmpty(),
                    author?.get("mid")?.jsonPrimitive?.longOrNull ?: 0
                )
            }.getOrNull()
        }

    suspend fun search(query: String): List<Video> {
        val params = linkedMapOf(
            "search_type" to "video", "keyword" to query, "page" to "1",
            "order" to "totalrank", "duration" to "0", "tids" to "0"
        )
        val nav = profile()
        val useWbi = nav.wbi_img.img_url.isNotBlank() && nav.wbi_img.sub_url.isNotBlank()
        val builder = (if (useWbi) {
            "https://api.bilibili.com/x/web-interface/wbi/search/type"
        } else {
            "https://api.bilibili.com/x/web-interface/search/type"
        }).toHttpUrl().newBuilder()
        (if (useWbi) signWbi(params, nav.wbi_img) else params).forEach { (key, value) ->
            builder.addQueryParameter(key, value)
        }
        return get<ApiResponse<SearchData>>(builder.build().toString()).requireData().result
            .asSequence()
            .filter { it.bvid.isNotBlank() && (it.type.isBlank() || it.type == "video") }
            .map { it.asVideo() }
            .toList()
    }

    suspend fun hotSearch(limit: Int = 10): List<HotSearchItem> {
        val data = get<JsonElement>("https://api.bilibili.com/x/web-interface/search/square?limit=${limit.coerceIn(1, 20)}&platform=web").apiData().jsonObject
        return data["trending"]?.jsonObject?.get("list")?.jsonArray.orEmpty().mapNotNull { value -> runCatching {
            val item = value.jsonObject
            val keyword = item.string("keyword")
            HotSearchItem(keyword, item.string("show_name").ifBlank { keyword }, item.long("heat_score"))
        }.getOrNull()?.takeIf { it.keyword.isNotBlank() } }
    }

    suspend fun upProfile(mid: Long): UpProfile {
        val data = get<JsonElement>("https://api.bilibili.com/x/web-interface/card?mid=$mid&photo=true").apiData().jsonObject
        val card = data["card"]?.jsonObject ?: error("UP 主资料缺少数据")
        return UpProfile(mid, card.string("name"), card.string("face"), card.string("sign"),
            card["level_info"]?.jsonObject?.long("current_level")?.toInt() ?: 0,
            data.long("follower"), data.string("following").toBoolean(), data.long("archive_count").toInt())
    }

    suspend fun upArchives(mid: Long, page: Int = 1): List<Video> {
        val params = linkedMapOf("mid" to mid.toString(), "pn" to page.toString(), "ps" to "30", "order" to "pubdate", "tid" to "0", "keyword" to "")
        val nav = profile()
        val signed = signWbi(params, nav.wbi_img)
        val builder = "https://api.bilibili.com/x/space/wbi/arc/search".toHttpUrl().newBuilder()
        signed.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        val data = get<JsonElement>(builder.build().toString()).apiData().jsonObject
        return data["list"]?.jsonObject?.get("vlist")?.jsonArray.orEmpty().mapNotNull { value -> runCatching {
            val item = value.jsonObject
            val bvid = item.string("bvid")
            Video(bvid = bvid, aid = item.long("aid"), title = item.string("title"), pic = item.string("pic"),
                desc = item.string("description"), duration = item.string("length").parseDuration(),
                owner = Owner(mid, item.string("author")), play = item.long("play"), pubdate = item.long("created"))
        }.getOrNull()?.takeIf { it.bvid.isNotBlank() } }
    }

    suspend fun details(bvid: String): Video = get<ApiResponse<Video>>(
        "https://api.bilibili.com/x/web-interface/view?bvid=$bvid"
    ).requireData()

    suspend fun related(bvid: String): List<Video> = get<ApiResponse<List<Video>>>(
        "https://api.bilibili.com/x/web-interface/archive/related?bvid=$bvid"
    ).requireData().filter { it.isPlayable }

    suspend fun comments(aid: Long, page: Int): ReplyData = get<ApiResponse<ReplyData>>(
        "https://api.bilibili.com/x/v2/reply".toHttpUrl().newBuilder()
            .addQueryParameter("type", "1").addQueryParameter("oid", aid.toString())
            .addQueryParameter("pn", page.toString()).addQueryParameter("ps", "20")
            .addQueryParameter("sort", "2").build().toString()
    ).requireData()

    suspend fun commentReplies(aid: Long, root: Long, page: Int): ReplyData = get<ApiResponse<ReplyData>>(
        "https://api.bilibili.com/x/v2/reply/reply".toHttpUrl().newBuilder()
            .addQueryParameter("type", "1").addQueryParameter("oid", aid.toString())
            .addQueryParameter("root", root.toString()).addQueryParameter("pn", page.toString())
            .addQueryParameter("ps", "20").build().toString()
    ).requireData()

    suspend fun history(): List<HistoryItem> = get<ApiResponse<HistoryData>>(
        "https://api.bilibili.com/x/web-interface/history/cursor?ps=20"
    ).requireData().list

    suspend fun watchLater(): List<Video> = get<ApiResponse<WatchLaterData>>(
        "https://api.bilibili.com/x/v2/history/toview"
    ).requireData().list.filter { it.bvid.isNotBlank() }

    suspend fun watchLaterPreview(): List<Video> = watchLater().take(12)

    suspend fun favoriteFolders(mid: Long): List<FavoriteFolder> = get<ApiResponse<FavoriteData>>(
        "https://api.bilibili.com/x/v3/fav/folder/created/list-all?up_mid=$mid"
    ).requireData().list

    suspend fun favoriteResources(mediaId: Long, page: Int): FavoriteResourceData = get<ApiResponse<FavoriteResourceData>>(
        "https://api.bilibili.com/x/v3/fav/resource/list".toHttpUrl().newBuilder()
            .addQueryParameter("media_id", mediaId.toString()).addQueryParameter("pn", page.toString())
            .addQueryParameter("ps", "20").build().toString()
    ).requireData()

    suspend fun playUrl(bvid: String, cid: Long, quality: Int = 64, forceDash: Boolean = false): PlayResult {
        fun requestUrl(fnval: String, platform: String? = null) = "https://api.bilibili.com/x/player/playurl".toHttpUrl().newBuilder()
            .addQueryParameter("bvid", bvid).addQueryParameter("cid", cid.toString())
            .addQueryParameter("qn", quality.toString()).addQueryParameter("fnval", fnval)
            .apply { platform?.let { addQueryParameter("platform", it) } }
            .build().toString()
        fun PlayData.urls() = durl.mapNotNull { segment ->
            segment.url.ifBlank { segment.backup_url.firstOrNull().orEmpty() }.takeIf(String::isNotBlank)
        }

        fun PlayData.labels() = accept_quality.zip(accept_description).toMap()
        if (!forceDash && quality <= 64) {
            val progressive = runCatching { get<ApiResponse<PlayData>>(requestUrl("0", "html5")).requireData() }.getOrNull()
            val urls = progressive?.urls().orEmpty()
            if (urls.isNotEmpty()) return PlayResult(urls, null, progressive!!.quality, progressive.accept_quality, progressive.labels())
        }
        val dash = get<ApiResponse<PlayData>>(requestUrl("16")).requireData()
        val video = dash.dash?.video?.filter { it.id <= quality && it.url.isNotBlank() }?.maxByOrNull { it.id }
            ?: dash.dash?.video?.filter { it.url.isNotBlank() }?.minByOrNull { it.id }
        val audio = dash.dash?.audio?.filter { it.url.isNotBlank() }?.maxByOrNull { it.bandwidth }
        if (video != null) return PlayResult(listOf(video.url), audio?.url, video.id, dash.accept_quality, dash.labels(), isDash = true)
        val html5 = get<ApiResponse<PlayData>>(requestUrl("0", "html5")).requireData()
        val urls = html5.urls()
        if (urls.isNotEmpty()) return PlayResult(urls, null, html5.quality, html5.accept_quality, html5.labels())
        // Some videos reject the HTML5 hint but still expose a progressive default response.
        val fallback = get<ApiResponse<PlayData>>(requestUrl("0")).requireData()
        return PlayResult(
            fallback.urls().ifEmpty { error("当前视频没有可用的播放地址") }, null,
            fallback.quality, fallback.accept_quality, fallback.labels()
        )
    }

    suspend fun interaction(aid: Long, mid: Long): InteractionState {
        return coroutineScope {
            val liked = async { runCatching { get<ApiResponse<Int>>("https://api.bilibili.com/x/web-interface/archive/has/like?aid=$aid").data == 1 }.getOrNull() }
            val later = async { runCatching { watchLater().any { it.aid == aid } }.getOrNull() }
            val folders = async { runCatching {
                get<ApiResponse<FavoriteData>>("https://api.bilibili.com/x/v3/fav/folder/created/list-all?up_mid=$mid&rid=$aid&type=2")
                    .requireData().list.filter(FavoriteFolder::isFavorite).map(FavoriteFolder::id).toSet()
            }.getOrNull() }
            val coins = async { runCatching { get<ApiResponse<CoinData>>("https://api.bilibili.com/x/web-interface/archive/coins?aid=$aid").requireData().multiply }.getOrNull() }
            InteractionState(liked.await(), later.await(), folders.await(), coins.await())
        }
    }

    suspend fun shortInteraction(aid: Long): ShortInteractionState = coroutineScope {
        val liked = async { runCatching { get<ApiResponse<Int>>("https://api.bilibili.com/x/web-interface/archive/has/like?aid=$aid").requireData() == 1 }.getOrNull() }
        val coins = async { runCatching { get<ApiResponse<CoinData>>("https://api.bilibili.com/x/web-interface/archive/coins?aid=$aid").requireData().multiply }.getOrNull() }
        ShortInteractionState(liked.await(), coins.await())
    }

    suspend fun setFollowing(mid: Long, follow: Boolean) = post(
        "https://api.bilibili.com/x/relation/modify",
        mapOf("fid" to mid.toString(), "act" to if (follow) "1" else "2", "re_src" to "11")
    )

    suspend fun setLike(aid: Long, add: Boolean) = post("https://api.bilibili.com/x/web-interface/archive/like",
        mapOf("aid" to aid.toString(), "like" to if (add) "1" else "2"))

    suspend fun setWatchLater(aid: Long, add: Boolean) = post(
        "https://api.bilibili.com/x/v2/history/toview/${if (add) "add" else "del"}", mapOf("aid" to aid.toString()))

    suspend fun setFavorite(aid: Long, folderId: Long, add: Boolean) = post(
        "https://api.bilibili.com/x/v3/fav/resource/deal", mapOf("rid" to aid.toString(), "type" to "2",
            (if (add) "add_media_ids" else "del_media_ids") to folderId.toString()))

    suspend fun postComment(aid: Long, message: String) = post("https://api.bilibili.com/x/v2/reply/add",
        mapOf("type" to "1", "oid" to aid.toString(), "message" to message, "plat" to "1"))

    suspend fun addCoin(aid: Long, bvid: String, multiply: Int, selectLike: Boolean) = post(
        "https://api.bilibili.com/x/web-interface/coin/add",
        mapOf("aid" to aid.toString(), "bvid" to bvid, "multiply" to multiply.toString(),
            "select_like" to if (selectLike) "1" else "0"),
        referer = "https://www.bilibili.com/video/$bvid/"
    )

    suspend fun reportHeartbeat(aid: Long, cid: Long, bvid: String, playedTime: Long, realtime: Long, startTs: Long, playType: Int) = post(
        "https://api.bilibili.com/x/click-interface/web/heartbeat",
        mapOf("aid" to aid.toString(), "cid" to cid.toString(), "bvid" to bvid,
            "played_time" to playedTime.toString(), "realtime" to realtime.toString(),
            "start_ts" to startTs.toString(), "type" to "3", "dt" to "2", "play_type" to playType.toString()),
        referer = "https://www.bilibili.com/video/$bvid/"
    )

    suspend fun postDanmaku(cid: Long, bvid: String, aid: Long, message: String, progress: Long) = post(
        "https://api.bilibili.com/x/v2/dm/post", mapOf("type" to "1", "oid" to cid.toString(),
            "bvid" to bvid, "aid" to aid.toString(), "msg" to message, "progress" to progress.toString(),
            "mode" to "1", "fontsize" to "25", "color" to "16777215", "pool" to "0",
            "rnd" to (System.currentTimeMillis() / 1000).toString()))

    suspend fun danmaku(cid: Long): DanmakuResult = withContext(Dispatchers.IO) {
        val sources = listOf("https://api.bilibili.com/x/v1/dm/list.so?oid=$cid", "https://comment.bilibili.com/$cid.xml")
        val errors = mutableListOf<String>()
        sources.forEachIndexed { index, source ->
            val loaded = runCatching { parseDanmaku(source) }.onFailure { errors += "${source.substringAfter("//").substringBefore('/')}: ${it.message}" }.getOrNull()
            if (!loaded.isNullOrEmpty()) return@withContext DanmakuResult(loaded, index > 0, source, lastError = errors.lastOrNull())
        }
        if (errors.size < sources.size) DanmakuResult(emptyList(), true, sources.last(), genuineEmpty = true, lastError = errors.lastOrNull())
        else error(errors.joinToString("；"))
    }

    private fun parseDanmaku(url: String): List<Danmaku> {
        val response = client.newCall(Request.Builder().url(url).header("Accept", "application/xml,text/xml,*/*")
            .header("Referer", "https://www.bilibili.com/").build()).execute()
        response.use {
            if (!it.isSuccessful) error("弹幕加载失败（HTTP ${it.code}）")
            val body = it.body ?: error("弹幕接口返回空内容")
            val stream = when (it.header("Content-Encoding")?.lowercase()) {
                "deflate" -> InflaterInputStream(body.byteStream(), Inflater(true))
                "gzip" -> GZIPInputStream(body.byteStream())
                else -> body.byteStream()
            }
            val parser = android.util.Xml.newPullParser().apply { setInput(stream, null) }
            return buildList {
                while (parser.eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "d") {
                        val values = parser.getAttributeValue(null, "p").orEmpty().split(',')
                        val text = parser.nextText().replace(Regex("[\\u0000-\\u001f&&[^\\n\\t]]"), "").trim()
                        val time = values.getOrNull(0)?.toFloatOrNull()
                        val mode = values.getOrNull(1)?.toIntOrNull()
                        val color = values.getOrNull(3)?.toLongOrNull()
                        if (time != null && time >= 0 && mode != null && mode in 1..5 &&
                            color != null && color in 0L..0xffffffL && text.isNotBlank()
                        ) { if (size < 20_000) add(Danmaku(time, mode, color, text.take(100))) }
                    } else parser.next()
                }
            }.sortedBy(Danmaku::time)
        }
    }

    suspend fun profile(): NavData {
        val response = get<ApiResponse<NavData>>("https://api.bilibili.com/x/web-interface/nav")
        // Signed-out nav responses use -101; they still represent a valid logged-out session.
        if (response.code == -101 && response.data != null) return response.data
        if (response.code == -101) return NavData()
        return response.requireData()
    }

    fun playbackHeaders(): Map<String, String> {
        val cookieHeader = cookieJar.loadForRequest("https://www.bilibili.com/".toHttpUrl())
            .joinToString("; ") { "${it.name}=${it.value}" }
        return buildMap {
            put("Referer", "https://www.bilibili.com/")
            put("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Mobile Safari/537.36")
            if (cookieHeader.isNotBlank()) put("Cookie", cookieHeader)
        }
    }

    fun livePlaybackHeaders(roomId: Long): Map<String, String> {
        val page = "https://live.bilibili.com/$roomId"
        val cookieHeader = cookieJar.loadForRequest(page.toHttpUrl()).joinToString("; ") { "${it.name}=${it.value}" }
        return buildMap {
            put("Referer", page)
            put("Origin", "https://live.bilibili.com")
            put("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Mobile Safari/537.36")
            put("Cache-Control", "no-cache")
            if (cookieHeader.isNotBlank()) put("Cookie", cookieHeader)
        }
    }

    suspend fun logout() {
        val target = "https://passport.bilibili.com/login/exit/v2".toHttpUrl()
        try {
            val csrf = cookieJar.valueFor(target, "bili_jct")
            if (csrf != null) {
                val response = postRaw<ApiResponse<JsonElement>>(target.toString(), mapOf("biliCSRF" to csrf))
                if (response.code != 0) throw ApiBusinessException(response.code, apiError(response.code))
            }
        } finally { cookieJar.clear() }
    }

    suspend fun captcha(): CaptchaParameters {
        val data = get<ApiResponse<CaptchaData>>("https://passport.bilibili.com/x/passport-login/captcha?source=main_web").requireData()
        val gt = data.geetest?.gt
        val challenge = data.geetest?.challenge
        if (data.token.isNullOrBlank() || gt.isNullOrBlank() || challenge.isNullOrBlank() ||
            (data.type != null && data.type !in setOf("geetest", "1"))) error("当前验证码类型不受支持，请改用二维码登录")
        return CaptchaParameters(data.token, gt, challenge)
    }

    suspend fun sendSms(phone: String, captcha: CaptchaParameters, validate: String, seccode: String): String {
        val response = postRaw<ApiResponse<SmsSendData>>("https://passport.bilibili.com/x/passport-login/web/sms/send",
            mapOf("tel" to phone, "cid" to MAINLAND_CID, "token" to captcha.token, "challenge" to captcha.challenge,
                "validate" to validate, "seccode" to seccode, "source" to "main_web"))
        if (response.code != 0) throw ApiBusinessException(response.code, apiError(response.code))
        return response.data?.captchaKey?.takeIf { it.isNotBlank() } ?: error("短信接口未返回 captcha_key")
    }

    suspend fun loginSms(phone: String, code: String, captchaKey: String): NavData {
        val response = postRaw<ApiResponse<JsonElement>>("https://passport.bilibili.com/x/passport-login/web/login/sms",
            mapOf("cid" to MAINLAND_CID, "tel" to phone, "code" to code, "captcha_key" to captchaKey,
                "source" to "main_web", "go_url" to "https://www.bilibili.com/", "keep" to "true"))
        if (response.code != 0) throw ApiBusinessException(response.code, apiError(response.code))
        return profile().takeIf { it.isLogin } ?: error("短信验证完成，但登录状态校验失败，请改用二维码")
    }

    suspend fun generateQr(): QrSession {
        val data = get<ApiResponse<QrData>>(
            "https://passport.bilibili.com/x/passport-login/web/qrcode/generate"
        ).requireData()
        return QrSession(data.qrcode_key, data.url)
    }

    suspend fun pollQr(key: String): QrPollData {
        val url = "https://passport.bilibili.com/x/passport-login/web/qrcode/poll".toHttpUrl()
            .newBuilder().addQueryParameter("qrcode_key", key).build().toString()
        return get<ApiResponse<QrPollData>>(url).requireData()
    }

    private suspend inline fun <reified T> get(url: String): T = withContext(Dispatchers.IO) {
        val response = client.newCall(Request.Builder().url(url).get().build()).execute()
        response.use {
            if (!it.isSuccessful) error("网络请求失败（HTTP ${it.code}）")
            json.decodeFromString<T>(it.body?.string() ?: error("服务器返回了空内容"))
        }
    }

    private suspend fun post(url: String, values: Map<String, String>, origin: String = "https://www.bilibili.com", referer: String = "https://www.bilibili.com/") = withContext(Dispatchers.IO) {
        val target = url.toHttpUrl()
        cookieJar.valueFor(target, "SESSDATA") ?: error("登录会话已失效，请重新登录")
        val csrf = cookieJar.valueFor(target, "bili_jct") ?: error("请先登录后再试")
        val body = FormBody.Builder().apply { (values + mapOf("csrf" to csrf, "csrf_token" to csrf)).forEach { (k, v) -> add(k, v) } }.build()
        val request = Request.Builder().url(url).header("Origin", origin)
            .header("Referer", referer).post(body).build()
        try { client.newCall(request).execute().use {
            if (!it.isSuccessful) error("操作失败（HTTP ${it.code}）")
            val response = json.decodeFromString<ApiResponse<JsonElement>>(it.body?.string().orEmpty())
            if (response.code != 0) throw ApiBusinessException(response.code, response.message.ifBlank { apiError(response.code) })
        } } catch (error: ApiBusinessException) { throw error }
        catch (error: IOException) { throw AmbiguousWriteException("写入结果未知", error) }
        catch (error: SerializationException) { throw AmbiguousWriteException("写入结果未知", error) }
    }

    private suspend inline fun <reified T> postRaw(url: String, values: Map<String, String>): T = withContext(Dispatchers.IO) {
        val body = FormBody.Builder().apply { values.forEach { (k, v) -> add(k, v) } }.build()
        val request = Request.Builder().url(url).header("Origin", "https://www.bilibili.com")
            .header("Referer", "https://www.bilibili.com/").post(body).build()
        client.newCall(request).execute().use {
            if (!it.isSuccessful) error("网络请求失败（HTTP ${it.code}）")
            json.decodeFromString<T>(it.body?.string() ?: error("服务器返回了空内容"))
        }
    }

    private fun ApiResponse<JsonElement>.requireDataOrUnit() { if (code != 0) error(apiError(code)) }

    private fun <T> ApiResponse<T>.requireData(): T {
        if (code != 0) error(apiError(code))
        return data ?: error("服务器响应缺少数据")
    }

    private fun apiError(code: Int) = when (code) {
        -101 -> "请先登录后再试"
        -401 -> "登录会话校验失败，请退出后重新登录"
        -111 -> "登录状态已失效，请重新登录"
        -400 -> "请求参数有误"
        -403, -412 -> "请求被服务器限制，请稍后再试"
        -404 -> "内容不存在或已失效"
        62002, 62004 -> "视频不可见或正在审核"
        else -> "服务器暂时无法处理请求（错误码 $code）"
    }

    private fun signWbi(params: Map<String, String>, wbi: WbiImage): Map<String, String> {
        val imgKey = wbi.img_url.substringAfterLast('/').substringBefore('.')
        val subKey = wbi.sub_url.substringAfterLast('/').substringBefore('.')
        if (imgKey.isBlank() || subKey.isBlank()) error("WBI key is unavailable")
        val table = intArrayOf(46,47,18,2,53,8,23,32,15,50,10,31,58,3,45,35,27,43,5,49,33,9,42,19,29,28,14,39,12,38,41,13,37,48,7,16,24,55,40,61,26,17,0,1,60,51,30,4,22,25,54,21,56,59,6,63,57,62,11,36,20,34,44,52)
        val source = imgKey + subKey
        val mixin = buildString {
            table.forEach { index -> if (index in source.indices) append(source[index]) }
        }.take(32)
        val values = params + ("wts" to (System.currentTimeMillis() / 1000).toString())
        val encoded = values.toSortedMap().entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value.filterNot { it in "!'()*" })}"
        }
        val digest = MessageDigest.getInstance("MD5").digest((encoded + mixin).toByteArray())
            .joinToString("") { "%02x".format(it) }
        return values + ("w_rid" to digest)
    }

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
        .replace("+", "%20").replace("%7E", "~")
}

private fun JsonElement.apiData(): JsonElement {
    val root = jsonObject
    val code = root["code"]?.jsonPrimitive?.intOrNull ?: -1
    if (code != 0) error("直播接口错误（$code）")
    return root["data"] ?: error("直播接口缺少数据")
}
private fun Map<String, JsonElement>.string(vararg names: String): String = names.firstNotNullOfOrNull { this[it]?.jsonPrimitive?.contentOrNull }.orEmpty()
private fun Map<String, JsonElement>.long(vararg names: String): Long = names.firstNotNullOfOrNull { this[it]?.jsonPrimitive?.longOrNull } ?: 0

private const val MAINLAND_CID = "1"

private fun String.parseDuration(): Int = split(':').fold(0) { total, part -> total * 60 + (part.toIntOrNull() ?: 0) }
private fun String?.parseCount(): Long {
    val value = this?.trim().orEmpty()
    val multiplier = when {
        value.endsWith("亿") -> 100_000_000.0
        value.endsWith("万") -> 10_000.0
        else -> 1.0
    }
    return ((value.dropLast(if (multiplier == 1.0) 0 else 1).toDoubleOrNull() ?: 0.0) * multiplier).toLong()
}
