@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.sampcity.social.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sampcity.social.SampApp
import com.sampcity.social.data.UserHit
import kotlinx.coroutines.delay

/** Elegir jugadores: aparecen tus amigos y se puede buscar a cualquiera. `selected` permite elegir varios. */
@Composable
fun PlayerPickerDialog(title: String, selected: List<String>? = null, exclude: Set<String> = emptySet(), onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val repo = SampApp.instance.repo
    var q by remember { mutableStateOf("") }
    var friends by remember { mutableStateOf<List<UserHit>>(emptyList()) }
    var res by remember { mutableStateOf<List<UserHit>>(emptyList()) }
    LaunchedEffect(Unit) { friends = runCatching { repo.friends() }.getOrDefault(emptyList()).map { UserHit(it.name, it.skin) } }
    LaunchedEffect(q) { if (q.trim().length < 2) res = emptyList() else { delay(250); res = runCatching { repo.searchUsers(q.trim()) }.getOrDefault(emptyList()) } }
    val list = (if (q.trim().length >= 2) res else friends).filter { it.name !in exclude && it.name != SampApp.instance.session.me?.name }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        Column {
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Buscar jugador…") })
            if (q.trim().length < 2 && friends.isNotEmpty()) Text("Tus amigos", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            LazyColumn(Modifier.height(260.dp)) {
                items(list, key = { it.name }) { u ->
                    ListItem({ Text(u.name.pretty()) }, leadingContent = { Avatar(u.name, u.skin, 36.dp) }, trailingContent = { if (selected != null && u.name in selected) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent), modifier = Modifier.clickable { onPick(u.name); if (selected == null) onDismiss() })
                }
            }
        }
    }, confirmButton = { TextButton(onDismiss) { Text(if (selected != null) "Listo" else "Cancelar") } })
}
