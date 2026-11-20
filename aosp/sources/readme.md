# aosp/sources 说明

本目录存放编译 SwiftShader 所需的第三方源码和工具. 它们体积大, 不入库 (.gitignore 已忽略下面两个子目录), 编译前请自行准备. 许可证和是否分发, 见 third_party/README.md.

## android-ndk-r15c

- 用途: 只在主机上交叉编译 SwiftShader (aosp/10-swiftshader.sh), 不进镜像, 不分发.
- 版本: Android NDK r15c (source.properties 中 Pkg.Revision = 15.2.4203891).
- 下载 (Linux x86_64 版, 与 WSL 编译环境一致): https://dl.google.com/android/repository/android-ndk-r15c-linux-x86_64.zip
- 放置: 解压后放到 aosp/sources/android-ndk-r15c/.

## swiftshader-2018

- 用途: 编译得到 libEGL, libGLESv2, libGLES_CM 的 SwiftShader 版本, 供新版 WebView 使用.
- 来源: 拷自 RetyOS 项目 https://github.com/Emokable/RetyOs 的 sources/swiftshader-2018/.
- 上游提交: 8e6ed2e35bce87eccd69569840a81c49669b6cb6 (RetyOS 记录, 含 Android 适配, 含 LLVM 和 Subzero 源码).
- 放置: 放到 aosp/sources/swiftshader-2018/.

## 编译 SwiftShader

在 aosp/ 目录下运行, 必须在 05-apply.sh 之前:

```
SS_SRC=$PWD/sources/swiftshader-2018 NDK=$PWD/sources/android-ndk-r15c bash 10-swiftshader.sh
```

产物 libEGL_swiftshader.so 等放进 aosp/device/kindle/common/swiftshader/lib/ (不入库). 缺少这些文件时, 编译镜像会报错.