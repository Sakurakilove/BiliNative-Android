package dev.opencode.bilimobile.ui

import android.graphics.Bitmap
import android.content.Intent
import android.net.Uri
import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.ui.viewinterop.AndroidView
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
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject

private enum class RootTab { Home, Dynamic, Profile }
private sealed interface AccountDestination { data object History : AccountDestination; data object Favorites : AccountDestination; data object Later : AccountDestination; data class Folder(val id: Long, val title: String) : AccountDestination }

@Composable fun BiliApp(vm: MainViewModel = viewModel()) {
    var tab by rememberSaveable { mutableStateOf(RootTab.Home) }
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
    var search by rememberSaveable { mutableStateOf(false) }
    var accountType by rememberSaveable { mutableStateOf("") }; var accountId by rememberSaveable { mutableLongStateOf(0L) }; var accountTitle by rememberSaveable { mutableStateOf("") }
    val account: AccountDestination? = when (accountType) { "history" -> AccountDestination.History; "favorites" -> AccountDestination.Favorites; "later" -> AccountDestination.Later; "folder" -> AccountDestination.Folder(accountId, accountTitle); else -> null }
    fun setAccount(value: AccountDestination?) { when (value) { AccountDestination.History -> accountType = "history"; AccountDestination.Favorites -> accountType = "favorites"; AccountDestination.Later -> accountType = "later"; is AccountDestination.Folder -> { accountType = "folder"; accountId = value.id; accountTitle = value.title }; null -> { accountType = ""; accountId = 0; accountTitle = "" } } }
    BackHandler(detail != null || search || account != null) { when { detail != null -> detail = null; account is AccountDestination.Folder -> setAccount(AccountDestination.Favorites); account != null -> setAccount(null); else -> search = false } }
    Scaffold(bottomBar = {
        AnimatedVisibility(detail == null && !search && account == null, enter = fadeIn(), exit = fadeOut()) {
            NavigationBar(Modifier.height(62.dp), containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .96f), tonalElevation = 0.dp) {
                RootTab.entries.forEach { item -> NavigationBarItem(tab == item, { tab = item }, {
                    Icon(when (item) { RootTab.Home -> Icons.Default.Home; RootTab.Dynamic -> Icons.Default.Subscriptions; RootTab.Profile -> Icons.Default.Person }, tabTitle(item))
                }, label = { Text(tabTitle(item)) }, alwaysShowLabel = true) }
            }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AnimatedContent(Triple(detail, search, account), label = "destination") { (id, searching, destination) ->
                when { id != null -> DetailScreen(id, vm) { detail = it }
                    searching -> SearchScreen(vm, { search = false }, { detail = it })
                    destination != null -> AccountScreen(destination, vm, ::setAccount, { detail = it })
                    tab == RootTab.Home -> HomeScreen(vm, { search = true }, { detail = it })
                    tab == RootTab.Dynamic -> DynamicScreen(vm) { detail = it }
                    else -> ProfileScreen(vm, ::setAccount) { detail = it }
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
    val detail by vm.details.collectAsState(); val stream by vm.playUrl.collectAsState(); val comments by vm.comments.collectAsState(); val related by vm.related.collectAsState(); val interaction by vm.interaction.collectAsState(); val replies by vm.replies.collectAsState(); val danmaku by vm.danmaku.collectAsState(); val favorites by vm.favorites.collectAsState(); val profile by vm.profile.collectAsState(); val commentPosting by vm.commentPosting.collectAsState(); val danmakuPosting by vm.danmakuPosting.collectAsState()
    var chooseFolder by rememberSaveable { mutableStateOf(false) }
    var chooseCoin by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(bvid) { vm.loadDetails(bvid) }
    StateBody(detail, { vm.loadDetails(bvid) }) { video ->
        var cid by remember(video.bvid) { mutableLongStateOf(video.cid.takeIf { it > 0 } ?: video.pages.firstOrNull()?.cid ?: 0) }; var section by rememberSaveable(video.bvid) { mutableIntStateOf(0) }; var expanded by rememberSaveable(video.bvid) { mutableStateOf(false) }; var commentText by rememberSaveable(video.bvid) { mutableStateOf("") }
        LazyColumn(Modifier.fillMaxSize()) {
            item { Box(Modifier.fillMaxWidth().aspectRatio(16 / 9f).background(Color.Black)) { when { stream.value != null -> VideoPlayer(key = "${video.bvid}:$cid", result = stream.value!!, headers = vm.playbackHeaders(), danmaku = danmaku, loggedIn = profile.value?.isLogin == true, danmakuPosting = danmakuPosting, retryDanmaku = { vm.loadDanmaku(cid) }, confirmDanmakuAfterRefresh = vm::clearDanmakuPostingAfterRefresh, sendDanmaku = vm::postDanmaku, quality = { vm.loadStream(video.bvid, cid, it) }, fallback = { vm.fallbackStream(video.bvid, cid, stream.value!!.quality, it) }); stream.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White); else -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) { Text(stream.error ?: stringResource(dev.opencode.bilimobile.R.string.player_unavailable), color = Color.White); TextButton({ vm.loadStream(video.bvid, cid) }) { Text(stringResource(dev.opencode.bilimobile.R.string.retry)) } } } } }
            item { Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp).animateContentSize()) { Text(clean(video.title), fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, maxLines = if (expanded) 8 else 2, overflow = TextOverflow.Ellipsis); Spacer(Modifier.height(5.dp)); Text("${formatCount(video.views)}播放 · ${formatDate(video.pubdate)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); TextButton({ expanded = !expanded }, contentPadding = PaddingValues(0.dp)) { Text(if (expanded) "收起" else "展开简介") }; if (expanded) Text(video.desc.ifBlank { "暂无简介" }, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            item { Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { NetworkImage(video.owner.face, video.creator, Modifier.size(38.dp).clip(CircleShape), 96, 96); Spacer(Modifier.width(6.dp)); Column(Modifier.weight(1f)) { Text(video.creator, fontWeight = FontWeight.SemiBold, maxLines = 1); Text("UID ${video.owner.mid}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }; InteractionButton(Icons.Default.ThumbUp, if (interaction.value?.liked == true) "已赞" else formatCount(video.stat.like), interaction.value?.liked == true, interaction.value?.liked != null && !interaction.loading) { vm.toggleLike(video.aid) }; InteractionButton(Icons.Default.Paid, interaction.value?.coinCount?.let { "硬币 $it" } ?: "硬币", false, interaction.value?.coinCount != null && interaction.value?.coinCount!! < 2 && !interaction.loading) { chooseCoin = true }; InteractionButton(Icons.Default.Schedule, "稍后", interaction.value?.watchLater == true, interaction.value?.watchLater != null && !interaction.loading) { vm.toggleWatchLater(video.aid) }; InteractionButton(Icons.Default.Star, "收藏", interaction.value?.favorite == true, interaction.value?.favoriteFolderIds != null && !interaction.loading) { chooseFolder = true } } }
            if (interaction.loading || interaction.error != null || (interaction.value != null && listOf(interaction.value?.liked, interaction.value?.watchLater, interaction.value?.favorite, interaction.value?.coinCount).any { it == null })) item { Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) { Text(interaction.error ?: if (interaction.loading) "互动状态加载中" else "部分互动状态不可用", Modifier.weight(1f), color = if (interaction.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp); if (!interaction.loading) TextButton({ vm.loadInteraction(video.aid) }) { Text(stringResource(dev.opencode.bilimobile.R.string.retry)) } } }
            if (video.pages.size > 1) item { LazyRow(contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(video.pages) { page -> FilterChip(cid == page.cid, { cid = page.cid; vm.loadStream(video.bvid, cid); vm.loadDanmaku(cid) }, { Text("P${page.page} ${page.part}", maxLines = 1) }) } } }
            item { TabRow(section) { listOf("简介", "评论 ${formatCount(video.stat.reply)}").forEachIndexed { i, text ->
                Tab(selected = section == i, onClick = { section = i }, text = { Text(text) })
            } } }
            if (section == 0) { item { SectionTitle("相关推荐") }; items(related.value.orEmpty(), key = { it.bvid }) { item -> RelatedRow(item) { open(item.bvid) } } }
            else { item { if (profile.value?.isLogin == true) CommentComposer(commentText, { commentText = it.take(1000) }, commentPosting, vm::clearCommentPostingAfterRefresh) { vm.postComment(commentText) { success -> if (success) commentText = "" } } else Text(stringResource(dev.opencode.bilimobile.R.string.comment_login_prompt), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }; items(comments.value.orEmpty(), key = { it.rpid }) { comment -> CommentRow(comment, replies[comment.rpid], { vm.loadReplies(comment.rpid) }) }; item { TextButton(vm::loadComments, Modifier.fillMaxWidth(), enabled = !comments.loading) { Text(if (comments.loading) stringResource(dev.opencode.bilimobile.R.string.loading) else stringResource(dev.opencode.bilimobile.R.string.load_more)) } } }
            item { Spacer(Modifier.height(24.dp)) }
        }
        if (chooseFolder) { val membership = interaction.value?.favoriteFolderIds.orEmpty(); val folders = favorites.value.orEmpty(); AlertDialog(onDismissRequest = { chooseFolder = false }, title = { Text("管理收藏夹") }, text = { when { favorites.loading -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; favorites.error != null -> Column { Text(favorites.error!!, color = MaterialTheme.colorScheme.error); TextButton(vm::refreshFavorites) { Text("重试") } }; folders.isEmpty() -> Column { Text("没有可用收藏夹，请先创建收藏夹"); TextButton(vm::refreshFavorites) { Text("刷新") } }; else -> LazyColumn { items(folders, key = { it.id }) { folder -> ListItem(headlineContent = { Text(folder.title) }, supportingContent = { Text("${folder.media_count} 个内容") }, leadingContent = { Checkbox(folder.id in membership, null) }, modifier = Modifier.clickable { vm.toggleFavorite(video.aid, folder.id) }) } } } }, confirmButton = { TextButton({ chooseFolder = false }) { Text("完成") } }) }
        if (chooseCoin) CoinDialog(interaction.value?.coinCount ?: 0, interaction.loading, interaction.error, { chooseCoin = false }) { count, like -> vm.addCoin(video.aid, count, like) }
    }
}

@Composable private fun CommentComposer(value: String, change: (String) -> Unit, state: ContentState<Unit>, refresh: () -> Unit, send: () -> Unit) { val blocked = state.error?.contains("刷新确认") == true; Column(Modifier.padding(12.dp)) { OutlinedTextField(value, change, Modifier.fillMaxWidth(), placeholder = { Text(stringResource(dev.opencode.bilimobile.R.string.comment_hint)) }, supportingText = { Text("${value.length}/1000") }, minLines = 2, maxLines = 4); state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }; Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { if (blocked) TextButton(refresh) { Text("刷新确认") }; Button(send, enabled = value.trim().length in 1..1000 && !state.loading && !blocked) { Text(if (state.loading) stringResource(dev.opencode.bilimobile.R.string.sending) else stringResource(dev.opencode.bilimobile.R.string.send_comment)) } } } }

