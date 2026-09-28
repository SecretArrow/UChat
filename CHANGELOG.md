# Changelog

All notable changes to UChat are documented here.
Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), versioning: [SemVer](https://semver.org/).

## [1.8.0] - 2026-09-28

### Fixed
- **Sessions no longer die silently in the background ("kok sering session exited")**:
  - the foreground service now runs whenever any session is running — the notification
    preference only switches between a detailed and a minimal notification, it no longer
    disables the protection that keeps Android from reaping the whole app process
  - on Android 14+ the service uses the `specialUse` foreground-service type: `dataSync`
    carries a 6-hour-per-24h runtime quota on Android 15 after which the system stops the
    service and every session dies; `onTimeout` is handled defensively
  - the notification's remote "Stop all" button was removed — one accidental tap used to
    kill every running session; sessions are stopped from the Processes screen instead
  - a session that exits while no terminal screen is attached now leaves a bilingual
    "[session exited … / sesi berakhir …]" banner in its replay (exit 137 is explained as
    an Android low-memory kill), so coming back shows WHAT happened instead of a silent
    grey tab
  - the terminal tab selection survives Activity recreation (`rememberSaveable`), so
    rotating the screen no longer shows an empty "no sessions" state
  - session restore is now ON by default and works after app-process death, not only reboots

### Added
- **Terminal size presets ("banyak opsi width & height")**: ten fixed grids from 80×24 up to
  300×94 selectable as chips in Terminal settings and via a new in-terminal "Size & zoom"
  dialog (overflow menu) that also carries font-size steppers 8–32 sp and shows the live
  columns×rows — fixed grids are scaled to stay fully visible, ideal for opencode/claude TUIs
- **Re-attach instead of duplicate sessions**: the Home OpenCode/Claude buttons show a live
  green dot when that tool is running and tapping goes back INTO the running session instead
  of stacking a second identical one
- Width slider now spans the full supported range (20–300 columns, 10–200 rows), matching
  the renderer clamps instead of silently disagreeing with them

## [1.7.0] - 2026-09-27

### Fixed
- **apt/dpkg can now actually finish on emulators and modern Android**: the proot
  environment no longer sets `PROOT_NO_SECCOMP=1` (a v1.0.0 leftover). With it set,
  proot's syscall-rewrite path never ran and every dpkg status commit died with
  "error installing new file '/var/lib/dpkg/status': Function not implemented" —
  the real cause behind the v1.6.0 "apt install failed / dpkg was interrupted" reports
  (confirmed upstream in termux/proot#390)
- **apt no longer stays broken after an interrupted install** ("E: dpkg was interrupted,
  you must manually run 'dpkg --configure -a'" / "Sub-process /usr/bin/dpkg returned an
  error code (2)"): a new `dpkg-recover.sh` step heals the package database before every
  apt run — stale locks removed, the interrupted-transaction journal replayed (or cleared
  when corrupt), pending configurations finished, and broken dependencies repaired
- **A killed app can no longer silently re-download the ~30 MB rootfs**: the resume step
  is now persisted BEFORE each step starts, so when Android kills the app mid-apt the
  next launch continues from that exact step (previously a process death mid-step fell
  through to a full re-download on metered data)
- **apt failures now report the real cause**: error reports show the actual `E:`/`dpkg:`
  lines instead of the harmless "debconf: delaying package configuration" warning, a
  nearly-full disk fails early with a clear storage message (and is detected in apt
  output via "No space left on device"), and `apt-utils` is installed so debconf stops
  delaying package configuration

### Changed
- **Pause now works at every install step**: previously Pause only stopped downloads —
  during the package-install steps the button did nothing. Scripts are now stopped
  safely (SIGTERM, then SIGKILL after a grace period), the wizard shows "Pausing…" and
  "Paused — progress is kept" feedback, and resume re-runs the interrupted step after
  healing dpkg state
- **More resilient apt on mobile networks**: 3 full install attempts (not just download
  retries) with dpkg recovery between attempts, HTTPS timeouts bounded, conffile prompts
  force-disabled, and the timeout for install steps raised from 30 to 60 minutes

## [1.6.0] - 2026-09-29

### Fixed
- **Install steps 5–10 no longer fail with "rootfs or proot missing"**: proot now only
  needs its bootstrap, not the ready marker, so the installer proceeds through the
  remaining steps instead of aborting halfway
- **Install retry/resume no longer re-downloads the rootfs**: steps 1–3 are skipped when
  the rootfs is already extracted, and the resume status is persisted across processes —
  interrupted installs continue where they stopped instead of starting over

### Changed
- **Persistent APK signing key via repo secrets**: releases are now signed with one
  stable key, so APK updates install over previous releases without uninstalling
  (Android rejects signature mismatches, and every earlier build was signed with a
  different ephemeral CI key). Exactly ONE uninstall is required to move off the old
  v1.5.0 builds; every release after that upgrades in place

## [1.5.0] - 2026-09-28

### Fixed
- **The installer now runs ALL 10 steps visibly and the dashboard appears only after
  "10. Ready"**:
  - A ready marker (`.uchat-ready`) is written inside the Ubuntu root at step 10 only —
    `isUbuntuInstalled` no longer returns true the moment `/bin/bash` exists (the
    ubuntu-base tarball ships it, so the dashboard previously swapped out the wizard right
    after step 3 while steps 4–10 were still running or failing)
  - The finished wizard ("10. Ready 🎉" + Done) stays on screen until the user taps Done
  - Legacy ≤ v1.4.0 installs are migrated automatically: rootfs trees with `/bin/bash` +
    `/usr/bin/git` (git proves the essentials step completed) get the marker and keep
    working; half-broken installs fall back to the wizard and a reinstall repairs them
  - Marker write failures fail step 10 with a clear, copyable error instead of a fake success
- **Health check (step 9) is now enforced**: `healthcheck.sh` hard-fails unless bash, apt-get,
  git, curl, node, npm, python3, pip3, opencode and claude all resolve (tmux warns only);
  the four broken `[h[uchat]` echo prefixes are fixed
- **Node.js install uses the robust tarball route**: `install-runtimes.sh` delegates to
  `install-node.sh` (arch-aware nodejs.org tarball, offline staged tarball, NodeSource
  fallback) instead of the fragile NodeSource-only path; pnpm is linked onto the standard
  PATH; the Claude Code binary is linked into `/usr/local/bin` so tarball-node installs pass
  the health check; opencode is additionally linked into `~/.opencode/bin`
- **Localized installer errors (EN + ID)**: network, storage, checksum, apt, runtimes,
  opencode, claude, health and generic failures show translated titles/leads while the raw
  technical detail (stderr tail, URLs) is kept for copyable diagnostics
- apt essentials use `Acquire::Retries=3` + `Acquire::http::Timeout=30` (mobile networks)
- proot sessions include `~/.opencode/bin` on PATH

## [1.4.0] - 2026-09-27

### Added
- **Close terminal** — full session lifecycle control:
  - ✕ Close button on every terminal tab: stops the process if needed, releases the pty
    master file descriptor, drops the session's replay buffer and removes the tab
  - "Close all sessions" in the terminal overflow menu
  - Dead sessions auto-close 60 s after exit (the `[session exited …]` banner stays readable),
    so zombie tabs and FD leaks are gone
- **API keys manager** (Settings → API keys, More → API keys, and the previously dead
  "Configure" button on AI tools): store OpenCode / Claude Code / GitHub tokens encrypted with
  Android Keystore; they are injected as environment variables into every Ubuntu session
  (`OPENCODE_API_KEY`, `ANTHROPIC_API_KEY`, `GITHUB_TOKEN`, `GH_TOKEN`) and never logged
- **Files manager is fully functional now**:
  - create files/folders, rename, delete (with protected-path guard), copy path
  - built-in text editor (≤ 256 KiB, base64-safe round-trip through the shell layer)
  - share any file to other apps via FileProvider (the manifest declaration is finally used)
  - "Extract here" for .tar.gz / .tgz / .tar
  - hidden-files toggle that actually filters dotfiles
- **Restore background sessions after reboot** now really works: launched sessions are
  persisted with autoRestart, removed when they exit or are closed, restored on reboot
  (BootReceiver) and on app start
- **Home dashboard shows real connectivity** (ConnectivityManager) instead of a hardcoded
  "Connected"

### Fixed
- Sessions that exited no longer pile up as untouchable tabs; the pty master FD leak is closed
- The foreground service stops itself when the last session ends (no more eternal
  "0 background process(es)" notification) and honours the persistent-notification setting
- The POST_NOTIFICATIONS runtime permission is requested on Android 13+
- Creating a project actually creates its directory inside Ubuntu before recording it
- Diagnostics shows a real device summary (ABI, RAM) instead of a null placeholder
- x86 APK split removed: Ubuntu 24.04 has no i386 rootfs and no x86 native libs were bundled,
  so the x86 APK could never start a shell
- Notification text uses the app's string resources (EN + ID)
- Removed the dead `unused_placeholder` DataStore key

## [1.3.1] - 2026-09-27

### Fixed
- Terminal cursor now sits exactly where the app expects it:
  - DECSCUSR mapping corrected — a steady-block request (CSI 3 q, used by
    Neovim/tmux/fish) was drawn as an UNDERLINE, making the cursor look
    vertically misplaced; underline/bar/block shapes now follow the spec
  - Steady cursor shapes (3/5/7) no longer blink, so the cursor stops
    appearing to jump around in apps that request a steady cursor
  - The cursor block now spans both cells of a wide (CJK/emoji) character and
    snaps back to the glyph's head cell instead of covering half a character

### Added
- Dark/Light theme for the whole app (Settings → Theme: System/Dark/Light):
  - Brand Material schemes for both modes (no device-dependent dynamic color)
  - Terminal chrome (tabs, toolbars, editor, settings) follows the theme
  - The terminal canvas itself switches to a light palette — ANSI colors
    rebalanced for readability on a near-white background
  - Status/navigation bar icons re-tint on theme change to stay readable
  - Persisted in DataStore; survives restarts

## [1.3.0] - 2026-09-27

### Fixed
- AI Tools installs now actually work end-to-end:
  - `install-node.sh` was referenced by the registry but missing from the APK assets —
    every Node.js install failed invisibly. Node is now installed from the official
    nodejs.org tarball (arm64/armv7l/x64/x86) with a NodeSource apt fallback
  - Claude Code self-heals: installs Node.js automatically when npm is missing
  - `gnupg` added to essentials (required by apt repository setups)
- Tool install/check feedback is no longer silent: busy spinner, live streamed
  installer log tail, and a structured error card with one-tap Copy + Retry
- Tools hub probes every registered tool when opened, so Installed/Not installed
  status is real data instead of a blank guess
- Back button no longer exits the app from any screen: overlays and tabs are
  popped first, and the HOME root requires a double-press with a toast hint
- Rotation no longer recreates the Activity (`configChanges`) and the selected
  tab/overlay survives recreation (`rememberSaveable`)

### Changed
- Terminal text rendering is crisper: exact glyph-advance cell metrics (fixes
  progressive glyph drift against cell backgrounds/cursor/box drawing) and edge
  insets so text never touches the screen border

### Added
- Terminal size settings: Fit screen (auto columns/rows) toggle plus fixed
  width (20–200 columns) and height (10–100 rows) sliders; a fixed grid larger
  than the screen is uniformly scaled down and centered so it stays visible
- E2E coverage: real Node.js install through the production script path
  (`RealToolInstallE2E`), unit tests for tool state machine, grid geometry,
  back-navigation policy and registry/asset script sync

### Planned
- Tablet navigation rail + multi-pane layouts
- SSH manager UI
- Backup scheduler
- Additional tool registry entries (Codex CLI, Gemini CLI, Aider)

## [1.1.0] - 2026-09-26

### Added
- Modern terminal experience inspired by JuiceSSH (terminal-first, dark, monospace):
  - Blinking cursor, high-contrast dark palette, Unicode + box-drawing glyph support
  - 10 000-line scrollback with smooth scrolling and fast-scroll (Alt) modifier
  - Smart auto-scroll: never force-scrolled while reading history; quick
    scroll-to-bottom button appears only when needed
  - Session tabs with running/exited status dots, per-session stop, bounded
    256 KiB replay buffer so switching sessions never loses context
  - Find in terminal (next/previous), clear, select-all copy, bracketed-paste paste
- Fully customizable extra-key toolbar:
  - 100% user-definable keys: text, terminal keys, modifiers, key combos,
    raw escape sequences (`\e`, `\x1b`, `\u001b`, octal) and commands (+Enter)
  - Long-press payloads, repeat-on-hold (arrows/PgUp/PgDn), per-key haptics
  - Modifier keys with momentary / latched / locked behaviour + long-press hard lock
  - xterm-compatible modifier encoding (CTRL+letter → 0x01–0x1A, CTRL+UP → `ESC[1;5A`,
    ALT → ESC prefix, F1–F12 SS3/CSI)
  - Drag & drop reordering, duplicate, edit, delete
- Multiple key layouts: Default, Linux, Developer, Git, Node.js, Custom —
  create, rename, duplicate, delete (custom only), reset, set as default
- Dedicated Edit Extra Keys screen with layout manager
- Terminal settings screen: font size, key height/width, toolbar position
  (top/bottom) and visibility, haptics, key repeat, modifier mode, cursor
  blink, scroll button
- Terminal architecture documentation (`docs/TERMINAL.md`)

### Changed
- Terminal layers fully separated (emulator / buffer / renderer / input /
  extra keys / editor / settings / session backend) behind a `TerminalBackend`
  interface — an SSH backend can be added without touching the UI
- Output coalescing (16 ms, ≤128 KiB per flush) for smooth rendering of
  high-throughput output without flooding the JS bridge
- Extra-key input writes directly to the pty (no JS roundtrip — low latency)
- `PtySession` supports multiple output listeners; `ProcessManager` feeds the
  replay cache for all sessions app-wide

### Fixed
- Terminal content no longer lost when switching between sessions or tabs

## [1.0.0] - 2026-09-26

### Added
- Real Ubuntu 24.04 LTS userspace on Android via proot (rootless, no Magisk/bootloader unlock)
- 10-step onboarding installer with pause/resume/retry/cancel, SHA-256 verification,
  disk-space validation and architecture detection
- Interactive file-size display BEFORE any download (pinned registry with byte-exact sizes)
- Real terminal: JNI pty + xterm.js, ANSI/UTF-8, scrollback, search, copy/paste,
  extra keys toolbar, multi-session support, proper resize propagation
- OpenCode CLI and Claude Code CLI installers with versioned installer layer
- AI Tools hub (tool registry: install/update/launch/verify)
- Process manager: PID, runtime, status, stop/kill/attach, orphan recovery
- Foreground service + persistent notification with Stop All action and channels
- Optional reboot recovery for user-approved auto-restart sessions
- Projects: create/open in Terminal/OpenCode/Claude, delete, rename metadata
- File manager over the Ubuntu filesystem with safe tar.gz compression
- Encrypted secret storage (Android Keystore AES/GCM), secrets never logged or exported
- Diagnostics screen with health check and one-tap copy (secret-free)
- GitHub Actions CI/CD: ci.yml (lint/unit/build), e2e.yml (emulator), autofix.yml
  (Spotless auto-fix), release.yml (ABI-split signed APKs + SHA256SUMS), nightly.yml,
  security.yml (gitleaks + trivy)
- Automatic versioning from git tags (versionCode = commit count)
- English + Indonesian UI strings

[Unreleased]: https://github.com/SecretArrow/UChat/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/SecretArrow/UChat/releases/tag/v1.0.0
