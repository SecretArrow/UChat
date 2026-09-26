package com.uchat.android.di

import android.content.Context
import com.uchat.android.core.arch.DeviceAbi
import com.uchat.android.core.fs.UChatPaths
import com.uchat.android.core.log.Logs
import com.uchat.android.core.settings.SettingsRepository
import com.uchat.android.data.db.AppDatabase
import com.uchat.android.data.registry.RegistryLoader
import com.uchat.android.data.repo.ProjectsRepository
import com.uchat.android.data.repo.SecretsRepository
import com.uchat.android.linux.ProcessManager
import com.uchat.android.linux.exec.Shell
import com.uchat.android.linux.install.UbuntuInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * App-scoped dependency container. Everything that must survive Activity recreation (installer,
 * sessions, database) hangs off this object.
 */
class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val paths: UChatPaths = UChatPaths(appContext).also { it.ensureDirs() }
    val abi: DeviceAbi = DeviceAbi.current()

    val registryLoader = RegistryLoader(appContext)
    val assetRegistry by lazy { registryLoader.loadAssets() }
    val toolRegistry by lazy { registryLoader.loadTools() }

    val database: AppDatabase = AppDatabase.build(appContext)
    val projectsRepository = ProjectsRepository(database.projectDao())
    val secretsRepository = SecretsRepository(appContext)
    val settingsRepository = SettingsRepository(appContext)

    val processManager = ProcessManager(appScope)
    val installer: UbuntuInstaller = UbuntuInstaller(paths, assetRegistry, appScope)

    val shell: Shell by lazy { Shell(paths, abi) }

    init {
        Logs.init(paths.logsDir)
        com.uchat.android.linux.install.ScriptInstaller.install(appContext, paths)
    }
}