@Composable private fun CoinDialog(coinCount: Int, posting: Boolean, error: String?, dismiss: () -> Unit, confirm: (Int, Boolean) -> Unit) { val max = (2 - coinCount).coerceAtLeast(0); var count by remember(max) { mutableIntStateOf(1.coerceAtMost(max)) }; var like by remember { mutableStateOf(false) }; AlertDialog(onDismissRequest = { if (!posting) dismiss() }, title = { Text(stringResource(dev.opencode.bilimobile.R.string.coin_title)) }, text = { Column { Text(stringResource(dev.opencode.bilimobile.R.string.coin_warning), color = MaterialTheme.colorScheme.error); Text("已投 $coinCount 枚，最多还可投 $max 枚", fontSize = 12.sp); error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }; Row { (1..max).forEach { value -> FilterChip(count == value, { count = value }, { Text("$value") }, Modifier.padding(end = 8.dp)) } }; Row(Modifier.clickable(enabled = max > 0) { like = !like }, verticalAlignment = Alignment.CenterVertically) { Checkbox(like, { like = it }, enabled = max > 0); Text(stringResource(dev.opencode.bilimobile.R.string.coin_like_too)) } } }, confirmButton = { TextButton({ confirm(count, like) }, enabled = !posting && max > 0 && error == null) { Text(stringResource(dev.opencode.bilimobile.R.string.confirm_pay)) } }, dismissButton = { TextButton(dismiss, enabled = !posting) { Text(stringResource(dev.opencode.bilimobile.R.string.cancel)) } }) }

