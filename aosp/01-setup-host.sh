#!/bin/bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Embrith contributors
# 在 x86_64 Ubuntu 18.04 / 20.04 服务器上准备 AOSP 7.1.2 编译环境. 
# 用法: bash 01-setup-host.sh   (需要 sudo 权限)
set -e

echo "== 检查机器"
echo "架构: $(uname -m)  (必须是 x86_64)"
echo "CPU 核数: $(nproc)"
free -g | awk '/Mem/{print "内存(GB): "$2}'
df -h ~ | tail -1 | awk '{print "家目录可用空间: "$4}'
[ "$(uname -m)" = "x86_64" ] || { echo "不是 x86_64, 停止"; exit 1; }

echo "== 安装依赖"
sudo apt-get update
sudo apt-get install -y git-core gnupg flex bison build-essential zip curl zlib1g-dev \
  gcc-multilib g++-multilib libc6-dev-i386 libncurses5 libncurses5-dev lib32ncurses5-dev \
  x11proto-core-dev libx11-dev lib32z1-dev libgl1-mesa-dev libxml2-utils xsltproc unzip \
  fontconfig python python3 openjdk-8-jdk ccache rsync bc libssl-dev squashfs-tools

# Android 7.1 必须用 JDK 8
sudo update-alternatives --set java $(update-alternatives --list java | grep 'java-8' | head -1) || true
sudo update-alternatives --set javac $(update-alternatives --list javac | grep 'java-8' | head -1) || true
java -version 2>&1 | head -1

echo "== 安装 repo"
mkdir -p ~/bin
if [ ! -x ~/bin/repo ]; then
  # 国内服务器可把下面地址换成清华镜像: https://mirrors.tuna.tsinghua.edu.cn/git/git-repo
  curl -fsSL https://storage.googleapis.com/git-repo-downloads/repo -o ~/bin/repo || \
  curl -fsSL https://mirrors.tuna.tsinghua.edu.cn/git/git-repo -o ~/bin/repo
  chmod +x ~/bin/repo
fi
grep -q 'HOME/bin' ~/.bashrc || echo 'export PATH=$HOME/bin:$PATH' >> ~/.bashrc

git config --global user.name  >/dev/null 2>&1 || git config --global user.name  "builder"
git config --global user.email >/dev/null 2>&1 || git config --global user.email "builder@localhost"
echo "完成. 接着运行 02-sync-source.sh"
