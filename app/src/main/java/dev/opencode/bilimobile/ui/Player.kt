@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package dev.opencode.bilimobile.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultLivePlaybackSpeedControl
import androidx.media3.exoplayer.source.ConcatenatingMediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.opencode.bilimobile.data.Danmaku
import dev.opencode.bilimobile.data.DanmakuResult
import dev.opencode.bilimobile.data.PlayResult
import dev.opencode.bilimobile.data.LivePlayInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

private enum class DanmakuFont(val label: String, val sp: Int) { Small("小", 12), Medium("中", 15), Large("大", 18) }
private enum class DanmakuSpeed(val label: String, val seconds: Float) { Slow("慢", 8f), Normal("正常", 6f), Fast("快", 4f) }
private enum class DanmakuArea(val label: String, val fraction: Float) { Quarter("1/4", .25f), Half("1/2", .5f), Full("全屏", 1f) }
private data class DanmakuPlacement(val item: Danmaku, val lane: Int, val widthDp: Float, val endTime: Float)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VideoPlayer(
    key: String,
    result: PlayResult,
    headers: Map<String, String>,
    danmaku: ContentState<DanmakuResult>,
    loggedIn: Boolean,
    danmakuPosting: ContentState<Unit>,
    retryDanmaku: () -> Unit,
    confirmDanmakuAfterRefresh: () -> Unit,
    sendDanmaku: (String, Long, (Boolean) -> Unit) -> Unit,
    reportWatch: (Long, Long, Long, Int) -> Unit,
    quality: (Int) -> Unit,
    fallback: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val prefs = remember { context.getSharedPreferences("playback_positions", 0) }
    val watchStartTs = remember(key) { System.currentTimeMillis() / 1000 }
    val watchStartElapsed = remember(key) { SystemClock.elapsedRealtime() }
    val player = remember(key, result.videoUrls, result.audioUrl, headers) {
        ExoPlayer.Builder(context).build().apply {
            val factory = DefaultHttpDataSource.Factory().setDefaultRequestProperties(headers)
            val videos = result.videoUrls.map { url -> ProgressiveMediaSource.Factory(factory).createMediaSource(
                MediaItem.Builder().setUri(url).setMimeType(MimeTypes.VIDEO_MP4).build()) }
            val video = if (videos.size == 1) videos.first() else ConcatenatingMediaSource(*videos.toTypedArray())
            val source = result.audioUrl?.let {
                MergingMediaSource(video, ProgressiveMediaSource.Factory(factory).createMediaSource(
                    MediaItem.Builder().setUri(it).setMimeType(MimeTypes.AUDIO_MP4).build()))
            } ?: video
            setMediaSource(source)
            prepare()
            playWhenReady = true
        }
    }
    var position by remember { mutableLongStateOf(0L) }
    var scrubPosition by remember { mutableLongStateOf(0L) }
    var scrubbing by remember { mutableStateOf(false) }
    var showDanmaku by rememberSaveable { mutableStateOf(true) }
    var opacity by rememberSaveable { mutableFloatStateOf(.78f) }
    var font by rememberSaveable { mutableStateOf(DanmakuFont.Medium) }
    var danmakuSpeed by rememberSaveable { mutableStateOf(DanmakuSpeed.Normal) }
    var area by rememberSaveable { mutableStateOf(DanmakuArea.Half) }
    var maximumLanes by rememberSaveable { mutableIntStateOf(6) }
    var scrolling by rememberSaveable { mutableStateOf(true) }
    var topMode by rememberSaveable { mutableStateOf(true) }
    var bottomMode by rememberSaveable { mutableStateOf(true) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var speedMenu by remember { mutableStateOf(false) }
    var qualityMenu by remember { mutableStateOf(false) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var resumeAfterPause by remember { mutableStateOf(false) }
    var controls by remember { mutableStateOf(true) }
    var playing by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var composeDanmaku by remember { mutableStateOf(false) }
    var longPressSpeed by remember { mutableStateOf(false) }
    var danmakuText by rememberSaveable { mutableStateOf("") }
    var fallbackSent by remember(result.videoUrls, result.audioUrl) { mutableStateOf(false) }
    var pendingSavedSeek by remember(player, key) { mutableLongStateOf(prefs.getLong(key, 0L).coerceAtLeast(0L)) }
    val currentReportWatch by rememberUpdatedState(reportWatch)

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(value: Boolean) { playing = value }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    playbackError = null
                    if (pendingSavedSeek > 0 && player.duration > 0) {
                        player.seekTo(pendingSavedSeek.coerceAtMost(player.duration))
                        pendingSavedSeek = 0
                    }
                    if (player.duration > 0 && player.currentPosition > player.duration) player.seekTo(player.duration)
                }
                if (state == Player.STATE_ENDED) currentReportWatch(-1, (SystemClock.elapsedRealtime() - watchStartElapsed) / 1000, watchStartTs, 4)
            }
            override fun onPlayerError(error: PlaybackException) {
                playbackError = "播放源连接失败"
                buffering = false
                if (!fallbackSent) { fallbackSent = true; fallback(result.isDash) }
            }
        }
        player.addListener(listener)
        playing = player.isPlaying
        buffering = player.playbackState == Player.STATE_BUFFERING
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(player, result) { playbackError = null }
    LaunchedEffect(player, key) {
        currentReportWatch(player.currentPosition / 1000, 0, watchStartTs, 1)
        while (true) {
            delay(15_000)
            if (player.isPlaying && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) currentReportWatch(player.currentPosition / 1000, (SystemClock.elapsedRealtime() - watchStartElapsed) / 1000, watchStartTs, 0)
        }
    }
    LaunchedEffect(player) {
        while (true) {
            delay(200)
            if (!scrubbing) {
                val current = player.currentPosition
                if (current != position) position = current
            }
        }
    }
    LaunchedEffect(controls, playing, scrubbing, speedMenu, qualityMenu) {
        if (controls && playing && !scrubbing && !speedMenu && !qualityMenu) {
            delay(3_000)
            controls = false
        }
    }
    DisposableEffect(player) { onDispose { currentReportWatch(player.currentPosition / 1000, (SystemClock.elapsedRealtime() - watchStartElapsed) / 1000, watchStartTs, 4); prefs.edit().putLong(key, player.currentPosition).apply(); player.release() } }
    DisposableEffect(owner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> { currentReportWatch(player.currentPosition / 1000, (SystemClock.elapsedRealtime() - watchStartElapsed) / 1000, watchStartTs, 2); resumeAfterPause = player.playWhenReady; player.pause() }
                Lifecycle.Event.ON_RESUME -> if (resumeAfterPause) { currentReportWatch(player.currentPosition / 1000, (SystemClock.elapsedRealtime() - watchStartElapsed) / 1000, watchStartTs, 3); player.play() }
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    fun togglePlayback() {
        if (player.playbackState == Player.STATE_ENDED) {
            player.seekTo(0)
            player.play()
        } else if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    val content: @Composable () -> Unit = {
      Box(
          Modifier
              .fillMaxSize()
              .background(Color.Black)
              .pointerInput(player) {
                  detectTapGestures(
                      onTap = { controls = !controls },
                       onDoubleTap = { tap ->
                           togglePlayback()
                           controls = true
                       },
                       onLongPress = {},
                      onPress = {
                          val releasedEarly = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                              tryAwaitRelease()
                          }
                          if (releasedEarly == null) {
                              val previous = player.playbackParameters
                              longPressSpeed = true
                              player.playbackParameters = PlaybackParameters(3f)
                              try {
                                  tryAwaitRelease()
                              } finally {
                                  player.playbackParameters = previous
                                  longPressSpeed = false
                              }
                          }
                      }
                  )
              }
      ) {
        AndroidView({ PlayerView(it).apply { this.player = player; useController = false } }, Modifier.fillMaxSize(),
            onRelease = { it.player = null })
        if (showDanmaku) FrameSyncedDanmaku(player, danmaku.value?.items.orEmpty(), opacity, font, danmakuSpeed, area, maximumLanes, scrolling, topMode, bottomMode)
        if (buffering) CircularProgressIndicator(Modifier.align(Alignment.Center).size(34.dp), color = Color.White, strokeWidth = 3.dp)
        AnimatedVisibility(longPressSpeed, Modifier.align(Alignment.TopCenter).padding(top = 18.dp), enter = fadeIn(tween(100)), exit = fadeOut(tween(100))) {
            Surface(color = Color.Black.copy(.72f), shape = RoundedCornerShape(18.dp)) {
                Text("3.0x 快进中", Modifier.padding(horizontal = 14.dp, vertical = 7.dp), color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
        }
        AnimatedVisibility(controls, Modifier.fillMaxSize(), enter = fadeIn(tween(140)), exit = fadeOut(tween(100))) {
           Box(Modifier.fillMaxSize()) {
              Box(Modifier.fillMaxWidth().height(72.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(.78f), Color.Transparent))))
              Row(
                  Modifier.align(Alignment.TopEnd).fillMaxWidth().height(56.dp).padding(start = if (fullscreen) 60.dp else 8.dp, end = 8.dp),
                  horizontalArrangement = Arrangement.End,
                  verticalAlignment = Alignment.CenterVertically
              ) {
                  Box {
                      TextButton({ qualityMenu = true }, Modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
                          Text(result.qualityLabels[result.quality] ?: qualityName(result.quality), color = Color.White, style = MaterialTheme.typography.labelLarge)
                      }
                      DropdownMenu(qualityMenu, { qualityMenu = false }) {
                          (result.availableQualities + result.quality).distinct().sortedDescending().forEach { q ->
                              DropdownMenuItem(
                                  { Text(result.qualityLabels[q] ?: qualityName(q)) },
                                  { qualityMenu = false; if (q != result.quality) quality(q) },
                                  modifier = Modifier.heightIn(min = 48.dp),
                                  trailingIcon = { if (q == result.quality) Text("当前", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall) }
                              )
                          }
                      }
                  }
                  Box {
                      TextButton({ speedMenu = true }, Modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
                          Text("${player.playbackParameters.speed}x", color = Color.White, style = MaterialTheme.typography.labelLarge)
                      }
                      DropdownMenu(speedMenu, { speedMenu = false }) {
                          listOf(.75f, 1f, 1.25f, 1.5f, 2f).forEach { value ->
                              DropdownMenuItem(
                                  { Text("${value}x") },
                                  { player.playbackParameters = PlaybackParameters(value); speedMenu = false },
                                  modifier = Modifier.heightIn(min = 48.dp)
                              )
                          }
                      }
                  }
              }
              val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 }
              Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(.9f)))).padding(top = 24.dp)) {
                 PlayerProgressSlider(
                     value = (if (scrubbing) scrubPosition else position).coerceIn(0, duration ?: 1).toFloat(),
                     onValueChange = { scrubbing = true; scrubPosition = it.toLong() },
                     onValueChangeFinished = { player.seekTo(scrubPosition); position = scrubPosition; scrubbing = false },
                     valueRange = 0f..(duration ?: 1).toFloat(), enabled = duration != null,
                     modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp)
                 )
                 Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                     IconButton(::togglePlayback, Modifier.size(48.dp)) {
                         Icon(if (playing) Icons.Default.Pause else if (player.playbackState == Player.STATE_ENDED) Icons.Default.Replay else Icons.Default.PlayArrow, stringResource(if (playing) dev.opencode.bilimobile.R.string.pause else dev.opencode.bilimobile.R.string.play), tint = Color.White)
                     }
                     Text("${formatTime(if (scrubbing) scrubPosition else position)} / ${duration?.let(::formatTime) ?: "--:--"}", color = Color.White, style = MaterialTheme.typography.labelMedium)
                     Spacer(Modifier.weight(1f))
                     TextButton({ showDanmaku = !showDanmaku }, Modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                         Text("弹幕", color = if (showDanmaku) Color(0xFF67D9FF) else Color.White.copy(.65f), style = MaterialTheme.typography.labelMedium)
                     }
                     if (loggedIn) IconButton({ composeDanmaku = true }, Modifier.size(48.dp)) {
                         Icon(Icons.Default.Edit, stringResource(dev.opencode.bilimobile.R.string.send_danmaku), tint = Color.White, modifier = Modifier.size(20.dp))
                     }
                     IconButton({ settings = true }, Modifier.size(48.dp)) { Icon(Icons.Default.Tune, "弹幕设置", tint = Color.White, modifier = Modifier.size(20.dp)) }
                     IconButton({ fullscreen = !fullscreen }, Modifier.size(48.dp)) {
                         Icon(if (fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, if (fullscreen) "退出全屏" else stringResource(dev.opencode.bilimobile.R.string.enter_fullscreen), tint = Color.White, modifier = Modifier.size(22.dp))
                     }
                 }
               }
           }
         }
        if (fullscreen) {
            IconButton(
                { fullscreen = false },
                Modifier.align(Alignment.TopStart).padding(4.dp).size(48.dp).background(Color.Black.copy(.46f), RoundedCornerShape(24.dp))
            ) {
                Icon(Icons.Default.FullscreenExit, "退出全屏", tint = Color.White, modifier = Modifier.size(23.dp))
            }
        }
        playbackError?.let { message -> Surface(Modifier.align(Alignment.Center), color = Color.Black.copy(.78f), shape = RoundedCornerShape(12.dp)) { Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(message, color = Color.White, fontSize = 12.sp); TextButton({ playbackError = null; player.prepare(); player.play() }) { Text(stringResource(dev.opencode.bilimobile.R.string.retry)) } } } }
      }
    }
    if (fullscreen) ImmersivePlayerDialog(controls, { fullscreen = false }, content) else content()
    if (composeDanmaku) AlertDialog(onDismissRequest = { if (!danmakuPosting.loading) composeDanmaku = false },
        title = { Text(stringResource(dev.opencode.bilimobile.R.string.send_danmaku)) },
        text = { Column { OutlinedTextField(danmakuText, { danmakuText = it.take(100) }, singleLine = true); danmakuPosting.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) } } },
        confirmButton = { TextButton({ sendDanmaku(danmakuText, position) { ok -> if (ok) { danmakuText = ""; composeDanmaku = false } } }, enabled = danmakuText.isNotBlank() && !danmakuPosting.loading) { Text(if (danmakuPosting.loading) stringResource(dev.opencode.bilimobile.R.string.loading) else stringResource(dev.opencode.bilimobile.R.string.send)) } },
        dismissButton = { if (danmakuPosting.error?.contains("刷新确认") == true) TextButton(confirmDanmakuAfterRefresh) { Text("刷新确认") } else TextButton({ composeDanmaku = false }, enabled = !danmakuPosting.loading) { Text(stringResource(dev.opencode.bilimobile.R.string.cancel)) } })
    if (settings) DanmakuSettings(showDanmaku, { showDanmaku = it }, opacity, { opacity = it }, font, { font = it }, danmakuSpeed, { danmakuSpeed = it }, area, { area = it }, maximumLanes, { maximumLanes = it }, scrolling, { scrolling = it }, topMode, { topMode = it }, bottomMode, { bottomMode = it }, danmaku, retryDanmaku) { settings = false }
}

