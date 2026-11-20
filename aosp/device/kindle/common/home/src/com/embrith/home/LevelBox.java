/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * LevelBox: Vertical level control: rounded box filled from the bottom up to the level, icon at the bottom; drag
 * or tap sets the level, long press opens a pop-up. */
package com.embrith.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/**
 * A vertical level control: a rounded box with a black frame, filled light gray from the bottom up to the level, an icon at the bottom.
 * Drag (or tap) to set the level; a long press calls performLongClick (the pop-up with the extra check box).
 */
final class LevelBox extends View {
    interface Listener {
        void onLevel(int level);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF rect = new RectF();
    private final int icon, iconDp, radiusDp;
    private int max = 24, level;
    private Listener listener;
    private boolean dragging, longFired;
    private float downY;
    private long lastSent;
    private int sentLevel = -1;
    private final Runnable longPress = new Runnable() {
        @Override public void run() {
            longFired = true;
            dragging = false;
            performLongClick();
        }
    };

    LevelBox(Context c, int icon, int iconDp, int radiusDp) {
        super(c);
        this.icon = icon;
        this.iconDp = iconDp;
        this.radiusDp = radiusDp;
    }

    void setMax(int m) {
        max = m < 1 ? 1 : m;
        invalidate();
    }

    void setLevel(int v) {
        int n = v < 0 ? 0 : v > max ? max : v;
        if (n == level) return;
        level = n;
        invalidate();
    }

    int getLevel() {
        return level;
    }

    boolean isDragging() {
        return dragging;
    }

    void setListener(Listener l) {
        listener = l;
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float sw = Ui.dp(getContext(), 1f);   // the same thin line as every frame
        float r = Ui.dp(getContext(), radiusDp);
        rect.set(sw / 2f, sw / 2f, w - sw / 2f, h - sw / 2f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        c.drawRoundRect(rect, r, r, paint);
        int save = c.save();
        clip.reset();
        clip.addRoundRect(rect, r, r, Path.Direction.CW);
        c.clipPath(clip);
        paint.setColor(Color.rgb(0xC8, 0xC8, 0xC8));
        c.drawRect(0, h - h * level / max, w, h, paint);
        c.restoreToCount(save);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(sw);
        paint.setColor(Ui.LINE);
        c.drawRoundRect(rect, r, r, paint);
        paint.setStyle(Paint.Style.FILL);
        float is = Ui.dp(getContext(), iconDp);
        Icons.draw(getContext(), c, icon, w / 2f, h - is / 2f - Ui.dp(getContext(), 14), is, paint, false);
    }

    private int levelAt(float y) {
        float h = getHeight();
        if (h <= 0) return level;
        int v = Math.round((h - y) / h * max);
        return v < 0 ? 0 : v > max ? max : v;
    }

    private void send(boolean force) {
        if (listener == null) return;
        long now = android.os.SystemClock.uptimeMillis();
        if (force || (level != sentLevel && now - lastSent >= 150)) {
            listener.onLevel(level);
            sentLevel = level;
            lastSent = now;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN) {
            downY = e.getY();
            dragging = false;
            longFired = false;
            sentLevel = -1;
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            if (isLongClickable()) postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
        } else if (a == MotionEvent.ACTION_MOVE) {
            if (longFired) return true;
            if (!dragging && Math.abs(e.getY() - downY) > ViewConfiguration.get(getContext()).getScaledTouchSlop()) {
                dragging = true;
                removeCallbacks(longPress);
            }
            if (dragging) {
                setLevel(levelAt(e.getY()));
                send(false);
            }
        } else if (a == MotionEvent.ACTION_UP) {
            removeCallbacks(longPress);
            if (!longFired) {
                setLevel(levelAt(e.getY()));   // a tap sets the level too
                send(true);
            }
            dragging = false;
        } else if (a == MotionEvent.ACTION_CANCEL) {
            removeCallbacks(longPress);
            dragging = false;
        }
        return true;
    }
}
