/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * ThinBar: A thin progress bar drawn like the frontlight level bars, with the percent over its end. */
package com.embrith.library;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import com.embrith.home.Ui;

/** A thin progress bar drawn like the level bars of the frontlight and the warmth: a thin line for what is left, a thicker bar for the part done, the percent over its end. */
final class ThinBar extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int pct;

    ThinBar(Context c) {
        super(c);
    }

    void set(int pct) {
        this.pct = Math.max(0, Math.min(100, pct));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        float mid = h - Ui.dp(getContext(), 8);
        float thin = Math.max(2f, Ui.dp(getContext(), 1.5f)), thick = Ui.dp(getContext(), 6);
        float left = Ui.dp(getContext(), 4), right = w - left;
        float split = left + (right - left) * pct / 100f;
        paint.setColor(com.embrith.home.Prefs.libAccent(getContext(), 2));
        paint.setStyle(Paint.Style.FILL);
        c.drawRoundRect(new RectF(split, mid - thin / 2, right, mid + thin / 2), thin / 2, thin / 2, paint);
        if (pct > 0) c.drawRoundRect(new RectF(left, mid - thick / 2, split, mid + thick / 2), thick / 2, thick / 2, paint);
        paint.setColor(Color.BLACK);
        String s = pct + "%";
        paint.setTextSize(Ui.dp(getContext(), 14));
        paint.setTextAlign(Paint.Align.CENTER);
        float tw = paint.measureText(s);
        c.drawText(s, Math.max(left + tw / 2, Math.min(right - tw / 2, split)), mid - thick / 2 - Ui.dp(getContext(), 4), paint);
    }
}
