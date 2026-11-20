#!/bin/bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Embrith contributors
# 只读: ~/aosp 里有哪些文件和官方 android-7.1.2_r36 不一样?
# 备份时只需要保存这些修改 + 本文件夹的脚本; AOSP 源码本身可以用 02-sync-source.sh 重新下载. 
# 05-apply.sh 之外的手工修改会在"没有被 05-apply.sh 覆盖"那一段里显示出来: 那些必须另外备份(或者加进 05-apply.sh). 
#   用法(在 WSL 里直接运行, 不用进容器):   bash 19-check-changes.sh        结果同时写进 ~/aosp-changes/
export PATH=$HOME/bin:$PATH
cd ~/aosp 2>/dev/null || { echo "没有 ~/aosp"; exit 1; }
command -v repo >/dev/null || { echo "找不到 repo 命令(~/bin/repo)"; exit 1; }
OUT=~/aosp-changes; mkdir -p "$OUT"

# 05-apply.sh 会改的文件(相对 ~/aosp)
COVERED="system/core/libcutils/ashmem-dev.c
hardware/libhardware/modules/gralloc/gralloc.cpp
system/core/init/init.cpp
system/core/init/property_service.cpp
system/core/init/service.cpp
system/core/init/util.cpp
system/core/rootdir/init.rc
external/libselinux/src/android.c
external/selinux/libselinux/src/android.c
frameworks/native/cmds/installd/commands.cpp
frameworks/base/services/core/java/com/android/server/AlarmManagerService.java"

echo "== 各个 git 项目里有改动的文件(可能要几分钟)"
repo forall -c 'git status --porcelain 2>/dev/null | cut -c4- | sed "s|^|$REPO_PATH/|"' 2>/dev/null \
  | grep -vE '\.orig$|\.orig_embrith$' | sort -u > "$OUT/changed-files.txt"
wc -l < "$OUT/changed-files.txt" | sed 's/^/改动的文件数: /'

echo; echo "== 已经被 05-apply.sh 覆盖的(丢了重新运行 05-apply.sh 就能恢复)"
grep -Fxf <(echo "$COVERED") "$OUT/changed-files.txt" | sed 's/^/   /'
echo; echo "== 没有被 05-apply.sh 覆盖的(这些必须备份!)"
grep -Fxvf <(echo "$COVERED") "$OUT/changed-files.txt" | sed 's/^/   /' | tee "$OUT/NOT-covered.txt"
[ -s "$OUT/NOT-covered.txt" ] || echo "   (没有: 05-apply.sh 已经包含了所有对 AOSP 源码的修改)"

repo diff > "$OUT/tracked-changes.patch" 2>/dev/null
echo; echo "== 完整补丁(所有已跟踪文件的改动): $OUT/tracked-changes.patch  ($(du -h "$OUT/tracked-changes.patch" | cut -f1))"
echo "== 不在任何 git 项目里的东西(不会出现在上面): "
ls -d device/kindle/common device/kindle/pw6 kindle-make.sh 08-package.sh 15-check-system.sh 2>/dev/null | sed 's/^/   /'
echo "   (它们都是 05-apply.sh 从本文件夹复制过去的, 不用单独备份)"
echo; echo "== 编译产物(不用备份源码, 但这两个文件很值得留一份, 重新编译要几个小时): "
ls -l pkg/embrith.raw 2>/dev/null | sed 's/^/   /'