@Composable
private fun ImmersivePlayerDialog(controls: Boolean, dismiss: () -> Unit, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    Dialog(dismiss, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        val activity = context.findActivity()
        val window = (view.parent as? DialogWindowProvider)?.window
        fun hideSystemBars() { window?.let {
            WindowCompat.getInsetsController(it, it.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
            @Suppress("DEPRECATION")
            it.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        } }
        DisposableEffect(window, owner) {
            val restoreOrientation = activity?.requestedOrientation
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            window?.let {
                WindowCompat.setDecorFitsSystemWindows(it, false)
                it.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
                it.setBackgroundDrawable(ColorDrawable(android.graphics.Color.BLACK))
                @Suppress("DEPRECATION")
                run { it.statusBarColor = android.graphics.Color.TRANSPARENT; it.navigationBarColor = android.graphics.Color.TRANSPARENT }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.attributes = it.attributes.apply {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    it.isStatusBarContrastEnforced = false; it.isNavigationBarContrastEnforced = false
                }
            }
            hideSystemBars()
            val focusListener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus -> if (hasFocus) hideSystemBars() }
            window?.decorView?.viewTreeObserver?.addOnWindowFocusChangeListener(focusListener)
            val lifecycleObserver = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) hideSystemBars() }
            owner.lifecycle.addObserver(lifecycleObserver)
            onDispose {
                window?.decorView?.viewTreeObserver?.takeIf { it.isAlive }?.removeOnWindowFocusChangeListener(focusListener)
                owner.lifecycle.removeObserver(lifecycleObserver)
                restoreOrientation?.let { activity?.requestedOrientation = it }
            }
        }
        LaunchedEffect(controls) { hideSystemBars() }
        Box(Modifier.fillMaxSize().background(Color.Black)) { content() }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerProgressSlider(value: Float, onValueChange: (Float) -> Unit, onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>, enabled: Boolean, modifier: Modifier = Modifier) {
    val colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(.35f))
    Slider(value = value, onValueChange = onValueChange, modifier = modifier, enabled = enabled,
        onValueChangeFinished = onValueChangeFinished, colors = colors,
        thumb = { Box(Modifier.size(16.dp).background(Color.White, RoundedCornerShape(50))) },
        track = { state -> SliderDefaults.Track(sliderState = state, modifier = Modifier.height(3.dp), enabled = enabled,
            colors = colors, drawStopIndicator = null, thumbTrackGapSize = 0.dp, trackInsideCornerSize = 0.dp) },
        valueRange = valueRange)
}

