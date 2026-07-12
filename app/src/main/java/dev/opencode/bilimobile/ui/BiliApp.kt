package dev.opencode.bilimobile.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import dev.opencode.bilimobile.BuildConfig
import dev.opencode.bilimobile.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

private enum class Tab { Home, Dynamic, Profile }

@Composable fun BiliApp(vm: MainViewModel = viewModel()) {
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
    var search by rememberSaveable { mutableStateOf(false) }
    BackHandler(detail != null || search) { if (detail != null) detail = null else search = false }
    Scaffold(bottomBar = {
        AnimatedVisibility(detail == null && !search, enter = fadeIn(), exit = fadeOut()) {
            NavigationBar(Modifier.height(72.dp), containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .92f), tonalElevation = 2.dp) {
                Tab.entries.forEach { item -> NavigationBarItem(tab == item, { tab = item }, {
                    Icon(when (item) { Tab.Home -> Icons.Default.Home; Tab.Dynamic -> Icons.Default.Subscriptions; Tab.Profile -> Icons.Default.Person }, tabTitle(item))
                }, label = { Text(tabTitle(item)) }, alwaysShowLabel = true) }
            }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AnimatedContent(Pair(detail, search), label = "destination") { (id, searching) ->
                when { id != null -> DetailScreen(id, vm) { detail = it }
                    searching -> SearchScreen(vm, { search = false }, { detail = it })
                    tab == Tab.Home -> HomeScreen(vm, { search = true }, { detail = it })
                    tab == Tab.Dynamic -> DynamicScreen(vm) { detail = it }
                    else -> ProfileScreen(vm) { detail = it }
                }
            }
        }
    }
}

@Composable private fun HomeScreen(vm: MainViewModel, search: () -> Unit, open: (String) -> Unit) {
    val state by vm.popular.collectAsState(); val selected by vm.channel.collectAsState(); val profile by vm.profile.collectAsState()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            NetworkImage(profile.value?.face.orEmpty(), "头像", Modifier.size(38.dp).clip(CircleShape), 80, 80)
            Surface(Modifier.weight(1f).padding(horizontal = 12.dp).height(44.dp).clickable(onClick = search), RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(.65f)) {
                Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Search, null); Spacer(Modifier.width(8.dp)); Text("搜索视频、UP 主", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(vm.channels) { channel -> FilterChip(selected == channel, { vm.selectChannel(channel) }, { Text(channel.title) }) }
        }
        StateBody(state, vm::refreshPopular) { VideoGrid(it, open) }
    }
}

@Composable private fun SearchScreen(vm: MainViewModel, close: () -> Unit, open: (String) -> Unit) {
    val state by vm.search.collectAsState(); var query by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(close) { Icon(Icons.Default.ArrowBack, "返回") }
            TextField(query, { query = it }, Modifier.weight(1f), placeholder = { Text("搜索视频") }, leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true, shape = RoundedCornerShape(18.dp), colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { vm.search(query) }))
        }
        StateBody(state, { vm.search(query) }) { if (it.isEmpty()) Empty("输入关键词开始搜索") else VideoGrid(it, open) }
    }
}

@Composable private fun VideoGrid(videos: List<Video>, open: (String) -> Unit) {
    LazyVerticalGrid(GridCells.Fixed(2), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        gridItems(videos, key = { it.bvid }) { VideoCard(it) { open(it.bvid) } }
    }
}

@Composable private fun VideoCard(video: Video, click: () -> Unit) {
    Surface(Modifier.clickable(onClick = click), RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, border = CardDefaults.outlinedCardBorder()) {
        Column { Box { NetworkImage(video.coverUrl, video.title, Modifier.fillMaxWidth().aspectRatio(16 / 9f), 480, 270)
            Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color.Black.copy(.42f)).padding(horizontal = 7.dp, vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("▶ ${formatCount(video.views)}", color = Color.White, fontSize = 11.sp); Text(formatDuration(video.duration), color = Color.White, fontSize = 11.sp)
            }
        }; Column(Modifier.padding(10.dp)) { Text(clean(video.title), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis); Spacer(Modifier.height(5.dp)); Text(video.creator, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) } }
    }
}