@Composable private fun InteractionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, enabled: Boolean, click: () -> Unit) { Column(Modifier.sizeIn(minWidth = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) { IconToggleButton(selected, { click() }, enabled = enabled) { Icon(icon, label) }; Text(label, fontSize = 10.sp, color = if (selected) MaterialTheme.colorScheme.primary else LocalContentColor.current.copy(alpha = if (enabled) 1f else .38f)) } }
@Composable private fun RelatedRow(video: Video, click: () -> Unit) { Row(Modifier.fillMaxWidth().clickable(onClick = click).padding(horizontal = 16.dp, vertical = 7.dp)) { NetworkImage(video.coverUrl, video.title, Modifier.width(140.dp).aspectRatio(16 / 9f).clip(RoundedCornerShape(14.dp)), 320, 180); Spacer(Modifier.width(10.dp)); Column { Text(clean(video.title), fontWeight = FontWeight.SemiBold, maxLines = 2); Text(video.creator, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("${formatCount(video.views)} 播放", fontSize = 11.sp) } } }
@Composable private fun CommentRow(comment: Comment, state: ContentState<List<Comment>>?, load: () -> Unit) { Column(Modifier.padding(horizontal = 16.dp, vertical = 9.dp)) { Row { NetworkImage(comment.member.avatar, comment.member.uname, Modifier.size(36.dp).clip(CircleShape), 72, 72); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(comment.member.uname.ifBlank { "用户" }, fontWeight = FontWeight.SemiBold, fontSize = 13.sp); Text(comment.content.message, lineHeight = 20.sp); Text("${formatDate(comment.ctime)} · ${comment.like}赞", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); if (comment.rcount > 0 && state == null) TextButton(load) { Text("展开 ${comment.rcount} 条回复") } } }; if (state?.loading == true) LinearProgressIndicator(Modifier.fillMaxWidth()); state?.value.orEmpty().forEach { reply -> Text("${reply.member.uname}: ${reply.content.message}", Modifier.padding(start = 46.dp, top = 6.dp), fontSize = 13.sp) } }
}

@Composable private fun AccountScreen(destination: AccountDestination, vm: MainViewModel, navigate: (AccountDestination?) -> Unit, open: (String) -> Unit) {
    val history by vm.history.collectAsState(); val later by vm.watchLater.collectAsState(); val favorites by vm.favorites.collectAsState(); val resources by vm.favoriteResources.collectAsState(); val favoriteHasMore by vm.favoriteHasMore.collectAsState()
    if (destination is AccountDestination.Folder) LaunchedEffect(destination.id) { vm.loadFavoriteResources(destination.id, reset = true) }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(when (destination) { AccountDestination.History -> stringResource(dev.opencode.bilimobile.R.string.history); AccountDestination.Favorites -> stringResource(dev.opencode.bilimobile.R.string.favorites); AccountDestination.Later -> stringResource(dev.opencode.bilimobile.R.string.watch_later); is AccountDestination.Folder -> destination.title }) }, navigationIcon = { IconButton({ navigate(if (destination is AccountDestination.Folder) AccountDestination.Favorites else null) }) { Icon(Icons.Default.ArrowBack, stringResource(dev.opencode.bilimobile.R.string.back)) } }, actions = { IconButton({ if (destination is AccountDestination.Folder) vm.loadFavoriteResources(destination.id, reset = true) else vm.refreshProfile() }) { Icon(Icons.Default.Refresh, stringResource(dev.opencode.bilimobile.R.string.refresh)) } } )
        when (destination) {
            AccountDestination.History -> StateBody(history, vm::refreshProfile) { values -> LazyColumn { items(values, key = { "${it.history.bvid}:${it.title}" }) { item -> AccountVideoRow(item.cover, item.title, item.author_name, item.progress, item.duration, item.history.bvid, open) } } }
            AccountDestination.Later -> StateBody(later, vm::refreshProfile) { values -> LazyColumn { items(values, key = { it.bvid }) { item -> AccountVideoRow(item.coverUrl, clean(item.title), item.creator, 0, item.duration, item.bvid, open) } } }
            AccountDestination.Favorites -> StateBody(favorites, vm::refreshFavorites) { values -> LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(values, key = { it.id }) { folder -> Card(onClick = { navigate(AccountDestination.Folder(folder.id, folder.title)) }, shape = RoundedCornerShape(12.dp)) { ListItem(headlineContent = { Text(folder.title) }, supportingContent = { Text(stringResource(dev.opencode.bilimobile.R.string.item_count, folder.media_count)) }, trailingContent = { Icon(Icons.Default.ChevronRight, null) }) } } } }
            is AccountDestination.Folder -> StateBody(resources, { vm.loadFavoriteResources(destination.id, reset = true) }) { values -> LazyColumn { items(values, key = { "${it.id}:${it.bvid}" }) { item -> val video = item.asVideo(); AccountVideoRow(video.coverUrl, clean(video.title), video.creator, 0, video.duration, video.bvid, open) }; item { if (favoriteHasMore) TextButton({ vm.loadFavoriteResources(destination.id) }, Modifier.fillMaxWidth(), enabled = !resources.loading) { Text(if (resources.loading) stringResource(dev.opencode.bilimobile.R.string.loading) else stringResource(dev.opencode.bilimobile.R.string.load_more)) } else Text("已加载全部收藏内容", Modifier.fillMaxWidth().padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) } } }
        }
    }
}

