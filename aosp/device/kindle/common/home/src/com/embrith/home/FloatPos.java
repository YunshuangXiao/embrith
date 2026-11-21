/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * FloatPos: The place of the floating button column (page buttons, library tools): at the right edge, movable by a long press on its grip. */
package com.embrith.home;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/**
 * The floating buttons of a page sit in one vertical column over the content, at the bottom right. The column has a small grip (three dots) on top: press it
 * for about half a second, then drag the whole column to another place and let go. The place is kept in Prefs (fl_r = the distance from the
 * right edge, fl_y = the distance from the bottom edge of the screen, both in dp) and is the same on every page, with or without a bottom bar: the
 * place does not depend on the page. The column never goes lower than the height of the bottom bar (Dock.HEIGHT), so it never covers the bar.
 * The default is the right edge itself (no margin to the page frames, so the column does not cover the buttons of the page frames) and 16 dp above
 * the height of the bottom bar. Settings > Personalize >
 * Appearance > Floating button position puts it back.
 * A tap on the grip folds the column to the grip alone, another tap unfolds it (Prefs fl_open, 1 = open, the same on every page).
 */
public final class FloatPos {
    static final String KEY_R = "fl_r", KEY_Y = "fl_y", KEY_OPEN = "fl_open";
    static final int DEF_R = 0, DEF_Y = Dock.HEIGHT + 16;   // dp

    private FloatPos() { }

    private static int read(Context c, String key, int def) {
        try { return Integer.parseInt(Prefs.getStr(c, key, String.valueOf(def))); } catch (NumberFormatException e) { return def; }
    }

    /** Sets the right and bottom margin of lp to the stored place (the parent is the whole page, so the place is the same on every page). */
    public static void place(Context c, FrameLayout.LayoutParams lp) {
        lp.rightMargin = Ui.dp(c, Math.max(0, read(c, KEY_R, DEF_R)));
        lp.bottomMargin = Ui.dp(c, Math.max(Dock.HEIGHT, read(c, KEY_Y, DEF_Y)));
    }

    /** Back to the default place (every page reads it again when it is built). */
    public static void reset(Context c) {
        Prefs.putStr(c, KEY_R, String.valueOf(DEF_R));
        Prefs.putStr(c, KEY_Y, String.valueOf(DEF_Y));
    }

    /** Shows or hides everything in the column except the grips (and the groups that hold a grip), by the stored state. */
    public static void applyOpen(Context c, ViewGroup box) {
        boolean open = !"0".equals(Prefs.getStr(c, KEY_OPEN, "1"));
        fold(box, open);
    }

    private static boolean hasGrip(View v) {
        if (v instanceof Grip) return true;
        if (!(v instanceof ViewGroup)) return false;
        ViewGroup g = (ViewGroup) v;
        for (int i = 0; i < g.getChildCount(); i++) if (hasGrip(g.getChildAt(i))) return true;
        return false;
    }

    private static void fold(ViewGroup g, boolean open) {
        for (int i = 0; i < g.getChildCount(); i++) {
            View v = g.getChildAt(i);
            if (v instanceof Grip) continue;
            if (v instanceof ViewGroup && hasGrip(v)) fold((ViewGroup) v, open);
            else v.setVisibility(open ? View.VISIBLE : View.GONE);
        }
    }

    /** The grip: three dots in a row on a small bar with the same light, transparent background as the round buttons. */
    public static View grip(Context c) {
        return new Grip(c);
    }

    /** The place of the grip in a vertical column: centered, with the same gap (10 dp) to the first button as the buttons have among themselves. */
    public static android.widget.LinearLayout.LayoutParams gripParams(Context c) {
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        lp.bottomMargin = Ui.dp(c, 10);
        return lp;
    }

