package com.uchat.android.ui

import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import com.uchat.android.R
import com.uchat.android.core.log.Logs
import com.uchat.android.core.settings.UChatSettings
import com.uchat.android.data.registry.ToolEntry
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
    TERMINAL_SETTINGS
}

/**
 * App router: bottom navigation + overlay screens. All application state is app-scoped (via
 * [AppContainer]); this composable only observes it.
 */
@Composable
fun AppRoot(container: AppContainer) {
    var tab by remember { mutableStateOf(Tab.HOME) }
    var overlay by remember { mutableStateOf(Overlay.NONE) }
    val scope = rememberCoroutineScope()

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

    // Tools state
    var toolVersions by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var busyTools by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Poll the live session list (cheap, CPU friendly — spec #33)
    LaunchedEffect(Unit) {
        while (true) {
            sessionsFlow.value = container.processManager.all
            delay(1000)
        }
    }

    val activeSession = sessionsState.firstOrNull { it.id == activeSessionId }

    // Foreground service while something is running (spec #9)
    LaunchedEffect(sessionsState.count { it.state == SessionState.RUNNING }) {
        if (
            container.paths.isUbuntuInstalled &&
                sessionsState.any { it.state == SessionState.RUNNING }
        ) {
            UChatService.start(container.appContext)
        }
    }

    // Initial listing once Ubuntu exists
    LaunchedEffect(container.paths.isUbuntuInstalled) {
        if (container.paths.isUbuntuInstalled) {
            listFiles(container, filesDir, { filesLoading = it }) { fileEntries = it }
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
                        if (!container.paths.isUbuntuInstalled) {
                            InstallWizardScreen(
                                abi = container.abi,
                                registry = container.assetRegistry,
                                paths = container.paths,
                                state = installState,
                                onStart = { container.installer.start(container.abi) },
                                onPause = { container.installer.pause() },
                                onResume = { container.installer.resume(container.abi) },
                                onCancel = { container.installer.cancel() },
                                onReady = { /* dashboard renders automatically once installed */},
                                modifier = commonModifier,
                            )
                        } else {
                            HomeScreen(
                                paths = container.paths,
                                runningProcesses =
                                    sessionsState.count { it.state == SessionState.RUNNING },
                                projectCount = projects.size,
                                networkConnected = true,
                                onOpenTerminal = { tab = Tab.TERMINAL },
                                onLaunchOpenCode = {
                                    launchCommand(
                                        container,
                                        "OpenCode",
                                        listOf("opencode"),
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
                                    container.projectsRepository.create(
                                        name,
                                        Proot.UBUNTU_WORKSPACE + "/projects/" + name,
                                    )
                                }
                            },
                            onOpenTerminal = { project ->
                                scope.launch { container.projectsRepository.touch(project.id) }
                                launchCommand(
                                    container,
                                    "Terminal",
                                    listOf("/bin/bash", "-l"),
                                    project.pathInUbuntu,
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
                                launchCommand(container, "Terminal", listOf("/bin/bash", "-l")) { id
                                    ->
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
                            onNavigate = { name ->
                                filesDir = normalizePath(filesDir, name)
                                listFiles(container, filesDir, { filesLoading = it }) {
                                    fileEntries = it
                                }
                            },
                            onNavigateParent = {
                                filesDir =
                                    filesDir.substringBeforeLast('/').ifEmpty {
                                        Proot.UBUNTU_WORKSPACE
                                    }
                                listFiles(container, filesDir, { filesLoading = it }) {
                                    fileEntries = it
                                }
                            },
                            onOpenTerminalHere = { dir ->
                                launchCommand(
                                    container,
                                    "Terminal",
                                    listOf("/bin/bash", "-l"),
                                    dir,
                                ) { id ->
                                    activeSessionId = id
                                    tab = Tab.TERMINAL
                                }
                            },
                            onCompress = { dir -> compressDir(container, dir) },
                            onRefresh = {
                                listFiles(container, filesDir, { filesLoading = it }) {
                                    fileEntries = it
                                }
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
                    versions = toolVersions,
                    busyTools = busyTools,
                    onCheck = { tool ->
                        checkTool(container, tool, busyTools, setBusy = { busyTools = it }) { result
                            ->
                            toolVersions = toolVersions + result
                            busyTools = busyTools - tool.id
                        }
                    },
                    onInstall = { tool ->
                        installTool(container, tool, busyTools, setBusy = { busyTools = it }) {
                            result ->
                            toolVersions = toolVersions + result
                            busyTools = busyTools - tool.id
                        }
                    },
                    onLaunch = { tool ->
                        launchCommand(container, tool.name, listOf(tool.executable)) { id ->
                            activeSessionId = id
                            overlay = Overlay.NONE
                            tab = Tab.TERMINAL
                        }
                    },
                    onConfigure = { /* configured via terminal session for now */},
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
                    onRestoreRebootChange = { enabled ->
                        scope.launch { container.settingsRepository.setRestoreAfterReboot(enabled) }
                    },
                    onPersistentNotificationChange = { enabled ->
                        scope.launch {
                            container.settingsRepository.setPersistentNotification(enabled)
                        }
                    },
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
                    device = null,
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
    onLaunched(session.id)
}

/** Injects user-configured API keys as env vars (never logged — spec #7). */
private fun secretsEnv(container: AppContainer): Map<String, String> {
    val map = mutableMapOf<String, String>()
    container.secretsRepository
        .get(com.uchat.android.data.repo.SecretsRepository.OPENCODE_API_KEY)
        ?.let { map["OPENCODE_API_KEY"] = String(it) }
    container.secretsRepository
        .get(com.uchat.android.data.repo.SecretsRepository.CLAUDE_API_KEY)
        ?.let { map["ANTHROPIC_API_KEY"] = String(it) }
    return map
}

private fun checkTool(
    container: AppContainer,
    tool: ToolEntry,
    busy: Set<String>,
    setBusy: (Set<String>) -> Unit,
    onResult: (Map<String, String>) -> Unit,
) {
    setBusy(busy + tool.id)
    container.appScope.launch {
        try {
            val result =
                container.shell.exec(listOf(tool.executable, "--version"), timeoutSeconds = 20)
            val version = if (result.success) result.stdout.trim().lineOrNull() else null
            onResult(if (version != null) mapOf(tool.id to version) else emptyMap())
        } catch (e: Exception) {
            Logs.app("check ${tool.id} failed: ${e.message}")
            onResult(emptyMap())
        }
    }
}

private fun installTool(
    container: AppContainer,
    tool: ToolEntry,
    busy: Set<String>,
    setBusy: (Set<String>) -> Unit,
    onResult: (Map<String, String>) -> Unit,
) {
    val script = tool.installScript ?: return
    setBusy(busy + tool.id)
    container.appScope.launch {
        try {
            container.shell.runScript("${Proot.UBUNTU_SCRIPTS}/$script")
            val result =
                container.shell.exec(listOf(tool.executable, "--version"), timeoutSeconds = 20)
            val version = if (result.success) result.stdout.trim().lineOrNull() else null
            onResult(if (version != null) mapOf(tool.id to version) else emptyMap())
        } catch (e: Exception) {
            Logs.app("install ${tool.id} failed: ${e.message}")
            onResult(emptyMap())
        }
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

private fun dirSize(file: java.io.File): Long =
    try {
        file.walkBottomUp().fold(0L) { acc, f -> acc + if (f.isFile) f.length() else 0L }
    } catch (_: Exception) {
        0L
    }

private fun String.lineOrNull(): String? = lineSequence().firstOrNull { it.isNotBlank() }
