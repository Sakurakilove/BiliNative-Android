package dev.opencode.bilimobile.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse<T>(val code: Int = -1, val message: String = "", val data: T? = null)

@Serializable
data class PopularData(val list: List<Video> = emptyList())
@Serializable data class RecommendData(val item: List<Video> = emptyList())
@Serializable data class RankingData(val list: List<Video> = emptyList())

@Serializable
data class SearchData(val result: List<SearchVideo> = emptyList())

@Serializable
data class SearchVideo(
    val type: String = "",
    val bvid: String = "",
    val aid: Long = 0,
    val title: String = "",
    val pic: String = "",
    val author: String = "",
    val duration: String = "",
    val play: Long = 0
) {
    fun asVideo() = Video(
        bvid = bvid,
        aid = aid,
        title = title,
        pic = pic,
        author = author,
        duration = duration.split(':').fold(0) { total, part -> total * 60 + (part.toIntOrNull() ?: 0) },
        play = play
    )
}

@Serializable
data class Video(
    val bvid: String = "",
    val aid: Long = 0,
    val cid: Long = 0,
    val title: String = "",
    val pic: String = "",
    val desc: String = "",
    val duration: Int = 0,
    val owner: Owner = Owner(),
    val author: String = "",
    val play: Long = 0,
    val goto: String = "",
    @SerialName("redirect_url") val redirectUrl: String = "",
    val stat: Stat = Stat(),
    val pages: List<Page> = emptyList(),
    val pubdate: Long = 0,
    val copyright: Int = 0,
    val dimension: Dimension = Dimension()
) {
    val creator: String get() = owner.name.ifBlank { author }
    val views: Long get() = stat.view.takeIf { it > 0 } ?: play
    val coverUrl: String get() = when {
        pic.startsWith("//") -> "https:$pic"
        pic.startsWith("http://") -> "https://${pic.removePrefix("http://")}"
        else -> pic
    }
    val isShort: Boolean get() = goto == "vertical_av" ||
        (dimension.width > 0 && dimension.height > dimension.width) || duration in 1..90
    // Popular also contains bangumi, live, and other redirect-only cards that this player cannot open.
    val isPlayable: Boolean get() = bvid.isNotBlank() &&
        (goto.isBlank() || goto == "av" || goto == "vertical_av") &&
        (redirectUrl.isBlank() || goto == "vertical_av")
}

@Serializable data class Owner(val mid: Long = 0, val name: String = "", val face: String = "")
@Serializable data class Stat(
    val view: Long = 0, val danmaku: Long = 0, val reply: Long = 0, val favorite: Long = 0,
    val coin: Long = 0, val share: Long = 0, val like: Long = 0
)
@Serializable data class Dimension(val width: Int = 0, val height: Int = 0, val rotate: Int = 0)
@Serializable data class Page(val cid: Long = 0, val page: Int = 1, val part: String = "", val duration: Int = 0)

@Serializable
data class PlayData(
    val quality: Int = 0, val accept_quality: List<Int> = emptyList(),
    val accept_description: List<String> = emptyList(), val durl: List<PlayUrl> = emptyList(),
    val dash: DashData? = null
)

@Serializable
data class PlayUrl(val url: String = "", val backup_url: List<String> = emptyList())

@Serializable data class DashData(val video: List<DashStream> = emptyList(), val audio: List<DashStream> = emptyList())
@Serializable data class DashStream(
    val id: Int = 0, @SerialName("baseUrl") val baseUrl: String = "",
    @SerialName("base_url") val baseUrlLegacy: String = "", val bandwidth: Long = 0,
    @SerialName("mimeType") val mimeType: String = "", val codecs: String = ""
) { val url: String get() = baseUrl.ifBlank { baseUrlLegacy } }

@Serializable
data class NavData(
    @SerialName("isLogin") val isLogin: Boolean = false,
    val uname: String = "",
    val face: String = "",
    val mid: Long = 0,
    val level_info: LevelInfo = LevelInfo(),
    val wbi_img: WbiImage = WbiImage()
)

@Serializable data class LevelInfo(@SerialName("current_level") val currentLevel: Int = 0)
@Serializable data class WbiImage(val img_url: String = "", val sub_url: String = "")
@Serializable data class QrData(val url: String = "", val qrcode_key: String = "")
@Serializable data class QrPollData(val code: Int = -1, val message: String = "")

data class QrSession(val key: String, val url: String, val createdAtMillis: Long = System.currentTimeMillis())

@Serializable data class CaptchaData(
    val type: String? = null, val token: String? = null, val geetest: GeetestData? = null
)
@Serializable data class GeetestData(val gt: String? = null, val challenge: String? = null)
@Serializable data class SmsSendData(@SerialName("captcha_key") val captchaKey: String? = null)
data class CaptchaParameters(val token: String, val gt: String, val challenge: String)

