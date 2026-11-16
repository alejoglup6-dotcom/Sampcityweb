@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.sampcity.social.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.sampcity.social.ui.components.pretty
import com.sampcity.social.ui.screens.*
import kotlinx.coroutines.launch

val LocalOpenDrawer = staticCompositionLocalOf<() -> Unit> { {} }

private data class Tab(val route: String, val label: String, val on: ImageVector, val off: ImageVector)
private val TABS = listOf(
    Tab("home", "Inicio", Icons.Filled.Home, Icons.Outlined.Home),
    Tab("reels", "Reels", Icons.Filled.PlayCircle, Icons.Outlined.PlayCircleOutline),
    Tab("chats", "Mensajes", Icons.Filled.Chat, Icons.Outlined.Chat),
    Tab("notifications", "Avisos", Icons.Filled.Notifications, Icons.Outlined.Notifications),
)

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
    val onTab = route == "home" || route == "chats" || route == "notifications" || route == "me" || route.startsWith("reels")
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
                bottomBar = {
                    if (onTab) NavigationBar {
                        TABS.forEach { t ->
                            val sel = route == t.route || (t.route == "reels" && route.startsWith("reels"))
                            val badge = when (t.route) { "chats" -> counts.m; "notifications" -> counts.n; else -> 0 }
                            NavigationBarItem(selected = sel, onClick = { navigator.tab(t.route) }, label = { Text(t.label) }, icon = {
                                BadgedBox(badge = { if (badge > 0) Badge { Text(if (badge > 99) "99+" else badge.toString()) } }) { Icon(if (sel) t.on else t.off, t.label) }
                            })
                        }
                        NavigationBarItem(selected = route == "me", onClick = { navigator.tab("me") }, label = { Text("Perfil") }, icon = { if (me != null) Avatar(me.name, me.skin, 26.dp) })
                    }
                },
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
            fun item(icon: ImageVector, label: String, badge: Int = 0, action: () -> Unit) = @Composable {
                NavigationDrawerItem(label = { Text(label) }, selected = false, onClick = { close(); action() }, icon = { Icon(icon, null) }, badge = if (badge > 0) ({ Badge { Text(badge.toString()) } }) else null, modifier = Modifier.padding(horizontal = 10.dp))
            }
            item(Icons.Outlined.People, "Amigos", freq) { nav.go("friends") }()
            item(Icons.Outlined.Group, "Grupos") { nav.go("groups") }()
            item(Icons.Outlined.Event, "Eventos") { nav.go("events") }()
            item(Icons.Outlined.Storefront, "Marketplace") { nav.go("market") }()
            item(Icons.Outlined.Search, "Buscar") { nav.search() }()
            item(Icons.Outlined.Bookmark, "Guardados") { nav.go("saved") }()
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            item(Icons.Outlined.Campaign, "Noticias") { nav.content("news") }()
            item(Icons.Outlined.SystemUpdate, "Actualizaciones") { nav.content("update") }()
            item(Icons.Outlined.PhotoLibrary, "Fotos de la comunidad") { nav.content("photo") }()
            item(Icons.Outlined.Help, "Preguntas frecuentes") { nav.content("faq") }()
            item(Icons.Outlined.Gavel, "Reglas") { nav.content("rules") }()
            item(Icons.Outlined.Star, "Testimonios") { nav.content("review") }()
            item(Icons.Outlined.Shield, "Equipo (staff)") { nav.go("staff") }()
            item(Icons.Outlined.EmojiEvents, "Clasificación") { nav.go("top") }()
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            item(Icons.Outlined.ShoppingBag, "Tienda") { openWeb(ctx, "/tienda") }()
            item(Icons.Outlined.Work, "Solicitar ser staff") { openWeb(ctx, "/solicitar-staff") }()
            item(Icons.Outlined.MenuBook, "Guía del servidor") { openWeb(ctx, "/guia") }()
            item(Icons.Outlined.Mail, "Contacto") { openWeb(ctx, "/contacto") }()
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            if (me?.canPost == true) item(Icons.Outlined.AdminPanelSettings, "Panel del staff") { nav.go("panel") }()
            item(Icons.Outlined.Settings, "Configuración") { nav.go("settings") }()
            item(Icons.Outlined.Logout, "Cerrar sesión") { scope.launch { SampApp.instance.session.logout() } }()
        }
    }
}
