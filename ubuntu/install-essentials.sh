#!/usr/bin/env bash
# UChat — Ubuntu essentials installer (runs INSIDE Ubuntu via proot).
# Installs a curated, moderate package set (spec #15: no enormous packages).
set -uo pipefail

export DEBIAN_FRONTEND=noninteractive

echo "[uchat] apt-get update"
apt-get update -qq || exit 1

echo "[uchat] installing essential packages"
apt-get install -y --no-install-recommends \
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
