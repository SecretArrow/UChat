package com.uchat.android.ui

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import com.uchat.android.R
import com.uchat.android.core.arch.DeviceAbi
import com.uchat.android.core.arch.DeviceSummary
import com.uchat.android.core.arch.NetworkMonitor
import com.uchat.android.core.fs.FileOps
import com.uchat.android.core.log.Logs
import com.uchat.android.core.settings.UChatSettings
import com.uchat.android.data.db.ProcessEntity
import com.uchat.android.data.registry.ToolEntry
import com.uchat.android.data.repo.SecretsRepository
import com.uchat.android.di.AppContainer
import com.uchat.android.linux.Proot
import com.uchat.android.linux.PtySession
import com.uchat.android.linux.SessionState
import com.uchat.android.service.UChatService
import com.uchat.android.ui.files.FileEntry
import com.uchat.android.ui.files.FilesScreen
import com.uchat.android.ui.home.HomeScreen
import com.uchat.android.ui.home.InstallWizardScreen
import com.uchat.android.ui.more.MoreScreen
import com.uchat.android.ui.processes.ProcessesScreen
import com.uchat.android.ui.projects.ProjectsScreen
import com.uchat.android.ui.settings.DiagnosticsScreen
import com.uchat.android.ui.settings.SettingsScreen
import com.uchat.android.ui.terminal.TerminalScreenView
import com.uchat.android.ui.tools.ToolUiState
import com.uchat.android.ui.tools.ToolsController
import com.uchat.android.ui.tools.ToolsScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Bottom navigation destinations. */
enum class Tab {
    HOME,
    PROJECTS,
    TERMINAL,
    FILES,
    MORE
}

private enum class Overlay {
    NONE,
    PROCESSES,
    TOOLS,
    SERVERS,
    SETTINGS,
    DIAGNOSTICS,
    EXTRA_KEYS,
    TERMINAL_SETTINGS,
    API_KEYS
}

internal enum class BackAction {
    CLOSE_OVERLAY,
    GO_HOME,
    CONFIRM_EXIT
}

/** Pure back-navigation decision (unit-tested): pop overlay → pop tab → confirm exit at root. */
internal fun resolveBackAction(hasOverlay: Boolean, notAtHome: Boolean): BackAction =
    when {
        hasOverlay -> BackAction.CLOSE_OVERLAY
        notAtHome -> BackAction.GO_HOME
        else -> BackAction.CONFIRM_EXIT
    }

/**
 * App router: bottom navigation + overlay screens. All application state is app-scoped (via
 * [AppContainer]); this composable only observes it.
 */
