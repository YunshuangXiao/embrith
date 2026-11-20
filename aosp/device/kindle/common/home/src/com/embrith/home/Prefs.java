/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * Prefs: Preference helpers: the wallpaper and lock screen pictures, the device name and the clock format. */
package com.embrith.home;

import android.content.Context;
import android.content.SharedPreferences;

/** Which picture is the desktop wallpaper / the lock screen picture ("" = plain white). */
public final class Prefs {
    static final String DIR = "/dev/wallpapers";   // = <embrith folder>/wallpapers on the Kindle (visible over USB)

    private Prefs() { }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("embrith", Context.MODE_PRIVATE);
    }

    public static String getStr(Context c, String key, String def) {
        return sp(c).getString(key, def);
    }

    public static void putStr(Context c, String key, String value) {
        sp(c).edit().putString(key, value).commit();
    }

    /** The name shown in the status bar (changeable in the menu: General > Device name). */
    static String deviceName(Context c) {
        return getStr(c, "device_name", "Embrith · 余烬");
    }

    /** The setting "show hidden files and folders" (advanced): names that start with a dot are listed when it is on. Default off. */
    public static boolean showHidden(Context c) {
        return "1".equals(getStr(c, "show_hidden", "0"));
    }

    static boolean clock24(Context c) {
        return "1".equals(getStr(c, "clock24", "1"));
    }

    static String wall(Context c, String which) {
        return sp(c).getString(which + "_wall", "");
    }

    static void setWall(Context c, String which, String path) {
        sp(c).edit().putString(which + "_wall", path).commit();
    }
}
