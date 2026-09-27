package com.uchat.android.service

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.uchat.android.UChatApp
import com.uchat.android.R
import com.uchat.android.core.log.Logs
import com.uchat.android.linux.SessionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Foreground service keeping eligible sessions alive while the user is away (spec #9, #24, #26).
 * Uses only Android-approved mechanisms: a dataSync foreground service with a persistent
 * notification — never hidden processes.
 *
 * The service now cleans up after itself: when the last session stops (or the user turns the
 * persistent notification off) it removes its own notification and stops — it no longer lingers
 * forever announcing "0 background process(es)".
 */
class UChatService : LifecycleService() {

    override fun onCreate() {
        super.onCreate()
        val app = application as UChatApp

        val notification =
            NotificationHelper.buildForeground(
                this,
                0,
                listOf(getString(R.string.service_starting)),
            )
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NotificationHelper.NOTIFICATION_ID_PROCESSES,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NotificationHelper.NOTIFICATION_ID_PROCESSES, notification)
        }

        lifecycleScope.launch {
            while (true) {
                val container = app.container
                val settings = container.settingsRepository.settings.first()
                val sessions = container.processManager.all
                val running = sessions.filter { it.state == SessionState.RUNNING }
                val persist = settings.persistentNotification
                if (running.isEmpty() || !persist) {
                    Logs.app(
                        "UChatService stopping (running=${running.size}, persist=$persist)"
                    )
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    break
                }
                val details = running.take(4).map { "${it.label} — ${getString(R.string.processes_running)}" }
                val notification2 =
                    NotificationHelper.buildForeground(this@UChatService, running.size, details)
                val manager =
                    getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                manager.notify(NotificationHelper.NOTIFICATION_ID_PROCESSES, notification2)
                delay(4000)
            }
        }
        Logs.app("UChatService started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP_ALL -> {
                (application as UChatApp).container.processManager.stopAll()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_START -> {
                // no-op: presence keeps the service alive
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    companion object {
        const val ACTION_START = "com.uchat.android.action.START"
        const val ACTION_STOP_ALL = "com.uchat.android.action.STOP_ALL"

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