@Composable
private fun FrameSyncedDanmaku(player: Player, items: List<Danmaku>, opacity: Float, font: DanmakuFont, speed: DanmakuSpeed,
    area: DanmakuArea, laneLimit: Int, scrolling: Boolean, top: Boolean, bottom: Boolean) {
    var position by remember(player) { mutableLongStateOf(player.currentPosition) }
    LaunchedEffect(player) {
        while (true) withFrameNanos { position = player.currentPosition }
    }
    DanmakuOverlay(items, position, opacity, font, speed, area, laneLimit, scrolling, top, bottom)
}

@Composable
internal fun ShortVideoPlayer(
    key: String,
    result: PlayResult,
    headers: Map<String, String>,
    active: Boolean,
    reportWatch: (Long, Long, Long, Int) -> Unit
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val startTs = remember(key) { System.currentTimeMillis() / 1000 }
    val startElapsed = remember(key) { SystemClock.elapsedRealtime() }
    val currentReport by rememberUpdatedState(reportWatch)
    val currentActive by rememberUpdatedState(active)
    val player = remember(key, result.videoUrls, result.audioUrl) {
        ExoPlayer.Builder(context).setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(3_000, 10_000, 700, 1_500).build()).build().apply {
            val factory = DefaultHttpDataSource.Factory().setDefaultRequestProperties(headers)
            val videos = result.videoUrls.map { url -> ProgressiveMediaSource.Factory(factory).createMediaSource(MediaItem.Builder().setUri(url).setMimeType(MimeTypes.VIDEO_MP4).build()) }
            val video = if (videos.size == 1) videos.first() else ConcatenatingMediaSource(*videos.toTypedArray())
            setMediaSource(result.audioUrl?.let { audio -> MergingMediaSource(video, ProgressiveMediaSource.Factory(factory).createMediaSource(MediaItem.Builder().setUri(audio).setMimeType(MimeTypes.AUDIO_MP4).build())) } ?: video)
            repeatMode = Player.REPEAT_MODE_ONE
            prepare(); playWhenReady = active
        }
    }
    var playing by remember(player) { mutableStateOf(player.isPlaying) }
    var resumeAfterPause by remember(player) { mutableStateOf(false) }
    var userWantsPlay by remember(player) { mutableStateOf(true) }
    DisposableEffect(player) {
        val listener = object : Player.Listener { override fun onIsPlayingChanged(value: Boolean) { playing = value } }
        player.addListener(listener)
        onDispose {
            currentReport(player.currentPosition / 1000, (SystemClock.elapsedRealtime() - startElapsed) / 1000, startTs, 4)
            player.removeListener(listener); player.release()
        }
    }
    LaunchedEffect(player, active) { if (active && userWantsPlay) player.play() else player.pause() }
    LaunchedEffect(player) {
        currentReport(player.currentPosition / 1000, 0, startTs, 1)
        while (true) { delay(15_000); if (player.isPlaying && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) currentReport(player.currentPosition / 1000, (SystemClock.elapsedRealtime() - startElapsed) / 1000, startTs, 0) }
    }
    DisposableEffect(owner, player) {
        val observer = LifecycleEventObserver { _, event -> when (event) { Lifecycle.Event.ON_PAUSE -> { resumeAfterPause = player.isPlaying; player.pause() }; Lifecycle.Event.ON_RESUME -> if (currentActive && resumeAfterPause && userWantsPlay) player.play(); else -> Unit } }
        owner.lifecycle.addObserver(observer); onDispose { owner.lifecycle.removeObserver(observer) }
    }
    Box(Modifier.fillMaxSize().background(Color.Black).clickable { userWantsPlay = !playing; if (playing) player.pause() else if (active) player.play() }) {
        AndroidView({ PlayerView(it).apply { this.player = player; useController = false; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM } }, Modifier.fillMaxSize(), onRelease = { it.player = null })
        AnimatedVisibility(!playing, Modifier.align(Alignment.Center), enter = fadeIn(tween(120)), exit = fadeOut(tween(90))) { FilledIconButton({ userWantsPlay = true; if (active) player.play() }, Modifier.size(56.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(.52f), contentColor = Color.White)) { Icon(Icons.Default.PlayArrow, "播放短视频") } }
    }
}

