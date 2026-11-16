@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.sampcity.social.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sampcity.social.SampApp
import com.sampcity.social.data.Post
import com.sampcity.social.data.StoryViewer
import com.sampcity.social.ui.LocalNavigator
import com.sampcity.social.ui.components.Avatar
import com.sampcity.social.ui.components.ConfirmDialog
import com.sampcity.social.ui.components.ErrorBox
import com.sampcity.social.ui.components.LoadingBox
import com.sampcity.social.ui.components.LocalSnack
import com.sampcity.social.ui.components.PlayerName
import com.sampcity.social.ui.components.VideoPlayer
import com.sampcity.social.ui.components.pretty
import com.sampcity.social.ui.components.userMessage
import com.sampcity.social.util.Clock
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Visor de historias: toca a la derecha/izquierda para avanzar o volver, mantén pulsado para pausar. */
@Composable
fun StoryScreen(author: String) {
    val nav = LocalNavigator.current; val app = SampApp.instance; val repo = app.repo; val api = app.api
    val scope = rememberCoroutineScope(); val snack = LocalSnack.current
    val me = app.session.me
    var all by remember { mutableStateOf<List<Post>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var who by remember { mutableStateOf(author) }
    var idx by remember { mutableStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var reply by remember { mutableStateOf("") }
    var viewers by remember { mutableStateOf<List<StoryViewer>?>(null) }
    var showViewers by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var liked by remember { mutableStateOf(setOf<Int>()) }
    var videoMs by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) { try { all = repo.stories() } catch (e: Exception) { error = e.userMessage() } }
    val byAuthor = all?.groupBy { it.author }.orEmpty()
    val authors = byAuthor.keys.toList().let { l -> l.sortedBy { if (it == me?.name) 0 else 1 } }
    val list = byAuthor[who].orEmpty().sortedBy { it.id }
    val cur = list.getOrNull(idx)

    fun next() {
        if (idx + 1 < list.size) { idx++; progress = 0f }
        else { val ai = authors.indexOf(who); if (ai >= 0 && ai + 1 < authors.size) { who = authors[ai + 1]; idx = 0; progress = 0f } else nav.back() }
    }
    fun prev() { if (idx > 0) { idx--; progress = 0f } else { val ai = authors.indexOf(who); if (ai > 0) { who = authors[ai - 1]; idx = (byAuthor[who]?.size ?: 1) - 1; progress = 0f } else progress = 0f } }

    // se marca como vista y avanza sola a los 5 s (las fotos); los videos avisan al terminar
    LaunchedEffect(cur?.id) {
        val c = cur ?: return@LaunchedEffect
        progress = 0f
        app.prefs.seenStories = app.prefs.seenStories + c.id.toString(); app.bus.tryEmit("story-seen")
        repo.storyViewed(c.id)
        if (c.media.firstOrNull()?.isVideo != true) { var t = 0L; while (t < 5000) { delay(50); if (!paused && viewers == null && !showViewers) t += 50; progress = t / 5000f }; next() }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            error != null -> ErrorBox(error!!, null)
            all == null -> LoadingBox()
            cur == null -> { LaunchedEffect(Unit) { nav.back() } }
            else -> {
                val m = cur.media.firstOrNull()
                if (m != null) {
                    if (m.isVideo) VideoPlayer(api.mediaUrl(m.id), play = !paused && !showViewers, modifier = Modifier.fillMaxSize(), onEnded = ::next, onProgress = { progress = it })
                    else AsyncImage(api.mediaUrl(m.id), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                }
                // zonas táctiles
                Row(Modifier.fillMaxSize().pointerInput(cur.id) { detectTapGestures(onPress = { paused = true; tryAwaitRelease(); paused = false }, onTap = { o -> if (o.x < size.width / 3) prev() else next() }) }) {}
                Column(Modifier.statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        list.forEachIndexed { i, _ -> LinearProgressIndicator(progress = { if (i < idx) 1f else if (i == idx) progress else 0f }, Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)), color = Color.White, trackColor = Color(0x55FFFFFF), drawStopIndicator = {}) }
                    }
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(cur.author, cur.skin, 38.dp, onClick = { nav.profile(cur.author) })
                        Column(Modifier.padding(start = 8.dp).weight(1f)) { PlayerName(cur.author, color = Color.White); Text(Clock.ago(cur.createdAt), style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = Color(0xCCFFFFFF)) }
                        if (cur.author == me?.name) IconButton({ confirmDelete = true }) { Icon(Icons.Filled.Delete, "Eliminar", tint = Color.White) }
                        IconButton({ nav.back() }) { Icon(Icons.Filled.Close, "Cerrar", tint = Color.White) }
                    }
                }
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().imePadding().padding(12.dp)) {
                    if (cur.body.isNotBlank()) Text(cur.body, color = Color.White, modifier = Modifier.padding(bottom = 10.dp).fillMaxWidth().background(Color(0x66000000), RoundedCornerShape(10.dp)).padding(10.dp))
                    if (cur.author == me?.name) Row(Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0x66000000)).padding(horizontal = 12.dp, vertical = 6.dp).pointerInput(cur.id) { detectTapGestures { scope.launch { viewers = runCatching { repo.storyViews(cur.id) }.getOrDefault(emptyList()); showViewers = true } } }, verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Visibility, null, tint = Color.White); Text("  Ver quién la vio", color = Color.White) }
                    else Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(reply, { if (it.length <= 300) reply = it }, Modifier.weight(1f), placeholder = { Text("Responder…", color = Color(0xAAFFFFFF)) }, singleLine = true, shape = RoundedCornerShape(24.dp),
                            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color.White, unfocusedBorderColor = Color(0x99FFFFFF)))
                        if (reply.isNotBlank()) IconButton({ val t = reply.trim(); reply = ""; scope.launch { runCatching { repo.addComment(cur.id, t, null) }.onSuccess { snack.showSnackbar("Respuesta enviada") }.onFailure { snack.showSnackbar(it.userMessage()) } } }) { Icon(Icons.AutoMirrored.Filled.Send, "Enviar", tint = Color.White) }
                        IconButton({ scope.launch { runCatching { repo.react(cur.id, 2) }.onSuccess { liked = if (it.liked) liked + cur.id else liked - cur.id } } }) { Icon(if (cur.id in liked || cur.mine > 0) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Me encanta", tint = if (cur.id in liked || cur.mine > 0) Color(0xFFFF5A6E) else Color.White) }
                    }
                }
            }
        }
    }
    if (showViewers) ModalBottomSheet({ showViewers = false }) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text("${viewers?.size ?: 0} visualizaciones", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
            viewers.orEmpty().forEach { v -> ListItem({ Text(v.name.pretty()) }, leadingContent = { Avatar(v.name, v.skin, 38.dp) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent)) }
        }
    }
    if (confirmDelete && cur != null) ConfirmDialog("¿Eliminar esta historia?", "", "Eliminar", true, { scope.launch { runCatching { repo.deletePost(cur.id) }.onSuccess { app.bus.tryEmit("post-created"); all = all?.filter { it.id != cur.id }; if (idx >= list.size - 1) idx = (idx - 1).coerceAtLeast(0) }.onFailure { snack.showSnackbar(it.userMessage()) } } }) { confirmDelete = false }
}
