/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * RingView: A ring of 4/5 of a circle that fills clockwise with the progress, with a label and the time in the
 * middle. */
package com.embrith.library;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import com.embrith.home.Ui;

/** A ring of 4/5 of a circle, open at the bottom, that fills clockwise from its left end with the progress; the darker the ring, the nearer the goal. Three lines sit in the middle: a label, the time (bold) and the goal. */
final class RingView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int pct;
    private String label = "", main = "", goal = "";
    private int maxW = Integer.MAX_VALUE;   // the widest slot the ring may use (px), so one ring and two rings come out the same size
    private int maxH = Integer.MAX_VALUE;   // the highest the ring may be (px)

    RingView(Context c) {
        super(c);
    }

    void setMaxWidth(int px) {
        maxW = px;
    }

    void setMaxHeight(int px) {
        maxH = px;
    }

    /** As high as the ring needs for its width (not more than maxH), so the parent can centre it. */
    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        float thick = Ui.dp(getContext(), 6), m = Ui.dp(getContext(), 12);
        int w = MeasureSpec.getSize(wSpec);
        float r = Math.max(0f, Math.min(w, maxW) / 2f - thick / 2f - m);
        setMeasuredDimension(w, Math.min((int) (2 * m + thick + 1.81f * r), maxH));
    }

    void set(int pct, String label, String main, String goal) {
        this.pct = Math.max(0, Math.min(100, pct));
        this.label = label;
        this.main = main;
        this.goal = goal;
        invalidate();
    }

    /** The shade of the ring for a progress: light at the start, black at the goal. */
    static int shade(int theme, int pct) {
        if (theme != Color.BLACK) return pct >= 100 ? theme : com.embrith.home.Prefs.mix(theme, 0.35f + 0.65f * pct / 100f);   // the theme color of the library: a gradient to the goal
        if (pct >= 100) return Color.BLACK;
        if (pct >= 66) return 0xFF333333;
        if (pct >= 33) return 0xFF666666;
        return 0xFF999999;
    }

    @Override
    protected void onDraw(Canvas cv) {
        float w = Math.min(getWidth(), maxW), h = getHeight();
        float thick = Ui.dp(getContext(), 6), thin = Math.max(2f, Ui.dp(getContext(), 1.5f)), m = Ui.dp(getContext(), 12);
        // 4/5 of a circle, open at the bottom: from 126 to 54 degrees clockwise, its lowest points are 0.81 r under the centre
        float r = Math.min(w / 2f - thick / 2f - m, (h - thick - 2 * m) / 1.81f);
        float cx = getWidth() / 2f, cy = m + thick / 2f + r;
        RectF box = new RectF(cx - r, cy - r, cx + r, cy + r);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        float done = 288f * pct / 100f;
        // like the level bars: a thin line for what is left, a thicker bar for the part done (both with round ends)
        int theme = com.embrith.home.Prefs.libAccent(getContext());
        paint.setColor(theme);
        paint.setStrokeWidth(thin);
        cv.drawArc(box, 126 + done, 288 - done, false, paint);
        if (pct > 0) {
            paint.setColor(shade(theme, pct));
            paint.setStrokeWidth(thick);
            cv.drawArc(box, 126, done, false, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.BLACK);
        paint.setTextAlign(Paint.Align.CENTER);
        float ms = r * 0.42f, ls = r * 0.17f;
        float base = cy + ms * 0.2f;   // the baseline of the time
        paint.setFakeBoldText(false);
        paint.setTextSize(ls);
        cv.drawText(label, cx, base - ms * 0.85f, paint);
        cv.drawText(goal, cx, base + ls * 1.8f, paint);
        paint.setFakeBoldText(true);
        paint.setTextSize(ms);
        cv.drawText(main, cx, base, paint);
        paint.setFakeBoldText(false);
    }
}
