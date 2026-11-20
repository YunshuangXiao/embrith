/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * WidgetOrderActivity: The order of the checked desktop widgets: one row for each, with a preview and up and down
 * buttons. */
package com.embrith.home;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.util.List;

/**
 * The order of the checked desktop widgets: one row for each, with a preview (the widget itself, drawn reduced and without touch) and
 * the buttons up and down. The order is the order of Prefs "widgets", which the desktop lays out row by row.
 */
public class WidgetOrderActivity extends Activity {
    private static final float PREVIEW_UNIT = 13;   // dp of one unit of width in the preview

    private LinearLayout body;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        body = Ui.page(this, "组件排序", null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        fill();
    }

    private void fill() {
        body.removeAllViews();
        List<String> en = Widgets.enabled(this);
        if (en.isEmpty()) {
            Ui.caption(body, "还没有勾选组件");
            return;
        }
        Ui.caption(body, "点上下箭头调整顺序; 预览是桌面上的样子 (缩小), 排在前面的先放");
        LinearLayout g = Ui.addGroup(body);
        for (int k = 0; k < en.size(); k++) Ui.add(g, rowOf(en, k));
    }

    private View rowOf(List<String> en, int k) {
        final String id = en.get(k);
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
        r.addView(preview(id));
        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 6), 0);
        mid.addView(Ui.text(this, (k + 1) + ". " + Ui.t(this, Widgets.name(id)), 17, true));
        mid.addView(Ui.text(this, Widgets.size(id), 14, false));
        r.addView(mid, new LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        IconButton up = new IconButton(this, Icons.UP, "", false, 28, 12), down = new IconButton(this, Icons.DOWN, "", false, 28, 12);
        up.setEnabled(k > 0);
        down.setEnabled(k < en.size() - 1);
        up.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Widgets.move(WidgetOrderActivity.this, id, -1); fill(); }
        });
        down.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Widgets.move(WidgetOrderActivity.this, id, 1); fill(); }
        });
        r.addView(up);
        r.addView(down);
        return r;
    }

    /** The widget as the desktop draws it (full size), scaled down into a frame of the reduced size; touches do not reach it. */
    private View preview(String id) {
        int u = Widgets.unit(this), m = Ui.dp(this, 4);
        int pw = Widgets.width(id) * u - 2 * m, ph = Widgets.height(id) * u - 2 * m;
        float s = Ui.dp(this, PREVIEW_UNIT) / (float) u;
        FrameLayout box = new FrameLayout(this) {
            @Override public boolean onInterceptTouchEvent(MotionEvent e) { return true; }
        };
        box.setBackground(Widgets.border(this, false));
        View v = Widgets.build(this, id, pw, ph, new Runnable() { @Override public void run() { } });
        v.setPivotX(0);
        v.setPivotY(0);
        v.setScaleX(s);
        v.setScaleY(s);
        box.addView(v, new FrameLayout.LayoutParams(pw, ph));
        box.setLayoutParams(new LinearLayout.LayoutParams(Math.round(pw * s), Math.round(ph * s)));
        return box;
    }
}