@Composable private fun DynamicScreen(vm: MainViewModel, open: (String) -> Unit) {
    val profile by vm.profile.collectAsState(); val state by vm.dynamics.collectAsState()
    LaunchedEffect(Unit) { if (profile.value?.isLogin == true && state.value == null) vm.loadDynamics() }
    Column { CompactTitle("动态", "关注的最新投稿")
        if (profile.value?.isLogin != true) Empty("登录后查看关注动态") else StateBody(state, vm::loadDynamics) { list ->
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { items(list, key = { it.id }) { item ->
                Card(onClick = { open(item.video.bvid) }, shape = RoundedCornerShape(18.dp)) { Column { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.AccountCircle, null); Spacer(Modifier.width(8.dp)); Column { Text(item.video.creator, fontWeight = FontWeight.SemiBold); Text(item.time, fontSize = 12.sp) } }; NetworkImage(item.video.coverUrl, item.video.title, Modifier.fillMaxWidth().aspectRatio(16 / 9f), 720, 405); Column(Modifier.padding(12.dp)) { Text(item.video.title, fontWeight = FontWeight.Bold); if (item.text.isNotBlank()) Text(item.text, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
            } }
        }
    }
}

@Composable private fun DetailScreen(bvid: String, vm: MainViewModel, open: (String) -> Unit) {
    val detail by vm.details.collectAsState(); val stream by vm.playUrl.collectAsState(); val comments by vm.comments.collectAsState(); val related by vm.related.collectAsState(); val interaction by vm.interaction.collectAsState(); val replies by vm.replies.collectAsState(); val danmaku by vm.danmaku.collectAsState()
    LaunchedEffect(bvid) { vm.loadDetails(bvid) }
    StateBody(detail, { vm.loadDetails(bvid) }) { video ->
        var cid by remember(video.bvid) { mutableLongStateOf(video.cid.takeIf { it > 0 } ?: video.pages.firstOrNull()?.cid ?: 0) }; var section by rememberSaveable(video.bvid) { mutableIntStateOf(0) }; var expanded by rememberSaveable(video.bvid) { mutableStateOf(false) }
        LazyColumn(Modifier.fillMaxSize()) {
            item { Box(Modifier.fillMaxWidth().aspectRatio(16 / 9f).background(Color.Black)) { when { stream.value != null -> VideoPlayer("${video.bvid}:$cid", stream.value!!, vm.playbackHeaders(), danmaku.value.orEmpty()) { vm.loadStream(video.bvid, cid, it) }; stream.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White); else -> Text(stream.error ?: "播放器暂不可用", Modifier.align(Alignment.Center), color = Color.White) } } }
            item { Column(Modifier.padding(16.dp).animateContentSize()) { Text(clean(video.title), fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = if (expanded) 8 else 2, overflow = TextOverflow.Ellipsis); Text("${formatCount(video.views)}播放 · ${formatDate(video.pubdate)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); TextButton({ expanded = !expanded }) { Text(if (expanded) "收起" else "展开简介") }; if (expanded) Text(video.desc.ifBlank { "暂无简介" }, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            item { Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) { NetworkImage(video.owner.face, video.creator, Modifier.size(44.dp).clip(CircleShape), 96, 96); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(video.creator, fontWeight = FontWeight.Bold); Text("UID ${video.owner.mid}", fontSize = 11.sp) }; InteractionButton(Icons.Default.ThumbUp, if (interaction.value?.liked == true) "已赞" else formatCount(video.stat.like), interaction.value?.liked == true) { vm.toggleLike(video.aid) }; InteractionButton(Icons.Default.Schedule, "稍后", interaction.value?.watchLater == true) { vm.toggleWatchLater(video.aid) }; InteractionButton(Icons.Default.Star, "收藏", interaction.value?.favorite == true) { vm.toggleFavorite(video.aid) } } }
            if (interaction.error != null) item { Text(interaction.error!!, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            if (video.pages.size > 1) item { LazyRow(contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(video.pages) { page -> FilterChip(cid == page.cid, { cid = page.cid; vm.loadStream(video.bvid, cid); vm.loadDanmaku(cid) }, { Text("P${page.page} ${page.part}", maxLines = 1) }) } } }
            item { TabRow(section) { listOf("简介", "评论 ${formatCount(video.stat.reply)}").forEachIndexed { i, text -> Tab(section == i, { section = i }, { Text(text) }) } } }
            if (section == 0) { item { SectionTitle("相关推荐") }; items(related.value.orEmpty(), key = { it.bvid }) { item -> RelatedRow(item) { open(item.bvid) } } }
            else { items(comments.value.orEmpty(), key = { it.rpid }) { comment -> CommentRow(comment, replies[comment.rpid], { vm.loadReplies(comment.rpid) }) }; item { TextButton(vm::loadComments, Modifier.fillMaxWidth(), enabled = !comments.loading) { Text(if (comments.loading) "加载中" else "加载更多") } } }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable private fun InteractionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, click: () -> Unit) { Column(Modifier.sizeIn(minWidth = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) { IconToggleButton(selected, { click() }) { Icon(icon, label) }; Text(label, fontSize = 10.sp, color = if (selected) MaterialTheme.colorScheme.primary else LocalContentColor.current) } }
@Composable private fun RelatedRow(video: Video, click: () -> Unit) { Row(Modifier.fillMaxWidth().clickable(onClick = click).padding(horizontal = 16.dp, vertical = 7.dp)) { NetworkImage(video.coverUrl, video.title, Modifier.width(140.dp).aspectRatio(16 / 9f).clip(RoundedCornerShape(14.dp)), 320, 180); Spacer(Modifier.width(10.dp)); Column { Text(clean(video.title), fontWeight = FontWeight.SemiBold, maxLines = 2); Text(video.creator, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("${formatCount(video.views)} 播放", fontSize = 11.sp) } } }
@Composable private fun CommentRow(comment: Comment, state: ContentState<List<Comment>>?, load: () -> Unit) { Column(Modifier.padding(horizontal = 16.dp, vertical = 9.dp)) { Row { NetworkImage(comment.member.avatar, comment.member.uname, Modifier.size(36.dp).clip(CircleShape), 72, 72); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(comment.member.uname.ifBlank { "用户" }, fontWeight = FontWeight.SemiBold, fontSize = 13.sp); Text(comment.content.message, lineHeight = 20.sp); Text("${formatDate(comment.ctime)} · ${comment.like}赞", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); if (comment.rcount > 0 && state == null) TextButton(load) { Text("展开 ${comment.rcount} 条回复") } } }; if (state?.loading == true) LinearProgressIndicator(Modifier.fillMaxWidth()); state?.value.orEmpty().forEach { reply -> Text("${reply.member.uname}: ${reply.content.message}", Modifier.padding(start = 46.dp, top = 6.dp), fontSize = 13.sp) } }
}

@Composable private fun ProfileScreen(vm: MainViewModel, open: (String) -> Unit) {
    val profile by vm.profile.collectAsState(); val login by vm.login.collectAsState(); val history by vm.history.collectAsState(); val later by vm.watchLater.collectAsState(); val favorites by vm.favorites.collectAsState()
    Box { StateBody(profile, vm::refreshProfile) { user -> if (!user.isLogin) SignedOut(vm::beginLogin) else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { NetworkImage(user.face, user.uname, Modifier.size(64.dp).clip(CircleShape), 144, 144); Spacer(Modifier.width(14.dp)); Column { Text(user.uname, fontSize = 21.sp, fontWeight = FontWeight.Bold); Text("LV${user.level_info.currentLevel} · UID ${user.mid}", color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
        item { PreviewCard("最近观看", history.value.orEmpty().map { it.title to it.history.bvid }, open) }
        item { PreviewCard("稍后再看", later.value.orEmpty().map { clean(it.title) to it.bvid }, open) }
        item { Card(shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(14.dp)) { Text("收藏夹", fontWeight = FontWeight.Bold); favorites.value.orEmpty().take(4).forEach { Text("${it.title} · ${it.media_count}", Modifier.padding(vertical = 6.dp)) }; if (favorites.value.isNullOrEmpty()) Text("暂无内容", Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
        item { Card(shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(16.dp)) { Text("设置与关于", fontWeight = FontWeight.Bold); Text("版本 ${BuildConfig.VERSION_NAME}", Modifier.padding(top = 10.dp)); Text("图片由 Coil 自动管理内存与磁盘缓存", Modifier.padding(top = 8.dp)); Text("非官方客户端。数据来自公开 Web API，接口可能变更。", Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
        item { OutlinedButton(vm::logout, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("退出登录") } }
    } }; if (login !is LoginState.Idle) LoginDialog(login, vm::beginLogin, vm::dismissLogin) }
}

@Composable private fun PreviewCard(title: String, values: List<Pair<String, String>>, open: (String) -> Unit) { Card(shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(14.dp)) { Text(title, fontWeight = FontWeight.Bold); values.take(4).forEach { (name, id) -> Text(name, Modifier.fillMaxWidth().clickable(enabled = id.isNotBlank()) { open(id) }.padding(vertical = 9.dp), maxLines = 1, overflow = TextOverflow.Ellipsis) }; if (values.isEmpty()) Text("暂无内容", Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
@Composable private fun SignedOut(login: () -> Unit) { Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Icon(Icons.Default.AccountCircle, null, Modifier.size(64.dp)); Text("登录后同步内容", fontSize = 21.sp, fontWeight = FontWeight.Bold); Text("Cookie 将加密保存在本机"); Button(login, Modifier.padding(top = 18.dp).heightIn(min = 48.dp)) { Text("扫码登录") } } }
@Composable private fun LoginDialog(state: LoginState, retry: () -> Unit, dismiss: () -> Unit) { Dialog(dismiss) { Card(shape = RoundedCornerShape(22.dp)) { Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("扫码登录", fontSize = 20.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(16.dp)); when (state) { LoginState.Loading -> CircularProgressIndicator(); is LoginState.Ready, is LoginState.Scanned -> { val qr = if (state is LoginState.Ready) state.qr else (state as LoginState.Scanned).qr; QrImage(qr.url); Text(if (state is LoginState.Scanned) "请在手机上确认" else "使用官方客户端扫描") }; LoginState.Success -> Text("登录成功"); is LoginState.Error -> { Text(state.message, color = MaterialTheme.colorScheme.error); TextButton(retry) { Text("重试") } }; LoginState.Idle -> Unit }; TextButton(dismiss) { Text("关闭") } } } } }
@Composable private fun QrImage(value: String) { var bitmap by remember(value) { mutableStateOf<Bitmap?>(null) }; LaunchedEffect(value) { bitmap = withContext(Dispatchers.Default) { qr(value) } }; bitmap?.let { Image(it.asImageBitmap(), "登录二维码", Modifier.size(210.dp)) } ?: CircularProgressIndicator() }

@Composable private fun CompactTitle(title: String, subtitle: String) { Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) { Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold); Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
@Composable private fun SectionTitle(title: String) { Text(title, Modifier.padding(16.dp), fontSize = 18.sp, fontWeight = FontWeight.Bold) }
@Composable private fun <T> StateBody(state: ContentState<T>, retry: () -> Unit, content: @Composable (T) -> Unit) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { when { state.value != null -> content(state.value); state.loading -> CircularProgressIndicator(); else -> Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(state.error ?: "暂无内容"); IconButton(retry) { Icon(Icons.Default.Refresh, "重试") } } } } }
@Composable private fun Empty(text: String) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
@Composable private fun NetworkImage(url: String, description: String, modifier: Modifier, width: Int, height: Int) { AsyncImage(ImageRequest.Builder(LocalContext.current).data(normalize(url)).size(width, height).crossfade(true).build(), description, modifier, placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant), error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant), contentScale = ContentScale.Crop) }

private fun qr(value: String): Bitmap { val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 600, 600); return Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888).also { image -> for (x in 0 until 600) for (y in 0 until 600) image.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE) } }
private fun clean(value: String) = value.replace(Regex("<[^>]*>"), "").replace("&quot;", "\"").replace("&amp;", "&")
private fun normalize(value: String) = if (value.startsWith("//")) "https:$value" else value.replace("http://", "https://")
private fun formatDuration(seconds: Int) = "%d:%02d".format(seconds / 60, seconds % 60)
private fun formatCount(value: Long) = when { value >= 100_000_000 -> "%.1f亿".format(value / 100_000_000.0); value >= 10_000 -> "%.1f万".format(value / 10_000.0); else -> value.toString() }
private fun formatDate(epoch: Long) = if (epoch <= 0) "" else SimpleDateFormat("MM-dd", Locale.CHINA).format(Date(epoch * 1000))
@Composable private fun tabTitle(tab: Tab) = stringResource(when (tab) { Tab.Home -> dev.opencode.bilimobile.R.string.home; Tab.Dynamic -> dev.opencode.bilimobile.R.string.dynamic; Tab.Profile -> dev.opencode.bilimobile.R.string.profile })
