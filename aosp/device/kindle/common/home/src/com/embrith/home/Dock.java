/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * Dock: Bottom bar of the desktop and of the library pages (overview, check-in, statistics, home, reading,
 * library, settings). */
package com.embrith.home;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.embrith.library.LibraryActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * The bottom bar of the desktop and of the library pages: overview, check-in, statistics, home, reading, library, settings. A tap goes
 * straight to the page: the library pages are shown by the library, home by the desktop, settings by the settings home.
 */
public final class Dock {
    private Dock() { }

    /** Implemented by the activity that shows the library pages: switches the page without starting another activity. */
    public interface Pages {
        void goPage(String id);
    }

    static final String[] IDS = { "ov", "ck", "st", "home", "reading", "shelf", "settings" };
    static final String[] TEXT = { "概览", "打卡", "统计", "主页", "阅读", "书库", "设置" };
    static final int[] ICON = { Icons.OVERVIEW, Icons.CHECKIN, Icons.STATS, Icons.HOME, Icons.READING, Icons.LIBRARY, Icons.SETTINGS };

    public static final int HEIGHT = 76;   // dp

    private static long went;   // uptime of the last tap on a button of the bar (the screen change that follows is a page turn)

    /** The desktop and the library ask for "not a page turn" when they come and go; not when the change comes from this bar. */
    public static void noSwipe() {
        if (SystemClock.uptimeMillis() - went > 2500) Ctl.request("noswipe");
    }

