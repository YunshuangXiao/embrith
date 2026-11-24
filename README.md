Embrith · 余烬 | Kindle 定制安卓系统
=================================

[English](README_EN.md) | **简体中文**

<table>
<tr>
<td valign="middle"><img src="https://raw.githubusercontent.com/yunshuangxiao/embrith/main/gh-pages/refs/logos/embrith_logo_horizontal.png" height="80" alt="Embrith"></td>
<td valign="middle">项目名来源: Embers and Rebirth<br>✨✨ 星火未灭, 余烬重生 ✨✨</td>
<td valign="middle"><a href="https://yunshuangxiao.github.io/embrith/"><img src="https://img.shields.io/badge/docs-online-blue?cacheSeconds=3600" alt="Docs"></a> <a href="LICENSE"><img src="https://img.shields.io/github/license/yunshuangxiao/embrith?cacheSeconds=3600" alt="License"></a><br>[ <a href="https://yunshuangxiao.github.io/embrith/"><b>主页</b></a> ] [ <a href="https://github.com/yunshuangxiao/embrith"><b>源码</b></a> ] [ <a href="https://github.com/yunshuangxiao/embrith/issues"><b>报错</b></a> ] [ <a href="https://github.com/yunshuangxiao/embrith/discussions"><b>讨论</b></a> ]</td>
</tr>
</table>

在越狱的 Kindle 上运行自编译的 Android 7.1.2 容器, 把墨水屏变成可以装 apk, 也能好好读书的安卓设备.

- 安卓内核: Android 7.1.2_r36 (core_minimal)
- 测试设备: Kindle Paperwhite 6 (MT8512, 32 位 armv7l, 约 980 MB 内存, 1272x1696 灰度墨水屏, **已越狱**)
- 设备固件: 5.20.1

---

## 功能

### 安装与运行安卓应用

- 自编译 Android 7.1.2 容器, 从 Kindle 存储中任选 apk 安装.
- 可选 dex2oat 编译加快应用启动 (很占内存, 一次一个应用).
- 选中的 Kindle 目录可只读共享到安卓内 (/data/media/0/Kindle); 不能共享整个存储, 需进入文件夹后选择.
- 运行 Embrith 时可通过 MTP 在电脑上传输文件.
- 支持新版网页内核 (Chromium 119).

<p>
<img src="https://raw.githubusercontent.com/yunshuangxiao/embrith/main/gh-pages/refs/shots/cn/settings_system.png" width="31%">
<img src="https://raw.githubusercontent.com/yunshuangxiao/embrith/main/gh-pages/refs/shots/cn/settings_shared_folders.png" width="31%">
<img src="https://raw.githubusercontent.com/yunshuangxiao/embrith/main/gh-pages/refs/shots/cn/net_mtp.png" width="31%">
</p>

### 桌面, 书库与阅读

- 简洁桌面: 应用网格, 组件 (屏幕时间 日 / 周, 图片展示, 文字展示, 时钟, 应用快捷方式, 快捷按钮), Kindle 原生风格状态栏. 桌面和设置首页上方的组件区按槽位排布, 提供整屏, 通栏, 半屏竖条, 半宽等多种尺寸, 可设置各尺寸的个数与摆放顺序.
- 时钟组件: 多种表盘 (简约刻度, 数字 1-12, 罗马数字, 极简, 圆点刻度) 和数字时钟, 可选时区.
- 底部栏可自选页面并排序: 统计 / 打卡 / 概览 / 主页 / 阅读 / 书库 / 设置.
- 书库: 选择书籍文件夹后只显示这些文件夹, 支持封面网格或列表, 每行列数 / 每屏行数, 无封面时显示书名, 排序 (含倒序, 文件夹在前) 与分组, 清空最近记录, 清除封面缓存.
- 阅读页: 显示正在阅读与最近阅读, 点击封面继续阅读.
- 支持的格式: txt, epub, mobi, azw, azw3, kfx (不带 DRM); 漫画 cbz, cbt (含由图片页组成的 epub / mobi / azw3). cbr, cb7 会在书库中列出, 但暂不支持阅读, 需先转成 cbz. 不支持 pdf.
- 章节目录: epub 按目录 (含同一文件内的锚点) 切分章节, 目录不足时按正文标题行识别; txt 自动识别章节标题 (中文章节词, 英文 chapter / part 等, 编号行, 版面), 也可在 设置 > 书库 中填写自定义正则.
- 阅读: 段首的空格不再叠加, 统一按设置里的首行缩进显示.

