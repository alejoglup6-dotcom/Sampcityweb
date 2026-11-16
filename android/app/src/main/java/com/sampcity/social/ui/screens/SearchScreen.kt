@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.sampcity.social.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.sampcity.social.SampApp
import com.sampcity.social.data.MarketItem
import com.sampcity.social.data.Post
import com.sampcity.social.data.SearchResult
import com.sampcity.social.data.Trend
import com.sampcity.social.ui.LocalNavigator
import com.sampcity.social.ui.components.Avatar
import com.sampcity.social.ui.components.EmptyBox
import com.sampcity.social.ui.components.LoadingBox
import com.sampcity.social.ui.components.PlayerName
import com.sampcity.social.ui.components.PostCard
import com.sampcity.social.ui.components.PostHostDialogs
import com.sampcity.social.ui.components.SectionTitle
import com.sampcity.social.ui.components.rememberPostHost
import com.sampcity.social.ui.components.userMessage

@Composable
fun SearchScreen(initial: String) {
    val nav = LocalNavigator.current
    val app = SampApp.instance
    var input by remember { mutableStateOf(initial) }
    var query by remember { mutableStateOf(initial) }
    var tab by remember { mutableStateOf(0) }
    var result by remember { mutableStateOf<SearchResult?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var trends by remember { mutableStateOf<List<Trend>>(emptyList()) }
    var recent by remember { mutableStateOf(app.prefs.recentSearches) }
    var posts by remember { mutableStateOf<List<Post>>(emptyList()) }
    val host = rememberPostHost(onUpdate = { p -> posts = posts.map { if (it.id == p.id) p else it } }, onRemove = { id -> posts = posts.filter { it.id != id } })
    val focus = remember { FocusRequester() }
    val tabs = listOf("" to "Todo", "people" to "Personas", "posts" to "Publicaciones", "market" to "Anuncios")

    LaunchedEffect(Unit) { trends = runCatching { app.repo.trends() }.getOrDefault(emptyList()); if (initial.isBlank()) runCatching { focus.requestFocus() } }
    LaunchedEffect(query, tab) {
        if (query.trim().length < 2) { result = null; return@LaunchedEffect }
        loading = true; error = null
        try { val r = app.repo.search(query.trim(), tabs[tab].first); result = r; posts = r.posts } catch (e: Exception) { error = e.userMessage() }
        loading = false
    }
    fun go(q: String) { input = q; query = q; if (q.trim().length >= 2) { recent = (listOf(q.trim()) + recent.filter { it != q.trim() }).take(8); app.prefs.recentSearches = recent } }

    Scaffold(topBar = {
        Row(Modifier.padding(top = 36.dp, start = 4.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(nav::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
            OutlinedTextField(input, { input = it }, Modifier.weight(1f).focusRequester(focus), singleLine = true, placeholder = { Text("Buscar en SampCity") }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { go(input) }),
                trailingIcon = { if (input.isNotEmpty()) IconButton({ input = ""; query = "" }) { Icon(Icons.Filled.Close, "Borrar") } })
        }
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (query.trim().length < 2) LazyColumn(Modifier.fillMaxSize()) {
                if (trends.isNotEmpty()) item { Column(Modifier.padding(16.dp)) { Text("🔥 Tendencias", style = MaterialTheme.typography.titleMedium); FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { trends.forEach { t -> AssistChip({ go("#" + t.tag) }, { Text("#${t.tag}  ${t.n}") }) } } } }
                if (recent.isNotEmpty()) {
                    item { Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) { Text("Búsquedas recientes", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); TextButton({ recent = emptyList(); app.prefs.recentSearches = emptyList() }) { Text("Borrar") } } }
                    items(recent, key = { it }) { r -> Row(Modifier.fillMaxWidth().clickable { go(r) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.History, null); Text("  $r") } }
                } else item { EmptyBox("Escribe al menos 2 letras. Busca jugadores, publicaciones, #etiquetas y anuncios.") }
            } else {
                TabRow(tab) { tabs.forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t.second, maxLines = 1) }) } }
                val r = result
                when {
                    loading && r == null -> LoadingBox()
                    error != null -> EmptyBox(error!!)
                    r == null -> {}
                    r.people.isEmpty() && posts.isEmpty() && r.market.isEmpty() -> EmptyBox("No encontramos nada con esa búsqueda.")
                    else -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (r.people.isNotEmpty()) { item { SectionTitle("Personas") }; items(r.people, key = { "p" + it.name }) { p -> Row(Modifier.fillMaxWidth().clickable { nav.profile(p.name) }.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { Avatar(p.name, p.skin, 44.dp, online = p.connected == 1); Column(Modifier.padding(start = 12.dp)) { PlayerName(p.name); Text("Nivel ${p.level}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } } }
                        if (r.market.isNotEmpty()) { item { SectionTitle("Anuncios") }; items(r.market, key = { "m" + it.id }) { m -> Column(Modifier.padding(horizontal = 12.dp)) { MarketCard(m) { nav.go("market") } } } }
                        if (posts.isNotEmpty()) { item { SectionTitle("Publicaciones") }; items(posts, key = { "s" + it.id }) { p -> PostCard(p, host) } }
                    }
                }
            }
        }
    }
    PostHostDialogs(host)
}
