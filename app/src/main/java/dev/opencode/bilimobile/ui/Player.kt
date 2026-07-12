@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package dev.opencode.bilimobile.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
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
import androidx.media3.exoplayer.source.ConcatenatingMediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import dev.opencode.bilimobile.data.Danmaku
import dev.opencode.bilimobile.data.DanmakuResult
import dev.opencode.bilimobile.data.PlayResult
import kotlinx.coroutines.delay

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
    quality: (Int) -> Unit,
    fallback: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val prefs = remember { context.getSharedPreferences("playback_positions", 0) }
    val player = remember(result.videoUrls, result.audioUrl) {
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
    var showDanmaku by rememberSaveable { mutableStateOf(true) }
    var opacity by rememberSaveable { mutableFloatStateOf(.78f) }
    var speedMenu by remember { mutableStateOf(false) }
    var qualityMenu by remember { mutableStateOf(false) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var resumeAfterPause by remember { mutableStateOf(false) }
    var controls by remember { mutableStateOf(true) }
    var playing by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var composeDanmaku by remember { mutableStateOf(false) }
    var danmakuText by rememberSaveable { mutableStateOf("") }
    var fallbackSent by remember(result.videoUrls, result.audioUrl) { mutableStateOf(false) }
    var pendingSavedSeek by remember(player, key) { mutableLongStateOf(prefs.getLong(key, 0L).coerceAtLeast(0L)) }

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
    LaunchedEffect(player) { while (true) { position = player.currentPosition; delay(250) } }
    LaunchedEffect(controls, playing) { if (controls && playing) { delay(3_000); controls = false } }
    DisposableEffect(player) { onDispose { prefs.edit().putLong(key, player.currentPosition).apply(); player.release() } }
    DisposableEffect(owner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> { resumeAfterPause = player.playWhenReady; player.pause() }
                Lifecycle.Event.ON_RESUME -> if (resumeAfterPause) player.play()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    val content: @Composable () -> Unit = {
      Box(Modifier.fillMaxSize().background(Color.Black).clickable { controls = !controls }) {
        AndroidView({ PlayerView(it).apply { this.player = player; useController = false } }, Modifier.fillMaxSize(),
            onRelease = { it.player = null })
        if (showDanmaku) DanmakuOverlay(danmaku.value?.items.orEmpty(), position, opacity)
        if (buffering) CircularProgressIndicator(Modifier.align(Alignment.Center).size(34.dp), color = Color.White, strokeWidth = 3.dp)
        AnimatedVisibility(controls, enter = fadeIn(), exit = fadeOut()) {
          Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .28f))) {
            val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 }
            Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton({ player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)) }, Modifier.size(44.dp)) { Icon(Icons.Default.Replay10, stringResource(dev.opencode.bilimobile.R.string.rewind_10), tint = Color.White, modifier = Modifier.size(23.dp)) }
                FilledIconButton({ if (player.playbackState == Player.STATE_ENDED) { player.seekTo(0); player.play() } else if (playing) player.pause() else player.play() }, Modifier.size(52.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(.9f), contentColor = Color.Black)) {
                    Icon(if (playing) Icons.Default.Pause else if (player.playbackState == Player.STATE_ENDED) Icons.Default.Replay else Icons.Default.PlayArrow, stringResource(if (playing) dev.opencode.bilimobile.R.string.pause else dev.opencode.bilimobile.R.string.play))
                }
                IconButton({ duration?.let { player.seekTo((player.currentPosition + 10_000).coerceAtMost(it)) } }, Modifier.size(44.dp), enabled = duration != null) { Icon(Icons.Default.Forward10, stringResource(dev.opencode.bilimobile.R.string.forward_10), tint = Color.White, modifier = Modifier.size(23.dp)) }
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(.35f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
              Slider(value = position.coerceIn(0, duration ?: 1).toFloat(), onValueChange = { player.seekTo(it.toLong()) }, valueRange = 0f..(duration ?: 1).toFloat(), enabled = duration != null, modifier = Modifier.fillMaxWidth().height(20.dp), colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(.3f)))
              Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${formatTime(position)} / ${duration?.let(::formatTime) ?: "--:--"}", color = Color.White, fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                 TextButton({ showDanmaku = !showDanmaku }, contentPadding = PaddingValues(horizontal = 6.dp)) { Text(if (showDanmaku) "弹幕开" else "弹幕关", color = if (showDanmaku) Color(0xFFFF9AB5) else Color.White, fontSize = 11.sp) }
                 TextButton({ if (danmaku.error != null) retryDanmaku() }, enabled = danmaku.error != null, contentPadding = PaddingValues(horizontal = 6.dp)) { Text(when { danmaku.loading -> "加载中"; danmaku.error != null -> "失败重试"; danmaku.value?.usedFallback == true -> "备用源 ${danmaku.value?.items?.size ?: 0}"; danmaku.value?.items.isNullOrEmpty() -> "暂无弹幕"; else -> "已载入 ${danmaku.value?.items?.size}" }, color = Color.White, fontSize = 11.sp) }
              }
              Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
             if (loggedIn) IconButton({ composeDanmaku = true }, Modifier.size(36.dp)) { Icon(Icons.Default.Edit, stringResource(dev.opencode.bilimobile.R.string.send_danmaku), tint = Color.White, modifier = Modifier.size(18.dp)) }
             Box {
                 TextButton({ speedMenu = true }, contentPadding = PaddingValues(horizontal = 7.dp)) { Text("${player.playbackParameters.speed}x", color = Color.White, fontSize = 11.sp) }
                DropdownMenu(speedMenu, { speedMenu = false }) {
                    listOf(.75f, 1f, 1.25f, 1.5f, 2f).forEach { value ->
                        DropdownMenuItem({ Text("${value}x") }, { player.playbackParameters = PlaybackParameters(value); speedMenu = false })
                    }
                }
            }
            Box {
                 TextButton({ qualityMenu = true }, contentPadding = PaddingValues(horizontal = 7.dp)) { Text(qualityName(result.quality), color = Color.White, fontSize = 11.sp) }
                DropdownMenu(qualityMenu, { qualityMenu = false }) {
                    result.availableQualities.distinct().sortedDescending().forEach { q ->
                        DropdownMenuItem({ Text(result.qualityLabels[q] ?: qualityName(q)) }, { qualityMenu = false; quality(q) })
                    }
                    DropdownMenuItem({ Text(stringResource(dev.opencode.bilimobile.R.string.danmaku_opacity, (opacity * 100).toInt())) }, { opacity = if (opacity > .6f) .45f else .8f; qualityMenu = false })
                }
            }
             IconButton({ fullscreen = true }, Modifier.size(36.dp)) { Icon(Icons.Default.Fullscreen, stringResource(dev.opencode.bilimobile.R.string.enter_fullscreen), tint = Color.White, modifier = Modifier.size(20.dp)) }
              }
            }
          }
        }
        playbackError?.let { message -> Surface(Modifier.align(Alignment.TopCenter).padding(10.dp), color = Color.Black.copy(.72f), shape = RoundedCornerShape(10.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(message, Modifier.padding(start = 12.dp), color = Color.White, fontSize = 12.sp); TextButton({ playbackError = null; player.prepare(); player.play() }) { Text(stringResource(dev.opencode.bilimobile.R.string.retry)) } } } }
      }
    }
    if (fullscreen) Dialog({ fullscreen = false }, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        DisposableEffect(Unit) {
            val activity = context as? Activity
            val oldOrientation = activity?.requestedOrientation
            val window = (view.parent as? DialogWindowProvider)?.window
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            window?.let { WindowCompat.getInsetsController(it, it.decorView).hide(androidx.core.view.WindowInsetsCompat.Type.systemBars()) }
            onDispose {
                window?.let { WindowCompat.getInsetsController(it, it.decorView).show(androidx.core.view.WindowInsetsCompat.Type.systemBars()) }
                if (oldOrientation != null) activity?.requestedOrientation = oldOrientation
            }
        }
        content()
    } else {
        content()
    }
    if (composeDanmaku) AlertDialog(onDismissRequest = { if (!danmakuPosting.loading) composeDanmaku = false },
        title = { Text(stringResource(dev.opencode.bilimobile.R.string.send_danmaku)) },
        text = { Column { OutlinedTextField(danmakuText, { danmakuText = it.take(100) }, singleLine = true); danmakuPosting.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) } } },
        confirmButton = { TextButton({ sendDanmaku(danmakuText, position) { ok -> if (ok) { danmakuText = ""; composeDanmaku = false } } }, enabled = danmakuText.isNotBlank() && !danmakuPosting.loading) { Text(if (danmakuPosting.loading) stringResource(dev.opencode.bilimobile.R.string.loading) else stringResource(dev.opencode.bilimobile.R.string.send)) } },
        dismissButton = { if (danmakuPosting.error?.contains("刷新确认") == true) TextButton(confirmDanmakuAfterRefresh) { Text("刷新确认") } else TextButton({ composeDanmaku = false }, enabled = !danmakuPosting.loading) { Text(stringResource(dev.opencode.bilimobile.R.string.cancel)) } })
}

