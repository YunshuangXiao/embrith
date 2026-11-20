# 第三方组件与引用项目

本文件按"实际使用情况"登记. 每个组件的许可证以上游为准, 本项目的 GPL-3.0-or-later 不改变它们的许可证.

## 1. 随发布包分发的二进制 (不入库, 由 host/tools/build.sh 或上游发布获得)

| 组件 | 位置 | 来源和版本 | 许可证 | 备注 |
|---|---|---|---|---|
| slirp4netns | 发布包 bin/slirp4netns | https://github.com/rootless-containers/slirp4netns/releases/tag/v1.3.7 (官方 armv7l 静态二进制, 未修改, 原文件名 slirp4netns-armv7l-v1.3.7) | GPL-2.0-or-later (内含 libslirp: BSD-3-Clause) | SHA-256 91b54c16e093208b40e93f8e13bcbaf932016b800895f245c1f62bd711c753f5 (与 host/bin/slirp4netns 一致). 对应源码: https://github.com/rootless-containers/slirp4netns/archive/refs/tags/v1.3.7.tar.gz . 分发安装包时必须同时提供该源码链接或把源码放进包里 |
| dropbear (dropbearmulti) | 发布包 bin/dropbearmulti | https://github.com/mkj/dropbear , 版本 DROPBEAR_VER 默认 2025.88, 打 host/tools/dropbear/embrith-auth.patch | MIT 风格 (见上游 LICENSE) | 由 host/tools/build.sh dropbear 现场下载源码并编译. 补丁是对上游的修改, 分发时要保留上游版权声明 |
| OpenSSH sftp-server | 发布包 bin/sftp-server | https://cdn.openbsd.org/pub/OpenBSD/OpenSSH/portable/ , 版本 OPENSSH_VER 默认 9.9p2 | BSD 和 ISC 类许可 (见上游 LICENCE) | 由 host/tools/build.sh sftp 现场下载源码并编译, 不含 openssl 和 zlib |
| Embrith 自有程序 | 发布包 bin/kdispd kutil kdetach ktrace kgest | host/src/ | GPL-3.0-or-later (见仓库 LICENSE 和各文件头的 SPDX) | kgest 动态链接 Kindle 自带的 liblipc.so.1, 见第 3 节 |

## 2. 系统镜像里的组件 (由 aosp/ 脚本构建或预编译, 随镜像分发)

