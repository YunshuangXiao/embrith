#!/bin/bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Embrith contributors
# 只读: 看 libselinux 里 restorecon / setfilecon 的定义, 以及 installd 怎么调用它们. 输出到 ~/selinux-inspect.txt
cd ~/aosp || exit 1
A=""
for c in external/libselinux/src/android.c external/selinux/libselinux/src/android.c; do [ -f "$c" ] && { A=$c; break; }; done
[ -n "$A" ] || A=$(find external -name android.c -path "*selinux*" | head -1)
{
echo "android.c is at: ${A:-NOT FOUND}"
echo "== marker already applied? $(grep -c KINDLE_NO_SELINUX_LIB "$A" 2>/dev/null)"
echo "== android.c: function definitions"
grep -n "^[a-z].*selinux_android_\(restorecon\|setfilecon\)\|^[a-z].*restorecon_sb\|Failed before getfilecon" "$A"
echo; echo "== context around 'Failed before getfilecon'"
grep -n "Failed before getfilecon" -B25 -A6 "$A" | head -80
echo; echo "== installd: calls into selinux"
grep -n "selinux\|getfilecon\|setfilecon\|restorecon" frameworks/native/cmds/installd/*.cpp | head -30
echo; echo "== built libselinux.so"; ls -l out/target/product/pw6/system/lib/libselinux.so 2>&1
} > ~/selinux-inspect.txt 2>&1
wc -l ~/selinux-inspect.txt
