package com.uchat.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.uchat.android.MainActivity
import com.uchat.android.R

/**
 * Notification channels (spec #26): background processes, downloads, ubuntu installation, errors,
 * updates.
 */
object NotificationHelper {

    const val CHANNEL_PROCESSES = "background_processes"
    const val CHANNEL_DOWNLOADS = "downloads"
    const val CHANNEL_UBUNTU_INSTALL = "ubuntu_install"
    const val CHANNEL_ERRORS = "errors"
    const val CHANNEL_UPDATES = "updates"

    const val NOTIFICATION_ID_PROCESSES = 1

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channels =
            listOf(
                NotificationChannel(
                    CHANNEL_PROCESSES,
                    "Background Processes",
                    NotificationManager.IMPORTANCE_LOW
                ),
                NotificationChannel(
                    CHANNEL_DOWNLOADS,
                    "Downloads",
                    NotificationManager.IMPORTANCE_LOW
                ),
                NotificationChannel(
                    CHANNEL_UBUNTU_INSTALL,
                    "Ubuntu Installation",
                    NotificationManager.IMPORTANCE_LOW
                ),
                NotificationChannel(CHANNEL_ERRORS, "Errors", NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(
                    CHANNEL_UPDATES,
                    "Updates",
                    NotificationManager.IMPORTANCE_DEFAULT
                ),
            )
        channels.forEach { manager.createNotificationChannel(it) }
    }

    /** Persistent foreground notification with live process count + Stop All. */
    fun buildForeground(context: Context, runningCount: Int, details: List<String>): Notification {
        val openIntent =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val stopAll =
            PendingIntent.getService(
                context,
                1,
                Intent(context, UChatService::class.java).setAction(UChatService.ACTION_STOP_ALL),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        val style =
            NotificationCompat.InboxStyle().also { inbox ->
                details.take(4).forEach { inbox.addLine(it) }
            }

        return NotificationCompat.Builder(context, CHANNEL_PROCESSES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("UChat")
            .setContentText("$runningCount background process(es)")
            .setStyle(style)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(0, "Stop All", stopAll)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}
