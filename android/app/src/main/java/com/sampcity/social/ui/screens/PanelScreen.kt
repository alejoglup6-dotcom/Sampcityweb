@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.sampcity.social.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sampcity.social.SampApp
import com.sampcity.social.data.AdminStats
import com.sampcity.social.data.Ban
import com.sampcity.social.data.ModLog
import com.sampcity.social.data.Report
import com.sampcity.social.data.VerifiedUser
import com.sampcity.social.ui.LocalNavigator
import com.sampcity.social.ui.components.Avatar
import com.sampcity.social.ui.components.EmptyBox
import com.sampcity.social.ui.components.ErrorBox
import com.sampcity.social.ui.components.LoadingBox
import com.sampcity.social.ui.components.LocalSnack
import com.sampcity.social.ui.components.SectionTitle
import com.sampcity.social.ui.components.pretty
import com.sampcity.social.ui.components.userMessage
import com.sampcity.social.util.Clock
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TOTALS = listOf("jugadores" to "Jugadores", "conectados" to "Conectados", "activos7" to "Activos en la app (7 días)", "publicaciones" to "Publicaciones", "reels" to "Reels", "comentarios" to "Comentarios", "mensajes" to "Mensajes", "grupos" to "Grupos", "anuncios" to "Anuncios activos", "reportes" to "Reportes pendientes", "suspendidos" to "Suspendidos", "medios_mb" to "Medios (MB)")

