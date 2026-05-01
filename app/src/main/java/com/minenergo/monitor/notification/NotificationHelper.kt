package com.minenergo.monitor.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.minenergo.monitor.Config
import com.minenergo.monitor.MainActivity
import com.minenergo.monitor.R

object NotificationHelper {

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(Config.NOTIFICATION_CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                Config.NOTIFICATION_CHANNEL_ID,
                Config.NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Уведомления о новых документах на странице Минэнерго"
                enableLights(true)
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    /** Push о найденных новых документах. */
    fun showDocumentsFound(context: Context, count: Int, summary: String) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        val contentIntent = PendingIntent.getActivity(context, 0, intent, pendingFlags)

        val title = if (count == 1) "Новый документ" else "Новые документы: $count"

        val notification = NotificationCompat.Builder(context, Config.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify(Config.NOTIFICATION_ID_NEW_DOCS, notification)
        } catch (_: SecurityException) {
            // Пользователь не выдал POST_NOTIFICATIONS — молча игнорируем.
        }
    }

    fun cancelAll(context: Context) {
        NotificationManagerCompat.from(context).cancel(Config.NOTIFICATION_ID_NEW_DOCS)
    }
}
