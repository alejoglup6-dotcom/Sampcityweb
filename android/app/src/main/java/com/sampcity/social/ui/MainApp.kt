@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.sampcity.social.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Help
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sampcity.social.AuthState
import com.sampcity.social.BuildConfig
import com.sampcity.social.SampApp
import com.sampcity.social.ui.components.Avatar
import com.sampcity.social.ui.components.LocalSnack
import com.sampcity.social.ui.components.PlayerName
import com.sampcity.social.ui.components.SIcon
import com.sampcity.social.ui.components.pretty
import com.sampcity.social.ui.screens.*
import kotlinx.coroutines.launch

val LocalOpenDrawer = staticCompositionLocalOf<() -> Unit> { {} }

@Composable
fun MainApp(initialRoute: String?) {
    val app = SampApp.instance
    val nav = rememberNavController()
    val navigator = remember { Navigator(nav) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val auth by app.session.state.collectAsStateWithLifecycle()
    val me = (auth as? AuthState.In)?.me
    val counts by app.session.counts.collectAsStateWithLifecycle()
    val back by nav.currentBackStackEntryAsState()
    val route = back?.destination?.route ?: "home"
    val onTab = route == "home" || route == "chats" || route == "notifications" || route == "me" || route == "friends" || route == "market" || route.startsWith("reels")
    val notice by app.session.notice.collectAsStateWithLifecycle()

    LaunchedEffect(initialRoute) { if (initialRoute != null) navigator.open(initialRoute) }
    LaunchedEffect(notice) { notice?.let { snack.showSnackbar(it); app.session.notice.value = null } }
    // un aviso en vivo o la cuenta de pendientes: se actualiza al abrir
    LaunchedEffect(Unit) { app.session.refreshMe() }

    // ¿hay una versión nueva de la app?
    var update by remember { mutableStateOf<com.sampcity.social.data.AppInfo?>(null) }
    LaunchedEffect(Unit) { runCatching { app.repo.appInfo() }.getOrNull()?.let { if (it.available && it.code > BuildConfig.VERSION_CODE && it.code > app.prefs.skippedUpdate) update = it } }
    update?.let { u ->
        AlertDialog(onDismissRequest = { update = null }, title = { Text("Hay una versión nueva") }, text = { Text("SampCity ${u.version} ya está disponible. Descárgala e instálala para tener las últimas mejoras.") },
            confirmButton = { TextButton({ openWeb(ctx, "/descargar-app"); update = null }) { Text("Descargar") } },
            dismissButton = { TextButton({ app.prefs.skippedUpdate = u.code; update = null }) { Text("Ahora no") } })
    }

    CompositionLocalProvider(LocalNavigator provides navigator, LocalSnack provides snack, LocalOpenDrawer provides { scope.launch { drawer.open() } }) {
        ModalNavigationDrawer(
            drawerState = drawer, gesturesEnabled = route == "home",
            drawerContent = { DrawerContent(me, counts.n, { scope.launch { drawer.close() } }, navigator, ctx) },
        ) {
            Scaffold(
                snackbarHost = { SnackbarHost(snack) }, contentWindowInsets = WindowInsets(0),
                bottomBar = { if (onTab) WebTabBar(route, me, counts, freq = me?.freq ?: 0, navigator) },
            ) { inner ->
                val bottom = inner.calculateBottomPadding()
                // la barra de abajo ya cubre el borde del sistema: las pantallas no lo vuelven a sumar
                NavHost(nav, startDestination = "home", modifier = Modifier.padding(bottom = bottom).consumeWindowInsets(androidx.compose.foundation.layout.PaddingValues(bottom = bottom))) {
                    composable("home") { HomeScreen() }
                    composable("reels?start={start}&author={author}", arguments = listOf(navArgument("start") { type = NavType.StringType; nullable = true }, navArgument("author") { type = NavType.StringType; nullable = true })) {
                        ReelsScreen(it.arguments?.getString("start")?.toIntOrNull(), it.arguments?.getString("author"))
                    }
                    composable("chats") { ChatsScreen() }
                    composable("notifications") { NotificationsScreen() }
                    composable("me") { ProfileScreen(me?.name ?: "", isTab = true) }
                    composable("post/{id}", listOf(navArgument("id") { type = NavType.IntType })) { PostScreen(it.arguments!!.getInt("id")) }
                    composable("profile/{name}", listOf(navArgument("name") { type = NavType.StringType })) { ProfileScreen(it.arguments!!.getString("name")!!) }
                    composable("chat/{name}", listOf(navArgument("name") { type = NavType.StringType })) { ChatScreen(ChatTarget.Player(it.arguments!!.getString("name")!!)) }
                    composable("room/{id}", listOf(navArgument("id") { type = NavType.IntType })) { ChatScreen(ChatTarget.Room(it.arguments!!.getInt("id"))) }
                    composable("group/{id}", listOf(navArgument("id") { type = NavType.IntType })) { GroupScreen(it.arguments!!.getInt("id")) }
                    composable("compose?group={group}", listOf(navArgument("group") { type = NavType.StringType; nullable = true })) { ComposeScreen(it.arguments?.getString("group")?.toIntOrNull()) }
                    composable("search?q={q}", listOf(navArgument("q") { type = NavType.StringType; defaultValue = "" })) { SearchScreen(it.arguments?.getString("q").orEmpty()) }
                    composable("friends") { FriendsScreen() }
                    composable("groups") { GroupsScreen() }
                    composable("events") { EventsScreen() }
                    composable("market") { MarketScreen() }
                    composable("saved") { SavedScreen() }
                    composable("content/{type}", listOf(navArgument("type") { type = NavType.StringType })) { ContentScreen(it.arguments!!.getString("type")!!) }
                    composable("story/{author}", listOf(navArgument("author") { type = NavType.StringType })) { StoryScreen(it.arguments!!.getString("author")!!) }
                    composable("staff") { StaffScreen() }
                    composable("top") { TopScreen() }
                    composable("settings") { SettingsScreen() }
                    composable("panel") { PanelScreen() }
                }
            }
        }
    }
}

@Composable
private fun DrawerContent(me: com.sampcity.social.data.Me?, unread: Int, close: () -> Unit, nav: Navigator, ctx: android.content.Context) {
    val scope = rememberCoroutineScope()
    val freq = me?.freq ?: 0
    ModalDrawerSheet {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            if (me != null) Row(Modifier.fillMaxWidth().clickable { close(); nav.tab("me") }.padding(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Avatar(me.name, me.skin, 52.dp)
                Column(Modifier.padding(start = 12.dp)) { PlayerName(me.name, style = MaterialTheme.typography.titleMedium); Text(me.rango, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            HorizontalDivider()
            fun item(icon: String, label: String, badge: Int = 0, action: () -> Unit) = @Composable {
                NavigationDrawerItem(label = { Text(label) }, selected = false, onClick = { close(); action() }, icon = { SIcon(icon) }, badge = if (badge > 0) ({ Badge { Text(badge.toString()) } }) else null, modifier = Modifier.padding(horizontal = 10.dp))
            }
            item("etiquetar", "Amigos", freq) { nav.go("friends") }()
            item("comunidad", "Grupos") { nav.go("groups") }()
            item("calendario", "Eventos") { nav.go("events") }()
            item("marketplace", "Marketplace") { nav.go("market") }()
            item("buscar", "Buscar") { nav.search() }()
            item("guardar", "Guardados") { nav.go("saved") }()
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            item("noticias", "Noticias") { nav.content("news") }()
            item("actualizaciones", "Actualizaciones") { nav.content("update") }()
            item("fotos", "Fotos de la comunidad") { nav.content("photo") }()
            item("faq", "Preguntas frecuentes") { nav.content("faq") }()
            item("reglas", "Reglas") { nav.content("rules") }()
            item("testimonios", "Testimonios") { nav.content("review") }()
            item("staff_equipo", "Equipo (staff)") { nav.go("staff") }()
            item("clasificacion", "Clasificación") { nav.go("top") }()
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            item("dinero", "Tienda") { openWeb(ctx, "/tienda") }()
            item("solicitar_staff", "Solicitar ser staff") { openWeb(ctx, "/solicitar-staff") }()
            item("buscar", "Guía del servidor") { openWeb(ctx, "/guia") }()
            item("contacto", "Contacto") { openWeb(ctx, "/contacto") }()
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            if (me?.canPost == true) item("rango_administrador", "Panel del staff") { nav.go("panel") }()
            item("candado", "Configuración") { nav.go("settings") }()
            item("cerrar", "Cerrar sesión") { scope.launch { SampApp.instance.session.logout() } }()
        }
    }
}

/** Barra de abajo igual que la de la web en el celular: Feed · Reels · Amigos · Mensajes · Marketplace · Avisos · Perfil, con una raya roja arriba en la activa. */
@Composable
private fun WebTabBar(route: String, me: com.sampcity.social.data.Me?, counts: com.sampcity.social.data.NotifCount, freq: Int, nav: Navigator) {
    val c = MaterialTheme.colorScheme
    data class T(val r: String, val icon: String, val badge: Int, val label: String)
    val tabs = listOf(T("home", "inicio", 0, "Inicio"), T("reels", "videos", 0, "Reels"), T("friends", "comunidad", freq, "Solicitudes de amistad"), T("chats", "mensajes", counts.m, "Mensajes"), T("market", "marketplace", 0, "Marketplace"), T("notifications", "campana", counts.n, "Notificaciones"))
    Column(Modifier.background(if (com.sampcity.social.ui.theme.LocalDark.current) androidx.compose.ui.graphics.Color(0xFF120C0F) else androidx.compose.ui.graphics.Color.White)) {
        HorizontalDivider(color = c.outlineVariant)
        Row(Modifier.fillMaxWidth().navigationBarsPadding()) {
            tabs.forEach { t ->
                val sel = route == t.r || (t.r == "reels" && route.startsWith("reels"))
                Box(Modifier.weight(1f).height(60.dp).clickable(role = androidx.compose.ui.semantics.Role.Tab, onClickLabel = t.label) { nav.tab(t.r) }) {
                    Box(Modifier.fillMaxWidth().height(3.dp).background(if (sel) androidx.compose.ui.graphics.Color(0xFFE8392F) else androidx.compose.ui.graphics.Color.Transparent))
                    Box(Modifier.align(Alignment.Center)) { SIcon(t.icon, size = 27.dp) }
                    if (t.badge > 0) Box(Modifier.align(Alignment.TopCenter).padding(top = 6.dp, start = 22.dp).defaultMinSize(minWidth = 18.dp, minHeight = 18.dp).background(androidx.compose.ui.graphics.Color(0xFFE8392F), androidx.compose.foundation.shape.CircleShape).border(2.dp, c.surface, androidx.compose.foundation.shape.CircleShape).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                        Text(if (t.badge > 99) "99+" else t.badge.toString(), color = androidx.compose.ui.graphics.Color.White, fontSize = 10.sp, lineHeight = 14.sp)
                    }
                }
            }
            Box(Modifier.weight(1f).height(60.dp).clickable(role = androidx.compose.ui.semantics.Role.Tab, onClickLabel = "Mi perfil") { nav.tab("me") }) {
                Box(Modifier.fillMaxWidth().height(3.dp).background(if (route == "me") androidx.compose.ui.graphics.Color(0xFFE8392F) else androidx.compose.ui.graphics.Color.Transparent))
                if (me != null) Box(Modifier.align(Alignment.Center)) { Avatar(me.name, me.skin, 30.dp) }
            }
        }
    }
}
