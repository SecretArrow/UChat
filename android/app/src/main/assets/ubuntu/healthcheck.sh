#!/usr/bin/env bash
# UChat — post-install health check (runs INSIDE Ubuntu).
#
# HARD gate for installer step 9: every tool the product promises must exist, otherwise the
# step (and therefore the install) fails visibly. tmux is a convenience — warn only.
# Exits 0 with "healthcheck OK (10/10)" only when all 10 required commands are present.
set -u

REQUIRED=(bash apt-get git curl node npm python3 pip3 opencode claude)
MISSING=""

for cmd in "${REQUIRED[@]}"; do
  if command -v "$cmd" >/dev/null 2>&1; then
    ver="$("$cmd" --version 2>/dev/null | head -n 1)"
    [ -z "$ver" ] && ver="present"
    echo "[uchat] ok: $cmd ($ver)"
  else
    echo "[uchat] missing: $cmd"
    MISSING="$MISSING $cmd"
  fi
done

# Optional: tmux improves the terminal experience but is not a product promise.
if command -v tmux >/dev/null 2>&1; then
  echo "[uchat] ok: tmux ($(tmux -V 2>/dev/null))"
else
  echo "[uchat] warn: tmux not installed (optional)"
fi

uname -a

if [ -n "$MISSING" ]; then
  echo "[uchat] MISSING:$MISSING"
  exit 1
fi

echo "[uchat] healthcheck OK (10/10)"
exit 0
