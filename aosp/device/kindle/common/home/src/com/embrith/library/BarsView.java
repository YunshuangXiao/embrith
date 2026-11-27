/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * BarsView: Bar chart: one bar for each value, a label under it and a short text over it; the marked bar is black. */
package com.embrith.library;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

import com.embrith.home.Ui;

/** A bar chart: one bar for each value, a label under it and a short text over it; the bar at mark is black, the others grey. */
final class BarsView extends View {
    private final String[] labels, tops;
    private final long[] values;
    private final int mark;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    BarsView(Context c, String[] labels, long[] values, String[] tops, int mark) {
        super(c);
        this.labels = labels;
        this.values = values;
        this.tops = tops;
        this.mark = mark;
    }

    @Override
    protected void onDraw(Canvas cv) {
        int n = values.length;
        if (n == 0) return;
        float w = getWidth(), h = getHeight();
        float labelH = Ui.dp(getContext(), 24), topH = Ui.dp(getContext(), 20);
        float base = h - labelH - Ui.dp(getContext(), 2);
        long max = 1;
        for (long v : values) max = Math.max(max, v);
        float cw = w / n, bw = cw * 0.56f;
        int theme = com.embrith.home.Prefs.libAccent(getContext());
        paint.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < n; i++) {
            float cx = cw * i + cw / 2f;
            float bh = values[i] > 0 ? Math.max(Ui.dp(getContext(), 3), (base - topH) * values[i] / max) : Ui.dp(getContext(), 2);
            paint.setStyle(Paint.Style.FILL);
            if (theme == Color.BLACK) paint.setColor(i == mark ? Color.BLACK : 0xFF999999);
            else paint.setColor(i == mark ? theme : com.embrith.home.Prefs.mix(theme, 0.3f + 0.7f * values[i] / max));   // the theme gradient: the longer the bar, the deeper the color
            cv.drawRect(cx - bw / 2f, base - bh, cx + bw / 2f, base, paint);
            paint.setColor(Color.BLACK);
            paint.setTextSize(Ui.dp(getContext(), 12));
            if (tops[i].length() > 0) cv.drawText(tops[i], cx, base - bh - Ui.dp(getContext(), 3), paint);
            paint.setTextSize(Ui.dp(getContext(), 14));
            cv.drawText(labels[i], cx, h - Ui.dp(getContext(), 5), paint);
        }
    }
}
