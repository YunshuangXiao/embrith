/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * ClockView: Black and white clock for the widgets: round dials in several styles, or a centered digital time. */
package com.embrith.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * A clock in a time zone (empty = the system time zone). Styles: plain (ticks), number (1 to 12), roman, minimal (four ticks), dots, and digital
 * (the time as big centered digits, 24 hour or 12 hour as the setting clock24). No second hand: the e-ink screen is redrawn once a minute.
 */
final class ClockView extends View {
    static final String[] STYLES = { "plain", "number", "roman", "minimal", "dots", "digital" };
    static final String[] NAMES = { "简约刻度", "数字 1-12", "罗马数字", "极简", "圆点刻度", "数字时钟" };
    private static final String[] ROMAN = { "XII", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI" };

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String style, tzId;
    // the minute hand: a beat at every full minute, only while this view is on the screen
    private final Tick tick = new Tick(new Runnable() {
        @Override public void run() { invalidate(); }
    }, 60000);

    ClockView(Context c, String style, String tzId) {
        super(c);
        this.style = style;
        this.tzId = tzId == null ? "" : tzId;
    }

    /** The style id when it is a known one, else the first one. */
    static String style(String s) {
        for (String t : STYLES) if (t.equals(s)) return s;
        return STYLES[0];
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) tick.start(); else tick.stop();   // also called when the view is attached
    }

    @Override
    protected void onDetachedFromWindow() {
        tick.stop();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas c) {
        Calendar cal = Calendar.getInstance(tzId.length() == 0 ? TimeZone.getDefault() : TimeZone.getTimeZone(tzId));
        if ("digital".equals(style)) digital(c, cal); else dial(c, cal);
    }

    private void digital(Canvas c, Calendar cal) {
        float w = getWidth(), h = getHeight();
        boolean h24 = Prefs.clock24(getContext());
        int hh = h24 ? cal.get(Calendar.HOUR_OF_DAY) : (cal.get(Calendar.HOUR) == 0 ? 12 : cal.get(Calendar.HOUR));
        String s = String.format(Locale.US, h24 ? "%02d:%02d" : "%d:%02d", hh, cal.get(Calendar.MINUTE));
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.BLACK);
        p.setTextAlign(Paint.Align.CENTER);
        p.setFakeBoldText(true);
        float size = Math.min(h * 0.45f, w * 0.34f);
        p.setTextSize(size);
        float tw = p.measureText(s);
        if (tw > w * 0.86f) {
            size *= w * 0.86f / tw;
            p.setTextSize(size);
        }
        Paint.FontMetrics fm = p.getFontMetrics();
        c.drawText(s, w / 2f, h / 2f - (fm.ascent + fm.descent) / 2f, p);
        p.setFakeBoldText(false);
    }

    private void dial(Canvas c, Calendar cal) {
        float w = getWidth(), h = getHeight(), cx = w / 2f, cy = h / 2f;
        float sw = Ui.dp(getContext(), 1.5f);
        float r = Math.min(w, h) / 2f - Ui.dp(getContext(), 6);
        p.setColor(Color.BLACK);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.BUTT);
        p.setStrokeWidth(sw);
        if (!"minimal".equals(style)) c.drawCircle(cx, cy, r, p);
        if ("plain".equals(style)) {
            for (int i = 0; i < 60; i++) {
                boolean major = i % 5 == 0;
                p.setStrokeWidth(major ? sw * 2.2f : sw * 0.8f);
                line(c, cx, cy, i * 6f, r - sw * 2 - r * (major ? 0.12f : 0.05f), r - sw * 2);
            }
        } else if ("minimal".equals(style)) {
            p.setStrokeWidth(sw * 3f);
            for (int i = 0; i < 4; i++) line(c, cx, cy, i * 90f, r * 0.84f, r * 0.98f);
        } else if ("dots".equals(style)) {
            p.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 12; i++) {
                float a = (float) Math.toRadians(i * 30f), d = r * 0.86f;
                c.drawCircle(cx + (float) Math.sin(a) * d, cy - (float) Math.cos(a) * d, r * (i % 3 == 0 ? 0.065f : 0.035f), p);
            }
        } else {   // number or roman
            boolean roman = "roman".equals(style);
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setFakeBoldText(true);
            p.setTextSize(r * (roman ? 0.16f : 0.21f));
            Paint.FontMetrics fm = p.getFontMetrics();
            for (int k = 0; k < 12; k++) {
                float a = (float) Math.toRadians(k * 30f), d = r * 0.78f;
                c.drawText(roman ? ROMAN[k] : String.valueOf(k == 0 ? 12 : k), cx + (float) Math.sin(a) * d,
                    cy - (float) Math.cos(a) * d - (fm.ascent + fm.descent) / 2f, p);
            }
            p.setFakeBoldText(false);
        }
        int m = cal.get(Calendar.MINUTE), hh = cal.get(Calendar.HOUR);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(Math.max(r * 0.07f, sw * 3f));
        line(c, cx, cy, (hh * 60 + m) * 0.5f, 0f, r * 0.5f);
        p.setStrokeWidth(Math.max(r * 0.045f, sw * 2f));
        line(c, cx, cy, m * 6f, 0f, r * 0.78f);
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(cx, cy, Math.max(r * 0.06f, sw * 2.5f), p);
        p.setStrokeCap(Paint.Cap.BUTT);
    }

    /** A line on the ray at the angle (degrees clockwise from 12 o'clock) from the radius r0 to r1. */
    private void line(Canvas c, float cx, float cy, float deg, float r0, float r1) {
        float a = (float) Math.toRadians(deg), s = (float) Math.sin(a), co = (float) Math.cos(a);
        c.drawLine(cx + s * r0, cy - co * r0, cx + s * r1, cy - co * r1, p);
    }
}
