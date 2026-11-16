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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.unit.dp
import com.sampcity.social.SampApp
import com.sampcity.social.data.EventItem
import com.sampcity.social.data.EventPerson
import com.sampcity.social.ui.LocalNavigator
import com.sampcity.social.ui.components.Avatar
import com.sampcity.social.ui.components.ConfirmDialog
import com.sampcity.social.ui.components.EmptyBox
import com.sampcity.social.ui.components.ErrorBox
import com.sampcity.social.ui.components.LoadingBox
import com.sampcity.social.ui.components.LocalSnack
import com.sampcity.social.ui.components.PlayerName
import com.sampcity.social.ui.components.pretty
import com.sampcity.social.ui.components.userMessage
import com.sampcity.social.util.Clock
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

@Composable
fun EventsScreen() {
    val nav = LocalNavigator.current; val repo = SampApp.instance.repo
    val scope = rememberCoroutineScope(); val snack = LocalSnack.current
    var past by remember { mutableStateOf(false) }
    var list by remember { mutableStateOf<List<EventItem>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var people by remember { mutableStateOf<EventItem?>(null) }
    var del by remember { mutableStateOf<EventItem?>(null) }
    fun load() { scope.launch { refreshing = list != null; try { list = repo.events(past); error = null } catch (e: Exception) { error = e.userMessage() }; refreshing = false } }
    LaunchedEffect(past) { list = null; load() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Eventos") }, navigationIcon = { IconButton(nav::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } }) },
        floatingActionButton = { FloatingActionButton({ creating = true }) { Icon(Icons.Filled.Add, "Crear evento") } },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(!past, { past = false }, { Text("Próximos") }); FilterChip(past, { past = true }, { Text("Pasados") }) }
            val l = list
            when {
                error != null && l == null -> ErrorBox(error!!, ::load)
                l == null -> LoadingBox()
                else -> PullToRefreshBox(refreshing, ::load, Modifier.weight(1f)) {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (l.isEmpty()) item { EmptyBox(if (past) "No hay eventos pasados." else "No hay eventos próximos. ¡Crea uno!") }
                        items(l, key = { it.id }) { e ->
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(e.title, style = MaterialTheme.typography.titleMedium)
                                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Event, null, Modifier.padding(end = 6.dp)); Text(Clock.eventDay(e.starts)) }
                                    if (e.place.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Place, null, Modifier.padding(end = 6.dp)); Text(e.place) }
                                    if (e.about.isNotBlank()) Text(e.about, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row(verticalAlignment = Alignment.CenterVertically) { Avatar(e.owner, e.skin, 24.dp); Text("  ${e.owner.pretty()}", style = MaterialTheme.typography.bodySmall) }
                                    Text("${e.going} asistirán · ${e.maybe} interesados", Modifier.clickable { people = e }.padding(vertical = 4.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                                    if (!past) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf(1 to "Asistiré", 2 to "Me interesa").forEach { (st, label) ->
                                            val onClick = { scope.launch { runCatching { repo.rsvp(e.id, st) }.onSuccess { r -> list = list?.map { if (it.id == r.id) r else it } }.onFailure { snack.showSnackbar(it.userMessage()) } }; Unit }
                                            if (e.mine == st) Button(onClick) { Text(label) } else OutlinedButton(onClick) { Text(label) }
                                        }
                                        if (e.own) TextButton({ del = e }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    people?.let { e -> PeopleSheet(e) { people = null } }
    del?.let { e -> ConfirmDialog("¿Eliminar el evento?", "", "Eliminar", true, { scope.launch { runCatching { repo.deleteEvent(e.id) }.onSuccess { load() }.onFailure { snack.showSnackbar(it.userMessage()) } } }) { del = null } }
    if (creating) EventDialog(onDismiss = { creating = false }, onDone = { past = false; load() })
}

@Composable
private fun PeopleSheet(e: EventItem, onDismiss: () -> Unit) {
    var list by remember { mutableStateOf<List<EventPerson>?>(null) }
    val nav = LocalNavigator.current
    LaunchedEffect(e.id) { list = runCatching { SampApp.instance.repo.eventPeople(e.id) }.getOrDefault(emptyList()) }
    ModalBottomSheet(onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(e.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
            if (list == null) LoadingBox(Modifier.fillMaxWidth().padding(24.dp))
            list.orEmpty().forEach { p -> Row(Modifier.fillMaxWidth().clickable { onDismiss(); nav.profile(p.name) }.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { Avatar(p.name, p.skin, 38.dp); PlayerName(p.name, Modifier.padding(start = 10.dp).weight(1f)); Text(if (p.st == 1) "Asistirá" else "Interesado", style = MaterialTheme.typography.labelMedium) } }
            if (list?.isEmpty() == true) EmptyBox("Nadie ha respondido todavía.")
        }
    }
}

@Composable
private fun EventDialog(onDismiss: () -> Unit, onDone: () -> Unit) {
    val repo = SampApp.instance.repo; val scope = rememberCoroutineScope(); val snack = LocalSnack.current
    var title by remember { mutableStateOf("") }; var place by remember { mutableStateOf("") }; var about by remember { mutableStateOf("") }
    var dateMs by remember { mutableStateOf<Long?>(null) }; var hour by remember { mutableStateOf(20) }; var minute by remember { mutableStateOf(0) }
    var step by remember { mutableStateOf(0) }  // 0 formulario, 1 fecha, 2 hora
    var busy by remember { mutableStateOf(false) }
    val dateFmt = SimpleDateFormat("EEE d 'de' MMM yyyy", Locale("es", "CO"))
    when (step) {
        1 -> { val st = rememberDatePickerState(); AlertDialog({ step = 0 }, confirmButton = { TextButton({ dateMs = st.selectedDateMillis; step = 0 }) { Text("Listo") } }, text = { DatePicker(st) }) }
        2 -> { val st = rememberTimePickerState(hour, minute, true); AlertDialog({ step = 0 }, confirmButton = { TextButton({ hour = st.hour; minute = st.minute; step = 0 }) { Text("Listo") } }, text = { TimePicker(st) }) }
        else -> AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("Crear evento") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { if (it.length <= 80) title = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Título") })
                OutlinedTextField(place, { if (it.length <= 80) place = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Lugar") })
                OutlinedTextField(about, { if (it.length <= 600) about = it }, Modifier.fillMaxWidth(), minLines = 2, label = { Text("Detalles") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ step = 1 }) { Text(dateMs?.let { dateFmt.apply { timeZone = TimeZone.getTimeZone("UTC") }.format(it) } ?: "Elegir fecha") }
                    OutlinedButton({ step = 2 }) { Text("%02d:%02d".format(hour, minute)) }
                }
            }
        }, confirmButton = {
            TextButton({
                val d = dateMs ?: return@TextButton
                val c = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = d }
                val starts = "%04d-%02d-%02dT%02d:%02d".format(Locale.US, c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH), hour, minute)
                busy = true
                scope.launch { try { repo.createEvent(title.trim(), about.trim(), place.trim(), starts); onDone(); onDismiss() } catch (e: Exception) { snack.showSnackbar(e.userMessage()); busy = false } }
            }, enabled = title.trim().length >= 3 && dateMs != null && !busy) { Text("Crear") }
        }, dismissButton = { TextButton(onDismiss, enabled = !busy) { Text("Cancelar") } })
    }
}
