#!/usr/bin/env bash
# UChat — dpkg self-healing (runs INSIDE Ubuntu via proot).
#
# Heals a package database left "interrupted" by a killed apt run (Android kills the app in
# the background, battery death, network stall). Without this, EVERY later apt run refuses
# with: "E: dpkg was interrupted, you must manually run 'dpkg --configure -a' to correct
# the problem." — which is exactly the v1.6.0 field report we are fixing.
#
# Exit codes: 0 = package database usable again; 1 = unrecoverable.
set -uo pipefail

export DEBIAN_FRONTEND=noninteractive

# 1. Stale locks. This proot session is single-user — UChat serializes every package
#    operation, so a lock file with no dpkg/apt process alive is debris from a killed run.
#    (fuser/pgrep are not installed in the minimal rootfs; the single-user guarantee makes
#    an unconditional removal safe here.)
rm -f /var/lib/dpkg/lock-frontend /var/lib/dpkg/lock \
      /var/cache/apt/archives/lock /var/lib/apt/lists/lock 2>/dev/null || true

# 2. Replay (or, if the journal itself is unreadable, clear) the interrupted-transaction
#    journal left in /var/lib/dpkg/updates, then finish any pending package configuration.
configure_ok=0
dpkg --configure -a --force-confdef --force-confold >/dev/null 2>&1 && configure_ok=1
if [ "$configure_ok" != "1" ]; then
  echo "[uchat] dpkg journal unreadable — clearing /var/lib/dpkg/updates and retrying"
  rm -f /var/lib/dpkg/updates/* 2>/dev/null || true
  if ! dpkg --configure -a --force-confdef --force-confold >/dev/null 2>&1; then
    echo "[uchat] dpkg --configure -a still failing — package database needs manual repair"
    dpkg --configure -a --force-confdef --force-confold 2>&1 | tail -5 || true
    exit 1
  fi
fi
rm -f /var/lib/dpkg/updates/* 2>/dev/null || true

# 3. Repair any half-installed package left by the killed run. Best effort: on a device
#    with no network right now this is allowed to fail — step 2 above already made dpkg
#    usable, and the next apt run fixes the rest. Needs network only when something is
#    actually broken, which is why the failure is non-fatal.
apt-get -f install -y --no-install-recommends \
  -o Dpkg::Options::=--force-confdef -o Dpkg::Options::=--force-confold \
  -o Acquire::Retries=3 -o Acquire::http::Timeout=30 -o Acquire::https::Timeout=30 \
  >/dev/null 2>&1 || true

echo "[uchat] dpkg state OK"
exit 0
