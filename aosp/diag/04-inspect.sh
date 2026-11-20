#!/bin/bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Embrith contributors
# 只读: 收集 AOSP 源码树里和我们要改的部分相关的信息, 输出到 ~/aosp-inspect.txt
# 在 WSL 里直接运行(不用进容器):  bash diag/04-inspect.sh
cd ~/aosp || exit 1
OUT=~/aosp-inspect.txt
{
echo "== manifest"; (cd .repo/manifests && git log -1 --oneline; grep -m1 -o 'revision="[^"]*"' default.xml)
echo "== size"; du -sh . 2>/dev/null
echo "== top"; ls
echo "== device"; ls device; ls device/generic 2>/dev/null
echo "== product"; ls build/target/product | head -80
echo "== libcutils ashmem"; ls system/core/libcutils | grep -iE "ashmem|android_reboot|Android.mk|Android.bp"
echo "-- ashmem-dev.c"; cat system/core/libcutils/ashmem-dev.c 2>/dev/null
echo "== binder 64bit flag"
grep -rn "64_BIT_BINDER" build frameworks/native system/core bionic hardware 2>/dev/null | head -20
echo "== hardware modules"; ls hardware/libhardware/modules
echo "-- gralloc"; ls hardware/libhardware/modules/gralloc
echo "-- hwcomposer"; ls hardware/libhardware/modules/hwcomposer 2>/dev/null
echo "== surfaceflinger"; ls frameworks/native/services/surfaceflinger | grep -iE "hwc|hardware|framebuffer|Android.mk|DisplayHardware"
ls frameworks/native/services/surfaceflinger/DisplayHardware 2>/dev/null
echo "== goldfish hwc"; ls device/generic/goldfish/hwcomposer 2>/dev/null
echo "== kernel-free minimal products"; ls build/target/product | grep -E "core|generic|minimal"
echo "== prebuilts"; ls prebuilts/clang/host/linux-x86 2>/dev/null; ls prebuilts/gcc/linux-x86/arm 2>/dev/null
echo "== done"
} > $OUT 2>&1
wc -c $OUT
echo "已写入 $OUT "