<p>
<img src="https://raw.githubusercontent.com/yunshuangxiao/embrith/main/gh-pages/refs/shots/cn/home_slots.png" width="31%">
<img src="https://raw.githubusercontent.com/yunshuangxiao/embrith/main/gh-pages/refs/shots/cn/overview.png" width="31%">
<img src="https://raw.githubusercontent.com/yunshuangxiao/embrith/main/gh-pages/refs/shots/cn/library_folders.png" width="31%">
</p>
<p>
<img src="https://raw.githubusercontent.com/yunshuangxiao/embrith/main/gh-pages/refs/shots/cn/settings_library.png" width="31%">
<img src="https://raw.githubusercontent.com/yunshuangxiao/embrith/main/gh-pages/refs/shots/cn/settings_library_sort.png" width="31%">
<img src="https://raw.githubusercontent.com/yunshuangxiao/embrith/main/gh-pages/refs/shots/cn/reader_comic.png" width="31%">
</p>

### 个性化

- 底部栏页面与顺序可调整; 桌面和设置首页的组件区在槽位编辑里摆放: 选一个组件, 再点预览里的格子放置, 点已放的组件可以移动.
- 书库位置, 最近阅读显示, 页面布局, 排序方式等可单独设置.
- 桌面壁纸与锁屏图片: 纯白, 从 Kindle 选择单张图片, 或使用 Embrith 壁纸文件夹随机切换; 可设置褪色度和文件夹切换时机; 选图列表显示缩略预览.

<p>
<img src="https://raw.githubusercontent.com/yunshuangxiao/embrith/main/gh-pages/refs/shots/cn/widget_slot_editor.png" width="31%">
<img src="https://raw.githubusercontent.com/yunshuangxiao/embrith/main/gh-pages/refs/shots/cn/widget_slot_selected.png" width="31%">
<img src="https://raw.githubusercontent.com/yunshuangxiao/embrith/main/gh-pages/refs/shots/cn/settings_wallpaper.png" width="31%">
</p>
<p>
<img src="https://raw.githubusercontent.com/yunshuangxiao/embrith/main/gh-pages/refs/shots/cn/wallpaper_pick_picture.png" width="31%">
<img src="https://raw.githubusercontent.com/yunshuangxiao/embrith/main/gh-pages/refs/shots/cn/home_widgets_wallpaper.png" width="31%">
<img src="https://raw.githubusercontent.com/yunshuangxiao/embrith/main/gh-pages/refs/shots/cn/settings_home_widgets.png" width="31%">
</p>

### 阅读统计与打卡

- 概览: 最近阅读, 今日目标 (阅读时长 / 应用时长), 本周小结.
- 打卡: 累计打卡 / 最长连续 / 当前连续, 按日历查看, 可修改每日目标, 底部按月柱状图显示每月打卡成功的天数.
- 统计: 按月日历热力图, 点选日期查看当天各应用时长, 本周 / 本月 / 全年阅读时长.

<p>
<img src="https://raw.githubusercontent.com/yunshuangxiao/embrith/main/gh-pages/refs/shots/cn/stats_calendar.png" width="31%">
<img src="https://raw.githubusercontent.com/yunshuangxiao/embrith/main/gh-pages/refs/shots/cn/stats_day_detail.png" width="31%">
<img src="https://raw.githubusercontent.com/yunshuangxiao/embrith/main/gh-pages/refs/shots/cn/checkin.png" width="31%">
</p>

### 触摸与手势

- 固定手势: 顶边右半下滑 = 下拉面板, 底边左半上滑 = 回到桌面, 底边右半上滑 = 返回, 按住左上角 3 秒 = 弹出 重启 / 退出菜单 (重启 Embrith, 退出 Embrith, 重启 Kindle; Android 无响应时仍会退出), 两指同时按对角 = 截图.
- 可配置手势: 单指 / 双指的点按与滑动, 动作与快捷按钮使用同一套选择: 刷新屏幕(去残影), 反色, 返回, 回到桌面, 前光 (开关 / 设为 / 调亮 / 调暗), 色温 (设为 / 调暖 / 调冷), Wi-Fi 开关, 打开状态栏, 打开设置, 休眠, 截图, 上一页, 下一页, 切换应用(最近任务) 等. 滑动类手势另有 调节前光亮度 / 调节色温, 可用于双指上下滑 (左半屏, 右半屏) 和单指沿左右边缘上下滑, 滑动过程中实时变化, 变化量随滑动距离增大.
- 一键恢复默认手势.
- 快捷按钮 (下拉面板的自定义按钮和设置首页组件共用同一套动作): 前光 (设为 / 调亮 / 调暗 / 开关), 色温, Wi-Fi, 蓝牙, 飞行模式, 反色, 刷新, 截图, 休眠, 重启, 弹出 USB 等动作可自选, 带数值的动作可设数值, 开关类按钮显示当前状态; 页面悬浮按钮的位置可拖动调整.
- 软键盘: 增加编辑行 (选择, 全选, 复制, 剪切, 粘贴).

