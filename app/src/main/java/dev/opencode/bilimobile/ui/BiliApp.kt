@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package dev.opencode.bilimobile.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.RemoveRedEye
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import dev.opencode.bilimobile.R
import dev.opencode.bilimobile.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

private enum class Tab(@androidx.annotation.StringRes val label: Int) { Home(R.string.home), Search(R.string.search), Profile(R.string.profile) }

@Composable fun BiliApp(vm: MainViewModel = viewModel()) {
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(detailId != null) { detailId = null }
    Scaffold(bottomBar = {
        AnimatedVisibility(visible = detailId == null, enter = fadeIn(), exit = fadeOut()) {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .94f)) {
                Tab.entries.forEach { item ->
                    NavigationBarItem(tab == item, { tab = item }, {
                        Icon(when (item) { Tab.Home -> Icons.Default.Home; Tab.Search -> Icons.Default.Search; Tab.Profile -> Icons.Default.Person }, stringResource(item.label))
                    }, label = { Text(stringResource(item.label)) })
                }
            }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AnimatedContent(detailId, label = "页面切换") { id ->
                if (id != null) DetailScreen(id, vm) { detailId = it } else when (tab) {
                    Tab.Home -> PopularScreen(vm) { detailId = it }
                    Tab.Search -> SearchScreen(vm) { detailId = it }
                    Tab.Profile -> ProfileScreen(vm) { detailId = it }
                }
            }
        }
    }
}

@Composable private fun PopularScreen(vm: MainViewModel, open: (String) -> Unit) {
    val state by vm.popular.collectAsState()
    VideoGrid(stringResource(R.string.popular_title), stringResource(R.string.popular_subtitle), state, vm::refreshPopular, open)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun SearchScreen(vm: MainViewModel, open: (String) -> Unit) {
    val state by vm.search.collectAsState(); var query by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        PageHeader(stringResource(R.string.discover), "WBI 签名视频搜索")
        TextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 20.dp), placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, stringResource(R.string.search)) }, singleLine = true, shape = RoundedCornerShape(18.dp),
            colors = TextFieldDefaults.colors(unfocusedIndicatorColor = Color.Transparent, focusedIndicatorColor = Color.Transparent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { vm.search(query) }))
        Spacer(Modifier.height(10.dp))
        StateBody(state, { vm.search(query) }) { videos -> if (videos.isEmpty()) EmptyMessage(stringResource(R.string.search_empty)) else VideoGridBody(videos, open) }
    }
}

@Composable private fun VideoGrid(title: String, subtitle: String, state: ContentState<List<Video>>, retry: () -> Unit, open: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) { PageHeader(title, subtitle); StateBody(state, retry) { VideoGridBody(it, open) } }
}

@Composable private fun VideoGridBody(videos: List<Video>, open: (String) -> Unit) {
    LazyVerticalGrid(GridCells.Adaptive(170.dp), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        gridItems(videos, key = { it.bvid }) { VideoCard(it) { open(it.bvid) } }
    }
}

@Composable private fun PageHeader(title: String, subtitle: String) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 14.dp)) {
        Text(title, fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f), fontSize = 14.sp)
    }
}

