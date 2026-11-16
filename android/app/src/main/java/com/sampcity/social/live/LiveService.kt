package com.sampcity.social.live

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.sampcity.social.MainActivity
import com.sampcity.social.R
import com.sampcity.social.SampApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Mantiene abierta la conexión en vivo con el servidor aunque la app esté cerrada, para que los mensajes y avisos
 * lleguen al instante como notificaciones. Android exige mostrar una notificación fija mientras está activo (se puede ocultar
 * desde los ajustes de notificaciones del teléfono, categoría «Conexión en vivo»).
 */
class LiveService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val seenMsg = HashMap<String, Int>()   // conversación → último mensaje ya avisado

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = SampApp.instance
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n: Notification = NotificationCompat.Builder(this, SampApp.CH_SERVICE).setSmallIcon(R.drawable.ic_notification).setContentTitle("SampCity").setContentText("Conectado para avisarte al instante")
            .setOngoing(true).setSilent(true).setPriority(NotificationCompat.PRIORITY_MIN).setCategory(NotificationCompat.CATEGORY_SERVICE).setContentIntent(open).setColor(0xFFE8392F.toInt()).build()
        try { ServiceCompat.startForeground(this, 1, n, if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0) } catch (e: Exception) { stopSelf(); return START_NOT_STICKY }
        app.live.keepAlive(true)
        if (job?.isActive != true) job = scope.launch { app.live.events.collect { ev -> runCatching { handle(ev) } } }
        return START_STICKY
    }

    private suspend fun handle(ev: LiveEvent) {
        val app = SampApp.instance
        if (app.live.foreground) return                // con la app a la vista ya se ve dentro de ella
        when (ev.type) {
            "msg" -> {
                val from = ev.data["from"]?.jsonPrimitive?.contentOrNull; val room = ev.data["room"]?.jsonPrimitive?.intOrNull
                if (room != null) app.repo.rooms().firstOrNull { it.id == room && !it.mine && it.unread > 0 }?.let { r ->
                    if ((seenMsg["r$room"] ?: 0) < r.mid) { seenMsg["r$room"] = r.mid; Notifier.show(this, 5000 + room, SampApp.CH_CHAT, r.name, (if (r.from.isNotBlank()) r.from.replace('_', ' ').substringBefore(' ') + ": " else "") + r.body.ifBlank { "Mensaje nuevo" }, "room/$room", "chats") }
                } else if (from != null) app.repo.chats().firstOrNull { it.name == from && !it.mine && it.unread > 0 }?.let { c ->
                    if ((seenMsg[from] ?: 0) < c.mid) { seenMsg[from] = c.mid; Notifier.show(this, 3000 + (from.hashCode() and 0xFFFF), SampApp.CH_CHAT, from.replace('_', ' '), c.body.ifBlank { "Mensaje nuevo" }, "chat/$from", "chats") }
                }
            }
            "notif" -> {
                val last = app.prefs.lastNotifId
                val unread = app.repo.notifications(null, true)
                unread.maxOfOrNull { it.id }?.let { if (it > last) app.prefs.lastNotifId = it }
                if (last >= 0) for (n in unread.filter { it.id > last }.sortedBy { it.id }.takeLast(5)) Notifier.show(this, 1000 + n.id, SampApp.CH_SOCIAL, "SampCity", NotifText.line(n), NotifText.route(n), "social")
            }
        }
    }

    override fun onDestroy() { SampApp.instance.live.keepAlive(false); scope.cancel(); super.onDestroy() }

    companion object {
        /** Arranca o detiene el servicio según los ajustes y si hay sesión. Seguro de llamar desde cualquier parte. */
        fun sync(ctx: Context) {
            val app = SampApp.instance
            val want = app.prefs.instantAlerts && app.api.hasSession
            val i = Intent(ctx, LiveService::class.java)
            if (want) runCatching { ContextCompat.startForegroundService(ctx, i) } else ctx.stopService(i)
        }
    }
}

class BootReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) { runCatching { LiveService.sync(ctx.applicationContext) } }
}
