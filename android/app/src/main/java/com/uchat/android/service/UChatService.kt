package com.uchat.android.service

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.uchat.android.R
import com.uchat.android.UChatApp
import com.uchat.android.core.log.Logs
import com.uchat.android.linux.SessionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Foreground service keeping eligible sessions alive while the user is away (spec #9, #24, #26).
 *
 * Reliability rules (learned from "kok sering session exited" reports):
 * - The service runs whenever any session is RUNNING — the "persistent notification" preference
 *   only chooses between a detailed and a minimal notification, it can no longer disable the
 *   protection itself (a backgrounded app without an FGS is killed by Android within minutes).
 * - On Android 14+ we use the `specialUse` FGS type: `dataSync` carries a 6-hour-per-24h runtime
 *   quota on Android 15 after which the system silently stops the service and every session dies.
 *   [onTimeout] is still handled defensively.
 * - There is intentionally NO remote "Stop all" action on the notification: one accidental tap used
 *   to kill every running session. Sessions are stopped from the in-app Processes screen.
 */
class UChatService : LifecycleService() {

    override fun onCreate() {
        super.onCreate()
        val app = application as UChatApp

        startForegroundCompat(NotificationHelper.buildForeground(this, 0, emptyList()))

        lifecycleScope.launch {
            while (true) {
                val container = app.container
                val settings = container.settingsRepository.settings.first()
                val sessions = container.processManager.all
                val running = sessions.filter { it.state == SessionState.RUNNING }
                if (running.isEmpty()) {
                    Logs.app("UChatService stopping (running=${running.size})")
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    break
                }
                val details =
                    running.take(4).map { "${it.label} — ${getString(R.string.processes_running)}" }
                val notification2 =
                    NotificationHelper.buildForeground(
                        this@UChatService,
                        running.size,
                        details,
                        detailed = settings.persistentNotification,
                    )
                val manager =
                    getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                manager.notify(NotificationHelper.NOTIFICATION_ID_PROCESSES, notification2)
                delay(4000)
            }
        }
        Logs.app("UChatService started")
    }

    private fun startForegroundCompat(notification: android.app.Notification) {
        when {
            Build.VERSION.SDK_INT >= 34 ->
                startForeground(
                    NotificationHelper.NOTIFICATION_ID_PROCESSES,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            Build.VERSION.SDK_INT >= 29 ->
                startForeground(
                    NotificationHelper.NOTIFICATION_ID_PROCESSES,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            else -> startForeground(NotificationHelper.NOTIFICATION_ID_PROCESSES, notification)
        }
    }

    /**
     * Android 15+ may impose a runtime quota on certain FGS types. With `specialUse` this should
     * not trigger, but if the system ever times the service out we fail loudly in the log and step
     * aside cleanly — the restore-on-next-launch machinery relaunches the sessions.
     */
    override fun onTimeout(timeoutId: Int) {
        Logs.app("UChatService onTimeout($timeoutId) — system revoked foreground time")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // Note: ACTION_STOP_ALL was removed on purpose — a notification button that killed every
        // running session caused "all my sessions exited" reports. Nothing remote stops sessions.
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    companion object {
        const val ACTION_START = "com.uchat.android.action.START"

        fun start(context: Context) {
            val intent = Intent(context, UChatService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** Removes the persistent notification when nothing needs to be kept alive anymore. */
        fun stop(context: Context) {
            context.stopService(Intent(context, UChatService::class.java))
        }
    }
}
