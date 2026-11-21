/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * MenuActivity: The settings menu (home page, first-level entries, second-level pages) and the pull-down panel in
 * its own mode. */
package com.embrith.home;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import com.embrith.library.FolderPickActivity;
import com.embrith.library.LibTools;
import com.embrith.library.Screens;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Map;

/**
 * "设置"菜单. 首页(tab = -1, 底部栏的"设置"打开它): 状态栏首行(设备名, 日期时间, 电量), 设置主页自定义区域(组件, 见 Slots, 在 个性化 > 设置组件 里选择),
 * 然后是并列的一级入口卡片(电源 网络 显示 个性化 书库 系统), 底部是和桌面相同的底部栏. 下拉手势打开的是下拉面板(tab = -2): 状态栏首行, 控制区(ControlPanel: 方框, 亮度和色温, 自定义按钮), 底部整条 ^ 关闭. 
 * 点入口进入该项的二级页(同一个 Activity, 带 tab 参数), 按分组各套一个圆角框, 分组标题栏右侧的信息标识点击后弹出说明窗口. 
 * 开关项用开关按钮, 只改该行; 多选一项进入三级页 ChoiceActivity. 
 */
public class MenuActivity extends Activity {
    private static final String[] TABS = { "电源", "网络", "显示", "个性化", "书库", "系统" };
    private static final int[] TAB_ICONS = { Icons.BATTERY, Icons.WIFI, Icons.MONITOR, Icons.PALETTE, Icons.LIBRARY, Icons.SETTINGS };
    // 入口卡片的子项名: 取各页现有的分组标题
    private static final String[][] TAB_SUBS = {
        { "电源状态", "休眠", "系统操作" },
        { "无线连接", "网络服务" },
        { "显示布局", "字体", "屏幕效果", "屏幕刷新" },
        { "外观", "下拉面板", "组件设置", "桌面应用" },
        { "书库位置", "最近阅读", "阅读目标", "排序" },
        { "设备信息", "地区与语言", "应用管理", "系统维护", "高级与兼容性" } };
    static final String[] SB_IDS = { "lock", "clear", "light", "exit", "none" };
    static final String[] SB_TEXT = { "休眠", "刷新屏幕", "开关前光", "退出Embrith", "无" };

    // 显示: 刷新(ctl/conf, kdispd)
    private static final String[] FLASH = { "1", "3", "6", "10", "0" };
    private static final String[] FLASH_TEXT = {
        "每翻 1 页整屏闪一次(最干净,最慢)", "每翻 3 页整屏闪一次", "每翻 6 页整屏闪一次(默认,同 Kindle)", "每翻 10 页整屏闪一次", "从不闪(残影要手动清除)" };
    private static final String[] FLASH_SHORT = { "每 1 页", "每 3 页", "每 6 页", "每 10 页", "从不" };
    // 显示: 熄屏(ctl/conf SLEEP_MIN, embrith.sh 看门狗读取)
    private static final String[] SLEEP = { "2", "5", "10", "15", "30", "0" };
    private static final String[] SLEEP_TEXT = { "2 分钟", "5 分钟", "10 分钟(默认)", "15 分钟", "30 分钟", "从不" };
    // 显示: 显示大小(wm density)与字体大小(font_scale), 交给 Kindle 侧在容器里执行, 影响所有应用
    private static final String[] DENSITY = { "260", "300", "340", "380" };
    private static final String[] DENSITY_TEXT = { "较小", "标准(默认)", "较大", "最大" };
    private static final String[] FONT = { "0.9", "1.0", "1.15", "1.3" };
    private static final String[] FONT_TEXT = { "小", "标准(默认)", "大", "特大" };

    private final Handler handler = new Handler();
    private static final int PANEL = -2;   // tab value of the pull-down panel
    private static MenuActivity homeInst;    // the settings home that is shown
    private LinearLayout homeRoot;           // the root of the settings home (its background is the desktop wallpaper when wall_lib is on)
    private static MenuActivity panelInst;   // the pull-down panel that is shown (the pull-down toggles it)

    static boolean panelShown() { return panelInst != null && !panelInst.isFinishing(); }

    static void closePanel() {
        PanelWindow.hide();
        if (panelShown()) panelInst.finish();
    }

    /** Both screens (USB and sleep take the screen). */
    static void closeHome() {
        closePanel();
        if (homeInst != null && !homeInst.isFinishing()) homeInst.finish();
    }

    boolean isHome() { return tab == -1; }

