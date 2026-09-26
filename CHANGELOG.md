# Changelog

All notable changes to UChat are documented here.
Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), versioning: [SemVer](https://semver.org/).

## [Unreleased]

### Planned
- Tablet navigation rail + multi-pane layouts
- SSH manager UI
- Backup scheduler
- Additional tool registry entries (Codex CLI, Gemini CLI, Aider)

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
