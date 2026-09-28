#!/usr/bin/env bash
# UChat session runner — every interactive pty session is exec'd through this script.
#
# WHY THIS EXISTS: opencode and Claude Code are Bun standalone binaries. Bun's JavaScriptCore
# deep-recurses on the main thread and Android caps the app stack (RLIMIT_STACK) far below what
# JSC needs on arm64, so the TUI dies seconds after start with
#   panic(main thread): Segmentation fault ... proot: vpid 1: terminated with signal 5
# (opencode#35384 — confirmed fix: raise RLIMIT_STACK before exec).
# The runner raises the stack limit as high as the kernel allows, then execs the requested tool
# so the limit applies to the final program image.
#
# Everything here is best-effort: if the kernel refuses a limit we still exec the tool —
# a session with the old 8 MB stack is better than no session at all.

# 1) Preferred: unlimited stack (works whenever the hard limit is RLIM_INFINITY).
ulimit -s unlimited 2>/dev/null || true

# 2) Fallback: raise the soft limit to the hard limit (Android often caps hard at 32 MB).
HARD="$(ulimit -Hs 2>/dev/null || true)"
if [ -n "$HARD" ] && [ "$HARD" != "unlimited" ]; then
  ulimit -s "$HARD" 2>/dev/null || true
fi

# Marker used by the E2E suite to prove the wrapper really ran around the session command.
export UCHAT_SESSION_RUNNER=1

exec "$@"
