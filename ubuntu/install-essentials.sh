#!/usr/bin/env bash
# UChat — Ubuntu essentials installer (runs INSIDE Ubuntu via proot).
# Installs a curated, moderate package set (spec #15: no enormous packages).
set -uo pipefail

export DEBIAN_FRONTEND=noninteractive

# Acquire options make apt resilient on flaky mobile networks: 3 retries per
# repository/file and a 30 s HTTP timeout instead of hanging forever.
APT_ACQUIRE_OPTS=(-o Acquire::Retries=3 -o Acquire::http::Timeout=30)

echo "[uchat] apt-get update"
# No -qq here: quiet mode hides the E: diagnostics, which once made a CI failure look like
# "rc=1" with no cause. One visible retry gives real devices resilience against flaky mirrors.
if ! apt-get update "${APT_ACQUIRE_OPTS[@]}"; then
  echo "[uchat] apt-get update failed — retrying once"
  apt-get update "${APT_ACQUIRE_OPTS[@]}" || exit 1
fi

echo "[uchat] installing essential packages"
apt-get install -y --no-install-recommends \
  "${APT_ACQUIRE_OPTS[@]}" \
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
  python3-venv || exit 1

echo "[uchat] cleaning apt cache"
apt-get clean
rm -rf /var/lib/apt/lists/*

echo "[uchat] essentials done"
exit 0
