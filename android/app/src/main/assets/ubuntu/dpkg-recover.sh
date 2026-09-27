#!/usr/bin/env bash
# UChat — dpkg self-healing (runs INSIDE Ubuntu via proot).
#
# Heals a package database left "interrupted" by a killed apt run (Android kills the app in
# the background, battery death, network stall, or a user pause). Without this, EVERY later
# apt run refuses with: "E: dpkg was interrupted, you must manually run 'dpkg --configure -a'
# to correct the problem." — exactly the v1.6.0 field report we are fixing.
#
# Exit codes: 0 = package database usable again; 1 = unrecoverable.
set -uo pipefail

export DEBIAN_FRONTEND=noninteractive

# 0. If a package manager is STILL running (e.g. an apt process that survived a pause-kill
#    is finishing its current dpkg action), wait for it — never yank locks out from under a
#    live dpkg. /proc inside proot shows the host process table; comm names are enough.
busy=1
waited=0
while [ "$waited" -lt 60 ]; do
  busy=0
  for comm_file in /proc/[0-9]*/comm; do
    name=""
    read -r name < "$comm_file" 2>/dev/null || continue
    case "$name" in
      dpkg|dpkg-deb|dpkg-query|dpkg-trigger|apt|apt-get|apt-cache|apt-config|apt-key|unattended-upgr) busy=1; break ;;
    esac
  done
  [ "$busy" = "0" ] && break
  sleep 1
  waited=$((waited + 1))
done
if [ "$busy" != "0" ]; then
  echo "[uchat] another package manager is still running — retry in a moment"
  exit 1
fi

# 1. Stale locks. This proot session is single-user — UChat serializes every package
#    operation, so with no dpkg/apt process alive (checked above) any lock file is debris
#    from a killed run. (fuser/pgrep are not installed in the minimal rootfs.)
rm -f /var/lib/dpkg/lock-frontend /var/lib/dpkg/lock \
      /var/cache/apt/archives/lock /var/lib/apt/lists/lock 2>/dev/null || true

# 2. Replay (or, if the journal itself is unreadable, clear) the interrupted-transaction
#    journal left in /var/lib/dpkg/updates, then finish any pending package configuration.
#    Errors are captured and echoed — silent failures cost hours of debugging.
configure_err="$(dpkg --configure -a --force-confdef --force-confold 2>&1)"
configure_rc=$?
if [ "$configure_rc" -ne 0 ]; then
  echo "[uchat] dpkg --configure -a failed (rc=$configure_rc)${configure_err:+: $configure_err}"
  echo "[uchat] clearing /var/lib/dpkg/updates and retrying"
  rm -f /var/lib/dpkg/updates/* 2>/dev/null || true
  retry_err="$(dpkg --configure -a --force-confdef --force-confold 2>&1)"
  retry_rc=$?
  if [ "$retry_rc" -ne 0 ]; then
    echo "[uchat] dpkg --configure -a still failing (rc=$retry_rc)${retry_err:+: $retry_err}"
    exit 1
  fi
fi
rm -f /var/lib/dpkg/updates/* 2>/dev/null || true

# 3. Repair any half-installed package left by the killed run. Best effort: on a device
#    with no network right now this is allowed to fail — step 2 already made dpkg usable,
#    and the next apt run fixes the rest. Needs network only when something is broken.
apt-get -f install -y --no-install-recommends \
  -o Dpkg::Options::=--force-confdef -o Dpkg::Options::=--force-confold \
  -o Acquire::Retries=3 -o Acquire::http::Timeout=30 -o Acquire::https::Timeout=30 \
  >/dev/null 2>&1 || true

echo "[uchat] dpkg state OK"
exit 0
