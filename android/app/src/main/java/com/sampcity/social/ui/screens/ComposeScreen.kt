@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sampcity.social.SampApp
import com.sampcity.social.data.UserHit
import com.sampcity.social.ui.LocalNavigator
import com.sampcity.social.ui.components.Avatar
import com.sampcity.social.ui.components.LocalSnack
import com.sampcity.social.ui.components.PlayerName
import com.sampcity.social.ui.components.pretty
import com.sampcity.social.ui.components.userMessage
import com.sampcity.social.util.Media
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ComposeScreen(group: Int?) {
    val nav = LocalNavigator.current
    val ctx = LocalContext.current
    val app = SampApp.instance
    val me = app.session.me
    val scope = rememberCoroutineScope()
    val snack = LocalSnack.current
    var text by remember { mutableStateOf("") }
    val photos = remember { mutableStateListOf<Uri>() }
    val tags = remember { mutableStateListOf<String>() }
    val poll = remember { mutableStateListOf("", "") }
    var pollOn by remember { mutableStateOf(false) }
    var vis by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var tagDialog by remember { mutableStateOf(false) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(4)) { l -> photos.addAll(l.filter { it !in photos }.take(4 - photos.size)) }
    val pollClean = if (pollOn) poll.map { it.trim() }.filter { it.isNotEmpty() } else emptyList()
    val pollOk = !pollOn || (pollClean.size >= 2 && text.isNotBlank())
    val canPost = (text.isNotBlank() || photos.isNotEmpty()) && pollOk && !busy

    fun publish() {
        busy = true
        scope.launch {
            try {
                val ids = ArrayList<Int>()
                photos.forEachIndexed { i, u ->
                    progress = "Subiendo foto ${i + 1} de ${photos.size}…"
                    val bytes = withContext(Dispatchers.IO) { Media.compress(ctx, u, 1600, 1100 * 1024) }
                    ids += app.api.upload("img", bytes, "image/jpeg")
                }
                progress = "Publicando…"
                app.repo.createPost(text.trim(), ids, tags.toList(), if (group != null) 0 else vis, pollClean, group = group)
                app.bus.tryEmit("post-created"); nav.back()
            } catch (e: Exception) { snack.showSnackbar(e.userMessage()); busy = false; progress = "" }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (group != null) "Publicar en el grupo" else "Nueva publicación") },
                navigationIcon = { IconButton(nav::back, enabled = !busy) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
                actions = { Button(::publish, enabled = canPost, modifier = Modifier.padding(end = 8.dp)) { Text("Publicar") } },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton({ pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = photos.size < 4 && !busy) { Icon(Icons.Outlined.AddPhotoAlternate, null); Text("  Fotos") }
                    TextButton({ tagDialog = true }, enabled = !busy) { Icon(Icons.Outlined.PersonAdd, null); Text("  Etiquetar") }
                    TextButton({ pollOn = !pollOn }, enabled = !busy) { Icon(Icons.Outlined.BarChart, null); Text("  Encuesta") }
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (me != null) { Avatar(me.name, me.skin, 44.dp); Column(Modifier.padding(start = 10.dp)) { PlayerName(me.name) } }
            }
            if (group == null) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Público", "Amigos", "Solo yo").forEachIndexed { i, l -> FilterChip(vis == i, { vis = i }, { Text(l) }) } }
            OutlinedTextField(text, { if (it.length <= 1000) text = it }, Modifier.fillMaxWidth().height(170.dp), placeholder = { Text(if (pollOn) "Escribe la pregunta de la encuesta" else "¿Qué estás pensando?") }, supportingText = { Text("${text.length}/1000") })
            if (tags.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { tags.forEach { t -> InputChip(true, { tags.remove(t) }, { Text("con ${t.pretty()}") }, trailingIcon = { Icon(Icons.Filled.Close, null, Modifier.size(16.dp)) }) } }
            if (photos.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(photos.toList()) { u ->
                    Box { AsyncImage(u, null, contentScale = ContentScale.Crop, modifier = Modifier.size(110.dp).clip(RoundedCornerShape(12.dp)))
                        IconButton({ photos.remove(u) }, Modifier.align(Alignment.TopEnd).size(30.dp).background(Color(0xAA000000), CircleShape)) { Icon(Icons.Filled.Close, "Quitar", tint = Color.White, modifier = Modifier.size(16.dp)) } }
                }
            }
            if (pollOn) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Opciones de la encuesta (2 a 4)", style = MaterialTheme.typography.titleSmall)
                poll.forEachIndexed { i, o -> OutlinedTextField(o, { if (it.length <= 60) poll[i] = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Opción ${i + 1}") }) }
                if (poll.size < 4) TextButton({ poll.add("") }) { Text("+ Añadir opción") }
            }
            if (busy) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp); Text(progress) }
        }
    }
    if (tagDialog) TagDialog(tags, onDismiss = { tagDialog = false })
}

@Composable
private fun TagDialog(tags: MutableList<String>, onDismiss: () -> Unit) {
    var q by remember { mutableStateOf("") }
    var res by remember { mutableStateOf<List<UserHit>>(emptyList()) }
    LaunchedEffect(q) { if (q.trim().length < 2) res = emptyList() else { delay(250); res = runCatching { SampApp.instance.repo.searchUsers(q.trim()) }.getOrDefault(emptyList()) } }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Etiquetar jugadores") }, text = {
        Column {
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Buscar jugador…") })
            LazyColumn(Modifier.height(240.dp)) {
                items(res, key = { it.name }) { u ->
                    ListItem({ Text(u.name.pretty()) }, leadingContent = { Avatar(u.name, u.skin, 34.dp) }, trailingContent = { if (u.name in tags) Text("✔") }, colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { if (u.name in tags) tags.remove(u.name) else if (tags.size < 10) tags.add(u.name) })
                }
            }
        }
    }, confirmButton = { TextButton(onDismiss) { Text("Listo") } })
}
