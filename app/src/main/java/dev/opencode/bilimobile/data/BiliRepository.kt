package dev.opencode.bilimobile.data

import android.content.Context
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class BiliRepository(context: Context) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val cookieJar = PersistentCookieJar(context)
    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Mobile Safari/537.36")
                .header("Referer", "https://www.bilibili.com/")
                .build())
        }.build()

    suspend fun popular(): List<Video> = get<ApiResponse<PopularData>>(
        "https://api.bilibili.com/x/web-interface/popular?ps=30&pn=1"
    ).requireData().list.filter { it.isPlayable }

    suspend fun channel(channel: Channel): List<Video> {
        if (channel.popular) return popular()
        // The anonymous recommendation endpoint frequently rejects otherwise valid requests.
        // Popular is a stable, non-empty recommendation baseline with a distinct UI label.
        if (channel.tid == null) return popular()
        return get<ApiResponse<RankingData>>("https://api.bilibili.com/x/web-interface/ranking/v2?rid=${channel.tid}&type=all")
            .requireData().list.filter { it.isPlayable }
    }

    suspend fun dynamics(): List<DynamicVideo> {
        val root = get<JsonElement>("https://api.bilibili.com/x/polymer/web-dynamic/v1/feed/all")
        val response = root.jsonObject
        if (response["code"]?.jsonPrimitive?.intOrNull != 0) error(apiError(response["code"]?.jsonPrimitive?.intOrNull ?: -1))
        return response["data"]?.jsonObject?.get("items")?.jsonArray.orEmpty().mapNotNull { element ->
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
                    author?.get("face")?.jsonPrimitive?.contentOrNull.orEmpty()
                )
            }.getOrNull()
        }
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
        "https://api.bilibili.com/x/web-interface/history/cursor?ps=12"
    ).requireData().list

    suspend fun watchLater(): List<Video> = get<ApiResponse<WatchLaterData>>(
        "https://api.bilibili.com/x/v2/history/toview"
    ).requireData().list.filter { it.bvid.isNotBlank() }

    suspend fun watchLaterPreview(): List<Video> = watchLater().take(12)

    suspend fun favoriteFolders(mid: Long): List<FavoriteFolder> = get<ApiResponse<FavoriteData>>(
        "https://api.bilibili.com/x/v3/fav/folder/created/list-all?up_mid=$mid"
    ).requireData().list

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

    suspend fun interaction(aid: Long): InteractionState {
        val liked = runCatching { get<ApiResponse<Int>>("https://api.bilibili.com/x/web-interface/archive/has/like?aid=$aid").data == 1 }.getOrNull()
        val later = runCatching { watchLater().any { it.aid == aid } }.getOrNull()
        val folders = runCatching {
            get<ApiResponse<FavoriteData>>("https://api.bilibili.com/x/v3/fav/folder/created/list-all?rid=$aid&type=2")
                .requireData().list.filter(FavoriteFolder::isFavorite).map(FavoriteFolder::id).toSet()
        }.getOrNull()
        return InteractionState(liked, later, folders)
    }

    suspend fun setLike(aid: Long, add: Boolean) = post("https://api.bilibili.com/x/web-interface/archive/like",
        mapOf("aid" to aid.toString(), "like" to if (add) "1" else "2"))

    suspend fun setWatchLater(aid: Long, add: Boolean) = post(
        "https://api.bilibili.com/x/v2/history/toview/${if (add) "add" else "del"}", mapOf("aid" to aid.toString()))

    suspend fun setFavorite(aid: Long, folderId: Long, add: Boolean) = post(
        "https://api.bilibili.com/x/v3/fav/resource/deal", mapOf("rid" to aid.toString(), "type" to "2",
            (if (add) "add_media_ids" else "del_media_ids") to folderId.toString()))

    suspend fun danmaku(cid: Long): List<Danmaku> = withContext(Dispatchers.IO) {
        val response = client.newCall(Request.Builder().url("https://comment.bilibili.com/$cid.xml").build()).execute()
        response.use {
            if (!it.isSuccessful) error("弹幕加载失败（HTTP ${it.code}）")
            val parser = android.util.Xml.newPullParser().apply { setInput(it.body?.byteStream(), "UTF-8") }
            buildList {
                while (parser.eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "d") {
                        val values = parser.getAttributeValue(null, "p").orEmpty().split(',')
                        val text = parser.nextText().trim()
                        val time = values.getOrNull(0)?.toFloatOrNull()
                        val mode = values.getOrNull(1)?.toIntOrNull()
                        val color = values.getOrNull(3)?.toLongOrNull()
                        if (time != null && time >= 0 && mode != null && mode in 1..5 &&
                            color != null && color in 0L..0xffffffL && text.isNotBlank()
                        ) add(Danmaku(time, mode, color, text.take(100)))
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

    fun logout() = cookieJar.clear()

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

    private suspend fun post(url: String, values: Map<String, String>) = withContext(Dispatchers.IO) {
        val csrf = cookieJar.value("bili_jct") ?: error("请先登录后再试")
        val body = FormBody.Builder().apply { (values + ("csrf" to csrf)).forEach { (k, v) -> add(k, v) } }.build()
        val request = Request.Builder().url(url).header("Origin", "https://www.bilibili.com")
            .header("Referer", "https://www.bilibili.com/").post(body).build()
        client.newCall(request).execute().use {
            if (!it.isSuccessful) error("操作失败（HTTP ${it.code}）")
            json.decodeFromString<ApiResponse<JsonElement>>(it.body?.string().orEmpty()).requireDataOrUnit()
        }
    }

    private fun ApiResponse<JsonElement>.requireDataOrUnit() { if (code != 0) error(apiError(code)) }

    private fun <T> ApiResponse<T>.requireData(): T {
        if (code != 0) error(apiError(code))
        return data ?: error("服务器响应缺少数据")
    }

    private fun apiError(code: Int) = when (code) {
        -101 -> "请先登录后再试"
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
