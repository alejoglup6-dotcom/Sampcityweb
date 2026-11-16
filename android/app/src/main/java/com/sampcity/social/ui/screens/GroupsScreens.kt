@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.sampcity.social.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sampcity.social.SampApp
import com.sampcity.social.data.GroupDetail
import com.sampcity.social.data.GroupItem
import com.sampcity.social.data.GroupList
import com.sampcity.social.data.GroupMember
import com.sampcity.social.data.Post
import com.sampcity.social.ui.LocalNavigator
import com.sampcity.social.ui.components.Avatar
import com.sampcity.social.ui.components.ConfirmDialog
import com.sampcity.social.ui.components.EmptyBox
import com.sampcity.social.ui.components.ErrorBox
import com.sampcity.social.ui.components.LoadingBox
import com.sampcity.social.ui.components.LocalSnack
import com.sampcity.social.ui.components.Paged
import com.sampcity.social.ui.components.PlayerName
import com.sampcity.social.ui.components.PostFeed
import com.sampcity.social.ui.components.PostHostDialogs
import com.sampcity.social.ui.components.SectionTitle
import com.sampcity.social.ui.components.rememberPostHost
import com.sampcity.social.ui.components.userMessage
import com.sampcity.social.util.count
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Composable
fun GroupsScreen() {
    val nav = LocalNavigator.current
    val repo = SampApp.instance.repo
    val scope = rememberCoroutineScope(); val snack = LocalSnack.current
    var data by remember { mutableStateOf<GroupList?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var q by remember { mutableStateOf("") }
    var refreshing by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    fun load() { scope.launch { refreshing = data != null; try { data = repo.groups(q.trim()); error = null } catch (e: Exception) { error = e.userMessage() }; refreshing = false } }
    LaunchedEffect(q) { delay(if (q.isEmpty()) 0 else 300); load() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Grupos") }, navigationIcon = { IconButton(nav::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } }) },
        floatingActionButton = { FloatingActionButton({ creating = true }) { Icon(Icons.Filled.Add, "Crear grupo") } },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(12.dp), singleLine = true, placeholder = { Text("Buscar grupos…") })
            val d = data
            when {
                error != null && d == null -> ErrorBox(error!!, ::load)
                d == null -> LoadingBox()
                else -> PullToRefreshBox(refreshing, ::load, Modifier.weight(1f)) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        if (d.mine.isNotEmpty()) { item { SectionTitle("Mis grupos") }; items(d.mine, key = { "m${it.id}" }) { GroupRow(it) } }
                        if (d.more.isNotEmpty()) { item { SectionTitle("Descubrir") }; items(d.more, key = { "d${it.id}" }) { GroupRow(it) } }
                        if (d.mine.isEmpty() && d.more.isEmpty()) item { EmptyBox(if (q.isBlank()) "Aún no hay grupos. Crea el primero." else "No encontramos grupos con ese nombre.") }
                    }
                }
            }
        }
    }
    if (creating) GroupFormDialog(null, onDismiss = { creating = false }, onSaved = { id -> if (id != null) nav.group(id) else load() })
}

