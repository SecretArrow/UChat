<div align="center">

# UChat

**Your mobile Ubuntu development workstation.**

Run a real Ubuntu 24.04 LTS environment on your Android phone — no root required —
and operate **OpenCode CLI**, **Claude Code CLI**, Node.js, Python and Git from a
polished Material 3 app.

[![CI](https://github.com/SecretArrow/UChat/actions/workflows/ci.yml/badge.svg)](https://github.com/SecretArrow/UChat/actions/workflows/ci.yml)
[![E2E](https://github.com/SecretArrow/UChat/actions/workflows/e2e.yml/badge.svg)](https://github.com/SecretArrow/UChat/actions/workflows/e2e.yml)
[![Security](https://github.com/SecretArrow/UChat/actions/workflows/security.yml/badge.svg)](https://github.com/SecretArrow/UChat/actions/workflows/security.yml)
[![Release](https://github.com/SecretArrow/UChat/actions/workflows/release.yml/badge.svg)](https://github.com/SecretArrow/UChat/releases)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

</div>

---

## What is UChat?

UChat is an Android-native Linux development environment. Instead of faking a
terminal, UChat downloads a **genuine Ubuntu 24.04 LTS root filesystem**,
verifies it byte-for-byte with SHA-256, and runs it through **proot** in
app-private storage. Everything you expect from an Ubuntu box works:

| Category | Tools |
| --- | --- |
| AI coding | OpenCode CLI, Claude Code CLI |
| Runtimes | Node.js 22 (npm, pnpm), Bun, Python 3 (pip, venv) |
| Dev tools | git, ssh, rsync, curl, wget, build-essential, cmake, ripgrep, fd, jq, tmux, vim, nano, htop, sqlite3 |
| Archives | zip, unzip, tar, gzip, bzip2, xz, zstd |

The workflow UChat is built for:

```
Phone → UChat → Ubuntu 24.04 → Project → OpenCode / Claude
      → Terminal / Git / Node / Python → Background processes
      → Persistent workspace
```

## Supported devices

- **Android 8.0+ (minSdk 26)**, target SDK 35
- **ABIs:** `arm64-v8a` (recommended), `armeabi-v7a`, `x86_64`; the `x86` APK
  is provided but Ubuntu 24.04 no longer ships an i386 rootfs
- No root, no Magisk, no bootloader unlock, no custom ROM

## Installation

1. Go to [Releases](https://github.com/SecretArrow/UChat/releases)
2. Pick the APK for your device — the file size is shown **before** you download:
   - `UChat-vX.Y.Z-arm64-v8a.apk` — most modern phones
   - `UChat-vX.Y.Z-armeabi-v7a.apk` — older 32-bit phones
   - `UChat-vX.Y.Z-x86_64.apk` — emulators / tablets
   - `UChat-vX.Y.Z-universal.apk` — unsure? use this
3. Verify with `SHA256SUMS.txt` if you want to be thorough
4. Install, open UChat, and follow the installer wizard. It shows the exact
   download size, required storage and verifies the rootfs checksum before
   extracting anything.

## Architecture

```
android/          Android app (Kotlin, Jetpack Compose Material 3)
  app/src/main/cpp/pty.c     — native PTY (real terminals, real TUIs)
  app/src/main/java/...      — app code (see below)
  app/src/main/assets/       — xterm.js, registries, installer scripts
ubuntu/           Canonical source of the Ubuntu installer scripts
scripts/          CI helpers (asset sync, registry verification)
docs/             Architecture notes & troubleshooting
.github/          Workflows: CI, E2E, Auto Fix, Release, Nightly, Security
```

Key components:

- **Installer** (`linux/install/UbuntuInstaller.kt`): resumable 10-step state
  machine — download → verify → extract → initialize → apt → runtimes →
  OpenCode → Claude → health check → ready. Every asset shows its pinned file
  size *before* download and is checksum-verified *after* download.
- **Terminal** (`terminal/`): JNI pty (`fork`/`execve`/`TIOCSWINSZ`) bridged to
  xterm.js in a WebView. Multiple independent sessions survive UI navigation.
- **Proot layer** (`linux/Proot.kt`): argument-array command construction only
  (no shell interpolation), app-private bind mounts, fake-root (`-0`).
- **Secrets** (`core/crypto/`): Android Keystore AES/GCM. API keys are never
  written to preferences, logs, databases or exports.
- **Background processing** (`service/`): foreground `dataSync` service with a
  persistent notification (process count + Stop All), optional reboot recovery
  for explicitly approved sessions.

## Building — GitHub Actions only

> **Release builds never happen on a developer machine.** All artifacts are
> produced by GitHub Actions.

| Workflow | Trigger | What it does |
| --- | --- | --- |
| `ci.yml` | push / PR | Android Lint, Spotless, unit tests, debug APK build, actionlint + shellcheck |
| `e2e.yml` | push / PR | Instrumented tests on an API 30 x86_64 emulator |
| `autofix.yml` | push to main | Runs `spotlessApply`, commits formatting fixes with `[skip ci]` |
| `release.yml` | tag `v*` (or manual) | Builds ABI-split release APKs, signs (secrets or ephemeral key), generates SHA256SUMS + notes, publishes the release |
| `nightly.yml` | schedule | Nightly pre-release, keeps the last 7 |
| `security.yml` | push / PR / weekly | gitleaks secret scan + Trivy dependency/misconfig scan |

### Automatic versioning

Version comes **only** from git tags:

```bash
git tag v1.2.0
git push origin v1.2.0
# → versionName=1.2.0, versionCode=<commit count>, release published automatically
```

### Release signing (optional, recommended)

Add these repository secrets for reproducible signing
(`Settings → Secrets and variables → Actions`):

| Secret | Meaning |
| --- | --- |
| `ANDROID_KEYSTORE_B64` | base64 of your `.jks` keystore |
| `ANDROID_KEYSTORE_PASSWORD` | keystore password |
| `ANDROID_KEY_ALIAS` | key alias |
| `ANDROID_KEY_PASSWORD` | key password |

Without secrets, releases are signed with an **ephemeral CI key** (installable,
but you must uninstall between versions). Generate a keystore locally and
export it: `base64 -w0 uchat-release.jks`.

## Troubleshooting

See [docs/troubleshooting.md](docs/troubleshooting.md) for: installation
failures, checksum mismatch, proot failures, noexec filesystems, storage
problems, background-process killing by OEM task killers, network/DNS issues,
and missing tools.

## Security

- Rootfs and proot binaries are pinned by SHA-256 in a versioned registry and
  verified before use
- Safe archive extraction (zip-slip traversal protection, size caps, symlink checks)
- Secrets in Android Keystore only; redaction applied to all diagnostics
- CI secret scanning (gitleaks) + dependency scanning (Trivy)

Please report vulnerabilities per [SECURITY.md](SECURITY.md).

## Contributing

PRs are welcome — see [CONTRIBUTING.md](CONTRIBUTING.md). Formatting is
enforced by Spotless (`./gradlew spotlessApply` — or just push to main and let
the auto-fix workflow handle it).

## License

[Apache-2.0](LICENSE). Third-party components and their licenses are listed in
[NOTICE.md](NOTICE.md).
