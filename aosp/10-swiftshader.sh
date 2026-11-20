#!/bin/bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Embrith contributors
# 交叉编译 SwiftShader 的 EGL / GLES 库 (armeabi-v7a), 产物放进 device/kindle/common/swiftshader/lib/. 在 WSL 宿主里运行.
# 用法: SS_SRC=<swiftshader 源码目录> NDK=<android-ndk-r15c 目录> bash 10-swiftshader.sh
# 源码和 NDK 都不进仓库. CMake 参数照 RetyOS 的 build-swiftshader-arm.ps1. 需要 cmake 和 ninja.
# Referenced project: RetyOS, MIT, Copyright (c) 2026 RetyOs contributors. File consulted:
#   scripts/build-swiftshader-arm.ps1        the CMake arguments (MIT notice: see third_party/README.md)
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
SS_SRC=${SS_SRC:?"需要 SS_SRC (SwiftShader 源码目录)"}
NDK=${NDK:?"需要 NDK (android-ndk-r15c 目录)"}
BUILD=${BUILD_DIR:-$HOME/swiftshader-arm-build}
OUT=$HERE/device/kindle/common/swiftshader/lib
[ -f "$SS_SRC/CMakeLists.txt" ] || { echo "找不到 $SS_SRC/CMakeLists.txt"; exit 1; }
[ -f "$NDK/build/cmake/android.toolchain.cmake" ] || { echo "找不到 $NDK/build/cmake/android.toolchain.cmake"; exit 1; }
for t in cmake ninja; do command -v $t >/dev/null || { echo "缺少 $t"; exit 1; }; done

cmake -S "$SS_SRC" -B "$BUILD" -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=armeabi-v7a -DANDROID_PLATFORM=android-24 -DANDROID_STL=c++_static \
  -DCMAKE_BUILD_TYPE=Release -DCMAKE_POLICY_VERSION_MINIMUM=3.5 \
  -DREACTOR_BACKEND=Subzero -DBUILD_SAMPLES=OFF -DBUILD_TESTS=OFF -DUSE_GROUP_SOURCES=OFF
cmake --build "$BUILD" --target libEGL libGLESv2 libGLES_CM --parallel "$(nproc)"

mkdir -p "$OUT"
for f in libEGL_swiftshader libGLESv1_CM_swiftshader libGLESv2_swiftshader; do
  [ -f "$BUILD/$f.so" ] || { echo "没有生成 $BUILD/$f.so"; exit 1; }
  cp "$BUILD/$f.so" "$OUT/"
done
ls -l "$OUT"
echo "完成. 接着运行 05-apply.sh 并重编镜像."
