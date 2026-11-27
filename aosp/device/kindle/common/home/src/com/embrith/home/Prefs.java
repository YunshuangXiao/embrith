/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * Prefs: Preference helpers: the wallpaper and lock screen pictures, the device name and the clock format. */
package com.embrith.home;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

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

    /** The accent color (settings, display): "#RRGGBB", default black. */
    static int parseColor(String s, int def) {
        try { return Color.parseColor(s); } catch (RuntimeException e) { return def; }
    }

    /** The accent color for switches and marks: only while the color display is on (the gray screen would turn it into gray), else black. */
    public static int accent(Context c) {
        if (!"1".equals(Ctl.get("COLOR", "0"))) return Color.BLACK;
        return parseColor(getStr(c, "accent", "#000000"), Color.BLACK);
    }

    /** The theme color of the library (settings, library): its own "#RRGGBB" in "lib_accent", or the theme color while it is empty. */
    public static int libAccent(Context c) {
        if (!"1".equals(Ctl.get("COLOR", "0"))) return Color.BLACK;
        String v = getStr(c, "lib_accent", "");
        return v.length() == 0 ? accent(c) : parseColor(v, Color.BLACK);
    }

    /** White blended toward a color: t = 0 is white, t = 1 the color (the gradient of the library charts). */
    public static int mix(int color, float t) {
        int r = 255 + Math.round((Color.red(color) - 255) * t), g = 255 + Math.round((Color.green(color) - 255) * t);
        int b = 255 + Math.round((Color.blue(color) - 255) * t);
        return Color.rgb(r, g, b);
    }

    /** True when white text is easier to read on this color than black text. */
    public static boolean isDark(int color) {
        return 0.299f * Color.red(color) + 0.587f * Color.green(color) + 0.114f * Color.blue(color) < 140f;
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
