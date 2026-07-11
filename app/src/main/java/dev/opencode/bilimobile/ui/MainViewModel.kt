package dev.opencode.bilimobile.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.opencode.bilimobile.data.BiliRepository
import dev.opencode.bilimobile.data.LoginState
import dev.opencode.bilimobile.data.NavData
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
    private val _playUrl = MutableStateFlow(ContentState<String>())
    val playUrl = _playUrl.asStateFlow()
    private val _profile = MutableStateFlow(ContentState<NavData>(loading = true))
    val profile = _profile.asStateFlow()
    private val _login = MutableStateFlow<LoginState>(LoginState.Idle)
    val login = _login.asStateFlow()
    private var loginJob: Job? = null
    private var searchJob: Job? = null
    private var detailsJob: Job? = null
    private var playJob: Job? = null
    private var searchRequest = 0
    private var detailsRequest = 0
    private var playRequest = 0

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
            try {
                val video = repository.details(bvid)
                if (request != detailsRequest) return@launch
                _details.value = ContentState(value = video)
                loadStream(video.bvid, video.cid.takeIf { it > 0 } ?: video.pages.firstOrNull()?.cid ?: 0)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (request == detailsRequest) _details.value = ContentState(error = error.userMessage())
            }
        }
    }

    private fun loadStream(bvid: String, cid: Long) {
        playJob?.cancel()
        val request = ++playRequest
        playJob = viewModelScope.launch {
            if (cid == 0L) {
                _playUrl.value = ContentState(error = "This video has no playable page")
                return@launch
            }
            _playUrl.value = ContentState(loading = true)
            try {
                val url = repository.playUrl(bvid, cid)
                if (request == playRequest) _playUrl.value = ContentState(value = url)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (request == playRequest) _playUrl.value = ContentState(error = error.userMessage())
            }
        }
    }

    fun refreshProfile() = viewModelScope.launch {
        _profile.value = ContentState(value = _profile.value.value, loading = true)
        try {
            _profile.value = ContentState(value = repository.profile())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            _profile.value = ContentState(error = error.userMessage())
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
                        _login.value = LoginState.Error("QR code expired. Generate a new one.")
                        return@launch
                    }
                    86101 -> if (_login.value !is LoginState.Scanned) _login.value = LoginState.Ready(qr)
                    else -> {
                        _login.value = LoginState.Error(status.message.ifBlank { "Login failed (${status.code})" })
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
        dismissLogin()
        refreshProfile()
    }

    fun playbackHeaders() = repository.playbackHeaders()

    private fun Throwable.userMessage() = message ?: "Something went wrong"
}
