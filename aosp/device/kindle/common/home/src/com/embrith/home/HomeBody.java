/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * HomeBody: The middle of the desktop: the widget area above the apps; the apps take all the height the widgets leave. */
package com.embrith.home;

import android.view.View;
import android.view.ViewGroup;


/**
 * The part of the desktop between the top row and the bottom bar. From the top: a margin m, the widget area (only as high as the chosen
 * widgets), a gap g = 2m, then the app block (the grid, or the "install" block when there are no apps) down to the bottom margin. So the
 * widgets and the apps fill the height with no space between them. One row of apps (h) always stays, so no widget is higher than
 * H` = height - the margins - g - h (Slots.HOME); the widgets are cells of a grid 8 wide, the rows are the cells that fit into H`. All frames are as wide as the page frames. h and H` are kept in Prefs (dh_h, dh_hmax),
 * the settings pages for the desktop widgets read them to draw their previews.
 */
final class HomeBody extends ViewGroup {
    private final HomeActivity a;
    private final SlotArea area;
    private final View empty, grid;
    private Slots.Metrics last;

    HomeBody(HomeActivity a, View empty, View grid) {
        super(a);
        this.a = a;
        this.empty = empty;
        this.grid = grid;
        area = new SlotArea(a, Slots.HOME, true);
        addView(area);
        addView(empty);
        addView(grid);
    }

    SlotArea area() {
        return area;
    }

    private static int exact(int px) {
        return MeasureSpec.makeMeasureSpec(Math.max(0, px), MeasureSpec.EXACTLY);
    }

    /** The top of the app block: below the widgets, one gap g after them (g = m + m, the margins of the two frames). */
    private int appsTop() {
        int m = Frames.half(a), c = area.contentH();
        return c > 0 ? m + c + 2 * m : m;
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int W = MeasureSpec.getSize(wSpec), H = MeasureSpec.getSize(hSpec);
        setMeasuredDimension(W, H);
        int m = Frames.half(a), g = 2 * m, edge = Frames.edge(a), w = W - 2 * edge, row = a.appRowPx();
        int top = H - edge - m - g - row;   // H`: the highest a widget can be
        if (H < Ui.dp(a, 200) || w <= 0) return;   // not measured with a real size yet
        Slots.Metrics now = Slots.forTop(w, g, row, Math.max(0, top));
        if (last == null || last.w != now.w || last.h != now.h || last.rows != now.rows) {
            last = now;
            Prefs.putStr(a, "dh_h", String.valueOf(row));
            Prefs.putStr(a, "dh_hmax", String.valueOf(Math.max(0, top)));
            area.load(now);
        }
        area.measure(exact(w), exact(area.contentH()));
        int at = appsTop();
        empty.measure(exact(w), exact(H - edge - at));
        grid.measure(exact(w), exact(H - edge - at));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (last == null) return;
        int m = Frames.half(a), edge = Frames.edge(a), H = b - t, at = appsTop();
        area.layout(edge, m, edge + last.w, m + area.contentH());
        empty.layout(edge, at, edge + last.w, H - edge);
        grid.layout(edge, at, edge + last.w, H - edge);
    }
}