@Composable
private fun DanmakuOverlay(items: List<Danmaku>, position: Long, opacity: Float) {
    val second = position / 1000f
    val start = items.lowerBound(second - 8f)
    val end = items.lowerBound(second + .001f)
    val active = items.subList(start, end).takeLast(10)
    BoxWithConstraints(Modifier.fillMaxSize().padding(top = 48.dp)) {
        active.forEachIndexed { index, item ->
            val age = (second - item.time).coerceIn(0f, 8f)
            val x = if (item.mode in 1..3) maxWidth * (1f - age / 4f) else 0.dp
            val y = if (item.mode == 4) maxHeight - 28.dp else ((index % 6) * 24).dp
            Text(item.text, Modifier.offset { IntOffset(x.roundToPx(), y.roundToPx()) }
                    .background(Color.Black.copy(alpha = .25f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp),
                    color = Color(item.color or 0xff000000).copy(alpha = opacity), fontSize = 13.sp, maxLines = 1)
        }
    }
}

private fun List<Danmaku>.lowerBound(time: Float): Int {
    var low = 0; var high = size
    while (low < high) { val mid = (low + high) ushr 1; if (this[mid].time < time) low = mid + 1 else high = mid }
    return low
}

private fun qualityName(value: Int) = when (value) { 16 -> "360P"; 32 -> "480P"; 64 -> "720P"; 74 -> "720P60"; 80 -> "1080P"; 112 -> "1080P+"; else -> "Q$value" }
private fun formatTime(value: Long): String { val seconds = (value.coerceAtLeast(0) / 1000); return "%02d:%02d".format(seconds / 60, seconds % 60) }