| 组件 | 位置和来源 | 许可证 | 备注 |
|---|---|---|---|
| AOSP android-7.1.2_r36 | aosp/02-sync-source.sh 同步; aosp/patches/ 是对它的补丁 | 以各文件头部为准, 主体 Apache-2.0, 另有 BSD, GPL, LGPL, MIT 等 | aosp/patches/ashmem-dev.c 派生自 system/core/libcutils/ashmem-dev.c (Apache-2.0), 头部已写明 |
| SwiftShader (EGL / GLES 软件渲染库) | aosp/10-swiftshader.sh 用 NDK r15c 从 swiftshader-2018 源码交叉编译, 产物 libEGL_swiftshader.so 等放进 aosp/device/kindle/common/swiftshader/lib/ (不入库). 上游 https://swiftshader.googlesource.com/SwiftShader | Apache-2.0, 版权 Google Inc. 和 The ANGLE Project Authors (源码树 LICENSE.txt 和 AUTHORS.txt). 源码树 third_party/ 里另有 LLVM, Subzero, llvm-subzero (University of Illinois/NCSA 许可, 各自 LICENSE.TXT) 和 PowerVR_SDK (Imagination 的 SDK 最终用户许可协议) | 项目使用, 不上传源码和 NDK, 只上传说明 (获取方式见 aosp/sources/readme.md); 发布镜像时保留上游许可文本. 源码树拷自 RetyOS 的 sources/swiftshader-2018; RetyOS 记录的上游提交为 8e6ed2e35bce87eccd69569840a81c49669b6cb6, 含 Android 适配 (见 RetyOS docs/PROVENANCE.md). PowerVR_SDK 随该源码树存在, 在 SwiftShader 的 CMakeLists.txt 中只被示例程序 OGLES2HelloAPI 引用 (BUILD_SAMPLES); aosp/10-swiftshader.sh 用 -DBUILD_SAMPLES=OFF, 只编译 libEGL libGLESv2 libGLES_CM, 不编译其代码. 其源码不入库, 发布 release 只上传编译得到的镜像 (embrith.raw) |
| WebView (旧) com.android.webview | AOSP 自带, Chromium 52 | 以 AOSP 和 Chromium 源码树内许可文件为准 | - |
| WebView (新) com.embrith.webview | 由 Chromium 源码自行编译, 不入库: aosp/device/kindle/common/webview/webview.apk (本地文件, 由 aosp/device/kindle/common/webview/Android.mk 作为 WebViewGoogle 打进镜像, 模块名沿用旧名, 随发布包的 embrith.raw 分发). 源码 https://chromium.googlesource.com/chromium/src.git 标签 119.0.6045.194, 构建目标 system_webview_apk, 并应用补丁 aosp/device/kindle/common/webview/chromium-nogms.patch (去掉对 Google Play 服务客户端库的依赖; 补丁本身修改的是 BSD-3-Clause 的 Chromium 源码), GN 参数和构建步骤见同目录 readme. 用自有密钥签名 (CN=Embrith WebView), 证书 SHA-256 2ca861e5b829f5aba0be12b7abfae4f38f99ac47b1204e47ca6d855f638ce58e, 私钥不入库, 证书 base64 见 aosp/patches/webview-sig.txt | Chromium 源码为 BSD-3-Clause 等开源许可, 其 third_party/ 下各组件另有各自许可 (以源码树内许可文件为准); 构建时关闭了专有编解码器 (proprietary_codecs = false). apk 里不含 Google Play 服务库. 第三方许可汇总由 Chromium 生成 (about_credits.html), 另存为 aosp/device/kindle/common/webview/webview_licenses.notice (不入库), 经 Android.mk 的 LOCAL_NOTICE_FILE 交给 AOSP 的 NOTICE 机制, 编译后的 system/etc/NOTICE.html.gz 已含该汇总 (解压后约 12 MB, 含 WebViewGoogle 条目) | apk SHA-256 5158f50ab9e73241ae5534fde8fee9ea6d81d65e8e781a0750eb2cfe0445c902 (本次构建) |
| 字体 | Embrith 仓库不含字体文件. 镜像里的字体由 AOSP 构建树带入: aosp/device/kindle/common/kindle_common.mk 引用 frameworks/base/data/fonts, external/roboto-fonts, external/noto-fonts, external/google-fonts 下的 dancing-script, carrois-gothic-sc, coming-soon, cutive-mono | 以 AOSP 各字体目录里的 NOTICE 和 LICENSE 为准; 字体随镜像分发, 未在 Embrith 界面中单独列出, 也未特意指定 | 用户自己放在 /mnt/us/fonts 的字体 (设置项 FONT_FILE, SSH 页面用的 NotoSerifSC) 不由本项目分发 |
| 拼音词典 pinyin.tsv | aosp/device/kindle/common/home/assets/pinyin.tsv . 与 RetyOS 的 sources/stage4/minimal/assets/pinyin.tsv 逐字节相同 | Apache-2.0 (数据派生自 Android Pinyin IME, 作者见 PINYIN-AUTHORS.txt, 许可证文本见 PINYIN-LICENSE.txt) | 不是 MIT, 也不是本项目原创数据 |
| Lucide 图标 | aosp/device/kindle/common/home/res/drawable/ic_*.xml (44 个), 由 Lucide 的 svg 转换为 Android VectorDrawable, 用于桌面与设置界面; 使用处见 Icons.java, LogBoard.java. 上游 https://lucide.dev/ | ISC (Lucide Contributors); 其中派生自 Feather 的部分为 MIT (Cole Bemis). 许可证文本见本文件文末 | 项目使用, 随镜像分发. 转换前的 svg 文件 (47 个, 2026-11-18 直接从官网 https://lucide.dev/icons/ 下载) 保存在开发者本地 icons/ 目录, 不在本仓库. 按 lucide.dev/license 的 MIT (Feather) 清单核对, 本项目使用的 check, chevron-down, chevron-left, chevron-right, chevron-up, info, lock, minus, monitor, plus, power, search, x 共 13 个属于 Feather 派生, 其余按 ISC. |