    private LinearLayout dockBar;   // the bottom bar of the settings home (null on the second-level pages and in the pull-down panel)

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent e) {
        boolean r = super.dispatchTouchEvent(e);
        if (tab == -1) Dock.pageTouch(this, dockBar, e);   // a horizontal swipe switches the page
        return r;
    }

    // 首页和下拉面板: 控制区(方框, 亮度和色温, 自定义按钮)
    private ControlPanel cp;                // the control area of the pull-down panel (null on the settings home)
    private SettingsHomeBox homeBox;         // the widget area and the entries of the settings home
    private LinearLayout list;
    private int tab = -1;                 // -1 = 设置首页, 0.. = 二级页

    // 当前页上需要局部更新的行(切换一级时重建)
    private Ui.Row battRow, sshRow, ipRow, nameRow, memRow, animRow, verRow;
    private ScrollView scroller;
    private android.widget.TextView contrastValue;
    private Ui.SwitchRow wifiRow, btRow, pskRow, devRow;
    private String lang;
    private int margin;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        lang = Prefs.getStr(this, "lang", "zh");
        margin = Frames.edgeDp(this);
        tab = getIntent().getIntExtra("tab", -1);
        if (getIntent().getBooleanExtra("panel", false)) {
            tab = PANEL;
            panel();
            return;
        }
        if (tab < 0 || tab >= TABS.length) {
            tab = -1;
            home();
            return;
        }
        LinearLayout body = Ui.page(this, TABS[tab], null, null, true);   // 内容超过一屏时, 右侧悬浮上下翻页按钮
        scroller = (ScrollView) body.getParent();

        // 二级: 各分组的容器
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        body.addView(list, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private static final int GAP_V = ControlPanel.GAP_V;
    private static final String[] HB_DEF = ControlPanel.HB_DEF;

    /**
     * Settings home. From top to bottom: the status row, the widget area (2h + g high, the widgets are chosen in Personalize > Settings widgets),
     * six first-level entries of the same height h that fill the rest of the screen, and the bottom bar. All gaps are the frame gap g (Frames.GAP);
     * the sizes are worked out by SettingsHomeBox.
     */
    private void home() {
        homeInst = this;
        LinearLayout root = new LinearLayout(this);
        homeRoot = root;
        root.setOrientation(LinearLayout.VERTICAL);
        applyWall();
        root.addView(TopBar.build(this), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 68)));   // the bar of the desktop

        // the first-level entries: one per row, the same height h; SettingsHomeBox sets h and puts the widget area above them
        java.util.List<View> cards = new java.util.ArrayList<View>();
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            StringBuilder sub = new StringBuilder();
            for (String t : TAB_SUBS[i]) {
                if (sub.length() > 0) sub.append(" / ");
                sub.append(Ui.t(this, t));
            }
            Ui.Card card = Ui.card(this, TAB_ICONS[i], TABS[i], sub.toString(), false, new View.OnClickListener() {
                @Override public void onClick(View v) { startActivity(new Intent(MenuActivity.this, MenuActivity.class).putExtra("tab", idx)); }
            });
            card.setGravity(android.view.Gravity.CENTER_VERTICAL);
            card.setPadding(Ui.dp(this, 24), 0, Ui.dp(this, 24), 0);
            cards.add(card);
        }
        homeBox = new SettingsHomeBox(this, cards);
        root.addView(homeBox, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        dockBar = Dock.build(this, "settings");
        root.addView(dockBar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        setContentView(root);
        scroller = null;
    }

    /** The desktop wallpaper behind the settings home when wall_lib is on, else white. */
    private void applyWall() {
        android.graphics.drawable.Drawable d = Widgets.wallDrawable(this);
        homeRoot.setBackground(d != null ? d : new android.graphics.drawable.ColorDrawable(android.graphics.Color.WHITE));
    }

    /** Pull-down panel (the fallback when the floating window cannot be added, see PanelWindow): the same content in a translucent activity. */
    private void panel() {
        panelInst = this;
        cp = new ControlPanel(this, null, null);
        setContentView(PanelWindow.content(this, cp, new Runnable() { @Override public void run() { finish(); } }));
        getWindow().setGravity(android.view.Gravity.TOP);
        getWindow().setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private static String onOff(String v) {
        return "1".equals(v) ? "开" : "0".equals(v) ? "关" : "";
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!Prefs.getStr(this, "lang", "zh").equals(lang) || margin != Frames.edgeDp(this)) { recreate(); return; }
        if (tab == -1) { Dock.refresh(this, dockBar); applyWall(); }
        render();
        if (tab < 0 && cp != null) cp.start();
        if (tab == -1 && homeBox != null) homeBox.area().update();
    }

    @Override
    protected void onPause() {
        if (cp != null) cp.stop();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (homeInst == this) homeInst = null;
        if (panelInst == this) panelInst = null;
        if (cp != null) cp.stop();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    /** 切换一级或回到本页时重建二级内容.  */
    private void render() {
        handler.removeCallbacksAndMessages(null);
        if (tab < 0) { update(); return; }
        list.removeAllViews();
        battRow = sshRow = ipRow = nameRow = memRow = verRow = null;
        wifiRow = btRow = pskRow = devRow = null;
        switch (tab) {
            case 0: power(); break;
            case 1: network(); break;
            case 2: display(); break;
            case 3: personal(); break;
            case 4: library(); break;
            default: system(); break;
        }
        update();
    }

    /** 只更新会变的值(不重建).  */
    private void update() {
        Map<String, String> st = Ctl.status();
        if (tab < 0 && cp != null) cp.update();
        if (tab == -1 && homeBox != null) homeBox.area().update();
        if (battRow != null) {
            int batt = Ctl.statusInt(st, "BATT", -1);
            battRow.setValue(batt >= 0 ? batt + "%" : "未知");
        }
        if (verRow != null) {
            String ver = st.get("VER");
            verRow.setValue(ver == null || ver.length() == 0 ? "未知" : ver);
        }
        if (wifiRow != null) {
            wifiRow.setOn("1".equals(st.get("WIFI")));
            wifiRow.setNote(st.get("WIFI") == null || "-1".equals(st.get("WIFI")) ? "未知" : "");
        }
        if (btRow != null) {
            btRow.setOn("1".equals(st.get("BT")));
            btRow.setNote(st.get("BT") == null || "-1".equals(st.get("BT")) ? "未知" : "");
        }
        if (sshRow != null) {
            String ssh = st.get("SSH");
            sshRow.setValue("1".equals(ssh) ? "开" : "关");
        }
        if (ipRow != null) {
            String ip = st.get("IP");
            ipRow.setValue(ip == null || ip.length() == 0 ? "未连接" : ip);
        }
    }

    /** Kindle 侧约 2 秒处理请求, 几秒后才发布新状态.  */
    private void later() {
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(new Runnable() { @Override public void run() { update(); } }, 4000);
        handler.postDelayed(new Runnable() { @Override public void run() { update(); } }, 8000);
    }

    /** 二级页里的一个分组: 标题栏(有说明时右侧带信息标识)加圆角框, 返回圆角框. */
    private LinearLayout section(String title, String... infos) {
        return Ui.section(this, list, title, infos);
    }

    private View.OnClickListener open(final Class<?> cls) {
        return new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(MenuActivity.this, cls)); }
        };
    }

    /** Opens a widget setting page for a set of widget slots (sh = settings home, dh = desktop). */
    private View.OnClickListener open(final Class<?> cls, final String set) {
        return new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(MenuActivity.this, cls).putExtra("set", set)); }
        };
    }

    // ------------------------------------------------------------------ 电源
    private void power() {
        LinearLayout g = section("电源状态");
        battRow = Ui.row(this, "电量", "", null);
        Ui.add(g, battRow);

        g = section("休眠", "休眠与唤醒\t休眠同 Kindle:按电源键或合上磁吸保护套即休眠,显示锁屏图片并暂停安卓,再按电源键或打开保护套唤醒.睡眠定时器在本页设置.");
        Ui.add(g, Ui.row(this, "立即休眠", "", new View.OnClickListener() {
            @Override public void onClick(View v) { Ctl.request("sleep"); }
        }));
        Ui.add(g, choiceRow("睡眠定时器", SLEEP, SLEEP_TEXT, null, "ctl", "SLEEP_MIN", "10",
            "没有触摸就自动休眠(同按电源键)的时限", Ctl.get("SLEEP_MIN", "10")));

        g = section("系统操作", "重启 Embrith\t重启只重启安卓容器, 不会回到 Kindle, 约一分钟后回到桌面.",
            "退出 Embrith\t退出后回到 Kindle 原来的界面. 任何时候单指按住屏幕左上角 3 秒会弹出菜单: 重启 Embrith, 退出 Embrith, 重启 Kindle.");
        Ui.add(g, Ui.row(this, "重启 Embrith", "", new View.OnClickListener() {
            @Override public void onClick(View v) {
                new Ui.Builder(MenuActivity.this).setTitle("重启 Embrith")
                    .setMessage("重启只重启安卓容器, 不会回到 Kindle, 约一分钟后回到桌面.")
                    .setPositiveButton("重启", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int w) {
                            Ui.toast(MenuActivity.this, "正在重启 Embrith...", false);
                            Ctl.request("restart");
                        }
                    })
                    .setNegativeButton("取消", null).show();
            }
        }));
        Ui.add(g, Ui.nav(this, "重启并更新", "", open(UpdateActivity.class)));
        Ui.add(g, Ui.row(this, "退出 Embrith", "回到 Kindle", new View.OnClickListener() {
            @Override public void onClick(View v) {
                new Ui.Builder(MenuActivity.this).setTitle("退出 Embrith")
                    .setMessage("退出后回到 Kindle 原来的界面.")
                    .setPositiveButton("退出", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int w) {
                            Ui.toast(MenuActivity.this, "正在回到 Kindle...", false);
                            Ctl.request("exit");
                        }
                    })
                    .setNegativeButton("取消", null).show();
            }
        }));
    }

    // ------------------------------------------------------------------ 网络
    private void network() {
        LinearLayout g = section("无线连接",
            "Wi-Fi 和蓝牙\tWi-Fi 和蓝牙属于 Kindle. 这里可以开关, 也可以在已保存的网络和已配对的蓝牙设备之间切换; 新蓝牙设备请先用 Kindle 自己的设置配对一次.",
            "允许输入 Wi-Fi 密码\t默认只能连接 Kindle 里已经保存的 Wi-Fi 网络. 打开后, 在网络页的选择 Wi-Fi 网络里可以直接输入新网络的密码并加入. "
            + "注意: 密码会以明文写入控制目录的一个临时文件(仅文件所有者可读, Kindle 侧读取后立即删除), 再交给 Kindle 的 wifid 保存; "
            + "Kindle 保存的网络信息里同样含明文密码, 发送日志或截图前请先去掉. 关闭后只能连接已保存的网络, 已保存的网络不受影响. 打开时会先弹出安全说明, 确认后才生效.");
        wifiRow = Ui.switchRow(this, "Wi-Fi", false, new Ui.OnToggle() {
            @Override public void toggled(boolean on) { Ctl.request("wifi"); later(); }
        });
        btRow = Ui.switchRow(this, "蓝牙", false, new Ui.OnToggle() {
            @Override public void toggled(boolean on) { Ctl.request("bt"); later(); }
        });
        Ui.add(g, wifiRow);
        Ui.add(g, Ui.nav(this, "选择 Wi-Fi 网络", "", new android.view.View.OnClickListener() {
            @Override public void onClick(android.view.View v) { NetPickActivity.open(MenuActivity.this, "wifi"); }
        }));
        Ui.add(g, btRow);
        Ui.add(g, Ui.nav(this, "选择蓝牙设备", "", new android.view.View.OnClickListener() {
            @Override public void onClick(android.view.View v) { NetPickActivity.open(MenuActivity.this, "bt"); }
        }));
        pskRow = Ui.switchRow(this, "允许输入 Wi-Fi 密码", "1".equals(Ctl.get("WIFI_PSK_INPUT", "0")), new Ui.OnToggle() {
            @Override public void toggled(boolean on) { if (on) askPskRisk(); else Ctl.set("WIFI_PSK_INPUT", "0"); }
        });
        Ui.add(g, pskRow);

        g = section("网络服务", "SSH 远程登录\tSSH 端口 2222,用户 root,可以用密钥文件,密码或免密登录.",
            "来源优先级\tEmbrith 内置 dropbear → KOReader → Kindle 自带; 自定义密码和免密只对内置 dropbear 有效.",
            "主机密钥已变化\t电脑提示主机密钥已变化(REMOTE HOST IDENTIFICATION HAS CHANGED)时,在电脑上执行 ssh-keygen -R \"[IP]:2222\".");
        sshRow = Ui.nav(this, "SSH 远程登录", "", open(SshActivity.class));
        Ui.add(g, sshRow);
        Ui.add(g, Ui.nav(this, "本机网络", ethState(), open(NetworkActivity.class)));   // 安卓容器的 eth0 (由 Kindle 的 Wi-Fi 转发), 页内有 DNS 设置
        ipRow = Ui.row(this, "Kindle 的 IP 地址", "", null);
        Ui.add(g, ipRow);
    }

    /** Turning the password switch on needs an explicit yes: the password travels as plain text through ctl/ for a moment. */
    private void askPskRisk() {
        new Ui.Builder(this).setTitle("允许输入 Wi-Fi 密码")
            .setMessage("开启后, 在选择网络时可以直接输入新网络的密码.\n\n"
                + "安全说明: 密码会以明文写入控制目录的一个临时文件(仅文件所有者可读, Kindle 侧读取后立即删除), 再交给 Kindle 的 wifid 保存. "
                + "Kindle 保存的网络信息里同样含明文密码, 发送日志或截图前请先去掉.\n\n"
                + "不开启时只能扫描并连接已保存的网络.")
            .setPositiveButton("我了解, 开启", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) { Ctl.set("WIFI_PSK_INPUT", "1"); }
            })
            .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) { if (pskRow != null) pskRow.setOn(false); }
            })
            .setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override public void onCancel(DialogInterface d) { if (pskRow != null) pskRow.setOn(false); }
            }).show();
    }

    private String ethState() {
        try {
            NetworkInterface ni = NetworkInterface.getByName("eth0");
            if (ni == null || !ni.isUp()) return "未连接";
            Enumeration<InetAddress> e = ni.getInetAddresses();
            while (e.hasMoreElements()) {
                InetAddress a = e.nextElement();
                if (!a.isLoopbackAddress() && a.getAddress().length == 4) return a.getHostAddress();
            }
            return "未连接";
        } catch (SocketException e) {
            return "未知";
        }
    }

    // ------------------------------------------------------------------ 个性化
    private void personal() {
        LinearLayout g = section("外观");
        Ui.add(g, Ui.nav(this, "壁纸与锁屏图片", "", open(WallpaperActivity.class)));
        Ui.add(g, Ui.nav(this, "手势", "", open(GestureActivity.class)));
        Ui.add(g, Ui.nav(this, "悬浮按钮位置", "恢复默认", new View.OnClickListener() {
            @Override public void onClick(View v) {
                FloatPos.reset(MenuActivity.this);
                Ui.toast(MenuActivity.this, "悬浮按钮已恢复默认位置", false);
            }
        }));

        g = section("下拉面板", "控制区\t下拉面板上方的控制区: 左边方框固定为飞行模式,Wi-Fi,蓝牙,反色; 中间是亮度和色温条; 最右边两个圆形按钮可以自定义.");
        for (int k = 0; k < HB_DEF.length; k++) Ui.add(g, sbRow("hb" + (k + 1), "自定义按钮 " + (k + 1), HB_DEF[k]));

        g = section("组件设置", "组件设置\t设置组件是设置首页上方的自定义区域, 桌面组件是桌面上方的自定义区域, 宽度都同页面框. 在组件页里按尺寸选择各放几个(受剩余空间限制), 设置每个组件的类型, 排列顺序, 并预览整个区域; 所有边缘按格对齐, 没有被组件覆盖的格子保持空白. 桌面组件最高占满除一行应用外的全部高度, 其余留给应用区. 回到设置首页或桌面后在组件上长按可以设置内容(图片, 文字, 时钟样式和时区, 按钮动作, 屏幕时间的应用). 屏幕时间需要给桌面授予使用情况访问权限.");
        Ui.add(g, Ui.nav(this, "设置组件", "", open(SlotOrderActivity.class, "sh")));
        Ui.add(g, Ui.nav(this, "桌面组件", "", open(SlotOrderActivity.class, "dh")));

        g = section("桌面应用", "每行显示\t桌面上每行放几个应用图标, 可选 4, 5, 6 个. 图标之间的间距自动调整.");
        Ui.add(g, choiceRow("每行显示", new String[] { "4", "5", "6" }, new String[] { "4 个(默认)", "5 个", "6 个" }, null, "pref", "home_cols", "4",
            "桌面每行放几个应用图标", Prefs.getStr(this, "home_cols", "4")));

        g = section("系统页边距", "页边距\t所有页面的框到屏幕左右和底部边缘的距离, 设置后各页面自动重新排版. 默认 22 dp.");
        Ui.add(g, choiceRow("系统页边距", MARGIN, MARGIN_TEXT, null, "pref", Frames.KEY, String.valueOf(Frames.EDGE), null, String.valueOf(Frames.edgeDp(this))));

        g = section("底部栏", "底部栏页面\t勾选的页面显示在底部栏, 按勾选先后从左往右排列, 方框里的数字是位置; 在这些页面之间左右滑动切换, 两端相连. 至少保留一项.");
        final Ui.CheckRow[] drows = new Ui.CheckRow[Dock.IDS.length];
        final Runnable dindex = new Runnable() {
            @Override public void run() {
                java.util.List<String> ord = Dock.order(MenuActivity.this);
                for (int k = 0; k < drows.length; k++) drows[k].setIndex(ord.indexOf(Dock.IDS[k]) + 1);
            }
        };
        for (int i = 0; i < drows.length; i++) {
            final String did = Dock.IDS[i];
            final int at = i;
            drows[i] = Ui.indexRow(this, Dock.TEXT[i], "", Dock.isOn(this, did), new Ui.OnToggle() {
                @Override public void toggled(boolean on) {
                    if (!on && Dock.order(MenuActivity.this).size() <= 1) {
                        drows[at].setOn(true);
                        Ui.toast(MenuActivity.this, "至少保留一项", false);
                        return;
                    }
                    Dock.setOn(MenuActivity.this, did, on);
                    dindex.run();
                }
            });
            Ui.add(g, drows[i]);
        }
        dindex.run();
    }

    // ------------------------------------------------------------------ 书库
    private static final String[] LCOLS = { "2", "3", "4", "5" };
    private static final String[] LCOLS_TEXT = { "2 列", "3 列(默认)", "4 列", "5 列" };
    private static final String[] LCOLS_SHORT = { "2 列", "3 列", "4 列", "5 列" };
    private static final String[] LROWS = { "0", "2", "3", "4", "5" };
    private static final String[] LROWS_TEXT = { "自动(封面 3:4, 可上下滑动)(默认)", "2 行", "3 行", "4 行", "5 行" };
    private static final String[] LROWS_SHORT = { "自动", "2 行", "3 行", "4 行", "5 行" };
    private static final String[] LNOCOVER = { "ext", "title", "both", "ta", "blank" };
    private static final String[] LNOCOVER_TEXT = { "格式名(如 EPUB)(默认)", "书名", "书名和格式名", "书名和作者", "空白" };
    private static final String[] LINFO = { "full", "fmt", "none" };
    private static final String[] LINFO_TEXT = { "格式和大小(默认)", "只显示格式", "不显示" };
    private static final String[] LLABEL = { "file", "title", "title_author", "author_title" };
    private static final String[] LLABEL_TEXT = { "文件名(默认)", "书名", "书名 - 作者", "作者 - 书名" };
    private static final String[] LGROUP = { "none", "author", "series", "format", "initial", "tag" };
    private static final String[] LGROUP_TEXT = { "不分组(默认)", "作者", "系列", "格式", "首字母", "标签" };
    private static final String[] LSORT = { "name", "natural", "pinyin", "date", "size", "type", "read" };
    private static final String[] LSORT_TEXT = { "名称(默认)", "名称(数字按大小, 如 2 在 10 前)", "名称(中文按拼音)", "修改时间(新的在前)",
        "大小(大的在前)", "类型", "最近阅读(没读过的在后)" };

    private void library() {
        LinearLayout g = section("书库位置", "书库文件夹\t选择放书的文件夹, 书库首页只显示它们.");
        Ui.add(g, Ui.nav(this, "选择文件夹", FolderPickActivity.chosen() + Ui.t(this, " 个文件夹"), new View.OnClickListener() {
            @Override public void onClick(View v) { FolderPickActivity.open(MenuActivity.this); }
        }));
        Ui.add(g, Ui.switchRow(this, "打开前询问", "1".equals(Prefs.getStr(this, "lib_ask", "0")), new Ui.OnToggle() {
            @Override public void toggled(boolean on) { Prefs.putStr(MenuActivity.this, "lib_ask", on ? "1" : "0"); }
        }));

        g = section("最近阅读");
        Ui.add(g, choiceRow("最近阅读显示", new String[] { "1", "2" }, new String[] { "一行封面(默认)", "两行封面" }, null, "pref", "recent_rows", "1",
            "书库的阅读页里, 最近阅读显示几行封面; 每行本数同 \"每行显示\"", Prefs.getStr(this, "recent_rows", "1")));

        g = section("阅读目标", "日目标\t阅读记录的概览和打卡按这个目标计算, 屏幕时间也算阅读时间.");
        Ui.add(g, choiceRow("日目标", new String[] { "0", "15", "30", "45", "60", "90", "120", "180" },
            new String[] { "不设目标", "15 分钟", "30 分钟", "45 分钟", "1 小时", "1.5 小时", "2 小时", "3 小时" }, null, "pref", "goal_day",
            Screens.goalDef(), null, Prefs.getStr(this, "goal_day", Screens.goalDef())));

        g = section("页面布局", "网格模式\t只影响网格模式. 封面始终是 3:4, 设置每行显示和每屏行数后, 间距自动调整. 文件夹的封面显示文件夹名.",
            "最近页显示所在文件夹\t只影响列表模式.",
            "打开前询问\t点书后先弹框确认, 防止在墨水屏上误触.");
        Ui.add(g, choiceRow("列表或网格", new String[] { "list", "grid" }, new String[] { "列表(默认)", "网格" }, null, "pref", "lib_view", "list",
            "书库和最近阅读用列表还是网格显示", Prefs.getStr(this, "lib_view", "list")));
        Ui.add(g, choiceRow("每行显示", LCOLS, LCOLS_TEXT, LCOLS_SHORT, "pref", "lib_cols", "3", "网格模式每行放几本书", Prefs.getStr(this, "lib_cols", "3")));
        Ui.add(g, choiceRow("每屏行数", LROWS, LROWS_TEXT, LROWS_SHORT, "pref", "lib_rows", "0", "网格模式一屏放几行; 自动时封面按 3:4, 超过一屏上下滑动", Prefs.getStr(this, "lib_rows", "0")));
        Ui.add(g, choiceRow("无封面时显示", LNOCOVER, LNOCOVER_TEXT, null, "pref", "lib_nocover", "ext", "网格模式里没有封面图片的书显示什么", Prefs.getStr(this, "lib_nocover", "ext")));
        Ui.add(g, Ui.switchRow(this, "封面角标", !"off".equals(Prefs.getStr(this, "lib_badge", "on")), new Ui.OnToggle() {
            @Override public void toggled(boolean on) { Prefs.putStr(MenuActivity.this, "lib_badge", on ? "on" : "off"); }
        }));
        Ui.add(g, choiceRow("书的名称", LLABEL, LLABEL_TEXT, null, "pref", "lib_label", "file", "书在列表和网格里显示什么名称; 书名和作者来自书里的信息, 第一次显示时会在后台读取, 没有的显示文件名", Prefs.getStr(this, "lib_label", "file")));
        Ui.add(g, Ui.switchRow(this, "显示名称", !"0".equals(Prefs.getStr(this, "lib_name", "1")), new Ui.OnToggle() {
            @Override public void toggled(boolean on) { Prefs.putStr(MenuActivity.this, "lib_name", on ? "1" : "0"); }
        }));
        Ui.add(g, choiceRow("列表里书名后显示", LINFO, LINFO_TEXT, null, "pref", "lib_info", "full", "列表模式下每本书右侧的小字", Prefs.getStr(this, "lib_info", "full")));
        Ui.add(g, Ui.switchRow(this, "最近页显示所在文件夹", "1".equals(Prefs.getStr(this, "lib_parent", "0")), new Ui.OnToggle() {
            @Override public void toggled(boolean on) { Prefs.putStr(MenuActivity.this, "lib_parent", on ? "1" : "0"); }
        }));

        g = section("TXT 章节", "章节正则\t自己写一个正则式, 书里的每一行(去掉行首行尾的空白)只要能匹配上就算章节标题; 对不上或少于两章时用自动检测. 改了以后重新打开书生效, 阅读位置可能偏移.\n\n"
            + "例子\t^\\s*第[0-9一二三四五六七八九十零〇百千两]+[章回节卷集部篇].{0,30}$");
        txtRow = Ui.nav(this, "章节正则", txtRegexShown(), new View.OnClickListener() {
            @Override public void onClick(View v) { editTxtRegex(); }
        });
        Ui.add(g, txtRow);

        g = section("排序");
        Ui.add(g, choiceRow("排序方式", LSORT, LSORT_TEXT, null, "pref", "lib_sort", "name", null, Prefs.getStr(this, "lib_sort", "name")));
        Ui.add(g, choiceRow("文件夹内分组", LGROUP, LGROUP_TEXT, null, "pref", "lib_group", "none", "把文件夹里的书按条件分成虚拟文件夹, 点进去才看到书. 作者, 系列, 标签来自书里的信息(系列只有 epub 有), 没有的归入未知作者, 无系列, 无标签", Prefs.getStr(this, "lib_group", "none")));
        Ui.add(g, Ui.switchRow(this, "倒序", "1".equals(Prefs.getStr(this, "lib_rev", "0")), new Ui.OnToggle() {
            @Override public void toggled(boolean on) { Prefs.putStr(MenuActivity.this, "lib_rev", on ? "1" : "0"); }
        }));
        Ui.add(g, Ui.switchRow(this, "文件夹在前", !"0".equals(Prefs.getStr(this, "lib_dirfirst", "1")), new Ui.OnToggle() {
            @Override public void toggled(boolean on) { Prefs.putStr(MenuActivity.this, "lib_dirfirst", on ? "1" : "0"); }
        }));

        g = section("维护");
        Ui.add(g, Ui.row(this, "清空最近记录", "", new View.OnClickListener() {
            @Override public void onClick(View v) {
                new Ui.Builder(MenuActivity.this).setTitle("清空最近记录")
                    .setMessage("只清空最近阅读的列表, 阅读位置和书签不受影响.")
                    .setPositiveButton("清空", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int w) {
                            LibTools.clearRecent();
                            Ui.toast(MenuActivity.this, "已清空", false);
                        }
                    })
                    .setNegativeButton("取消", null).show();
            }
        }));
        Ui.add(g, Ui.row(this, "清除封面缓存", "", new View.OnClickListener() {
            @Override public void onClick(View v) {
                new Ui.Builder(MenuActivity.this).setTitle("清除封面缓存")
                    .setMessage("下次显示网格时会重新生成封面, 第一次会慢一些.")
                    .setPositiveButton("清除", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int w) {
                            Ui.toast(MenuActivity.this, "已清除 " + LibTools.clearCovers() + " 个文件", false);
                        }
                    })
                    .setNegativeButton("取消", null).show();
            }
        }));
    }

    // ------------------------------------------------------------------ 显示
    private Ui.Row choiceRow(final String label, final String[] ids, final String[] text, final String[] shortText,
                             final String store, final String key, final String def, final String note, String cur) {
        int i = ChoiceActivity.indexOf(ids, cur);
        int d = ChoiceActivity.indexOf(ids, def);
        String[] sh = shortText != null ? shortText : text;
        return Ui.nav(this, label, sh[i >= 0 ? i : d >= 0 ? d : 0], new View.OnClickListener() {
            @Override public void onClick(View v) { ChoiceActivity.open(MenuActivity.this, label, ids, text, store, key, def, note); }
        });
    }

    private void display() {
        LinearLayout g = section("显示布局", "显示大小\t显示大小同时缩放布局, 间隔和文字, 对所有应用生效, 切换后界面会重新排版一次. 字体大小在下面的\"字体\"里设置.");
        Ui.add(g, choiceRow("显示大小", DENSITY, DENSITY_TEXT, null, "preq", "display_size", "300",
            "所有界面的元素,间隔和字一起缩放", String.valueOf(getResources().getDisplayMetrics().densityDpi)));

        g = section("字体", "字体文件\t把 ttf 或 otf 字体文件放进 Kindle 的 fonts 文件夹, 在这里选择. 系统字体重启 Embrith 后生效, 字体大小立即生效.");
        Ui.add(g, fontFileRow());
        Ui.add(g, choiceRow("字体大小", FONT, FONT_TEXT, null, "preq", "font_scale", "1.0",
            "只改文字大小", fontScale()));

        int wn = WhiteBg.chosen(this).size();
        g = section("屏幕效果",
            "反色显示\t反色显示把整个屏幕黑白对调,适合夜间.",
            "对比度\tgamma 1.0 ~ 2.5:数字越大,文字越黑,浅灰越白.",
            "抖动\t灰度画面用 8×8 有序抖动,图片更细腻,文字不受影响.",
            "阅读背景过滤\t选中的应用(从桌面启动)里, 比阈值浅的背景一律显示为纯白, 文字和图片的深色部分不变, 让阅读页的灰色底色在墨水屏上更干净.",
            "过滤阈值\t灰度不小于这个数的颜色按纯白显示, 数字越小, 过滤的范围越大. "
            + "注意: 阈值太小时, 浅灰色的图片细节和浅色文字会一起被过滤成白色而看不见; 阈值对所有选中的应用共用, 没选中的应用不受影响. "
            + "建议先用默认值, 底色仍发灰就调小, 图片发白就调大.");
        if ("1".equals(Ctl.text("color_supported"))) Ui.add(g, Ui.switchRow(this, "彩色显示（KC）", "1".equals(Ctl.get("COLOR", "0")), new Ui.OnToggle() {
            @Override public void toggled(boolean on) { Ctl.set("COLOR", on ? "1" : "0"); }
        }));
        Ui.add(g, Ui.nav(this, "色温设置", "", open(AmberScheduleActivity.class)));
        Ui.add(g, Ui.switchRow(this, "反色显示", "1".equals(Ctl.get("INVERT", "0")), new Ui.OnToggle() {
            @Override public void toggled(boolean on) { Ctl.set("INVERT", on ? "1" : "0"); }
        }));
        Ui.add(g, contrastRow());
        Ui.add(g, Ui.switchRow(this, "抖动", "1".equals(Ctl.get("DITHER", "1")), new Ui.OnToggle() {
            @Override public void toggled(boolean on) { Ctl.set("DITHER", on ? "1" : "0"); }
        }));
        Ui.add(g, Ui.nav(this, "阅读背景过滤", wn == 0 ? "未开启" : wn + Ui.t(this, " 个应用"), open(WhiteAppsActivity.class)));
        Ui.add(g, choiceRow("过滤阈值", WHITE, WHITE_TEXT, null, "ctl", "WHITE_AT", "224",
            "灰度不小于这个数的浅色显示为纯白", Ctl.get("WHITE_AT", "224")));

        g = section("屏幕刷新",
            "局部刷新\t局部刷新同 Kindle:只刷新变化的地方,翻页每隔几页整屏闪一次去残影.关闭局部刷新 = 整屏闪刷(兼容),每次变化都闪一次."
            + "有残影时双指轻点(默认手势)或状态栏\"刷新屏幕\"可以手动整屏刷新.",
            "整屏重绘\t打开(默认)后, 应用每次刷新都重画整个屏幕, 可以避免书籍封面等区域出现黑框; 关闭则只重画变化的部分, 更流畅更省电, 但黑框可能再次出现. "
            + "注意: 切换后需要重新打开应用才生效, 已经打开的应用请先退出; 打开时刷新稍慢, 耗电略多.");
        int na = WhiteBg.noAnim(this).size();
        Ui.add(g, Ui.nav(this, "翻页动画", na == 0 ? "全部开启" : na + Ui.t(this, " 个应用关闭"), open(AnimAppsActivity.class)));
        Ui.add(g, Ui.switchRow(this, "局部刷新", "0".equals(Ctl.get("FLASH_ONLY", "0")), new Ui.OnToggle() {
            @Override public void toggled(boolean on) { Ctl.set("FLASH_ONLY", on ? "0" : "1"); }
        }));
        Ui.add(g, choiceRow("自动全屏刷新", FLASH, FLASH_TEXT, FLASH_SHORT, "ctl", "FLASH_EVERY", "6",
            "翻页时只刷新变化的部分,每隔几页自动整屏闪一次去残影", Ctl.get("FLASH_EVERY", "6")));
        Ui.add(g, Ui.switchRow(this, "整屏重绘", !"0".equals(Ctl.get("FULLDIRTY", "1")), new Ui.OnToggle() {
            @Override public void toggled(boolean on) { setFullDirty(on); }
        }));
        animRow = Ui.nav(this, "翻页动画步长", animSteps(), new View.OnClickListener() {
            @Override public void onClick(View v) { editAnimSteps(); }
        });
        Ui.add(g, animRow);
    }

    private Ui.Row fontFileRow() {
        java.util.List<String> ids = new java.util.ArrayList<String>();
        ids.add("");
        java.io.File[] fs = new java.io.File("/dev/kindle/fonts").listFiles();   // = /mnt/us/fonts on the Kindle
        if (fs != null) {
            java.util.Arrays.sort(fs);
            for (java.io.File f : fs) {
                String n = f.getName().toLowerCase();
                if (f.isFile() && (!n.startsWith(".") || Prefs.showHidden(this)) && (n.endsWith(".ttf") || n.endsWith(".otf"))) ids.add(f.getName());
            }
        }
        String[] id = ids.toArray(new String[0]);
        String[] text = id.clone();
        text[0] = "系统默认";
        return choiceRow("系统字体", id, text, null, "ctl", "FONT_FILE", "",
            "选好后重启 Embrith 生效; 字体需包含中文才会显示中文", Ctl.get("FONT_FILE", ""));
    }

    private String fontScale() {
        float f = getResources().getConfiguration().fontScale;
        for (String s : FONT) if (Math.abs(Float.parseFloat(s) - f) < 0.02f) return s;
        return "1.0";
    }

    private LinearLayout contrastRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Ui.dp(this, 64));
        row.setPadding(Ui.dp(this, 18), 0, Ui.dp(this, 6), 0);
        row.addView(Ui.text(this, "对比度", 18, false), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        IconButton minus = new IconButton(this, Icons.MINUS, "", false, 24, 12);
        IconButton plus = new IconButton(this, Icons.PLUS, "", false, 24, 12);
        contrastValue = Ui.text(this, String.format(java.util.Locale.US, "%.1f", contrast()), 20, true);
        contrastValue.setGravity(android.view.Gravity.CENTER);
        row.addView(minus, new LinearLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 52)));
        row.addView(contrastValue, new LinearLayout.LayoutParams(Ui.dp(this, 56), LinearLayout.LayoutParams.WRAP_CONTENT));
        row.addView(plus, new LinearLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 52)));
        minus.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { stepContrast(-1); } });
        plus.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { stepContrast(1); } });
        return row;
    }

    private double contrast() {
        try { return Double.parseDouble(Ctl.get("CONTRAST", "1.3")); } catch (NumberFormatException e) { return 1.3; }
    }

    private void stepContrast(int dir) {
        double c = Math.round(contrast() * 10) / 10.0 + dir * 0.1;
        if (c < 1.0) c = 1.0;
        if (c > 2.5) c = 2.5;
        String v = String.format(java.util.Locale.US, "%.1f", c);
        Ctl.set("CONTRAST", v);
        contrastValue.setText(v);
    }

    static String sbText(String id) {
        for (int i = 0; i < SB_IDS.length; i++) if (SB_IDS[i].equals(id)) return SB_TEXT[i];
        return "无";
    }

    private Ui.Row sbRow(final String key, final String label, final String def) {
        return Ui.nav(this, label, sbText(Prefs.getStr(this, key, def)), new View.OnClickListener() {
            @Override public void onClick(View v) {
                ChoiceActivity.open(MenuActivity.this, label, SB_IDS, SB_TEXT, "pref", key, def, null);
            }
        });
    }

    // ------------------------------------------------------------------ 系统
    static final String[] TZ = {
        "Pacific/Honolulu", "America/Anchorage", "America/Los_Angeles", "America/Denver", "America/Chicago", "America/New_York",
        "America/Sao_Paulo", "Europe/London", "Europe/Paris", "Europe/Athens", "Europe/Moscow", "Asia/Dubai", "Asia/Karachi",
        "Asia/Kolkata", "Asia/Dhaka", "Asia/Bangkok", "Asia/Shanghai", "Asia/Tokyo", "Australia/Sydney", "Pacific/Auckland" };
    static final String[] TZ_CITY = {
        "檀香山", "安克雷奇", "洛杉矶", "丹佛", "芝加哥", "纽约",
        "圣保罗", "伦敦", "巴黎 / 布鲁塞尔 / 柏林", "雅典", "莫斯科", "迪拜", "卡拉奇",
        "新德里", "达卡", "曼谷", "北京", "东京", "悉尼", "奥克兰" };
    private static final String[] MARGIN = { "16", "18", "20", "22", "24", "26", "28", "30", "32" };
    private static final String[] MARGIN_TEXT = { "16 dp", "18 dp", "20 dp", "22 dp (默认)", "24 dp", "26 dp", "28 dp", "30 dp", "32 dp" };
    private static final String[] WHITE = { "240", "224", "208", "192" };
    private static final String[] WHITE_TEXT = {
        "240: 只过滤近乎白色的背景", "224: 浅灰和浅米色(默认)", "208: 更深的米色", "192: 中浅灰" };
    private static final String[] LANG = { "zh", "en" };
    private static final String[] LANG_TEXT = { "中文", "English" };

    /** 时区选择页的文字: 城市 (UTC 偏移), 偏移按当前日期算(含夏令时). */
    private String[] tzText() {
        String[] t = new String[TZ.length];
        long now = System.currentTimeMillis();
        for (int i = 0; i < TZ.length; i++) {
            int m = java.util.TimeZone.getTimeZone(TZ[i]).getOffset(now) / 60000;
            t[i] = Ui.t(this, TZ_CITY[i]) + " (UTC" + (m < 0 ? "-" : "+") + Math.abs(m) / 60 + (m % 60 != 0 ? ":" + Math.abs(m) % 60 : "") + ")";
        }
        return t;
    }

    private void system() {
        LinearLayout g = section("设备信息", "设备名称\t设备名称显示在下拉状态栏里.");
        nameRow = Ui.nav(this, "设备名称", Prefs.deviceName(this), new View.OnClickListener() {
            @Override public void onClick(View v) { editName(); }
        });
        Ui.add(g, nameRow);
        verRow = Ui.row(this, "Embrith 版本", "", null);
        Ui.add(g, verRow);
        Ui.add(g, Ui.nav(this, "检查更新", "", open(NetUpdateActivity.class)));
        Ui.add(g, Ui.nav(this, "关于 Embrith", "", new View.OnClickListener() {
            @Override public void onClick(View v) {
                new Ui.Builder(MenuActivity.this)
                    .setTitle("关于 Embrith")
                    .setMessage(Ui.t(MenuActivity.this, "设备名称: ") + Prefs.deviceName(MenuActivity.this) + "\nAndroid 版本: " + Build.VERSION.RELEASE
                        + Ui.t(MenuActivity.this, "\n内核版本: ") + System.getProperty("os.version") + "\nRAM: " + memory()
                        + Ui.t(MenuActivity.this, "\n版权: ") + "Copyright (C) 2026 Embrith Contributors"
                        + Ui.t(MenuActivity.this, "\n许可证: ") + "GPL-3.0-or-later")
                    .setPositiveButton("好", null)
                    .show();
            }
        }));

        g = section("地区与语言");
        String tz = java.util.TimeZone.getDefault().getID();
        Ui.add(g, choiceRow("时区", TZ, tzText(), TZ_CITY, "tz", "timezone", tz, null, Prefs.getStr(this, "timezone", tz)));
        Ui.add(g, Ui.switchRow(this, "24 小时制", Prefs.clock24(this), new Ui.OnToggle() {
            @Override public void toggled(boolean on) { Prefs.putStr(MenuActivity.this, "clock24", on ? "1" : "0"); }
        }));
        Ui.add(g, choiceRow("语言", LANG, LANG_TEXT, null, "pref", "lang", "zh", null, Prefs.getStr(this, "lang", "zh")));

        g = section("应用管理", "安装应用\tapk 用 USB 拷到 Kindle 存储的任意位置,在这里选中后由 Kindle 侧静默安装,一般要一两分钟.");
        Ui.add(g, Ui.nav(this, "安装应用", "从 Kindle 选择 apk", open(PickApkActivity.class)));
        Ui.add(g, Ui.nav(this, "应用管理", "", open(AppsActivity.class)));

        g = section("系统维护", "生成日志\t平时不往 Kindle 存储持续写日志. 点这里生成最近 5 分钟的完整日志, 保存在 Kindle 的 logs/report/ 目录, 可以在里面查看和删除.");
        memRow = Ui.row(this, "内存", memory(), null);
        Ui.add(g, memRow);
        Ui.add(g, Ui.nav(this, "内存管理", "释放后台应用", new View.OnClickListener() {
            @Override public void onClick(View v) { freeMemory(); }
        }));
        Ui.add(g, Ui.nav(this, "生成日志", "", open(LogsActivity.class)));

        g = section("高级与兼容性",
            "新版 WebView\tEmbrith系统全局不使用网页内核, 新版(Chromium 119)和旧版(Chromium 52)网页内核主要在应用中使用(以下用新版, 旧版指代). 两版网页内核在JavaScript 引擎, Web API, 渲染管线, 安全模型, 网络协议和 WebView 系统集成方面都有非常大差距, 对内存的占用也大有不同. Embrith默认启用新版网页内核. 需注意: 切换回旧版网页内核可以适当减小内存压力, 但是可能会打不开应用内部分页面, 例如验证码. 注意: 切换后正在使用网页的应用会被系统结束, 未保存的内容会丢失, 请先保存; 重新打开该应用即生效, 不需要重启 Embrith; 如果重新打开后仍是旧内核, 请重启 Embrith. 内存紧张时, 新版内核会让后台应用更容易被系统结束.",
            "优化: dex2oat\t注意: 编译很占内存, 大应用要几分钟到十几分钟, 期间请不要重启或退出 Embrith; 编译后的文件会多占一些存储空间; 中途被打断时, 再打开一次开关即可重新编译. 关闭后新装的应用不再编译, 已编译的不受影响.",
            "共享文件夹\t把选中的 Kindle 文件夹以只读方式共享给安卓, 安卓里的路径是 /data/media/0/Kindle/ 加上它在 Kindle 上的位置. "
            + "改动在下次启动或重启 Embrith 后生效. 注意: 共享是只读的, 应用不能修改或删除里面的文件; 不能共享整个存储, 也不能共享 system 和 embrith 文件夹; "
            + "共享出去的文件夹, 有存储权限的安卓应用都能读取, 隐私文件请不要共享.",
            "开发者模式\t" + DEV_NOTE);
        Ui.add(g, Ui.switchRow(this, "新版 WebView", !"0".equals(Ctl.get("WEBVIEW", "1")), new Ui.OnToggle() {
            @Override public void toggled(boolean on) { setWebview(on); }
        }));
        Ui.add(g, Ui.switchRow(this, "优化: dex2oat", "1".equals(Ctl.get("DEX2OAT", "0")), new Ui.OnToggle() {
            @Override public void toggled(boolean on) { setDex2oat(on); }
        }));
        int ns = 0;
        for (String sp : Ctl.get("KINDLE_SHARED_SEL", "").split(":")) if (sp.length() > 0) ns++;
        Ui.add(g, Ui.nav(this, "共享文件夹", ns == 0 ? "未共享" : ns + Ui.t(this, " 个文件夹"), open(ShareFoldersActivity.class)));
        Ui.add(g, Ui.switchRow(this, "显示隐藏文件(夹)", Prefs.showHidden(this), new Ui.OnToggle() {
            @Override public void toggled(boolean on) { Prefs.putStr(MenuActivity.this, "show_hidden", on ? "1" : "0"); }
        }));
        devRow = Ui.switchRow(this, "开发者模式", "1".equals(Ctl.get("DEV_MODE", "0")), new Ui.OnToggle() {
            @Override public void toggled(boolean on) { if (on) askDevRisk(); else Ctl.set("DEV_MODE", "0"); }
        });
        Ui.add(g, devRow);
    }

    private static final String DEV_NOTE = "给开发和调试用, 普通使用请保持关闭. 打开或关闭后, 下次启动 Embrith 时生效(可以用电源页的重启 Embrith).\n\n"
        + "打开后权限的变化:\n"
        + "1. 日志: 调试日志持续写入 Kindle 存储里 embrith 文件夹的 logs 目录(平时只放在内存里, 有大小上限). 重启后日志还在, 但会不断占用存储空间和闪存寿命, 不会自动清理, 需要自己删除. 日志里可能含有 Wi-Fi 名称, 应用包名等个人信息, 发给别人前请先检查. 另外还会保存每次停止时的内核日志, 应用列表, 以及启动失败时的现场.\n"
        + "2. 安装: 启动时, Kindle 上 embrith 文件夹的 src 里有什么就覆盖安装什么: 不校验文件是否完整, 不比较版本号, 版本更低, 内容缺失或损坏的包也会被装上, 可能导致 Embrith 无法启动. 只覆盖放进 src 的文件, 没放的保持不变. 被替换的文件会保留 .old 副本, 本次安卓启动成功后才删除; 启动不成功, 下次运行时自动还原.\n"
        + "3. 提示: 安装过程中的屏幕提示会多停留几秒, 方便查看.\n\n"
        + "不受影响: 你的设置, 壁纸, 截图和应用数据不会被安装动作改动.\n"
        + "关闭后恢复普通模式: 更新包必须完整, 校验通过, 并且版本号更高才会安装.";

    private void askDevRisk() {
        new Ui.Builder(this).setTitle("开发者模式")
            .setMessage(DEV_NOTE)
            .setPositiveButton("我了解, 开启", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) { Ctl.set("DEV_MODE", "1"); }
            })
            .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) { if (devRow != null) devRow.setOn(false); }
            })
            .setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override public void onCancel(DialogInterface d) { if (devRow != null) devRow.setOn(false); }
            }).show();
    }

    /** 优化: dex2oat. 开: 清掉旧进度, 让 Kindle 侧开始(先打开安装时 speed, 再编译没编的应用), 并打开全屏进度页; 关: 只关安装时 speed. */
    private void setDex2oat(boolean on) {
        Ctl.set("DEX2OAT", on ? "1" : "0");
        if (!on) { Ctl.request("dexopt_apply"); return; }
        Ctl.remove("dexopt");
        Ctl.request("dexopt_start");
        startActivity(new Intent(this, DexoptActivity.class));
    }

    /** 整屏重绘: 写 ctl/conf 的 FULLDIRTY, 请 Kindle 侧在容器里设置 debug.embrith.fulldirty (应用进程启动时读取一次). */
    private void setFullDirty(boolean on) {
        Ctl.set("FULLDIRTY", on ? "1" : "0");
        Ctl.request("fulldirty_apply");
        Ui.toast(this, "已切换, 重新打开应用后生效", true);
    }

    /** 新版 WebView: 写 ctl/conf 的 WEBVIEW, 请 Kindle 侧在容器里启用对应的 WebView 包并停用另一个. */
    private void setWebview(boolean on) {
        Ctl.set("WEBVIEW", on ? "1" : "0");
        Ctl.request("webview_apply");
        Ui.toast(this, "已切换, 重新打开使用网页的应用后生效", true);
    }

    private String animSteps() {
        return Ctl.get("ANIM_STEPS", "12");
    }

    /** 翻页动画步长: 输入数字 1 到 60, 写入 ctl/conf 的 ANIM_STEPS (kdispd 读取, 默认 12). */
    private void editAnimSteps() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setFilters(new android.text.InputFilter[] { new android.text.InputFilter.LengthFilter(2) });
        input.setText(animSteps());
        input.setSelectAllOnFocus(true);
        new Ui.Builder(this)
            .setTitle("翻页动画步长")
            .setMessage("输入 1 到 60 的数字, 越大越慢越细腻 (默认 12)")
            .setView(input)
            .setPositiveButton("好", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int which) {
                    int n;
                    try { n = Integer.parseInt(input.getText().toString().trim()); } catch (NumberFormatException e) { n = -1; }
                    if (n < 1 || n > 60) { Ui.toast(MenuActivity.this, "请输入 1 到 60 的数字", true); return; }
                    Ctl.set("ANIM_STEPS", String.valueOf(n));
                    if (animRow != null) animRow.setValue(String.valueOf(n));
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private Ui.Row txtRow;

    private String txtRegexShown() {
        String r = Prefs.getStr(this, "txt_regex", "").trim();
        return r.length() == 0 ? Ui.t(this, "自动") : Ui.t(this, "自定义");
    }

    /** The regular expression for the chapters of a txt book: checked when it is saved (it must compile); empty = the automatic detection. */
    private void editTxtRegex() {
        final EditText in = new EditText(this);
        in.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        in.setHint(Ui.t(this, "留空 = 自动检测"));
        in.setText(Prefs.getStr(this, "txt_regex", ""));
        in.setSelection(in.getText().length());
        new Ui.Builder(this).setTitle("TXT 章节正则").setView(in)
            .setPositiveButton("好", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    String r = in.getText().toString().trim();
                    if (r.length() > 0) {
                        try { java.util.regex.Pattern.compile(r); } catch (RuntimeException e) {
                            Ui.toast(MenuActivity.this, "正则式不正确: " + String.valueOf(e.getMessage()).split("\n")[0], true);
                            return;
                        }
                    }
                    Prefs.putStr(MenuActivity.this, "txt_regex", r);
                    if (txtRow != null) txtRow.setValue(txtRegexShown());
                }
            })
            .setNegativeButton("取消", null)
            .setNeutralButton("恢复自动", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    Prefs.putStr(MenuActivity.this, "txt_regex", "");
                    if (txtRow != null) txtRow.setValue(txtRegexShown());
                }
            })
            .show();
    }

    private void editName() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(Prefs.deviceName(this));
        input.setSelectAllOnFocus(true);
        new Ui.Builder(this)
            .setTitle("设备名称")
            .setView(input)
            .setPositiveButton("好", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int which) {
                    String n = input.getText().toString().trim();
                    Prefs.putStr(MenuActivity.this, "device_name", n.length() == 0 ? "Embrith" : n);
                    if (nameRow != null) nameRow.setValue(Prefs.deviceName(MenuActivity.this));
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    /**
     * Memory management, the platform's own way: ActivityManager.killBackgroundProcesses for every app the user installed. The system
     * kills only processes that are in the background, so the app on screen and the services in use stay.
     */
    private void freeMemory() {
        freeMemory(this, new Runnable() {
            @Override public void run() { if (memRow != null) memRow.setValue(memory()); }
        });
    }

    /** The same from any page (the clean-up button in the top row of the library and the desktop); after runs on the main thread before the result toast. */
    public static void freeMemory(final Activity a, final Runnable after) {
        final long before = availKb();
        Ctl.request("ssh_clean");      // Kindle side: drop inactive ssh/sftp sessions (ssh-toggle.sh clean)
        ActivityManager am = (ActivityManager) a.getSystemService(Context.ACTIVITY_SERVICE);
        for (ApplicationInfo ai : a.getPackageManager().getInstalledApplications(0)) {
            if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) == 0 && !ai.packageName.equals(a.getPackageName())) am.killBackgroundProcesses(ai.packageName);
        }
        Ui.toast(a, "正在释放内存...", false);
        new Handler().postDelayed(new Runnable() {
            @Override public void run() {
                long now = availKb();
                if (after != null) after.run();
                Ui.toast(a, Ui.t(a, "已释放 ") + Math.max(0, (now - before) / 1024) + Ui.t(a, " MB, 现在可用 ") + now / 1024 + " MB", false);
            }
        }, 1500);
    }

    private static long availKb() {
        try {
            BufferedReader r = new BufferedReader(new FileReader("/proc/meminfo"));
            try {
                String line;
                while ((line = r.readLine()) != null) if (line.startsWith("MemAvailable:")) return kb(line);
            } finally {
                r.close();
            }
        } catch (IOException e) {
            // unknown
        }
        return 0;
    }

    /** MemAvailable and MemTotal of the Kindle (a container sees the real numbers). */
    private String memory() {
        long total = 0, avail = 0;
        try {
            BufferedReader r = new BufferedReader(new FileReader("/proc/meminfo"));
            try {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.startsWith("MemTotal:")) total = kb(line);
                    else if (line.startsWith("MemAvailable:")) avail = kb(line);
                }
            } finally {
                r.close();
            }
        } catch (IOException e) {
            return "未知";
        }
        return Ui.t(this, "可用 ") + avail / 1024 + Ui.t(this, " MB / 共 ") + total / 1024 + " MB";
    }

    private static long kb(String line) {
        String[] p = line.trim().split("\\s+");
        try {
            return Long.parseLong(p[1]);
        } catch (Exception e) {
            return 0;
        }
    }
}