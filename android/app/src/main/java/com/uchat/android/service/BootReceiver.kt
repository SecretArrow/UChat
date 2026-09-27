package com.uchat.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.uchat.android.UChatApp
import com.uchat.android.core.log.Logs
import com.uchat.android.linux.Proot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Optional reboot recovery (spec #25, finally functional).
 *
 * While "Restore background sessions after reboot" is on, every session the user launches is
 * persisted with autoRestart=1 and removed again the moment it exits or the user closes it. So
 * after BOOT_COMPLETED the rows that remain are exactly the sessions the device killed — and only
 * those are restarted here. Never arbitrary commands: only commands UChat itself launched.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val container = (context.applicationContext as UChatApp).container
        if (!container.paths.isUbuntuInstalled) return
        Logs.app("boot completed — evaluating auto-restart sessions")
        val db = container.database
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val candidates = db.processDao().restorable()
                var restored = 0
                candidates.forEach { meta ->
                    try {
                        Logs.app("auto-restarting session '${meta.label}'")
                        val session =
                            Proot.launchSession(
                                paths = container.paths,
                                abi = container.abi,
                                label = meta.label,
                                innerCommand = meta.command.split(" "),
                                workingDirInUbuntu = meta.workingDirectory,
                                scope = container.appScope,
                            )
                        container.processManager.register(session)
                        // The old row is consumed: a fresh one is written when the session exits
                        // (it never will while running) — instead re-persist the live session.
                        db.processDao().deleteBySessionId(meta.sessionId)
                        db.processDao().insert(meta.copy(sessionId = session.id))
                        restored++
                    } catch (e: Exception) {
                        Logs.app("could not restore '${meta.label}': ${e.message}")
                    }
                }
                if (restored > 0) {
                    // Keep the restored sessions alive now that the app process was woken for them.
                    UChatService.start(context)
                }
            } catch (e: Exception) {
                Logs.app("boot restore failed: ${e.message}")
            }
        }
    }
}
