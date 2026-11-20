#!/bin/bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Embrith contributors
# 同步 AOSP 7.1.2_r36 源码. 耗时几小时, 建议在 tmux/screen 里运行, 断线不会中断. 
# 用法: 
#   bash 02-sync-source.sh            # 官方源(Google)
#   MIRROR=tuna bash 02-sync-source.sh  # 清华镜像(国内服务器用)
set -e
export PATH=$HOME/bin:$PATH
mkdir -p ~/aosp && cd ~/aosp

if [ "$MIRROR" = "tuna" ]; then
  URL=https://mirrors.tuna.tsinghua.edu.cn/git/AOSP/platform/manifest
  export REPO_URL=https://mirrors.tuna.tsinghua.edu.cn/git/git-repo
else
  URL=https://android.googlesource.com/platform/manifest
fi

[ -d .repo ] || repo init -u "$URL" -b android-7.1.2_r36 --depth=1
# -c 只拉当前分支, --no-tags 省空间; 失败可直接重跑, 会续传
until repo sync -c -j4 --no-tags --no-clone-bundle --force-sync; do
  echo "同步中断, 30 秒后重试"; sleep 30
done
echo "源码同步完成: $(du -sh ~/aosp | cut -f1)"
