package dev.opencode.bilimobile.data

import android.content.Context
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
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

    suspend fun playUrl(bvid: String, cid: Long): String {
        fun requestUrl(platform: String?) = "https://api.bilibili.com/x/player/playurl".toHttpUrl().newBuilder()
            .addQueryParameter("bvid", bvid).addQueryParameter("cid", cid.toString())
            .addQueryParameter("qn", "64").addQueryParameter("fnval", "0")
            .apply { platform?.let { addQueryParameter("platform", it) } }
            .build().toString()
        fun PlayData.primaryUrl() = durl.firstOrNull()?.let { segment ->
            segment.url.ifBlank { segment.backup_url.firstOrNull().orEmpty() }
        }.orEmpty()

        val primary = get<ApiResponse<PlayData>>(requestUrl("html5")).requireData().primaryUrl()
        if (primary.isNotBlank()) return primary
        // Some videos reject the HTML5 hint but still expose a progressive default response.
        return get<ApiResponse<PlayData>>(requestUrl(null)).requireData().primaryUrl()
            .ifBlank { error("No progressive stream is available for this video") }
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
            if (!it.isSuccessful) error("HTTP ${it.code}")
            json.decodeFromString<T>(it.body?.string() ?: error("Empty response"))
        }
    }

    private fun <T> ApiResponse<T>.requireData(): T {
        if (code != 0) error(message.ifBlank { "API error $code" })
        return data ?: error("Response did not include data")
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
