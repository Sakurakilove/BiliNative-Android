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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.RemoveRedEye
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import dev.opencode.bilimobile.data.LoginState
import dev.opencode.bilimobile.data.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class Tab(val label: String) { Home("Popular"), Search("Search"), Profile("Profile") }

@Composable
fun BiliApp(vm: MainViewModel = viewModel()) {
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(detailId != null) { detailId = null }

    Scaffold(
        bottomBar = {
            AnimatedVisibility(detailId == null, enter = fadeIn(), exit = fadeOut()) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .96f)) {
                    Tab.entries.forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            icon = { Icon(when (item) { Tab.Home -> Icons.Default.Home; Tab.Search -> Icons.Default.Search; Tab.Profile -> Icons.Default.Person }, null) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AnimatedContent(targetState = detailId, label = "page") { id ->
                if (id != null) DetailScreen(id, vm) else when (tab) {
                    Tab.Home -> PopularScreen(vm) { detailId = it }
                    Tab.Search -> SearchScreen(vm) { detailId = it }
                    Tab.Profile -> ProfileScreen(vm)
                }
            }
        }
    }
}

@Composable
private fun PopularScreen(vm: MainViewModel, open: (String) -> Unit) {
    val state by vm.popular.collectAsState()
    VideoList(
        title = "Trending now",
        subtitle = "A fresh look at what people are watching",
        state = state,
        retry = vm::refreshPopular,
        open = open
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchScreen(vm: MainViewModel, open: (String) -> Unit) {
    val state by vm.search.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Discover", "WBI-signed video search")
        TextField(
            value = query, onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            placeholder = { Text("Search videos") }, leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true, shape = RoundedCornerShape(18.dp),
            colors = TextFieldDefaults.colors(unfocusedIndicatorColor = Color.Transparent, focusedIndicatorColor = Color.Transparent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.search(query) })
        )
        Spacer(Modifier.height(10.dp))
        StateBody(state, retry = { vm.search(query) }) { videos ->
            if (videos.isEmpty()) EmptyMessage("Search by title, creator, or topic")
            else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(videos, key = { it.bvid }) { VideoCard(it) { open(it.bvid) } }
            }
        }
    }
}

@Composable
private fun VideoList(title: String, subtitle: String, state: ContentState<List<Video>>, retry: () -> Unit, open: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PageHeader(title, subtitle)
        StateBody(state, retry) { videos ->
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                items(videos, key = { it.bvid }) { VideoCard(it) { open(it.bvid) } }
            }
        }
    }
}

@Composable
private fun PageHeader(title: String, subtitle: String) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 14.dp)) {
        Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f), fontSize = 14.sp)
    }
}

@Composable
private fun VideoCard(video: Video, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(22.dp), elevation = CardDefaults.cardElevation(1.dp)) {
        Column {
            Box {
                AsyncImage(video.coverUrl, video.title, Modifier.fillMaxWidth().aspectRatio(16 / 9f), contentScale = ContentScale.Crop)
                Text(formatDuration(video.duration), Modifier.align(Alignment.BottomEnd).padding(10.dp).background(Color.Black.copy(.68f), RoundedCornerShape(7.dp)).padding(horizontal = 7.dp, vertical = 3.dp), color = Color.White, fontSize = 12.sp)
            }
            Column(Modifier.padding(15.dp)) {
                Text(cleanTitle(video.title), fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(7.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(video.creator, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface.copy(.56f), maxLines = 1)
                    Icon(Icons.Outlined.RemoveRedEye, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurface.copy(.45f))
                    Spacer(Modifier.width(4.dp))
                    Text(formatCount(video.views), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(.5f))
                }
            }
        }
    }
}

@Composable
private fun DetailScreen(bvid: String, vm: MainViewModel) {
    val detail by vm.details.collectAsState()
    val stream by vm.playUrl.collectAsState()
    LaunchedEffect(bvid) { vm.loadDetails(bvid) }
    StateBody(detail, retry = { vm.loadDetails(bvid) }) { video ->
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Box(Modifier.fillMaxWidth().aspectRatio(16 / 9f).background(Color.Black)) {
                    when {
                        stream.value != null -> VideoPlayer(stream.value!!, vm.playbackHeaders())
                        stream.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
                        else -> Text(stream.error ?: "Player unavailable", Modifier.align(Alignment.Center).padding(24.dp), color = Color.White)
                    }
                }
            }
            item {
                Column(Modifier.padding(20.dp)) {
                    Text(cleanTitle(video.title), fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("${formatCount(video.views)} views  ·  ${video.creator}", color = MaterialTheme.colorScheme.onSurface.copy(.58f))
                    Spacer(Modifier.height(22.dp))
                    Text("About", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    Spacer(Modifier.height(7.dp))
                    Text(video.desc.ifBlank { "No description provided." }, color = MaterialTheme.colorScheme.onSurface.copy(.72f), lineHeight = 22.sp)
                }
            }
        }
    }
}

