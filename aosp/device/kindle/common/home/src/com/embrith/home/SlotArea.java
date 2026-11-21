/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * SlotArea: The widget area of the settings home: the slots of Slots at their places; also the reduced previews. */
package com.embrith.home;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * The widget area of a set (settings home or desktop): every slot of the set at the place the flow layout gives it, built by SlotViews.
 * live = true on the pages (taps run the buttons, long press sets a slot up); live = false in the previews (drawn only, no touch; for the
 * desktop the preview also shows where the apps are).
 */
final class SlotArea extends FrameLayout {
    private final Activity a;
    private final Slots set;
    private final boolean live;
    private Slots.Metrics m;
    private int contentH;   // the lowest edge of the slots
    private final List<CircleButton> btns = new ArrayList<CircleButton>();
    private final List<String> acts = new ArrayList<String>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override public void run() { update(); }
    };
    private final Runnable redraw = new Runnable() {
        @Override public void run() { reload(); }
    };

    SlotArea(Activity a, Slots set, boolean live) {
        super(a);
        this.a = a;
        this.set = set;
        this.live = live;
    }

    /** The height the slots take: the lowest edge of the placed slots (0 when there are none). */
    int contentH() {
        return contentH;
    }

    void load(Slots.Metrics m) {
        this.m = m;
        reload();
    }

    void reload() {
        removeAllViews();
        btns.clear();
        acts.clear();
        contentH = 0;
        if (m == null) return;
        set.migrate(a, m);
        List<String> ids = set.order(a);
        List<int[]> pos = set.place(a, ids, m);
        for (int i = 0; i < ids.size(); i++) {
            int[] p = pos.get(i);
            if (p == null) continue;
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(p[2], p[3]);
            lp.leftMargin = p[0];
            lp.topMargin = p[1];
            contentH = Math.max(contentH, p[1] + p[3]);
            addView(SlotViews.build(a, set, ids.get(i), p[2], p[3], live, redraw, this), lp);
        }
        if (!live && set.apps) {   // the apps take the rest of the page
            int y = contentH > 0 ? contentH + m.g : 0, total = m.height(m.rows) + m.g + m.h;
            FrameLayout box = Widgets.framed(a);
            box.addView(Widgets.center(a, "应用区", 16, false), new FrameLayout.LayoutParams(-1, -1));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(m.w, Math.max(1, total - y));
            lp.topMargin = y;
            addView(box, lp);
        }
    }

    /** SlotViews registers its button views, so that their on / off look can follow the status. */
    void track(CircleButton b, String act) {
        btns.add(b);
        acts.add(act);
    }

    void update() {
        for (int i = 0; i < btns.size(); i++) QuickActions.setState(btns.get(i), acts.get(i));
    }

    /** The radios answer slowly: look again after a few seconds. */
    void later() {
        handler.removeCallbacks(refresh);
        handler.postDelayed(refresh, 4000);
        handler.postDelayed(refresh, 8000);
    }

    // the on / off look of the buttons follows the status of the radios: told only when Wi-Fi or bluetooth change, and only while the area is shown
    private final StatusBus.Listener bus = new StatusBus.Listener() {
        @Override public void changed() { if (getWindowVisibility() == VISIBLE) update(); }
    };

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (live) StatusBus.listen(bus, "WIFI", "BT");
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacks(refresh);
        StatusBus.unlisten(bus);
        super.onDetachedFromWindow();
    }

    // ------------------------------------------------------------------ reduced views
    /** The view v (laid out at w x h) drawn at the scale s, in a box of the reduced size; touches do not reach it. */
    static FrameLayout scaled(Context c, View v, int w, int h, float s) {
        FrameLayout box = new FrameLayout(c) {
            @Override public boolean onInterceptTouchEvent(MotionEvent e) { return true; }
        };
        v.setPivotX(0);
        v.setPivotY(0);
        v.setScaleX(s);
        v.setScaleY(s);
        box.addView(v, new FrameLayout.LayoutParams(w, h));
        box.setLayoutParams(new LinearLayout.LayoutParams(Math.round(w * s), Math.round(h * s)));
        return box;
    }

    /** Adds the preview of the whole area (as the page draws it, reduced) to the page body. */
    static void addPreview(LinearLayout body, Activity a, Slots set) {
        Slots.Metrics m = set.metrics(a);
        SlotArea area = new SlotArea(a, set, false);
        area.load(m);
        int total = set.apps ? m.height(m.rows) + m.g + m.h : m.height(m.rows);
        float s = Math.min(0.62f, 0.42f * a.getResources().getDisplayMetrics().heightPixels / total);
        FrameLayout box = scaled(a, area, m.w, total, s);
        box.setBackground(Widgets.border(a, false));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(box.getLayoutParams());
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.topMargin = Ui.dp(a, 14);
        lp.bottomMargin = Ui.dp(a, 8);
        body.addView(box, lp);
    }
}
