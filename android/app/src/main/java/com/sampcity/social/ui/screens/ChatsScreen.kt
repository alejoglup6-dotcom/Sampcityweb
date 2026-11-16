@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.sampcity.social.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sampcity.social.SampApp
import com.sampcity.social.data.MsgHit
import com.sampcity.social.ui.LocalNavigator
import com.sampcity.social.ui.components.Avatar
import com.sampcity.social.ui.components.EmptyBox
import com.sampcity.social.ui.components.ErrorBox
import com.sampcity.social.ui.components.LoadingBox
import com.sampcity.social.ui.components.LocalSnack
import com.sampcity.social.ui.components.PlayerPickerDialog
import com.sampcity.social.ui.components.SIcon
import com.sampcity.social.ui.components.pretty
import com.sampcity.social.ui.components.userMessage
import com.sampcity.social.util.Clock
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Una fila de la lista: conversación privada (name) o chat de grupo (room). */
data class ConvRow(val name: String, val skin: String?, val online: Boolean, val room: Int?, val body: String, val mine: Boolean, val from: String, val at: String?, val unread: Int, val mid: Int, val members: Int = 0)

class ChatsVM : ViewModel() {
    private val app = SampApp.instance
    var rows by mutableStateOf<List<ConvRow>>(emptyList())
    var loading by mutableStateOf(true)
    var refreshing by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    init {
        load()
        viewModelScope.launch { app.live.events.collect { if (it.type == "msg" || it.type == "del" || it.type == "seen") load(silent = true) } }
    }
    fun load(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) { if (rows.isEmpty()) loading = true else refreshing = true }
            try {
                val a = app.repo.chats(); val b = runCatching { app.repo.rooms() }.getOrDefault(emptyList())
                rows = (a.map { ConvRow(it.name, it.skin, it.connected == 1, null, it.body, it.mine, "", it.at, it.unread, it.mid) } +
                    b.map { ConvRow(it.name, null, false, it.id, it.body, it.mine, it.from, it.at, it.unread, it.mid, it.members) }).sortedByDescending { it.mid }
                error = null
            } catch (e: Exception) { if (rows.isEmpty()) error = e.userMessage() }
            loading = false; refreshing = false
            app.session.refreshCounts()
        }
    }
}

@Composable
fun ChatsScreen(vm: ChatsVM = viewModel()) {
    val nav = LocalNavigator.current
    val repo = SampApp.instance.repo
    val scope = rememberCoroutineScope()
    val snack = LocalSnack.current
    var searching by remember { mutableStateOf(false) }
    var q by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<MsgHit>>(emptyList()) }
    var newChat by remember { mutableStateOf(false) }
    var newGroup by remember { mutableStateOf(false) }
    LaunchedEffect(q) { if (q.trim().length < 2) hits = emptyList() else { kotlinx.coroutines.delay(300); hits = runCatching { repo.searchMsgs(q.trim()) }.getOrDefault(emptyList()) } }

    Scaffold(topBar = {
        TopAppBar(
            title = { if (searching) OutlinedTextField(q, { q = it }, singleLine = true, placeholder = { Text("Buscar en mensajes…") }, modifier = Modifier.fillMaxWidth().padding(end = 8.dp)) else Text("Mensajes") },
            actions = {
                if (searching) IconButton({ searching = false; q = "" }) { Icon(Icons.Filled.Close, "Cerrar") }
                else { IconButton({ searching = true }) { SIcon("buscar") }; IconButton({ newGroup = true }) { SIcon("comunidad") }; IconButton({ newChat = true }) { SIcon("mas") } }
            },
        )
    }) { pad ->
        when {
            searching && q.trim().length >= 2 -> LazyColumn(Modifier.padding(pad).fillMaxSize()) {
                if (hits.isEmpty()) item { EmptyBox("Sin resultados") }
                items(hits, key = { it.id }) { h ->
                    Column(Modifier.fillMaxWidth().clickable { if (h.room != 0) nav.room(h.room) else nav.chat(h.with) }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(if (h.room != 0) h.rname else h.with.pretty(), style = MaterialTheme.typography.titleSmall)
                        Text((if (h.mine) "Tú: " else "${h.from.pretty().substringBefore(' ')}: ") + h.body, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            vm.loading -> LoadingBox(Modifier.padding(pad).fillMaxSize())
            vm.error != null -> ErrorBox(vm.error!!, { vm.load() }, Modifier.padding(pad).fillMaxSize())
            else -> PullToRefreshBox(vm.refreshing, { vm.load() }, Modifier.padding(pad)) {
                LazyColumn(Modifier.fillMaxSize()) {
                    if (vm.rows.isEmpty()) item { EmptyBox("Aún no tienes conversaciones.", action = "Nuevo mensaje", onAction = { newChat = true }) }
                    items(vm.rows, key = { (it.room?.let { r -> "r$r" } ?: it.name) }) { r ->
                        Row(Modifier.fillMaxWidth().clickable { if (r.room != null) nav.room(r.room) else nav.chat(r.name) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (r.room != null) Box(Modifier.size(50.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) { SIcon("comunidad", size = 26.dp) }
                            else Avatar(r.name, r.skin, 50.dp, online = r.online)
                            Column(Modifier.padding(horizontal = 12.dp).weight(1f)) {
                                Text(if (r.room != null) r.name else r.name.pretty(), style = MaterialTheme.typography.titleSmall, fontWeight = if (r.unread > 0) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text((if (r.mine) "Tú: " else if (r.room != null && r.from.isNotBlank()) r.from.pretty().substringBefore(' ') + ": " else "") + r.body.ifBlank { if (r.room != null) "${r.members} miembros" else "" }, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (r.unread > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(Clock.ago(r.at), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (r.unread > 0) Badge(Modifier.padding(top = 4.dp)) { Text(if (r.unread > 99) "99+" else r.unread.toString()) }
                            }
                        }
                    }
                }
            }
        }
    }
    if (newChat) PlayerPickerDialog("Nuevo mensaje", onPick = { nav.chat(it) }, onDismiss = { newChat = false })
    if (newGroup) NewGroupDialog(onDismiss = { newGroup = false }, onCreated = { nav.room(it) })
}

@Composable
private fun NewGroupDialog(onDismiss: () -> Unit, onCreated: (Int) -> Unit) {
    val scope = rememberCoroutineScope(); val snack = LocalSnack.current
    var name by remember { mutableStateOf("") }
    val members = remember { mutableStateListOf<String>() }
    var pick by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("Nuevo grupo") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { if (it.length <= 40) name = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Nombre del grupo") })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { members.forEach { m -> InputChip(true, { members.remove(m) }, { Text(m.pretty()) }, trailingIcon = { Icon(Icons.Filled.Close, null, Modifier.size(16.dp)) }) } }
            TextButton({ pick = true }) { Text("+ Agregar jugadores") }
        }
    }, confirmButton = {
        TextButton({ busy = true; scope.launch { runCatching { SampApp.instance.repo.createRoom(name.trim(), members.toList()) }.onSuccess { onCreated(it.id); onDismiss() }.onFailure { snack.showSnackbar(it.userMessage()); busy = false } } }, enabled = name.trim().length >= 2 && members.isNotEmpty() && !busy) { Text("Crear") }
    }, dismissButton = { TextButton(onDismiss, enabled = !busy) { Text("Cancelar") } })
    if (pick) PlayerPickerDialog("Agregar al grupo", selected = members, onPick = { if (it in members) members.remove(it) else if (members.size < 29) members.add(it) }, onDismiss = { pick = false })
}
