package dev.opencode.bilimobile.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.opencode.bilimobile.data.BiliRepository
import dev.opencode.bilimobile.data.LoginState
import dev.opencode.bilimobile.data.Comment
import dev.opencode.bilimobile.data.FavoriteFolder
import dev.opencode.bilimobile.data.HistoryItem
import dev.opencode.bilimobile.data.NavData
import dev.opencode.bilimobile.data.PlayResult
import dev.opencode.bilimobile.data.Video
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ContentState<T>(
    val value: T? = null,
    val loading: Boolean = false,
    val error: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = BiliRepository(application)
    private val _popular = MutableStateFlow(ContentState<List<Video>>(loading = true))
    val popular = _popular.asStateFlow()
    private val _search = MutableStateFlow(ContentState<List<Video>>(value = emptyList()))
    val search = _search.asStateFlow()
    private val _details = MutableStateFlow(ContentState<Video>())
    val details = _details.asStateFlow()
    private val _playUrl = MutableStateFlow(ContentState<PlayResult>())
    val playUrl = _playUrl.asStateFlow()
    private val _profile = MutableStateFlow(ContentState<NavData>(loading = true))
    val profile = _profile.asStateFlow()
    private val _comments = MutableStateFlow(ContentState<List<Comment>>(value = emptyList()))
    val comments = _comments.asStateFlow()
    private val _related = MutableStateFlow(ContentState<List<Video>>(value = emptyList()))
    val related = _related.asStateFlow()
    private val _history = MutableStateFlow(ContentState<List<HistoryItem>>())
    val history = _history.asStateFlow()
    private val _watchLater = MutableStateFlow(ContentState<List<Video>>())
    val watchLater = _watchLater.asStateFlow()
    private val _favorites = MutableStateFlow(ContentState<List<FavoriteFolder>>())
    val favorites = _favorites.asStateFlow()
    private val _login = MutableStateFlow<LoginState>(LoginState.Idle)
    val login = _login.asStateFlow()
    private var loginJob: Job? = null
    private var searchJob: Job? = null
    private var detailsJob: Job? = null
    private var playJob: Job? = null
    private var searchRequest = 0
    private var detailsRequest = 0
    private var playRequest = 0
    private var commentPage = 1
    private var currentAid = 0L

    init {
        refreshPopular()
        refreshProfile()
    }

    fun refreshPopular() = viewModelScope.launch {
        _popular.value = ContentState(value = _popular.value.value, loading = true)
        try {
            _popular.value = ContentState(value = repository.popular())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            _popular.value = ContentState(error = error.userMessage())
        }
    }

    fun search(query: String) {
        if (query.isBlank()) return
        searchJob?.cancel()
        val request = ++searchRequest
        searchJob = viewModelScope.launch {
            _search.value = ContentState(value = _search.value.value, loading = true)
            try {
                val results = repository.search(query.trim())
                if (request == searchRequest) _search.value = ContentState(value = results)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (request == searchRequest) _search.value = ContentState(error = error.userMessage())
            }
        }
    }

    fun loadDetails(bvid: String) {
        detailsJob?.cancel()
        playJob?.cancel()
        val request = ++detailsRequest
        playRequest++
        detailsJob = viewModelScope.launch {
            _details.value = ContentState(loading = true)
            _playUrl.value = ContentState()
            _comments.value = ContentState(value = emptyList(), loading = true)
            _related.value = ContentState(value = emptyList(), loading = true)
            try {
                val video = repository.details(bvid)
                if (request != detailsRequest) return@launch
                _details.value = ContentState(value = video)
                loadStream(video.bvid, video.cid.takeIf { it > 0 } ?: video.pages.firstOrNull()?.cid ?: 0)
                currentAid = video.aid
                commentPage = 1
                loadComments(reset = true)
                loadRelated(video.bvid)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (request == detailsRequest) _details.value = ContentState(error = error.userMessage())
            }
        }
    }

    fun loadStream(bvid: String, cid: Long, quality: Int = 64) {
        playJob?.cancel()
        val request = ++playRequest
        playJob = viewModelScope.launch {
            if (cid == 0L) {
                _playUrl.value = ContentState(error = "当前分集无法播放")
                return@launch
            }
            _playUrl.value = ContentState(loading = true)
            try {
                val url = repository.playUrl(bvid, cid, quality)
                if (request == playRequest) _playUrl.value = ContentState(value = url)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (request == playRequest) _playUrl.value = ContentState(error = error.userMessage())
            }
        }
    }

    private fun loadRelated(bvid: String) = viewModelScope.launch {
        try { _related.value = ContentState(value = repository.related(bvid)) }
        catch (error: CancellationException) { throw error }
        catch (error: Throwable) { _related.value = ContentState(error = error.userMessage()) }
    }

    fun loadComments(reset: Boolean = false) = viewModelScope.launch {
        if (currentAid == 0L || (_comments.value.loading && !reset)) return@launch
        val page = if (reset) 1 else commentPage + 1
        _comments.value = _comments.value.copy(loading = true, error = null)
        try {
            val result = repository.comments(currentAid, page).replies.orEmpty()
            val existing = if (reset) emptyList() else _comments.value.value.orEmpty()
            _comments.value = ContentState(value = existing + result)
            commentPage = page
        } catch (error: CancellationException) { throw error }
        catch (error: Throwable) { _comments.value = _comments.value.copy(loading = false, error = error.userMessage()) }
    }

    fun refreshProfile() = viewModelScope.launch {
        _profile.value = ContentState(value = _profile.value.value, loading = true)
        try {
            val profile = repository.profile()
            _profile.value = ContentState(value = profile)
            if (profile.isLogin) loadProfileSections(profile.mid)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            _profile.value = ContentState(error = error.userMessage())
        }
    }

    private fun loadProfileSections(mid: Long) {
        fun <T> load(target: MutableStateFlow<ContentState<List<T>>>, block: suspend () -> List<T>) = viewModelScope.launch {
            target.value = ContentState(loading = true)
            try { target.value = ContentState(value = block()) }
            catch (error: CancellationException) { throw error }
            catch (error: Throwable) { target.value = ContentState(error = error.userMessage()) }
        }
        load(_history) { repository.history() }
        load(_watchLater) { repository.watchLater() }
        load(_favorites) { repository.favoriteFolders(mid) }
    }

    fun beginLogin() {
        loginJob?.cancel()
        loginJob = viewModelScope.launch {
            _login.value = LoginState.Loading
            val qr = try {
                repository.generateQr()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _login.value = LoginState.Error(error.userMessage()); return@launch
            }
            _login.value = LoginState.Ready(qr)
            while (true) {
                delay(2_000)
                val status = try {
                    repository.pollQr(qr.key)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    _login.value = LoginState.Error(error.userMessage()); return@launch
                }
                when (status.code) {
                    0 -> {
                        _login.value = LoginState.Success
                        refreshProfile()
                        return@launch
                    }
                    86090 -> _login.value = LoginState.Scanned(qr)
                    86038 -> {
                        _login.value = LoginState.Error("二维码已过期，请重新生成")
                        return@launch
                    }
                    86101 -> if (_login.value !is LoginState.Scanned) _login.value = LoginState.Ready(qr)
                    else -> {
                        _login.value = LoginState.Error("登录失败（错误码 ${status.code}）")
                        return@launch
                    }
                }
            }
        }
    }

    fun dismissLogin() {
        loginJob?.cancel()
        _login.value = LoginState.Idle
    }

    fun logout() {
        repository.logout()
        _history.value = ContentState()
        _watchLater.value = ContentState()
        _favorites.value = ContentState()
        dismissLogin()
        refreshProfile()
    }

    fun playbackHeaders() = repository.playbackHeaders()

    private fun Throwable.userMessage(): String {
        val raw = message.orEmpty()
        return when {
            raw.isBlank() -> "发生未知错误，请稍后重试"
            raw.contains("timeout", true) -> "连接超时，请检查网络后重试"
            raw.contains("Unable to resolve host", true) -> "无法连接网络，请检查网络设置"
            raw.contains("serialization", true) || raw.contains("JSON", true) -> "服务器数据格式发生变化"
            else -> raw
        }
    }
}