<p>
<img src="https://raw.githubusercontent.com/yunshuangxiao/embrith/main/gh-pages/refs/shots/cn/settings_gestures.png" width="31%">
<img src="https://raw.githubusercontent.com/yunshuangxiao/embrith/main/gh-pages/refs/shots/cn/widget_text_edit_keyboard.png" width="31%">
</p>

### 显示与刷新

- 显示大小, 系统字体 (可从 Kindle 选择 ttf / otf), 字体大小.
- 色温设置, 反色显示, 全局对比度, 抖动.
- 阅读背景过滤: 为选定的应用把背景图案/颜色过滤为纯白, 并可调阈值.
- 局部刷新, 自动全屏刷新, 自定义翻页动画步长, 可为指定应用关闭翻页动画, 整屏重绘.

<p>
<img src="https://raw.githubusercontent.com/yunshuangxiao/embrith/main/gh-pages/refs/shots/cn/settings_display.png" width="31%">
<img src="https://raw.githubusercontent.com/yunshuangxiao/embrith/main/gh-pages/refs/shots/cn/settings_display_effects.png" width="31%">
</p>

### 网络与连接

- 系统内连接新 Wi-Fi, 蓝牙连接已配对设备.
- DNS 预设 (自动 / 阿里 / 腾讯 / 谷歌 / 自定义).
- SSH (内置 dropbear, 密码与密钥登录) 和 SFTP (OpenSSH sftp-server), 带 Kindle 端 SSH 页与会话清理; Embrith 与 KOReader 两个来源可同时开启, 端口不同即可. 提供 Booklet 启动脚本, 可以在不启动 Embrith 的情况下启用 SSH 连接.
- 支持 MTP 连接, 弹出连接即可恢复使用. MTP 连接期间, Embrith 读不到 Kindle 数据.

<p>
<img src="https://raw.githubusercontent.com/yunshuangxiao/embrith/main/gh-pages/refs/shots/cn/settings_network.png" width="31%">
<img src="https://raw.githubusercontent.com/yunshuangxiao/embrith/main/gh-pages/refs/shots/cn/network_ssh.png" width="31%">
</p>

### 电源与系统

- 立即休眠, 睡眠定时器, 重启 Embrith, 重启 Kindle, 重启并更新, 退出回到 Kindle.
- 设备名称, 时区, 24 小时制, 语言 (中文 / 英文), 应用管理, 内存查看与释放后台应用.
- 高级与兼容性: 新版 WebView, dex2oat 优化, 共享文件夹, 开发者模式.

<p>
<img src="https://raw.githubusercontent.com/yunshuangxiao/embrith/main/gh-pages/refs/shots/cn/settings_power.png" width="31%">
<img src="https://raw.githubusercontent.com/yunshuangxiao/embrith/main/gh-pages/refs/shots/cn/settings_about.png" width="31%">
<img src="https://raw.githubusercontent.com/yunshuangxiao/embrith/main/gh-pages/refs/shots/cn/settings_timezone.png" width="31%">
</p>
<p>
<img src="https://raw.githubusercontent.com/yunshuangxiao/embrith/main/gh-pages/refs/shots/cn/settings_language.png" width="31%">
</p>

### 可靠性

- 安全更新: 提供更新包校验, 新包启动失败可回滚.
- 本地更新: 提供校验更新和强制覆盖更新.
- 在线更新: 从 GitHub 下载最新 release 更新.
- 日志报告: `embrith.sh report [分钟数]` 或 设置 > 系统 > 生成日志, 日志可在列表中查看与删除; 日志默认写内存 (DEV_MODE=1 才写闪存).

<p>
<img src="https://raw.githubusercontent.com/yunshuangxiao/embrith/main/gh-pages/refs/shots/cn/settings_logs.png" width="31%">
</p>

### 其他

- 支持硬键盘, 以及屏幕中英文输入法 (EmbrithIME: 离线拼音).

## 状态