@Composable
private fun VideoPlayer(url: String, headers: Map<String, String>) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val player = remember(url, headers) {
        val factory = DefaultHttpDataSource.Factory().setDefaultRequestProperties(headers)
        ExoPlayer.Builder(context).build().apply {
            setMediaSource(ProgressiveMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri(url)))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    AndroidView({ PlayerView(it).apply { this.player = player } }, Modifier.fillMaxSize())
}

@Composable
private fun ProfileScreen(vm: MainViewModel) {
    val state by vm.profile.collectAsState()
    val login by vm.login.collectAsState()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            PageHeader("You", "Your session stays on this device")
            StateBody(state, vm::refreshProfile) { profile ->
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (profile.isLogin) {
                        AsyncImage(profile.face, profile.uname, Modifier.size(96.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                        Spacer(Modifier.height(16.dp))
                        Text(profile.uname, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("UID ${profile.mid}", color = MaterialTheme.colorScheme.onSurface.copy(.5f))
                        Spacer(Modifier.height(24.dp))
                        Button(vm::logout, shape = RoundedCornerShape(14.dp)) { Text("Sign out") }
                    } else {
                        Box(Modifier.size(96.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, null, Modifier.size(44.dp)) }
                        Spacer(Modifier.height(18.dp))
                        Text("Sign in to Bilibili", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        Text("Scan securely with the official mobile app", color = MaterialTheme.colorScheme.onSurface.copy(.58f))
                        Spacer(Modifier.height(24.dp))
                        Button(vm::beginLogin, shape = RoundedCornerShape(14.dp)) { Text("Show QR code") }
                    }
                }
            }
        }
        if (login !is LoginState.Idle) {
            LoginDialog(login, vm::beginLogin, vm::dismissLogin)
        }
    }
}

@Composable
private fun LoginDialog(state: LoginState, retry: () -> Unit, dismiss: () -> Unit) {
    Dialog(onDismissRequest = dismiss) {
        Card(shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("QR sign in", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(18.dp))
                when (state) {
                    LoginState.Loading -> CircularProgressIndicator()
                    is LoginState.Ready, is LoginState.Scanned -> {
                        val qr = if (state is LoginState.Ready) state.qr else (state as LoginState.Scanned).qr
                        QrImage(qr.url)
                        Spacer(Modifier.height(14.dp))
                        Text(if (state is LoginState.Scanned) "Scanned. Confirm on your phone." else "Open Bilibili and scan this code", color = MaterialTheme.colorScheme.onSurface.copy(.65f))
                    }
                    LoginState.Success -> Text("Signed in successfully")
                    is LoginState.Error -> { Text(state.message, color = MaterialTheme.colorScheme.error); Spacer(Modifier.height(12.dp)); Button(retry) { Text("Try again") } }
                    LoginState.Idle -> Unit
                }
                Spacer(Modifier.height(16.dp))
                Button(dismiss) { Text(if (state is LoginState.Success) "Continue" else "Cancel") }
            }
        }
    }
}

@Composable
private fun QrImage(value: String) {
    var bitmap by remember(value) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(value) { bitmap = withContext(Dispatchers.Default) { qrBitmap(value) } }
    if (bitmap == null) CircularProgressIndicator(Modifier.size(48.dp))
    else Image(bitmap!!.asImageBitmap(), "Bilibili sign-in QR code", Modifier.size(220.dp))
}

@Composable
private fun <T> StateBody(state: ContentState<T>, retry: () -> Unit, content: @Composable (T) -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            state.value != null -> content(state.value)
            state.loading -> CircularProgressIndicator()
            else -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                Text(state.error ?: "Nothing here yet", color = MaterialTheme.colorScheme.onSurface.copy(.6f))
                Spacer(Modifier.height(12.dp))
                IconButton(retry) { Icon(Icons.Default.Refresh, "Retry") }
            }
        }
        if (state.loading && state.value != null) CircularProgressIndicator(Modifier.align(Alignment.TopCenter).padding(8.dp).size(22.dp), strokeWidth = 2.dp)
    }
}

@Composable private fun EmptyMessage(text: String) = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text, color = MaterialTheme.colorScheme.onSurface.copy(.55f)) }

private fun qrBitmap(value: String): Bitmap {
    val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 640, 640)
    return Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888).also { bitmap ->
        for (x in 0 until 640) for (y in 0 until 640) bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    }
}

private fun cleanTitle(value: String) = value.replace(Regex("<[^>]*>"), "").replace("&quot;", "\"").replace("&amp;", "&")
private fun formatDuration(seconds: Int) = "%d:%02d".format(seconds / 60, seconds % 60)
private fun formatCount(value: Long) = when {
    value >= 1_000_000_000 -> "%.1fB".format(value / 1_000_000_000.0)
    value >= 1_000_000 -> "%.1fM".format(value / 1_000_000.0)
    value >= 1_000 -> "%.1fK".format(value / 1_000.0)
    else -> value.toString()
}
