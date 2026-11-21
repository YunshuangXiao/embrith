/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * Actions: The table of the actions of the quick buttons (and, later, of the gestures): id, name, sub-menu, what it acts on, and how it runs. */
package com.embrith.home;

import android.content.Context;
import android.content.Intent;

/**
 * Every action acts on one object:
 * HOST = the Kindle side (lipc services, display driver, the host processes): the launcher writes "id [argument]" to ctl/act and host/ui/ctl.sh
 * does it (act_run), so the logic is in one place and the same for every caller; APP = the launcher itself or Android (home, settings, the airplane
 * logic that reads both radios, the display setting INVERT that the launcher writes to ctl/conf).
 * Ids written before this table (wifi, bt, plane, lock, clear, light, exit) still work: canon() gives the new id.
 * Reference: KOReader (AGPL-3.0, https://github.com/koreader/koreader), frontend/dispatcher.lua. Only the action names follow it where it has the
 * action (full_refresh, toggle_frontlight, set_frontlight, increase/decrease_frontlight, set/increase/decrease_frontlight_warmth, toggle_wifi,
 * wifi_on, wifi_off, suspend, restart, reboot, exit, screenshot, show_menu, filemanager); no code is copied.
 */
final class Actions {
    static final int HOST = 0, APP = 1;
    /** Where an action can be chosen (a bit mask): BTN = a button, TAP = a gesture without swipe steps (tap, hold, corner, ...), SWP = a swipe gesture that
     *  gives steps (the *_adjust actions follow the distance of the swipe; the levels set / up / down do not belong there). */
    static final int BTN = 1, TAP = 2, SWP = 4, ALL_KINDS = BTN | TAP | SWP;
    /** The sub-menus. */
    static final String[] CATS = { "屏幕与前光", "网络", "电源与系统", "导航与应用" };
    private static final int SCREEN = 0, NET = 1, POWER = 2, NAV = 3;

    static final class Def {
        final String id, name, paramTitle;
        final int cat, target, icon;
        final boolean leave;       // the action leaves the page: the floating panel is closed first
        final String[] opts;       // the values of the argument (null = no argument)
        final int scope;           // BTN, TAP and SWP bits: where the action can be chosen

        Def(String id, String name, int cat, int target, int icon, boolean leave, String paramTitle, String[] opts) {
            this(id, name, cat, target, icon, leave, paramTitle, opts, ALL_KINDS);
        }

        Def(String id, String name, int cat, int target, int icon, boolean leave, String paramTitle, String[] opts, int scope) {
            this.scope = scope;
            this.id = id;
            this.name = name;
            this.cat = cat;
            this.target = target;
            this.icon = icon;
            this.leave = leave;
            this.paramTitle = paramTitle;
            this.opts = opts;
        }
    }

    private static final String[] LEVELS = { "0", "3", "6", "9", "12", "15", "18", "21", "24" };
    private static final String[] STEPS = { "1", "2", "3", "5" };

    private static Def d(String id, String name, int cat, int target, int icon) {
        return new Def(id, name, cat, target, icon, false, null, null);
    }

    /** An action that only gestures offer: kgest runs it (keys, steps of a swipe). */
    private static Def g(String id, String name, int cat, int icon, int scope) {
        return new Def(id, name, cat, APP, icon, false, null, null, scope);
    }

