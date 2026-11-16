@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.sampcity.social.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sampcity.social.SampApp
import com.sampcity.social.data.Post
import com.sampcity.social.data.StaffMember
import com.sampcity.social.data.TopEntry
import com.sampcity.social.ui.LocalNavigator
import com.sampcity.social.ui.components.Avatar
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
import com.sampcity.social.util.Clock
import com.sampcity.social.util.hoursPlayed
import kotlinx.coroutines.launch

val RANGOS = listOf("Ciudadano", "Soporte", "Ayudante", "Moderador", "Moderador Global", "Administrador", "Encargado de Staff", "Desarrollador", "Co-Fundador", "Fundador")

class ListVM(private val query: String) : ViewModel() {
    val posts = Paged<Post>(viewModelScope, { it.id }, { it.size > 12 }, { it.take(12) }) { _, page -> SampApp.instance.repo.posts("$query&page=$page") }
    init { posts.refresh(true) }
}
class ListVMFactory(private val q: String) : ViewModelProvider.Factory { @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = ListVM(q) as T }

/** Lista simple de publicaciones con una barra superior (guardados, noticias, FAQ, reglas…). */
@Composable
private fun PostsPage(title: String, query: String, empty: String, fab: (@Composable () -> Unit)? = null, onReload: Int = 0) {
    val vm: ListVM = viewModel(key = "list$query", factory = ListVMFactory(query))
    val nav = LocalNavigator.current
    val host = rememberPostHost(onUpdate = { p -> vm.posts.update({ it.id == p.id }, p) }, onRemove = { id -> vm.posts.remove { it.id == id } })
    LaunchedEffect(onReload) { if (onReload > 0) vm.posts.refresh() }
    Scaffold(topBar = { TopAppBar(title = { Text(title) }, navigationIcon = { IconButton(nav::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } }) }, floatingActionButton = { fab?.invoke() }) { pad ->
        PostFeed(vm.posts, host, Modifier.padding(pad), empty = empty)
    }
    PostHostDialogs(host)
}

@Composable fun SavedScreen() = PostsPage("Guardados", "saved=1", "Aún no has guardado publicaciones.")

private val CONTENT = mapOf(
    "news" to "Noticias", "update" to "Actualizaciones", "photo" to "Fotos de la comunidad", "faq" to "Preguntas frecuentes", "rules" to "Reglas", "review" to "Testimonios",
)

@Composable
fun ContentScreen(type: String) {
    val me = SampApp.instance.session.me
    val scope = rememberCoroutineScope(); val snack = LocalSnack.current
    var creating by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }
    PostsPage(CONTENT[type] ?: "Contenido", "type=$type", "Aún no hay publicaciones.", fab = if (me?.canPost == true) ({ FloatingActionButton({ creating = true }) { Icon(Icons.Filled.Add, "Nueva") } }) else null, onReload = reload)
    if (creating) {
        var title by remember { mutableStateOf("") }; var body by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { creating = false }, title = { Text("Nueva publicación (${CONTENT[type]})") }, text = {
            Column { OutlinedTextField(title, { if (it.length <= 120) title = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Título") })
                OutlinedTextField(body, { if (it.length <= 4000) body = it }, Modifier.fillMaxWidth().padding(top = 8.dp), minLines = 5, label = { Text(if (type == "photo") "Enlace de la foto (https://…)" else "Texto") }) }
        }, confirmButton = { TextButton({ scope.launch { runCatching { SampApp.instance.repo.createStaffPost(type, title.trim(), body.trim()) }.onSuccess { creating = false; reload++ }.onFailure { snack.showSnackbar(it.userMessage()) } } }, enabled = title.isNotBlank() && body.isNotBlank()) { Text("Publicar") } }, dismissButton = { TextButton({ creating = false }) { Text("Cancelar") } })
    }
}

@Composable
fun StaffScreen() {
    val nav = LocalNavigator.current
    var list by remember { mutableStateOf<List<StaffMember>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { try { list = SampApp.instance.repo.staff() } catch (e: Exception) { error = e.userMessage() } }
    Scaffold(topBar = { TopAppBar(title = { Text("Equipo") }, navigationIcon = { IconButton(nav::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } }) }) { pad ->
        val l = list
        when {
            error != null -> ErrorBox(error!!, null, Modifier.padding(pad).fillMaxSize())
            l == null -> LoadingBox(Modifier.padding(pad).fillMaxSize())
            else -> LazyColumn(Modifier.padding(pad).fillMaxSize()) {
                l.groupBy { it.level }.toSortedMap(compareByDescending { it }).forEach { (lv, members) ->
                    item { SectionTitle(RANGOS.getOrElse(lv) { "Staff" }) }
                    items(members, key = { it.name }) { m ->
                        Row(Modifier.fillMaxWidth().clickable { nav.profile(m.name) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(m.name, m.skin, 44.dp, online = m.connected == 1)
                            Column(Modifier.padding(start = 12.dp)) { PlayerName(m.name); Text(if (m.connected == 1) "En línea" else Clock.ago(m.lastConnection).let { if (it.isBlank()) "" else "Última vez $it" }, style = MaterialTheme.typography.bodySmall, color = if (m.connected == 1) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TopScreen() {
    val nav = LocalNavigator.current
    var by by remember { mutableStateOf("level") }
    var list by remember { mutableStateOf<List<TopEntry>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(by) { list = null; try { list = SampApp.instance.repo.top(by) } catch (e: Exception) { error = e.userMessage() } }
    Scaffold(topBar = { TopAppBar(title = { Text("Clasificación") }, navigationIcon = { IconButton(nav::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                listOf("level" to "Nivel", "time" to "Horas jugadas", "rep" to "Reputación").forEach { (k, l) -> FilterChip(by == k, { by = k }, { Text(l) }) }
            }
            val l = list
            when {
                error != null -> ErrorBox(error!!, null)
                l == null -> LoadingBox()
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(l.withIndex().toList(), key = { it.value.name }) { (i, t) ->
                        Row(Modifier.fillMaxWidth().clickable { nav.profile(t.name) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}", Modifier.padding(end = 12.dp), style = MaterialTheme.typography.titleMedium, color = if (i < 3) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
                            Avatar(t.name, t.skin, 42.dp); Column(Modifier.padding(start = 12.dp).weight(1f)) { PlayerName(t.name); Text("Nivel ${t.level} · Rep. ${t.rep} · ${hoursPlayed(t.timePlaying)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Text(when (by) { "time" -> hoursPlayed(t.timePlaying); "rep" -> "${t.rep}"; else -> "${t.level}" }, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}
