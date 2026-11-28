/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * CalendarView: One month as a grid of days (Monday first); a tap on a day reports it as yyyyMMdd; days that
 * reached a goal are marked. */
package com.embrith.library;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import com.embrith.home.Ui;

import java.util.Calendar;
import java.util.Locale;
import java.util.Map;

/**
 * One month as a grid of days (Monday first) of a fixed total height; a tap on a day reports it as yyyyMMdd.
 * CHECK: a day that reached a goal gets a light grey circle (with both goals a dot under the number tells which: black reading, grey app). HEAT: a rounded square in a shade of grey by the time read, the time under the number.
 */
final class CalendarView extends View {
    static final int CHECK = 0, HEAT = 1;

    interface OnDay { void picked(String day); }

    
    private final int year, month;   // month 1..12
    private final Map<String, Long> byDay;
    private final int mode, totalH;
    private final long goal;
    private final Map<String, Map<String, Long>> dayBooks;   // for the goal of an app, else null
    private final String appKey;                             // "app:" and the package of the app of the goal, else null
    private final long appGoal;
    private final boolean both;                              // both goals count: the dots tell which one a day reached
    private final OnDay cb;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int firstCol, days, weeks, headH, cellH;
    private final long max;
    private final String today = ReadTime.today();

    CalendarView(Context c, int year, int month, Map<String, Long> byDay, int mode, long goal, int totalH, OnDay cb) {
        this(c, year, month, byDay, mode, goal, totalH, cb, null, null, 0);
    }

    CalendarView(Context c, int year, int month, Map<String, Long> byDay, int mode, long goal, int totalH, OnDay cb,
                 Map<String, Map<String, Long>> dayBooks, String appKey, long appGoal) {
        super(c);
        this.dayBooks = dayBooks;
        this.appKey = appKey;
        this.appGoal = appGoal;
        this.both = goal > 0 && appKey != null && appGoal > 0;
        this.year = year;
        this.month = month;
        this.byDay = byDay;
        this.mode = mode;
        this.goal = goal;
        this.totalH = totalH;
        this.cb = cb;
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(year, month - 1, 1);
        firstCol = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7;   // Monday = 0
        days = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        weeks = (firstCol + days + 6) / 7;
        headH = Ui.dp(c, 30);
        cellH = (totalH - headH) / 6;   // the same cell height for 4, 5 and 6 weeks
        long m = 0;
        for (int d = 1; d <= days; d++) {
            Long v = byDay.get(key(d));
            if (v != null) m = Math.max(m, v);
        }
        max = m;
    }

    private long appMs(String day) {
        Map<String, Long> m = dayBooks == null ? null : dayBooks.get(day);
        Long v = m == null || appKey == null ? null : m.get(appKey);
        return v == null ? 0 : v;
    }

    private String key(int d) {
        return String.format(Locale.US, "%04d%02d%02d", year, month, d);
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(MeasureSpec.getSize(w), totalH);
    }

    /** 0 = none, 1..4 = the darker the more time (against the longest day of the month). */
    private int level(long ms) {
        if (ms <= 0 || max <= 0) return 0;
        return (int) Math.max(1, Math.min(4, (ms * 4 + max - 1) / max));
    }

    @Override
    protected void onDraw(Canvas cv) {
        Context c = getContext();
        int theme = com.embrith.home.Prefs.calAccent(c, mode == CHECK);
        boolean themed = theme != Color.BLACK;   // the theme color of the library (the color display is on): gradients instead of grays
        int cw = getWidth() / 7;
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.BLACK);
        paint.setTextSize(Ui.dp(c, 15));
        String[] head = LogBoard.weekLabels(c);
        for (int i = 0; i < 7; i++) cv.drawText(head[i], cw * i + cw / 2f, headH * 0.7f, paint);
        for (int d = 1; d <= days; d++) {
            int idx = firstCol + d - 1;
            float cx = (idx % 7) * cw + cw / 2f;
            float top = headH + (idx / 7) * cellH;
            Long v = byDay.get(key(d));
            long ms = v == null ? 0 : v;
            boolean isToday = key(d).equals(today);
            int textColor = Color.BLACK;
            if (mode == CHECK) {
                float r = Math.min(cw, cellH) / 2f - Ui.dp(c, 4);
                float cy = top + cellH / 2f;
                boolean rd = goal > 0 && ms >= goal;
                boolean ap = appKey != null && appGoal > 0 && appMs(key(d)) >= appGoal;
                if (rd || ap) {
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(themed ? com.embrith.home.Prefs.mix(theme, 0.25f) : 0xFFD9D9D9);
                    cv.drawCircle(cx, cy, r, paint);
                }
                if (isToday) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(Ui.dp(c, 2));
                    paint.setColor(theme);
                    cv.drawCircle(cx, cy, r, paint);
                }
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(textColor);
                paint.setTextSize(Ui.dp(c, 17));
                cv.drawText(String.valueOf(d), cx, cy + Ui.dp(c, 6), paint);
                if (both && (rd || ap)) {
                    // under the number: a black dot for the reading goal, a grey dot for the app goal
                    float dr = Ui.dp(c, 2.5f), dy = cy + Ui.dp(c, 15), gap = Ui.dp(c, 5);
                    int n = (rd ? 1 : 0) + (ap ? 1 : 0);
                    float x = cx - (n - 1) * gap;
                    if (rd) {
                        paint.setColor(theme);
                        cv.drawCircle(x, dy, dr, paint);
                        x += 2 * gap;
                    }
                    if (ap) {
                        paint.setColor(0xFF808080);
                        cv.drawCircle(x, dy, dr, paint);
                    }
                }
            } else {
                float pad = Ui.dp(c, 3);
                RectF box = new RectF(cx - cw / 2f + pad, top + pad, cx + cw / 2f - pad, top + cellH - pad);
                int lv = level(ms);
                int[] shades = { 0xFFF4F4F4, 0xFFDDDDDD, 0xFFB0B0B0, 0xFF808080, 0xFF404040 };
                float[] ts = { 0.05f, 0.25f, 0.5f, 0.75f, 1f };   // the gradient of the theme color: the more time, the deeper
                if (themed) for (int k = 0; k < shades.length; k++) shades[k] = com.embrith.home.Prefs.mix(theme, ts[k]);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(shades[lv]);
                cv.drawRoundRect(box, Ui.dp(c, 6), Ui.dp(c, 6), paint);
                if (isToday) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(Ui.dp(c, 2));
                    paint.setColor(theme);
                    cv.drawRoundRect(box, Ui.dp(c, 6), Ui.dp(c, 6), paint);
                }
                textColor = (themed ? com.embrith.home.Prefs.isDark(shades[lv]) : lv >= 3) ? Color.WHITE : Color.BLACK;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(textColor);
                paint.setTextSize(Ui.dp(c, 16));
                cv.drawText(String.valueOf(d), cx, top + cellH * 0.45f, paint);
                if (ms >= 60000) {
                    paint.setTextSize(Ui.dp(c, 11));
                    cv.drawText(ReadTime.fmt(ms), cx, top + cellH * 0.82f, paint);
                }
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) return true;
        if (e.getAction() != MotionEvent.ACTION_UP) return false;
        if (e.getY() < headH) return true;
        int idx = (int) ((e.getY() - headH) / cellH) * 7 + (int) (e.getX() / (getWidth() / 7f));
        int d = idx - firstCol + 1;
        if (d >= 1 && d <= days) cb.picked(key(d));
        return true;
    }
}
