/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * BarView: Flat level bar (frontlight, warmth) with the number above it; tap or drag sets the level, optionally
 * sent live while dragging. */
package com.embrith.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** A flat level bar (frontlight, warmth) with the number above it. Tap or drag to set the level: the bar follows the finger, with setLive(true) the value is also sent while dragging (throttled), and always on release. */
final class BarView extends View {
    interface Listener {
        void onLevel(int level);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int max = 24;
    private int level;
    private Listener listener;
    private boolean live;
    private boolean dragging;
    private long lastSent;
    private int sentLevel = -1;

    BarView(Context c) {
        super(c);
    }

    void setMax(int m) {
        max = m < 1 ? 1 : m;
        invalidate();
    }

    void setLevel(int v) {
        level = v < 0 ? 0 : v > max ? max : v;
        invalidate();
    }

    /** True while a finger is on the bar: a level coming from outside (a gesture) must not move it then. */
    boolean isDragging() {
        return dragging;
    }

    int getLevel() {
        return level;
    }

    void setLive(boolean b) {
        live = b;
    }

    void setListener(Listener l) {
        listener = l;
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth();
        int h = getHeight();
        int pad = Ui.dp(getContext(), 4);
        float mid = h / 2f;
        float thin = Math.max(2f, Ui.dp(getContext(), 1.5f));
        float thick = Ui.dp(getContext(), 6);
        float left = pad, right = w - pad;
        float split = left + (right - left) * level / max;
        paint.setColor(Color.BLACK);
        paint.setStyle(Paint.Style.FILL);
        // the part that is not filled: a thin line; the filled part: a thicker bar (both with round ends)
        c.drawRoundRect(new RectF(split, mid - thin / 2, right, mid + thin / 2), thin / 2, thin / 2, paint);
        if (level > 0) c.drawRoundRect(new RectF(left, mid - thick / 2, split, mid + thick / 2), thick / 2, thick / 2, paint);
        // the number above the end of the filled part (follows the finger while dragging)
        paint.setTextSize(Ui.dp(getContext(), 14));
        paint.setTextAlign(Paint.Align.CENTER);
        float tw = paint.measureText(String.valueOf(level));
        float tx = Math.max(left + tw / 2, Math.min(right - tw / 2, split));
        c.drawText(String.valueOf(level), tx, mid - thick / 2 - Ui.dp(getContext(), 4), paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (getWidth() <= 0) return true;
        int a = e.getActionMasked();
        dragging = a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_MOVE;
        if (a == MotionEvent.ACTION_DOWN && getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
        if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_MOVE || a == MotionEvent.ACTION_UP) {
            int pad = Ui.dp(getContext(), 4);
            int v = Math.round((e.getX() - pad) / (getWidth() - 2f * pad) * max);
            v = v < 0 ? 0 : v > max ? max : v;
            if (v != level) setLevel(v);   // redraws only when the number changes (e-ink)
            if (listener != null) {
                long now = android.os.SystemClock.uptimeMillis();
                if (a == MotionEvent.ACTION_UP) {
                    listener.onLevel(level);
                    sentLevel = level;
                } else if (live && level != sentLevel && now - lastSent >= 150) {
                    listener.onLevel(level);
                    sentLevel = level;
                    lastSent = now;
                }
            }
            if (a == MotionEvent.ACTION_UP) sentLevel = -1;
        }
        return true;
    }
}
