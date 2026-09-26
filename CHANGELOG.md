# Changelog

All notable changes to UChat are documented here.
Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), versioning: [SemVer](https://semver.org/).

## [Unreleased]

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