@Composable private fun VideoCard(video: Video, onClick: () -> Unit) {
    Card(onClick, shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(1.dp)) {
        Column {
            Box {
                NetworkImage(video.coverUrl, video.title, Modifier.fillMaxWidth().aspectRatio(16 / 9f), 480, 270)
                Text(formatDuration(video.duration), Modifier.align(Alignment.BottomEnd).padding(8.dp).background(Color.Black.copy(.68f), RoundedCornerShape(7.dp)).padding(horizontal = 6.dp, vertical = 2.dp), color = Color.White, fontSize = 11.sp)
            }
            Column(Modifier.padding(13.dp)) {
                Text(cleanTitle(video.title), fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(7.dp)); Text(video.creator, color = MaterialTheme.colorScheme.onSurface.copy(.56f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.RemoveRedEye, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text(formatCount(video.views), fontSize = 12.sp) }
            }
        }
    }
}

@Composable private fun DetailScreen(bvid: String, vm: MainViewModel, open: (String) -> Unit) {
    val detail by vm.details.collectAsState(); val stream by vm.playUrl.collectAsState(); val comments by vm.comments.collectAsState(); val related by vm.related.collectAsState()
    LaunchedEffect(bvid) { vm.loadDetails(bvid) }
    StateBody(detail, { vm.loadDetails(bvid) }) { video ->
        var cid by remember(video.bvid) { mutableLongStateOf(video.cid.takeIf { it > 0 } ?: video.pages.firstOrNull()?.cid ?: 0) }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Box(Modifier.fillMaxWidth().aspectRatio(16 / 9f).background(Color.Black)) {
                when { stream.value != null -> VideoPlayer(stream.value!!, vm.playbackHeaders()) { vm.loadStream(video.bvid, cid, it) }
                    stream.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
                    else -> Text(stream.error ?: stringResource(R.string.player_unavailable), Modifier.align(Alignment.Center).padding(24.dp), color = Color.White) }
            } }
            item { DetailHeader(video) }
            if (video.pages.size > 1) item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(video.pages, key = { it.cid }) { page -> FilterChip(cid == page.cid, { cid = page.cid; vm.loadStream(video.bvid, page.cid) }, { Text("P${page.page} ${page.part}", maxLines = 1) }) }
                }
            }
            item { SectionTitle(stringResource(R.string.comments), video.stat.reply) }
            if (comments.error != null && comments.value.orEmpty().isEmpty()) item { InlineError(comments.error!!, { vm.loadComments(true) }) }
            items(comments.value.orEmpty(), key = { it.rpid }) { CommentRow(it) }
            item { Button(vm::loadComments, Modifier.fillMaxWidth().padding(horizontal = 20.dp).heightIn(min = 48.dp), enabled = !comments.loading) { Text(if (comments.loading) stringResource(R.string.loading) else stringResource(R.string.load_more)) } }
            item { SectionTitle(stringResource(R.string.related)) }
            if (related.error != null) item { InlineError(related.error!!, { vm.loadDetails(bvid) }) }
            item { LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(related.value.orEmpty(), key = { it.bvid }) { Box(Modifier.width(230.dp)) { VideoCard(it) { open(it.bvid) } } } } }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable private fun DetailHeader(video: Video) {
    var expanded by rememberSaveable(video.bvid) { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 20.dp)) {
        Text(cleanTitle(video.title), Modifier.fillMaxWidth(), fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp, maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
        TextButton({ expanded = !expanded }) { Text(if (expanded) "收起" else "展开标题与简介") }
        Text(stringResource(R.string.views, formatCount(video.views)) + " · ${formatDate(video.pubdate)}", color = MaterialTheme.colorScheme.onSurface.copy(.58f))
        Spacer(Modifier.height(12.dp)); Card(shape = RoundedCornerShape(24.dp)) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            NetworkImage(video.owner.face, video.creator, Modifier.size(52.dp).clip(CircleShape), 120, 120); Spacer(Modifier.width(12.dp)); Column { Text(video.creator, fontWeight = FontWeight.Bold); Text("UID ${video.owner.mid}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(.55f)) }
        } }
        Spacer(Modifier.height(14.dp)); Row(Modifier.fillMaxWidth(), Arrangement.SpaceAround) {
            Stat(Icons.Default.ThumbUp, video.stat.like, "点赞"); Stat(Icons.Default.MonetizationOn, video.stat.coin, "投币"); Stat(Icons.Default.Star, video.stat.favorite, "收藏"); Stat(Icons.Default.Share, video.stat.share, "分享")
        }
        Spacer(Modifier.height(18.dp)); Text(stringResource(R.string.description), fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(video.desc.ifBlank { stringResource(R.string.no_description) }, maxLines = if (expanded) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurface.copy(.72f))
    }
}

@Composable private fun Stat(icon: androidx.compose.ui.graphics.vector.ImageVector, count: Long, label: String) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, label); Text(formatCount(count), fontSize = 12.sp) } }
@Composable private fun SectionTitle(text: String, count: Long = 0) { Text(text + if (count > 0) " ${formatCount(count)}" else "", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), fontWeight = FontWeight.Bold, fontSize = 21.sp) }

@Composable private fun CommentRow(comment: Comment) { Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
    NetworkImage(comment.member.avatar, comment.member.uname, Modifier.size(42.dp).clip(CircleShape), 96, 96); Spacer(Modifier.width(12.dp)); Column {
        Text(comment.member.uname.ifBlank { "匿名用户" }, fontWeight = FontWeight.SemiBold); Text(comment.content.message, lineHeight = 21.sp); Spacer(Modifier.height(5.dp)); Text("${formatDate(comment.ctime)}  ·  ${comment.like} 赞  ·  ${comment.rcount} 回复", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(.52f))
    }
} }

