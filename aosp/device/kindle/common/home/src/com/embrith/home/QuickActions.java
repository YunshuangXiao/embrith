/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * QuickActions: The actions of the quick buttons (pull-down panel and settings home widgets). */
package com.embrith.home;

import android.content.Context;

import java.util.Map;

/** The actions behind the quick buttons, shared by ControlPanel (pull-down panel) and the button widgets: the table and the logic are in Actions. */
final class QuickActions {
    private QuickActions() { }

    static int index(String id) {
        return Actions.find(id) == null ? -1 : 0;
    }

    static int icon(String id) {
        Actions.Def d = Actions.find(id);
        return d == null ? Icons.NONE : d.icon;
    }

    static String name(String id) {
        Actions.Def d = Actions.find(id);
        return d == null ? "" : d.name;
    }

    /** The on / off look of a button for the action id, from the status of the Kindle. */
    static void setState(CircleButton b, String id) {
        Map<String, String> st = Ctl.status();
        String w = st.get("WIFI"), bt = st.get("BT");
        Actions.Def d = Actions.find(id);
        String c = d == null ? "" : d.id;
        if ("wifi_toggle".equals(c)) { b.setOn("1".equals(w)); b.setOff("0".equals(w)); }
        else if ("bt_toggle".equals(c)) { b.setOn("1".equals(bt)); b.setOff("0".equals(bt)); }
        else if ("airplane".equals(c)) b.setOn("0".equals(w) && "0".equals(bt));
        else if ("invert".equals(c)) b.setOn("1".equals(Ctl.get("INVERT", "0")));
    }

    /** Airplane mode: radios on -> turn the ones that are on off; all off -> turn Wi-Fi back on. */
    static void airplane() {
        Map<String, String> st = Ctl.status();
        boolean w = "1".equals(st.get("WIFI")), b = "1".equals(st.get("BT"));
        if (w || b) {
            if (w) Ctl.request("wifi");
            if (b) Ctl.request("bt");
        } else {
            Ctl.request("wifi");
        }
    }

    /** Runs the action (no argument); leave (may be null) is run first when the action leaves the page (closes the floating panel). */
    static void run(Context c, String id, Runnable leave) {
        Actions.run(c, id, null, leave);
    }
}