sealed interface SmsLoginState {
    data object Idle : SmsLoginState
    data object CaptchaLoading : SmsLoginState
    data class CaptchaReady(val parameters: CaptchaParameters) : SmsLoginState
    data object Sending : SmsLoginState
    data class CodeSent(val captchaKey: String, val maskedPhone: String, val cooldownUntilMillis: Long, val error: String? = null) : SmsLoginState
    data object LoggingIn : SmsLoginState
    data class Failed(val message: String, val fallbackToQr: Boolean = true) : SmsLoginState
    data object Success : SmsLoginState
}

sealed interface LoginState {
    data object Idle : LoginState
    data object Loading : LoginState
    data class Ready(val qr: QrSession) : LoginState
    data class Scanned(val qr: QrSession) : LoginState
    data object Success : LoginState
    data class Error(val message: String, val qr: QrSession? = null) : LoginState
}

@Serializable data class ReplyData(val page: ReplyPage = ReplyPage(), val replies: List<Comment>? = emptyList())
@Serializable data class ReplyPage(val num: Int = 1, val size: Int = 20, val count: Int = 0)
@Serializable data class Comment(
    val rpid: Long = 0, val ctime: Long = 0, val like: Long = 0, val rcount: Int = 0,
    val member: CommentMember = CommentMember(), val content: CommentContent = CommentContent()
)
@Serializable data class CommentMember(val mid: String = "", val uname: String = "", val avatar: String = "")
@Serializable data class CommentContent(val message: String = "")

@Serializable data class HistoryData(val cursor: HistoryCursor = HistoryCursor(), val list: List<HistoryItem> = emptyList())
@Serializable data class HistoryCursor(val max: Long = 0, val view_at: Long = 0)
@Serializable data class HistoryItem(
    val title: String = "", val cover: String = "", val author_name: String = "", val progress: Int = 0,
    val duration: Int = 0, val history: HistoryRef = HistoryRef()
)
@Serializable data class HistoryRef(val bvid: String = "")

@Serializable data class WatchLaterData(val list: List<Video> = emptyList(), val count: Int = 0)
@Serializable data class FavoriteData(val list: List<FavoriteFolder> = emptyList(), val count: Int = 0)
@Serializable data class FavoriteFolder(
    val id: Long = 0, val title: String = "", val media_count: Int = 0,
    val fav_state: Int = 0
) { val isFavorite: Boolean get() = fav_state == 1 }
@Serializable data class FavoriteResourceData(
    val info: FavoriteFolder = FavoriteFolder(), val medias: List<FavoriteResource>? = emptyList(),
    val has_more: Boolean = false
)
@Serializable data class FavoriteResource(
    val id: Long = 0, val bvid: String = "", val title: String = "", val cover: String = "",
    val duration: Int = 0, val upper: Owner = Owner(), val cnt_info: FavoriteResourceCount = FavoriteResourceCount()
) {
    fun asVideo() = Video(aid = id, bvid = bvid, title = title, pic = cover, duration = duration,
        owner = upper, stat = Stat(view = cnt_info.play))
}
@Serializable data class FavoriteResourceCount(val play: Long = 0)
@Serializable data class CoinData(val multiply: Int = 0)

data class PlayResult(
    val videoUrls: List<String>, val audioUrl: String? = null, val quality: Int,
    val availableQualities: List<Int>, val qualityLabels: Map<Int, String> = emptyMap(),
    val isDash: Boolean = false
) { val videoUrl: String get() = videoUrls.first() }

data class Channel(val title: String, val tid: Int? = null, val popular: Boolean = false, val live: Boolean = false, val short: Boolean = false)
data class DynamicVideo(val id: String, val video: Video, val text: String = "", val time: String = "", val avatar: String = "", val authorMid: Long = 0)
data class DynamicPage(val items: List<DynamicVideo>, val offset: String = "", val hasMore: Boolean = false)
data class HotSearchItem(val keyword: String, val displayName: String, val heatScore: Long = 0)
data class UpProfile(
    val mid: Long, val name: String, val face: String, val sign: String,
    val level: Int, val follower: Long, val following: Boolean, val archiveCount: Int
)
data class InteractionState(
    val liked: Boolean? = null,
    val watchLater: Boolean? = null,
    val favoriteFolderIds: Set<Long>? = null,
    val coinCount: Int? = null
) { val favorite: Boolean? get() = favoriteFolderIds?.isNotEmpty() }
data class Danmaku(val time: Float, val mode: Int, val color: Long, val text: String)
data class DanmakuResult(
    val items: List<Danmaku>, val usedFallback: Boolean, val source: String,
    val genuineEmpty: Boolean = false, val lastError: String? = null
)

data class LiveRoomSummary(
    val roomId: Long, val title: String, val cover: String, val userName: String,
    val areaName: String, val online: Long
)
data class LiveRoomDetail(
    val roomId: Long, val title: String, val cover: String, val userName: String,
    val areaName: String, val online: Long
)
data class LiveQuality(
    val quality: Int, val name: String, val url: String,
    val format: String = "ts", val hls: Boolean = true
)
data class LivePlayInfo(val qualities: List<LiveQuality>) {
    val default: LiveQuality? get() = qualities.firstOrNull()
}
data class LiveMessage(val id: String, val userName: String, val text: String, val time: Long)