    static final Def[] ALL = {
        d("refresh", "刷新屏幕", SCREEN, HOST, Icons.REFRESH),
        d("invert", "反色", SCREEN, APP, Icons.INVERT),
        d("light_toggle", "开关前光", SCREEN, HOST, Icons.SUN),
        new Def("light_set", "前光设为", SCREEN, HOST, Icons.SUN, false, "前光亮度 (0-24)", LEVELS, BTN | TAP),
        new Def("light_inc", "前光调亮", SCREEN, HOST, Icons.SUN, false, "每次调亮", STEPS, BTN | TAP),
        new Def("light_dec", "前光调暗", SCREEN, HOST, Icons.SUN, false, "每次调暗", STEPS, BTN | TAP),
        new Def("warm_set", "色温设为", SCREEN, HOST, Icons.THERMOMETER, false, "色温 (0-24)", LEVELS, BTN | TAP),
        new Def("warm_inc", "色温调暖", SCREEN, HOST, Icons.THERMOMETER, false, "每次调暖", STEPS, BTN | TAP),
        new Def("warm_dec", "色温调冷", SCREEN, HOST, Icons.THERMOMETER, false, "每次调冷", STEPS, BTN | TAP),
        d("screenshot", "截图", SCREEN, HOST, Icons.MONITOR),
        d("wifi_toggle", "Wi-Fi 开/关", NET, HOST, Icons.WIFI),
        d("wifi_on", "打开 Wi-Fi", NET, HOST, Icons.WIFI),
        d("wifi_off", "关闭 Wi-Fi", NET, HOST, Icons.WIFI),
        d("bt_toggle", "蓝牙", NET, HOST, Icons.BT),
        new Def("airplane", "飞行模式", NET, APP, Icons.PLANE, false, null, null, BTN),
        d("sleep", "休眠", POWER, HOST, Icons.LOCK),
        d("restart_app", "重启 Embrith", POWER, HOST, Icons.REFRESH),
        d("reboot", "重启 Kindle", POWER, HOST, Icons.EXIT),
        d("usb_eject", "弹出 USB", POWER, HOST, Icons.MORE),
        d("check_update", "检查更新", POWER, HOST, Icons.REFRESH),
        new Def("exit_app", "退出 Embrith", POWER, HOST, Icons.EXIT, true, null, null),
        new Def("home", "桌面", NAV, APP, Icons.HOME, true, null, null),
        d("open_settings", "打开设置", NAV, APP, Icons.SETTINGS),
        g("back", "返回", NAV, Icons.CHEVRON_LEFT, TAP | SWP),
        g("page_next", "下一页", NAV, Icons.RIGHT, TAP | SWP),
        g("page_prev", "上一页", NAV, Icons.CHEVRON_LEFT, TAP | SWP),
        g("app_switch", "切换应用(最近任务)", NAV, Icons.OVERVIEW, TAP | SWP),
        g("statusbar", "下拉状态栏", NAV, Icons.DOWN, TAP | SWP),
        g("light_adjust", "调节前光亮度", SCREEN, Icons.SUN, SWP),
        g("warm_adjust", "调节色温", SCREEN, Icons.THERMOMETER, SWP),
    };

    private static final String[][] LEGACY = {
        { "wifi", "wifi_toggle" }, { "bt", "bt_toggle" }, { "plane", "airplane" }, { "lock", "sleep" }, { "clear", "refresh" },
        { "light", "light_toggle" }, { "exit", "exit_app" }, { "menu", "open_settings" } };

    private Actions() { }

    /** The id of the table for an id that may be an old one. */
    static String canon(String id) {
        for (String[] l : LEGACY) if (l[0].equals(id)) return l[1];
        return id;
    }

    /** The action of an id (old ids too), null when there is none. */
    static Def find(String id) {
        String c = canon(id);
        for (Def d : ALL) if (d.id.equals(c)) return d;
        return null;
    }

    /** The actions of a sub-menu that can be chosen for a kind of place: BTN, TAP or SWP. */
    static java.util.List<Def> of(int cat, int kind) {
        java.util.List<Def> l = new java.util.ArrayList<Def>();
        for (Def d : ALL) if (d.cat == cat && (d.scope & kind) != 0) l.add(d);
        return l;
    }

    /** An old gesture id that stands for an action with an argument: {new id, argument}; null when the id needs no change. */
    static String[] split(String id) {
        if ("light_max".equals(id)) return new String[] { "light_set", "24" };
        if ("light_min".equals(id)) return new String[] { "light_set", "0" };
        if ("warm_max".equals(id)) return new String[] { "warm_set", "24" };
        if ("warm_min".equals(id)) return new String[] { "warm_set", "0" };
        return null;
    }

    /** Runs the action; arg may be null; leave (may be null) is run first when the action leaves the page. */
    static void run(Context c, String id, String arg, Runnable leave) {
        Def d = find(id);
        if (d == null) return;
        if (d.leave && leave != null) leave.run();
        if ("airplane".equals(d.id)) {
            QuickActions.airplane();
        } else if ("invert".equals(d.id)) {
            Ctl.set("INVERT", "1".equals(Ctl.get("INVERT", "0")) ? "0" : "1");
        } else if ("home".equals(d.id)) {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_HOME);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } else if ("open_settings".equals(d.id)) {
            c.startActivity(new Intent(c, MenuActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } else if (d.target == HOST) {
            Ctl.put("act", d.id + (arg != null && arg.length() > 0 ? " " + arg : ""));
        }
    }
}