@Composable private fun VideoPlayer(result: PlayResult, headers: Map<String, String>, quality: (Int) -> Unit) {
    val context = LocalContext.current; val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val player = remember(result.url) { ExoPlayer.Builder(context).build().apply { val factory = DefaultHttpDataSource.Factory().setDefaultRequestProperties(headers); setMediaSource(ProgressiveMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri(result.url))); prepare(); playWhenReady = true } }
    var speedMenu by remember { mutableStateOf(false) }; var qualityMenu by remember { mutableStateOf(false) }; var speed by remember { mutableFloatStateOf(1f) }
    DisposableEffect(player) { onDispose { player.release() } }
    DisposableEffect(owner, player) { val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) player.pause() }; owner.lifecycle.addObserver(observer); onDispose { owner.lifecycle.removeObserver(observer) } }
    Box(Modifier.fillMaxSize()) {
        AndroidView({ PlayerView(it).apply { this.player = player; useController = true } }, Modifier.fillMaxSize())
        Row(Modifier.align(Alignment.TopEnd).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box { AssistChip({ speedMenu = true }, { Text("${speed}x") }); DropdownMenu(speedMenu, { speedMenu = false }) { listOf(.75f, 1f, 1.25f, 1.5f, 2f).forEach { value -> DropdownMenuItem({ Text("${value}x") }, { speed = value; player.playbackParameters = PlaybackParameters(value); speedMenu = false }) } } }
            Box { AssistChip({ qualityMenu = true }, { Text(qualityName(result.quality)) }); DropdownMenu(qualityMenu, { qualityMenu = false }) { (result.availableQualities + listOf(16, 32, 64)).distinct().sorted().forEach { q -> DropdownMenuItem({ Text(qualityName(q)) }, { qualityMenu = false; quality(q) }) } } }
        }
    }
}

@Composable private fun ProfileScreen(vm: MainViewModel, open: (String) -> Unit) {
    val state by vm.profile.collectAsState(); val login by vm.login.collectAsState(); val history by vm.history.collectAsState(); val later by vm.watchLater.collectAsState(); val favorites by vm.favorites.collectAsState()
    Box(Modifier.fillMaxSize()) { Column(Modifier.fillMaxSize()) { PageHeader(stringResource(R.string.you), stringResource(R.string.session_local)); StateBody(state, vm::refreshProfile) { profile ->
        if (!profile.isLogin) SignedOut(vm::beginLogin) else LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Row(verticalAlignment = Alignment.CenterVertically) { NetworkImage(profile.face, profile.uname, Modifier.size(82.dp).clip(CircleShape), 180, 180); Spacer(Modifier.width(16.dp)); Column { Text(profile.uname, fontSize = 24.sp, fontWeight = FontWeight.Bold); Text("LV${profile.level_info.currentLevel} · UID ${profile.mid}") } } }
            item { ProfileSection(stringResource(R.string.history), history, open) { item -> item.title to item.history.bvid } }
            item { ProfileSection(stringResource(R.string.watch_later), later, open) { item -> cleanTitle(item.title) to item.bvid } }
            item { FolderSection(favorites) }
            item { OutlinedButton(vm::logout, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.sign_out)) } }
        }
    } }; if (login !is LoginState.Idle) LoginDialog(login, vm::beginLogin, vm::dismissLogin) }
}

@Composable private fun SignedOut(login: () -> Unit) { Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Person, null, Modifier.size(72.dp)); Text(stringResource(R.string.sign_in), fontSize = 23.sp, fontWeight = FontWeight.Bold); Text(stringResource(R.string.scan_tip)); Spacer(Modifier.height(20.dp)); Button(login, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.show_qr)) } } }

