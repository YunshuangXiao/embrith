/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * English: English texts of the Embrith UI: a table from the Chinese source strings to English. */
package com.embrith.language;

import java.util.HashMap;
import java.util.Map;

/**
 * English texts of the Embrith UI: Chinese source string to English.
 * Keys are the Chinese strings of the source code with CJK punctuation converted to ASCII before lookup:
 * U+FF0C and U+3001 to ',' ; U+FF1B to ';' ; U+FF1A to ':' ; U+3002 to '.' ; U+FF08 to '(' ; U+FF09 to ')' ;
 * U+FF1F to '?' ; U+FF01 to '!' ; U+FF5E to '~' ; U+FF0B to '+' ; U+FF0D to '-' ; U+2026 to '...'.
 * Entries marked fragment are pieces of a text joined in code, so a call site has to translate each piece on its own.
 */
public final class English {
    private static final Map<String, String> MAP = new HashMap<String, String>(1024);

    private English() { }

    /** English text for a Chinese source string (already normalized), or null when the table has none. */
    public static String get(String zh) {
        return MAP.get(zh);
    }

    private static void p(String zh, String en) {
        MAP.put(zh, en);
    }

    static {
        // ---- AmberScheduleActivity
        p("色温设置", "Color temperature settings");
        p("亮度", "Brightness");
        p("不可用", "Unavailable");
        p("自动调整亮度", "Auto brightness");
        p("色温", "Color temperature");
        p("色温启动时间设置", "Color temperature schedule");
        p("开始时间", "Start time");
        p("结束时间", "End time");
        p("开启后,在开始和结束时间之间使用时段色温;其余时间(或关闭时)使用上面的默认色温.开始晚于结束表示跨过午夜(如 21:00 到次日 07:00).", "When on, the schedule color temperature is used between the start and end times; the default color temperature above is used the rest of the time (or when off). A start time later than the end time means the period crosses midnight (for example 21:00 to 07:00 the next day).");
        p("上午", "AM");
        p("下午", "PM");
        p("好", "OK");
        p("请输入有效时间 (0 到 23 时, 0 到 59 分)", "Enter a valid time (hours 0 to 23, minutes 0 to 59)");
        p("请输入有效时间 (1 到 12 时, 0 到 59 分)", "Enter a valid time (hours 1 to 12, minutes 0 to 59)");
        p("取消", "Cancel");
        p("关", "Off");
        // ---- AppsActivity
        p("应用管理", "Manage apps");
        p("卸载", "Uninstall");
        p("没有自己安装的应用", "No user-installed apps");
        // ---- DexoptActivity
        p("正在优化应用 (dex2oat)", "Optimizing apps (dex2oat)");
        p("编译期间已停掉其他应用, 请不要操作, 完成后自动返回", "Other apps were stopped during compilation. Do not touch the screen; this page closes by itself when done");
        p("出错: ", "Error: "); // fragment
        p("准备中...", "Preparing...");
        // ---- UsbActivity
        p("USB 已连接", "USB connected");
        p("电脑正通过 MTP 访问 Kindle 的存储, 现在可以传输文件.\nEmbrith 仍在运行. 传输完成后点\"弹出连接\", 或直接拔线.",
                "The computer is accessing the Kindle storage over MTP; files can be transferred now.\nEmbrith keeps running. Tap \"Eject\" when done, or unplug the cable.");
        p("警告", "Warning");
        p("不要修改, 移动或删除 system 文件夹里的任何内容.\nEmbrith 的系统镜像和数据在其中, 运行期间改动会损坏数据.",
                "Do not change, move or delete anything in the system folder.\nIt holds the Embrith system image and data; changes while running corrupt the data.");
        p("弹出连接", "Eject");
        // ---- GestureActivity
        p("无", "None");
        p("刷新屏幕(去残影)", "Refresh screen (remove ghosting)");
        p("反色", "Invert");
        p("返回", "Back");
        p("回到桌面", "Go to desktop");
        p("前光开/关", "Front light on/off");
        p("调节前光亮度", "Adjust front light");
        p("调节色温", "Adjust color temperature");
        p("Wi-Fi 开/关", "Wi-Fi on/off");
        p("下拉状态栏", "Pull-down status bar");
        p("打开设置", "Open settings");
        p("休眠", "Sleep");
        p("截图", "Screenshot");
        p("双指轻点", "Two-finger tap");
        p("双指长按 2 秒", "Two-finger press for 2 s");
        p("双指左右滑", "Two-finger swipe left or right");
        p("双指上下滑(左半屏)", "Two-finger swipe up or down (left half)");
        p("双指上下滑(右半屏)", "Two-finger swipe up or down (right half)");
        p("右上角长按 2 秒", "Press top-right corner for 2 s");
        p("左下角长按 2 秒", "Press bottom-left corner for 2 s");
        p("右下角长按 2 秒", "Press bottom-right corner for 2 s");
        p("手势", "Gestures");
        p("启用手势", "Enable gestures");
        p("自定义手势", "Custom gestures");
        p("恢复默认手势", "Restore default gestures");
        p("设了动作后, 贴边起滑的触摸不再传给应用; 没设动作的边缘不受影响.", "Once an action is set, a touch that starts at that edge is no longer passed to the app; edges without an action are not affected.");
        p("角落轻点和双击不拦截触摸, 应用同时也会收到. 一次双击会先触发一次轻点的动作.", "Corner taps and double taps do not block the touch, the app receives it too. A double tap first runs the action of a tap.");
        p("双指上下滑(整屏)只在左右半屏的设置为无时使用.", "Two-finger swipe (whole screen) is used only when the left and right half settings are none.");
        p("设置首页", "Settings home");
        p("右边缘", "Right edge");
        p("左边缘", "Left edge");
        p("顶边", "Top edge");
        p("显示隐藏文件(夹)", "Show hidden files (folders)");
        p("顶边左半下滑", "Swipe down from the left half of the top edge");
        p("双击", "Double tap");
        p("轻点", "Tap");
        p("长按", "Press");
        p("缩放", "Pinch");
        p("斜向", "Diagonal");
        p("单指滑动", "One-finger swipes");
        p("单指点按", "One-finger taps");
        p("双指滑动", "Two-finger swipes");
        p("双指点按", "Two-finger taps");
        p("下一页", "Next page");
        p("上一页", "Previous page");
        p("前光最亮", "Frontlight to maximum");
        p("前光关到最暗", "Frontlight to minimum");
        p("色温最暖", "Warmest");
        p("色温最冷", "Coolest");
        p("切换应用(最近任务)", "Switch app (recent tasks)");
        p("双指手势", "Two-finger gestures");
        p("双指上滑(整屏)", "Two-finger swipe up (whole screen)");
        p("双指下滑(整屏)", "Two-finger swipe down (whole screen)");
        p("双指向右上滑", "Two-finger swipe to the upper right");
        p("双指向左上滑", "Two-finger swipe to the upper left");
        p("双指向右下滑", "Two-finger swipe to the lower right");
        p("双指向左下滑", "Two-finger swipe to the lower left");
        p("双指捏合", "Two-finger pinch");
        p("双指张开", "Two-finger spread");
        p("边缘滑动(单指)", "Edge swipes (one finger)");
        p("沿左边缘上滑", "Swipe up along the left edge");
        p("沿左边缘下滑", "Swipe down along the left edge");
        p("沿右边缘上滑", "Swipe up along the right edge");
        p("沿右边缘下滑", "Swipe down along the right edge");
        p("角落(单指)", "Corners (one finger)");
        p("左上角轻点", "Tap the top left corner");
        p("右上角轻点", "Tap the top right corner");
        p("左下角轻点", "Tap the bottom left corner");
        p("右下角轻点", "Tap the bottom right corner");
        p("左上角双击", "Double tap the top left corner");
        p("右上角双击", "Double tap the top right corner");
        p("左下角双击", "Double tap the bottom left corner");
        p("右下角双击", "Double tap the bottom right corner");
        p("其他", "Other");
        p("只控制以下支持自定义的手势. 固定手势不受此开关影响, 始终有效.", "Only controls the customizable gestures below. Fixed gestures are not affected by this switch and always work.");
        p("固定手势(不可改)", "Fixed gestures (cannot be changed)");
        p("单指从顶边右半向下滑", "One finger swipe down from the right half of the top edge");
        p("状态栏", "Status bar");
        p("单指从底边左半向上滑", "One finger swipe up from the left half of the bottom edge");
        p("单指从底边右半向上滑", "One finger swipe up from the right half of the bottom edge");
        p("单指按住左上角 3 秒", "One finger press top-left corner for 3 s");
        p("退出 Embrith", "Exit Embrith");
        p("同时按左上角和右下角", "Press top-left and bottom-right corners together");
        p("同时按左下角和右上角", "Press bottom-left and top-right corners together");
        // ---- HomeActivity
        p("Embrith \u00b7 余烬", "Embrith \u00b7 余烬");        p("设置", "Settings");
        p("回到 Kindle", "Back to Kindle");
        p("退出", "Exit");
        p("重启", "Restart");
        p("关机", "Power off");
        p("选择操作", "Choose an action");
        p("安装应用", "Install app");
        p("从 Kindle 的文件夹里选择 apk 文件", "Pick an apk file from a Kindle folder");
        // ---- LogsActivity
        p("生成日志", "Generate logs");
        p("立即生成", "Generate now");
        p("最近 5 分钟", "Last 5 minutes");
        p("正在生成, 请稍等...", "Generating, please wait...");
        p("读取中...", "Reading...");
        p("Kindle 侧没有回应", "No reply from the Kindle side");
        p("没有日志文件", "No log files");
        p("日志文件, 点一下查看或删除", "Log files, tap one to view or delete");
        p("全部删除", "Delete all");
        p(" 个", " items"); // fragment
        p("查看", "View");
        p("删除", "Delete");
        p("正在删除...", "Deleting...");
        p("删除 logs/report/ 里的全部日志文件", "Delete all log files in logs/report/");
        // ---- MenuActivity
        p("阅读背景过滤", "Reading background filter");
        p("未开启", "Off");
        p(" 个应用", " apps");
        p("翻页动画", "Page turn animation");
        p("全部开启", "On for all apps");
        p(" 个应用关闭", " apps off");
        p("系统页面", "System pages");
        p("自动(默认)", "Auto (default)");
        p("始终动画", "Always animate");
        p("始终局部刷新", "Always partial refresh");
        p("系统页面翻页", "System page turns");
        p("自动: 设了壁纸并打开全局桌面应用时, 系统页面翻页改用局部刷新(只刷变化的文字, 不用翻页动画), 避免壁纸出现黑影; 没有壁纸时用翻页动画. 始终动画: 一律用翻页动画. 始终局部刷新: 一律不用翻页动画.", "Auto: when a wallpaper is set and Global desktop apps is on, system pages turn with a partial refresh (only the changed text is redrawn, no animation) so the wallpaper gets no black shadow; without a wallpaper the animation is used. Always animate: the animation is always used. Always partial refresh: the animation is never used.");
        p("每个应用默认都打开翻页动画. 取消勾选后, 从桌面进入这个应用时翻页不使用翻页动画, 改为按自动整屏闪的间隔刷新. 翻页动画不自然或黑块太多时可以关掉. 只对从桌面启动的应用有效.", "Page turn animation is on for every app by default. Untick an app to turn the animation off inside it: pages then refresh like the automatic full flash. Use this when the animation looks unnatural or leaves too many black blocks. Only for apps started from the home screen.");
        p("过滤阈值", "White threshold");
        p("灰度不小于这个数的浅色显示为纯白", "Light grays at or above this level are shown as pure white");
        p("240: 只过滤近乎白色的背景", "240: only nearly white backgrounds");
        p("224: 浅灰和浅米色(默认)", "224: light gray and cream (default)");
        p("208: 更深的米色", "208: darker cream");
        p("192: 中浅灰", "192: medium-light gray");
        p("选中的应用(从桌面启动)里, 比阈值浅的背景一律显示为纯白, 文字和图片的深色部分不变, 让阅读页的灰色底色在墨水屏上更干净. 过滤阈值: 灰度不小于这个数的颜色按纯白显示, 数字越小, 过滤的范围越大. 注意: 阈值太小时, 浅灰色的图片细节和浅色文字会一起被过滤成白色而看不见; 阈值对所有选中的应用共用, 没选中的应用不受影响. 建议先用默认值, 底色仍发灰就调小, 图片发白就调大.", "In the chosen apps (started from the home screen), backgrounds lighter than the threshold are shown as pure white; dark text and image parts stay. This keeps the gray page background of reading apps clean on e-ink. White threshold: colors with a gray level at or above this number are shown as pure white; a smaller number whitens more. Note: with a very small threshold, light-gray image detail and light text are whitened too and become invisible. The threshold is shared by all chosen apps; apps that are not chosen are not affected. Start with the default; lower it if the background still looks gray, raise it if images look washed out.");
        p("打开后, 从桌面进入这个应用时, 浅灰和米色的背景会显示为纯白, 深色字不变. 只对从桌面启动的应用有效. 灰度阈值在显示设置里调.", "When on, light gray and cream backgrounds of this app are shown as pure white; dark text stays. Only for apps started from the home screen. The gray threshold is in Display settings.");
        p("电源", "Power");
        p("网络", "Network");
        p("显示", "Display");
        p("高级", "Advanced");
        p("开发者模式", "Developer mode");
        p("默认只能连接 Kindle 里已经保存的 Wi-Fi 网络. 打开后, 在网络页的选择 Wi-Fi 网络里可以直接输入新网络的密码并加入. 注意: 密码会以明文写入控制目录的一个临时文件(仅文件所有者可读, Kindle 侧读取后立即删除), 再交给 Kindle 的 wifid 保存; Kindle 保存的网络信息里同样含明文密码, 发送日志或截图前请先去掉. 关闭后只能连接已保存的网络, 已保存的网络不受影响. 打开时会先弹出安全说明, 确认后才生效.", "By default only Wi-Fi networks already saved in Kindle can be joined. When turned on, you can enter the password of a new network directly in Choose a Wi-Fi network on the Network page and join it. Note: the password is written as plain text to a temporary file in the control directory (readable only by the file owner, deleted right after the Kindle side reads it) and then handed to the Kindle wifid to be saved; the network information saved by Kindle also contains the plain-text password, so remove it before sending logs or screenshots. When turned off, only saved networks can be joined; saved networks are not affected. A security note is shown first when turning it on, and it takes effect only after you confirm.");
        p("给开发和调试用, 普通使用请保持关闭. 打开或关闭后, 下次启动 Embrith 时生效(可以用电源页的重启 Embrith).\n\n打开后权限的变化:\n1. 日志: 调试日志持续写入 Kindle 存储里 embrith 文件夹的 logs 目录(平时只放在内存里, 有大小上限). 重启后日志还在, 但会不断占用存储空间和闪存寿命, 不会自动清理, 需要自己删除. 日志里可能含有 Wi-Fi 名称, 应用包名等个人信息, 发给别人前请先检查. 另外还会保存每次停止时的内核日志, 应用列表, 以及启动失败时的现场.\n2. 安装: 启动时, Kindle 上 embrith 文件夹的 src 里有什么就覆盖安装什么: 不校验文件是否完整, 不比较版本号, 版本更低, 内容缺失或损坏的包也会被装上, 可能导致 Embrith 无法启动. 只覆盖放进 src 的文件, 没放的保持不变. 被替换的文件会保留 .old 副本, 本次安卓启动成功后才删除; 启动不成功, 下次运行时自动还原.\n3. 提示: 安装过程中的屏幕提示会多停留几秒, 方便查看.\n\n不受影响: 你的设置, 壁纸, 截图和应用数据不会被安装动作改动.\n关闭后恢复普通模式: 更新包必须完整, 校验通过, 并且版本号更高才会安装.", "For development and debugging; keep it off for normal use. After turning it on or off, it takes effect the next time Embrith starts (you can use Restart Embrith on the Power page).\n\nWhat changes when it is on:\n1. Logs: debug logs are written continuously to the logs folder inside the embrith folder on the Kindle storage (normally they stay in memory with a size limit). The logs survive a restart, but they keep using storage space and flash lifetime and are never cleaned up automatically, so delete them yourself. Logs may contain personal information such as Wi-Fi names and app package names; check them before sharing. The kernel log of each stop, the list of apps, and the state at a failed start are saved as well.\n2. Install: at start, whatever is in src in the embrith folder on the Kindle is installed over the current files: files are not checked for completeness and versions are not compared, so an older, incomplete or damaged package is installed too and may keep Embrith from starting. Only the files you put in src are replaced; files you did not put there stay unchanged. Replaced files are kept as .old copies and deleted only after Android has started successfully; if the start fails, they are restored automatically on the next run.\n3. Prompts: the on-screen messages during installation stay a few seconds longer so you can read them.\n\nNot affected: your settings, wallpapers, screenshots and app data are not changed by installing.\nWhen turned off, normal mode is back: an update package must be complete, pass the check and have a higher version number to be installed.");
        p("Embrith 版本", "Embrith version");
        p("共享文件夹", "Shared folders");
        p("未共享", "Not shared");
        p(" 个文件夹", " folders");
        p("不能共享整个存储", "The whole storage cannot be shared");
        p("请进入文件夹后选择", "Open a folder to choose");
        p("已随上级文件夹共享", "Shared with the parent folder");
        p("共享这个文件夹", "Share this folder");
        p("这个文件夹打不开 (或者 Kindle 的存储没有接入)", "This folder cannot be opened (or the Kindle storage is not attached)");
        p("已共享", "Shared");
        p("含已共享的子文件夹", "Contains shared subfolders");
        p("这里没有子文件夹", "No subfolders here");
        p("把选中的 Kindle 文件夹以只读方式共享给安卓. 安卓里的路径是 /data/media/0/Kindle/ 加上它在 Kindle 上的位置, 例如 documents/weread. 改动在下次启动或重启 Embrith 后生效. 注意: 共享是只读的, 安卓应用不能修改或删除里面的文件; 选中上级文件夹后, 它下面的子文件夹不用再选; 不能共享整个存储, system 和 embrith 文件夹不会出现在这里; 共享出去的文件夹, 有存储权限的安卓应用都能读取, 隐私文件请不要共享.", "The chosen Kindle folders are shared with Android read-only. In Android the path is /data/media/0/Kindle/ plus the folder position on the Kindle, for example documents/weread. Changes take effect the next time Embrith starts or restarts. Note: sharing is read-only, so Android apps cannot change or delete the files in it; once a folder is chosen, its subfolders need not be chosen again; the whole storage cannot be shared, and the system and embrith folders are not listed here; Android apps with storage permission can read a shared folder, so do not share private files.");
        p("把选中的 Kindle 文件夹以只读方式共享给安卓, 安卓里的路径是 /data/media/0/Kindle/ 加上它在 Kindle 上的位置. 改动在下次启动或重启 Embrith 后生效. 注意: 共享是只读的, 应用不能修改或删除里面的文件; 不能共享整个存储, 也不能共享 system 和 embrith 文件夹; 共享出去的文件夹, 有存储权限的安卓应用都能读取, 隐私文件请不要共享.", "The chosen Kindle folders are shared with Android read-only; in Android the path is /data/media/0/Kindle/ plus the folder position on the Kindle. Changes take effect the next time Embrith starts or restarts. Note: sharing is read-only, so apps cannot change or delete the files in it; the whole storage and the system and embrith folders cannot be shared; Android apps with storage permission can read a shared folder, so do not share private files.");
        p("重启并更新 >", "Restart and update >");
        p("检查本地更新", "Check local update");
        p("校验更新", "Verified update");
        p("强制更新", "Forced update");
        p("没有更新包", "No update package");
        p("缺少 VERSION", "VERSION missing");
        p("Kindle 的 embrith/src 里没有更新包.", "There is no update package in embrith/src on the Kindle.");
        p("embrith/src 里有文件, 但没有读到 VERSION, 校验更新不会安装它.", "embrith/src has files but VERSION could not be read; a verified update will not install it.");
        p("更新包版本: ", "Package version: ");
        p(" (含 embrith.raw)", " (with embrith.raw)");
        p(" (纯代码, 不含 embrith.raw)", " (code only, no embrith.raw)");
        p("\n当前版本: ", "\nCurrent version: ");
        p("版本不高于当前版本: 校验更新会跳过它, 需要覆盖请用强制更新.", "Version is not higher than the current one: a verified update will skip it; use a forced update to overwrite.");
        p("校验更新可以安装它.", "A verified update can install it.");
        p("检查本地更新: 看 Kindle 上 embrith 文件夹的 src 里有没有更新包, 以及它的版本号. 校验更新: 重启前按常规规则安装; 更新包必须完整, 校验通过, 并且版本号高于当前版本, 否则不安装, 照常重启. 强制更新: 重启前不校验, 不比较版本号, 直接覆盖安装 src 里的文件; 版本更低, 不完整或损坏的包也会被装上, 可能导致 Embrith 无法启动, 只对这一次生效. 两种更新都会重启 Embrith, 约需一分钟; 安装失败或启动失败时, 下次运行会自动还原.", "Check local update: looks for an update package in src in the embrith folder on the Kindle and shows its version. Verified update: installs before the restart by the normal rules; the package must be complete, pass the check and have a higher version than the current one, otherwise it is not installed and Embrith simply restarts. Forced update: before the restart, files in src are installed over the current ones without any check or version compare; an older, incomplete or damaged package is installed too and may keep Embrith from starting; it applies to this one time only. Both restart Embrith, which takes about a minute; if the install or the start fails, the next run restores the old files automatically.");
        p("重启 Embrith, 并在重启前把 embrith/src 里的文件直接覆盖安装: 不校验是否完整, 不比较版本号, 只覆盖放进 src 的文件. 版本更低, 不完整或损坏的包也会被装上, 可能导致 Embrith 无法启动. 被替换的文件会保留 .old 副本, 启动失败时下次运行自动还原. 只对这一次生效, 不改变开发者模式的设置.", "Restarts Embrith and, before the restart, installs the files in embrith/src directly over the current ones: no completeness check and no version compare, and only the files you put in src are replaced. An older, incomplete or damaged package is installed too and may keep Embrith from starting. Replaced files are kept as .old copies and restored automatically on the next run if the start fails. It applies to this one time only and does not change the developer mode setting.");
        p("重启 Embrith, 并在重启前安装 Kindle 上 embrith/src 里的更新包. 更新包必须完整, 校验通过, 并且版本号高于当前版本, 否则不安装, 照常重启. 重启约需一分钟.", "Restarts Embrith and, before the restart, installs the update package in embrith/src on the Kindle. The package must be complete, pass the check and have a higher version than the current one, otherwise it is not installed and Embrith simply restarts. The restart takes about a minute.");
        p("个性化", "Personalization");
        p("通用", "General");
        p("刷新屏幕", "Refresh screen");
        p("主页", "Home");
        p("开关前光", "Toggle front light");
        p("退出Embrith", "Exit Embrith");
        p("每翻 1 页整屏闪一次(最干净,最慢)", "Full flash every page (cleanest, slowest)");
        p("每翻 3 页整屏闪一次", "Full flash every 3 pages");
        p("每翻 6 页整屏闪一次(默认,同 Kindle)", "Full flash every 6 pages (default, same as Kindle)");
        p("每翻 10 页整屏闪一次", "Full flash every 10 pages");
        p("从不闪(残影要手动清除)", "Never flash (clear ghosting manually)");
        p("每 1 页", "Every page");
        p("每 3 页", "Every 3 pages");
        p("每 6 页", "Every 6 pages");
        p("每 10 页", "Every 10 pages");
        p("从不", "Never");
        p("2 分钟", "2 minutes");
        p("5 分钟", "5 minutes");
        p("10 分钟(默认)", "10 minutes (default)");
        p("15 分钟", "15 minutes");
        p("30 分钟", "30 minutes");
        p("较小", "Smaller");
        p("标准(默认)", "Standard (default)");
        p("较大", "Larger");
        p("最大", "Largest");
        p("小", "Small");
        p("大", "Large");
        p("特大", "Extra large");
        p("桌面", "Desktop");
        p("未知", "Unknown");
        p("开", "On");
        p("未连接", "Not connected");
        p("电量", "Battery");
        p("睡眠定时器", "Sleep timer");
        p("没有触摸就自动休眠(同按电源键)的时限", "Sleep automatically after a period without touch (same as the power button)");
        p("重启 Embrith", "Restart Embrith");
        p("重启并更新", "Restart and update");
        p("先安装 Kindle 上放好的新版本, 再重启", "Install the new version placed on the Kindle, then restart");
        p("正在重启并更新 Embrith...", "Restarting and updating Embrith...");
        p("正在重启 Embrith...", "Restarting Embrith...");
        p("正在回到 Kindle...", "Returning to Kindle...");
        p("检查更新", "Check for updates");
        p("当前版本", "Current version");
        p("最新版本", "Latest version");
        p("现在更新", "Update now");
        p("更新日志", "Change log");
        p("自动更新", "Auto update");
        p("正在检查...", "Checking...");
        p("已是最新版本", "Up to date");
        p("发现新版本", "New version found");
        p("正在下载...", "Downloading...");
        p("已下载", "Downloaded");
        p("请先检查更新", "Check for updates first");
        p("Kindle 上没有 curl", "curl is missing on the Kindle");
        p("无法连接 GitHub, 请检查 Wi-Fi", "Cannot reach GitHub, check Wi-Fi");
        p("没有找到发布的版本", "No release found");
        p("发布的版本号格式不对", "The release version has a wrong format");
        p("发布里没有 .zip 安装包", "The release has no .zip package");
        p("Kindle 上没有 unzip", "unzip is missing on the Kindle");
        p("安装包不完整", "The package is incomplete");
        p("出错", "Error");
        p("下载并更新", "Download and update");
        p("这个版本没有写更新日志.", "This version has no change log.");
        p("新版本 ", "New version ");
        p("下载新版本 ", "Download new version ");
        p(" 已下载. 重启 Embrith 并安装它, 约需一分钟.", " is downloaded. Restart Embrith and install it, about a minute.");
        p(", 然后重启 Embrith 并安装. 下载需要几分钟, 请保持 Wi-Fi 连接.", ", then restart Embrith and install it. The download takes a few minutes, keep Wi-Fi on.");
        p("从 GitHub 的最新发布(release)更新 Embrith, 和重启并更新里的本地手动更新互不影响. 现在更新: 下载新版本, 然后重启 Embrith 安装, 约需几分钟. 自动更新: 开启后, 每次启动 Embrith 时(20 小时内最多一次)检查并下载新版本, 在下次从 Kindle 启动 Embrith 时安装, 不会自动重启. 需要先连上 Wi-Fi.", "Update Embrith from the latest GitHub release. It is independent of the manual local update under Restart and update. Update now: download the new version, then restart Embrith to install it, a few minutes. Auto update: when on, every start of Embrith (at most once in 20 hours) looks for a new version and downloads it; it is installed the next time Embrith is started from the Kindle, and nothing restarts by itself. Wi-Fi must be connected first.");
        p("重启只重启安卓容器, 不会回到 Kindle, 约一分钟后回到桌面.", "Restarts only the Android container, you do not return to Kindle. The home screen is back after about one minute.");
        p("退出后回到 Kindle 原来的界面.", "Leave Embrith and return to the Kindle screen as it was.");
        p("休眠同 Kindle:按电源键或合上磁吸保护套即休眠,显示锁屏图片并暂停安卓,再按电源键或打开保护套唤醒.睡眠定时器在本页设置.", "Sleep works like Kindle: press the power button or close the magnetic cover to sleep. The lock screen picture is shown and Android is paused. Press the power button or open the cover to wake. The sleep timer is set on this page.");
        p("重启与退出", "Restart and exit");
        p("重启只重启安卓容器, 不会回到 Kindle, 约一分钟后回到桌面; 退出后回到 Kindle 原来的界面. 任何时候单指按住屏幕左上角 3 秒也能退出.", "Restart only restarts the Android container and does not return to Kindle; the desktop is back in about a minute. Exit returns to the original Kindle interface. At any time, pressing the top-left corner of the screen with one finger for 3 s also exits.");
        p("蓝牙", "Bluetooth");
        p("选择 Wi-Fi 网络", "Choose a Wi-Fi network");
        p("选择蓝牙设备", "Choose a Bluetooth device");
        p("Wi-Fi 与蓝牙", "Wi-Fi and Bluetooth");
        p("SSH 远程登录", "SSH remote login");
        p("本机网络", "Local network");
        p("Kindle 的 IP 地址", "Kindle IP address");
        p("SSH 端口 2222,用户 root,可以用密钥文件,密码或免密登录.来源优先级: Embrith 内置 dropbear \u2192 KOReader \u2192 Kindle 自带; 自定义密码和免密只对内置 dropbear 有效. 电脑提示主机密钥已变化(REMOTE HOST IDENTIFICATION HAS CHANGED)时,在电脑上执行 ssh-keygen -R \"[IP]:2222\".", "SSH port 2222, user root; key file, password or no-password login is supported. Source priority: built-in Embrith dropbear > KOReader > Kindle's own; custom password and no-password only work with the built-in dropbear. If the computer warns that the host key has changed (REMOTE HOST IDENTIFICATION HAS CHANGED), run ssh-keygen -R \"[IP]:2222\" on the computer.");
        p("壁纸与锁屏图片", "Wallpaper and lock screen picture");
        p("自定义按钮 1", "Custom button 1");
        p("自定义按钮 2", "Custom button 2");
        p("自定义按钮 3", "Custom button 3");
        p("自定义按钮 4", "Custom button 4");
        p("自定义按钮 5", "Custom button 5");
        p("自定义按钮 6", "Custom button 6");
        p("飞行模式", "Airplane mode");
        p("按时间自动启用色温", "Enable color temperature on schedule");
        p("下拉面板", "Pull-down panel");
        p("下拉面板里有一行四个自定义按钮.", "The pull-down panel has one row of four customizable buttons.");
        p("设置首页左边方框固定为飞行模式,Wi-Fi,蓝牙,反色; 右边的圆形按钮可以自定义, 个数按屏幕宽度决定, 按从左到右,从上到下的顺序排列.", "The square on the left of the settings home is fixed to Airplane mode, Wi-Fi, Bluetooth and Invert; the round buttons on the right are customizable. How many fit depends on the screen width; they are laid out left to right, top to bottom.");
        p("桌面组件", "Desktop widgets");
        p("按类型勾选组件, 方框里的数字是排列顺序; 右侧是宽:高(一行宽 10, 宽 5 的可两个并排, 宽 10 的独占一行, 一行的高度取其中最高的, 桌面最多放 9 个单位高; 5:6 的前面或后面连续两个 5:3 会上下叠放在它旁边). 勾选后在\"调整顺序\"里用 ▲ ▼ 排序并查看预览. 回到桌面后在组件上长按进行设置. 屏幕时间需要给桌面授予使用情况访问权限.",
            "Check widgets by type; the number in the box is the order. The value on the right is width:height (a row is 10 wide; two widgets of width 5 fit side by side, a widget of width 10 takes a full row; a row is as high as its highest widget, and the desktop holds at most 9 units of height; two 5:3 widgets right before or after a 5:6 one are stacked beside it). After checking, use Reorder to sort them with the arrows and see a preview. After going back to the desktop, long-press a widget to configure it. Screen time needs usage access granted to the desktop.");
        p("字体", "Fonts");
        p("字体大小", "Font size");
        p("只改文字大小", "Changes text size only");
        p("把 ttf 或 otf 字体文件放进 Kindle 的 fonts 文件夹, 在这里选择. 系统字体重启 Embrith 后生效, 字体大小立即生效.", "Put ttf or otf font files in the fonts folder on Kindle, then pick one here. System font changes apply after restarting Embrith; font size applies immediately.");
        p("反色显示", "Inverted display");
        p("反色显示把整个屏幕黑白对调,适合夜间.", "Inverted display swaps black and white on the whole screen, suitable for night use.");
        p("局部刷新", "Partial refresh");
        p("自动全屏刷新", "Auto full-screen refresh");
        p("翻页时只刷新变化的部分,每隔几页自动整屏闪一次去残影", "Only changed areas refresh when turning pages, with an automatic full flash every few pages to remove ghosting");
        p("翻页动画步长", "Page turn animation steps");
        p("刷新方式", "Refresh mode");
        p("局部刷新同 Kindle:只刷新变化的地方,翻页每隔几页整屏闪一次去残影.关闭局部刷新 = 整屏闪刷(兼容),每次变化都闪一次.有残影时双指轻点(默认手势)或状态栏\"刷新屏幕\"可以手动整屏刷新.", "Partial refresh works like Kindle: only changed areas refresh, with a full flash every few pages to remove ghosting. Turning partial refresh off means full-screen flashing (compatible): every change flashes once. When ghosting appears, a two-finger tap (default gesture) or \"Refresh screen\" in the status bar refreshes the whole screen manually.");
        p("抖动", "Dithering");
        p("对比度与抖动", "Contrast and dithering");
        p("对比度(gamma 1.0 ~ 2.5):数字越大,文字越黑,浅灰越白.抖动:灰度画面用 8\u00d78 有序抖动,图片更细腻,文字不受影响.", "Contrast (gamma 1.0 to 2.5): the larger the number, the blacker the text and the whiter the light grays. Dithering: grayscale images use 8x8 ordered dithering for finer pictures; text is not affected.");
        p("显示大小", "Display size");
        p("所有界面的元素,间隔和字一起缩放", "Scales the elements, spacing and text of all screens together");
        p("大小", "Size");
        p("显示大小同时缩放布局, 间隔和文字, 对所有应用生效, 切换后界面会重新排版一次. 字体大小在下面的\"字体\"里设置.", "Display size scales layout, spacing and text together for all apps; the screen is laid out once more after switching. Font size is set under Fonts below.");
        p("系统默认", "System default");
        p("系统字体", "System font");
        p("选好后重启 Embrith 生效; 字体需包含中文才会显示中文", "Takes effect after restarting Embrith; the font must contain Chinese glyphs to display Chinese");
        p("对比度", "Contrast");
        p("设备名称", "Device name");
        p("关于 Embrith", "About Embrith");
        p("设备名称:", "Device name: "); // fragment
        p("\n内核:", "\nKernel: "); // fragment
        p("\n版权: ", "\nCopyright: "); // fragment
        p("\n许可证: ", "\nLicense: "); // fragment
        p("设备名称显示在下拉状态栏里.", "The device name is shown in the pull-down status bar.");
        p("24 小时制", "24-hour clock");
        p("从 Kindle 选择 apk", "Choose an apk from Kindle");
        p("apk 用 USB 拷到 Kindle 存储的任意位置,在这里选中后由 Kindle 侧静默安装,一般要一两分钟.", "Copy the apk anywhere on the Kindle storage over USB and select it here; the Kindle side installs it silently, usually within a minute or two.");
        p("内存", "Memory");
        p("内存管理", "Memory management");
        p("释放后台应用", "Free background apps");
        p("优化: dex2oat", "Optimize: dex2oat");
        p("打开后先在全屏页里把还没编译的应用编成 speed(机器码), 编完或出错自动退出, 期间不能操作; 之后新装的应用(桌面安装, 浏览器或应用内下载更新)安装时自动编译. 注意: 编译很占内存, 大应用要几分钟到十几分钟, 期间请不要重启或退出 Embrith; 编译后的文件会多占一些存储空间; 中途被打断时, 再打开一次开关即可重新编译. 关闭后新装的应用不再编译, 已编译的不受影响.", "When turned on, a full-screen page first compiles the apps not yet compiled to speed (machine code) and exits automatically when finished or on error; you cannot operate during this time. Afterwards, newly installed apps (installed from the desktop, or downloaded or updated in the browser or in apps) are compiled automatically at install time. Note: compiling uses a lot of memory; large apps take a few minutes to a dozen minutes, and Embrith must not be restarted or exited meanwhile. The compiled files take some extra storage. If it is interrupted, turn the switch off and on again to compile again. When turned off, newly installed apps are no longer compiled; apps already compiled are not affected.");
        p("新版 WebView", "Newer WebView");
        p("Embrith系统全局不使用网页内核, 新版(Chromium 119)和旧版(Chromium 52)网页内核主要在应用中使用(以下用新版, 旧版指代). 两版网页内核在JavaScript 引擎, Web API, 渲染管线, 安全模型, 网络协议和 WebView 系统集成方面都有非常大差距, 对内存的占用也大有不同. Embrith默认启用新版网页内核. 需注意: 切换回旧版网页内核可以适当减小内存压力, 但是可能会打不开应用内部分页面, 例如验证码.", "Embrith itself uses no web engine system-wide; the newer (Chromium 119) and older (Chromium 52) web engines are used mainly inside apps (called newer and older below). The two differ greatly in JavaScript engine, Web APIs, rendering pipeline, security model, network protocols and WebView system integration, and their memory use is very different. Embrith enables the newer engine by default. Note: switching back to the older engine can ease memory pressure somewhat, but some pages inside apps may fail to open, for example captchas.");
        p("已切换, 重新打开使用网页的应用后生效", "Switched. Reopen apps that show web pages to apply it");
        p("整屏重绘", "Full redraw");
        p("打开(默认)后, 应用每次刷新都重画整个屏幕, 可以避免书籍封面等区域出现黑框; 关闭则只重画变化的部分, 更流畅更省电, 但黑框可能再次出现. 注意: 切换后需要重新打开应用才生效, 已经打开的应用请先退出; 打开时刷新稍慢, 耗电略多.", "When on (default), apps redraw the whole screen on every refresh, which avoids black boxes around book covers and similar areas. When off, only the changed parts are redrawn: smoother and lighter on the battery, but the black boxes may come back. Note: reopen an app after switching, and quit apps that are already open first. When on, refreshes are slightly slower and use a little more power.");
        p("已切换, 重新打开应用后生效", "Switched. Reopen apps to apply it");
        p("输入 1 到 60 的数字, 越大越慢越细腻 (默认 12)", "Enter a number from 1 to 60; larger is slower and smoother (default 12)");
        p("请输入 1 到 60 的数字", "Enter a number from 1 to 60");
        p("正在释放内存...", "Freeing memory...");
        p("已释放 ", "Freed "); // fragment
        p(" MB, 现在可用 ", " MB, now available "); // fragment
        p("可用 ", "Available "); // fragment
        p(" MB / 共 ", " MB / total "); // fragment
        p("允许输入 Wi-Fi 密码", "Allow entering Wi-Fi passwords");
        p("Wi-Fi 和蓝牙属于 Kindle. 这里可以开关, 也可以在已保存的网络和已配对的蓝牙设备之间切换; 新蓝牙设备请先用 Kindle 自己的设置配对一次. 新 Wi-Fi 网络默认也要先用 Kindle 自己的设置添加; 在 \"高级\" 里打开 \"允许输入 Wi-Fi 密码\" 后可以在选择网络时直接输入密码(见开启时的安全说明).", "Wi-Fi and Bluetooth belong to Kindle. You can switch them on or off here and switch between saved networks and paired Bluetooth devices. Pair new Bluetooth devices once in the Kindle settings first. New Wi-Fi networks also have to be added in the Kindle settings first by default; after turning on \"Allow entering Wi-Fi passwords\" in \"Advanced\" you can enter the password directly when choosing a network (see the security note shown when turning it on).");
        p("开启后, 在选择网络时可以直接输入新网络的密码.\n\n安全说明: 密码会以明文写入控制目录的一个临时文件(仅文件所有者可读, Kindle 侧读取后立即删除), 再交给 Kindle 的 wifid 保存. Kindle 保存的网络信息里同样含明文密码, 发送日志或截图前请先去掉.\n\n不开启时只能扫描并连接已保存的网络.", "When on, you can enter the password of a new network directly when choosing a network.\n\nSecurity note: the password is written in plain text to a temporary file in the control directory (readable by its owner only, deleted by the Kindle side right after reading) and then handed to the Kindle wifid to be saved. The network information saved by Kindle also contains the plain text password, so remove it before sending logs or screenshots.\n\nWhen off, you can only scan and connect to saved networks.");
        p("我了解, 开启", "I understand, turn on");
        p("檀香山", "Honolulu");
        p("安克雷奇", "Anchorage");
        p("洛杉矶", "Los Angeles");
        p("丹佛", "Denver");
        p("芝加哥", "Chicago");
        p("纽约", "New York");
        p("圣保罗", "Sao Paulo");
        p("伦敦", "London");
        p("巴黎 / 布鲁塞尔 / 柏林", "Paris / Brussels / Berlin");
        p("雅典", "Athens");
        p("莫斯科", "Moscow");
        p("迪拜", "Dubai");
        p("卡拉奇", "Karachi");
        p("新德里", "New Delhi");
        p("达卡", "Dhaka");
        p("曼谷", "Bangkok");
        p("北京", "Beijing");
        p("东京", "Tokyo");
        p("悉尼", "Sydney");
        p("奥克兰", "Auckland");
        p("时区", "Time zone");
        p("语言", "Language");
        p("无法更改时区", "Cannot change the time zone");
        p("平时不往 Kindle 存储持续写日志. 点这里生成最近 5 分钟的完整日志, 保存在 Kindle 的 logs/report/ 目录, 可以在里面查看和删除.", "Logs are not written to the Kindle storage continuously. Tap here to generate the full log of the last 5 minutes, saved in the logs/report/ folder on Kindle, where you can view and delete it.");
        // ---- NetPickActivity
        p("只列出已配对的设备. 新设备请先在 Kindle 的设置里配对一次. ", "Only paired devices are listed. Pair new devices once in the Kindle settings first. "); // fragment
        p("刷新", "Refresh");
        p("桌面应用", "Desktop apps");
        p("每行显示", "Per row");
        p("桌面上每行放几个应用图标, 可选 4, 5, 6 个. 图标之间的间距自动调整.", "How many app icons the desktop shows in a row: 4, 5 or 6. The space between the icons adjusts automatically.");
        p("4 个(默认)", "4 (default)");
        p("5 个", "5");
        p("6 个", "6");
        p("桌面每行放几个应用图标", "How many app icons the desktop shows in a row");
        p("清理", "Clean up");
        p("关闭", "Close");
        p("正在读取...", "Reading...");
        p("Kindle 侧没有响应", "No response from the Kindle side");
        p("Wi-Fi 已关闭", "Wi-Fi is off");
        p("蓝牙已关闭", "Bluetooth is off");
        p("没有找到网络", "No networks found");
        p("没有已配对的设备", "No paired devices");
        p("网络(", "Networks ("); // fragment
        p("设备(", "Devices ("); // fragment
        p("已连接", "Connected");
        p("已配对", "Paired");
        p("已连接  ", "Connected  "); // fragment
        p("已保存  ", "Saved  "); // fragment
        p("企业  ", "Enterprise  "); // fragment
        p("加密  ", "Encrypted  "); // fragment
        p("已经连接到 ", "Already connected to "); // fragment
        p("不支持企业网络", "Enterprise networks are not supported");
        p("正在连接 ", "Connecting to "); // fragment
        p("正在断开 ", "Disconnecting from "); // fragment
        p("列表来自 Kindle 最近一次扫描. 点未保存的加密网络可输入密码; 企业网络不支持. ", "The list comes from the latest Kindle scan. Tap an encrypted network that is not saved to enter its password; enterprise networks are not supported. ");
        p("列表来自 Kindle 最近一次扫描. 未保存的加密网络请先在 Kindle 的设置里连接一次, 或在设置的网络页允许输入 Wi-Fi 密码; 企业网络不支持. ", "The list comes from the latest Kindle scan. For encrypted networks that are not saved yet, connect once in the Kindle settings first, or allow entering Wi-Fi passwords on the Network page of Settings; enterprise networks are not supported. ");
        p("密码(8 到 63 个字符)", "Password (8 to 63 characters)");
        p("连接", "Connect");
        p("密码长度应为 8 到 63 个字符", "The password must be 8 to 63 characters long");
        p("暂不支持含引号或反斜杠的网络名或密码", "Network names or passwords containing quotes or backslashes are not supported yet");
        p("请先在 Kindle 的设置里连接一次这个网络, 或在设置的网络页允许输入 Wi-Fi 密码", "Connect to this network once in the Kindle settings first, or allow entering Wi-Fi passwords on the Network page of Settings");
        // ---- NetworkActivity
        p("自动 (默认)", "Automatic (default)");
        p("阿里 223.5.5.5", "Alibaba 223.5.5.5");
        p("腾讯 119.29.29.29", "Tencent 119.29.29.29");
        p("谷歌 8.8.8.8", "Google 8.8.8.8");
        p("自定义...", "Custom...");
        p("DNS 服务器", "DNS server");
        p("本机网络是安卓容器里的 eth0, 由 Kindle 的 Wi-Fi 转发出去. Wi-Fi 和蓝牙的开关在设置的网络页.", "The local network is eth0 inside the Android container, forwarded out through the Kindle Wi-Fi. The Wi-Fi and Bluetooth switches are on the Network page of Settings.");
        p("DNS 已设置", "DNS set");
        p("例如 1.1.1.1 8.8.8.8", "For example 1.1.1.1 8.8.8.8");
        p("自定义 DNS (IPv4, 最多 3 个)", "Custom DNS (IPv4, up to 3)");
        p("请输入 1 到 3 个 IPv4 地址", "Enter 1 to 3 IPv4 addresses");
        p("没有这个网卡(Kindle 侧没有提供网络:需要 bin/slirp4netns)", "No such network interface (the Kindle side provides no network: bin/slirp4netns is required)");
        p("已启用", "Enabled");
        p("未启用", "Not enabled");
        p(",地址 ", ", address "); // fragment
        p("读取失败", "Read failed");
        // ---- PickApkActivity
        p("选择 apk 文件", "Choose an apk file");
        p("这个文件夹打不开 (或者 Kindle 的存储没有接入)", "This folder cannot be opened (or the Kindle storage is not attached)");
        p("这里没有文件夹和 apk 文件", "No folders or apk files here");
        p("安装", "Install");
        p("覆盖安装", "Install over existing");
        p("\n\n已安装: ", "\n\nInstalled: "); // fragment
        p(")\n将安装: ", ")\nTo install: "); // fragment
        p("\n版本低于已安装版本, 将降级安装.", "\nThis version is older than the installed one and will be downgraded."); // fragment
        p("\n应用数据保留.", "\nApp data is kept."); // fragment
        p("正在安装", "Installing");
        p("这可能要一两分钟, 请稍候...", "This may take a minute or two, please wait...");
        p("安装完成", "Installation complete");
        p("安装失败", "Installation failed");
        p("没有响应", "No response");
        p("Kindle 一侧没有回应, 请看 logs/last-run.txt", "No reply from the Kindle side, see logs/last-run.txt");
        // ---- SshActivity
        p("SSH 设置", "SSH settings");
        p("端口", "Port");
        p("登录方式", "Login method");
        p("连接命令", "Connect command");
        p("切换后几秒内生效. 两个来源可以同时开, 但端口不能相同; 改端口时正在运行的会自动重启. 关闭只停监听, 已连接的会话保留. 登录方式按各来源的 dropbear 提供: Embrith 有 密钥文件 / 密码(可自定义) / 免密, KOReader 有 密钥文件 / 系统密码(Kindle root) / 免密, 没有自定义密码. 不启动安卓时, 在 Kindle 书库里点 SSH 书目也能开关.", "Changes take effect within a few seconds. Both sources can be on at once, but their ports must differ; a running one restarts automatically when its port changes. Turning off only stops listening; connected sessions stay. Login methods follow each source's dropbear: Embrith has key file / password (customizable) / no password, KOReader has key file / system password (Kindle root) / no password, without a custom password. When Android is not running, the SSH entry in the Kindle library can also switch it on or off.");
        p("Kindle 上没有找到 ", "No dropbear for "); // fragment
        p(" 的 dropbear", " found on Kindle"); // fragment
        p(" 端口", " port"); // fragment
        p("端口范围 1 到 65535", "Port range 1 to 65535");
        p("这个端口已被另一个来源使用", "This port is already used by the other source");
        p("密钥文件", "Key file");
        p("不使用密码", "No password");
        p("密码(自定义)", "Password (custom)");
        p("密码(Kindle root)", "Password (Kindle root)");
        p("未安装", "Not installed");
        p("Wi-Fi 未连接", "Wi-Fi not connected");
        p("公钥文件", "Public key file");
        p("未找到(放到 ", "Not found (put it in "); // fragment
        p("注意", "Note");
        p("同一网络里任何人都能以 root 登录", "Anyone on the same network can log in as root");
        // ---- SshAuthActivity
        p("使用密钥文件", "Use key file");
        p("使用密码", "Use password");
        p("登录方式 \u00b7 KOReader", "Login method - KOReader");
        p("使用系统密码(Kindle root)", "Use system password (Kindle root)");
        p("当前", "Current");
        p("密钥文件: 电脑公钥放到 /mnt/us/.ssh/authorized_keys(或 embrith/ssh/). 密码: 留空用 Kindle root 密码, 或设自定义密码(只以哈希保存). 不使用密码: 同一网络的任何人都能登录, 只建议临时使用. ", "Key file: put the public key of your computer in /mnt/us/.ssh/authorized_keys (or embrith/ssh/). Password: leave empty to use the Kindle root password, or set a custom password (stored only as a hash). No password: anyone on the same network can log in; recommended for temporary use only. ");
        p("KOReader 的 dropbear 只有三种: 密钥文件(公钥放到 /mnt/us/koreader/settings/SSH/authorized_keys), 系统密码(Kindle root 的密码, 不能自定义), 不使用密码(同一网络的任何人都能登录, 只建议临时使用). ", "The dropbear of KOReader has only three methods: key file (put the public key in /mnt/us/koreader/settings/SSH/authorized_keys), system password (the Kindle root password, cannot be customized), and no password (anyone on the same network can log in; recommended for temporary use only). ");
        p("正在应用...", "Applying...");
        p("公钥:", "Public key: "); // fragment
        p("没有公钥文件,将无法登录", "No public key file; login will not work");
        p("任何人都能以 root 登录", "Anyone can log in as root");
        p("自定义密码", "Custom password");
        p("Kindle root 密码", "Kindle root password");
        p("没有找到公钥文件", "Public key file not found");
        p("请先把电脑上的公钥(如 id_ed25519.pub)复制为 Kindle 的 ", "Copy the public key of your computer (such as id_ed25519.pub) to this file on Kindle first: "); // fragment
        p(",否则切换后无法登录.仍然切换?", ". Otherwise you cannot log in after switching. Switch anyway?"); // fragment
        p("切换", "Switch");
        p("同一网络里的任何人都能不输密码以 root 登录这台 Kindle.只建议在自己的网络里临时使用.确定?", "Anyone on the same network can log in to this Kindle as root without a password. Only recommended for temporary use on your own network. Are you sure?");
        p("确定", "OK");
        p("留空 = 使用 Kindle root 密码", "Leave empty = use the Kindle root password");
        p("设置 SSH 密码", "Set SSH password");
        p("密码至少 4 位", "Password must be at least 4 characters");
        p("无法生成密码哈希", "Cannot generate the password hash");
        // ---- StatusService
        p("无法显示状态栏", "Cannot show the status bar");
        p("M月d日 EEEE  HH:mm", "EEE, MMM d  HH:mm"); // date pattern
        p("M月d日 EEEE  h:mm a", "EEE, MMM d  h:mm a"); // date pattern
        p("蓝牙 ", "Bluetooth "); // fragment
        p("时间设置  ", "Schedule  "); // fragment
        p("冷", "Cool");
        p("暖", "Warm");
        p("(不支持)", " (not supported)");
        // ---- Ui
        p("详情 \u203a", "Details \u203a");
        p("详情 >", "Details >");
        // ---- WallpaperActivity
        p("不褪色", "No fading");
        p("80%(很淡)", "80% (very light)");
        p("每次回到桌面", "Every time returning to the desktop");
        p("每 30 分钟", "Every 30 minutes");
        p("每小时", "Every hour");
        p("每天", "Every day");
        p("已选", "Selected");
        p("纯白", "Plain white");
        p("文件夹 ", "Folder "); // fragment
        p("(随机)", " (random)");
        p("桌面壁纸", "Desktop wallpaper");
        p("锁屏图片", "Lock screen picture");
        p("壁纸与锁屏", "Wallpaper and lock screen");
        p("纯白(无图片)", "Plain white (no picture)");
        p("从 Kindle 选择", "Choose from Kindle");
        p("Embrith 壁纸文件夹", "Embrith wallpaper folder");
        p("选择图片", "Choose a picture");
        p("可以选 Kindle 存储里任意一张 png / jpg 图片,或一个文件夹(随机切换).进入文件夹后,点\"使用此文件夹\"即按该文件夹随机,点图片即用这一张.图片会按屏幕裁切并转成灰度.", "You can pick any png / jpg picture in the Kindle storage, or a folder (random switching). After entering a folder, tap \"Use this folder\" to pick randomly from it, or tap a picture to use that one. Pictures are cropped to the screen and converted to grayscale.");
        p("褪色", "Fade");
        p("全局桌面应用", "Apply to whole desktop");
        p("应用于统计, 打卡, 概览, 主页, 阅读, 书库和设置首页.", "Applied to statistics, check-in, overview, home, reading, library and the settings home.");        p("文件夹切换", "Folder switching");
        p("桌面壁纸切换间隔", "Desktop wallpaper switch interval");
        p("选了文件夹时,桌面壁纸按这个间隔随机换一张", "When a folder is chosen, the desktop wallpaper switches to a random picture at this interval");
        p("褪色与切换", "Fade and switching");
        p("褪色对桌面壁纸和锁屏图片分别设置.选了文件夹时:锁屏图片每次锁屏随机换一张;桌面壁纸按\"文件夹切换\"的间隔随机换.", "Fade is set separately for the desktop wallpaper and the lock screen picture. When a folder is chosen: the lock screen picture changes randomly each time the screen locks; the desktop wallpaper changes randomly at the \"Folder switching\" interval.");
        p(" \u00b7 为", " - for "); // fragment
        p("选择", ", choose");
        p("使用此文件夹(随机,", "Use this folder (random, "); // fragment
        p(" 张)", " pictures)"); // fragment
        p("这个文件夹打不开(或者 Kindle 的存储没有接入)", "This folder cannot be opened (or the Kindle storage is not attached)");
        p("这里没有文件夹和图片", "No folders or pictures here");
        p("0 到 90", "0 to 90");
        p("褪色百分比 (0 到 90)", "Fade percentage (0 to 90)");
        p("请输入 0 到 90 的整数", "Enter an integer from 0 to 90");
        // ---- Widgets
        p("图片 1", "Picture 1");
        p("图片 2", "Picture 2");
        p("图片 3", "Picture 3");
        p("自定义文字 1", "Custom text 1");
        p("自定义文字 2", "Custom text 2");
        p("屏幕时间: 总计", "Screen time: total");
        p("屏幕时间: 单个应用 1", "Screen time: single app 1");
        p("屏幕时间: 单个应用 2", "Screen time: single app 2");
        p("空白 1", "Blank 1");
        p("空白 2", "Blank 2");
        p("空白 3", "Blank 3");
        p("阅读统计", "Reading stats");
        p("列表或网格", "List or grid");
        p("列表(默认)", "List (default)");
        p("网格", "Grid");
        p("名称", "Name");
        p("自然数字", "Natural");
        p("拼音", "Pinyin");
        p("修改时间", "Modified");
        p("文件大小", "File size");
        p("类型", "Type");
        p("不分组", "No grouping");
        p("分组", "Grouping");
        p("列表", "List");
        p("书库和最近阅读用列表还是网格显示", "Show the library and recent books as a list or a grid");
        p("开启", "Enabled");
        p("阅读目标", "Reading goal");
        p("阅读记录的概览和打卡按这个目标计算, 屏幕时间也算阅读时间.", "The overview and check-in of the reading record use this goal; screen time counts as reading time.");
        p("日目标", "Daily goal");
        p("周目标", "Weekly goal");
        p("月目标", "Monthly goal");
        p("年目标", "Yearly goal");
        p("不设目标", "No goal");
        p("45 分钟", "45 minutes");
        p("1 小时", "1 hour");
        p("1.5 小时", "1.5 hours");
        p("2 小时", "2 hours");
        p("3.5 小时", "3.5 hours");
        p("5 小时", "5 hours");
        p("7 小时", "7 hours");
        p("10 小时", "10 hours");
        p("15 小时", "15 hours");
        p("20 小时", "20 hours");
        p("30 小时", "30 hours");
        p("50 小时", "50 hours");
        p("100 小时", "100 hours");
        p("180 小时", "180 hours");
        p("300 小时", "300 hours");
        p("500 小时", "500 hours");
        p("今日目标", "Today's goal");
        p("本周目标", "This week's goal");
        p("本月目标", "This month's goal");
        p("本年目标", "This year's goal");
        p("设置里没有设置阅读目标", "No reading goal is set in settings");
        p("最近阅读显示", "Recent shows");
        p("一行封面(默认)", "One row of covers (default)");
        p("两行封面", "Two rows of covers");
        p("书库的阅读页里, 最近阅读显示几行封面; 每行本数同 \"每行显示\"", "How many rows of covers Recent shows on the Reading page of the library; books per row as in \"Books per row\"");
        p("阅读记录内容", "Reading record contents");
        p("阅读记录按日期汇总阅读时间, 点日历里的某天或某本书可以看详细记录. 方框里的数字是显示顺序, 先勾选的在前.", "The reading record sums up the reading time by date; tap a day in the calendar or a book for the details. The number in the box is the display order; the one checked first comes first.");
        p("汇总", "Summary");
        p("日历", "Calendar");
        p("书籍记录", "Books");
        p("书库", "Library");
        p("最近阅读", "Recent");
        p("阅读记录", "Reading record");
        p("近 7 天", "Last 7 days");
        p("本月", "This month");
        p("总计", "Total");
        p("读过的书", "Books read");
        p("当天合计", "Day total");
        p("累计", "Cumulative");
        p("打开这本书", "Open this book");
        p("更早", "Earlier");
        p("‹ 返回", "‹ Back");
        p("设置里没有选择阅读记录的内容", "No reading record contents are selected in settings");
        p("设置里没有选择这一屏要显示的内容", "No boards are selected for this screen in settings");
        p("添加文件夹", "Add folder");
        p("图片 4", "Picture 4");
        p("图片 5", "Picture 5");
        p("图片 6", "Picture 6");
        p("正在阅读", "Now reading");
        p("剩余 ", "Left ");   // fragment
        p("路径", "Path");
        p("包含", "Contains");
        p(" 本书", " books");   // fragment
        p("修改", "Modified");
        p("已读", "Read");
        p("进度", "Progress");
        p("打开", "Open");
        p("还没有阅读记录", "No reading history yet");
        p("已读 ", "Read ");   // fragment
        p("图片", "Pictures");
        p("文字", "Text");
        p("阅读", "Reading");
        p("空白", "Blank");
        p("书名和作者", "Title and author");
        p("封面角标", "Cover badge");
        p("新", "New");
        p("调整顺序", "Reorder");
        p("组件排序", "Widget order");
        p("还没有勾选组件", "No widgets checked");
        p("点 ▲ ▼ 调整顺序; 预览是桌面上的样子 (缩小), 排在前面的先放", "Tap the arrows to reorder; the preview is the desktop look (reduced), earlier ones are placed first");
        p("桌面最多放 9 个单位高, 请先取消别的组件", "The desktop holds at most 9 units of height; uncheck another widget first");
        p("长按选择图片", "Long-press to choose a picture");
        p("长按编辑文字", "Long-press to edit text");
        p("屏幕时间", "Screen time");
        p("长按选择应用", "Long-press to choose an app");
        p(" 小时 ", " h "); // fragment
        p(" 分", " min"); // fragment
        p(" 分钟", " min"); // fragment
        p("近 7 天", "Last 7 days");
        p("今日", "Today");
        p(" (未授权或无数据)", " (not authorized or no data)"); // fragment
        p("自定义文字", "Custom text");
        p("选择应用", "Choose an app");
        p("此组件没有可设置项", "This widget has no options");
        // ---- settings page groups
        p("电源状态", "Power status");
        p("系统操作", "System actions");
        p("无线连接", "Wireless");
        p("网络服务", "Network services");
        p("显示布局", "Layout");
        p("屏幕效果", "Screen effects");
        p("屏幕刷新", "Screen refresh");
        p("外观", "Appearance");
        p("页边距", "Page margin");
        p("所有页面的框到屏幕左右和底部边缘的距离, 设置后各页面自动重新排版. 默认 22 dp.", "Distance from the frames of every page to the left, right and bottom screen edge; pages re-layout automatically. Default 22 dp.");
        p("22 dp (默认)", "22 dp (default)");
        p("书库位置", "Library location");
        p("页面布局", "Page layout");
        p("排序", "Sorting");
        p("维护", "Maintenance");
        p("设备信息", "Device info");
        p("地区与语言", "Region and language");
        p("系统维护", "System maintenance");
        p("高级与兼容性", "Advanced and compatibility");
        p("系统", "System");
        p("立即休眠", "Sleep now");
        p("说明", "Info");
        p("显示名称", "Show names");
        p("书的名称", "Book names");
        p("文件名(默认)", "File name (default)");
        p("书名", "Title");
        p("书名 - 作者", "Title - Author");
        p("编辑属性", "Edit properties");
        p("保存", "Save");
        p("还原", "Reset");
        p("系列序号", "Series number");
        p("标签 (逗号分隔)", "Tags (comma separated)");
        p("作者 - 书名", "Author - Title");
        p("书在列表和网格里显示什么名称; 书名和作者来自书里的信息, 第一次显示时会在后台读取, 没有的显示文件名", "What a book is called in the list and the grid. The title and author come from the book itself and are read in the background the first time; a book without them shows its file name.");
        p("文件夹内分组", "Group books in a folder");
        p("不分组(默认)", "No grouping (default)");
        p("作者", "Author");
        p("系列", "Series");
        p("格式", "Format");
        p("首字母", "Initial");
        p("标签", "Tag");
        p("把文件夹里的书按条件分成虚拟文件夹, 点进去才看到书. 作者, 系列, 标签来自书里的信息(系列只有 epub 有), 没有的归入未知作者, 无系列, 无标签", "Splits the books of a folder into virtual folders by a condition; tap one to see its books. Author, series and tag come from the book itself (only epub has a series); a book without them goes to Unknown author, No series or No tag.");
        p("未知作者", "Unknown author");
        p("无系列", "No series");
        p("无标签", "No tag");
        p("正在读取书籍信息...", "Reading book information...");
        p("只影响网格模式. 封面始终是 3:4, 设置每行显示和每屏行数后, 间距自动调整. 文件夹的封面显示文件夹名.", "Grid mode only. Covers are always 3:4; the spacing adjusts automatically for the columns and rows you set. A folder cover shows the folder name.");
        p("Wi-Fi 和蓝牙属于 Kindle. 这里可以开关, 也可以在已保存的网络和已配对的蓝牙设备之间切换; 新蓝牙设备请先用 Kindle 自己的设置配对一次.", "Wi-Fi and Bluetooth belong to Kindle. You can switch them on or off here and switch between saved networks and paired Bluetooth devices. Pair new Bluetooth devices once in the Kindle settings first.");
        // ---- punctuation only
        p("-", "-"); // fragment
        p("+", "+"); // fragment
        p(")", ")"); // fragment
        p("概览", "Overview");
        p("打卡", "Check-in");
        p("统计", "Statistics");
        p("今日阅读", "Today");
        p("目标", "Goal");
        p("未设置", "Not set");
        p("设置目标", "Set goal");
        p("修改目标", "Change goal");
        p("累计时长", "Total time");
        p("累计本数", "Books read");
        p("已读完本数", "Finished");
        p("完读本数", "Finished");
        p("本周小结", "This week");
        p("阅读时长", "Reading time");
        p("APP 时长", "App time");
        p("目标类型", "Goal type");
        p("仅阅读时间", "Reading time only");
        p("仅 APP 时间", "App time only");
        p("阅读和 APP 并排", "Reading and app side by side");
        p("选择 APP", "Choose app");
        p("APP 目标", "App goal");
        p("未选择", "None chosen");
        p("累计打卡", "Check-ins");
        p("最长连续", "Longest streak");
        p("当前连续", "Current streak");
        p("当前目标", "Current goal");
        p(" 天", " days");
        p("本周阅读", "This week");
        p("本月阅读", "This month");
        p("年阅读时长", "Year time");
        p("年完结书籍", "Year finished");
        p("3 小时", "3 hours");
        p("不设目标", "No goal");
        p("‹ 返回", "‹ Back");
        // texts that had no English: brightness, footnote subtitles, library settings, reader, comic, folder picker
        p("时段色温", "Scheduled color temperature");
        p("开启后, 在开始和结束时间之间使用时段色温; 其余时间(或关闭时)使用上面的默认色温.", "When on, the scheduled color temperature is used between the start and end time; the rest of the time (or when off) the default color temperature above is used.");
        p("跨过午夜", "Past midnight");
        p("开始晚于结束表示跨过午夜(如 21:00 到次日 07:00).", "A start later than the end means the period runs past midnight (for example 21:00 to 07:00 the next day).");
        p("暗", "Dim");
        p("亮", "Bright");
        p("自动亮度", "Auto brightness");
        p("休眠与唤醒", "Sleep and wake");
        p("退出后回到 Kindle 原来的界面. 任何时候单指按住屏幕左上角 3 秒也能退出.", "After leaving you are back in the Kindle UI. At any time you can also leave by holding one finger on the top left corner of the screen for 3 seconds.");
        p("Wi-Fi 和蓝牙", "Wi-Fi and Bluetooth");
        p("设置首页和下拉面板", "Home page and pull-down panel");
        p("控制区", "Control area");
        p("设置首页和下拉面板上方的控制区相同: 左边方框固定为飞行模式,Wi-Fi,蓝牙,反色; 中间是亮度和色温条; 最右边两个圆形按钮可以自定义.", "The control area on top of the settings home page and the pull-down panel is the same: the box on the left is fixed to airplane mode, Wi-Fi, Bluetooth and invert; in the middle are the brightness and color temperature bars; the two round buttons on the right can be customized.");
        p("系统页边距", "System page margin");
        p("底部栏", "Bottom bar");
        p("底部栏页面", "Bottom bar pages");
        p("勾选的页面显示在底部栏, 按勾选先后从左往右排列, 方框里的数字是位置; 在这些页面之间左右滑动切换, 两端相连. 至少保留一项.", "Checked pages are shown in the bottom bar, from left to right in the order they were checked; the number in the box is the position. Swipe left or right to switch between these pages, the two ends are connected. At least one must stay.");
        p("至少保留一项", "At least one must stay");
        p("按类型勾选组件, 方框里的数字是排列顺序; 右侧是宽:高(一行宽 10, 宽 5 的可两个并排, 宽 10 的独占一行, 桌面最多放 9 个单位高; 按顺序依次放进最靠上靠左的空位, 后面的小组件会填进 5:6 旁边的空位, 两个 5:6 可以错位排列). 勾选后在\"调整顺序\"里用 ▲ ▼ 排序并查看预览. 回到桌面后在组件上长按进行设置. 屏幕时间需要给桌面授予使用情况访问权限.", "Check the widgets by type, the number in the box is the order; on the right is width:height (a row is 10 wide, two widgets of width 5 can sit side by side, a widget of width 10 takes a whole row, the desktop holds at most 9 units of height; widgets are placed in order into the topmost and leftmost free space, later small widgets fill the space next to a 5:6, two 5:6 can be staggered). After checking, sort them with the up and down arrows in \"Reorder\" and look at the preview. Back on the desktop, long press a widget to set it up. Screen time needs the usage access permission for the desktop.");
        p("2 列", "2 columns");
        p("3 列(默认)", "3 columns (default)");
        p("4 列", "4 columns");
        p("5 列", "5 columns");
        p("3 列", "3 columns");
        p("自动(封面 3:4, 可上下滑动)(默认)", "Auto (cover 3:4, scroll up and down) (default)");
        p("2 行", "2 rows");
        p("3 行", "3 rows");
        p("4 行", "4 rows");
        p("5 行", "5 rows");
        p("自动", "Auto");
        p("格式名(如 EPUB)(默认)", "Format name (like EPUB) (default)");
        p("书名和格式名", "Title and format name");
        p("格式和大小(默认)", "Format and size (default)");
        p("只显示格式", "Format only");
        p("不显示", "Hide");
        p("名称(默认)", "Name (default)");
        p("名称(数字按大小, 如 2 在 10 前)", "Name (numbers by value, 2 before 10)");
        p("名称(中文按拼音)", "Name (Chinese by pinyin)");
        p("修改时间(新的在前)", "Modified (newest first)");
        p("大小(大的在前)", "Size (largest first)");
        p("最近阅读(没读过的在后)", "Last read (unread last)");
        p("书库文件夹", "Library folders");
        p("选择放书的文件夹, 书库首页只显示它们.", "Choose the folders that hold your books; the library home shows only these.");
        p("选择文件夹", "Choose folders");
        p("打开前询问", "Ask before opening");
        p("网格模式", "Grid mode");
        p("最近页显示所在文件夹", "Recent page shows the folder");
        p("只影响列表模式.", "Only affects list mode.");
        p("点书后先弹框确认, 防止在墨水屏上误触.", "Tapping a book first asks for confirmation, to avoid accidental touches on the e-ink screen.");
        p("网格模式每行放几本书", "Books per row in grid mode");
        p("每屏行数", "Rows per screen");
        p("网格模式一屏放几行; 自动时封面按 3:4, 超过一屏上下滑动", "Rows per screen in grid mode; with Auto the covers are 3:4 and you scroll up and down when there is more than a screen");
        p("无封面时显示", "Without a cover show");
        p("网格模式里没有封面图片的书显示什么", "What a book without a cover picture shows in grid mode");
        p("列表里书名后显示", "After the title in the list show");
        p("列表模式下每本书右侧的小字", "The small text on the right of each book in list mode");
        p("排序方式", "Sort by");
        p("倒序", "Reverse order");
        p("文件夹在前", "Folders first");
        p("清空最近记录", "Clear recent list");
        p("只清空最近阅读的列表, 阅读位置和书签不受影响.", "Only the list of recently read books is cleared; reading positions and bookmarks are kept.");
        p("清空", "Clear");
        p("已清空", "Cleared");
        p("清除封面缓存", "Clear cover cache");
        p("下次显示网格时会重新生成封面, 第一次会慢一些.", "The covers are made again the next time the grid is shown, the first time is slower.");
        p("清除", "Clear");
        p("字体文件", "Font files");
        p("gamma 1.0 ~ 2.5:数字越大,文字越黑,浅灰越白.", "Gamma 1.0 to 2.5: the larger the number, the blacker the text and the whiter the light gray.");
        p("灰度画面用 8×8 有序抖动,图片更细腻,文字不受影响.", "Gray pictures use 8x8 ordered dithering for finer images; text is not affected.");
        p("彩色显示(KC)", "Color display (KC)");
        p("中文", "Chinese");
        p("设备名称: ", "Device name: ");
        p("\n内核版本: ", "\nKernel version: ");
        p("Embrith系统全局不使用网页内核, 新版(Chromium 119)和旧版(Chromium 52)网页内核主要在应用中使用(以下用新版, 旧版指代). 两版网页内核在JavaScript 引擎, Web API, 渲染管线, 安全模型, 网络协议和 WebView 系统集成方面都有非常大差距, 对内存的占用也大有不同. Embrith默认启用新版网页内核. 需注意: 切换回旧版网页内核可以适当减小内存压力, 但是可能会打不开应用内部分页面, 例如验证码. 注意: 切换后正在使用网页的应用会被系统结束, 未保存的内容会丢失, 请先保存; 重新打开该应用即生效, 不需要重启 Embrith; 如果重新打开后仍是旧内核, 请重启 Embrith. 内存紧张时, 新版内核会让后台应用更容易被系统结束.", "Embrith itself does not use a web engine; the new (Chromium 119) and the old (Chromium 52) web engines are used mainly inside apps (below, \"new\" and \"old\" refer to them). The two differ a lot in JavaScript engine, Web APIs, rendering pipeline, security model, network protocols and WebView integration, and also in memory use. Embrith uses the new web engine by default. Note: switching back to the old one can ease memory pressure, but some pages inside apps may not open, for example captchas. Note: after switching, apps that are using a web page are ended by the system and unsaved content is lost, so save first; the change takes effect when the app is opened again, Embrith does not need to be restarted; if the old engine is still used after reopening, restart Embrith. When memory is tight, the new engine makes background apps more likely to be ended by the system.");
        p("注意: 编译很占内存, 大应用要几分钟到十几分钟, 期间请不要重启或退出 Embrith; 编译后的文件会多占一些存储空间; 中途被打断时, 再打开一次开关即可重新编译. 关闭后新装的应用不再编译, 已编译的不受影响.", "Note: compiling uses a lot of memory, a large app takes minutes to more than ten minutes, do not restart or leave Embrith meanwhile; the compiled files take some more storage; if it is interrupted, turn the switch on again to compile again. After turning it off, newly installed apps are not compiled, compiled ones are not affected.");
        p("GitHub 更新", "GitHub update");
        p("从 GitHub 的最新发布(release)更新 Embrith, 和重启并更新里的本地手动更新互不影响.", "Update Embrith from the latest GitHub release; it is independent of the manual local update in Restart and update.");
        p("下载新版本, 然后重启 Embrith 安装, 约需几分钟.", "Downloads the new version, then restarts Embrith to install it, which takes a few minutes.");
        p("开启后, 每次启动 Embrith 时(20 小时内最多一次)检查并下载新版本, 在下次从 Kindle 启动 Embrith 时安装, 不会自动重启.", "When on, every time Embrith starts (at most once in 20 hours) it checks for and downloads a new version, which is installed the next time Embrith is started from the Kindle; it does not restart by itself.");
        p("网络条件", "Network");
        p("需要先连上 Wi-Fi.", "Wi-Fi must be connected first.");
        p("共享方式", "How sharing works");
        p("把选中的 Kindle 文件夹以只读方式共享给安卓. 安卓里的路径是 /data/media/0/Kindle/ 加上它在 Kindle 上的位置, 例如 documents/weread.", "Shares the chosen Kindle folders with Android as read-only. The path in Android is /data/media/0/Kindle/ plus the location on the Kindle, for example documents/weread.");
        p("生效时间", "When it takes effect");
        p("改动在下次启动或重启 Embrith 后生效.", "Changes take effect the next time Embrith is started or restarted.");
        p("注意事项", "Notes");
        p("共享是只读的, 安卓应用不能修改或删除里面的文件; 选中上级文件夹后, 它下面的子文件夹不用再选; 不能共享整个存储, system 和 embrith 文件夹不会出现在这里; 共享出去的文件夹, 有存储权限的安卓应用都能读取, 隐私文件请不要共享.", "Sharing is read-only, Android apps cannot change or delete the files in it; after you choose a parent folder, its subfolders need not be chosen again; the whole storage cannot be shared, and the system and embrith folders do not appear here; a shared folder can be read by every Android app with storage permission, so do not share private files.");
        p("来源与端口", "Source and port");
        p("切换后几秒内生效. 两个来源可以同时开, 但端口不能相同; 改端口时正在运行的会自动重启.", "Takes effect a few seconds after switching. Both sources can be on at the same time, but the ports must differ; when a port is changed, the running one restarts by itself.");
        p("关闭只停监听, 已连接的会话保留.", "Turning off only stops listening, sessions that are already connected stay.");
        p("登录方式按各来源的 dropbear 提供: Embrith 有 密钥文件 / 密码(可自定义) / 免密, KOReader 有 密钥文件 / 系统密码(Kindle root) / 免密, 没有自定义密码.", "The login methods come from the dropbear of each source: Embrith has key file / password (can be customized) / no password, KOReader has key file / system password (Kindle root) / no password, and no custom password.");
        p("书库里的开关", "Switch in the library");
        p("不启动安卓时, 在 Kindle 书库里点 SSH 书目也能开关.", "When Android is not running, you can also switch it by tapping the SSH entry in the Kindle library.");
        p("电脑公钥放到 /mnt/us/.ssh/authorized_keys(或 embrith/ssh/).", "Put the public key of your computer in /mnt/us/.ssh/authorized_keys (or embrith/ssh/).");
        p("密码", "Password");
        p("留空用 Kindle root 密码, 或设自定义密码(只以哈希保存).", "Leave empty to use the Kindle root password, or set a custom password (only a hash is stored).");
        p("同一网络的任何人都能登录, 只建议临时使用.", "Anyone on the same network can log in, only use it temporarily.");
        p("公钥放到 /mnt/us/koreader/settings/SSH/authorized_keys.", "Put the public key in /mnt/us/koreader/settings/SSH/authorized_keys.");
        p("系统密码", "System password");
        p("Kindle root 的密码, 不能自定义.", "The password of Kindle root, it cannot be customized.");
        p("看 Kindle 上 embrith 文件夹的 src 里有没有更新包, 以及它的版本号.", "Looks for an update package in src of the embrith folder on the Kindle, and shows its version.");
        p("重启前按常规规则安装; 更新包必须完整, 校验通过, 并且版本号高于当前版本, 否则不安装, 照常重启.", "Installs by the normal rules before the restart; the package must be complete, pass the check and have a higher version than the current one, otherwise nothing is installed and Embrith restarts as usual.");
        p("重启前不校验, 不比较版本号, 直接覆盖安装 src 里的文件; 版本更低, 不完整或损坏的包也会被装上, 可能导致 Embrith 无法启动, 只对这一次生效.", "No check and no version comparison before the restart, the files in src are installed over the current ones; a package with a lower version, or an incomplete or damaged one, is installed too and may keep Embrith from starting; it only applies this one time.");
        p("重启与还原", "Restart and restore");
        p("两种更新都会重启 Embrith, 约需一分钟; 安装失败或启动失败时, 下次运行会自动还原.", "Both kinds of update restart Embrith, which takes about a minute; if the install or the start fails, the next run restores the old version automatically.");
        p("可选内容", "What you can choose");
        p("可以选 Kindle 存储里任意一张 png / jpg 图片, 或一个文件夹(随机切换).", "You can choose any png / jpg picture in the Kindle storage, or a folder (picked at random).");
        p("选取方式", "How to choose");
        p("进入文件夹后, 点\"使用此文件夹\"即按该文件夹随机, 点图片即用这一张. 图片会按屏幕裁切并转成灰度.", "Inside a folder, tap \"Use this folder\" to pick at random from it, or tap a picture to use that one. Pictures are cropped to the screen and turned into gray.");
        p("褪色对桌面壁纸和锁屏图片分别设置.", "Fade is set separately for the desktop wallpaper and the lock screen picture.");
        p("选了文件夹时", "When a folder is chosen");
        p("锁屏图片每次锁屏随机换一张; 桌面壁纸按\"文件夹切换\"的间隔随机换.", "The lock screen picture changes at random every time the screen is locked; the desktop wallpaper changes at random at the interval of \"Folder switching\".");
        p("点上下箭头调整顺序; 预览是桌面上的样子 (缩小), 排在前面的先放", "Tap the up and down arrows to change the order; the preview shows the desktop (scaled down), widgets earlier in the list are placed first");
        p("打不开这本漫画", "This comic cannot be opened");
        p("里面没有图片", "There are no pictures in it");
        p("正在打开...", "Opening...");
        p("已经是最后一页", "This is the last page");
        p("已经是第一页", "This is the first page");
        p("取消书签", "Remove bookmark");
        p("添加书签", "Add bookmark");
        p("书签", "Bookmarks");
        p("跳页", "Go to page");
        p("目录", "Contents");
        p("方向: 右到左", "Direction: right to left");
        p("方向: 左到右", "Direction: left to right");
        p("适应: 适宽", "Fit: width");
        p("适应: 整页", "Fit: whole page");
        p("拆分跨页: 开", "Split spreads: on");
        p("拆分跨页: 关", "Split spreads: off");
        p("页码不对", "Wrong page number");
        p("这本书没有目录", "This book has no contents");
        p("已取消这页的书签", "Bookmark of this page removed");
        p("已加书签", "Bookmark added");
        p("还没有书签", "No bookmarks yet");
        p("这本 epub 不是漫画", "This epub is not a comic");
        p("不是漫画文件", "Not a comic file");
        p("RAR (cbr) 格式暂不支持, 请先转成 cbz", "The RAR (cbr) format is not supported yet, convert it to cbz first");
        p("7z (cb7) 格式暂不支持, 请先转成 cbz", "The 7z (cb7) format is not supported yet, convert it to cbz first");
        p("不认识这种漫画格式", "Unknown comic format");
        p("这一页太大", "This page is too large");
        p("epub 文件格式不对", "The epub file format is wrong");
        p("epub 里找不到内容清单", "The content list was not found in the epub");
        p("这本书没有可读的内容", "This book has nothing to read");
        p("这一章太大", "This chapter is too large");
        p("选中放书的文件夹, 可以选多个; 选中上级文件夹后, 它下面的子文件夹不用再选.", "Choose the folders that hold your books, you can choose several; after you choose a parent folder, its subfolders need not be chosen again.");
        p("共享", "Sharing");
        p("Kindle 上的文件夹要先在 设置 > 共享文件夹 里共享, 共享后会出现在 Kindle 文件夹下.", "A folder on the Kindle must first be shared in Settings > Shared folders, after that it appears under the Kindle folder.");
        p("存储", "Storage");
        p("已选文件夹", "Chosen folders");
        p("不能选择整个存储", "The whole storage cannot be chosen");
        p("已随上级文件夹选中", "Chosen with the parent folder");
        p("选择这个文件夹", "Choose this folder");
        p("这个文件夹打不开", "This folder cannot be opened");
        p("含已选的子文件夹", "Contains chosen subfolders");
        p("还没有选择文件夹", "No folder chosen yet");
        p("kfx 数据损坏", "The kfx data is damaged");
        p("这个 kfx 有 DRM 或压缩, 不支持", "This kfx has DRM or compression and is not supported");
        p("不是 kfx 文件", "Not a kfx file");
        p("kfx 里没有阅读顺序", "There is no reading order in the kfx");
        p("kfx 里没有读到文字", "No text was found in the kfx");
        p("最近", "Recent");
        p("这里没有文件夹和书籍", "No folders or books here");
        p("选择放书的文件夹, 这里只显示它们", "Choose the folders that hold your books, only they are shown here");
        p("mobi 文件损坏", "The mobi file is damaged");
        p("开头", "Start");
        p("这本书是空的", "This book is empty");
        p("不是 mobi 文件", "Not a mobi file");
        p("这本书有 DRM, 不能阅读", "This book has DRM and cannot be read");
        p("文件太大", "The file is too large");
        p("KF8 索引损坏", "The KF8 index is damaged");
        p("KF8 缺少索引", "The KF8 index is missing");
        p("上边距", "Top margin");
        p("下边距", "Bottom margin");
        p("左边距", "Left margin");
        p("右边距", "Right margin");
        p("打不开这本书", "This book cannot be opened");
        p("字号", "Font size");
        p("行距", "Line spacing");
        p("首行缩进", "First line indent");
        p("跳到", "Go to");
        p("(空白)", "(blank)");
        p("文件太大, 超过 24 MB", "The file is too large, over 24 MB");
        p("全文", "Whole text");
        p("SSH 端口 2222,用户 root,可以用密钥文件,密码或免密登录.", "SSH port 2222, user root; key file, password or no-password login is supported.");
        p("来源优先级", "Source priority");
        p("Embrith 内置 dropbear \u2192 KOReader \u2192 Kindle 自带; 自定义密码和免密只对内置 dropbear 有效.", "Built-in Embrith dropbear > KOReader > Kindle's own; custom password and no-password only work with the built-in dropbear.");
        p("主机密钥已变化", "Host key changed");
        p("电脑提示主机密钥已变化(REMOTE HOST IDENTIFICATION HAS CHANGED)时,在电脑上执行 ssh-keygen -R \"[IP]:2222\".", "If the computer warns that the host key has changed (REMOTE HOST IDENTIFICATION HAS CHANGED), run ssh-keygen -R \"[IP]:2222\" on the computer.");
        p("选中的应用(从桌面启动)里, 比阈值浅的背景一律显示为纯白, 文字和图片的深色部分不变, 让阅读页的灰色底色在墨水屏上更干净.", "In the chosen apps (started from the home screen), backgrounds lighter than the threshold are shown as pure white; dark text and image parts stay. This keeps the gray page background of reading apps clean on e-ink.");
        p("灰度不小于这个数的颜色按纯白显示, 数字越小, 过滤的范围越大. 注意: 阈值太小时, 浅灰色的图片细节和浅色文字会一起被过滤成白色而看不见; 阈值对所有选中的应用共用, 没选中的应用不受影响. 建议先用默认值, 底色仍发灰就调小, 图片发白就调大.", "Colors with a gray level at or above this number are shown as pure white; a smaller number whitens more. Note: with a very small threshold, light gray image details and light text are whitened away and become invisible; the threshold is shared by all chosen apps, apps that are not chosen are not affected. Start with the default; lower it if the background is still gray, raise it if images turn white.");
        p("设了壁纸并打开全局桌面应用时, 系统页面翻页改用局部刷新(只刷变化的文字, 不用翻页动画), 避免壁纸出现黑影; 没有壁纸时用翻页动画.", "When a wallpaper is set and the global desktop app is on, system page turns use partial refresh (only the changed text is refreshed, no page turn animation) to avoid dark shadows on the wallpaper; without a wallpaper the page turn animation is used.");
        p("一律用翻页动画.", "Always use the page turn animation.");
        p("一律不用翻页动画.", "Never use the page turn animation.");
    }
}