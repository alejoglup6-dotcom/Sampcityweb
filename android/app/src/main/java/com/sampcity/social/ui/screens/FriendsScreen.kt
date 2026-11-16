@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.sampcity.social.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sampcity.social.SampApp
import com.sampcity.social.data.Friend
import com.sampcity.social.data.FriendHit
import com.sampcity.social.data.FriendReq
import com.sampcity.social.data.Suggestion
import com.sampcity.social.ui.LocalNavigator
import com.sampcity.social.ui.components.Avatar
import com.sampcity.social.ui.components.EmptyBox
import com.sampcity.social.ui.components.ErrorBox
import com.sampcity.social.ui.components.LoadingBox
import com.sampcity.social.ui.components.LocalSnack
import com.sampcity.social.ui.components.PlayerName
import com.sampcity.social.ui.components.userMessage
import com.sampcity.social.util.Clock
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun FriendsScreen() {
    val nav = LocalNavigator.current
    val app = SampApp.instance
    val repo = app.repo
    val scope = rememberCoroutineScope()
    val snack = LocalSnack.current
    var tab by remember { mutableStateOf(0) }
    var friends by remember { mutableStateOf<List<Friend>?>(null) }
    var reqs by remember { mutableStateOf<List<FriendReq>?>(null) }
    var sug by remember { mutableStateOf<List<Suggestion>?>(null) }
    var q by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<FriendHit>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    fun load() { scope.launch { try {
        friends = repo.friends(); reqs = repo.friendRequests(); sug = repo.suggestions(); error = null; app.session.refreshMe()
    } catch (e: Exception) { error = e.userMessage() } } }
    LaunchedEffect(Unit) { load() }
    LaunchedEffect(q) { if (q.trim().length < 2) hits = emptyList() else { delay(300); hits = runCatching { repo.findPlayers(q.trim()) }.getOrDefault(emptyList()) } }
    fun act(block: suspend () -> Unit) { scope.launch { try { block(); load(); if (q.trim().length >= 2) hits = repo.findPlayers(q.trim()) } catch (e: Exception) { snack.showSnackbar(e.userMessage()) } } }

    Scaffold(topBar = { TopAppBar(title = { Text("Amigos") }, navigationIcon = { IconButton(nav::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            TabRow(tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Amigos") })
                Tab(tab == 1, { tab = 1 }, text = { BadgedBox(badge = { if ((reqs?.size ?: 0) > 0) Badge { Text("${reqs!!.size}") } }) { Text("Solicitudes") } })
                Tab(tab == 2, { tab = 2 }, text = { Text("Sugerencias") })
                Tab(tab == 3, { tab = 3 }, text = { Text("Buscar") })
            }
            if (error != null && friends == null) ErrorBox(error!!, ::load) else if (friends == null) LoadingBox() else LazyColumn(Modifier.fillMaxSize()) {
                when (tab) {
                    0 -> { if (friends!!.isEmpty()) item { EmptyBox("Aún no tienes amigos.", action = "Buscar amigos", onAction = { tab = 3 }) }
                        items(friends!!, key = { it.name }) { f -> FriendLineRow(f.name, f.skin, f.connected == 1, null) { OutlinedButton({ nav.chat(f.name) }) { Text("Mensaje") } } } }
                    1 -> { if (reqs!!.isEmpty()) item { EmptyBox("No tienes solicitudes pendientes.") }
                        items(reqs!!, key = { it.name }) { r ->
                            FriendLineRow(r.name, r.skin, false, buildString { append(Clock.ago(r.createdAt)); if (r.mutual > 0) append(" · ${r.mutual} amigo${if (r.mutual == 1) "" else "s"} en común") }) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Button({ act { repo.acceptFriend(r.name) } }) { Text("Aceptar") }; OutlinedButton({ act { repo.removeFriend(r.name) } }) { Text("Rechazar") } }
                            }
                        } }
                    2 -> { if (sug!!.isEmpty()) item { EmptyBox("No hay sugerencias por ahora.") }
                        items(sug!!, key = { it.name }) { s -> FriendLineRow(s.name, s.skin, s.connected == 1, if (s.mutual > 0) "${s.mutual} amigo${if (s.mutual == 1) "" else "s"} en común" else "Nivel ${s.level}") { StateButton(s.state, s.name, ::act) } } }
                    else -> { item { OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(12.dp), singleLine = true, label = { Text("Buscar jugadores…") }) }
                        if (q.trim().length >= 2 && hits.isEmpty()) item { EmptyBox("Sin resultados") }
                        items(hits, key = { it.name }) { h -> FriendLineRow(h.name, h.skin, h.connected == 1, null) { StateButton(h.state, h.name, ::act) } } }
                }
            }
        }
    }
}

@Composable
private fun StateButton(state: String, name: String, act: (suspend () -> Unit) -> Unit) {
    val repo = SampApp.instance.repo
    when (state) {
        "friends" -> OutlinedButton({ act { repo.removeFriend(name) } }) { Text("Amigos ✓") }
        "sent" -> OutlinedButton({ act { repo.removeFriend(name) } }) { Text("Enviada") }
        "received" -> Button({ act { repo.acceptFriend(name) } }) { Text("Aceptar") }
        else -> Button({ act { repo.addFriend(name) } }) { Text("Agregar") }
    }
}

@Composable
private fun FriendLineRow(name: String, skin: String?, online: Boolean, sub: String?, trailing: @Composable () -> Unit) {
    val nav = LocalNavigator.current
    Row(Modifier.fillMaxWidth().clickable { nav.profile(name) }.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(name, skin, 46.dp, online = online)
        Column(Modifier.padding(horizontal = 12.dp).weight(1f)) { PlayerName(name); if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        trailing()
    }
}
