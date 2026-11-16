package com.sampcity.social.util

import android.content.Context

class Prefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    /** "system", "dark" o "light" */
    var theme: String get() = p.getString("theme", "system") ?: "system"; set(v) { p.edit().putString("theme", v).apply() }
    /** avisos al instante con la app cerrada (servicio con una notificación fija) */
    var instantAlerts: Boolean get() = p.getBoolean("instantAlerts", true); set(v) { p.edit().putBoolean("instantAlerts", v).apply() }
    var bgAlerts: Boolean get() = p.getBoolean("bgAlerts", true); set(v) { p.edit().putBoolean("bgAlerts", v).apply() }
    var lastNotifId: Int get() = p.getInt("lastNotifId", -1); set(v) { p.edit().putInt("lastNotifId", v).apply() }
    var lastMsgCount: Int get() = p.getInt("lastMsgCount", 0); set(v) { p.edit().putInt("lastMsgCount", v).apply() }
    var recentSearches: List<String>
        get() = (p.getString("recent", "") ?: "").split("\n").filter { it.isNotBlank() }
        set(v) { p.edit().putString("recent", v.take(8).joinToString("\n")).apply() }
    /** historias ya vistas (ids) */
    var seenStories: Set<String>
        get() = p.getStringSet("seenStories", emptySet()) ?: emptySet()
        set(v) { p.edit().putStringSet("seenStories", v.toList().takeLast(400).toSet()).apply() }
    var skippedUpdate: Int get() = p.getInt("skippedUpdate", 0); set(v) { p.edit().putInt("skippedUpdate", v).apply() }
}
