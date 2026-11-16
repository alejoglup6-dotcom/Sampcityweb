package com.sampcity.social.live

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sampcity.social.MainActivity
import com.sampcity.social.R
import com.sampcity.social.SampApp

/** Muestra las notificaciones del teléfono (mensajes y avisos). Al tocarlas se abre la app en el lugar correcto. */
object Notifier {
    fun allowed(ctx: Context) = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(ctx: Context, id: Int, channel: String, title: String, text: String, route: String, group: String? = null) {
        if (!allowed(ctx)) return
        val pi = PendingIntent.getActivity(ctx, id, Intent(ctx, MainActivity::class.java).putExtra("route", route).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val b = NotificationCompat.Builder(ctx, channel).setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text)).setAutoCancel(true).setColor(0xFFE8392F.toInt()).setContentIntent(pi)
            .setPriority(if (channel == SampApp.CH_CHAT) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT).setCategory(if (channel == SampApp.CH_CHAT) NotificationCompat.CATEGORY_MESSAGE else NotificationCompat.CATEGORY_SOCIAL)
        if (group != null) b.setGroup(group)
        runCatching { NotificationManagerCompat.from(ctx).notify(id, b.build()) }
    }

    fun clearAll(ctx: Context) { runCatching { NotificationManagerCompat.from(ctx).cancelAll() } }
}