@Composable
private fun GroupRow(g: GroupItem) {
    val nav = LocalNavigator.current
    Row(Modifier.fillMaxWidth().clickable { nav.group(g.id) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Group, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text(g.name, style = MaterialTheme.typography.titleSmall, maxLines = 1); if (g.priv == 1) Icon(Icons.Filled.Lock, "Privado", Modifier.padding(start = 4.dp).size(14.dp)) }
            Text("${count(g.members)} miembros · ${count(g.posts)} publicaciones", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (g.about.isNotBlank()) Text(g.about, style = MaterialTheme.typography.bodySmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GroupFormDialog(g: GroupDetail?, onDismiss: () -> Unit, onSaved: (Int?) -> Unit) {
    val repo = SampApp.instance.repo; val scope = rememberCoroutineScope(); val snack = LocalSnack.current
    var name by remember { mutableStateOf(g?.name ?: "") }; var about by remember { mutableStateOf(g?.about ?: "") }; var priv by remember { mutableStateOf(g?.priv == 1) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(if (g == null) "Crear grupo" else "Editar grupo") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { if (it.length <= 60) name = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Nombre") })
            OutlinedTextField(about, { if (it.length <= 300) about = it }, Modifier.fillMaxWidth(), minLines = 3, label = { Text("Descripción") })
            Row(verticalAlignment = Alignment.CenterVertically) { Switch(priv, { priv = it }); Text("  Grupo privado (hay que pedir unirse)") }
        }
    }, confirmButton = {
        TextButton({ busy = true; scope.launch { try { if (g == null) onSaved(repo.createGroup(name.trim(), about.trim(), priv).id) else { repo.updateGroup(g.id, name.trim(), about.trim(), priv); onSaved(null) }; onDismiss() } catch (e: Exception) { snack.showSnackbar(e.userMessage()); busy = false } } }, enabled = name.trim().length >= 3 && !busy) { Text("Guardar") }
    }, dismissButton = { TextButton(onDismiss, enabled = !busy) { Text("Cancelar") } })
}

class GroupVM(val id: Int) : ViewModel() {
    private val repo = SampApp.instance.repo
    var group by mutableStateOf<GroupDetail?>(null)
    var error by mutableStateOf<String?>(null)
    val posts = Paged<Post>(viewModelScope, { it.id }, { it.size > 12 }, { it.take(12) }) { _, page -> repo.posts("group=$id&type=post&page=$page") }
    init {
        load()
        viewModelScope.launch { SampApp.instance.bus.collect { if (it == "post-created") posts.refresh() } }
    }
    fun load() { viewModelScope.launch { try { group = repo.group(id); error = null; posts.refresh(true) } catch (e: Exception) { error = e.userMessage() } } }
}
class GroupVMFactory(private val id: Int) : ViewModelProvider.Factory { @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = GroupVM(id) as T }

@Composable
fun GroupScreen(id: Int) {
    val vm: GroupVM = viewModel(key = "group$id", factory = GroupVMFactory(id))
    val nav = LocalNavigator.current; val repo = SampApp.instance.repo
    val scope = rememberCoroutineScope(); val snack = LocalSnack.current
    val host = rememberPostHost(onUpdate = { p -> vm.posts.update({ it.id == p.id }, p) }, onRemove = { i -> vm.posts.remove { it.id == i } })
    var tab by remember { mutableStateOf(0) }
    var edit by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }
    val g = vm.group
    val state = rememberLazyListState()
    fun act(done: String? = null, block: suspend () -> Unit) { scope.launch { try { block(); vm.load(); done?.let { snack.showSnackbar(it) } } catch (e: Exception) { snack.showSnackbar(e.userMessage()) } } }

    Scaffold(
        topBar = { TopAppBar(title = { Text(g?.name ?: "Grupo", maxLines = 1) }, navigationIcon = { IconButton(nav::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } }) },
        floatingActionButton = { if (g != null && g.role > 0) FloatingActionButton({ nav.compose(id) }) { Icon(Icons.Filled.Add, "Publicar") } },
    ) { pad ->
        when {
            g == null && vm.error != null -> ErrorBox(vm.error!!, vm::load, Modifier.padding(pad).fillMaxSize())
            g == null -> LoadingBox(Modifier.padding(pad).fillMaxSize())
            else -> LazyColumn(Modifier.padding(pad).fillMaxSize(), state = state, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Text(g.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f)); if (g.priv == 1) Icon(Icons.Filled.Lock, "Privado") }
                        if (g.about.isNotBlank()) Text(g.about)
                        Text("${count(g.members)} miembros · ${if (g.priv == 1) "Grupo privado" else "Grupo público"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        when (g.role) {
                            -1 -> Button({ act(if (g.priv == 1) "Solicitud enviada" else "Te uniste al grupo") { repo.joinGroup(id) } }) { Text(if (g.priv == 1) "Pedir unirme" else "Unirme") }
                            0 -> OutlinedButton({ act { repo.leaveGroup(id) } }) { Text("Solicitud enviada · Cancelar") }
                            else -> if (!g.isOwner) OutlinedButton({ confirm = "leave" }) { Text("Salir del grupo") }
                        }
                    }
                }
                item { TabRow(tab) { listOf("Publicaciones", "Miembros", "Información").forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t, maxLines = 1) }) } } }
                when (tab) {
                    0 -> if (!g.open) item { EmptyBox("Este grupo es privado. Pide unirte para ver sus publicaciones.") } else {
                        when {
                            vm.posts.loading && vm.posts.items.isEmpty() -> item { LoadingBox(Modifier.fillMaxWidth().padding(40.dp)) }
                            vm.posts.loaded && vm.posts.items.isEmpty() -> item { EmptyBox("Aún no hay publicaciones en el grupo.") }
                        }
                        items(vm.posts.items, key = { it.id }) { p -> com.sampcity.social.ui.components.PostCard(p, host) }
                    }
                    1 -> {
                        if (g.pend.isNotEmpty()) { item { SectionTitle("Solicitudes (${g.pend.size})") }; items(g.pend, key = { "p${it.name}" }) { m -> MemberRow(m, g, act = ::act, pending = true) } }
                        if (!g.open) item { EmptyBox("Solo los miembros ven la lista.") } else items(g.ms, key = { it.name }) { m -> MemberRow(m, g, act = ::act) }
                    }
                    else -> item {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(g.about.ifBlank { "Sin descripción." })
                            if (g.role == 2) { OutlinedButton({ edit = true }) { Text("Editar grupo") } }
                            if (g.isOwner || SampApp.instance.session.isStaff) OutlinedButton({ confirm = "delete" }) { Text("Eliminar grupo", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
                if (tab == 0 && vm.posts.loadingMore) item { LoadingBox(Modifier.fillMaxWidth().padding(16.dp)) }
            }
        }
    }
    com.sampcity.social.ui.components.OnEndReached(state) { if (tab == 0) vm.posts.more() }
    PostHostDialogs(host)
    if (edit && g != null) GroupFormDialog(g, onDismiss = { edit = false }, onSaved = { vm.load() })
    when (confirm) {
        "leave" -> ConfirmDialog("¿Salir del grupo?", "", "Salir", true, { act { repo.leaveGroup(id) } }) { confirm = null }
        "delete" -> ConfirmDialog("¿Eliminar el grupo?", "Se borran también sus publicaciones.", "Eliminar", true, { scope.launch { runCatching { repo.deleteGroup(id) }.onSuccess { nav.back() }.onFailure { snack.showSnackbar(it.userMessage()) } } }) { confirm = null }
    }
}

@Composable
private fun MemberRow(m: GroupMember, g: GroupDetail, act: (String?, suspend () -> Unit) -> Unit, pending: Boolean = false) {
    val nav = LocalNavigator.current; val repo = SampApp.instance.repo
    val me = SampApp.instance.session.me
    Row(Modifier.fillMaxWidth().clickable { nav.profile(m.name) }.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(m.name, m.skin, 42.dp, online = m.connected == 1)
        Column(Modifier.padding(start = 12.dp).weight(1f)) { PlayerName(m.name); if (m.role == 2) Text("Administrador", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
        if (g.role == 2 && m.name != me?.name) {
            if (pending) { TextButton({ act(null) { repo.groupMember(g.id, m.name, "accept") } }) { Text("Aceptar") }; TextButton({ act(null) { repo.groupMember(g.id, m.name, "remove") } }) { Text("Rechazar") } }
            else { TextButton({ act(null) { repo.groupMember(g.id, m.name, "admin") } }) { Text(if (m.role == 2) "Quitar admin" else "Hacer admin") }; TextButton({ act(null) { repo.groupMember(g.id, m.name, "remove") } }) { Text("Expulsar") } }
        }
    }
}