## 3. 只在构建时使用, 不随发布包分发

| 组件 | 用法 | 许可证 | 备注 |
|---|---|---|---|
| FBInk (fbink) | 运行时由 kdispd 和 embrith.sh 调用设备上已有的 /mnt/us/libkh/bin/fbink , 本项目不携带 | GPL-3.0-or-later (上游 https://github.com/NiLuJe/FBInk) | 不分发, 只作为运行依赖 |
| Kindle 自带 liblipc.so.1 | 从设备拷到 host/tools/lib/ , 只用于 kgest 链接 (构建必需), 不上传设备; 运行时加载设备 /usr/lib/liblipc.so.1 | Amazon 固件文件, 非开源; 本项目不分发该文件 | 项目使用, 不入库, 只上传说明. 开发者自己从自己的 Kindle 拷 /usr/lib/liblipc.so.1. 发布包含编译后的 bin/kgest, 它动态链接该库, 运行时加载设备自带的 /usr/lib/liblipc.so.1, 发布包不含该库文件 |
| openlipc 头文件 | host/src/kgest.c 包含 openlipc.h (编译开关 KGEST_LIPC), 路径由 host/tools/build.sh 的 OPENLIPC_DIR 指定 (默认指向开发机本地目录) | MIT (上游 arkq/openlipc, 文档页 https://arkq.github.io/openlipc 写明 MIT license) | 只在编译时用头文件, 不分发其源码. 若把它的代码拷进仓库, 需要保留 MIT 版权声明. 使用的版本: arkq/openlipc master, 提交 91645e77b291d92926b4292e31b2fa470322d1c7 (2017-10-19, C++ linkage compatibility) |
| Zig, Podman, Ubuntu 16.04 镜像, Android NDK r15c | 编译工具 | 各自许可证 | 不分发. NDK r15c (Linux x86_64) 的下载方式见 aosp/sources/readme.md |

## 4. 引用项目 (参考了行为, 调用顺序或设计, 具体文件写在引用处的文件头部)

| 项目 | 许可证 | 引用位置 |
|---|---|---|
| KOReader https://github.com/koreader/koreader | AGPL-3.0 (见该项目 COPYING) | host/modules/ui/kindle_ui.sh, host/modules/power/sleep.sh, host/modules/core/settings.sh, container.sh, stop.sh, hwinfo.sh, host/modules/conf/models.conf, host/embrith.sh, host/src/kdispd.c, host/src/kgest.c, host/tools/build.sh |
| VeraDroid | GPL-3.0-or-later, Copyright (C) 2026 the VeraDroid contributors | host/modules/ui/kindle_ui.sh, host/modules/core/stop.sh, host/modules/lib/enter.sh |
| RetyOS | MIT, Copyright (c) 2026 RetyOs contributors (个别文件有自己的 SPDX, 如 ashmem_memfd.c 为 Apache-2.0) | aosp/device/kindle/common/kindleashmem/kindleashmem.c (只参考, 头部已写明), aosp/device/kindle/common/home/src/com/embrith/home/EmbrithIME.java , aosp/10-swiftshader.sh , 以及 pinyin.tsv (见第 2 节) |

RetyOS 的 MIT 许可证要求在副本或其实质性部分中保留版权声明和许可声明. 声明原文如下:

```
MIT License

Copyright (c) 2026 RetyOs contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Lucide 许可证声明

ISC 许可证要求在所有副本中保留版权声明和许可声明; Feather 派生部分的 MIT 许可证要求在副本或其实质性部分中保留声明. 声明原文如下:

```
ISC License

Copyright (c) 2026 Lucide Icons and Contributors

Permission to use, copy, modify, and/or distribute this software for any
purpose with or without fee is hereby granted, provided that the above
copyright notice and this permission notice appear in all copies.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.

The MIT License (MIT) (for portions derived from Feather)

Copyright (c) 2013-present Cole Bemis

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
