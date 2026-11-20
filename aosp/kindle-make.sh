#!/bin/bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Embrith contributors
# 在编译容器里运行: bash 03-container.sh run './kindle-make.sh'
# 12GB 内存的机器: 并行度 4, 限制 Jack 的堆. 
set -e
trap 'echo; echo "== BUILD FAILED. The first real error is above (search the log for: error: / FAILED / No rule). Do NOT run 08-package.sh."' ERR
set -o pipefail   # without this, 'make | tee' hides a failed build and the old system.img looks fine
cd "$(dirname "$0")"

# 同一个源码目录只允许一个编译: 两个编译同时写 out/ 会互相删文件, 报 "Directory not empty" 之类的怪错. 
exec 9>.kindle-make.lock
if ! flock -n 9; then echo "已经有另一个编译在使用这个目录, 退出. "; exit 1; fi
echo "== env"; df -T . | tail -1; free -m | sed -n 2p
echo "== 已有的 ninja/jack 进程(正常应为空)"; { ps -eo pid,etime,cmd | grep -E "[n]inja|[j]ack-server|[j]ack-launcher" | head; } || true   # grep finds nothing = good; with pipefail that would end the script
export HOME="$PWD/.build-home"; mkdir -p "$HOME"
export LC_ALL=C
# 容器里没有登录 shell, USER 未设置; Android 的 jack 包装脚本用了 set -u 并读取 $USER
export USER=${USER:-$(id -un 2>/dev/null || echo builder)}
export ALLOW_MISSING_DEPENDENCIES=true
export USE_CCACHE=${USE_CCACHE:-1} CCACHE_DIR="$PWD/.ccache"
export JACK_SERVER_VM_ARGUMENTS="-Dfile.encoding=UTF-8 -XX:+TieredCompilation -Xmx3g"
export ANDROID_JACK_VM_ARGS="-Dfile.encoding=UTF-8 -XX:+TieredCompilation -Xmx3g"
JOBS=${JOBS:-4}
ccache -M 20G >/dev/null 2>&1 || true

source build/envsetup.sh
lunch kindle_pw6-userdebug
# Removing a package from PRODUCT_PACKAGES does NOT remove its files from out/target/product/pw6/system: the image is built from
# that folder, so the old apps stay in it. "installclean" deletes the installed files (not the object files, the rebuild is fast).
if [ "${INSTALLCLEAN:-0}" = 1 ]; then echo "== make installclean"; make installclean; fi
TARGETS=${TARGETS:-"systemimage ramdisk"}
time make -j$JOBS $TARGETS 2>&1 | tee "$PWD/build-$(date +%m%d-%H%M).log"
ls -lh out/target/product/pw6/*.img 2>/dev/null || true
echo "== BUILD OK ($(date))"
