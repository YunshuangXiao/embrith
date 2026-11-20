# Kindle Colorsoft (KC) 共用镜像适配

本目录是 KC 适配说明和验证入口，不是独立的 AOSP lunch target。
继续使用 `kindle_pw6-userdebug` 构建同一镜像：桌面、HWC、init 和 ARM32 用户空间共用，避免复制一套设备树后分叉。现有 `pw6` 名称保留以兼容打包脚本、`ro.hardware` 和 HAL 加载路径。

## 自动选择时机

每次启动 `kdispd` 时读取 `/dev/fb0` 的 FBIOGET_VSCREENINFO / FBIOGET_FSCREENINFO：

| 条件 | 路径 | 共享内存 | 彩色开关 |
| --- | --- | --- | --- |
| 8bpp | 原有灰度路径（PW6） | 4096 + W×H | 隐藏 |
| hwtcon、32bpp、RGB 偏移 0/8/16，各 8 位 | KC RGBA8888 | 4096 + W×H×5 | 显示 |
| 其他布局 | 明确拒绝并记录日志 | 不启动显示 | 不适用 |

不在安装时保存 KC/PW6 选择，不根据分辨率猜机型。型号表只提供 DPI；显示格式以运行时 ioctl 为准。相同安装包可用于 PW6 和 KC。无需替换 HAL 或对系统镜像做设备专用 bind mount；将共用 HWC 和主机程序一起重新构建发布即可。旧 HWC 只能输出灰度，启用 KC 彩色需要新版 HWC。

## 协议与设置

协议 v1 保留原有灰度平面和字段。`KDISP_FLAG_RGBA_PLANE` 表示后接 W×H×4 RGBA8888 平面，同一个 seq/ack 保护两者。没有该标记时 HWC 保持旧灰度行为。彩色帧按 RGB 比较脏区，避免等亮度的不同颜色被遗漏。

主机写入 `ctl/color_supported`，桌面只在能力为 1 时显示“设置 → 显示 → 屏幕效果 → 彩色显示（KC）”。`COLOR=1/0` 随 display.conf 持久化；PW6 忽略彩色设置。彩色模式保留源 RGB；灰度模式沿用对比度、抖动和背景过滤。当前截图仍为灰度，彩色路径暂不应用灰度专用过滤。

## 刷新与翻页动画

KC 普通刷新使用 AUTO=257，全屏重绘提交 FULL。翻页动画与 PW6 共用 waveform=13、ENABLE_SWIPE=0x10000、方向和步长；彩色路径也处理翻页提示、应用排除和防重复触发。ioctl 失败时回退普通刷新。驱动返回成功不能替代实屏验证。

KC 停止原生框架时还停止独立的 `x` job，只在本次确实停止过它时恢复，并先于 lab126_gui 恢复。

## 验证范围

已在 KC、固件 5.20.1 上验证：Android 启动、彩色输出、黑白/彩色切换、手势、两个模式的翻页动画，用户已确认实屏正常。PR 整理后的运行时自动检测已在 KC 上启动验证：日志为 kc-rgba8888、color_supported=1，共享帧 10790656 字节，Android 和桌面进程正常。未在 PW6 实机回归；保留原有 8bpp 路径并通过双模式共享帧测试，不能宣称已完成 PW6 实测。

`validation/frame_sink_test.cpp` 验证灰度兼容、RGB 主色、等亮度颜色变化、未变帧抑制及脏区合并。可在 Linux 使用 C++11 编译运行，也可静态交叉编译后在 KC 的可执行 tmpfs 中运行。不要把发布二进制、签名文件、用户镜像或设备日志加入 PR。