@Composable
private fun DanmakuSettings(enabled: Boolean, setEnabled: (Boolean) -> Unit, opacity: Float, setOpacity: (Float) -> Unit,
    font: DanmakuFont, setFont: (DanmakuFont) -> Unit, speed: DanmakuSpeed, setSpeed: (DanmakuSpeed) -> Unit,
    area: DanmakuArea, setArea: (DanmakuArea) -> Unit, lanes: Int, setLanes: (Int) -> Unit,
    scrolling: Boolean, setScrolling: (Boolean) -> Unit, top: Boolean, setTop: (Boolean) -> Unit,
    bottom: Boolean, setBottom: (Boolean) -> Unit, state: ContentState<DanmakuResult>, retry: () -> Unit, dismiss: () -> Unit) {
    AppModal(title = "弹幕设置", dismiss = dismiss, primaryLabel = "完成", primary = dismiss) {
      Column(Modifier.weight(1f, fill = false).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
        SettingSwitch("显示弹幕", enabled, setEnabled)
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("显示样式", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Text("透明度 ${(opacity * 100).toInt()}%", fontSize = 13.sp); Slider(opacity, setOpacity, valueRange = .2f..1f)
        ChoiceRow("字号", DanmakuFont.entries, font, setFont) { it.label }
        ChoiceRow("速度", DanmakuSpeed.entries, speed, setSpeed) { it.label }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("显示范围", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        ChoiceRow("显示区域", DanmakuArea.entries, area, setArea) { it.label }
        Text("最大轨道 $lanes", fontSize = 13.sp); Slider(lanes.toFloat(), { setLanes(it.toInt()) }, valueRange = 2f..12f, steps = 9)
        SettingSwitch("滚动弹幕", scrolling, setScrolling); SettingSwitch("顶部弹幕", top, setTop); SettingSwitch("底部弹幕", bottom, setBottom)
        HorizontalDivider(Modifier.padding(vertical = 8.dp)); Text(when { state.loading -> "弹幕正在加载"; state.error != null -> "弹幕加载失败：${state.error}"; else -> "已加载 ${state.value?.items?.size ?: 0} 条弹幕" }, Modifier.padding(top = 8.dp), fontSize = 12.sp, color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.error != null || state.value?.genuineEmpty == true) TextButton(retry) { Text("重新加载弹幕") }
      }
    }
}
@Composable private fun SettingSwitch(label: String, checked: Boolean, change: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f)); Switch(checked, change) } }
@Composable private fun <T> ChoiceRow(label: String, values: List<T>, selected: T, change: (T) -> Unit, text: (T) -> String) { Column { Text(label, fontSize = 13.sp); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { values.forEach { FilterChip(it == selected, { change(it) }, { Text(text(it), fontSize = 11.sp) }) } } } }

