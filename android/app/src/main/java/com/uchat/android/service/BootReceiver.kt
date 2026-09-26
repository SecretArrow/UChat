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
 * Optional reboot recovery (spec #25). Only services the user explicitly marked "auto restart" are
 * restarted — never arbitrary commands.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val container = (context.applicationContext as UChatApp).container
        Logs.app("boot completed — evaluating auto-restart sessions")
        val db = container.database
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val candidates = db.processDao().autoRestartCandidates()
                candidates
                    .filter { it.autoRestart && it.label.startsWith("dev:") }
                    .forEach { meta ->
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
                    }
            } catch (e: Exception) {
                Logs.app("boot restore failed: ${e.message}")
            }
        }
    }
}
