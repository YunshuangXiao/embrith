#!/bin/bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Embrith contributors
# 只读: 找出 installd 报 "Failed before getfilecon" 的代码到底在哪个库里, 以及补丁有没有真正进入编译产物. 
# 输出到 ~/getfilecon.txt (运行时间约 1 分钟)
cd ~/aosp || exit 1
OUT=out/target/product/pw6
{
echo "== 1. who prints the message (source search)"
grep -rn "Failed before getfilecon" external frameworks/native/cmds frameworks/native/libs system/core bionic 2>/dev/null | head -10
echo
echo "== 2. is the patch in the source, and is the built library newer than the source?"
for c in external/libselinux/src/android.c external/selinux/libselinux/src/android.c; do
  [ -f "$c" ] && { echo "$c  marker count: $(grep -c KINDLE_NO_SELINUX_LIB "$c")"; ls -l --time-style=full-iso "$c"; }
done
ls -l --time-style=full-iso $OUT/system/lib/libselinux.so $OUT/system/bin/installd $OUT/system.img 2>&1
echo
echo "== 3. does installd use the shared libselinux or a copy of its own?"
(readelf -d $OUT/system/bin/installd 2>/dev/null || true) | grep -E "NEEDED" 
echo "strings in installd/libselinux that contain the message:"
for f in $OUT/system/bin/installd $OUT/system/lib/libselinux.so $OUT/system/lib/libcutils.so; do printf '%s: ' "$f"; strings -a "$f" 2>/dev/null | grep -c "Failed before getfilecon"; done
echo
echo "== 4. what installd calls from selinux"
grep -n "selinux\|getfilecon\|restorecon" frameworks/native/cmds/installd/*.cpp 2>/dev/null | head -25
echo
echo "== 5. the code around the message (if it is in a source file we found)"
F=$(grep -rln "Failed before getfilecon" external frameworks/native/cmds system/core 2>/dev/null | head -1)
[ -n "$F" ] && { echo "file: $F"; grep -n "Failed before getfilecon" -B30 -A8 "$F" | head -90; }
} > ~/getfilecon.txt 2>&1
wc -l ~/getfilecon.txt
