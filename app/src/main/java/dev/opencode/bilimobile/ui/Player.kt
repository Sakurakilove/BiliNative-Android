@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package dev.opencode.bilimobile.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
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
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
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
import dev.opencode.bilimobile.data.PlayResult
import kotlinx.coroutines.delay

@Composable
internal fun VideoPlayer(
    key: String,
    result: PlayResult,
    headers: Map<String, String>,
    danmaku: List<Danmaku>,
    quality: (Int) -> Unit
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
            seekTo(prefs.getLong(key, 0L))
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

    LaunchedEffect(player) { while (true) { position = player.currentPosition; delay(250) } }
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
      Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView({ PlayerView(it).apply { this.player = player; useController = true } }, Modifier.fillMaxSize())
        if (showDanmaku) DanmakuOverlay(danmaku, position, opacity)
        Row(Modifier.align(Alignment.TopEnd).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(showDanmaku, { showDanmaku = !showDanmaku }, { Text(stringResource(dev.opencode.bilimobile.R.string.danmaku_short)) })
            Box {
                AssistChip({ speedMenu = true }, { Text("${player.playbackParameters.speed}x") })
                DropdownMenu(speedMenu, { speedMenu = false }) {
                    listOf(.75f, 1f, 1.25f, 1.5f, 2f).forEach { value ->
                        DropdownMenuItem({ Text("${value}x") }, { player.playbackParameters = PlaybackParameters(value); speedMenu = false })
                    }
                }
            }
            Box {
                AssistChip({ qualityMenu = true }, { Text(qualityName(result.quality)) })
                DropdownMenu(qualityMenu, { qualityMenu = false }) {
                    result.availableQualities.distinct().sortedDescending().forEachIndexed { index, q ->
                        DropdownMenuItem({ Text(result.qualityLabels.getOrNull(index) ?: qualityName(q)) }, { qualityMenu = false; quality(q) })
                    }
                    DropdownMenuItem({ Text(stringResource(dev.opencode.bilimobile.R.string.danmaku_opacity, (opacity * 100).toInt())) }, { opacity = if (opacity > .6f) .45f else .8f; qualityMenu = false })
                }
            }
            IconButton({ fullscreen = true }) { Icon(Icons.Default.Fullscreen, stringResource(dev.opencode.bilimobile.R.string.enter_fullscreen), tint = Color.White) }
        }
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