    private static final class Grip extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), bg = new Paint(Paint.ANTI_ALIAS_FLAG);

        Grip(Context c) {
            super(c);
            p.setColor(Color.BLACK);
            bg.setColor(Color.argb(0x66, 0xF0, 0xF0, 0xF0));
        }

        @Override protected void onMeasure(int w, int h) {
            setMeasuredDimension(Ui.dp(getContext(), 34), Ui.dp(getContext(), 14));
        }

        @Override protected void onDraw(Canvas cv) {
            float round = getHeight() / 2f;
            cv.drawRoundRect(new RectF(0, 0, getWidth(), getHeight()), round, round, bg);
            int cx = getWidth() / 2, cy = getHeight() / 2, gap = Ui.dp(getContext(), 7);
            float r = Ui.dp(getContext(), 2);
            for (int i = -1; i <= 1; i++) cv.drawCircle(cx + i * gap, cy, r, p);
        }
    }

    /**
     * A long press on the grip, then moving the finger, moves the column box (its layout params are lp, a child of a FrameLayout with the gravity
     * right and bottom). While the finger moves the box follows by translation; when it is lifted the margins are set and the place is stored.
     * The column stays inside its parent and above the height of the bottom bar, on every page.
     */
    public static void attach(final Activity a, final View box, final FrameLayout.LayoutParams lp, View grip) {
        final int dockPx = Ui.dp(a, Dock.HEIGHT);
        final Handler h = new Handler(Looper.getMainLooper());
        final float[] st = new float[6];   // down x, down y, margins at the start (right, bottom), the margins now (right, bottom)
        final boolean[] on = new boolean[1], moved = new boolean[1];
        final int slop = ViewConfiguration.get(a).getScaledTouchSlop();
        final Runnable start = new Runnable() {
            @Override public void run() {
                on[0] = true;
                if (box.getParent() != null) box.getParent().requestDisallowInterceptTouchEvent(true);
                Ui.toast(a, "拖动到新位置, 松手保存", false);
            }
        };
        if (box instanceof ViewGroup) applyOpen(a, (ViewGroup) box);
        grip.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        st[0] = e.getRawX();
                        st[1] = e.getRawY();
                        st[2] = st[4] = lp.rightMargin;
                        st[3] = st[5] = lp.bottomMargin;
                        on[0] = false;
                        moved[0] = false;
                        h.postDelayed(start, ViewConfiguration.getLongPressTimeout());
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (!on[0]) {
                            if (Math.abs(e.getRawX() - st[0]) > slop || Math.abs(e.getRawY() - st[1]) > slop) {
                                moved[0] = true;
                                h.removeCallbacks(start);
                            }
                            return true;
                        }
                        if (!(box.getParent() instanceof ViewGroup)) return true;
                        ViewGroup parent = (ViewGroup) box.getParent();
                        float maxR = Math.max(0, parent.getWidth() - box.getWidth()), maxB = Math.max(dockPx, parent.getHeight() - box.getHeight());
                        st[4] = Math.max(0, Math.min(maxR, st[2] - (e.getRawX() - st[0])));
                        st[5] = Math.max(dockPx, Math.min(maxB, st[3] - (e.getRawY() - st[1])));
                        box.setTranslationX(st[2] - st[4]);
                        box.setTranslationY(st[3] - st[5]);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        h.removeCallbacks(start);
                        if (on[0]) {
                            on[0] = false;
                            box.setTranslationX(0);
                            box.setTranslationY(0);
                            lp.rightMargin = Math.round(st[4]);
                            lp.bottomMargin = Math.round(st[5]);
                            box.setLayoutParams(lp);
                            float d = a.getResources().getDisplayMetrics().density;
                            Prefs.putStr(a, KEY_R, String.valueOf(Math.round(st[4] / d)));
                            Prefs.putStr(a, KEY_Y, String.valueOf(Math.round(st[5] / d)));
                        } else if (e.getActionMasked() == MotionEvent.ACTION_UP && !moved[0] && box instanceof ViewGroup) {
                            boolean open = "0".equals(Prefs.getStr(a, KEY_OPEN, "1"));   // a tap: fold or unfold the column
                            Prefs.putStr(a, KEY_OPEN, open ? "1" : "0");
                            applyOpen(a, (ViewGroup) box);
                        }
                        return true;
                    default:
                        return true;
                }
            }
        });
    }
}
