package com.sampcity.social.live

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sampcity.social.MainActivity
import com.sampcity.social.R
import com.sampcity.social.SampApp
import com.sampcity.social.data.Notif
import java.util.concurrent.TimeUnit

/** Texto de cada tipo de aviso (igual que en la web). */
object NotifText {
    private val verbs = mapOf(
        "like" to "reaccionó a tu publicación", "like_c" to "reaccionó a tu comentario", "comment" to "comentó tu publicación", "reply" to "respondió en una publicación",
        "mention" to "te mencionó", "mention_c" to "te mencionó en un comentario", "tag" to "te etiquetó", "friend_req" to "te envió una solicitud de amistad",
        "friend_acc" to "aceptó tu solicitud de amistad", "follow" to "empezó a seguirte", "share" to "compartió tu publicación",
        "grp_req" to "quiere unirse a tu grupo", "grp_ok" to "te aceptó en el grupo",
    )
    fun name(n: String?) = (n ?: "Alguien").replace('_', ' ')
    fun line(n: Notif): String = when (n.type) {
        "news", "update" -> n.body.ifBlank { "Hay una publicación nueva" }
        "contact" -> "Nuevo mensaje en Contacto"
        "vac_app" -> "Nueva solicitud en una vacante"
        else -> verbs[n.type]?.let { "${name(n.actor)} $it" } ?: "Tienes una notificación nueva"
    }
    /** a dónde lleva un aviso: ruta de la app */
    fun route(n: Notif): String = when {
        n.postId != null -> "post/${n.postId}"
        n.type == "friend_req" || n.type == "friend_acc" -> "friends"
        n.type == "follow" && n.actor != null -> "profile/${n.actor}"
        n.type.startsWith("grp") -> "groups"
        else -> "notifications"
    }
}

/**
 * Con la app cerrada revisa cada ~15 min si hay avisos o mensajes nuevos y los muestra como notificaciones del teléfono.
 * (Android no deja consultar más seguido; notificaciones al instante necesitarían Firebase Cloud Messaging.)
 */
class NotifWorker(private val ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = SampApp.instance
        if (!app.api.hasSession || app.live.foreground) return Result.success()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
        return try {
            val unread = app.repo.notifications(null, true)
            val last = app.prefs.lastNotifId
            val fresh = if (last < 0) emptyList() else unread.filter { it.id > last }.sortedBy { it.id }
            unread.maxOfOrNull { it.id }?.let { if (it > last) app.prefs.lastNotifId = it }
            val nm = NotificationManagerCompat.from(ctx)
            for (n in fresh.takeLast(5)) nm.notify(1000 + n.id, build(NotifText.line(n), NotifText.route(n), SampApp.CH_SOCIAL, n.id))
            val msgs = app.repo.notifCount().m
            if (msgs > app.prefs.lastMsgCount) nm.notify(9, build(if (msgs == 1) "Tienes un mensaje nuevo" else "Tienes $msgs mensajes sin leer", "chats", SampApp.CH_CHAT, 9))
            app.prefs.lastMsgCount = msgs
            Result.success()
        } catch (e: Exception) { Result.retry() }
    }

    private fun build(text: String, route: String, channel: String, id: Int) = NotificationCompat.Builder(ctx, channel)
        .setSmallIcon(R.drawable.ic_notification).setContentTitle("SampCity").setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setAutoCancel(true).setColor(0xFFE8392F.toInt())
        .setContentIntent(PendingIntent.getActivity(ctx, id, Intent(ctx, MainActivity::class.java).putExtra("route", route).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        .build()

    companion object {
        private const val NAME = "sampcity-alerts"
        fun schedule(ctx: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(ctx)
            if (!enabled) { wm.cancelUniqueWork(NAME); return }
            wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<NotifWorker>(15, TimeUnit.MINUTES).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
        }
        fun cancelAll(ctx: Context) { WorkManager.getInstance(ctx).cancelUniqueWork(NAME); ctx.getSystemService(NotificationManager::class.java).cancelAll() }
    }
}
