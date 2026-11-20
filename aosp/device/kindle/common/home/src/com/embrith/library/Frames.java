/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * Frames: The one margin rule of every page: the edge distance from the left, right and bottom edge, and a small
 * gap between two frames. */
package com.embrith.home;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.LinearLayout;

/** The one margin rule of every page: EDGE dp (user setting, default 22) from the left, right and bottom edge, 8 dp between two frames. */
public final class Frames {
    public static final int EDGE = 22;   // dp: default of the frame to screen edge distance (left, right, bottom)
    public static final int MIN = 16;    // dp: smallest setting (the padding of the library pages)
    public static final int MAX = 32;    // dp: largest setting
    public static final String KEY = "page_margin";
    public static final int GAP = 8;     // dp: frame to frame

    private Frames() { }

    /** The margin setting in dp (Settings > Personalization > Page margin). */
    public static int edgeDp(Context c) {
        int v = EDGE;
        try { v = Integer.parseInt(Prefs.getStr(c, KEY, String.valueOf(EDGE))); } catch (NumberFormatException e) { }
        return Math.max(MIN, Math.min(MAX, v));
    }

    public static int edge(Context c) { return Ui.dp(c, edgeDp(c)); }

    public static int half(Context c) { return Ui.dp(c, GAP / 2); }

    /** Layout params of a frame inside a parent that has padDp padding on the left and right. h = 0 with weight 1 is set by the caller. */
    public static LinearLayout.LayoutParams params(Context c, int h, int padDp, boolean bottomEdge) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h);
        int side = Ui.dp(c, edgeDp(c) - padDp);
        p.setMargins(side, half(c), side, bottomEdge ? edge(c) : half(c));
        return p;
    }

    /** Like group, with a fixed height in pixels. */
    public static LinearLayout group(LinearLayout body, int padDp, int heightPx) {
        LinearLayout g = Ui.group(body.getContext());
        body.addView(g, params(body.getContext(), heightPx, padDp, false));
        return g;
    }

    /** A new bordered group added to the body of a page (the body has padDp padding on the left and right). */
    public static LinearLayout group(LinearLayout body, int padDp) {
        LinearLayout g = Ui.group(body.getContext());
        body.addView(g, params(body.getContext(), ViewGroup.LayoutParams.WRAP_CONTENT, padDp, false));
        return g;
    }
}