@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.sampcity.social.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.sampcity.social.SampApp
import com.sampcity.social.data.Friend
import com.sampcity.social.data.PhotoRef
import com.sampcity.social.data.Post
import com.sampcity.social.data.UserProfile
import com.sampcity.social.ui.LocalNavigator
import com.sampcity.social.ui.components.Avatar
import com.sampcity.social.ui.components.ConfirmDialog
import com.sampcity.social.ui.components.EmptyBox
import com.sampcity.social.ui.components.ErrorBox
import com.sampcity.social.ui.components.LoadingBox
import com.sampcity.social.ui.components.LocalSnack
import com.sampcity.social.ui.components.OnEndReached
import com.sampcity.social.ui.components.Paged
import com.sampcity.social.ui.components.PlayerName
import com.sampcity.social.ui.components.PostCard
import com.sampcity.social.ui.components.PostHostDialogs
import com.sampcity.social.ui.components.pretty
import com.sampcity.social.ui.components.rememberPostHost
import com.sampcity.social.ui.components.userMessage
import com.sampcity.social.ui.openWeb
import com.sampcity.social.util.Clock
import com.sampcity.social.util.Media
import com.sampcity.social.util.count
import com.sampcity.social.util.hoursPlayed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileVM(val name: String) : ViewModel() {
    private val repo = SampApp.instance.repo
    var user by mutableStateOf<UserProfile?>(null)
    var error by mutableStateOf<String?>(null)
    var photos by mutableStateOf<List<PhotoRef>?>(null)
    var pinned by mutableStateOf<Post?>(null)
    var refreshing by mutableStateOf(false)
    val posts = Paged<Post>(viewModelScope, { it.id }, { it.size > 12 }, { it.take(12) }) { _, page -> repo.posts("type=post&author=${java.net.URLEncoder.encode(name, "UTF-8")}&page=$page") }

    init {
        load()
        viewModelScope.launch { SampApp.instance.bus.collect { if (it == "post-created" || it == "profile-changed") load() } }
    }

    fun load() {
        viewModelScope.launch {
            refreshing = user != null; error = null
            try {
                val u = repo.user(name); user = u
                if (!u.locked) { posts.refresh(); pinned = if (u.pin > 0) runCatching { repo.post(u.pin) }.getOrNull() else null }
            } catch (e: Exception) { error = e.userMessage() }
            refreshing = false
        }
    }
    fun loadPhotos() { if (photos == null) viewModelScope.launch { photos = runCatching { repo.userPhotos(name) }.getOrDefault(emptyList()) } }
}

class ProfileVMFactory(private val name: String) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = ProfileVM(name) as T
}

