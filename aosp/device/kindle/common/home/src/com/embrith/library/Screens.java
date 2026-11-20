/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * Screens: Settings of the library pages: the reading goals, the rows of recent books, the screen time sync. */
package com.embrith.library;

import android.content.Context;

import com.embrith.home.Prefs;

/**
 * Settings of the library pages (the pages are the buttons of the bottom bar, Dock): the reading goals, the rows of the recent books, the screen time sync.
 */
public final class Screens {
    private static final int GOAL_DEF = 30;   // minutes a day

    private Screens() { }

    /** The reading goal of a day in minutes, 0 = none (the goal of the check-in and of the ring). */
    public static int goal(Context c) {
        try {
            return Math.max(0, Integer.parseInt(Prefs.getStr(c, "goal_day", String.valueOf(GOAL_DEF))));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** The goal mode of the ring and the check-in: "read", "app" or "both" (two rings side by side). */
    public static String goalMode(Context c) {
        String m = Prefs.getStr(c, "goal_mode", "read");
        return "app".equals(m) || "both".equals(m) ? m : "read";
    }

    /** The goal of a day for the chosen app in minutes, 0 = none. */
    public static int appGoal(Context c) {
        try {
            return Math.max(0, Integer.parseInt(Prefs.getStr(c, "goal_app_min", String.valueOf(GOAL_DEF))));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** The package of the app the app goal counts, "" = none chosen. */
    public static String appPkg(Context c) {
        return Prefs.getStr(c, "goal_app_pkg", "");
    }

    public static String goalDef() {
        return String.valueOf(GOAL_DEF);
    }

    /** 1 or 2 rows of covers on the "recent" board when it shares the screen. */
    public static int recentRows(Context c) {
        return "2".equals(Prefs.getStr(c, "recent_rows", "1")) ? 2 : 1;
    }

    /** From the desktop: writes the screen time of the apps into the reading time, in the background, at most every 10 minutes. */
    public static void syncUsage(Context c) {
        final Context app = c.getApplicationContext();
        long now = System.currentTimeMillis();
        try {
            if (now - Long.parseLong(Prefs.getStr(app, "usage_sync", "0")) < 10 * 60000L) return;
        } catch (NumberFormatException e) {
            // never synced
        }
        Prefs.putStr(app, "usage_sync", String.valueOf(now));
        new Thread(new Runnable() {
            @Override public void run() { ReadTime.syncApps(app); }
        }).start();
    }
}
