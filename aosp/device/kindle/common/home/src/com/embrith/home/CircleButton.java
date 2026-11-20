/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * CircleButton: Round quick button with an icon inside, no text; filled black when on, drawn inverted while
 * pressed. */
package com.embrith.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** A round quick button: black ring with an icon inside, no text. "on" = filled black with a white icon. Pressed = drawn inverted. */
final class CircleButton extends View {
    static final int SIZE_DP = 64;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int icon;
    private boolean on;
    private boolean off;
    private boolean square;   // a square frame with rounded corners instead of the ring (the custom buttons)

    CircleButton(Context c, int icon) {
        super(c);
        this.icon = icon;
        setClickable(true);
    }

    void setSquare(boolean square) {
        if (this.square == square) return;
        this.square = square;
        invalidate();
    }

    void setIcon(int icon) {
        this.icon = icon;
        invalidate();
    }

    void setOn(boolean on) {
        if (this.on == on) return;
        this.on = on;
        invalidate();
    }

    /** Draw the slashed variant of the icon (Wi-Fi and Bluetooth have their own "off" icons). */
    void setOff(boolean off) {
        if (this.off == off) return;
        this.off = off;
        invalidate();
    }

    @Override
    public void setPressed(boolean pressed) {
        super.setPressed(pressed);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        boolean filled = on != isPressed();
        float w = getWidth(), h = getHeight();
        float sw = Ui.dp(getContext(), 1.5f);
        float r = Math.min(w, h) / 2f - sw / 2f;
        RectF box = new RectF(w / 2f - r, h / 2f - r, w / 2f + r, h / 2f + r);
        float rad = Ui.dp(getContext(), 6);
        if (filled) {   // an unfilled button is transparent
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.BLACK);
            if (square) c.drawRoundRect(box, rad, rad, paint); else c.drawCircle(w / 2f, h / 2f, r, paint);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(sw);
        paint.setColor(Ui.LINE);
        if (square) c.drawRoundRect(box, rad, rad, paint); else c.drawCircle(w / 2f, h / 2f, r, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(filled ? Color.WHITE : Color.BLACK);
        Icons.draw(getContext(), c, icon, w / 2f, h / 2f, Ui.dp(getContext(), 30), paint, off);
    }
}
