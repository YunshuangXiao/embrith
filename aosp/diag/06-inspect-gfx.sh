#!/bin/bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Embrith contributors
# 只读: 检查图形栈相关源码(SurfaceFlinger/GLES/ashmem 引用/模块名). 输出到 ~/aosp-inspect2.txt
cd ~/aosp || exit 1
OUT=~/aosp-inspect2.txt
{
echo "== RenderEngine"; ls frameworks/native/services/surfaceflinger/RenderEngine
grep -n "ES-CM\|GLES11\|GLES20\|GL_VERSION\|GLES_VERSION" frameworks/native/services/surfaceflinger/RenderEngine/RenderEngine.cpp | head -30
echo "== libagl / EGL"; ls frameworks/native/opengl/libagl | head -30; ls frameworks/native/opengl/libs
grep -n "LOCAL_MODULE" frameworks/native/opengl/libagl/Android.mk
grep -n "libGLES_android\|egl.cfg\|hw_get_module\|ro.kernel.qemu\|qemu.gles" frameworks/native/opengl/libs/EGL/Loader.cpp frameworks/native/opengl/libs/EGL/egl.cpp | head -30
echo "== ThreadedRenderer qemu"; grep -n -B2 -A12 "static boolean isAvailable" frameworks/base/core/java/android/view/ThreadedRenderer.java
echo "== SF qemu / hwc2"; grep -rn "ro.kernel.qemu\|qemu.gles\|TARGET_USES_HWC2" frameworks/native/services/surfaceflinger build/core 2>/dev/null | head -20
echo "== DisplayDevice::swapBuffers"; grep -n -A25 "void DisplayDevice::swapBuffers" frameworks/native/services/surfaceflinger/DisplayDevice.cpp
echo "== HWC1 loading"; grep -n "hw_get_module\|loadHwcModule\|loadFbHalModule\|HWC_HARDWARE_MODULE_ID\|GRALLOC_HARDWARE" frameworks/native/services/surfaceflinger/DisplayHardware/HWComposer_hwc1.cpp | head -20
echo "== module names"
grep -n "LOCAL_MODULE\b\|LOCAL_MODULE :=\|LOCAL_MODULE:=" hardware/libhardware/modules/gralloc/Android.mk hardware/libhardware/modules/hwcomposer/Android.mk
grep -n "eglSwapBuffers\|hwc_set\|HWC_DEVICE_API_VERSION\|HWC_HARDWARE_COMPOSER" hardware/libhardware/modules/hwcomposer/hwcomposer.cpp | head
echo "== direct /dev/ashmem users"
grep -rIl "/dev/ashmem" frameworks system bionic art hardware libcore external/skia external/libcxx 2>/dev/null | head -30
echo "== permissions xml"; ls frameworks/native/data/etc | grep -E "handheld|touchscreen"
echo "== external"; ls external | grep -iE "swift|mesa|angle|llvm|skia|chromium|webview"
ls external/chromium-webview/prebuilt 2>/dev/null
echo "== done"
} > $OUT 2>&1
wc -c $OUT; echo "已写入 $OUT"
