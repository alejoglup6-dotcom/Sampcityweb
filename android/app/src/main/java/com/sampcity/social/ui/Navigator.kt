package com.sampcity.social.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavHostController
import com.sampcity.social.SampApp
import java.net.URLEncoder

private fun e(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

/** Rutas de la app y atajos para navegar (se obtiene con LocalNavigator.current). */
class Navigator(val c: NavHostController) {
    fun back() { c.popBackStack() }
    fun go(route: String) { c.navigate(route) { launchSingleTop = true } }
    fun post(id: Int) = go("post/$id")
    fun profile(name: String) = go("profile/${e(name)}")
    fun chat(name: String) = go("chat/${e(name)}")
    fun room(id: Int) = go("room/$id")
    fun group(id: Int) = go("group/$id")
    fun compose(group: Int? = null) = go("compose" + (group?.let { "?group=$it" } ?: ""))
    fun search(q: String = "") = go("search" + if (q.isNotBlank()) "?q=${e(q)}" else "")
    fun content(type: String) = go("content/$type")
    fun reels(start: Int? = null, author: String? = null) = go("reels" + listOfNotNull(start?.let { "start=$it" }, author?.let { "author=${e(it)}" }).let { if (it.isEmpty()) "" else "?" + it.joinToString("&") })
    fun story(author: String) = go("story/${e(author)}")
    fun tab(route: String) { c.navigate(route) { popUpTo("home") { saveState = true }; launchSingleTop = true; restoreState = true } }
    /** Abre una ruta de la app (las que mandan los avisos y los enlaces) */
    fun open(route: String) {
        when {
            route == "chats" -> tab("chats"); route == "notifications" -> tab("notifications"); route == "home" -> tab("home")
            else -> go(route)
        }
    }
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("sin Navigator") }

/** Abre una página de la web (tienda, vacantes, guía…) en una pestaña del navegador dentro de la app. */
fun openWeb(ctx: Context, path: String) {
    val url = if (path.startsWith("http")) path else SampApp.instance.api.baseUrl + path
    runCatching { CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(ctx, Uri.parse(url)) }
        .onFailure { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** De un enlace de la web (https://dominio/p/12, /u/Nombre…) a la ruta de la app, o null */
fun routeFromUrl(u: Uri): String? {
    val seg = u.pathSegments
    return when (seg.firstOrNull()) {
        "p" -> seg.getOrNull(1)?.toIntOrNull()?.let { "post/$it" }
        "u" -> seg.getOrNull(1)?.let { "profile/${e(it)}" }
        "mensajes" -> u.getQueryParameter("u")?.let { "chat/${e(it)}" } ?: u.getQueryParameter("g")?.let { "room/$it" } ?: "chats"
        "notificaciones" -> "notifications"
        else -> null
    }
}