@Composable
fun ProfileScreen(name: String, isTab: Boolean = false) {
    if (name.isBlank()) return
    val vm: ProfileVM = viewModel(key = "profile$name", factory = ProfileVMFactory(name))
    val nav = LocalNavigator.current
    val app = SampApp.instance
    val scope = rememberCoroutineScope()
    val snack = LocalSnack.current
    val me = app.session.me
    val own = me?.name == name
    val host = rememberPostHost(onUpdate = { p -> vm.posts.update({ it.id == p.id }, p) }, onRemove = { id -> vm.posts.remove { it.id == id } })
    var tab by remember { mutableStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }
    val u = vm.user

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(name.pretty(), maxLines = 1) },
                navigationIcon = { if (!isTab) IconButton(nav::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
                actions = {
                    if (own) IconButton({ nav.go("settings") }) { Icon(Icons.Filled.Settings, "Configuración") }
                    else Box {
                        IconButton({ menu = true }) { Icon(Icons.Filled.MoreVert, "Más") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem({ Text("Reportar") }, { menu = false; confirm = "report" })
                            DropdownMenuItem({ Text("Bloquear") }, { menu = false; confirm = "block" })
                        }
                    }
                },
            )
        },
    ) { pad ->
        when {
            u == null && vm.error != null -> ErrorBox(vm.error!!, vm::load, Modifier.padding(pad).fillMaxSize())
            u == null -> LoadingBox(Modifier.padding(pad).fillMaxSize())
            else -> {
                val state = rememberLazyListState()
                PullToRefreshBox(vm.refreshing, { vm.load() }, Modifier.padding(pad)) {
                    LazyColumn(Modifier.fillMaxSize(), state = state, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { ProfileHeader(u, own, onEdit = { edit = true }, onChanged = { vm.load() }) }
                        if (u.locked && !own) item { Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Filled.Lock, null, Modifier.size(40.dp)); Text("Este perfil es privado", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)); Text("Solo sus amigos pueden ver su perfil.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center) } }
                        else {
                            item {
                                val tabs = listOf("Publicaciones", "Fotos", "Amigos")
                                TabRow(tab) { tabs.forEachIndexed { i, t -> Tab(tab == i, { tab = i; if (i == 1) vm.loadPhotos() }, text = { Text(t, maxLines = 1) }) } }
                            }
                            when (tab) {
                                0 -> {
                                    if (own) item { Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button({ nav.compose() }, Modifier.weight(1f)) { Text("Publicar") }
                                        OutlinedButton({ nav.go("saved") }, Modifier.weight(1f)) { Text("Guardados") }
                                    } }
                                    if (u.reels > 0) item { OutlinedButton({ nav.reels(author = name) }, Modifier.fillMaxWidth().padding(horizontal = 12.dp)) { Text("Ver sus reels (${u.reels})") } }
                                    vm.pinned?.let { p -> item(key = "pin") { Column { Text("📌 Publicación fijada", Modifier.padding(horizontal = 14.dp, vertical = 4.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary); PostCard(p, host) } } }
                                    when {
                                        vm.posts.loading && vm.posts.items.isEmpty() -> item { LoadingBox(Modifier.fillMaxWidth().height(160.dp)) }
                                        vm.posts.loaded && vm.posts.items.isEmpty() && vm.pinned == null -> item { EmptyBox("Aún no hay publicaciones.") }
                                    }
                                    items(vm.posts.items.filter { it.id != vm.pinned?.id }, key = { it.id }) { p -> PostCard(p, host) }
                                }
                                1 -> item { PhotosGrid(vm.photos) { nav.post(it.postId) } }
                                else -> { if (u.fl.isEmpty()) item { EmptyBox("Aún no tiene amigos.") } else items(u.fl, key = { it.name }) { f -> FriendLine(f) } }
                            }
                        }
                    }
                    OnEndReached(state) { if (tab == 0) vm.posts.more() }
                }
            }
        }
    }
    PostHostDialogs(host)
    if (edit && u != null) EditProfileSheet(u, onDismiss = { edit = false }, onSaved = { app.bus.tryEmit("profile-changed"); scope.launch { app.session.refreshMe() } })
    when (confirm) {
        "block" -> ConfirmDialog("¿Bloquear a ${name.pretty()}?", "No podrán verse ni escribirse. Dejarán de ser amigos.", "Bloquear", true, { scope.launch { runCatching { app.repo.block(name) }.onSuccess { snack.showSnackbar("Bloqueaste a ${name.pretty()}"); nav.back() }.onFailure { snack.showSnackbar(it.userMessage()) } } }) { confirm = null }
        "report" -> ReportUserDialog(name) { confirm = null }
    }
}

