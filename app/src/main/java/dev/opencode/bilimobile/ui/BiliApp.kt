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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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

private enum class RootTab { Home, Dynamic, Profile }

@Composable fun BiliApp(vm: MainViewModel = viewModel()) {
    var tab by rememberSaveable { mutableStateOf(RootTab.Home) }
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
    var search by rememberSaveable { mutableStateOf(false) }
    BackHandler(detail != null || search) { if (detail != null) detail = null else search = false }
    Scaffold(bottomBar = {
        AnimatedVisibility(detail == null && !search, enter = fadeIn(), exit = fadeOut()) {
            NavigationBar(Modifier.height(62.dp), containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .96f), tonalElevation = 0.dp) {
                RootTab.entries.forEach { item -> NavigationBarItem(tab == item, { tab = item }, {
                    Icon(when (item) { RootTab.Home -> Icons.Default.Home; RootTab.Dynamic -> Icons.Default.Subscriptions; RootTab.Profile -> Icons.Default.Person }, tabTitle(item))
                }, label = { Text(tabTitle(item)) }, alwaysShowLabel = true) }
            }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AnimatedContent(Pair(detail, search), label = "destination") { (id, searching) ->
                when { id != null -> DetailScreen(id, vm) { detail = it }
                    searching -> SearchScreen(vm, { search = false }, { detail = it })
                    tab == RootTab.Home -> HomeScreen(vm, { search = true }, { detail = it })
                    tab == RootTab.Dynamic -> DynamicScreen(vm) { detail = it }
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
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items(vm.channels) { channel -> Column(Modifier.semantics { role = Role.Tab; this.selected = selected == channel }.clickable { vm.selectChannel(channel) }.padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(channel.title, fontSize = 14.sp, fontWeight = if (selected == channel) FontWeight.SemiBold else FontWeight.Normal, color = if (selected == channel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(5.dp)); Box(Modifier.width(18.dp).height(2.dp).background(if (selected == channel) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)) } }
        }
        if (state.error != null && state.value != null) ErrorBanner(state.error!!, vm::refreshPopular)
        StateBody(state, vm::refreshPopular, skeleton = true) { VideoGrid(it, open) }
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
    LazyVerticalGrid(GridCells.Fixed(2), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        gridItems(videos, key = { it.bvid }) { VideoCard(it) { open(it.bvid) } }
    }
}

@Composable private fun VideoCard(video: Video, click: () -> Unit) {
    Surface(Modifier.clickable(onClick = click), RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        Column { Box { NetworkImage(video.coverUrl, video.title, Modifier.fillMaxWidth().aspectRatio(16 / 9f), 480, 270)
            Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color.Black.copy(.42f)).padding(horizontal = 7.dp, vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("▶ ${formatCount(video.views)}", color = Color.White, fontSize = 11.sp); Text(formatDuration(video.duration), color = Color.White, fontSize = 11.sp)
            }
        }; Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) { Text(clean(video.title), fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis); Spacer(Modifier.height(5.dp)); Text("UP ${video.creator}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) } }
    }
}

@Composable private fun DynamicScreen(vm: MainViewModel, open: (String) -> Unit) {
    val profile by vm.profile.collectAsState(); val state by vm.dynamics.collectAsState()
    LaunchedEffect(Unit) { if (profile.value?.isLogin == true && state.value == null) vm.loadDynamics() }
    Column { CompactTitle("动态", "关注的最新投稿")
        if (profile.value?.isLogin != true) Empty("登录后查看关注动态") else StateBody(state, vm::loadDynamics) { list ->
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { items(list, key = { it.id }) { item ->
                Card(onClick = { open(item.video.bvid) }, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Column { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { NetworkImage(item.avatar, item.video.creator, Modifier.size(36.dp).clip(CircleShape), 72, 72); Spacer(Modifier.width(9.dp)); Column { Text(item.video.creator, fontWeight = FontWeight.SemiBold, fontSize = 14.sp); Text(item.time, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } }; NetworkImage(item.video.coverUrl, item.video.title, Modifier.fillMaxWidth().aspectRatio(16 / 9f), 720, 405); Column(Modifier.padding(12.dp)) { Text(item.video.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp); if (item.text.isNotBlank()) Text(item.text, maxLines = 2, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
            } }
        }
    }
}

@Composable private fun DetailScreen(bvid: String, vm: MainViewModel, open: (String) -> Unit) {
    val detail by vm.details.collectAsState(); val stream by vm.playUrl.collectAsState(); val comments by vm.comments.collectAsState(); val related by vm.related.collectAsState(); val interaction by vm.interaction.collectAsState(); val replies by vm.replies.collectAsState(); val danmaku by vm.danmaku.collectAsState(); val favorites by vm.favorites.collectAsState()
    var chooseFolder by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(bvid) { vm.loadDetails(bvid) }
    StateBody(detail, { vm.loadDetails(bvid) }) { video ->
        var cid by remember(video.bvid) { mutableLongStateOf(video.cid.takeIf { it > 0 } ?: video.pages.firstOrNull()?.cid ?: 0) }; var section by rememberSaveable(video.bvid) { mutableIntStateOf(0) }; var expanded by rememberSaveable(video.bvid) { mutableStateOf(false) }
        LazyColumn(Modifier.fillMaxSize()) {
            item { Box(Modifier.fillMaxWidth().aspectRatio(16 / 9f).background(Color.Black)) { when { stream.value != null -> VideoPlayer("${video.bvid}:$cid", stream.value!!, vm.playbackHeaders(), danmaku.value.orEmpty(), { vm.loadStream(video.bvid, cid, it) }, { vm.fallbackStream(video.bvid, cid, stream.value!!.quality, it) }); stream.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White); else -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) { Text(stream.error ?: "播放器暂不可用", color = Color.White); TextButton({ vm.loadStream(video.bvid, cid) }) { Text("重试") } } } } }
            item { Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp).animateContentSize()) { Text(clean(video.title), fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, maxLines = if (expanded) 8 else 2, overflow = TextOverflow.Ellipsis); Spacer(Modifier.height(5.dp)); Text("${formatCount(video.views)}播放 · ${formatDate(video.pubdate)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); TextButton({ expanded = !expanded }, contentPadding = PaddingValues(0.dp)) { Text(if (expanded) "收起" else "展开简介") }; if (expanded) Text(video.desc.ifBlank { "暂无简介" }, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            item { Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { NetworkImage(video.owner.face, video.creator, Modifier.size(42.dp).clip(CircleShape), 96, 96); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(video.creator, fontWeight = FontWeight.SemiBold); Text("UID ${video.owner.mid}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }; InteractionButton(Icons.Default.ThumbUp, if (interaction.value?.liked == true) "已赞" else formatCount(video.stat.like), interaction.value?.liked == true, interaction.value?.liked != null && !interaction.loading) { vm.toggleLike(video.aid) }; InteractionButton(Icons.Default.Schedule, "稍后", interaction.value?.watchLater == true, interaction.value?.watchLater != null && !interaction.loading) { vm.toggleWatchLater(video.aid) }; InteractionButton(Icons.Default.Star, "收藏", interaction.value?.favorite == true, interaction.value?.favoriteFolderIds != null && !interaction.loading) { chooseFolder = true } } }
            if (interaction.loading || interaction.error != null || (interaction.value != null && listOf(interaction.value?.liked, interaction.value?.watchLater, interaction.value?.favorite).any { it == null })) item { Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) { Text(interaction.error ?: if (interaction.loading) "互动状态加载中" else "部分互动状态不可用", Modifier.weight(1f), color = if (interaction.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp); if (!interaction.loading) TextButton({ vm.loadInteraction(video.aid) }) { Text("重试") } } }
            if (video.pages.size > 1) item { LazyRow(contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(video.pages) { page -> FilterChip(cid == page.cid, { cid = page.cid; vm.loadStream(video.bvid, cid); vm.loadDanmaku(cid) }, { Text("P${page.page} ${page.part}", maxLines = 1) }) } } }
            item { TabRow(section) { listOf("简介", "评论 ${formatCount(video.stat.reply)}").forEachIndexed { i, text ->
                Tab(selected = section == i, onClick = { section = i }, text = { Text(text) })
            } } }
            if (section == 0) { item { SectionTitle("相关推荐") }; items(related.value.orEmpty(), key = { it.bvid }) { item -> RelatedRow(item) { open(item.bvid) } } }
            else { items(comments.value.orEmpty(), key = { it.rpid }) { comment -> CommentRow(comment, replies[comment.rpid], { vm.loadReplies(comment.rpid) }) }; item { TextButton(vm::loadComments, Modifier.fillMaxWidth(), enabled = !comments.loading) { Text(if (comments.loading) "加载中" else "加载更多") } } }
            item { Spacer(Modifier.height(24.dp)) }
        }
        if (chooseFolder) { val removing = interaction.value?.favorite == true; val folders = if (removing) favorites.value.orEmpty().filter { it.id in interaction.value?.favoriteFolderIds.orEmpty() } else favorites.value.orEmpty(); AlertDialog(onDismissRequest = { chooseFolder = false }, title = { Text(if (removing) "选择要取消收藏的文件夹" else "收藏到") }, text = { when { favorites.loading -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; favorites.error != null -> Column { Text(favorites.error!!, color = MaterialTheme.colorScheme.error); TextButton(vm::refreshFavorites) { Text("重试") } }; folders.isEmpty() -> Column { Text(if (removing) "没有包含此视频的收藏夹" else "没有可用收藏夹，请先创建收藏夹"); TextButton(vm::refreshFavorites) { Text("刷新") } }; else -> LazyColumn { items(folders, key = { it.id }) { folder -> ListItem(headlineContent = { Text(folder.title) }, supportingContent = { Text("${folder.media_count} 个内容") }, modifier = Modifier.clickable { chooseFolder = false; vm.toggleFavorite(video.aid, folder.id) }) } } } }, confirmButton = {}, dismissButton = { TextButton({ chooseFolder = false }) { Text("取消") } }) }
    }
}

@Composable private fun InteractionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, enabled: Boolean, click: () -> Unit) { Column(Modifier.sizeIn(minWidth = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) { IconToggleButton(selected, { click() }, enabled = enabled) { Icon(icon, label) }; Text(label, fontSize = 10.sp, color = if (selected) MaterialTheme.colorScheme.primary else LocalContentColor.current.copy(alpha = if (enabled) 1f else .38f)) } }
@Composable private fun RelatedRow(video: Video, click: () -> Unit) { Row(Modifier.fillMaxWidth().clickable(onClick = click).padding(horizontal = 16.dp, vertical = 7.dp)) { NetworkImage(video.coverUrl, video.title, Modifier.width(140.dp).aspectRatio(16 / 9f).clip(RoundedCornerShape(14.dp)), 320, 180); Spacer(Modifier.width(10.dp)); Column { Text(clean(video.title), fontWeight = FontWeight.SemiBold, maxLines = 2); Text(video.creator, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("${formatCount(video.views)} 播放", fontSize = 11.sp) } } }
@Composable private fun CommentRow(comment: Comment, state: ContentState<List<Comment>>?, load: () -> Unit) { Column(Modifier.padding(horizontal = 16.dp, vertical = 9.dp)) { Row { NetworkImage(comment.member.avatar, comment.member.uname, Modifier.size(36.dp).clip(CircleShape), 72, 72); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(comment.member.uname.ifBlank { "用户" }, fontWeight = FontWeight.SemiBold, fontSize = 13.sp); Text(comment.content.message, lineHeight = 20.sp); Text("${formatDate(comment.ctime)} · ${comment.like}赞", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); if (comment.rcount > 0 && state == null) TextButton(load) { Text("展开 ${comment.rcount} 条回复") } } }; if (state?.loading == true) LinearProgressIndicator(Modifier.fillMaxWidth()); state?.value.orEmpty().forEach { reply -> Text("${reply.member.uname}: ${reply.content.message}", Modifier.padding(start = 46.dp, top = 6.dp), fontSize = 13.sp) } }
}

@Composable private fun ProfileScreen(vm: MainViewModel, open: (String) -> Unit) {
    val profile by vm.profile.collectAsState(); val login by vm.login.collectAsState(); val history by vm.history.collectAsState(); val later by vm.watchLater.collectAsState(); val favorites by vm.favorites.collectAsState()
    Box { StateBody(profile, vm::refreshProfile) { user -> if (!user.isLogin) SignedOut(vm::beginLogin) else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { NetworkImage(user.face, user.uname, Modifier.size(64.dp).clip(CircleShape), 144, 144); Spacer(Modifier.width(14.dp)); Column { Text(user.uname, fontSize = 21.sp, fontWeight = FontWeight.Bold); Text("LV${user.level_info.currentLevel} · UID ${user.mid}", color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
        item { ShortcutRow(history.value.orEmpty().size, favorites.value.orEmpty().size, later.value.orEmpty().size) }
        item { PreviewCard("最近观看", history.value.orEmpty().map { PreviewItem(it.title, it.history.bvid, it.cover) }, open) }
        item { PreviewCard("稍后再看", later.value.orEmpty().map { PreviewItem(clean(it.title), it.bvid, it.coverUrl) }, open) }
        item { Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Column(Modifier.padding(14.dp)) { Text("收藏夹", fontWeight = FontWeight.SemiBold); favorites.value.orEmpty().take(4).forEach { HorizontalDivider(); Text("${it.title}  ·  ${it.media_count}", Modifier.padding(vertical = 9.dp), fontSize = 14.sp) }; if (favorites.value.isNullOrEmpty()) Text("暂无内容", Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
        item { Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Column(Modifier.padding(16.dp)) { Text("设置与关于", fontWeight = FontWeight.SemiBold); Text("版本 ${BuildConfig.VERSION_NAME}", Modifier.padding(top = 10.dp), fontSize = 13.sp); Text("图片缓存由系统自动管理", Modifier.padding(top = 8.dp), fontSize = 13.sp); Text("非官方客户端。数据来自公开 Web API，接口可能变更。", Modifier.padding(top = 8.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
        item { OutlinedButton(vm::logout, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("退出登录") } }
    } }; if (login !is LoginState.Idle) LoginDialog(login, vm::beginLogin, vm::dismissLogin) }
}

private data class PreviewItem(val title: String, val id: String, val image: String)
@Composable private fun PreviewCard(title: String, values: List<PreviewItem>, open: (String) -> Unit) { Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Column(Modifier.padding(14.dp)) { Text(title, fontWeight = FontWeight.SemiBold); values.take(3).forEach { item -> Row(Modifier.fillMaxWidth().clickable(enabled = item.id.isNotBlank()) { open(item.id) }.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) { NetworkImage(item.image, item.title, Modifier.size(72.dp, 42.dp).clip(RoundedCornerShape(7.dp)), 160, 90); Spacer(Modifier.width(10.dp)); Text(item.title, Modifier.weight(1f), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) } }; if (values.isEmpty()) Text("暂无内容", Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
@Composable private fun ShortcutRow(history: Int, favorites: Int, later: Int) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(Triple(Icons.Outlined.History, "历史", history), Triple(Icons.Outlined.Star, "收藏夹", favorites), Triple(Icons.Outlined.Schedule, "稍后再看", later)).forEach { (icon, label, count) -> Surface(Modifier.weight(1f), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface) { Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Text(label, fontSize = 12.sp); Text(count.toString(), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } } } } }
@Composable private fun SignedOut(login: () -> Unit) { Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Icon(Icons.Default.AccountCircle, null, Modifier.size(64.dp)); Text("登录后同步内容", fontSize = 21.sp, fontWeight = FontWeight.Bold); Text("Cookie 将加密保存在本机"); Button(login, Modifier.padding(top = 18.dp).heightIn(min = 48.dp)) { Text("扫码登录") } } }
@Composable private fun LoginDialog(state: LoginState, retry: () -> Unit, dismiss: () -> Unit) { Dialog(dismiss) { Card(shape = RoundedCornerShape(22.dp)) { Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("扫码登录", fontSize = 20.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(16.dp)); when (state) { LoginState.Loading -> CircularProgressIndicator(); is LoginState.Ready, is LoginState.Scanned -> { val qr = if (state is LoginState.Ready) state.qr else (state as LoginState.Scanned).qr; QrImage(qr.url); Text(if (state is LoginState.Scanned) "请在手机上确认" else "使用官方客户端扫描") }; LoginState.Success -> Text("登录成功"); is LoginState.Error -> { Text(state.message, color = MaterialTheme.colorScheme.error); TextButton(retry) { Text("重试") } }; LoginState.Idle -> Unit }; TextButton(dismiss) { Text("关闭") } } } } }
@Composable private fun QrImage(value: String) { var bitmap by remember(value) { mutableStateOf<Bitmap?>(null) }; LaunchedEffect(value) { bitmap = withContext(Dispatchers.Default) { qr(value) } }; bitmap?.let { Image(it.asImageBitmap(), "登录二维码", Modifier.size(210.dp)) } ?: CircularProgressIndicator() }

@Composable private fun CompactTitle(title: String, subtitle: String) { Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) { Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold); Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
@Composable private fun SectionTitle(title: String) { Text(title, Modifier.padding(16.dp), fontSize = 18.sp, fontWeight = FontWeight.Bold) }
@Composable private fun <T> StateBody(state: ContentState<T>, retry: () -> Unit, skeleton: Boolean = false, content: @Composable (T) -> Unit) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { when { state.value != null -> content(state.value); state.loading && skeleton -> SkeletonGrid(); state.loading -> CircularProgressIndicator(); else -> Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(state.error ?: "暂无内容"); TextButton(retry) { Text("重试") } } } } }
@Composable private fun ErrorBanner(message: String, retry: () -> Unit) { Surface(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.errorContainer) { Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) { Text(message, Modifier.weight(1f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis); TextButton(retry) { Text("重试") } } } }
@Composable private fun SkeletonGrid() { LazyVerticalGrid(GridCells.Fixed(2), contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { items(8) { Column { Box(Modifier.fillMaxWidth().aspectRatio(16 / 9f).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)); Spacer(Modifier.height(8.dp)); Box(Modifier.fillMaxWidth(.9f).height(13.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)); Spacer(Modifier.height(6.dp)); Box(Modifier.fillMaxWidth(.55f).height(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) } } } }
@Composable private fun Empty(text: String) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
@Composable private fun NetworkImage(url: String, description: String, modifier: Modifier, width: Int, height: Int) { AsyncImage(ImageRequest.Builder(LocalContext.current).data(normalize(url)).size(width, height).crossfade(true).build(), description, modifier, placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant), error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant), contentScale = ContentScale.Crop) }

private fun qr(value: String): Bitmap { val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 600, 600); return Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888).also { image -> for (x in 0 until 600) for (y in 0 until 600) image.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE) } }
private fun clean(value: String) = value.replace(Regex("<[^>]*>"), "").replace("&quot;", "\"").replace("&amp;", "&")
private fun normalize(value: String) = if (value.startsWith("//")) "https:$value" else value.replace("http://", "https://")
private fun formatDuration(seconds: Int) = "%d:%02d".format(seconds / 60, seconds % 60)
private fun formatCount(value: Long) = when { value >= 100_000_000 -> "%.1f亿".format(value / 100_000_000.0); value >= 10_000 -> "%.1f万".format(value / 10_000.0); else -> value.toString() }
private fun formatDate(epoch: Long) = if (epoch <= 0) "" else SimpleDateFormat("MM-dd", Locale.CHINA).format(Date(epoch * 1000))
@Composable private fun tabTitle(tab: RootTab) = stringResource(when (tab) { RootTab.Home -> dev.opencode.bilimobile.R.string.home; RootTab.Dynamic -> dev.opencode.bilimobile.R.string.dynamic; RootTab.Profile -> dev.opencode.bilimobile.R.string.profile })