@Composable
private fun DanmakuOverlay(items: List<Danmaku>, position: Long, opacity: Float, font: DanmakuFont, speed: DanmakuSpeed,
    area: DanmakuArea, laneLimit: Int, scrolling: Boolean, top: Boolean, bottom: Boolean) {
    val second = position / 1000f
    BoxWithConstraints(Modifier.fillMaxSize().padding(6.dp)) {
        val lineHeight = (font.sp + 4).sp
        val line = with(LocalDensity.current) { lineHeight.toDp() + 3.dp }
        val lanes = minOf(laneLimit, (maxHeight * area.fraction / line).toInt().coerceAtLeast(1))
        val viewportWidth = maxWidth.value.coerceAtLeast(1f)
        val pixelsPerSecond = viewportWidth / speed.seconds
        val sortedItems = remember(items) { items.sortedBy(Danmaku::time) }
        val placements = remember(sortedItems, lanes, viewportWidth, font, speed, scrolling, top, bottom) {
            val laneAvailable = FloatArray(lanes) { -100f }
            sortedItems.asSequence().mapNotNull { item ->
                if (when (item.mode) { in 1..3 -> !scrolling; 4 -> !bottom; 5 -> !top; else -> true }) return@mapNotNull null
                val width = (item.text.length * font.sp * 1.05f + 14f).coerceIn(48f, viewportWidth * .75f)
                val duration = if (item.mode in 1..3) speed.seconds + width / pixelsPerSecond else 4f
                val lane = when (item.mode) {
                    in 1..3, 5 -> (0 until lanes).firstOrNull { laneAvailable[it] <= item.time }
                    4 -> (lanes - 1 downTo 0).firstOrNull { laneAvailable[it] <= item.time }
                    else -> null
                } ?: return@mapNotNull null
                laneAvailable[lane] = item.time + if (item.mode in 1..3) (width + 24f) / pixelsPerSecond + .12f else duration + .12f
                DanmakuPlacement(item, lane, width, item.time + duration)
            }.toList()
        }
        val start = placements.lowerBoundPlacement(second - speed.seconds * 1.75f)
        val end = placements.lowerBoundPlacement(second + .001f)
        placements.subList(start, end).forEach { placement ->
            val item = placement.item
            val age = (second - item.time).coerceAtLeast(0f)
            if (second > placement.endTime) return@forEach
            val x = if (item.mode in 1..3) maxWidth - (age * pixelsPerSecond).dp else (maxWidth - placement.widthDp.dp) / 2
            val y = line * placement.lane
            Text(item.text, Modifier.widthIn(max = placement.widthDp.dp).offset { IntOffset(x.roundToPx(), y.roundToPx()) },
                    color = Color(item.color or 0xff000000).copy(alpha = opacity), fontSize = font.sp.sp, maxLines = 1,
                    lineHeight = lineHeight,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    style = LocalTextStyle.current.copy(shadow = Shadow(Color.Black.copy(.9f), Offset(1.2f, 1.2f), 2.4f)))
        }
    }
}

