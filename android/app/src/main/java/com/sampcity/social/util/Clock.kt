package com.sampcity.social.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * El servidor manda las fechas en SU hora local, sin zona. Con /api/info (campo `now`) se calcula cuánto se desfasa
 * respecto a este teléfono, así «hace 5 minutos» es correcto aunque el hosting esté en otra zona.
 */
object Clock {
    @Volatile var offsetMs: Long = 0
    private val fmt get() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun sync(serverNow: String) { parse(serverNow, 0)?.let { offsetMs = System.currentTimeMillis() - it } }

    private fun parse(s: String, off: Long): Long? = runCatching { fmt.parse(s.take(19).replace('T', ' '))!!.time + off }.getOrNull()

    /** Instante real (ms) de una fecha del servidor, o null */
    fun time(s: String?): Long? = if (s.isNullOrBlank()) null else parse(s, offsetMs)

    fun ago(s: String?): String {
        val t = time(s) ?: return ""
        val m = (System.currentTimeMillis() - t) / 60000.0
        if (m < 1) return "ahora"
        val (n, u) = when { m < 60 -> m to "min"; m < 1440 -> m / 60 to "h"; m < 43200 -> m / 1440 to "d"; else -> m / 43200 to "mes" }
        return if (u == "mes") "hace ${n.toInt()} ${if (n.toInt() > 1) "meses" else "mes"}" else "hace ${n.toInt()} $u"
    }

    fun clock(s: String?): String = time(s)?.let { SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(it)) } ?: ""
    fun day(s: String?): String = time(s)?.let { SimpleDateFormat("d 'de' MMM yyyy", Locale("es", "CO")).format(Date(it)) } ?: ""
    fun dayKey(s: String?): String = time(s)?.let { SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(it)) } ?: ""
    fun dayTime(s: String?): String = time(s)?.let { SimpleDateFormat("EEE d 'de' MMM, h:mm a", Locale("es", "CO")).format(Date(it)) } ?: ""

    /** Una fecha del servidor de «starts» de un evento (hora local del servidor) */
    fun eventDay(s: String?): String = dayTime(s)
}

fun mmss(sec: Int): String { val s = sec.coerceAtLeast(0); return "${s / 60}:${(s % 60).toString().padStart(2, '0')}" }

fun count(n: Int): String = when { n < 1000 -> n.toString(); n < 10000 -> "%.1f".format(Locale.US, n / 1000.0).removeSuffix(".0") + " mil"; n < 1000000 -> "${n / 1000} mil"; else -> "%.1f".format(Locale.US, n / 1e6).removeSuffix(".0") + " M" }

fun money(n: Long): String = "$" + "%,d".format(Locale("es", "CO"), n)

fun hoursPlayed(sec: Int): String = "${sec / 3600} h"
