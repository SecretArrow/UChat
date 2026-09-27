#!/usr/bin/env bash
# UChat — Ubuntu essentials installer (runs INSIDE Ubuntu via proot).
# Installs a curated, moderate package set (spec #15: no enormous packages).
#
# Hardened for real mobile conditions (v1.6.0 field reports):
# - heals an interrupted dpkg state BEFORE touching apt, so a run killed mid-install
#   (Android background kill, battery, network stall) can never wedge later retries
# - bounded HTTP(S) timeouts + 3 download retries per attempt + 3 full install attempts
# - noninteractive everywhere, conffiles never prompt (force-confdef/confold)
# - apt-utils installed first so debconf stops emitting "delaying package configuration"
set -uo pipefail

export DEBIAN_FRONTEND=noninteractive

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Acquire options make apt resilient on flaky mobile networks: 3 retries per
# repository/file and a 30 s HTTP(S) timeout instead of hanging forever.
APT_ACQUIRE_OPTS=(-o Acquire::Retries=3 -o Acquire::http::Timeout=30 -o Acquire::https::Timeout=30)
# Conffile policy: never block a headless install on a prompt.
DPKG_FORCE_OPTS=(-o Dpkg::Options::=--force-confdef -o Dpkg::Options::=--force-confold)

echo "[uchat] healing any interrupted dpkg state"
if ! bash "$SCRIPT_DIR/dpkg-recover.sh"; then
  echo "[uchat] dpkg recovery failed — cannot run apt safely"
  exit 1
fi

echo "[uchat] apt-get update"
update_ok=0
for attempt in 1 2 3; do
  # No -qq here: quiet mode hides the E: diagnostics, which once made a CI failure look like
  # "rc=1" with no cause. One visible retry gives real devices resilience against flaky mirrors.
  if apt-get update "${APT_ACQUIRE_OPTS[@]}"; then
    update_ok=1
    break
  fi
  echo "[uchat] apt-get update failed (attempt $attempt/3)"
  sleep $((attempt * 3))
done
if [ "$update_ok" != "1" ]; then
  echo "[uchat] apt-get update failed after 3 attempts"
  exit 1
fi

echo "[uchat] installing essential packages"
install_ok=0
for attempt in 1 2 3; do
  if apt-get install -y --no-install-recommends \
      "${APT_ACQUIRE_OPTS[@]}" "${DPKG_FORCE_OPTS[@]}" \
      apt-utils \
      ca-certificates \
      curl \
      wget \
      gnupg \
      git \
      openssh-client \
      rsync \
      zip \
      unzip \
      tar \
      gzip \
      bzip2 \
      xz-utils \
      zstd \
      jq \
      less \
      nano \
      vim-tiny \
      tmux \
      htop \
      procps \
      psmisc \
      net-tools \
      iproute2 \
      file \
      tree \
      ripgrep \
      fd-find \
      build-essential \
      cmake \
      pkg-config \
      sqlite3 \
      openssl \
      python3 \
      python3-pip \
      python3-venv; then
    install_ok=1
    break
  fi
  echo "[uchat] apt install failed (attempt $attempt/3) — healing dpkg state and retrying"
  bash "$SCRIPT_DIR/dpkg-recover.sh" || true
  sleep $((attempt * 3))
done
if [ "$install_ok" != "1" ]; then
  echo "[uchat] apt install failed after 3 attempts"
  exit 1
fi

echo "[uchat] cleaning apt cache"
apt-get clean
rm -rf /var/lib/apt/lists/*

echo "[uchat] essentials done"
exit 0