@Composable
fun AppRoot(container: AppContainer) {
    // Saveable so rotation / process death never dumps the user back to an unexpected screen.
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var overlay by rememberSaveable { mutableStateOf(Overlay.NONE) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val settings by container.settingsRepository.settings.collectAsState(initial = UChatSettings())
    val projects by container.projectsRepository.observeAll().collectAsState(initial = emptyList())
    val installState by container.installer.state.collectAsState()
    val extraKeysState by container.extraKeysStore.state.collectAsState()
    val sessionsFlow = remember { MutableStateFlow<List<PtySession>>(emptyList()) }

    // Keep the screen on while the terminal tab is visible (user preference, JuiceSSH-style).
    val view = LocalView.current
    LaunchedEffect(tab, overlay, settings.terminalKeepScreenOn) {
        view.keepScreenOn =
            settings.terminalKeepScreenOn && tab == Tab.TERMINAL && overlay == Overlay.NONE
    }
    val sessionsState by sessionsFlow.collectAsState()
    var activeSessionId by remember { mutableStateOf<Long?>(null) }

    // Files state
    var filesDir by remember { mutableStateOf(Proot.UBUNTU_WORKSPACE) }
    var fileEntries by remember { mutableStateOf<List<FileEntry>>(emptyList()) }
    var filesLoading by remember { mutableStateOf(false) }

    // Tools state — per-tool busy/log/version/error, all surfaced in the UI (never silent).
    val toolStates = remember { androidx.compose.runtime.mutableStateOf(ToolsController.initial()) }
    var toolVersions by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var toolsAutoChecked by rememberSaveable { mutableStateOf(false) }
    var backPressedAt by rememberSaveable { mutableLongStateOf(0L) }

    // Real network status for the Home dashboard (was hardcoded "Connected").
    var networkConnected by remember { mutableStateOf(false) }

    // Restore-after-reboot bookkeeping: attempt once per process, never in a loop.
    var restoreAttempted by rememberSaveable { mutableStateOf(false) }
    val recordedExits = remember { java.util.concurrent.ConcurrentHashMap.newKeySet<Long>() }

    // The finished wizard ("10. Ready 🎉" + Done) stays on screen until the user taps Done.
    // Before the ready-marker existed the dashboard replaced the wizard the moment step 3
    // produced /bin/bash, hiding steps 4–10; now the wizard only releases AFTER a successful
    // finish AND an explicit dismissal.
    var wizardDismissed by rememberSaveable { mutableStateOf(false) }

    // Built-in text editor state for the Files tab (guest path + buffered content).
    var editTarget by remember { mutableStateOf<String?>(null) }
    var editText by remember { mutableStateOf("") }

    val deviceSummary = remember { buildDeviceSummary(context, container.abi) }

    // Android 13+ needs a runtime grant before the persistent notification can be shown.
    val notifPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    fun refreshFiles() {
        listFiles(container, filesDir, settings.showHiddenFiles, { filesLoading = it }) {
            fileEntries = it
        }
    }

    fun toastOp(ok: Boolean) {
        if (!ok) {
            Toast.makeText(context, R.string.files_op_failed, Toast.LENGTH_SHORT).show()
        }
    }

    // Back navigation: overlays and non-home tabs are popped first; the app only exits from the
    // HOME root, and even then requires a second press within 2s so a running terminal session
    // can never be lost by an accidental back (user requirement: exit ONLY from main screen).
    BackHandler {
        when (
            resolveBackAction(hasOverlay = overlay != Overlay.NONE, notAtHome = tab != Tab.HOME)
        ) {
            BackAction.CLOSE_OVERLAY -> overlay = Overlay.NONE
            BackAction.GO_HOME -> tab = Tab.HOME
            BackAction.CONFIRM_EXIT -> {
                val now = System.currentTimeMillis()
                if (now - backPressedAt < 2000L) {
                    (context as? Activity)?.finish()
                } else {
                    backPressedAt = now
                    Toast.makeText(context, R.string.back_exit_hint, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Probe every registered tool once when the hub is first opened, so "Not installed" vs
    // "Installed" is real data instead of a blank guess.
    LaunchedEffect(overlay, container.paths.isUbuntuInstalled) {
        if (overlay == Overlay.TOOLS && !toolsAutoChecked && container.paths.isUbuntuInstalled) {
            toolsAutoChecked = true
            container.toolRegistry.tools.forEach { tool ->
                checkToolNow(container, tool, toolStates)
            }
        }
    }

    // Poll the live session list (cheap, CPU friendly — spec #33)
    LaunchedEffect(Unit) {
        while (true) {
            sessionsFlow.value = container.processManager.all
            networkConnected = NetworkMonitor.isOnline(context)
            // Auto-close dead tabs (EXITED > grace window, FAILED immediately) so pty master
            // FDs are released and the tab row never fills up with zombies.
            container.processManager.reapExited()
            // A session that exited on its own must not be restored after reboot.
            container.processManager.all
                .filter { it.state == SessionState.EXITED }
                .forEach { session ->
                    if (recordedExits.add(session.id)) {
                        container.database.processDao().deleteBySessionId(session.id)
                    }
                }
            delay(1000)
        }
    }

    val activeSession = sessionsState.firstOrNull { it.id == activeSessionId }

    // Foreground service while something is running (spec #9) — and ONLY then. The service also
    // stops itself now, so the notification no longer lingers announcing "0 background processes".
    val runningCount = sessionsState.count { it.state == SessionState.RUNNING }
    LaunchedEffect(
        runningCount,
        settings.persistentNotification,
        container.paths.isUbuntuInstalled,
    ) {
        if (
            container.paths.isUbuntuInstalled && runningCount > 0 && settings.persistentNotification
        ) {
            if (
                Build.VERSION.SDK_INT >= 33 &&
                    androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            UChatService.start(container.appContext)
        } else {
            UChatService.stop(container.appContext)
        }
    }

    // "Restore background sessions after reboot" (spec #25) — now actually functional: the rows
    // with autoRestart=1 are exactly the sessions the device killed; relaunch each of them once.
    LaunchedEffect(settings.restoreSessionsAfterReboot, container.paths.isUbuntuInstalled) {
        if (
            restoreAttempted ||
                !settings.restoreSessionsAfterReboot ||
                !container.paths.isUbuntuInstalled
        ) {
            return@LaunchedEffect
        }
        restoreAttempted = true
        try {
            container.database.processDao().restorable().forEach { row ->
                launchCommand(
                    container,
                    row.label,
                    row.command.split(" "),
                    row.workingDirectory,
                    restore = true,
                ) {}
                container.database.processDao().deleteBySessionId(row.sessionId)
            }
        } catch (e: Exception) {
            Logs.app("session restore failed: ${e.message}")
        }
    }

    // Initial listing once Ubuntu exists — re-listed when the hidden-files preference flips.
    LaunchedEffect(container.paths.isUbuntuInstalled, settings.showHiddenFiles) {
        if (container.paths.isUbuntuInstalled) {
            listFiles(container, filesDir, settings.showHiddenFiles, { filesLoading = it }) {
                fileEntries = it
            }
        }
    }

    Scaffold(
        bottomBar = {
            if (overlay == Overlay.NONE) {
                NavigationBar {
                    NavItem(Tab.HOME, tab) { tab = Tab.HOME }
                    NavItem(Tab.PROJECTS, tab) { tab = Tab.PROJECTS }
                    NavItem(Tab.TERMINAL, tab) { tab = Tab.TERMINAL }
                    NavItem(Tab.FILES, tab) { tab = Tab.FILES }
                    NavItem(Tab.MORE, tab) { tab = Tab.MORE }
                }
            }
        },
    ) { padding ->
        val commonModifier = Modifier.padding(padding)
        when (overlay) {
            Overlay.NONE ->
                when (tab) {
                    Tab.HOME ->
                        // Until Ubuntu exists the HOME tab IS the install wizard — always showing
                        // live step progress, the download bar and any fatal error with retry.
                        // (Previously the wizard was orphaned and the user saw nothing happen.)
                        // Once installed, the finished wizard remains visible until dismissed so
                        // the user actually sees "10. Ready" before the dashboard takes over.
                        if (
                            !container.paths.isUbuntuInstalled ||
                                (installState.finished && !wizardDismissed)
                        ) {
                            InstallWizardScreen(
                                abi = container.abi,
                                registry = container.assetRegistry,
                                paths = container.paths,
                                state = installState,
                                onStart = { container.installer.start(container.abi) },
                                onPause = { container.installer.pause() },
                                onResume = { container.installer.resume(container.abi) },
                                onCancel = { container.installer.cancel() },
                                onReady = { wizardDismissed = true },
                                modifier = commonModifier,
                            )
                        } else {
                            HomeScreen(
                                paths = container.paths,
                                runningProcesses =
                                    sessionsState.count { it.state == SessionState.RUNNING },
                                projectCount = projects.size,
                                networkConnected = networkConnected,
                                onOpenTerminal = { tab = Tab.TERMINAL },
                                onLaunchOpenCode = {
                                    launchCommand(
                                        container,
                                        "OpenCode",
                                        listOf("opencode"),
                                        restore = settings.restoreSessionsAfterReboot,
                                    ) { id ->
                                        activeSessionId = id
                                        tab = Tab.TERMINAL
                                    }
                                },
                                onLaunchClaude = {
                                    launchCommand(
                                        container,
                                        "Claude",
                                        listOf("claude"),
                                        restore = settings.restoreSessionsAfterReboot,
                                    ) { id ->
                                        activeSessionId = id
                                        tab = Tab.TERMINAL
                                    }
                                },
                                onOpenProjects = { tab = Tab.PROJECTS },
                                onOpenFiles = { tab = Tab.FILES },
                                modifier = commonModifier,
                            )
                        }
                    Tab.PROJECTS ->
                        ProjectsScreen(
                            projects = projects,
                            onCreate = { name ->
                                scope.launch {
                                    // The folder must REALLY exist before the record does —
                                    // otherwise "Open terminal" lands in a missing directory.
                                    val path = Proot.UBUNTU_WORKSPACE + "/projects/" + name
                                    val made =
                                        try {
                                            container.shell
                                                .exec(
                                                    listOf("/bin/bash", "-c", FileOps.mkdir(path)),
                                                    timeoutSeconds = 30,
                                                )
                                                .success
                                        } catch (e: Exception) {
                                            Logs.app("mkdir project failed: ${e.message}")
                                            false
                                        }
                                    if (!made) {
                                        Toast.makeText(
                                                context,
                                                R.string.files_op_failed,
                                                Toast.LENGTH_SHORT,
                                            )
                                            .show()
                                        return@launch
                                    }
                                    val result = container.projectsRepository.create(name, path)
                                    if (result.isFailure) {
                                        Toast.makeText(
                                                context,
                                                R.string.projects_invalid_name,
                                                Toast.LENGTH_SHORT,
                                            )
                                            .show()
                                    }
                                }
                            },
                            onOpenTerminal = { project ->
                                scope.launch { container.projectsRepository.touch(project.id) }
                                launchCommand(
                                    container,
                                    "Terminal",
                                    listOf("/bin/bash", "-l"),
                                    project.pathInUbuntu,
                                    restore = settings.restoreSessionsAfterReboot,
                                ) { id ->
                                    activeSessionId = id
                                    tab = Tab.TERMINAL
                                }
                            },
                            onOpenOpenCode = { project ->
                                launchCommand(
                                    container,
                                    "OpenCode",
                                    listOf("opencode"),
                                    project.pathInUbuntu,
                                    restore = settings.restoreSessionsAfterReboot,
                                ) { id ->
                                    activeSessionId = id
                                    tab = Tab.TERMINAL
                                }
                            },
                            onOpenClaude = { project ->
                                launchCommand(
                                    container,
                                    "Claude",
                                    listOf("claude"),
                                    project.pathInUbuntu,
                                    restore = settings.restoreSessionsAfterReboot,
                                ) { id ->
                                    activeSessionId = id
                                    tab = Tab.TERMINAL
                                }
                            },
                            onDelete = { project ->
                                scope.launch { container.projectsRepository.delete(project) }
                            },
                            modifier = commonModifier,
                        )
                    Tab.TERMINAL ->
                        TerminalScreenView(
                            sessions = sessionsState,
                            activeSession = activeSession,
                            settings = settings,
                            extraKeysState = extraKeysState,
                            replayCache = container.replayCache,
                            onSelectSession = { id -> activeSessionId = id },
                            onCreateSession = {
                                launchCommand(
                                    container,
                                    "Terminal",
                                    listOf("/bin/bash", "-l"),
                                    restore = settings.restoreSessionsAfterReboot,
                                ) { id ->
                                    activeSessionId = id
                                }
                            },
                            onStopSession = { session ->
                                session.stop()
                                if (session.id == activeSessionId) {
                                    activeSessionId =
                                        sessionsState.firstOrNull { it.id != session.id }?.id
                                }
                            },
                            onCloseSession = { session ->
                                // CLOSE (user request): stop if needed, release the pty FD, drop
                                // the replay buffer and remove the tab — the real end of a session.
                                container.processManager.remove(session.id)
                                container.replayCache.remove(session.id)
                                scope.launch {
                                    container.database.processDao().deleteBySessionId(session.id)
                                }
                                if (session.id == activeSessionId) {
                                    activeSessionId =
                                        sessionsState.firstOrNull { it.id != session.id }?.id
                                }
                            },
                            onCloseAllSessions = {
                                container.processManager.closeAll()
                                container.replayCache.clearAll()
                                scope.launch { container.database.processDao().deleteAll() }
                                activeSessionId = null
                            },
                            onSelectLayout = { id -> container.extraKeysStore.selectLayout(id) },
                            onOpenEditor = { overlay = Overlay.EXTRA_KEYS },
                            onOpenSettings = { overlay = Overlay.TERMINAL_SETTINGS },
                            modifier = commonModifier,
                        )
                    Tab.FILES ->
                        FilesScreen(
                            entries = fileEntries,
                            currentDir = filesDir,
                            loading = filesLoading,
                            showHidden = settings.showHiddenFiles,
                            onNavigate = { name ->
                                filesDir = normalizePath(filesDir, name)
                                refreshFiles()
                            },
                            onNavigateParent = {
                                filesDir =
                                    filesDir.substringBeforeLast('/').ifEmpty {
                                        Proot.UBUNTU_WORKSPACE
                                    }
                                refreshFiles()
                            },
                            onOpenTerminalHere = { dir ->
                                launchCommand(
                                    container,
                                    "Terminal",
                                    listOf("/bin/bash", "-l"),
                                    dir,
                                    restore = settings.restoreSessionsAfterReboot,
                                ) { id ->
                                    activeSessionId = id
                                    tab = Tab.TERMINAL
                                }
                            },
                            onCompress = { dir -> compressDir(container, dir) },
                            onRefresh = { refreshFiles() },
                            onToggleHidden = {
                                scope.launch {
                                    container.settingsRepository.setShowHiddenFiles(
                                        !settings.showHiddenFiles
                                    )
                                }
                            },
                            onCreateFile = { name ->
                                scope.launch {
                                    toastOp(
                                        runFileOp(
                                            container,
                                            FileOps.touch(joinGuestPath(filesDir, name))
                                        )
                                    )
                                    refreshFiles()
                                }
                            },
                            onCreateFolder = { name ->
                                scope.launch {
                                    toastOp(
                                        runFileOp(
                                            container,
                                            FileOps.mkdir(joinGuestPath(filesDir, name))
                                        )
                                    )
                                    refreshFiles()
                                }
                            },
                            onRename = { entry, newName ->
                                scope.launch {
                                    val from = joinGuestPath(filesDir, entry.name)
                                    val to = joinGuestPath(filesDir, newName)
                                    toastOp(runFileOp(container, FileOps.rename(from, to)))
                                    refreshFiles()
                                }
                            },
                            onDelete = { entry ->
                                val full = joinGuestPath(filesDir, entry.name)
                                if (!FileOps.isDeletable(full)) {
                                    Toast.makeText(
                                            context,
                                            R.string.files_protected_path,
                                            Toast.LENGTH_SHORT,
                                        )
                                        .show()
                                } else {
                                    scope.launch {
                                        toastOp(runFileOp(container, FileOps.delete(full)))
                                        refreshFiles()
                                    }
                                }
                            },
                            onCopyPath = { path ->
                                val cm =
                                    context.getSystemService(Context.CLIPBOARD_SERVICE)
                                        as? ClipboardManager
                                cm?.setPrimaryClip(ClipData.newPlainText("UChat path", path))
                                Toast.makeText(context, R.string.action_copied, Toast.LENGTH_SHORT)
                                    .show()
                            },
                            onExtract = { entry ->
                                scope.launch {
                                    val archive = joinGuestPath(filesDir, entry.name)
                                    val cmd =
                                        if (entry.name.lowercase().endsWith(".tar")) {
                                            FileOps.extractTar(archive, filesDir)
                                        } else {
                                            FileOps.extractTarGz(archive, filesDir)
                                        }
                                    toastOp(runFileOp(container, cmd, timeoutSeconds = 600))
                                    refreshFiles()
                                }
                            },
                            onEdit = { entry ->
                                scope.launch {
                                    if (entry.sizeBytes > FileOps.MAX_EDIT_BYTES) {
                                        Toast.makeText(
                                                context,
                                                R.string.files_import_too_large,
                                                Toast.LENGTH_SHORT,
                                            )
                                            .show()
                                        return@launch
                                    }
                                    val full = joinGuestPath(filesDir, entry.name)
                                    val result =
                                        try {
                                            container.shell.exec(
                                                listOf("/bin/bash", "-c", FileOps.cat(full)),
                                                timeoutSeconds = 30,
                                            )
                                        } catch (e: Exception) {
                                            Logs.app("read file failed: ${e.message}")
                                            null
                                        }
                                    if (result?.success == true) {
                                        editTarget = full
                                        editText = result.stdout
                                    } else {
                                        toastOp(false)
                                    }
                                }
                            },
                            onShare = { entry ->
                                shareFile(
                                    context,
                                    container,
                                    joinGuestPath(filesDir, entry.name),
                                    entry.name,
                                )
                            },
                            modifier = commonModifier,
                        )
                    Tab.MORE ->
                        MoreScreen(
                            onOpenProcesses = { overlay = Overlay.PROCESSES },
                            onOpenTools = { overlay = Overlay.TOOLS },
                            onOpenServers = { overlay = Overlay.SERVERS },
                            onOpenSettings = { overlay = Overlay.SETTINGS },
                            onOpenDiagnostics = { overlay = Overlay.DIAGNOSTICS },
                            onOpenExtraKeys = { overlay = Overlay.EXTRA_KEYS },
                            onOpenTerminalSettings = { overlay = Overlay.TERMINAL_SETTINGS },
                            onOpenSecrets = { overlay = Overlay.API_KEYS },
                            modifier = commonModifier,
                        )
                }
            Overlay.PROCESSES ->
                ProcessesScreen(
                    sessions = sessionsState,
                    onAttach = { session ->
                        activeSessionId = session.id
                        overlay = Overlay.NONE
                        tab = Tab.TERMINAL
                    },
                    onStop = { it.stop() },
                    onKill = { it.kill() },
                    onStopAll = { container.processManager.stopAll() },
                    modifier = commonModifier,
                )
            Overlay.TOOLS ->
                ToolsScreen(
                    tools = container.toolRegistry.tools,
                    toolStates = toolStates.value,
                    onCheck = { tool ->
                        scope.launch { checkToolNow(container, tool, toolStates) }
                    },
                    onInstall = { tool ->
                        scope.launch { installToolNow(container, tool, toolStates) }
                    },
                    onLaunch = { tool ->
                        launchCommand(
                            container,
                            tool.name,
                            listOf(tool.executable),
                            restore = settings.restoreSessionsAfterReboot,
                        ) { id ->
                            activeSessionId = id
                            overlay = Overlay.NONE
                            tab = Tab.TERMINAL
                        }
                    },
                    onConfigure = { _ -> overlay = Overlay.API_KEYS },
                    modifier = commonModifier,
                )
            Overlay.SERVERS ->
                com.uchat.android.ui.servers.ServersScreen(
                    container = container,
                    modifier = commonModifier,
                )
            Overlay.SETTINGS ->
                SettingsScreen(
                    settings = settings,
                    paths = container.paths,
                    ubuntuBytes = dirSize(container.paths.ubuntuRoot),
                    workspaceBytes = dirSize(container.paths.workspaceDir),
                    onFontSizeChange = { size ->
                        scope.launch { container.settingsRepository.setTerminalFontSize(size) }
                    },
                    onThemeModeChange = { mode ->
                        scope.launch { container.settingsRepository.setAppThemeMode(mode) }
                    },
                    onRestoreRebootChange = { enabled ->
                        scope.launch { container.settingsRepository.setRestoreAfterReboot(enabled) }
                    },
                    onPersistentNotificationChange = { enabled ->
                        scope.launch {
                            container.settingsRepository.setPersistentNotification(enabled)
                        }
                    },
                    onOpenSecrets = { overlay = Overlay.API_KEYS },
                    onCleanup = {
                        scope.launch {
                            container.shell.exec(
                                listOf(
                                    "/bin/bash",
                                    "-c",
                                    "apt-get clean; npm cache clean --force >/dev/null 2>&1 || true; rm -rf /tmp/* 2>/dev/null || true",
                                ),
                                timeoutSeconds = 120,
                            )
                        }
                    },
                    onReset = {
                        scope.launch {
                            container.processManager.stopAll()
                            container.paths.ubuntuRoot.deleteRecursively()
                            container.installer.reset()
                            overlay = Overlay.NONE
                            tab = Tab.HOME
                        }
                    },
                    onOpenDiagnostics = { overlay = Overlay.DIAGNOSTICS },
                    modifier = commonModifier,
                )
            Overlay.DIAGNOSTICS ->
                DiagnosticsScreen(
                    device = deviceSummary,
                    toolVersions = toolVersions,
                    uchatVersion = com.uchat.android.core.BuildInfo.VERSION_NAME,
                    onRunHealthCheck = {
                        scope.launch {
                            val results = runHealthCheck(container)
                            toolVersions = toolVersions + results
                        }
                    },
                    modifier = commonModifier,
                )
            Overlay.EXTRA_KEYS ->
                com.uchat.android.ui.terminal.ExtraKeysEditorScreen(
                    store = container.extraKeysStore,
                    onBack = { overlay = Overlay.NONE },
                    modifier = commonModifier,
                )
            Overlay.TERMINAL_SETTINGS ->
                com.uchat.android.ui.terminal.TerminalSettingsScreen(
                    settings = settings,
                    repository = container.settingsRepository,
                    scope = scope,
                    onBack = { overlay = Overlay.NONE },
                    modifier = commonModifier,
                )
            Overlay.API_KEYS ->
                com.uchat.android.ui.settings.SecretsScreen(
                    repository = container.secretsRepository,
                    onBack = { overlay = Overlay.NONE },
                    modifier = commonModifier,
                )
        }

        // Built-in text editor for the Files tab (overlay dialog — reachable from any tab).
        editTarget?.let { path ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { editTarget = null },
                title = { Text(path.substringAfterLast('/')) },
                text = {
                    androidx.compose.material3.OutlinedTextField(
                        value = editText,
                        onValueChange = { editText = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 6,
                        maxLines = 14,
                    )
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            val target = path
                            val content = editText
                            editTarget = null
                            scope.launch {
                                val b64 =
                                    android.util.Base64.encodeToString(
                                        content.toByteArray(Charsets.UTF_8),
                                        android.util.Base64.NO_WRAP,
                                    )
                                toastOp(runFileOp(container, FileOps.writeBase64(target, b64)))
                                refreshFiles()
                            }
                        }
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { editTarget = null }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun RowScope.NavItem(tab: Tab, current: Tab, onClick: () -> Unit) {
    val icon =
        when (tab) {
            Tab.HOME -> Icons.Filled.Home
            Tab.PROJECTS -> Icons.Filled.FolderOpen
            Tab.TERMINAL -> Icons.Filled.Terminal
            Tab.FILES -> Icons.Filled.Folder
            Tab.MORE -> Icons.Filled.MoreHoriz
        }
    val label =
        when (tab) {
            Tab.HOME -> stringResource(R.string.nav_home)
            Tab.PROJECTS -> stringResource(R.string.nav_projects)
            Tab.TERMINAL -> stringResource(R.string.nav_terminal)
            Tab.FILES -> stringResource(R.string.nav_files)
            Tab.MORE -> stringResource(R.string.nav_more)
        }
    NavigationBarItem(
        selected = tab == current,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) },
    )
}

// --------------------------------------------------------------------- //
// Session + shell helpers

private fun launchCommand(
    container: AppContainer,
    label: String,
    command: List<String>,
    workingDirInUbuntu: String = Proot.UBUNTU_HOME,
    restore: Boolean = false,
    onLaunched: (Long) -> Unit,
) {
    if (!container.paths.isUbuntuInstalled) return
    val session =
        Proot.launchSession(
            paths = container.paths,
            abi = container.abi,
            label = label,
            innerCommand = command,
            workingDirInUbuntu = workingDirInUbuntu,
            extraEnv = secretsEnv(container),
            scope = container.appScope,
        )
    container.processManager.register(session)
    if (restore) {
        // Persist for restore-after-reboot / app-death recovery (removed again on exit/close).
        container.appScope.launch {
            try {
                container.database
                    .processDao()
                    .insert(
                        ProcessEntity(
                            sessionId = session.id,
                            label = label,
                            command = command.joinToString(" "),
                            workingDirectory = workingDirInUbuntu,
                            autoRestart = true,
                        )
                    )
            } catch (e: Exception) {
                Logs.app("persist session failed: ${e.message}")
            }
        }
    }
    onLaunched(session.id)
}

/** Injects user-configured API keys as env vars (never logged — spec #7). */
private fun secretsEnv(container: AppContainer): Map<String, String> {
    val map = mutableMapOf<String, String>()
    container.secretsRepository.get(SecretsRepository.OPENCODE_API_KEY)?.let {
        map["OPENCODE_API_KEY"] = String(it)
    }
    container.secretsRepository.get(SecretsRepository.CLAUDE_API_KEY)?.let {
        map["ANTHROPIC_API_KEY"] = String(it)
    }
    container.secretsRepository.get(SecretsRepository.GITHUB_TOKEN)?.let { token ->
        map["GITHUB_TOKEN"] = String(token)
        map["GH_TOKEN"] = String(token)
    }
    return map
}

/**
 * Probes whether [tool] is present, streaming the outcome into [states]. Failures during a plain
 * check are shown as "Not installed" (no error card) — installing is where errors surface.
 */
private suspend fun checkToolNow(
    container: AppContainer,
    tool: ToolEntry,
    states: androidx.compose.runtime.MutableState<Map<String, ToolUiState>>,
) {
    if (!container.paths.isUbuntuInstalled) {
        states.value =
            ToolsController.fail(
                states.value,
                tool.id,
                container.appContext.getString(R.string.tools_ubuntu_missing),
            )
        return
    }
    states.value = ToolsController.begin(states.value, tool.id)
    try {
        val result = container.shell.exec(listOf(tool.executable, "--version"), timeoutSeconds = 30)
        val version = if (result.success) result.stdout.trim().lineOrNull() else null
        states.value =
            if (version != null) {
                ToolsController.succeed(states.value, tool.id, version)
            } else {
                ToolsController.absent(states.value, tool.id)
            }
    } catch (e: Exception) {
        Logs.app("check ${tool.id} failed: ${e.message}")
        states.value = ToolsController.absent(states.value, tool.id)
    }
}

/**
 * Runs the tool's install script INSIDE Ubuntu, streaming every output line into [states], then
 * verifies the executable. Every failure path is surfaced — the old version logged to logcat and
 * moved on, which looked exactly like "the button does nothing".
 */
private suspend fun installToolNow(
    container: AppContainer,
    tool: ToolEntry,
    states: androidx.compose.runtime.MutableState<Map<String, ToolUiState>>,
) {
    val script = tool.installScript ?: return
    if (!container.paths.isUbuntuInstalled) {
        states.value =
            ToolsController.fail(
                states.value,
                tool.id,
                container.appContext.getString(R.string.tools_ubuntu_missing),
            )
        return
    }
    states.value = ToolsController.begin(states.value, tool.id)
    try {
        val result =
            container.shell.runScript("${Proot.UBUNTU_SCRIPTS}/$script", timeoutSeconds = 1800) {
                line ->
                states.value = ToolsController.appendLog(states.value, tool.id, line)
            }
        // Verify regardless of the script exit code — presence is the real signal.
        val verify = container.shell.exec(listOf(tool.executable, "--version"), timeoutSeconds = 30)
        val version = if (verify.success) verify.stdout.trim().lineOrNull() else null
        states.value =
            if (version != null) {
                ToolsController.succeed(states.value, tool.id, version)
            } else {
                val detail =
                    ToolsController.summarizeFailure(if (result.success) verify else result)
                        ?: "installer exited ${result.exitCode}, ${tool.executable} still not found"
                ToolsController.fail(states.value, tool.id, detail)
            }
    } catch (e: Exception) {
        Logs.app("install ${tool.id} failed: ${e.message}")
        states.value =
            ToolsController.fail(states.value, tool.id, e.message ?: e.javaClass.simpleName)
    }
}

private suspend fun runHealthCheck(container: AppContainer): Map<String, String> {
    if (!container.paths.isUbuntuInstalled) return emptyMap()
    val versions = mutableMapOf<String, String>()
    listOf(
            "git" to listOf("git", "--version"),
            "node" to listOf("node", "--version"),
            "npm" to listOf("npm", "--version"),
            "python" to listOf("python3", "--version"),
            "opencode" to listOf("opencode", "--version"),
            "claude" to listOf("claude", "--version"),
        )
        .forEach { (key, cmd) ->
            try {
                val result = container.shell.exec(cmd, timeoutSeconds = 30)
                if (result.success) {
                    versions[key] = result.stdout.trim().lineOrNull() ?: "ok"
                }
            } catch (_: Exception) {
                // tool not installed — omitted from diagnostics
            }
        }
    return versions
}

private fun listFiles(
    container: AppContainer,
    dir: String,
    showHidden: Boolean,
    setLoading: (Boolean) -> Unit,
    onResult: (List<FileEntry>) -> Unit,
) {
    if (!container.paths.isUbuntuInstalled) {
        onResult(emptyList())
        return
    }
    setLoading(true)
    container.appScope.launch {
        try {
            // find -printf gives a stable, parseable format (type|size|mode|name)
            val result =
                container.shell.exec(
                    listOf(
                        "/usr/bin/find",
                        dir,
                        "-maxdepth",
                        "1",
                        "-mindepth",
                        "1",
                        "-printf",
                        "%y|%s|%m|%f\n",
                    ),
                    timeoutSeconds = 30,
                )
            val entries =
                if (result.success) {
                    result.stdout
                        .lineSequence()
                        .filter { it.isNotBlank() }
                        .mapNotNull { line ->
                            val parts = line.split("|", limit = 4)
                            if (parts.size < 4) return@mapNotNull null
                            FileEntry(
                                name = parts[3],
                                isDir = parts[0] == "d",
                                isLink = parts[0] == "l",
                                sizeBytes = parts[1].toLongOrNull() ?: 0,
                                mode = parts[2],
                            )
                        }
                        // Respect the user's hidden-files preference (was stored but ignored).
                        .filter { showHidden || !it.name.startsWith(".") }
                        .sortedWith(
                            compareByDescending<FileEntry> { it.isDir }
                                .thenBy { it.name.lowercase() }
                        )
                        .toList()
                } else {
                    emptyList()
                }
            setLoading(false)
            onResult(entries)
        } catch (e: Exception) {
            Logs.app("listFiles failed: ${e.message}")
            setLoading(false)
            onResult(emptyList())
        }
    }
}

private fun compressDir(container: AppContainer, dir: String) {
    if (!container.paths.isUbuntuInstalled) return
    container.appScope.launch {
        val name = dir.substringAfterLast('/').ifEmpty { "archive" }
        val stamp = System.currentTimeMillis()
        try {
            container.shell.exec(
                listOf(
                    "/bin/bash",
                    "-c",
                    "cd \"${dir.replace("\"", "\\\"")}\" && tar -czf /root/downloads/$name-$stamp.tar.gz .",
                ),
                timeoutSeconds = 600,
            )
            Logs.app("compress finished: $name-$stamp.tar.gz")
        } catch (e: Exception) {
            Logs.app("compress failed: ${e.message}")
        }
    }
}

private fun normalizePath(current: String, name: String): String {
    if (name.startsWith("/")) return name
    return if (current == "/") "/$name" else "$current/$name"
}

private fun joinGuestPath(dir: String, name: String): String = normalizePath(dir, name)

/** Runs a Files-tab operation through the safe shell layer; false means the op failed. */
private suspend fun runFileOp(
    container: AppContainer,
    command: String,
    timeoutSeconds: Long = 60,
): Boolean =
    try {
        if (!container.paths.isUbuntuInstalled) {
            false
        } else {
            container.shell
                .exec(listOf("/bin/bash", "-c", command), timeoutSeconds = timeoutSeconds)
                .success
        }
    } catch (e: Exception) {
        Logs.app("file op failed: ${e.message}")
        false
    }

/**
 * Maps a guest (Ubuntu) path to its host file. Bind-mounted trees (workspace, downloads, shared)
 * live OUTSIDE the rootfs on the host, so they are remapped to their real bind source; everything
 * else resolves under the ubuntu rootfs directory.
 */
internal fun hostFileForGuest(container: AppContainer, guestPath: String): java.io.File? {
    val clean = guestPath.trim().trimEnd('/')
    if (clean.isEmpty() || clean.split('/').any { it == ".." }) return null
    val mapped =
        when {
            clean == Proot.UBUNTU_WORKSPACE || clean.startsWith("${Proot.UBUNTU_WORKSPACE}/") ->
                container.paths.workspaceDir.absolutePath.trimEnd('/') +
                    clean.removePrefix(Proot.UBUNTU_WORKSPACE)
            clean == Proot.UBUNTU_DOWNLOADS || clean.startsWith("${Proot.UBUNTU_DOWNLOADS}/") ->
                container.paths.downloadsDir.absolutePath.trimEnd('/') +
                    clean.removePrefix(Proot.UBUNTU_DOWNLOADS)
            else -> container.paths.ubuntuRoot.absolutePath.trimEnd('/') + clean
        }
    return java.io.File(mapped).takeIf { it.isFile }
}

/** Share a file out of Ubuntu via the (previously dead) FileProvider declaration. */
private fun shareFile(
    context: Context,
    container: AppContainer,
    guestPath: String,
    name: String,
) {
    val src = hostFileForGuest(container, guestPath)
    if (src == null) {
        Toast.makeText(context, R.string.files_op_failed, Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val destDir = java.io.File(container.paths.cacheDir, "shared").apply { mkdirs() }
        val dest = java.io.File(destDir, name)
        src.inputStream().use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        val uri =
            androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                dest,
            )
        val send =
            Intent(Intent.ACTION_SEND)
                .setType("application/octet-stream")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, null))
    } catch (e: Exception) {
        Logs.app("share failed: ${e.message}")
        Toast.makeText(context, R.string.files_op_failed, Toast.LENGTH_SHORT).show()
    }
}

/** Real device facts for the Diagnostics screen (was falling back to a null summary). */
private fun buildDeviceSummary(context: Context, abi: DeviceAbi): DeviceSummary {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val memInfo = android.app.ActivityManager.MemoryInfo()
    am?.getMemoryInfo(memInfo)
    return DeviceSummary(
        abi = abi,
        availableBytes = context.filesDir.usableSpace,
        totalBytes = context.filesDir.totalSpace,
        model = Build.MODEL,
        androidVersion = Build.VERSION.RELEASE,
        ramMb = memInfo.totalMem / (1024L * 1024L),
    )
}

private fun dirSize(file: java.io.File): Long =
    try {
        file.walkBottomUp().fold(0L) { acc, f -> acc + if (f.isFile) f.length() else 0L }
    } catch (_: Exception) {
        0L
    }

private fun String.lineOrNull(): String? = lineSequence().firstOrNull { it.isNotBlank() }