@Composable
fun PanelScreen() {
    val nav = LocalNavigator.current; val repo = SampApp.instance.repo
    val scope = rememberCoroutineScope(); val snack = LocalSnack.current
    val me = SampApp.instance.session.me
    var tab by remember { mutableStateOf(0) }
    var stats by remember { mutableStateOf<AdminStats?>(null) }
    var bans by remember { mutableStateOf<List<Ban>>(emptyList()) }
    var words by remember { mutableStateOf<List<String>>(emptyList()) }
    var verified by remember { mutableStateOf<List<VerifiedUser>>(emptyList()) }
    var reports by remember { mutableStateOf<List<Report>>(emptyList()) }
    var log by remember { mutableStateOf<List<ModLog>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    fun load() { scope.launch { try {
        stats = repo.adminStats(); bans = repo.bans(); words = repo.words(); verified = repo.verified(); reports = repo.reports(); log = repo.modLog(); error = null; loaded = true
    } catch (e: Exception) { error = e.userMessage() } } }
    LaunchedEffect(Unit) { if (me?.canPost == true) load() }
    fun act(msg: String = "Hecho", block: suspend () -> Unit) { scope.launch { try { block(); load(); snack.showSnackbar(msg) } catch (e: Exception) { snack.showSnackbar(e.userMessage()) } } }
    val tabs = listOf("Resumen", "Reportes", "Suspensiones", "Palabras", "Verificados", "Registro")

    Scaffold(topBar = { TopAppBar(title = { Text("Panel del staff") }, navigationIcon = { IconButton(nav::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } }) }) { pad ->
        when {
            me?.canPost != true -> EmptyBox("Solo el staff puede ver esta página.", Modifier.padding(pad).fillMaxSize())
            error != null && !loaded -> ErrorBox(error!!, ::load, Modifier.padding(pad).fillMaxSize())
            !loaded -> LoadingBox(Modifier.padding(pad).fillMaxSize())
            else -> Column(Modifier.padding(pad).fillMaxSize()) {
                ScrollableTabRow(tab, edgePadding = 8.dp) { tabs.forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(if (i == 1 && reports.isNotEmpty()) "$t (${reports.size})" else t, maxLines = 1) }) } }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (tab) {
                        0 -> { val st = stats!!
                            item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { TOTALS.forEach { (k, l) -> Card(Modifier.width(158.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Column(Modifier.padding(12.dp)) { Text("${st.totals[k] ?: 0}", style = MaterialTheme.typography.titleLarge); Text(l, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } } } }
                            listOf("registros" to "Registros", "publicaciones" to "Publicaciones", "comentarios" to "Comentarios", "mensajes" to "Mensajes").forEach { (k, l) -> item { Bars(l, st.series[k].orEmpty()) } }
                            if (st.top.isNotEmpty()) { item { SectionTitle("Más activos (30 días)") }; item { FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { st.top.forEach { t -> androidx.compose.material3.AssistChip({}, { Text("${t.name.pretty()} · ${t.n}") }) } } } } }
                        1 -> { if (reports.isEmpty()) item { EmptyBox("No hay reportes pendientes.") }
                            items(reports, key = { it.id }) { r ->
                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("${r.kind} · ${r.reason} · ${Clock.ago(r.createdAt)}", style = MaterialTheme.typography.labelLarge)
                                    Text("${(r.reporter ?: "?").pretty()} reportó: ${r.info}", style = MaterialTheme.typography.bodyMedium)
                                    if (r.note.isNotBlank()) Text("“${r.note}”", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        val p = Regex("^/p/(\\d+)").find(r.href)?.groupValues?.get(1)?.toIntOrNull(); val u = Regex("^/u/(\\w+)").find(r.href)?.groupValues?.get(1)
                                        if (p != null) OutlinedButton({ nav.post(p) }) { Text("Ver") } else if (u != null) OutlinedButton({ nav.profile(u) }) { Text("Ver") }
                                        Button({ act { repo.closeReport(r.id) } }) { Text("Resolver") }
                                    }
                                } }
                            } }
                        2 -> { item { BanForm { player, hours, reason -> act("Suspendido") { repo.ban(player, hours, reason) } } }
                            if (bans.isEmpty()) item { EmptyBox("No hay jugadores suspendidos.") }
                            items(bans, key = { it.name }) { b -> Row(verticalAlignment = Alignment.CenterVertically) { Avatar(b.name, b.skin, 40.dp); Column(Modifier.padding(start = 10.dp).weight(1f)) { Text(b.name.pretty()); Text("hasta ${SimpleDateFormat("d MMM, h:mm a", Locale("es", "CO")).format(Date(b.until))}${if (b.reason.isNotBlank()) " · ${b.reason}" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; TextButton({ act("Suspensión quitada") { repo.unban(b.name) } }) { Text("Quitar") } } } }
                        3 -> { item { WordForm { w -> act("Palabras agregadas") { repo.addWords(w) } } }
                            item { Text("No se podrá publicar ni enviar textos que las contengan.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            item { FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { words.forEach { w -> InputChip(true, { act("Permitida") { repo.removeWord(w) } }, { Text(w) }, trailingIcon = { Icon(Icons.Filled.Close, null, Modifier.height(16.dp)) }) } } } }
                        4 -> { item { NameForm("Verificar jugador", "Verificar") { n -> act("Verificado") { repo.verify(n) } } }
                            item { Text("El staff ya sale verificado. Aquí verificas a otros jugadores (creadores, líderes de facción…).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            items(verified, key = { it.name }) { v -> Row(verticalAlignment = Alignment.CenterVertically) { Avatar(v.name, v.skin, 38.dp); Text(v.name.pretty(), Modifier.padding(start = 10.dp).weight(1f)); TextButton({ act("Quitado") { repo.unverify(v.name) } }) { Text("Quitar") } } } }
                        else -> { if (log.isEmpty()) item { EmptyBox("Sin registros todavía.") }
                            items(log, key = { it.id }) { l -> Column { Text("${l.staff.pretty()} · ${l.action}${if (l.target.isNotBlank()) " · ${l.target.pretty()}" else ""}", style = MaterialTheme.typography.bodyMedium); if (l.detail.isNotBlank()) Text(l.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(Clock.ago(l.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
                    }
                }
            }
        }
    }
}

@Composable
private fun Bars(title: String, rows: List<com.sampcity.social.data.DayCount>) {
    val max = (rows.maxOfOrNull { it.n } ?: 1).coerceAtLeast(1)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp)) {
            Text("$title · ${rows.sumOf { it.n }} en 14 días", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth().height(60.dp).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
                val days = rows.associate { it.d to it.n }
                val cal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -13) }
                repeat(14) { val k = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time); val n = days[k] ?: 0; cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
                    Box(Modifier.weight(1f).fillMaxHeight((n.toFloat() / max).coerceAtLeast(.04f)).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))) }
            }
        }
    }
}

@Composable
private fun BanForm(onBan: (String, Int, String) -> Unit) {
    var player by remember { mutableStateOf("") }; var hours by remember { mutableStateOf("24") }; var reason by remember { mutableStateOf("") }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Suspender en la red social", style = MaterialTheme.typography.titleSmall)
        Text("No podrá publicar, comentar, reaccionar ni escribir mensajes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(player, { player = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Nombre_Apellido") })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(hours, { if (it.length <= 4 && it.all(Char::isDigit)) hours = it }, Modifier.width(110.dp), singleLine = true, label = { Text("Horas") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(reason, { if (it.length <= 200) reason = it }, Modifier.weight(1f), singleLine = true, label = { Text("Motivo") })
        }
        Button({ onBan(player.trim(), hours.toIntOrNull() ?: 24, reason.trim()); player = ""; reason = "" }, enabled = player.isNotBlank()) { Text("Suspender") }
    } }
}

@Composable
private fun WordForm(onAdd: (String) -> Unit) {
    var w by remember { mutableStateOf("") }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(w, { if (it.length <= 300) w = it }, Modifier.weight(1f), singleLine = true, label = { Text("Palabras (separadas por comas)") })
        Button({ onAdd(w.trim()); w = "" }, enabled = w.isNotBlank()) { Text("Agregar") }
    }
}

@Composable
private fun NameForm(label: String, action: String, onGo: (String) -> Unit) {
    var n by remember { mutableStateOf("") }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(n, { if (it.length <= 24) n = it }, Modifier.weight(1f), singleLine = true, label = { Text("Nombre_Apellido") })
        Button({ onGo(n.trim()); n = "" }, enabled = n.isNotBlank()) { Text(action) }
    }
}
