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

    /**
     * Persistent foreground notification.
     *
     * Deliberately has NO destructive actions: the old "Stop all" button caused accidental taps
     * that killed every running session (reported as "kok sering session exited"). Tap opens the
     * app; sessions are stopped from the in-app Processes screen with explicit confirmation-less
     * but deliberate buttons.
     *
     * [detailed] = the "persistent notification" preference: on → live process list, off → a
     * minimal one-liner (the foreground service itself always runs while sessions are alive).
     */
    fun buildForeground(
        context: Context,
        runningCount: Int,
        details: List<String>,
        detailed: Boolean = true,
    ): Notification {
        val openIntent =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        val builder =
            NotificationCompat.Builder(context, CHANNEL_PROCESSES)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(
                    context.getString(R.string.processes_service_notification, runningCount)
                )
                .setOngoing(true)
                .setContentIntent(openIntent)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)

        if (detailed) {
            val style =
                NotificationCompat.InboxStyle().also { inbox ->
                    details.take(4).forEach { inbox.addLine(it) }
                }
            builder.setStyle(style)
        }
        return builder.build()
    }
}
