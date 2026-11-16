@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.sampcity.social.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ScrollableTabRow
import com.sampcity.social.SampApp
import com.sampcity.social.data.Post
import com.sampcity.social.ui.LocalNavigator
import com.sampcity.social.ui.LocalOpenDrawer
import com.sampcity.social.ui.components.Avatar
import com.sampcity.social.ui.components.SIcon
import com.sampcity.social.ui.components.Paged
import com.sampcity.social.ui.components.PostFeed
import com.sampcity.social.ui.components.PostHostDialogs
import com.sampcity.social.ui.components.pretty
import com.sampcity.social.ui.components.rememberPostHost
import com.sampcity.social.ui.theme.Rojo
import com.sampcity.social.ui.theme.Rosa
import com.sampcity.social.ui.theme.Russo
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class FeedVM : ViewModel() {
    private val app = SampApp.instance
    private val repo = app.repo
    private var cursor = 0
    var tab by mutableStateOf("feed")
    var stories by mutableStateOf<List<Post>>(emptyList())
    var seen by mutableStateOf(app.prefs.seenStories)

    /** Amigos y a quienes sigo: paginación por «antes del id», sin repetir ni saltar. Las historias van en su franja, no aquí. */
    val friends = Paged<Post>(viewModelScope, { it.id }, { it.size > 12 }, { it.take(12).filter { p -> p.type == "post" } }) { last, _ ->
        val raw = repo.posts(if (last == null) "type=feed&page=0" else "type=feed&before=$cursor")
        cursor = raw.take(12).minOfOrNull { it.id } ?: cursor
        raw
    }
    /** «Para ti»: por interacción y recencia (por páginas) */
    val forYou = Paged<Post>(viewModelScope, { it.id }, { it.size > 12 }, { it.take(12) }) { _, page -> repo.posts("type=foryou&page=$page") }

    init {
        friends.refresh(true); forYou.refresh(true); loadStories()
        viewModelScope.launch { app.bus.collect { if (it == "post-created") { friends.refresh(); forYou.refresh(); loadStories() } else if (it == "story-seen") seen = app.prefs.seenStories } }
    }
    val current get() = if (tab == "feed") friends else forYou
    fun loadStories() { viewModelScope.launch { runCatching { repo.stories() }.onSuccess { stories = it } } }
    fun reloadSeen() { seen = app.prefs.seenStories }
}

@Composable
fun HomeScreen(vm: FeedVM = viewModel()) {
    val nav = LocalNavigator.current
    val openDrawer = LocalOpenDrawer.current
    val me = SampApp.instance.session.me
    val host = rememberPostHost(onUpdate = { p -> vm.friends.update({ it.id == p.id }, p); vm.forYou.update({ it.id == p.id }, p) }, onRemove = { id -> vm.friends.remove { it.id == id }; vm.forYou.remove { it.id == id } })
    val scroll = TopAppBarDefaults.enterAlwaysScrollBehavior()
    var creating by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.reloadSeen() }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.sampcity.social.R.drawable.logo), "SampCity", Modifier.height(30.dp)) },
                navigationIcon = { IconButton(openDrawer) { SIcon("menu_hamburguesa") } },
                actions = { IconButton({ nav.search() }) { SIcon("buscar") } },
                scrollBehavior = scroll,
            )
        },
    ) { pad ->
        PostFeed(vm.current, host, Modifier.padding(pad), empty = if (vm.tab == "feed") "Tus amigos aún no han publicado nada. Agrega amigos o mira «Para ti»." else "Aún no hay publicaciones.") {
            item { StoryStrip(vm, onCreate = { creating = true }) }
            item {
                Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (me != null) Avatar(me.name, me.skin, 40.dp)
                    Box(Modifier.padding(horizontal = 10.dp).weight(1f).clip(RoundedCornerShape(24.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp)).clickable { nav.compose() }.padding(horizontal = 16.dp, vertical = 11.dp)) { Text("¿Qué estás pensando?", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    IconButton({ nav.compose() }) { SIcon("galeria") }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(vm.tab == "feed", { vm.tab = "feed" }, { Text("Amigos") }, Modifier.weight(1f))
                    FilterChip(vm.tab == "foryou", { vm.tab = "foryou" }, { Text("Para ti") }, Modifier.weight(1f))
                }
            }
        }
    }
    PostHostDialogs(host)
    if (creating) StoryCreateDialog(onDismiss = { creating = false }, onDone = { vm.loadStories() })
}

@Composable
private fun StoryStrip(vm: FeedVM, onCreate: () -> Unit) {
    val nav = LocalNavigator.current
    val me = SampApp.instance.session.me
    val byAuthor = vm.stories.groupBy { it.author }
    val authors = byAuthor.keys.sortedBy { if (it == me?.name) 0 else 1 }
    LazyRow(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(vertical = 10.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(Modifier.width(68.dp).clickable(onClick = onCreate), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(62.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { SIcon("mas", size = 30.dp) }
                Text("Crear historia", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
        }
        items(authors, key = { it }) { a ->
            val list = byAuthor[a].orEmpty()
            val unseen = list.any { it.id.toString() !in vm.seen }
            val ring = if (unseen) Modifier.border(BorderStroke(3.dp, Brush.linearGradient(listOf(Rojo, Rosa))), CircleShape) else Modifier.border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            Column(Modifier.width(68.dp).clickable { nav.story(a) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(ring.padding(4.dp)) { Avatar(a, list.first().skin, 54.dp) }
                Text(if (a == me?.name) "Tu historia" else a.pretty().substringBefore(' '), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
