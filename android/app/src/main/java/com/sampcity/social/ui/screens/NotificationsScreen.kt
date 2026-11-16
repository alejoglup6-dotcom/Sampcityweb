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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.sampcity.social.data.Notif
import com.sampcity.social.live.NotifText
import com.sampcity.social.ui.LocalNavigator
import com.sampcity.social.ui.components.Avatar
import com.sampcity.social.ui.components.EmptyBox
import com.sampcity.social.ui.components.SIcon
import com.sampcity.social.ui.components.ErrorBox
import com.sampcity.social.ui.components.LoadingBox
import com.sampcity.social.ui.components.OnEndReached
import com.sampcity.social.ui.components.Paged
import com.sampcity.social.util.Clock
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class NotifVM : ViewModel() {
    private val app = SampApp.instance
    var unreadOnly by mutableStateOf(false)
    val list = Paged<Notif>(viewModelScope, { it.id }, { it.size > 30 }, { it.take(30) }) { last, _ -> app.repo.notifications(last?.id, unreadOnly) }
    init {
        list.refresh(true)
        viewModelScope.launch { app.live.events.collect { if (it.type == "notif") list.refresh() } }
    }
    fun setFilter(u: Boolean) { unreadOnly = u; list.refresh(true) }
    fun markAll() { viewModelScope.launch { runCatching { app.repo.readNotif(null) }.onSuccess { list.refresh(); app.session.refreshCounts() } } }
    fun read(n: Notif) { if (n.seen == 0) viewModelScope.launch { runCatching { app.repo.readNotif(n.id) }.onSuccess { list.update({ it.id == n.id }, n.copy(seen = 1)); app.session.refreshCounts() } } }
    fun delete(n: Notif) { viewModelScope.launch { runCatching { app.repo.deleteNotif(n.id) }.onSuccess { list.remove { it.id == n.id }; app.session.refreshCounts() } } }
}

@Composable
fun NotificationsScreen(vm: NotifVM = viewModel()) {
    val nav = LocalNavigator.current
    var menu by remember { mutableStateOf(false) }
    val state = rememberLazyListState()
    Scaffold(topBar = {
        TopAppBar(title = { Text("Avisos") }, actions = {
            Box {
                IconButton({ menu = true }) { SIcon("puntos") }
                DropdownMenu(menu, { menu = false }) { DropdownMenuItem({ Text("Marcar todo como leído") }, { menu = false; vm.markAll() }) }
            }
        })
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!vm.unreadOnly, { vm.setFilter(false) }, { Text("Todas") }); FilterChip(vm.unreadOnly, { vm.setFilter(true) }, { Text("No leídas") })
            }
            PullToRefreshBox(vm.list.refreshing, { vm.list.refresh() }, Modifier.weight(1f)) {
                LazyColumn(Modifier.fillMaxSize(), state = state) {
                    when {
                        vm.list.loading && vm.list.items.isEmpty() -> item { LoadingBox(Modifier.fillMaxWidth().padding(40.dp)) }
                        vm.list.error != null && vm.list.items.isEmpty() -> item { ErrorBox(vm.list.error!!, { vm.list.refresh(true) }, Modifier.fillMaxWidth().padding(24.dp)) }
                        vm.list.loaded && vm.list.items.isEmpty() -> item { EmptyBox(if (vm.unreadOnly) "No tienes avisos sin leer." else "Aún no tienes avisos.") }
                    }
                    items(vm.list.items, key = { it.id }) { n -> NotifRow(n, onOpen = { vm.read(n); nav.open(NotifText.route(n)) }, onDelete = { vm.delete(n) }, onRead = { vm.read(n) }) }
                }
                OnEndReached(state) { vm.list.more() }
            }
        }
    }
}

@Composable
private fun NotifRow(n: Notif, onOpen: () -> Unit, onDelete: () -> Unit, onRead: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val unread = n.seen == 0
    Row(Modifier.fillMaxWidth().background(if (unread) MaterialTheme.colorScheme.primary.copy(alpha = .08f) else androidx.compose.ui.graphics.Color.Transparent).clickable(onClick = onOpen).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (n.actor != null) Avatar(n.actor, n.skin, 46.dp) else Box(Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer))
        Column(Modifier.padding(horizontal = 12.dp).weight(1f)) {
            Text(NotifText.line(n), fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (n.body.isNotBlank() && n.type !in setOf("news", "update")) Text(n.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(Clock.ago(n.createdAt), style = MaterialTheme.typography.labelSmall, color = if (unread) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (unread) Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        Box {
            IconButton({ menu = true }, Modifier.size(32.dp)) { SIcon("puntos", size = 20.dp) }
            DropdownMenu(menu, { menu = false }) {
                if (unread) DropdownMenuItem({ Text("Marcar como leído") }, { menu = false; onRead() })
                DropdownMenuItem({ Text("Eliminar") }, { menu = false; onDelete() })
            }
        }
    }
}
