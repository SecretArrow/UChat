#!/usr/bin/env bash
# UChat — post-install health check (runs INSIDE Ubuntu).
# Exits 0 when the environment is usable; prints a short report.
set -u

FAILED=0

check() {
  local label="$1"
  shift
  if command -v "$1" >/dev/null 2>&1; then
    echo "[h[uchat] ok: $label ($("$@" 2>/dev/null | head -n 1))"
  else
    echo "[h[uchat] MISSING: $label"
    FAILED=1
  fi
}

check "bash" bash --version
check "apt" apt-get --version
check "git" git --version
check "curl" curl --version
check "node" node --version
check "python3" python3 --version
check "opencode" opencode --version
check "claude" claude --version
check "tmux" tmux -V

uname -a

if [ "$FAILED" -ne 0 ]; then
  echo "[h[uchat] health check FAILED (missing components above are non-fatal for base usage but reported)"
  exit 0
fi
echo "[h[uchat] all checks passed"
exit 0
