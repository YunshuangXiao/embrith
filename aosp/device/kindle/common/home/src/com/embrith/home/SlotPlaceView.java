/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * SlotPlaceView: The reduced picture of a widget area on the setting page: the grid cells, the placed widgets, the selected one; a tap says which cell. */
package com.embrith.home;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the whole area (the grid of cells, every placed widget with its type and size, the apps block on the desktop) reduced to fit the
 * width. A tap is reported as the cell it hit and the widget in it (null for an empty cell). No animation (e-ink): the page redraws it.
 */
final class SlotPlaceView extends View {
    interface OnCell {
        void tap(int col, int row, String hit);
    }

    private final Slots set;
    private final Slots.Metrics m;
    private final List<String> ids;
    private final List<int[]> pos;
    private final String sel;
    private final OnCell cb;
    private final List<View> thumbs = new ArrayList<View>();   // the real widgets (as on the page), drawn reduced
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG), txt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int total;
    private float s, ox;
    private float dx, dy;

    SlotPlaceView(Activity c, Slots set, Slots.Metrics m, List<String> ids, List<int[]> pos, String sel, OnCell cb, Runnable redraw) {
        super(c);
        this.set = set;
        this.m = m;
        this.ids = ids;
        this.pos = pos;
        this.sel = sel;
        this.cb = cb;
        total = set.apps ? m.height(m.rows) + m.g + m.h : m.height(m.rows);
        line.setStyle(Paint.Style.STROKE);
        fill.setStyle(Paint.Style.FILL);
        txt.setColor(Color.BLACK);
        txt.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < ids.size(); i++) {
            int[] p = pos.get(i);
            View v = null;
            if (p != null) {
                v = SlotViews.build(c, set, ids.get(i), p[2], p[3], false, redraw, null);
                v.measure(MeasureSpec.makeMeasureSpec(p[2], MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(p[3], MeasureSpec.EXACTLY));
                v.layout(0, 0, p[2], p[3]);
            }
            thumbs.add(v);
        }
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int W = MeasureSpec.getSize(wSpec);
        float maxH = 0.5f * getResources().getDisplayMetrics().heightPixels;
        s = Math.min((float) W / m.w, maxH / Math.max(1, total));
        ox = (W - m.w * s) / 2f;
        setMeasuredDimension(W, Math.max(1, Math.round(total * s)));
    }

    private float sx(int px) { return ox + px * s; }

    private float sy(int px) { return px * s; }

    @Override
    protected void onDraw(Canvas cv) {
        line.setColor(Ui.LINE);
        line.setStrokeWidth(Ui.dp(getContext(), 0.5f) < 1 ? 1 : Ui.dp(getContext(), 0.5f));
        for (int r = 0; r < m.rows; r++) {
            for (int c = 0; c < m.cols; c++) cv.drawRect(sx(m.edge(c)), sy(m.edge(r)), sx(m.end(c, 1)), sy(m.end(r, 1)), line);
        }
        if (set.apps) {
            float top = sy(m.height(m.rows) + m.g);
            line.setStrokeWidth(Ui.dp(getContext(), 1));
            cv.drawRect(sx(0), top, sx(m.w), sy(total), line);
            txt.setTextSize(Ui.dp(getContext(), 12));
            cv.drawText(Ui.t(getContext(), "应用区"), sx(m.w / 2), (top + sy(total)) / 2f, txt);
        }
        for (int i = 0; i < ids.size(); i++) {
            int[] p = pos.get(i);
            if (p == null) continue;
            boolean on = ids.get(i).equals(sel);
            float l = sx(p[0]), t = sy(p[1]), r = sx(p[0] + p[2]), b = sy(p[1] + p[3]);
            fill.setColor(Color.WHITE);
            cv.drawRect(l, t, r, b, fill);
            View v = thumbs.get(i);
            if (v != null) {
                cv.save();
                cv.translate(l, t);
                cv.scale(s, s);
                v.draw(cv);
                cv.restore();
            }
            if (on) {
                fill.setColor(Color.argb(70, 0, 0, 0));
                cv.drawRect(l, t, r, b, fill);
            }
            line.setColor(Color.BLACK);
            line.setStrokeWidth(Ui.dp(getContext(), on ? 3 : 1));
            cv.drawRect(l, t, r, b, line);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            dx = e.getX();
            dy = e.getY();
            return true;
        }
        if (e.getAction() != MotionEvent.ACTION_UP) return true;
        if (Math.abs(e.getX() - dx) > Ui.dp(getContext(), 12) || Math.abs(e.getY() - dy) > Ui.dp(getContext(), 12)) return true;
        float px = (e.getX() - ox) / s, py = e.getY() / s;
        int col = Math.max(0, Math.min(m.cols - 1, (int) (px / (m.cell + m.g)))), row = Math.max(0, Math.min(m.rows - 1, (int) (py / (m.cell + m.g))));
        if (m.rows <= 0 || py > m.height(m.rows)) return true;
        String hit = null;
        for (int i = 0; i < ids.size() && hit == null; i++) {
            int[] p = pos.get(i);
            if (p != null && px >= p[0] && px < p[0] + p[2] && py >= p[1] && py < p[1] + p[3]) hit = ids.get(i);
        }
        cb.tap(col, row, hit);
        return true;
    }
}
