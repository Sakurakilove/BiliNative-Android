package dev.opencode.bilimobile.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse<T>(val code: Int = -1, val message: String = "", val data: T? = null)

@Serializable
data class PopularData(val list: List<Video> = emptyList())

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
    val pages: List<Page> = emptyList()
) {
    val creator: String get() = owner.name.ifBlank { author }
    val views: Long get() = stat.view.takeIf { it > 0 } ?: play
    val coverUrl: String get() = when {
        pic.startsWith("//") -> "https:$pic"
        pic.startsWith("http://") -> "https://${pic.removePrefix("http://")}"
        else -> pic
    }
    // Popular also contains bangumi, live, and other redirect-only cards that this player cannot open.
    val isPlayable: Boolean get() = bvid.isNotBlank() && redirectUrl.isBlank() && (goto.isBlank() || goto == "av")
}

@Serializable data class Owner(val name: String = "", val face: String = "")
@Serializable data class Stat(val view: Long = 0, val danmaku: Long = 0, val like: Long = 0)
@Serializable data class Page(val cid: Long = 0, val page: Int = 1, val part: String = "")

@Serializable
data class PlayData(val durl: List<PlayUrl> = emptyList())

@Serializable
data class PlayUrl(val url: String = "", val backup_url: List<String> = emptyList())

@Serializable
data class NavData(
    @SerialName("isLogin") val isLogin: Boolean = false,
    val uname: String = "",
    val face: String = "",
    val mid: Long = 0,
    val wbi_img: WbiImage = WbiImage()
)

@Serializable data class WbiImage(val img_url: String = "", val sub_url: String = "")
@Serializable data class QrData(val url: String = "", val qrcode_key: String = "")
@Serializable data class QrPollData(val code: Int = -1, val message: String = "")

data class QrSession(val key: String, val url: String)

sealed interface LoginState {
    data object Idle : LoginState
    data object Loading : LoginState
    data class Ready(val qr: QrSession) : LoginState
    data class Scanned(val qr: QrSession) : LoginState
    data object Success : LoginState
    data class Error(val message: String) : LoginState
}