已在 Kindle PW6 上测试: 容器开机与桌面, 休眠与唤醒, 应用安装, SSH, SFTP, 网络 (slirp4netns), 刷新引擎, Wi-Fi, WebView, USB 页, 中英文界面, 截图手势, 系统字体, DNS 设置, 桌面组件排版, dex2oat 调优, 本地文件阅读...

不提供: 音频与视频播放.

## 安装

1. 把发布包内容 (src/) 复制到 Kindle 的 /mnt/us/embrith/src/.
2. 把 documents/ 里的脚本复制到 /mnt/us/documents/.
3. 在 Kindle 书库里运行 "Embrith Starter", 它会把通过校验的安装包装到 /mnt/us/system/embrith/ (保留上一版用于回滚), 然后启动安卓.
4. 退出: 在安卓桌面点右上角电源按钮选择退出, 或按住左上角 3 秒.

命令行 (SSH 或 shell): `embrith.sh start|stop|restart|status|log|report [minutes]|logcat|shot|disptest|fbdiag|icons|shell [cmd]|install <apk>|launch <package>`.

## 从源码编译

- aosp/: AOSP 补丁, 设备树, 编译脚本 (Linux, Podman 编译容器; AOSP 源码本身和三方源码如 `android-ndk-r15c`, `swiftshader-2018` 不在本仓库.)
- host/: Kindle 端 (shell 脚本, C 源码; 由 host/tools/build.sh 用 Zig 交叉编译)
<!-- - docs/: 项目规则, 架构, 协议, 代码索引, 编译流程 (docs/WORKFLOW.md) -->

> aosp/ 与 host/ 解耦. 二进制文件不入库.

## 版本说明

早期版本的源码与 releases 含有未获授权的第三方(墨卓)源码, 未获授权再分发的 WebView 预编译包 (`com.google.android.webview`, Chromium 119.0.6045.194, 取自 APKMirror), 和未详列的第三方授权/许可证信息, 这些内容已不再公开.

自 v4.0.0 起, Embrith 重构了源码及部分功能的实现, 并补充了代码来源信息与相应的许可/版权说明 (见 third_party/README.md).

如果你曾下载过早期版本的源码或 releases, 请删除这些文件, 不要继续使用或转发, 并改用本仓库的最新版本.

如发现来源, 署名或许可证方面仍有遗漏, 请通过 [Issues](https://github.com/YunshuangXiao/embrith/issues) 告知, 我会尽快核实并处理.

## 合并的外部贡献

- Kindle Colorsoft (KC) 显示适配 (RGBA 显示后端, 彩色显示开关, 翻页动画等), 由 GitHub 用户 Emokable 以 pull request 贡献并合并 (v3.1.0). 涉及 aosp/device/kindle/kc/ 和 PR-KC.md (该 pull request 的说明), 以及显示协议, 显示守护进程和桌面设置中的相关改动.

## 致谢

思路与技术参考了 KOReader, RetyOS, VeraDroid. 第三方组件: AOSP, Chromium (WebView), fbink, dropbear, OpenSSH (sftp-server), slirp4netns, SwiftShader, openlipc (仅编译时使用其头文件). 图标: Lucide. 拼音词典: Android Pinyin IME (数据经 RetyOS 获得).

## AI 辅助开发

Embrith 由作者主导需求与方向, 大部分代码草稿, 文档和分析由 Claude (Anthropic) 在对话中生成. 作者负责提出需求, 采纳改动, 并在真机上测试行为; 代码没有逐行审查, 未上机验证的内容标为实验性. 每次改动会对照已知的参考项目和第三方许可证做来源核查, 但 AI 生成的代码仍可能与现有开源代码相似. 如果你发现来源, 署名或许可证方面的遗漏, 请在 [issues](https://github.com/YunshuangXiao/embrith/issues) 反馈, 我会尽快核实处理.

## 商标声明

Kindle 和 Amazon 是 Amazon.com, Inc. 或其关联公司的商标, Android 是 Google LLC 的商标. Embrith 是独立的开源项目, 与 Amazon, Google 没有隶属, 赞助或认可关系. 文中提到这些名称, 只是为了说明所适配的设备和系统.

## 许可证

GPL-3.0-or-later, 见 LICENSE 文件. 引用项目和第三方组件的许可证见 third_party/README.md.

## 支持 Embrith · 余烬

如果您觉得 Embrith 很有用, 也考虑支持其开发.

[![Sponsor](https://img.shields.io/badge/Sponsor-Embrith-pink?logo=githubsponsors)](https://paypal.me/embrith)



[embrith-homepage]: https://yunshuangxiao.github.io/embrith/
[embrith-src]: https://github.com/yunshuangxiao/embrith/