private fun List<DanmakuPlacement>.lowerBoundPlacement(time: Float): Int {
    var low = 0; var high = size
    while (low < high) { val mid = (low + high) ushr 1; if (this[mid].item.time < time) low = mid + 1 else high = mid }
    return low
}

private fun qualityName(value: Int) = when (value) { 16 -> "360P"; 32 -> "480P"; 64 -> "720P"; 74 -> "720P60"; 80 -> "1080P"; 112 -> "1080P+"; else -> "Q$value" }
private fun formatTime(value: Long): String { val seconds = (value.coerceAtLeast(0) / 1000); return "%02d:%02d".format(seconds / 60, seconds % 60) }

@Composable
internal fun LivePlayer(info: LivePlayInfo, headers: Map<String, String>, selectQuality: (Int) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var controls by remember { mutableStateOf(false) }; var playing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }; var qualityMenu by remember { mutableStateOf(false) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var resumeAfterPause by remember { mutableStateOf(false) }
    var candidateIndex by remember(info) { mutableIntStateOf(0) }
    val selected = info.qualities.getOrNull(candidateIndex) ?: info.default ?: return
    val player = remember(selected.url) {
        val loadControl = DefaultLoadControl.Builder().setBufferDurationsMs(5_000, 15_000, 1_000, 2_000).build()
        ExoPlayer.Builder(context).setLoadControl(loadControl)
            .setLivePlaybackSpeedControl(DefaultLivePlaybackSpeedControl.Builder()
                .setFallbackMinPlaybackSpeed(.97f).setFallbackMaxPlaybackSpeed(1.05f).build())
            .build().apply {
        val factory = DefaultHttpDataSource.Factory().setDefaultRequestProperties(headers)
        val mediaItem = MediaItem.Builder().setUri(selected.url)
            .setMimeType(if (selected.hls) MimeTypes.APPLICATION_M3U8 else MimeTypes.VIDEO_MP4)
            .setLiveConfiguration(MediaItem.LiveConfiguration.Builder()
                .setTargetOffsetMs(5_000).setMinOffsetMs(2_000).setMaxOffsetMs(15_000)
                .setMinPlaybackSpeed(.97f).setMaxPlaybackSpeed(1.05f).build())
            .build()
        val source = if (selected.hls) {
            HlsMediaSource.Factory(factory).setAllowChunklessPreparation(true).createMediaSource(mediaItem)
        } else {
            ProgressiveMediaSource.Factory(factory).createMediaSource(mediaItem)
        }
        setMediaSource(source); prepare(); playWhenReady = true
    } }
    DisposableEffect(player) { val listener = object : Player.Listener {
        override fun onIsPlayingChanged(value: Boolean) { playing = value }
        override fun onPlayerError(value: PlaybackException) {
            if (value.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                player.seekToDefaultPosition(); player.prepare(); player.play(); return
            }
            if (candidateIndex + 1 < info.qualities.size) candidateIndex++ else error = "直播线路连接失败，请重试"
        }
    }; player.addListener(listener); onDispose { player.removeListener(listener); player.release() } }
    DisposableEffect(owner, player) {
        val observer = LifecycleEventObserver { _, event -> when (event) {
            Lifecycle.Event.ON_PAUSE -> { resumeAfterPause = player.playWhenReady; player.pause() }
            Lifecycle.Event.ON_RESUME -> if (resumeAfterPause) player.play()
            else -> Unit
        } }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(player) {
        var previous = -1L; var stalled = 0
        while (true) {
            delay(5_000)
            val current = player.currentPosition
            stalled = if (player.playWhenReady && current <= previous + 200) stalled + 1 else 0
            previous = current
            if (stalled >= 2) {
                player.seekToDefaultPosition(); player.prepare(); player.play(); delay(5_000)
                if (player.currentPosition <= previous + 200 && candidateIndex + 1 < info.qualities.size) candidateIndex++
                stalled = 0
            }
        }
    }
    LaunchedEffect(controls, playing) { if (controls && playing) { delay(2_500); controls = false } }
    val content: @Composable () -> Unit = { Box(Modifier.fillMaxSize().background(Color.Black).clickable { controls = !controls }) {
        AndroidView({ PlayerView(it).apply { this.player = player; useController = false } }, Modifier.fillMaxSize(), onRelease = { it.player = null })
        AnimatedVisibility(controls, enter = fadeIn(tween(140)), exit = fadeOut(tween(100))) { Box(Modifier.fillMaxSize()) {
            FilledIconButton({ if (playing) player.pause() else player.play() }, Modifier.align(Alignment.Center).size(48.dp)) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) "暂停直播" else "播放直播") }
            Row(Modifier.align(Alignment.BottomEnd).padding(6.dp), verticalAlignment = Alignment.CenterVertically) { Box { TextButton({ qualityMenu = true }) { Text("${selected.name} · ${selected.format}", color = Color.White) }; DropdownMenu(qualityMenu, { qualityMenu = false }) { info.qualities.distinctBy { it.quality }.forEach { q -> DropdownMenuItem({ Text(q.name) }, { qualityMenu = false; selectQuality(q.quality) }) } } }; IconButton({ fullscreen = !fullscreen }) { Icon(if (fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, if (fullscreen) "退出直播全屏" else "直播全屏", tint = Color.White) } }
        } }
        error?.let { Surface(Modifier.align(Alignment.Center), color = Color.Black.copy(.8f), shape = RoundedCornerShape(12.dp)) { Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(it, color = Color.White); TextButton({ error = null; player.prepare(); player.play() }) { Text("重试") } } } }
    } }
    if (fullscreen) ImmersivePlayerDialog(controls, { fullscreen = false }, content) else content()
}