@Composable private fun AccountVideoRow(image: String, title: String, author: String, progress: Int, duration: Int, bvid: String, open: (String) -> Unit) { Row(Modifier.fillMaxWidth().clickable(enabled = bvid.isNotBlank()) { open(bvid) }.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) { NetworkImage(image, title, Modifier.width(128.dp).aspectRatio(16 / 9f).clip(RoundedCornerShape(10.dp)), 320, 180); Spacer(Modifier.width(11.dp)); Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis); if (author.isNotBlank()) Text(author, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1); if (progress > 0) { Text("${formatDuration(progress)} / ${formatDuration(duration)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary); LinearProgressIndicator({ if (duration > 0) progress.toFloat() / duration else 0f }, Modifier.fillMaxWidth().padding(top = 3.dp)) } } } }

@Composable private fun ProfileScreen(vm: MainViewModel, destination: (AccountDestination) -> Unit, open: (String) -> Unit) {
    val profile by vm.profile.collectAsState(); val login by vm.login.collectAsState(); val sms by vm.smsLogin.collectAsState(); val history by vm.history.collectAsState(); val later by vm.watchLater.collectAsState(); val favorites by vm.favorites.collectAsState()
    Box { StateBody(profile, vm::refreshProfile) { user -> if (!user.isLogin) SignedOut(profile.error, vm::beginLogin) else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        profile.error?.let { item { ErrorBanner(it, vm::refreshProfile) } }
        item { Row(verticalAlignment = Alignment.CenterVertically) { NetworkImage(user.face, user.uname, Modifier.size(64.dp).clip(CircleShape), 144, 144); Spacer(Modifier.width(14.dp)); Column { Text(user.uname, fontSize = 21.sp, fontWeight = FontWeight.Bold); Text("LV${user.level_info.currentLevel} · UID ${user.mid}", color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
        item { ShortcutRow(history.value.orEmpty().size, favorites.value.orEmpty().size, later.value.orEmpty().size, destination) }
        item { PreviewCard("最近观看", history.value.orEmpty().map { PreviewItem(it.title, it.history.bvid, it.cover) }, { destination(AccountDestination.History) }, open) }
        item { PreviewCard("稍后再看", later.value.orEmpty().map { PreviewItem(clean(it.title), it.bvid, it.coverUrl) }, { destination(AccountDestination.Later) }, open) }
        item { Card(Modifier.clickable { destination(AccountDestination.Favorites) }, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Column(Modifier.padding(14.dp)) { Text("收藏夹", fontWeight = FontWeight.SemiBold); favorites.value.orEmpty().take(4).forEach { HorizontalDivider(); Text("${it.title}  ·  ${it.media_count}", Modifier.padding(vertical = 9.dp), fontSize = 14.sp) }; if (favorites.value.isNullOrEmpty()) Text("暂无内容", Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
        item { PrivacyCard() }
        item { Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Column(Modifier.padding(16.dp)) { Text("设置与关于", fontWeight = FontWeight.SemiBold); Text("版本 ${BuildConfig.VERSION_NAME}", Modifier.padding(top = 10.dp), fontSize = 13.sp); Text("图片缓存由系统自动管理", Modifier.padding(top = 8.dp), fontSize = 13.sp); Text("非官方客户端。数据来自公开 Web API，接口可能变更。", Modifier.padding(top = 8.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
        item { OutlinedButton(vm::logout, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("退出登录") } }
    } }; if (login !is LoginState.Idle || sms !is SmsLoginState.Idle) LoginDialog(login, sms, vm) }
}

private data class PreviewItem(val title: String, val id: String, val image: String)
@Composable private fun PreviewCard(title: String, values: List<PreviewItem>, more: () -> Unit, open: (String) -> Unit) { Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Column(Modifier.padding(14.dp)) { Row(Modifier.fillMaxWidth().clickable(onClick = more), horizontalArrangement = Arrangement.SpaceBetween) { Text(title, fontWeight = FontWeight.SemiBold); Icon(Icons.Default.ChevronRight, stringResource(dev.opencode.bilimobile.R.string.view_all)) }; values.take(3).forEach { item -> Row(Modifier.fillMaxWidth().clickable(enabled = item.id.isNotBlank()) { open(item.id) }.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) { NetworkImage(item.image, item.title, Modifier.size(72.dp, 42.dp).clip(RoundedCornerShape(7.dp)), 160, 90); Spacer(Modifier.width(10.dp)); Text(item.title, Modifier.weight(1f), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) } }; if (values.isEmpty()) Text("暂无内容", Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
@Composable private fun ShortcutRow(history: Int, favorites: Int, later: Int, open: (AccountDestination) -> Unit) { val values = listOf(Triple(Icons.Outlined.History, "历史", history) to AccountDestination.History, Triple(Icons.Outlined.Star, "收藏夹", favorites) to AccountDestination.Favorites, Triple(Icons.Outlined.Schedule, "稍后再看", later) to AccountDestination.Later); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { values.forEach { (value, destination) -> val (icon, label, count) = value; Surface(Modifier.weight(1f).clickable { open(destination) }, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface) { Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Text(label, fontSize = 12.sp); Text(count.toString(), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } } } } }
@Composable private fun SignedOut(error: String?, login: () -> Unit) { LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) { error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) } }; item { Icon(Icons.Default.AccountCircle, null, Modifier.size(64.dp)); Text("登录后同步内容", fontSize = 21.sp, fontWeight = FontWeight.Bold); Text("持久 Cookie 将加密保存在本机"); Button(login, Modifier.padding(top = 12.dp).heightIn(min = 48.dp)) { Text("扫码或短信登录") } }; item { PrivacyCard() } } }
@Composable private fun PrivacyCard() { Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Column(Modifier.fillMaxWidth().padding(16.dp)) { Text(stringResource(dev.opencode.bilimobile.R.string.privacy_title), fontWeight = FontWeight.SemiBold); Text(stringResource(dev.opencode.bilimobile.R.string.privacy_statement), Modifier.padding(top = 8.dp), fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
@Composable private fun LoginDialog(state: LoginState, sms: SmsLoginState, vm: MainViewModel) {
    val qr = when (state) { is LoginState.Ready -> state.qr; is LoginState.Scanned -> state.qr; is LoginState.Error -> state.qr; else -> null }
    var phone by remember { mutableStateOf("") }; var code by remember { mutableStateOf("") }; var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val seconds = qr?.let { ((it.createdAtMillis + 180_000 - now).coerceAtLeast(0) / 1000).toInt() } ?: 0
    LaunchedEffect(qr?.key, sms) { while (true) { now = System.currentTimeMillis(); delay(1_000) } }
    Dialog(vm::dismissLogin) { Card(shape = RoundedCornerShape(22.dp)) { Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (sms is SmsLoginState.Idle) "扫码登录" else "短信验证码（实验性）", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        when (sms) {
            SmsLoginState.Idle -> when (state) { LoginState.Loading -> CircularProgressIndicator(); is LoginState.Ready, is LoginState.Scanned -> { QrImage(qr!!.url); Text(if (state is LoginState.Scanned) "请在手机上确认" else "使用官方客户端扫描"); Text("二维码约 $seconds 秒后过期", fontSize = 12.sp) }; LoginState.Success -> Text("登录成功"); is LoginState.Error -> { Text(state.message, color = MaterialTheme.colorScheme.error); TextButton(vm::beginLogin) { Text("重新生成二维码") } }; LoginState.Idle -> Unit }
            SmsLoginState.CaptchaLoading, SmsLoginState.Sending, SmsLoginState.LoggingIn -> CircularProgressIndicator()
            is SmsLoginState.CaptchaReady -> { OutlinedTextField(phone, { phone = it.filter(Char::isDigit).take(11) }, label = { Text("+86 中国大陆手机号") }); CaptchaWebView(sms.parameters, { validate, seccode -> vm.sendSms(phone, sms.parameters, validate, seccode) }, vm::captchaFailed) }
            is SmsLoginState.CodeSent -> { Text("验证码已发送至 ${sms.maskedPhone}"); sms.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }; OutlinedTextField(code, { code = it.filter(Char::isDigit).take(8) }, label = { Text("短信验证码") }); Button({ vm.loginSms(phone, code, sms.captchaKey) }, enabled = code.isNotBlank()) { Text("登录") }; Text("重新发送倒计时 ${((sms.cooldownUntilMillis - now).coerceAtLeast(0) / 1000)} 秒", fontSize = 12.sp) }
            is SmsLoginState.Failed -> { Text(sms.message, color = MaterialTheme.colorScheme.error); if (sms.fallbackToQr) Button(vm::fallbackSmsToQr) { Text("切换并重新生成二维码") } else TextButton(vm::beginSmsCaptcha) { Text("重试验证码") } }
            SmsLoginState.Success -> Text("登录成功")
        }
        if (sms is SmsLoginState.Idle) TextButton(vm::beginSmsCaptcha) { Text("短信验证码（实验性）") } else TextButton(vm::fallbackSmsToQr) { Text("返回可靠的二维码登录") }
        Text("实验性短信流程会直连哔哩哔哩及第三方 Geetest 极验域名，不经过私人中转服务器，不收集密码。图片由 Coil 使用磁盘/内存缓存。", fontSize = 11.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(vm::dismissLogin) { Text("关闭") }
    } } }

@SuppressLint("SetJavaScriptEnabled")
@Composable private fun CaptchaWebView(parameters: CaptchaParameters, success: (String, String) -> Unit, failure: () -> Unit) {
    val attempt = remember(parameters) { UUID.randomUUID().toString() }
    val bridge = remember(attempt) { CaptchaBridge(attempt, success, failure) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    AndroidView({ context -> WebView(context).also { view -> webView = view; view.settings.apply { javaScriptEnabled = true; allowFileAccess = false; allowContentAccess = false; databaseEnabled = false; cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE; mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW; domStorageEnabled = false }; if (android.os.Build.VERSION.SDK_INT >= 26) view.settings.safeBrowsingEnabled = true; view.addJavascriptInterface(bridge, "CaptchaBridge"); view.webViewClient = object : WebViewClient() {
        private fun allowed(request: WebResourceRequest): Boolean { val host = request.url.host.orEmpty(); return request.url.scheme == "https" && (host == "bilibili.com" || host.endsWith(".bilibili.com") || host == "geetest.com" || host.endsWith(".geetest.com")) }
        override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest) = !allowed(request)
        override fun shouldInterceptRequest(v: WebView, request: WebResourceRequest): WebResourceResponse? = if (allowed(request)) null else WebResourceResponse("text/plain", "UTF-8", null)
        override fun onReceivedError(v: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) { if (request.isForMainFrame) bridge.onFailure(attempt) }
    }; view.loadDataWithBaseURL("https://passport.bilibili.com/", captchaHtml(parameters, attempt), "text/html", "UTF-8", null) } }, Modifier.fillMaxWidth().height(280.dp))
    DisposableEffect(attempt) { onDispose { bridge.invalidate(); webView?.apply { removeJavascriptInterface("CaptchaBridge"); stopLoading(); clearHistory(); clearCache(true); loadUrl("about:blank"); destroy() } } }
}
}

private fun captchaHtml(value: CaptchaParameters, attempt: String) = """<!doctype html><meta name=viewport content='width=device-width'><div id=captcha></div><script src='https://static.geetest.com/static/tools/gt.js'></script><script>try{initGeetest({gt:${JSONObject.quote(value.gt)},challenge:${JSONObject.quote(value.challenge)},offline:false,new_captcha:true,product:'bind'},function(c){c.appendTo('#captcha');c.onSuccess(function(){var r=c.getValidate();CaptchaBridge.onSuccess(${JSONObject.quote(attempt)},r.geetest_validate,r.geetest_seccode)});c.onError(function(){CaptchaBridge.onFailure(${JSONObject.quote(attempt)})})})}catch(e){CaptchaBridge.onFailure(${JSONObject.quote(attempt)})}</script>"""

private class CaptchaBridge(private val attempt: String, private val success: (String, String) -> Unit, private val failure: () -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private val completed = AtomicBoolean(false)
    @JavascriptInterface fun onSuccess(challenge: String, validate: String, seccode: String) { if (challenge == attempt && completed.compareAndSet(false, true)) main.post { success(validate, seccode) } }
    @JavascriptInterface fun onFailure(challenge: String) { if (challenge == attempt && completed.compareAndSet(false, true)) main.post { failure() } }
    fun invalidate() { completed.set(true) }
}
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
