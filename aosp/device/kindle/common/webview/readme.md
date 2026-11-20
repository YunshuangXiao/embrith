# 新版 WebView 预编译包 (webview.apk) 说明

本目录的 Android.mk 需要 webview.apk. 该文件不入库 (.gitignore 已忽略 *.apk), 编译镜像前请自行放到本目录.
缺少该文件时, 编译会报错 "webview/webview.apk is missing".

## 来源

由 Chromium 源码自行编译, 不使用 Google 签名的发布包.

- 包名: com.embrith.webview (Android System WebView 的自编构建)
- 版本: Chromium 119.0.6045.194 (源码标签 119.0.6045.194), armeabi-v7a, minAPI 24
- 构建目标: system_webview_apk
- 签名: 自有密钥 (证书 CN=Embrith WebView), 私钥不入库
- 证书 SHA-256: 2ca861e5b829f5aba0be12b7abfae4f38f99ac47b1204e47ca6d855f638ce58e
- 本次构建的 apk SHA-256: 5158f50ab9e73241ae5534fde8fee9ea6d81d65e8e781a0750eb2cfe0445c902

GN 参数 (out/webview/args.gn):

```
target_os = "android"
target_cpu = "arm"
is_debug = false
is_official_build = true
is_component_build = false
symbol_level = 0
blink_symbol_level = 0
dcheck_always_on = false
treat_warnings_as_errors = false
proprietary_codecs = false
ffmpeg_branding = "Chromium"
use_official_google_api_keys = false
system_webview_package_name = "com.embrith.webview"
```

## 构建方法

环境: Linux x64, 100 GB 以上可用磁盘, 16 GB 以上内存更稳妥. 本次在 Ubuntu 22.04 容器 (Podman) 中构建.

```
fetch --nohooks --git-cache android
cd src
git checkout -b webview119 refs/tags/119.0.6045.194
gclient sync -D --force --reset --nohooks --with_branch_heads --with_tags
git apply <repo>/aosp/device/kindle/common/webview/chromium-nogms.patch
build/install-build-deps.sh --android --no-prompt
gclient runhooks
gn gen out/webview
ninja -j6 -C out/webview system_webview_apk
```

注意:

- chromium-nogms.patch 是对 Chromium 源码的补丁, 去掉 WebView 对 Google Play 服务客户端库的依赖 (定位, 形状检测, 短信验证码三处改为不使用 Play 服务), 要在 gclient sync 之后, 编译之前应用. 补丁只改 Java 和 BUILD.gn, 不改原生代码.
- 在 rootless Podman 容器里以 root 运行时, 解压 sysroot 的 tar 会因无法保留属主而失败, 需要让 tar 使用 --no-same-owner.
- 新版 depot_tools 的 autoninja 默认使用 siso, 本地编译请直接用 ninja.
- 产物为 out/webview/apks/SystemWebView.apk.

## 签名

用 Chromium 源码树里的 apksigner 和自己的 keystore 重新签名 (不要使用源码树自带的公开调试密钥):

```
apksigner sign --ks <keystore> --ks-key-alias <alias> --out webview.apk SystemWebView.apk
apksigner verify -v --print-certs webview.apk
```

换用其他密钥时, 需要同步更新 aosp/patches/webview-sig.txt (证书的 base64), 否则框架不会接受该 WebView.

## 第三方许可汇总

Chromium 的第三方组件许可汇总不在 apk 里, 构建时单独生成为 out/webview/gen/components/resources/about_credits.html. 如果没有该文件, 先生成:

```
ninja -j6 -C out/webview components/resources:about_credits
```

把它另存为本目录的 webview_licenses.notice (这是 Chromium 给 AOSP 用的文件名):

```
cp out/webview/gen/components/resources/about_credits.html <this dir>/webview_licenses.notice
```

Android.mk 通过 LOCAL_NOTICE_FILE 把它交给 AOSP 的 NOTICE 机制, 随镜像分发. 该文件不入库 (.gitignore 已忽略 *.notice), 缺少时编译会报错 "webview/webview_licenses.notice is missing".

## 使用方法

1. 按上面的方法得到 apk, 重命名为 webview.apk, 放到本目录 (与 Android.mk 同级); 同时放入 webview_licenses.notice (见上一节).
2. 照常编译镜像. kindle_common.mk 通过 `PRODUCT_PACKAGES += WebViewGoogle` 把它打进 system 分区, 与 AOSP 自带的旧版 WebView (Chromium 52) 并存. 运行时由 ctl/conf 的 WEBVIEW 选项决定启用哪一个.

## 许可说明

- Chromium 是开源项目, 自行编译的产物不涉及 Google 签名包的再分发问题.
- 已用 chromium-nogms.patch 去掉对 Google Play 服务客户端库的依赖 (它们适用 Google 自己的 Android SDK 许可协议, 不是 Chromium 的许可). 构建后 apk 的 classes.dex 里不含 com.google.android.gms 字符串. 代价: 定位只走 Android 自带的位置服务, 条码与文字检测不可用, 人脸检测用 Android 自带实现, 短信验证码自动填充不可用.
- apk 内含 Chromium 的其他第三方组件, 各自许可见 Chromium 源码树 third_party/ 下各目录; 许可汇总见上一节 (webview_licenses.notice). 它已通过 LOCAL_NOTICE_FILE 被 AOSP 收进镜像的 NOTICE (/system/etc/NOTICE.html.gz). 核对方法: 编译后 gzip -dc out/target/product/pw6/system/etc/NOTICE.html.gz, 应约 12 MB 且含 WebViewGoogle 条目.
- 未启用专有编解码器 (proprietary_codecs = false).
