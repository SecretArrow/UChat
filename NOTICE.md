# NOTICE — Third-party components

UChat is licensed under the Apache License 2.0. It builds on the work of many
projects; their licenses are respected as described below.

## Runtime-fetched components (not redistributed in the APK)

| Component | License | Source | How used |
| --- | --- | --- | --- |
| Ubuntu 24.04 LTS base rootfs | Ubuntu uses its own licenses; base image contains packages under GPL/LGPL/MIT/etc. | cdimage.ubuntu.com | Downloaded at install time, SHA-256 verified, executed via proot. Not redistributed by UChat. |
| proot 5.3.0 static binaries | GPL-3.0-or-later | github.com/proot-me/proot | Downloaded at install time from upstream releases (never bundled). Source: same repository. |
| OpenCode CLI | MIT | opencode.ai | Installed at user request inside Ubuntu via the official installer. |
| Claude Code CLI | Anthropic terms | npm `@anthropic-ai/claude-code` | Installed at user request inside Ubuntu via npm. |
| Node.js 22 LTS | MIT | nodesource.com | Installed inside Ubuntu via apt at user request. |

Where a component is installed dynamically instead of redistributed, that is
the documented distribution strategy (see spec section 57): UChat fetches from
the upstream publisher and verifies integrity where the publisher provides
checksums, plus pinned SHA-256 for rootfs/proot.

## Bundled in the APK (source-committed)

| Component | License | Location |
| --- | --- | --- |
| xterm.js 5.5.0 | MIT | `android/app/src/main/assets/terminal/xterm.js` |
| @xterm/addon-fit 0.10.0 | MIT | `android/app/src/main/assets/terminal/xterm-addon-fit.js` |
| @xterm/addon-search 0.15.0 | MIT | `android/app/src/main/assets/terminal/xterm-addon-search.js` |
| @xterm/addon-web-links 0.11.0 | MIT | `android/app/src/main/assets/terminal/xterm-addon-web-links.js` |

## Dependencies (Gradle, via Maven Central / Google)

AndroidX (Apache-2.0), Kotlin & kotlinx.serialization (Apache-2.0),
OkHttp (Apache-2.0), Apache Commons Compress (Apache-2.0), Room (Apache-2.0),
Compose Material 3 (Apache-2.0), Robolectric (MIT), JUnit (EPL-1.0).

## Trademarks

Ubuntu and Canonical are trademarks of Canonical Ltd. UChat is not affiliated
with or endorsed by Canonical. Anthropic, Claude and OpenCode are trademarks of
their respective owners.
