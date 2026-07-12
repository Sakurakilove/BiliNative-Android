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
import dev.opencode.bilimobile.data.Channel
import dev.opencode.bilimobile.data.Danmaku
import dev.opencode.bilimobile.data.DynamicVideo
import dev.opencode.bilimobile.data.InteractionState
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
    val channels = listOf(Channel("推荐"), Channel("热门", popular = true), Channel("动画", 1), Channel("游戏", 4), Channel("知识", 36), Channel("科技", 188), Channel("生活", 160))
    private val _channel = MutableStateFlow(channels.first())
    val channel = _channel.asStateFlow()
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
    private val _dynamics = MutableStateFlow(ContentState<List<DynamicVideo>>())
    val dynamics = _dynamics.asStateFlow()
    private val _interaction = MutableStateFlow(ContentState<InteractionState>())
    val interaction = _interaction.asStateFlow()
    private val _replies = MutableStateFlow<Map<Long, ContentState<List<Comment>>>>(emptyMap())
    val replies = _replies.asStateFlow()
    private val _danmaku = MutableStateFlow(ContentState<List<Danmaku>>())
    val danmaku = _danmaku.asStateFlow()
    private val _login = MutableStateFlow<LoginState>(LoginState.Idle)
    val login = _login.asStateFlow()
    private var loginJob: Job? = null
    private var profileJob: Job? = null
    private var searchJob: Job? = null
    private var detailsJob: Job? = null
    private var playJob: Job? = null
    private var channelJob: Job? = null
    private var dynamicsJob: Job? = null
    private var relatedJob: Job? = null
    private var commentsJob: Job? = null
    private var danmakuJob: Job? = null
    private var interactionJob: Job? = null
    private val replyJobs = mutableMapOf<Long, Job>()
    private val accountJobs = mutableListOf<Job>()
    private var searchRequest = 0
    private var detailsRequest = 0
    private var playRequest = 0
    private var commentPage = 1
    private var currentAid = 0L
    private var currentBvid = ""
    private var currentCid = 0L
    private val channelCache = mutableMapOf<Channel, List<Video>>()
    private val fallbackAttempts = mutableSetOf<Pair<String, Long>>()

    init {
        refreshPopular()
        refreshProfile()
    }

    fun refreshPopular() {
        channelJob?.cancel()
        val selected = _channel.value
        channelJob = viewModelScope.launch {
        _popular.value = ContentState(value = channelCache[selected], loading = true)
        try {
            val result = repository.channel(selected)
            channelCache[selected] = result
            if (_channel.value == selected) _popular.value = ContentState(value = result)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (_channel.value == selected) _popular.value = ContentState(value = channelCache[selected], error = error.userMessage())
        }
        }
    }

    fun selectChannel(value: Channel) {
        if (_channel.value == value) return
        _channel.value = value
        _popular.value = ContentState(value = channelCache[value], loading = true)
        refreshPopular()
    }

    fun loadDynamics() {
        dynamicsJob?.cancel()
        dynamicsJob = viewModelScope.launch {
        _dynamics.value = ContentState(value = _dynamics.value.value, loading = true)
        try { _dynamics.value = ContentState(repository.dynamics()) }
        catch (error: CancellationException) { throw error }
        catch (error: Throwable) { _dynamics.value = ContentState(value = _dynamics.value.value, error = error.userMessage()) }
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
        cancelDetailJobs()
        val request = ++detailsRequest
        playRequest++
        detailsJob = viewModelScope.launch {
            _details.value = ContentState(loading = true)
            _playUrl.value = ContentState()
            _comments.value = ContentState(value = emptyList(), loading = true)
            _related.value = ContentState(value = emptyList(), loading = true)
            _interaction.value = ContentState()
            _replies.value = emptyMap()
            _danmaku.value = ContentState()
            try {
                val video = repository.details(bvid)
                if (request != detailsRequest) return@launch
                _details.value = ContentState(value = video)
                currentBvid = video.bvid
                currentAid = video.aid
                val cid = video.cid.takeIf { it > 0 } ?: video.pages.firstOrNull()?.cid ?: 0
                loadStream(video.bvid, cid)
                loadDanmaku(cid)
                commentPage = 1
                loadComments(reset = true)
                loadRelated(video.bvid)
                if (_profile.value.value?.isLogin == true) loadInteraction(video.aid)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (request == detailsRequest) _details.value = ContentState(error = error.userMessage())
            }
        }
    }

    fun loadDanmaku(cid: Long) {
        danmakuJob?.cancel()
        val request = detailsRequest
        danmakuJob = viewModelScope.launch {
        if (cid <= 0) return@launch
        _danmaku.value = ContentState(loading = true)
        try { val result = repository.danmaku(cid); if (request == detailsRequest && cid == currentCid) _danmaku.value = ContentState(result) }
        catch (error: CancellationException) { throw error }
        catch (error: Throwable) { if (request == detailsRequest && cid == currentCid) _danmaku.value = ContentState(error = error.userMessage()) }
        }
    }

    fun loadReplies(root: Long, page: Int = 1) {
        replyJobs[root]?.cancel()
        val aid = currentAid
        val request = detailsRequest
        replyJobs[root] = viewModelScope.launch {
        _replies.value = _replies.value + (root to ContentState(value = _replies.value[root]?.value, loading = true))
        try {
            val loaded = repository.commentReplies(aid, root, page).replies.orEmpty()
            if (request != detailsRequest || aid != currentAid) return@launch
            val old = if (page == 1) emptyList() else _replies.value[root]?.value.orEmpty()
            _replies.value = _replies.value + (root to ContentState(old + loaded))
        } catch (error: CancellationException) { throw error }
        catch (error: Throwable) { if (request == detailsRequest && aid == currentAid) _replies.value = _replies.value + (root to ContentState(error = error.userMessage())) }
        }
    }

    fun loadInteraction(aid: Long = currentAid) {
        interactionJob?.cancel()
        val request = detailsRequest
        interactionJob = viewModelScope.launch {
        _interaction.value = ContentState(loading = true)
        try { val result = repository.interaction(aid); if (request == detailsRequest && aid == currentAid) _interaction.value = ContentState(result) }
        catch (error: Throwable) { if (request == detailsRequest && aid == currentAid) _interaction.value = ContentState(error = error.userMessage()) }
        }
    }

    fun toggleLike(aid: Long) = updateInteraction { state -> val current = state.liked ?: return@updateInteraction state; repository.setLike(aid, !current); repository.interaction(aid) }
    fun toggleWatchLater(aid: Long) = updateInteraction { state -> val current = state.watchLater ?: return@updateInteraction state; repository.setWatchLater(aid, !current); repository.interaction(aid) }
    fun toggleFavorite(aid: Long, folderId: Long) = updateInteraction { state ->
        val folders = state.favoriteFolderIds ?: return@updateInteraction state
        repository.setFavorite(aid, folderId, folderId !in folders); repository.interaction(aid)
    }

    private fun updateInteraction(action: suspend (InteractionState) -> InteractionState) {
        if (interactionJob?.isActive == true) return
        val aid = currentAid
        val request = detailsRequest
        interactionJob = viewModelScope.launch {
        val old = _interaction.value.value ?: run { _interaction.value = ContentState(error = "请先登录后再试"); return@launch }
        _interaction.value = ContentState(old, loading = true)
        try { val result = action(old); if (request == detailsRequest && aid == currentAid) _interaction.value = ContentState(result) }
        catch (error: Throwable) { if (request == detailsRequest && aid == currentAid) _interaction.value = ContentState(old, error = error.userMessage()) }
        }
    }

    fun loadStream(bvid: String, cid: Long, quality: Int = 64, forceDash: Boolean = false, resetFallback: Boolean = true) {
        if (bvid != currentBvid) return
        playJob?.cancel()
        val request = ++playRequest
        val detailRequest = detailsRequest
        currentCid = cid
        if (resetFallback) fallbackAttempts.remove(bvid to cid)
        playJob = viewModelScope.launch {
            if (cid == 0L) {
                _playUrl.value = ContentState(error = "当前分集无法播放")
                return@launch
            }
            _playUrl.value = ContentState(loading = true)
            try {
                val url = repository.playUrl(bvid, cid, quality, forceDash)
                if (request == playRequest && detailRequest == detailsRequest && bvid == currentBvid && cid == currentCid) _playUrl.value = ContentState(value = url)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (request == playRequest && detailRequest == detailsRequest && bvid == currentBvid && cid == currentCid) _playUrl.value = ContentState(error = error.userMessage())
            }
        }
    }

    fun fallbackStream(bvid: String, cid: Long, quality: Int, failedDash: Boolean) {
        if (!fallbackAttempts.add(bvid to cid)) return
        loadStream(bvid, cid, if (failedDash) minOf(quality, 64) else quality, forceDash = !failedDash, resetFallback = false)
    }

    private fun loadRelated(bvid: String) {
        relatedJob?.cancel()
        val request = detailsRequest
        relatedJob = viewModelScope.launch {
        try { val result = repository.related(bvid); if (request == detailsRequest) _related.value = ContentState(value = result) }
        catch (error: CancellationException) { throw error }
        catch (error: Throwable) { if (request == detailsRequest) _related.value = ContentState(error = error.userMessage()) }
        }
    }

    fun loadComments(reset: Boolean = false) {
        if (commentsJob?.isActive == true && !reset) return
        if (reset) commentsJob?.cancel()
        val aid = currentAid
        val request = detailsRequest
        commentsJob = viewModelScope.launch {
        if (currentAid == 0L || (_comments.value.loading && !reset)) return@launch
        val page = if (reset) 1 else commentPage + 1
        _comments.value = _comments.value.copy(loading = true, error = null)
        try {
            val result = repository.comments(aid, page).replies.orEmpty()
            if (request != detailsRequest || aid != currentAid) return@launch
            val existing = if (reset) emptyList() else _comments.value.value.orEmpty()
            _comments.value = ContentState(value = existing + result)
            commentPage = page
        } catch (error: CancellationException) { throw error }
        catch (error: Throwable) { if (request == detailsRequest && aid == currentAid) _comments.value = _comments.value.copy(loading = false, error = error.userMessage()) }
        }
    }

    fun refreshProfile() {
        profileJob?.cancel()
        profileJob = viewModelScope.launch {
        _profile.value = ContentState(value = _profile.value.value, loading = true)
        try {
            val profile = repository.profile()
            _profile.value = ContentState(value = profile)
            if (profile.isLogin) loadProfileSections(profile.mid)
            if (profile.isLogin) loadDynamics()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            _profile.value = ContentState(error = error.userMessage())
        }
        }
    }

    private fun loadProfileSections(mid: Long) {
        fun <T> load(target: MutableStateFlow<ContentState<List<T>>>, block: suspend () -> List<T>) = viewModelScope.launch {
            target.value = ContentState(loading = true)
            try { target.value = ContentState(value = block()) }
            catch (error: CancellationException) { throw error }
            catch (error: Throwable) { target.value = ContentState(error = error.userMessage()) }
        }
        accountJobs += load(_history) { repository.history() }
        accountJobs += load(_watchLater) { repository.watchLaterPreview() }
        accountJobs += load(_favorites) { repository.favoriteFolders(mid) }
    }

    fun refreshFavorites() {
        val mid = _profile.value.value?.takeIf(NavData::isLogin)?.mid ?: return
        accountJobs += viewModelScope.launch {
            _favorites.value = ContentState(value = _favorites.value.value, loading = true)
            try { _favorites.value = ContentState(repository.favoriteFolders(mid)) }
            catch (error: CancellationException) { throw error }
            catch (error: Throwable) { _favorites.value = ContentState(value = _favorites.value.value, error = error.userMessage()) }
        }
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
        cancelDetailJobs()
        accountJobs.forEach { it.cancel() }; accountJobs.clear()
        detailsJob?.cancel(); playJob?.cancel(); dynamicsJob?.cancel(); interactionJob?.cancel(); profileJob?.cancel()
        detailsRequest++; playRequest++
        repository.logout()
        _history.value = ContentState()
        _watchLater.value = ContentState()
        _favorites.value = ContentState()
        _dynamics.value = ContentState()
        _interaction.value = ContentState()
        _replies.value = emptyMap()
        _danmaku.value = ContentState()
        _profile.value = ContentState(loading = true)
        currentAid = 0; currentBvid = ""; currentCid = 0
        dismissLogin()
        refreshProfile()
    }

    fun playbackHeaders() = repository.playbackHeaders()

    private fun cancelDetailJobs() {
        relatedJob?.cancel(); commentsJob?.cancel(); danmakuJob?.cancel(); interactionJob?.cancel()
        replyJobs.values.forEach { it.cancel() }; replyJobs.clear()
    }

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
