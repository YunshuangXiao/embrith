/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * IconView: Draws one icon from Icons at any size. */
package com.embrith.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/** One icon from Icons, any size. */
final class IconView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private int type, level = -1;
    private boolean charging;
    private int color = Color.BLACK;
    private boolean off;
    private boolean fade;
    private final Paint fadePaint = new Paint();

    IconView(Context c, int type) {
        super(c);
        this.type = type;
    }

    void setType(int t) {
        type = t;
        invalidate();
    }

    void setColor(int c) {
        color = c;
        invalidate();
    }

    /** Battery level (BATTERY only). */
    void setLevel(int l) {
        if (l == level) return;
        level = l;
        invalidate();
    }

    /** Charging mark inside the battery (BATTERY only). */
    void setCharging(boolean on) {
        if (on == charging) return;
        charging = on;
        invalidate();
    }

    void setOff(boolean o) {
        off = o;
        invalidate();
    }

    /** 按下反馈: 图标按 50% 棋盘格变淡(见 Ui.fade), 背景不动. */
    void setFade(boolean f) {
        if (f == fade) return;
        fade = f;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (!fade) { drawIcon(c); return; }
        int sc = c.saveLayer(0, 0, getWidth(), getHeight(), null, Canvas.ALL_SAVE_FLAG);
        drawIcon(c);
        int[] xy = new int[2];
        getLocationOnScreen(xy);
        fadePaint.setShader(Ui.checker((xy[0] + xy[1]) & 1, true));
        fadePaint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_OUT));
        c.drawRect(0, 0, getWidth(), getHeight(), fadePaint);
        c.restoreToCount(sc);
    }

    private void drawIcon(Canvas c) {
        paint.setColor(color);
        paint.setAlpha(255);
        float s = Math.min(getWidth(), getHeight());
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        if (type == Icons.BATTERY) { Icons.battery(getContext(), c, cx, cy, s * 0.92f, paint, level, charging); return; }
        Icons.draw(getContext(), c, type, cx, cy, s * 0.92f, paint, off);
    }
}
