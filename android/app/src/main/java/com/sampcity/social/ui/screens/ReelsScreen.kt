@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.sampcity.social.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sampcity.social.SampApp
import com.sampcity.social.data.Post
import com.sampcity.social.ui.LocalNavigator
import com.sampcity.social.ui.components.Avatar
import com.sampcity.social.ui.components.ErrorBox
import com.sampcity.social.ui.components.LoadingBox
import com.sampcity.social.ui.components.LocalSnack
import com.sampcity.social.ui.components.PlayerName
import com.sampcity.social.ui.components.RichText
import com.sampcity.social.ui.components.VideoPlayer
import com.sampcity.social.ui.components.userMessage
import com.sampcity.social.util.Media
import com.sampcity.social.util.count
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReelsVM(private val start: Int?, private val author: String?) : ViewModel() {
    private val repo = SampApp.instance.repo
    val items = mutableStateListOf<Post>()
    var more by mutableStateOf(true)
    var loading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    private var busy = false
    init { load(true) }
    fun load(first: Boolean = false) {
        if (busy || (!more && !first)) return
        busy = true
        viewModelScope.launch {
            try {
                val r = repo.reels(if (first) null else items.lastOrNull()?.id, author, false, if (first) start else null)
                val have = items.map { it.id }.toHashSet(); items.addAll(r.items.filter { have.add(it.id) }); more = r.more; error = null
            } catch (e: Exception) { if (items.isEmpty()) error = e.userMessage() }
            loading = false; busy = false
        }
    }
    fun update(p: Post) { val i = items.indexOfFirst { it.id == p.id }; if (i >= 0) items[i] = p }
    fun reload() { items.clear(); more = true; loading = true; load(true) }
}
class ReelsVMFactory(private val s: Int?, private val a: String?) : ViewModelProvider.Factory { @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = ReelsVM(s, a) as T }

@Composable
fun ReelsScreen(start: Int?, author: String?) {
    val vm: ReelsVM = viewModel(key = "reels$start$author", factory = ReelsVMFactory(start, author))
    val nav = LocalNavigator.current; val repo = SampApp.instance.repo; val api = SampApp.instance.api
    val scope = rememberCoroutineScope(); val snack = LocalSnack.current; val ctx = LocalContext.current
    var creating by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    val pager = rememberPagerState { vm.items.size }
    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect { if (it >= vm.items.size - 3) vm.load() } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            vm.loading -> LoadingBox()
            vm.error != null && vm.items.isEmpty() -> ErrorBox(vm.error!!, vm::reload)
            vm.items.isEmpty() -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { Text("Aún no hay reels.", color = Color.White); OutlinedButton({ creating = true }, Modifier.padding(top = 12.dp)) { Text("Subir el primero") } }
            else -> VerticalPager(pager, Modifier.fillMaxSize(), key = { vm.items[it].id }) { page ->
                val p = vm.items[page]
                val m = p.media.firstOrNull()
                Box(Modifier.fillMaxSize().clickable { paused = !paused }) {
                    if (m != null) VideoPlayer(api.mediaUrl(m.id), play = pager.currentPage == page && !paused, modifier = Modifier.fillMaxSize(), loop = true, fill = true)
                    if (paused && pager.currentPage == page) Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(72.dp).background(Color(0x66000000), CircleShape))
                    Column(Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 14.dp, bottom = 16.dp, end = 84.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { nav.profile(p.author) }) { Avatar(p.author, p.skin, 40.dp); PlayerName(p.author, Modifier.padding(start = 8.dp), color = Color.White) }
                        if (p.body.isNotBlank()) RichText(p.body, Modifier.padding(top = 8.dp), color = Color.White, maxLines = 3)
                    }
                    Column(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 8.dp, bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        ReelAction(if (p.mine > 0) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, count(p.likes), if (p.mine > 0) Color(0xFFFF5A6E) else Color.White) {
                            val nm = if (p.mine > 0) 0 else 2; vm.update(p.copy(mine = nm, likes = (p.likes + if (nm > 0) 1 else -1).coerceAtLeast(0)))
                            scope.launch { runCatching { repo.react(p.id, if (p.mine > 0) p.mine else 2) }.onSuccess { r -> vm.update(p.copy(mine = r.mine, likes = r.likes)) }.onFailure { vm.update(p); snack.showSnackbar(it.userMessage()) } }
                        }
                        ReelAction(Icons.Filled.ChatBubble, count(p.comments), Color.White) { nav.post(p.id) }
                        ReelAction(if (p.saved > 0) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, count(p.saves), Color.White) {
                            scope.launch { runCatching { repo.savePost(p.id) }.onSuccess { r -> vm.update(p.copy(saved = if (r.saved) 1 else 0, saves = r.saves)) }.onFailure { snack.showSnackbar(it.userMessage()) } }
                        }
                        ReelAction(Icons.Filled.Share, "", Color.White) { ctx.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, api.baseUrl + "/p/" + p.id), "Compartir").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            if (author != null || start != null) IconButton(nav::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Color.White) } else Box(Modifier.size(48.dp))
            Text("Reels", color = Color.White, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 10.dp))
            IconButton({ creating = true }) { Icon(Icons.Filled.Add, "Subir reel", tint = Color.White) }
        }
    }
    if (creating) ReelUploadDialog(onDismiss = { creating = false }, onDone = { vm.reload() })
}

@Composable
private fun ReelAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(CircleShape).clickable(onClick = onClick).padding(4.dp)) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(34.dp)); if (label.isNotEmpty()) Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ReelUploadDialog(onDismiss: () -> Unit, onDone: () -> Unit) {
    val ctx = LocalContext.current; val app = SampApp.instance
    val scope = rememberCoroutineScope(); val snack = LocalSnack.current
    var uri by remember { mutableStateOf<Uri?>(null) }
    var caption by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val info by app.session.info.collectAsStateWithLifecycle()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri = it ?: uri }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("Subir reel") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }, enabled = !busy) { Text(if (uri == null) "Elegir video (máx. ${info.reelSecs} s)" else "Video elegido ✔ · Cambiar") }
            OutlinedTextField(caption, { if (it.length <= 500) caption = it }, Modifier.fillMaxWidth(), minLines = 2, label = { Text("Descripción") })
            if (busy) Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Text("Subiendo… puede tardar") }
        }
    }, confirmButton = {
        TextButton({
            val u = uri ?: return@TextButton; busy = true
            scope.launch {
                try {
                    val id = withContext(Dispatchers.IO) {
                        if (Media.videoDurationSec(ctx, u) > info.reelSecs + 0.5) error("El video debe durar máximo ${info.reelSecs} segundos")
                        val thumb = app.api.upload("img", Media.videoThumb(ctx, u), "image/jpeg")
                        val mime = Media.mimeOf(ctx, u).let { if (it == "video/quicktime" || it == "video/webm") it else "video/mp4" }
                        app.api.upload("rel", Media.readBytes(ctx, u, info.reelMax * 1048576L), mime, thumb)
                    }
                    app.repo.createReel(id, caption.trim()); onDone(); onDismiss()
                } catch (e: Exception) { snack.showSnackbar(e.userMessage()); busy = false }
            }
        }, enabled = uri != null && !busy) { Text("Publicar") }
    }, dismissButton = { TextButton(onDismiss, enabled = !busy) { Text("Cancelar") } })
}
