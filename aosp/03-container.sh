#!/bin/bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Embrith contributors
# 构建并进入 AOSP 编译容器(Ubuntu 16.04), 自动选择 podman 或 docker. 
# 用法: 
#   bash 03-container.sh build          # 构建镜像(第一次, 几分钟)
#   bash 03-container.sh shell          # 进入容器, 源码目录挂载到同一路径
#   bash 03-container.sh run '命令'     # 在容器里执行一条命令(适合放进 sbatch)
# 环境变量: AOSP_DIR(源码目录, 默认 ~/aosp)
set -e
SRC=${AOSP_DIR:-$HOME/aosp}
IMG=aosp7-build
cd "$(dirname "$0")"

if command -v podman >/dev/null 2>&1; then
  RUNDIR=${XDG_RUNTIME_DIR:-/tmp/podman-run-$(id -u)}; mkdir -p "$RUNDIR"
  OPTS=""
  # 家目录在 Lustre/NFS 上时 overlay 常不可用, 改用 vfs
  if df -T "$HOME" 2>/dev/null | tail -1 | grep -qiE 'lustre|nfs'; then
    STORE=${PODMAN_STORE:-$HOME/podman-storage}; mkdir -p "$STORE"
    OPTS="--root $STORE/root --runroot $RUNDIR --storage-driver vfs"
  fi
  ENG="podman $OPTS"; USERFLAG="--userns=keep-id"
elif command -v docker >/dev/null 2>&1; then
  ENG="docker"; USERFLAG="--user $(id -u):$(id -g)"
else
  echo "没有找到 podman 或 docker. WSL/本机可运行: sudo apt-get install -y podman"; exit 1
fi

case "$1" in
  build) $ENG build -t $IMG -f Containerfile . ;;
  shell) $ENG run --rm -it $USERFLAG -e HOME="$HOME" -v "$SRC":"$SRC" -w "$SRC" $IMG bash ;;
  run)   shift; $ENG run --rm $USERFLAG -e HOME="$HOME" -v "$SRC":"$SRC" -w "$SRC" $IMG bash -lc "$*" ;;
  *) echo "用法: $0 build|shell|run '命令'"; exit 1 ;;
esac
