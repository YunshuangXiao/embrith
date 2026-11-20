#!/bin/bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Embrith contributors
# 只读: 为写 hwcomposer 和 gralloc 补丁收集源码细节. 输出到 ~/aosp-inspect3.txt
cd ~/aosp || exit 1
OUT=~/aosp-inspect3.txt
SF=frameworks/native/services/surfaceflinger/DisplayHardware
G=hardware/libhardware/modules/gralloc
{
echo "== HWComposer_hwc1 ctor"; sed -n 85,215p $SF/HWComposer_hwc1.cpp
echo "== vsync thread decisions"; grep -n "needVSyncThread\|mVSyncThread\|HWC_DEVICE_API_VERSION\|hwcHasApiVersion" $SF/HWComposer_hwc1.cpp | head -40
echo "== gralloc HW_FB"; grep -n -B3 -A14 "GRALLOC_USAGE_HW_FB" $G/gralloc.cpp
echo "== gralloc alloc stride"; grep -n "stride\|pStride\|align\|bpp" $G/gralloc.cpp | head -40
echo "== gralloc lock/register"; grep -n -A22 "int gralloc_lock" $G/mapper.cpp
grep -n -A25 "int gralloc_register_buffer" $G/mapper.cpp
echo "== private handle"; sed -n 1,140p $G/gralloc_priv.h
echo "== Parcel /dev/ashmem"; grep -n -B3 -A3 "/dev/ashmem" frameworks/native/libs/binder/Parcel.cpp
echo "== ro.zygote"; grep -rn "ro.zygote\|zygote32\|zygote64" build/target/product/*.mk system/core/rootdir/init.rc 2>/dev/null | head -20
echo "== HWUI qemu"; grep -rn "qemu" frameworks/base/core/jni/android_view_*.cpp frameworks/base/core/jni/android/graphics/*.cpp 2>/dev/null | head
echo "== rootdir"; ls system/core/rootdir
echo "== done"
} > $OUT 2>&1
wc -c $OUT; echo "已写入 $OUT"