@Composable private fun <T> ProfileSection(title: String, state: ContentState<List<T>>, open: (String) -> Unit, label: (T) -> Pair<String, String>) { Card(shape = RoundedCornerShape(24.dp)) { Column(Modifier.fillMaxWidth().padding(16.dp)) { Text("$title ${state.value?.size ?: ""}", fontWeight = FontWeight.Bold, fontSize = 18.sp); when { state.loading -> LinearProgressIndicator(Modifier.fillMaxWidth()); state.error != null -> Text(state.error, color = MaterialTheme.colorScheme.error); else -> state.value.orEmpty().take(4).forEach { val value = label(it); Text(value.first, Modifier.fillMaxWidth().clickable(enabled = value.second.isNotBlank()) { open(value.second) }.padding(vertical = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis) } } } } }
@Composable private fun FolderSection(state: ContentState<List<FavoriteFolder>>) { Card(shape = RoundedCornerShape(24.dp)) { Column(Modifier.fillMaxWidth().padding(16.dp)) { Text(stringResource(R.string.favorites), fontWeight = FontWeight.Bold, fontSize = 18.sp); when { state.loading -> LinearProgressIndicator(Modifier.fillMaxWidth()); state.error != null -> Text(state.error, color = MaterialTheme.colorScheme.error); else -> state.value.orEmpty().take(5).forEach { Text("${it.title} · ${it.media_count} 个内容", Modifier.padding(vertical = 7.dp)) } } } } }

@Composable private fun LoginDialog(state: LoginState, retry: () -> Unit, dismiss: () -> Unit) { Dialog(dismiss) { Card(shape = RoundedCornerShape(28.dp)) { Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(stringResource(R.string.qr_sign_in), fontSize = 22.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(18.dp)); when (state) { LoginState.Loading -> CircularProgressIndicator(); is LoginState.Ready, is LoginState.Scanned -> { val qr = if (state is LoginState.Ready) state.qr else (state as LoginState.Scanned).qr; QrImage(qr.url); Text(if (state is LoginState.Scanned) stringResource(R.string.qr_confirm) else stringResource(R.string.qr_scan)) }; LoginState.Success -> Text(stringResource(R.string.login_success)); is LoginState.Error -> { Text(state.message, color = MaterialTheme.colorScheme.error); Button(retry) { Text(stringResource(R.string.retry)) } }; LoginState.Idle -> Unit }; Spacer(Modifier.height(16.dp)); Button(dismiss) { Text(if (state is LoginState.Success) stringResource(R.string.continue_text) else stringResource(R.string.cancel)) } } } } }

@Composable private fun QrImage(value: String) { var bitmap by remember(value) { mutableStateOf<Bitmap?>(null) }; LaunchedEffect(value) { bitmap = withContext(Dispatchers.Default) { qrBitmap(value) } }; if (bitmap == null) CircularProgressIndicator() else Image(bitmap!!.asImageBitmap(), "登录二维码", Modifier.size(220.dp)) }

@Composable private fun NetworkImage(url: String, description: String, modifier: Modifier, width: Int, height: Int) { val context = LocalContext.current; AsyncImage(ImageRequest.Builder(context).data(normalizeUrl(url)).size(width, height).precision(Precision.INEXACT).memoryCachePolicy(CachePolicy.ENABLED).diskCachePolicy(CachePolicy.ENABLED).crossfade(true).build(), description, modifier, placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant), error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant), contentScale = ContentScale.Crop) }

@Composable private fun <T> StateBody(state: ContentState<T>, retry: () -> Unit, content: @Composable (T) -> Unit) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { when { state.value != null -> content(state.value); state.loading -> CircularProgressIndicator(); else -> InlineError(state.error ?: stringResource(R.string.empty), retry) }; if (state.loading && state.value != null) CircularProgressIndicator(Modifier.align(Alignment.TopCenter).padding(8.dp).size(22.dp), strokeWidth = 2.dp) } }
@Composable private fun InlineError(message: String, retry: () -> Unit) { Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(message, color = MaterialTheme.colorScheme.error); IconButton(retry) { Icon(Icons.Default.Refresh, stringResource(R.string.retry)) } } }
@Composable private fun EmptyMessage(text: String) = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text) }

private fun qrBitmap(value: String): Bitmap { val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 640, 640); return Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888).also { bitmap -> for (x in 0 until 640) for (y in 0 until 640) bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE) } }
private fun cleanTitle(value: String) = value.replace(Regex("<[^>]*>"), "").replace("&quot;", "\"").replace("&amp;", "&")
private fun normalizeUrl(value: String) = when { value.startsWith("//") -> "https:$value"; value.startsWith("http://") -> "https://${value.removePrefix("http://")}"; else -> value }
private fun formatDuration(seconds: Int) = "%d:%02d".format(seconds / 60, seconds % 60)
private fun formatCount(value: Long) = when { value >= 100_000_000 -> "%.1f亿".format(value / 100_000_000.0); value >= 10_000 -> "%.1f万".format(value / 10_000.0); else -> value.toString() }
private fun formatDate(epoch: Long) = if (epoch <= 0) "" else SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(epoch * 1000))
private fun qualityName(value: Int) = when (value) { 16 -> "360P"; 32 -> "480P"; 64 -> "720P"; 74 -> "720P60"; 80 -> "1080P"; else -> "${value}P" }