    /** The bar for activity a; cur = the id of the page shown (bold), null = none. No line and no background of its own, like the top bar. */
    public static LinearLayout build(final Activity a, String cur) {
        final LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Frames.edge(a), 0, Frames.edge(a), 0);   // no frame: the bar ends where the frame lines of the page end (the page margin)
        LinearLayout row = new SwipeRow(a, new SwipeRow.OnSwipe() {
            @Override public void swiped(int dir) { swipe(a, box, dir); }
        });
        fill(a, box, row);
        box.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(a, HEIGHT)));
        mark(box, cur);
        return box;
    }

    /** The buttons of the chosen pages, left to right in the chosen order (settings > personal). */
    private static void fill(final Activity a, final LinearLayout box, LinearLayout row) {
        List<String> ord = order(a);
        row.removeAllViews();
        for (final String id : ord) {
            int i = indexOf(id);
            IconButton b = new IconButton(a, ICON[i], TEXT[i], true, 28, 12);
            b.setTag(id);
            b.setGravity(Gravity.CENTER);   // icon and text centred vertically in the bar too
            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { go(a, box, id); }
            });
            if (row.getChildCount() > 0) row.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1f));   // equal stretchy gap between two buttons
            row.addView(b, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        int n = row.getChildCount();
        if (n > 1) {   // the first and the last button touch the frame lines (no outer padding); the gaps between buttons share the rest equally
            int p = Ui.dp(a, 6);
            View f = row.getChildAt(0), l = row.getChildAt(n - 1);
            f.setPadding(0, p, p, p);
            l.setPadding(p, p, 0, p);
        }
        row.setTag(ord.toString());   // what the bar shows now
    }

    /** Builds the buttons again when the chosen pages or their order changed since the bar was made (call from onResume). */
    public static void refresh(Activity a, LinearLayout box) {
        if (box == null) return;
        LinearLayout row = (LinearLayout) box.getChildAt(0);
        if (order(a).toString().equals(row.getTag())) return;
        fill(a, box, row);
        mark(box, (String) box.getTag());
    }

    /** Bold text and a dot under the button of page cur, normal for the others. */
    public static void mark(LinearLayout box, String cur) {
        if (cur != null) box.setTag(cur);   // the page shown (an open detail page keeps the one it came from)
        LinearLayout row = (LinearLayout) box.getChildAt(0);
        for (int i = 0; i < row.getChildCount(); i++) {
            View v = row.getChildAt(i);   // the gaps between the buttons are plain views: skipped below
            if (v instanceof IconButton) {
                boolean on = v.getTag().equals(cur);
                ((IconButton) v).setBold(on);
                ((IconButton) v).setDot(on);
            }
        }
    }

    private static int indexOf(String id) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return i;
        return -1;
    }

    /** Switching pages with the bar is a page turn: next when the target is to the right of the page shown, previous when to the left. */
    private static void go(Activity a, LinearLayout box, String id) {
        List<String> ord = order(a);
        int from = ord.indexOf((String) box.getTag()), to = ord.indexOf(id);
        if (from >= 0 && to >= 0 && from != to) {
            went = SystemClock.uptimeMillis();
            Ctl.put("turn", System.currentTimeMillis() + (to > from ? " N" : " P"));
        }
        go(a, id);
    }

    private static final String PREF = "dock_pages";   // the pages of the bar, in the order they were checked; not stored = all of them

    /** The pages of the bar from left to right, in the order they were checked (settings > personal); nothing stored = all seven. A swipe goes through the same order. */
    public static List<String> order(Context c) {
        List<String> r = new ArrayList<String>();
        String s = Prefs.getStr(c, PREF, "*");
        if ("*".equals(s)) {
            for (String id : IDS) r.add(id);
            return r;
        }
        for (String p : s.split(",")) if (indexOf(p) >= 0 && !r.contains(p)) r.add(p);
        return r;
    }

    static boolean isOn(Context c, String id) {
        return order(c).contains(id);
    }

    /** Checking a page puts it last in the order; unchecking removes it. */
    static void setOn(Context c, String id, boolean on) {
        List<String> r = order(c);
        r.remove(id);
        if (on) r.add(id);
        StringBuilder b = new StringBuilder();
        for (String p : r) b.append(b.length() > 0 ? "," : "").append(p);
        Prefs.putStr(c, PREF, b.toString());
    }

    /** A swipe: left = the next page of the order, right = the previous one; the ends meet (after the last comes the first). A page turn too. */
    private static void swipe(Activity a, LinearLayout box, int dir) {
        List<String> ord = order(a);
        int from = ord.indexOf((String) box.getTag());
        if (from < 0 || ord.size() < 2) return;   // the page shown is not one of the chosen pages
        int to = (from + dir + ord.size()) % ord.size();
        went = SystemClock.uptimeMillis();
        Ctl.put("turn", System.currentTimeMillis() + (dir > 0 ? " N" : " P"));
        go(a, ord.get(to));
    }

    private static float touchX, touchY;
    private static boolean touching;

    /**
     * A horizontal swipe anywhere on a page that has the bar switches the page (see swipe). Called by the page for every touch event; a touch
     * that starts in the bar is left to the bar, a second finger cancels (two-finger gestures belong to the display side).
     */
    public static void pageTouch(Activity a, LinearLayout box, MotionEvent e) {
        if (box == null) return;
        int act = e.getActionMasked();
        if (act == MotionEvent.ACTION_DOWN) {
            int[] xy = new int[2];
            box.getLocationOnScreen(xy);
            touching = e.getRawY() < xy[1];
            touchX = e.getRawX();
            touchY = e.getRawY();
        } else if (act == MotionEvent.ACTION_POINTER_DOWN || act == MotionEvent.ACTION_CANCEL) {
            touching = false;
        } else if (act == MotionEvent.ACTION_UP) {
            float dx = e.getRawX() - touchX, dy = e.getRawY() - touchY;
            boolean go = touching && Math.abs(dx) >= Ui.dp(a, 120) && Math.abs(dx) > Math.abs(dy) * 2;
            touching = false;
            if (go) swipe(a, box, dx < 0 ? 1 : -1);
        }
    }

    /** The row of buttons; takes over a horizontal drag from the buttons and reports it when it is long enough (-1 = to the right, 1 = to the left). */
    private static final class SwipeRow extends LinearLayout {
        interface OnSwipe {
            void swiped(int dir);
        }

        private final OnSwipe cb;
        private final int slop, min;
        private float downX, downY;
        private boolean taking;

        SwipeRow(Context c, OnSwipe cb) {
            super(c);
            this.cb = cb;
            slop = ViewConfiguration.get(c).getScaledTouchSlop();
            min = Ui.dp(c, 48);
            setOrientation(HORIZONTAL);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent e) {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                downX = e.getX();
                downY = e.getY();
                taking = false;
            } else if (a == MotionEvent.ACTION_MOVE && !taking) {
                float dx = Math.abs(e.getX() - downX);
                if (dx > slop * 2 && dx > Math.abs(e.getY() - downY)) taking = true;
            }
            return taking;
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (!taking) return false;
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_UP) {
                float dx = e.getX() - downX;
                taking = false;
                if (Math.abs(dx) >= min) cb.swiped(dx < 0 ? 1 : -1);
            } else if (a == MotionEvent.ACTION_CANCEL) {
                taking = false;
            }
            return true;
        }
    }
    private static void go(Activity a, String id) {
        if (id.equals("settings") && a instanceof MenuActivity && ((MenuActivity) a).isHome()) return;
        if (a instanceof Pages) {
            ((Pages) a).goPage(id);   // the desktop is the host of all seven pages: no other activity is started
        } else {
            a.startActivity(new Intent(a, HomeActivity.class).putExtra(LibraryActivity.EXTRA_PAGE, id)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        }
    }
}
