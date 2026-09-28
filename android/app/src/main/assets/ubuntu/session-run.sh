#!/usr/bin/env bash
# UChat session runner — every interactive pty session is exec'd through this script.
#
# WHY THIS EXISTS: opencode and Claude Code are Bun standalone binaries. Bun's JavaScriptCore
# deep-recurses on the main thread and Android caps the app stack (RLIMIT_STACK) far below what
# JSC needs on arm64, so the TUI dies seconds after start with
#   panic(main thread): Segmentation fault ... proot: vpid 1: terminated with signal 5
# (opencode#35384 — confirmed fix: raise RLIMIT_STACK before exec).
#
# The limit is raised to a FINITE value ON PURPOSE: RLIM_INFINITY ("unlimited") makes the
# kernel switch to the legacy bottom-up mmap layout, which proot's exec translation cannot
# survive on some Android kernels — the guest then dies SILENTLY before the tool starts
# (observed on the API-30 emulator E2E). Finite 16-64 MB keeps the modern layout and still
# gives JSC 2-8x headroom over Android's 8 MB default.
#
# Everything here is best-effort: if the kernel refuses every size we still exec the tool —
# a session with the old 8 MB stack is better than no session at all.

# Android init gives app processes soft=8MB / hard=32MB; other environments vary. Try the
# largest sane size first and fall back until the kernel accepts one.
for SIZE in 65536 32768 16384; do
  if ulimit -s "$SIZE" 2>/dev/null; then
    break
  fi
done

# Marker used by the E2E suite to prove the wrapper really ran around the session command.
export UCHAT_SESSION_RUNNER=1

exec "$@"
