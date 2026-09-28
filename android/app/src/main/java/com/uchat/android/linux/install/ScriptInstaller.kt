package com.uchat.android.linux.install

import android.content.Context
import com.uchat.android.core.fs.UChatPaths
import com.uchat.android.core.log.Logs
import java.io.File

/** Scripts copied from APK assets into the proot bind dir. Public for integrity tests. */
val SCRIPTS =
    listOf(
        "dpkg-recover.sh",
        "install-essentials.sh",
        "install-runtimes.sh",
        "install-node.sh",
        "install-opencode.sh",
        "install-claude.sh",
        "install-bun.sh",
        "install-python.sh",
        "healthcheck.sh",
        "session-run.sh",
    )

/**
 * Copies the bundled Ubuntu installer scripts from APK assets into the app-private scripts
 * directory that proot bind-mounts at /root/.uchat-scripts inside the environment.
 */
object ScriptInstaller {

    fun install(context: Context, paths: UChatPaths) {
        paths.scriptsDir.mkdirs()
        SCRIPTS.forEach { name ->
            try {
                val target = File(paths.scriptsDir, name)
                context.assets.open("ubuntu/$name").use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                target.setExecutable(true, false)
                target.setReadable(true, false)
            } catch (e: Exception) {
                Logs.installer("script install failed for $name: ${e.message}")
            }
        }
        Logs.installer("installer scripts ready (${SCRIPTS.size} files)")
    }
}
