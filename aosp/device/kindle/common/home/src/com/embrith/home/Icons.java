/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * Icons: Lucide icons built into res/drawable, tinted with the paint colour; switched-off variants where the icon
 * set has one. */
package com.embrith.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;

/**
 * Icons: the Lucide icons built into res/drawable (ic_*.xml, converted from the svg files of icons/), tinted with the paint colour.
 * "off" = switched off: the Lucide "-off" icon when there is one (wifi, bluetooth), otherwise a slash across the icon.
 */
public final class Icons {
    public static final int NONE = 0, MORE = 1, WIFI = 2, BT = 3, REFRESH = 4, SETTINGS = 5, LOCK = 6, INVERT = 7, HOME = 8, SUN = 9, EXIT = 10,
            PLUS = 11, CHEVRON_LEFT = 12, CHEVRON_DOWN = 13, BATTERY = 14, LIST = 15, GRID = 16, OVERVIEW = 17, CHECKIN = 18, STATS = 19, LIBRARY = 20, READING = 21, MONITOR = 22, PALETTE = 23, UP = 24, DOWN = 25, RIGHT = 26, PLANE = 27, THERMOMETER = 28, MINUS = 29, BROOM = 30, SORT = 31, GROUP = 32;

    private static final int[] RES = { 0, R.drawable.ic_ellipsis, R.drawable.ic_wifi, R.drawable.ic_bluetooth, R.drawable.ic_refresh_cw,
            R.drawable.ic_settings, R.drawable.ic_lock, R.drawable.ic_contrast, R.drawable.ic_house, R.drawable.ic_sun, R.drawable.ic_power,
            R.drawable.ic_plus, R.drawable.ic_chevron_left, R.drawable.ic_chevron_up, R.drawable.ic_battery, R.drawable.ic_list,
            R.drawable.ic_layout_grid, R.drawable.ic_calendar_days, R.drawable.ic_calendar_check, R.drawable.ic_chart_column,
            R.drawable.ic_library, R.drawable.ic_book_open_text, R.drawable.ic_monitor, R.drawable.ic_palette,
            R.drawable.ic_chevron_up, R.drawable.ic_chevron_down, R.drawable.ic_chevron_right, R.drawable.ic_plane, R.drawable.ic_thermometer, R.drawable.ic_minus, R.drawable.ic_broom_sparkles,
            R.drawable.ic_arrow_up_down, R.drawable.ic_layers };

    private Icons() { }

    /** Tint a built-in icon with "color" and draw it centred at (cx, cy) inside a square of side "s". */
    private static void blit(Context ctx, Canvas c, int res, float cx, float cy, float s, int color) {
        Drawable d = ctx.getResources().getDrawable(res, null).mutate();
        d.setTint(color);
        d.setBounds(Math.round(cx - s / 2), Math.round(cy - s / 2), Math.round(cx + s / 2), Math.round(cy + s / 2));
        d.draw(c);
    }

    /** Battery icon of the level pct (0..100, < 0 = unknown: empty) in four steps; the charging icon while charging. */
    static void battery(Context ctx, Canvas c, float cx, float cy, float s, Paint p, int pct, boolean charging) {
        int res = charging ? R.drawable.ic_battery_charging : pct < 10 ? R.drawable.ic_battery : pct < 40 ? R.drawable.ic_battery_low
                : pct < 70 ? R.drawable.ic_battery_medium : R.drawable.ic_battery_full;
        blit(ctx, c, res, cx, cy, s, p.getColor());
    }

    /** Icon for a status-bar custom button id (lock, invert, home, light, exit, none). */
    static int forId(String id) {
        if ("lock".equals(id)) return LOCK;
        if ("invert".equals(id)) return INVERT;
        if ("home".equals(id)) return HOME;
        if ("light".equals(id)) return SUN;
        if ("exit".equals(id)) return EXIT;
        if ("clear".equals(id)) return REFRESH;
        return NONE;
    }

    /** Draw icon "type" centred at (cx, cy) inside a square of side "s". The paint decides the colour. */
    static void draw(Context ctx, Canvas c, int type, float cx, float cy, float s, Paint p, boolean off) {
        int color = p.getColor();
        if (off && type == WIFI) {
            blit(ctx, c, R.drawable.ic_wifi_off, cx, cy, s, color);
            return;
        }
        if (off && type == BT) {
            blit(ctx, c, R.drawable.ic_bluetooth_off, cx, cy, s, color);
            return;
        }
        if (type > 0 && type < RES.length) blit(ctx, c, RES[type], cx, cy, s, color);
        if (off) blit(ctx, c, R.drawable.ic_slash, cx, cy, s, color);
    }
}
