/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * SettingsHomeBox: The middle of the settings home: the widget area above the first-level entries, laid out by the entry height h. */
package com.embrith.home;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;


import java.util.List;

/**
 * The middle part of the settings home. From the top: the widget area (as many rows of cells as the widgets cover, see Slots), a gap g,
 * the six entries (e high, a gap g between). The block touches the top bar and the bottom bar: it has no gap of its own to them (the bars have
 * their own). The height is the one of the plain split, h = (height - 7g) / 8: an entry is h high and the
 * area is two rows of about h (this is the design of a 1272 x 1696 screen). Another device changes it in two ways. The six entries come first:
 * eMin is the height their content needs (it follows the display density and the font size), so a smaller screen keeps its entries and the
 * area gets fewer rows (widgets that no longer fit are not shown). eMax (ENTRY_MAX_DP) is the most an entry grows to, so on a larger screen the
 * entries stay as they are and the area gets more rows and holds more widgets. e0 = h kept between eMin and eMax is the entry height the
 * grid is built for; the area is only as high as the widgets cover, the entries then take the room that is left (at least eMin, at most eMax).
 * e0 and the room for the area are kept in Prefs (sh_h, sh_hmax): the settings page for the widgets draws its preview from them.
 */
final class SettingsHomeBox extends ViewGroup {
    private final Activity a;
    private final SlotArea area;
    private final List<View> cards;
    /** The most an entry grows to, in dp: the entry height of the plain split on the 1272 x 1696 screen (about 82 dp) with a little room. */
    private static final int ENTRY_MAX_DP = 86;
    private int lastW = -1, lastH = -1, lastE = -1, lastCap = -1, areaH, entryH, extra;
    private Slots.Metrics grid;

    SettingsHomeBox(Activity a, List<View> cards) {
        super(a);
        this.a = a;
        this.cards = cards;
        area = new SlotArea(a, Slots.SETTINGS, true);
        addView(area);
        for (View v : cards) addView(v);
    }

    SlotArea area() {
        return area;
    }

    /** The page is shown again: the widgets are built again when the settings for the widgets changed them, then the buttons look at the status. */
    void refresh() {
        if (area.reloadIfChanged()) requestLayout();
        area.update();
    }

    private static int exact(int px) {
        return MeasureSpec.makeMeasureSpec(Math.max(0, px), MeasureSpec.EXACTLY);
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int W = MeasureSpec.getSize(wSpec), H0 = MeasureSpec.getSize(hSpec);
        setMeasuredDimension(W, H0);
        int g = Slots.gap(a), side = Frames.edge(a), w = W - 2 * side;
        int H = H0 - 2 * side;   // the page margin above the first frame and below the last, as on the other pages
        if (H < Ui.dp(a, 200) || w <= 0) return;   // not measured with a real size yet
        int nat = 0;
        for (View v : cards) {
            v.measure(exact(w), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            nat = Math.max(nat, v.getMeasuredHeight());
        }
        int eMin = Math.max(Ui.dp(a, 40), nat), eMax = Math.max(eMin, Ui.dp(a, ENTRY_MAX_DP));
        int e0 = Math.max(eMin, Math.min(eMax, (H - 7 * g) / 8));
        int cap = Math.max(0, H - 6 * g - 6 * e0);
        if (grid == null || w != lastW || H != lastH || e0 != lastE || cap != lastCap) {
            lastW = w;
            lastH = H;
            lastE = e0;
            lastCap = cap;
            Prefs.putStr(a, "sh_h", String.valueOf(e0));
            Prefs.putStr(a, "sh_hmax", String.valueOf(cap));
            grid = Slots.forEntry(w, g, e0, cap);
            area.load(grid);
        }
        int rows = Slots.usedRows(Slots.SETTINGS.place(a, Slots.SETTINGS.order(a), grid), grid);
        areaH = grid.height(rows);
        int fixed = areaH > 0 ? areaH + 6 * g : 5 * g;
        // the entries fill the room down to the bottom bar (no eMax cap here); the pixel remainder goes to the first entries
        entryH = Math.max(eMin, (H - fixed) / 6);
        extra = Math.max(0, H - fixed - 6 * entryH);
        area.measure(exact(w), exact(areaH));
        int k = 0;
        for (View v : cards) v.measure(exact(w), exact(entryH + (k++ < extra ? 1 : 0)));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (grid == null) return;
        int g = Slots.gap(a), side = Frames.edge(a), w = lastW;
        int y = side;
        if (areaH > 0) {
            area.layout(side, y, side + w, y + areaH);
            y += areaH + g;
        } else {
            area.layout(side, y, side + w, y);
        }
        int k = 0;
        for (View v : cards) {
            int eh = entryH + (k++ < extra ? 1 : 0);
            v.layout(side, y, side + w, y + eh);
            y += eh + g;
        }
    }
}
