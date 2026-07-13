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
import dev.opencode.bilimobile.data.DanmakuResult
import dev.opencode.bilimobile.data.FavoriteResource
import dev.opencode.bilimobile.data.DynamicVideo
import dev.opencode.bilimobile.data.DynamicPage
import dev.opencode.bilimobile.data.InteractionState
import dev.opencode.bilimobile.data.AmbiguousWriteException
import dev.opencode.bilimobile.data.ApiBusinessException
import dev.opencode.bilimobile.data.CaptchaParameters
import dev.opencode.bilimobile.data.SmsLoginState
import dev.opencode.bilimobile.data.LiveRoomSummary
import dev.opencode.bilimobile.data.LiveRoomDetail
import dev.opencode.bilimobile.data.LivePlayInfo
import dev.opencode.bilimobile.data.LiveMessage
import dev.opencode.bilimobile.data.CommentMember
import dev.opencode.bilimobile.data.CommentContent
import dev.opencode.bilimobile.data.Danmaku
import dev.opencode.bilimobile.data.HotSearchItem
import dev.opencode.bilimobile.data.UpProfile
import dev.opencode.bilimobile.data.ShortInteractionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

data class ContentState<T>(
    val value: T? = null,
    val loading: Boolean = false,
    val error: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = BiliRepository(application)
    private val searchPreferences = application.getSharedPreferences("search_history", 0)
    private val _popular = MutableStateFlow(ContentState<List<Video>>(loading = true))
    val popular = _popular.asStateFlow()
    val channels = listOf(Channel("推荐"), Channel("短视频", short = true), Channel("热门", popular = true), Channel("直播", live = true), Channel("动画", 1), Channel("游戏", 4), Channel("知识", 36), Channel("科技", 188), Channel("生活", 160))
    private val _channel = MutableStateFlow(channels.first())
    val channel = _channel.asStateFlow()
    private val _search = MutableStateFlow(ContentState<List<Video>>(value = emptyList()))
    val search = _search.asStateFlow()
    private val _hotSearch = MutableStateFlow(ContentState<List<HotSearchItem>>(loading = true))
    val hotSearch = _hotSearch.asStateFlow()
    private val _searchHistory = MutableStateFlow(searchPreferences.getString("items", "").orEmpty().split('\u001f').filter(String::isNotBlank))
    val searchHistory = _searchHistory.asStateFlow()
    private val _upProfile = MutableStateFlow(ContentState<UpProfile>())
    val upProfile = _upProfile.asStateFlow()
    private val _upVideos = MutableStateFlow(ContentState<List<Video>>())
    val upVideos = _upVideos.asStateFlow()
    private val _upDynamics = MutableStateFlow(ContentState<List<DynamicVideo>>())
    val upDynamics = _upDynamics.asStateFlow()
    private val _upDynamicHasMore = MutableStateFlow(false)
    val upDynamicHasMore = _upDynamicHasMore.asStateFlow()
    private val _upFollowPosting = MutableStateFlow(ContentState<Unit>())
    val upFollowPosting = _upFollowPosting.asStateFlow()
    private val _shortDetail = MutableStateFlow(ContentState<Video>())
    val shortDetail = _shortDetail.asStateFlow()
    private val _shortPlay = MutableStateFlow(ContentState<PlayResult>())
    val shortPlay = _shortPlay.asStateFlow()
    private val _shortInteraction = MutableStateFlow(ContentState<ShortInteractionState>())
    val shortInteraction = _shortInteraction.asStateFlow()
    private val _shortComments = MutableStateFlow(ContentState<List<Comment>>())
    val shortComments = _shortComments.asStateFlow()
    private val _shortCommentsHasMore = MutableStateFlow(false)
    val shortCommentsHasMore = _shortCommentsHasMore.asStateFlow()
    private val _shortCommentPosting = MutableStateFlow(ContentState<Unit>())
    val shortCommentPosting = _shortCommentPosting.asStateFlow()
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
    private val _danmaku = MutableStateFlow(ContentState<DanmakuResult>())
    val danmaku = _danmaku.asStateFlow()
    private val _favoriteResources = MutableStateFlow(ContentState<List<FavoriteResource>>())
    val favoriteResources = _favoriteResources.asStateFlow()
    private val _commentPosting = MutableStateFlow(ContentState<Unit>())
    val commentPosting = _commentPosting.asStateFlow()
    private val _danmakuPosting = MutableStateFlow(ContentState<Unit>())
    val danmakuPosting = _danmakuPosting.asStateFlow()
    private val _liveRooms = MutableStateFlow(ContentState<List<LiveRoomSummary>>())
    val liveRooms = _liveRooms.asStateFlow()
    private val _liveDetail = MutableStateFlow(ContentState<LiveRoomDetail>())
    val liveDetail = _liveDetail.asStateFlow()
    private val _livePlay = MutableStateFlow(ContentState<LivePlayInfo>())
    val livePlay = _livePlay.asStateFlow()
    private val _liveMessages = MutableStateFlow(ContentState<List<LiveMessage>>(value = emptyList()))
    val liveMessages = _liveMessages.asStateFlow()
    private val _livePosting = MutableStateFlow(ContentState<Unit>())
    val livePosting = _livePosting.asStateFlow()
    private val _pinnedOwnComment = MutableStateFlow<Comment?>(null)
    val pinnedOwnComment = _pinnedOwnComment.asStateFlow()
    private val _login = MutableStateFlow<LoginState>(LoginState.Idle)
    val login = _login.asStateFlow()
    private val _smsLogin = MutableStateFlow<SmsLoginState>(SmsLoginState.Idle)
    val smsLogin = _smsLogin.asStateFlow()
    private var loginJob: Job? = null
    private var profileJob: Job? = null
    private var searchJob: Job? = null
    private var detailsJob: Job? = null
    private var playJob: Job? = null
    private var channelJob: Job? = null
    private var dynamicsJob: Job? = null
    private var liveJob: Job? = null
    private var relatedJob: Job? = null
    private var commentsJob: Job? = null
    private var danmakuJob: Job? = null
    private var interactionJob: Job? = null
    private var favoriteJob: Job? = null
    private var commentPostingJob: Job? = null
    private var danmakuPostingJob: Job? = null
    private var smsJob: Job? = null
    private var upSpaceJob: Job? = null
    private var upDynamicJob: Job? = null
    private var shortJob: Job? = null
    private var followJob: Job? = null
    private var shortInteractionReadJob: Job? = null
    private var shortMutationJob: Job? = null
    private var shortCommentJob: Job? = null
    private var shortCommentPostingJob: Job? = null
    private val replyJobs = mutableMapOf<Long, Job>()
    private val accountJobs = mutableListOf<Job>()
    private var searchRequest = 0
    private var detailsRequest = 0
    private var playRequest = 0
    private var commentPage = 1
    private var favoritePage = 0
    private var favoriteMediaId = 0L
    private var favoriteGeneration = 0
    private val _favoriteHasMore = MutableStateFlow(true)
    val favoriteHasMore = _favoriteHasMore.asStateFlow()
    private var blockedCommentDetail = -1
    private var blockedDanmakuDetail = -1
    private var currentAid = 0L
    private var currentBvid = ""
    private var currentCid = 0L
    private val channelCache = mutableMapOf<Channel, List<Video>>()
    private val channelPages = mutableMapOf<Channel, Int>()
    private val fallbackAttempts = mutableSetOf<Pair<String, Long>>()
    private var smsCooldownUntilMillis = 0L
    private var dynamicsLoadedAt = 0L
    private var currentUpMid = 0L
    private var upDynamicOffset = ""
    private var shortRequest = 0
    private var blockedShortCommentAid = 0L
    private var shortCommentPage = 0
    private val shortCache = object : LinkedHashMap<String, Pair<Video, PlayResult>>(4, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Video, PlayResult>>?) = size > 3
    }
    private var localCommentId = -1L
    private val localDanmaku = mutableMapOf<Long, MutableList<Danmaku>>()

    init {
        refreshPopular()
        refreshProfile()
        loadHotSearch()
    }

    fun refreshPopular(force: Boolean = false) {
        if (_channel.value.live) { refreshLiveRooms(); return }
        channelJob?.cancel()
        val selected = _channel.value
        val page = if (force && selected.tid == null) (channelPages[selected] ?: 1) % 5 + 1 else channelPages[selected] ?: 1
        channelJob = viewModelScope.launch {
        _popular.value = ContentState(value = channelCache[selected], loading = true)
        try {
            val result = repository.channel(selected, page)
            channelCache[selected] = result
            channelPages[selected] = page
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
        _popular.value = ContentState(value = channelCache[value], loading = !value.live)
        refreshPopular()
    }

    fun refreshLiveRooms() {
        liveJob?.cancel()
        liveJob = viewModelScope.launch {
            _liveRooms.value = _liveRooms.value.copy(loading = true, error = null)
            try { _liveRooms.value = ContentState(repository.liveRooms()) }
            catch (error: CancellationException) { throw error }
            catch (error: Throwable) { _liveRooms.value = _liveRooms.value.copy(loading = false, error = error.userMessage()) }
        }
    }

    fun loadDynamics(force: Boolean = true) {
        dynamicsJob?.cancel()
        dynamicsJob = viewModelScope.launch {
        _dynamics.value = ContentState(value = _dynamics.value.value, loading = true)
        try { _dynamics.value = ContentState(repository.dynamics(force)); dynamicsLoadedAt = System.currentTimeMillis() }
        catch (error: CancellationException) { throw error }
        catch (error: Throwable) { _dynamics.value = ContentState(value = _dynamics.value.value, error = error.userMessage()) }
        }
    }

    fun refreshDynamicsIfStale() { if (System.currentTimeMillis() - dynamicsLoadedAt > 60_000) loadDynamics(force = false) }

    fun loadLiveRoom(roomId: Long, quality: Int = 10000) {
        liveJob?.cancel()
        liveJob = viewModelScope.launch {
            _liveDetail.value = ContentState(loading = true); _livePlay.value = ContentState(loading = true)
            try {
                val detail = repository.liveDetail(roomId)
                _liveDetail.value = ContentState(detail)
                _livePlay.value = ContentState(repository.livePlayInfo(detail.roomId, quality))
                while (true) {
                    runCatching { repository.liveHistory(detail.roomId) }.onSuccess { incoming ->
                        val merged = (_liveMessages.value.value.orEmpty() + incoming).distinctBy { it.id }.takeLast(100)
                        _liveMessages.value = ContentState(merged)
                    }.onFailure { _liveMessages.value = _liveMessages.value.copy(error = it.userMessage()) }
                    delay(2_000)
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Throwable) { _liveDetail.value = ContentState(error = error.userMessage()); _livePlay.value = ContentState(error = error.userMessage()) }
        }
    }

    fun closeLiveRoom() { liveJob?.cancel(); _liveDetail.value = ContentState(); _livePlay.value = ContentState(); _liveMessages.value = ContentState(emptyList()); _livePosting.value = ContentState() }
    fun sendLiveMessage(message: String, finished: (Boolean) -> Unit) {
        val roomId = _liveDetail.value.value?.roomId ?: return
        val text = message.trim().take(100); if (text.isBlank() || _livePosting.value.loading) return
        viewModelScope.launch {
            _livePosting.value = ContentState(loading = true)
            try {
                repository.sendLiveMessage(roomId, text)
                val user = _profile.value.value
                val local = LiveMessage("local:${System.currentTimeMillis()}", user?.uname.orEmpty(), text, System.currentTimeMillis())
                _liveMessages.value = ContentState((_liveMessages.value.value.orEmpty() + local).distinctBy { it.id }.takeLast(100))
                _livePosting.value = ContentState(Unit); finished(true)
            }
            catch (error: Throwable) { _livePosting.value = ContentState(error = if (error is AmbiguousWriteException) "发送结果未知，请勿重复发送" else error.userMessage()); finished(false) }
        }
    }

    fun search(query: String) {
        if (query.isBlank()) return
        val normalized = query.trim()
        val history = (listOf(normalized) + _searchHistory.value.filterNot { it == normalized }).take(12)
        _searchHistory.value = history
        searchPreferences.edit().putString("items", history.joinToString("\u001f")).apply()
        searchJob?.cancel()
        val request = ++searchRequest
        searchJob = viewModelScope.launch {
            _search.value = ContentState(value = _search.value.value, loading = true)
            try {
                val results = repository.search(normalized)
                if (request == searchRequest) _search.value = ContentState(value = results)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (request == searchRequest) _search.value = ContentState(error = error.userMessage())
            }
        }
    }

    fun clearSearchHistory() { _searchHistory.value = emptyList(); searchPreferences.edit().remove("items").apply() }

    fun loadHotSearch() {
        viewModelScope.launch {
            _hotSearch.value = _hotSearch.value.copy(loading = true, error = null)
            try { _hotSearch.value = ContentState(repository.hotSearch()) }
            catch (error: Throwable) { _hotSearch.value = ContentState(error = error.userMessage()) }
        }
    }

    fun loadUpSpace(mid: Long) {
        upSpaceJob?.cancel()
        currentUpMid = mid
        _upProfile.value = ContentState(loading = true); _upVideos.value = ContentState(loading = true)
        loadUpDynamics(mid, reset = true)
        upSpaceJob = viewModelScope.launch {
            supervisorScope {
                launch {
                    try { val value = repository.upProfile(mid); if (mid == currentUpMid) _upProfile.value = ContentState(value) }
                    catch (error: CancellationException) { throw error }
                    catch (error: Throwable) { if (mid == currentUpMid) _upProfile.value = ContentState(error = error.userMessage()) }
                }
                launch {
                    try { val value = repository.upArchives(mid); if (mid == currentUpMid) _upVideos.value = ContentState(value) }
                    catch (error: CancellationException) { throw error }
                    catch (error: Throwable) { if (mid == currentUpMid) _upVideos.value = ContentState(error = error.userMessage()) }
                }
            }
        }
    }

    fun loadUpDynamics(mid: Long = currentUpMid, reset: Boolean = false) {
        if (mid <= 0 || (upDynamicJob?.isActive == true && !reset)) return
        if (reset || mid != currentUpMid) {
            upDynamicJob?.cancel(); currentUpMid = mid; upDynamicOffset = ""; _upDynamicHasMore.value = true
            _upDynamics.value = ContentState(loading = true)
        } else if (!_upDynamicHasMore.value) return
        val requestedOffset = if (reset) "" else upDynamicOffset
        upDynamicJob = viewModelScope.launch {
            _upDynamics.value = _upDynamics.value.copy(loading = true, error = null)
            try {
                val page: DynamicPage = repository.upDynamics(mid, requestedOffset)
                if (mid != currentUpMid) return@launch
                val old = if (requestedOffset.isBlank()) emptyList() else _upDynamics.value.value.orEmpty()
                _upDynamics.value = ContentState((old + page.items).distinctBy(DynamicVideo::id))
                upDynamicOffset = page.offset; _upDynamicHasMore.value = page.hasMore && page.offset.isNotBlank()
            } catch (error: CancellationException) { throw error }
            catch (error: Throwable) { if (mid == currentUpMid) _upDynamics.value = _upDynamics.value.copy(loading = false, error = error.userMessage()) }
        }
    }

    fun toggleFollowing(mid: Long) {
        val old = _upProfile.value.value ?: return
        if (old.mid != mid || followJob?.isActive == true || _profile.value.value?.isLogin != true) return
        val desired = !old.following
        followJob = viewModelScope.launch {
            _upProfile.value = ContentState(old.copy(following = desired))
            _upFollowPosting.value = ContentState(loading = true)
            try {
                repository.setFollowing(mid, desired)
                if (mid == currentUpMid) _upFollowPosting.value = ContentState(Unit)
            } catch (error: CancellationException) { _upProfile.value = ContentState(old); throw error }
            catch (error: Throwable) {
                if (mid == currentUpMid) { _upProfile.value = ContentState(old); _upFollowPosting.value = ContentState(error = error.userMessage()) }
            }
        }
    }

    fun loadShortVideo(video: Video, next: Video? = null) {
        shortJob?.cancel()
        shortInteractionReadJob?.cancel(); shortCommentJob?.cancel()
        val request = ++shortRequest
        shortCache[video.bvid]?.let { cached ->
            _shortDetail.value = ContentState(cached.first); _shortPlay.value = ContentState(cached.second)
        } ?: run { _shortDetail.value = ContentState(loading = true); _shortPlay.value = ContentState(loading = true) }
        _shortInteraction.value = ContentState(); _shortComments.value = ContentState(); _shortCommentsHasMore.value = false; shortCommentPage = 0; _shortCommentPosting.value = ContentState()
        shortJob = viewModelScope.launch {
            try {
                val current = shortCache[video.bvid] ?: loadShort(video)
                shortCache[video.bvid] = current
                if (request == shortRequest) {
                    _shortDetail.value = ContentState(current.first); _shortPlay.value = ContentState(current.second)
                    if (blockedShortCommentAid == current.first.aid) _shortCommentPosting.value = ContentState(error = "发送结果未知，请刷新确认")
                    if (_profile.value.value?.isLogin == true) loadShortInteraction(current.first.aid, request)
                }
                if (next != null && next.bvid !in shortCache) runCatching { loadShort(next) }.getOrNull()?.let { shortCache[next.bvid] = it }
            } catch (error: CancellationException) { throw error }
            catch (error: Throwable) { if (request == shortRequest) { _shortDetail.value = ContentState(error = error.userMessage()); _shortPlay.value = ContentState(error = error.userMessage()) } }
        }
    }

    private fun loadShortInteraction(aid: Long, request: Int = shortRequest) {
        shortInteractionReadJob?.cancel()
        shortInteractionReadJob = viewModelScope.launch {
            _shortInteraction.value = ContentState(loading = true)
            try { val value = repository.shortInteraction(aid); if (request == shortRequest) _shortInteraction.value = ContentState(value) }
            catch (error: CancellationException) { throw error }
            catch (error: Throwable) { if (request == shortRequest) _shortInteraction.value = ContentState(error = error.userMessage()) }
        }
    }

    fun refreshShortInteraction() {
        val aid = _shortDetail.value.value?.aid ?: return
        loadShortInteraction(aid, shortRequest)
    }

    fun loadShortComments(reset: Boolean = true) {
        val aid = _shortDetail.value.value?.aid ?: return
        loadShortComments(aid, shortRequest, reset)
    }

    private fun loadShortComments(aid: Long, request: Int, reset: Boolean = true, clearBlockedOnSuccess: Boolean = false) {
        if (shortCommentJob?.isActive == true && !reset) return
        shortCommentJob?.cancel()
        shortCommentJob = viewModelScope.launch {
            val page = if (reset) 1 else shortCommentPage + 1
            _shortComments.value = _shortComments.value.copy(loading = true, error = null)
            try {
                val response = repository.comments(aid, page)
                if (request == shortRequest) {
                    val old = if (reset) emptyList() else _shortComments.value.value.orEmpty()
                    val merged = (old + response.replies.orEmpty()).distinctBy(Comment::rpid)
                    _shortComments.value = ContentState(merged); shortCommentPage = page
                    _shortCommentsHasMore.value = merged.size < response.page.count && response.replies.orEmpty().isNotEmpty()
                    if (clearBlockedOnSuccess && blockedShortCommentAid == aid) { blockedShortCommentAid = 0; _shortCommentPosting.value = ContentState() }
                }
            }
            catch (error: CancellationException) { throw error }
            catch (error: Throwable) { if (request == shortRequest) _shortComments.value = _shortComments.value.copy(loading = false, error = error.userMessage()) }
        }
    }

    fun toggleShortLike() {
        val video = _shortDetail.value.value ?: return
        val old = _shortInteraction.value.value ?: return
        val liked = old.liked ?: return
        if (shortMutationJob?.isActive == true) return
        val request = shortRequest
        shortMutationJob = viewModelScope.launch {
            _shortInteraction.value = ContentState(old.copy(liked = !liked), loading = true)
            try { repository.setLike(video.aid, !liked); if (request == shortRequest) _shortInteraction.value = ContentState(repository.shortInteraction(video.aid)) }
            catch (error: CancellationException) { throw error }
            catch (error: Throwable) { if (request == shortRequest) _shortInteraction.value = ContentState(old, error = error.userMessage()) }
        }
    }

    fun addShortCoin(count: Int, like: Boolean, finished: (Boolean) -> Unit) {
        val video = _shortDetail.value.value ?: return
        val old = _shortInteraction.value.value ?: return
        if (shortMutationJob?.isActive == true) return
        val request = shortRequest
        shortMutationJob = viewModelScope.launch {
            _shortInteraction.value = ContentState(old, loading = true)
            try {
                repository.addCoin(video.aid, video.bvid, count, like)
                if (request == shortRequest) {
                    val optimistic = old.copy(coinCount = (old.coinCount ?: 0) + count, liked = if (like) true else old.liked)
                    val reconciled = runCatching { repository.shortInteraction(video.aid) }.getOrNull()?.takeIf { it.coinCount != null }
                    _shortInteraction.value = ContentState(reconciled ?: optimistic, error = if (reconciled == null) "投币成功，状态稍后同步" else null)
                    finished(true)
                }
            }
            catch (error: CancellationException) { throw error }
            catch (error: AmbiguousWriteException) {
                if (request == shortRequest) {
                    val expected = (old.coinCount ?: 0) + count
                    val reconciled = runCatching { repository.shortInteraction(video.aid) }.getOrNull()
                    val confirmed = reconciled?.coinCount?.let { it >= expected } == true
                    _shortInteraction.value = if (confirmed) ContentState(reconciled!!, error = "操作结果已重新同步") else ContentState(old, error = "操作结果未知，请刷新确认")
                    finished(confirmed)
                }
            }
            catch (error: Throwable) { if (request == shortRequest) { _shortInteraction.value = ContentState(old, error = error.userMessage()); finished(false) } }
        }
    }

    fun postShortComment(message: String, finished: (Boolean) -> Unit) {
        val video = _shortDetail.value.value ?: return
        val text = message.trim()
        if (text.length !in 1..1000 || _shortCommentPosting.value.loading || blockedShortCommentAid == video.aid) return
        val request = shortRequest
        shortCommentPostingJob = viewModelScope.launch {
            _shortCommentPosting.value = ContentState(loading = true)
            try { repository.postComment(video.aid, text); if (request == shortRequest) { _shortCommentPosting.value = ContentState(Unit); loadShortComments(video.aid, request, reset = true); finished(true) } }
            catch (error: CancellationException) { throw error }
            catch (error: Throwable) { if (request == shortRequest) { if (error is AmbiguousWriteException) blockedShortCommentAid = video.aid; _shortCommentPosting.value = ContentState(error = if (error is AmbiguousWriteException) "发送结果未知，请刷新确认" else error.userMessage()); finished(false) } }
        }
    }

    fun confirmShortCommentRefresh() {
        val aid = _shortDetail.value.value?.aid ?: return
        loadShortComments(aid, shortRequest, reset = true, clearBlockedOnSuccess = true)
    }

    private suspend fun loadShort(video: Video): Pair<Video, PlayResult> {
        val detail = repository.details(video.bvid)
        val cid = detail.cid.takeIf { it > 0 } ?: detail.pages.firstOrNull()?.cid ?: error("当前短视频无法播放")
        return detail to repository.playUrl(detail.bvid, cid, 64)
    }

    fun closeShortVideos() {
        shortJob?.cancel(); shortInteractionReadJob?.cancel(); shortCommentJob?.cancel(); shortRequest++; _shortDetail.value = ContentState(); _shortPlay.value = ContentState(); _shortInteraction.value = ContentState(); _shortComments.value = ContentState(); _shortCommentsHasMore.value = false; shortCommentPage = 0; _shortCommentPosting.value = ContentState(); blockedShortCommentAid = 0
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
            _commentPosting.value = ContentState()
            _danmakuPosting.value = ContentState()
            _pinnedOwnComment.value = null; localDanmaku.clear()
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
        _danmaku.value = ContentState(loading = cid > 0)
        val request = detailsRequest
        danmakuJob = viewModelScope.launch {
        if (cid <= 0) return@launch
        try { val result = repository.danmaku(cid); if (request == detailsRequest && cid == currentCid) _danmaku.value = ContentState(result.copy(items = (result.items + (localDanmaku[cid] ?: emptyList())).distinctBy { Triple(it.time, it.mode, it.text) }.sortedBy { it.time })) }
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
        try { val result = repository.interaction(aid, _profile.value.value?.mid ?: error("请先登录后再试")); if (request == detailsRequest && aid == currentAid) _interaction.value = ContentState(result) }
        catch (error: Throwable) { if (request == detailsRequest && aid == currentAid) _interaction.value = ContentState(error = error.userMessage()) }
        }
    }

    fun toggleLike(aid: Long) = updateInteraction { state -> val current = state.liked ?: return@updateInteraction state; repository.setLike(aid, !current); repository.interaction(aid, _profile.value.value?.mid ?: error("请先登录后再试")) }
    fun toggleWatchLater(aid: Long) = updateInteraction { state -> val current = state.watchLater ?: return@updateInteraction state; repository.setWatchLater(aid, !current); repository.interaction(aid, _profile.value.value?.mid ?: error("请先登录后再试")) }
    fun toggleFavorite(aid: Long, folderId: Long) = updateInteraction { state ->
        val folders = state.favoriteFolderIds ?: return@updateInteraction state
        repository.setFavorite(aid, folderId, folderId !in folders); repository.interaction(aid, _profile.value.value?.mid ?: error("请先登录后再试"))
    }
    fun addCoin(aid: Long, bvid: String, count: Int, like: Boolean) = updateInteraction { state ->
        repository.addCoin(aid, bvid, count, like); repository.interaction(aid, _profile.value.value?.mid ?: error("请先登录后再试"))
    }

    fun postComment(message: String, finished: (Boolean) -> Unit) {
        val normalized = message.trim()
        if (_commentPosting.value.loading || blockedCommentDetail == detailsRequest || normalized.length !in 1..1000) return
        val aid = currentAid; val request = detailsRequest
        commentPostingJob = viewModelScope.launch {
            _commentPosting.value = ContentState(loading = true)
            try {
                repository.postComment(aid, normalized)
                if (request == detailsRequest && aid == currentAid) {
                    val user = _profile.value.value
                    _pinnedOwnComment.value = Comment(localCommentId--, System.currentTimeMillis() / 1000, member = CommentMember(user?.mid.toString(), user?.uname.orEmpty(), user?.face.orEmpty()), content = CommentContent(normalized))
                    _commentPosting.value = ContentState(Unit); loadComments(reset = true); finished(true)
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Throwable) { if (request == detailsRequest && aid == currentAid) { if (error is AmbiguousWriteException) blockedCommentDetail = request; _commentPosting.value = ContentState(error = if (error is AmbiguousWriteException) "可能已发送，请刷新确认，勿重复发送" else error.userMessage()); finished(false) } }
        }
    }

    fun postDanmaku(message: String, progress: Long, finished: (Boolean) -> Unit) {
        val normalized = message.trim()
        if (_danmakuPosting.value.loading || blockedDanmakuDetail == detailsRequest || normalized.length !in 1..100 || currentCid <= 0) return
        val cid = currentCid; val request = detailsRequest
        danmakuPostingJob = viewModelScope.launch {
            _danmakuPosting.value = ContentState(loading = true)
            try {
                repository.postDanmaku(cid, currentBvid, currentAid, normalized, progress)
                if (request == detailsRequest && cid == currentCid) { localDanmaku.getOrPut(cid) { mutableListOf() } += Danmaku(progress / 1000f, 1, 0xffffff, normalized); _danmakuPosting.value = ContentState(Unit); loadDanmaku(cid); finished(true) }
            } catch (error: CancellationException) { throw error }
            catch (error: Throwable) { if (request == detailsRequest && cid == currentCid) { if (error is AmbiguousWriteException) blockedDanmakuDetail = request; _danmakuPosting.value = ContentState(error = if (error is AmbiguousWriteException) "可能已发送，请刷新确认，勿重复发送" else error.userMessage()); finished(false) } }
        }
    }

    private fun updateInteraction(action: suspend (InteractionState) -> InteractionState) {
        if (interactionJob?.isActive == true) return
        val aid = currentAid
        val request = detailsRequest
        interactionJob = viewModelScope.launch {
        val old = _interaction.value.value ?: run { _interaction.value = ContentState(error = "请先登录后再试"); return@launch }
        _interaction.value = ContentState(old, loading = true)
        try { val result = action(old); if (request == detailsRequest && aid == currentAid) _interaction.value = ContentState(result) }
        catch (error: AmbiguousWriteException) {
            if (request == detailsRequest && aid == currentAid) {
                val reconciled = runCatching { repository.interaction(aid, _profile.value.value?.mid ?: return@runCatching null) }.getOrNull()
                _interaction.value = if (reconciled != null) ContentState(reconciled, error = "操作结果已重新同步")
                else ContentState(old, error = "操作结果未知，请刷新确认")
            }
        }
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
            val own = _pinnedOwnComment.value
            if (own != null && result.any { it.member.mid == own.member.mid && it.content.message == own.content.message }) _pinnedOwnComment.value = null
            val existing = if (reset) emptyList() else _comments.value.value.orEmpty()
            _comments.value = ContentState(value = (existing + result).distinctBy { it.rpid })
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
        accountJobs += load(_watchLater) { repository.watchLater() }
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

    fun refreshHistory() = refreshAccount(_history) { repository.history() }
    fun refreshWatchLater() = refreshAccount(_watchLater) { repository.watchLater() }
    private fun <T> refreshAccount(target: MutableStateFlow<ContentState<List<T>>>, block: suspend () -> List<T>) {
        accountJobs.forEach { it.cancel() }; accountJobs.clear()
        accountJobs += viewModelScope.launch { target.value = target.value.copy(loading = true, error = null); try { target.value = ContentState(block()) } catch (error: CancellationException) { throw error } catch (error: Throwable) { target.value = target.value.copy(loading = false, error = error.userMessage()) } }
    }

    fun loadFavoriteResources(mediaId: Long, reset: Boolean = false) {
        if (favoriteJob?.isActive == true && !reset) return
        if (!reset && mediaId == favoriteMediaId && !_favoriteHasMore.value) return
        if (reset || mediaId != favoriteMediaId) { favoriteJob?.cancel(); favoriteGeneration++; favoriteMediaId = mediaId; favoritePage = 0; _favoriteHasMore.value = true; _favoriteResources.value = ContentState(loading = true) }
        val generation = favoriteGeneration
        favoriteJob = viewModelScope.launch {
            val page = favoritePage + 1
            _favoriteResources.value = _favoriteResources.value.copy(loading = true, error = null)
            try {
                val data = repository.favoriteResources(mediaId, page)
                if (mediaId != favoriteMediaId || generation != favoriteGeneration) return@launch
                val old = if (page == 1) emptyList() else _favoriteResources.value.value.orEmpty()
                val merged = (old + data.medias.orEmpty().filter { it.id > 0 && it.bvid.isNotBlank() }).distinctBy { it.id to it.bvid }
                _favoriteResources.value = ContentState(merged)
                favoritePage = page; _favoriteHasMore.value = data.has_more
            } catch (error: CancellationException) { throw error }
            catch (error: Throwable) { if (mediaId == favoriteMediaId) _favoriteResources.value = _favoriteResources.value.copy(loading = false, error = error.userMessage()) }
        }
    }

    fun beginSmsCaptcha() {
        if (smsJob?.isActive == true) return
        if (System.currentTimeMillis() < smsCooldownUntilMillis) {
            val sent = _smsLogin.value as? SmsLoginState.CodeSent
            if (sent != null) _smsLogin.value = sent.copy(error = "请等待倒计时结束后再获取验证码")
            return
        }
        loginJob?.cancel()
        smsJob = viewModelScope.launch {
            _smsLogin.value = SmsLoginState.CaptchaLoading
            try { _smsLogin.value = SmsLoginState.CaptchaReady(repository.captcha()) }
            catch (error: CancellationException) { throw error }
            catch (error: Throwable) { _smsLogin.value = SmsLoginState.Failed(error.userMessage(), error.isSmsRisk()) }
        }
    }

    fun sendSms(phone: String, captcha: CaptchaParameters, validate: String, seccode: String) {
        if (System.currentTimeMillis() < smsCooldownUntilMillis) return
        if (smsJob?.isActive == true || !phone.matches(Regex("1[3-9]\\d{9}"))) { if (!phone.matches(Regex("1[3-9]\\d{9}"))) _smsLogin.value = SmsLoginState.Failed("请输入有效的中国大陆 11 位手机号", false); return }
        smsJob = viewModelScope.launch {
            _smsLogin.value = SmsLoginState.Sending
            try { val key = repository.sendSms(phone, captcha, validate, seccode); smsCooldownUntilMillis = System.currentTimeMillis() + 60_000; _smsLogin.value = SmsLoginState.CodeSent(key, phone.take(3) + "****" + phone.takeLast(4), smsCooldownUntilMillis) }
            catch (error: CancellationException) { throw error }
            catch (error: Throwable) { _smsLogin.value = SmsLoginState.Failed(error.userMessage(), error.isSmsRisk()) }
        }
    }

    fun loginSms(phone: String, code: String, captchaKey: String) {
        if (smsJob?.isActive == true || code.isBlank()) return
        smsJob = viewModelScope.launch {
            val sent = _smsLogin.value as? SmsLoginState.CodeSent ?: return@launch
            _smsLogin.value = SmsLoginState.LoggingIn
            try { repository.loginSms(phone, code, captchaKey); _smsLogin.value = SmsLoginState.Success; refreshProfile() }
            catch (error: CancellationException) { throw error }
            catch (error: Throwable) {
                _smsLogin.value = if (error.isSmsRisk()) SmsLoginState.Failed(error.userMessage(), true)
                else sent.copy(error = error.userMessage())
            }
        }
    }

    fun fallbackSmsToQr() { smsJob?.cancel(); _smsLogin.value = SmsLoginState.Idle; beginLogin() }

    fun captchaFailed() { _smsLogin.value = SmsLoginState.Failed("验证码加载失败，请检查网络后重试", false) }

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
                    _login.value = LoginState.Error(error.userMessage(), qr); return@launch
                }
                when (status.code) {
                    0 -> {
                        val verified = runCatching { repository.profile() }.getOrNull()
                        if (verified?.isLogin != true) {
                            _login.value = LoginState.Error("扫码完成，但登录凭据校验失败，请重新生成二维码")
                            return@launch
                        }
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
        smsJob?.cancel()
        _login.value = LoginState.Idle
        _smsLogin.value = SmsLoginState.Idle
    }

    fun logout() {
        cancelDetailJobs()
        accountJobs.forEach { it.cancel() }; accountJobs.clear()
        detailsJob?.cancel(); playJob?.cancel(); dynamicsJob?.cancel(); interactionJob?.cancel(); profileJob?.cancel(); upSpaceJob?.cancel(); upDynamicJob?.cancel(); shortJob?.cancel(); followJob?.cancel()
        detailsRequest++; playRequest++
        favoriteJob?.cancel(); smsJob?.cancel(); favoriteGeneration++
        _history.value = ContentState()
        _watchLater.value = ContentState()
        _favorites.value = ContentState()
        _dynamics.value = ContentState()
        _upDynamics.value = ContentState(); _upProfile.value = ContentState(); _upVideos.value = ContentState(); _upFollowPosting.value = ContentState()
        _shortDetail.value = ContentState(); _shortPlay.value = ContentState(); shortCache.clear()
        _interaction.value = ContentState()
        _replies.value = emptyMap()
        _danmaku.value = ContentState()
        _pinnedOwnComment.value = null; localDanmaku.clear(); closeLiveRoom()
        _favoriteResources.value = ContentState(); _commentPosting.value = ContentState(); _danmakuPosting.value = ContentState()
        favoritePage = 0; favoriteMediaId = 0; _favoriteHasMore.value = true; blockedCommentDetail = -1; blockedDanmakuDetail = -1
        _profile.value = ContentState(loading = true)
        currentAid = 0; currentBvid = ""; currentCid = 0
        dismissLogin()
        viewModelScope.launch {
            val warning = runCatching { repository.logout() }.exceptionOrNull()?.let { "本地凭据已清除，但服务器退出状态不确定：${it.userMessage()}" }
            refreshProfile()
            profileJob?.join()
            if (warning != null) _profile.value = ContentState(_profile.value.value, error = warning)
        }
    }

    fun playbackHeaders() = repository.playbackHeaders()
    fun livePlaybackHeaders(roomId: Long) = repository.livePlaybackHeaders(roomId)
    fun reportWatchProgress(aid: Long, cid: Long, bvid: String, playedTime: Long, realtime: Long, startTs: Long, playType: Int) {
        if (_profile.value.value?.isLogin != true) return
        viewModelScope.launch { runCatching { repository.reportHeartbeat(aid, cid, bvid, playedTime, realtime, startTs, playType) } }
    }

    private fun cancelDetailJobs() {
        relatedJob?.cancel(); commentsJob?.cancel(); danmakuJob?.cancel(); interactionJob?.cancel(); commentPostingJob?.cancel(); danmakuPostingJob?.cancel()
        replyJobs.values.forEach { it.cancel() }; replyJobs.clear()
    }

    fun clearCommentPostingAfterRefresh() {
        loadComments(reset = true)
        blockedCommentDetail = -1
        _commentPosting.value = ContentState()
    }

    fun clearDanmakuPostingAfterRefresh() {
        loadDanmaku(currentCid)
        blockedDanmakuDetail = -1
        _danmakuPosting.value = ContentState()
    }

    private fun Throwable.isSmsRisk() = this is ApiBusinessException && code in setOf(-412, -403, 86038, 86090)

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