@Composable
fun ReportUserDialog(name: String, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope(); val snack = LocalSnack.current
    var reason by remember { mutableStateOf("acoso") }
    val reasons = listOf("spam" to "Spam", "acoso" to "Acoso o insultos", "contenido" to "Contenido inapropiado", "estafa" to "Estafa", "suplantacion" to "Suplantación", "otro" to "Otro")
    androidx.compose.material3.AlertDialog(onDismissRequest = onDismiss, title = { Text("Reportar a ${name.pretty()}") }, text = {
        Column { reasons.forEach { (k, l) -> Row(Modifier.fillMaxWidth().clickable { reason = k }, verticalAlignment = Alignment.CenterVertically) { androidx.compose.material3.RadioButton(reason == k, { reason = k }); Text(l) } } }
    }, confirmButton = { TextButton({ scope.launch { runCatching { SampApp.instance.repo.report("user", null, name, reason, "") }.onSuccess { snack.showSnackbar("Gracias, el staff lo revisará") }.onFailure { snack.showSnackbar(it.userMessage()) } }; onDismiss() }) { Text("Enviar") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancelar") } })
}

@Composable
private fun ProfileHeader(u: UserProfile, own: Boolean, onEdit: () -> Unit, onChanged: () -> Unit) {
    val nav = LocalNavigator.current
    val ctx = LocalContext.current
    val api = SampApp.instance.api
    val repo = SampApp.instance.repo
    val scope = rememberCoroutineScope()
    val snack = LocalSnack.current
    var busy by remember { mutableStateOf(false) }
    fun act(block: suspend () -> Unit) { if (busy) return; busy = true; scope.launch { try { block(); onChanged() } catch (e: Exception) { snack.showSnackbar(e.userMessage()) }; busy = false } }
    Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(bottom = 12.dp)) {
        Box {
            Box(Modifier.fillMaxWidth().height(150.dp).background(MaterialTheme.colorScheme.primaryContainer)) {
                if (u.cover.isNotBlank()) AsyncImage(api.imgUrl(u.cover), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Box(Modifier.padding(start = 16.dp, top = 100.dp).size(100.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface).padding(4.dp)) {
                Avatar(u.name, u.skin, 92.dp, online = u.connected == 1)
            }
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            PlayerName(u.name, style = MaterialTheme.typography.headlineSmall)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip({}, label = { Text(u.rango) }, leadingIcon = if (u.rango != "Ciudadano") ({ Icon(Icons.Filled.Star, null, Modifier.size(16.dp)) }) else null)
                Text(if (u.connected == 1) "● En línea" else Clock.ago(u.lastConnection).let { if (it.isBlank()) "" else "Últ. vez $it" }, style = MaterialTheme.typography.bodySmall, color = if (u.connected == 1) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (u.bio.isNotBlank()) Text(u.bio, Modifier.padding(top = 8.dp))
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Stat(count(u.posts), "publicaciones"); Stat(count(u.friends), "amigos"); Stat(count(u.followers), "seguidores"); Stat(count(u.likes), "me gusta")
            }
            if (!u.locked || own) FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Info(Icons.Filled.Star, "Nivel ${u.level} · Rep. ${u.rep}")
                if (u.timePlaying > 0) Info(Icons.Filled.Star, "${hoursPlayed(u.timePlaying)} jugadas")
                if (u.job.isNotBlank()) Info(Icons.Filled.Work, u.job)
                if (u.city.isNotBlank()) Info(Icons.Filled.Place, "Vive en ${u.city}")
                if (u.bday.isNotBlank()) Info(Icons.Filled.Cake, "Cumple ${u.bday.replace('-', '/')}")
                if (u.link.isNotBlank()) Row(Modifier.clickable { openWeb(ctx, u.link) }, verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Link, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary); Text(" " + u.link.removePrefix("https://"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
            }
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (own) OutlinedButton(onEdit, Modifier.weight(1f)) { Text("Editar perfil") }
                else {
                    when (u.fstate) {
                        "friends" -> OutlinedButton({ act { repo.removeFriend(u.name) } }, Modifier.weight(1f), enabled = !busy) { Text("Amigos ✓") }
                        "sent" -> OutlinedButton({ act { repo.removeFriend(u.name) } }, Modifier.weight(1f), enabled = !busy) { Text("Solicitud enviada") }
                        "received" -> Button({ act { repo.acceptFriend(u.name) } }, Modifier.weight(1f), enabled = !busy) { Text("Aceptar solicitud") }
                        else -> Button({ act { repo.addFriend(u.name) } }, Modifier.weight(1f), enabled = !busy) { Text("Agregar") }
                    }
                    OutlinedButton({ nav.chat(u.name) }, Modifier.weight(1f)) { Text("Mensaje") }
                    OutlinedButton({ act { repo.follow(u.name) } }, Modifier.weight(1f), enabled = !busy) { Text(if (u.following) "Siguiendo" else "Seguir") }
                }
            }
        }
    }
}

@Composable private fun Stat(n: String, label: String) = Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(n, style = MaterialTheme.typography.titleMedium); Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
@Composable private fun Info(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) = Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant); Text(" $text", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }

@Composable
private fun PhotosGrid(photos: List<PhotoRef>?, onOpen: (PhotoRef) -> Unit) {
    val api = SampApp.instance.api
    when {
        photos == null -> LoadingBox(Modifier.fillMaxWidth().height(160.dp))
        photos.isEmpty() -> EmptyBox("Aún no hay fotos.")
        else -> Column(Modifier.padding(horizontal = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            photos.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    row.forEach { p -> AsyncImage(api.mediaUrl(p.id), null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).aspectRatio(1f).clickable { onOpen(p) }) }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
fun FriendLine(f: Friend, trailing: @Composable (() -> Unit)? = null) {
    val nav = LocalNavigator.current
    Row(Modifier.fillMaxWidth().clickable { nav.profile(f.name) }.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(f.name, f.skin, 44.dp, online = f.connected == 1)
        PlayerName(f.name, Modifier.padding(start = 12.dp).weight(1f))
        trailing?.invoke()
    }
}

/** Editar biografía, datos del perfil, foto y banner. */
@Composable
private fun EditProfileSheet(u: UserProfile, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val ctx = LocalContext.current
    val repo = SampApp.instance.repo
    val scope = rememberCoroutineScope()
    val snack = LocalSnack.current
    var bio by remember { mutableStateOf(u.bio) }
    var city by remember { mutableStateOf(u.city) }
    var job by remember { mutableStateOf(u.job) }
    var link by remember { mutableStateOf(u.link) }
    var bday by remember { mutableStateOf(u.bday) }
    var busy by remember { mutableStateOf(false) }
    var hasAvatar by remember { mutableStateOf(u.avatar.isNotBlank()) }
    var hasCover by remember { mutableStateOf(u.cover.isNotBlank()) }

    fun photo(kind: String, uri: Uri?) {
        if (uri == null) return
        busy = true
        scope.launch {
            try { val b = withContext(Dispatchers.IO) { Media.compress(ctx, uri, if (kind == "cover") 1600 else 800, 850 * 1024) }; repo.setPhoto(kind, Media.dataUrl(b)); if (kind == "cover") hasCover = true else hasAvatar = true; onSaved(); snack.showSnackbar("Foto actualizada") }
            catch (e: Exception) { snack.showSnackbar(e.userMessage()) }
            busy = false
        }
    }
    val pickAvatar = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { photo("avatar", it) }
    val pickCover = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { photo("cover", it) }
    fun remove(kind: String) { scope.launch { runCatching { repo.removePhoto(kind) }.onSuccess { if (kind == "cover") hasCover = false else hasAvatar = false; onSaved() }.onFailure { snack.showSnackbar(it.userMessage()) } } }

    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Editar perfil", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ pickAvatar.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !busy) { Icon(Icons.Outlined.CameraAlt, null, Modifier.size(18.dp)); Text("  Foto") }
                if (hasAvatar) TextButton({ remove("avatar") }) { Text("Quitar") }
                OutlinedButton({ pickCover.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !busy) { Icon(Icons.Outlined.CameraAlt, null, Modifier.size(18.dp)); Text("  Banner") }
                if (hasCover) TextButton({ remove("cover") }) { Text("Quitar") }
            }
            OutlinedTextField(bio, { if (it.length <= 160) bio = it }, Modifier.fillMaxWidth(), label = { Text("Biografía") }, supportingText = { Text("${bio.length}/160") }, minLines = 2)
            OutlinedTextField(city, { if (it.length <= 40) city = it }, Modifier.fillMaxWidth(), label = { Text("Ciudad") }, singleLine = true)
            OutlinedTextField(job, { if (it.length <= 60) job = it }, Modifier.fillMaxWidth(), label = { Text("Ocupación") }, singleLine = true)
            OutlinedTextField(link, { if (it.length <= 120) link = it }, Modifier.fillMaxWidth(), label = { Text("Enlace (https://…)") }, singleLine = true)
            OutlinedTextField(bday, { if (it.length <= 5) bday = it }, Modifier.fillMaxWidth(), label = { Text("Cumpleaños (MM-DD, ej. 03-21)") }, singleLine = true)
            Button({
                busy = true
                scope.launch {
                    try { repo.setBio(bio.trim()); repo.setInfo(city.trim(), job.trim(), link.trim(), bday.trim()); onSaved(); onDismiss() }
                    catch (e: Exception) { snack.showSnackbar(e.userMessage()); busy = false }
                }
            }, Modifier.fillMaxWidth(), enabled = !busy) { Text("Guardar") }
        }
    }
}
