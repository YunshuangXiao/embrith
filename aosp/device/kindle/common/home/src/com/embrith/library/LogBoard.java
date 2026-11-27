/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * LogBoard: The reading record on a screen of its own, in three tabs: overview, check-in and statistics. */
package com.embrith.library;

import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.embrith.home.Frames;
import com.embrith.home.IconButton;
import com.embrith.home.Icons;
import com.embrith.home.Prefs;
import com.embrith.home.Ui;
import com.embrith.language.T;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * The reading record, on a screen of its own, in three tabs (the fixed bar at the bottom of the page): the overview (recent covers, the goal of
 * today as a half ring, the week), the check-in (the days that reached the goal: totals, month, year) and the statistics (the month as a heat map,
 * the year). The frames are 10 units wide and 2, 3, 6 or 10 units high like the widgets. A tap on a day of the heat map opens the day, and a tap on a book in it the book.
 * The screen time of the apps counts as reading time, an app stands for a book.
 */
final class LogBoard {
    static final String[] TABS = { "概览", "打卡", "统计" };
    private static final String[] WEEK_ZH = { "一", "二", "三", "四", "五", "六", "日" };
    private static final String[] WEEK_EN = { "M", "T", "W", "T", "F", "S", "S" };
    private static final int[] GOAL_MIN = { 0, 15, 30, 45, 60, 90, 120, 180 };
    private static final String[] GOAL_TEXT = { "不设目标", "15 分钟", "30 分钟", "45 分钟", "1 小时", "1.5 小时", "2 小时", "3 小时" };
    private static final int HEAD = 44;   // dp: the row with the arrows over a month or a year

    private final LibraryActivity act;
    private int tab;
    private int ckYear, ckMonth, ckBar;   // check-in: the month shown and the year of the bars
    private int stYear, stMonth, stBar;   // statistics: the month shown and the year
    private String day;                   // a day opened (yyyyMMdd), or null
    private String book;                  // a book opened (path), or null
    private ReadTime.Data data;
    private Map<String, String> lastDay;          // book -> the newest day it was read
    private Map<String, Boolean> finished;        // book -> read to the end

    LogBoard(LibraryActivity act) {
        this.act = act;
        Calendar c = Calendar.getInstance();
        ckYear = stYear = ckBar = stBar = c.get(Calendar.YEAR);
        ckMonth = stMonth = c.get(Calendar.MONTH) + 1;
    }

    static String[] weekLabels(android.content.Context c) {
        return T.english(c) ? WEEK_EN : WEEK_ZH;
    }

    int tab() { return tab; }

    void setTab(int t) {
        tab = t;
        day = null;
        book = null;
    }

    /** Back from a day or a book to the tab; false when it is already on the tab. */
    boolean back() {
        if (day == null && book == null) return false;
        day = null;
        book = null;
        return true;
    }

    /** The title of the page while a day or a book is open, else null. */
    String title() {
        if (day != null) return "阅读记录 / " + date(day);
        if (book != null) return "阅读记录 / " + ReadTime.name(act, book);
        return null;
    }

    void render(LinearLayout body) {
        data = ReadTime.load();
        lastDay = new HashMap<String, String>();
        for (Map.Entry<String, Map<String, Long>> e : data.dayBooks.entrySet()) {
            if (e.getKey().equals(ReadTime.EARLIER)) continue;
            for (String k : e.getValue().keySet()) {
                String o = lastDay.get(k);
                if (o == null || o.compareTo(e.getKey()) < 0) lastDay.put(k, e.getKey());
            }
        }
        finished = new HashMap<String, Boolean>();
        if (day != null) dayView(body);
        else if (book != null) bookView(body);
        else if (tab == 0) overview(body);
        else if (tab == 1) checkin(body);
        else stats(body);
    }

    // ------------------------------------------------------------------ helpers
    /** One unit of the frames in pixels: the same as the unit of the desktop widgets. */
    private int u() {
        android.util.DisplayMetrics m = act.getResources().getDisplayMetrics();
        int byWidth = (m.widthPixels - 2 * (Ui.dp(act, Frames.edgeDp(act)) - Ui.dp(act, 4))) / 10;
        // every tab is 12 units high: it has to fit between the title row and the bar at the bottom (68 dp, 64 dp, the line, the bottom padding)
        int room = m.heightPixels - Ui.dp(act, 68 + 64 + 4) - (Frames.edge(act) - Frames.half(act));
        return Math.min(byWidth, room / 12);
    }

    /** The room inside a frame of this many units (the frame has margins and a border). */
    private int innerH(int units) {
        return units * u() - 2 * Frames.half(act) - Ui.dp(act, 3);
    }

    private int innerW() {
        return act.getResources().getDisplayMetrics().widthPixels - 2 * Frames.edge(act) - Ui.dp(act, 3);
    }

    private LinearLayout frame(LinearLayout body, int units) {
        return Frames.group(body, LibraryActivity.PAD, units * u() - 2 * Frames.half(act));
    }

    private TextView text(String s, int sp, boolean bold) {
        return Ui.text(act, s, sp, bold);
    }

    private String t(String s) {
        return T.t(act, s);
    }

    private static View.OnClickListener click(final Runnable r) {
        return new View.OnClickListener() {
            @Override public void onClick(View v) { r.run(); }
        };
    }

    private long goalMs() {
        return Screens.goal(act) * 60000L;
    }

    private boolean readOn() { return !"app".equals(Screens.goalMode(act)); }

    private boolean appOn() { return !"read".equals(Screens.goalMode(act)); }

    private long appGoalMs() {
        return Screens.appGoal(act) * 60000L;
    }

    /** "app:" and the package of the app of the app goal, null when none is chosen. */
    private String appKey() {
        String p = Screens.appPkg(act);
        return p.length() == 0 ? null : ReadTime.APP + p;
    }

    private boolean isFinished(String key) {
        if (ReadTime.isApp(key)) return false;
        Boolean b = finished.get(key);
        if (b == null) {
            b = ReadState.load(new File(key)).pct >= 99;
            finished.put(key, b);
        }
        return b;
    }

    /** A page button: the left or right icon, no text. */
    private View arrow(int icon) {
        return new IconButton(act, icon, "", true, 32, 12);
    }

    /** The row with the arrows (the title between them). */
    private LinearLayout header(String title, Runnable prev, Runnable next) {
        LinearLayout h = new LinearLayout(act);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        View p = arrow(Icons.CHEVRON_LEFT);
        p.setOnClickListener(click(prev));
        View n = arrow(Icons.RIGHT);
        n.setOnClickListener(click(next));
        TextView tt = text(title, 18, true);
        tt.setGravity(Gravity.CENTER);
        h.addView(p, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        h.addView(tt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        h.addView(n, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return h;
    }

    private LinearLayout.LayoutParams headParams() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(act, HEAD));
    }

    private static final String[] MODE_TEXT = { "仅阅读时间", "仅 APP 时间", "阅读和 APP 并排" };
    private static final String[] MODE_VAL = { "read", "app", "both" };

    /** Step 1: the type of the goal; then the reading goal (when it counts), the app and the app goal (when it counts). */
    private void goalDialog() {
        String[] items = new String[MODE_TEXT.length];
        for (int i = 0; i < items.length; i++) items[i] = t(MODE_TEXT[i]);
        new Ui.Builder(act).setTitle("目标类型").setItems(items, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                Prefs.putStr(act, "goal_mode", MODE_VAL[which]);
                if (which == 1) appPickDialog();
                else readGoalDialog(which == 2);
            }
        }).show();
    }

    private void readGoalDialog(final boolean thenApp) {
        String[] items = new String[GOAL_TEXT.length];
        for (int i = 0; i < items.length; i++) items[i] = t(GOAL_TEXT[i]);
        new Ui.Builder(act).setTitle("设置目标").setItems(items, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                Prefs.putStr(act, "goal_day", String.valueOf(GOAL_MIN[which]));
                if (thenApp) appPickDialog();
                else act.show();
            }
        }).show();
    }

    private void appPickDialog() {
        final List<String[]> apps = ReadTime.userApps(act);
        if (apps.isEmpty()) {
            act.show();
            return;
        }
        String[] items = new String[apps.size()];
        for (int i = 0; i < items.length; i++) items[i] = apps.get(i)[1];
        new Ui.Builder(act).setTitle("选择 APP").setItems(items, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                Prefs.putStr(act, "goal_app_pkg", apps.get(which)[0]);
                appGoalDialog();
            }
        }).show();
    }

    private void appGoalDialog() {
        String[] items = new String[GOAL_TEXT.length];
        for (int i = 0; i < items.length; i++) items[i] = t(GOAL_TEXT[i]);
        new Ui.Builder(act).setTitle("APP 目标").setItems(items, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                Prefs.putStr(act, "goal_app_min", String.valueOf(GOAL_MIN[which]));
                act.show();
            }
        }).show();
    }

    private static final float GOAL_FONT = 16;   // sp: the font of the goal line; the text of the button is a little smaller

    private int sp(float v) {
        return (int) android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, v, act.getResources().getDisplayMetrics());
    }

    /** The height of the button that changes the goal (the touch area; the button has no frame). */
    private int goalButtonH() {
        return sp(GOAL_FONT * 1.75f);
    }

    /** The button that changes the goal: a square-pen icon and the text side by side, no frame. inTitle: in the title row of the frame, the size of the title. */
    private View goalButton(boolean inTitle) {
        android.widget.Button b = Ui.button(act, t("修改目标"));
        float size = inTitle ? 15 : GOAL_FONT * 0.875f;
        b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, size);
        android.graphics.drawable.Drawable ic = act.getResources().getDrawable(com.embrith.home.R.drawable.ic_square_pen, null).mutate();   // Lucide square-pen
        ic.setTint(com.embrith.home.Prefs.libAccent(act));
        int z = sp(size);
        if (inTitle) b.setTypeface(null, android.graphics.Typeface.BOLD);
        // line box = ascent..descent (no font padding); the icon is centred on the line box, so move it by the distance between the line
        // box centre and the centre of the glyphs of this text, to put the centre of the icon on the centre of the text
        b.setIncludeFontPadding(false);
        android.graphics.Rect tb = new android.graphics.Rect();
        String label = b.getText().toString();
        b.getPaint().getTextBounds(label, 0, label.length(), tb);
        android.graphics.Paint.FontMetrics fm = b.getPaint().getFontMetrics();
        int dy = Math.round((tb.top + tb.bottom) / 2f - (fm.ascent + fm.descent) / 2f);
        ic.setBounds(0, dy, z, z + dy);
        b.setCompoundDrawables(ic, null, null, null);
        b.setCompoundDrawablePadding(Ui.dp(act, 6));
        if (inTitle) {
            b.setMinHeight(Ui.dp(act, 30));
            b.setMinimumHeight(Ui.dp(act, 30));
            b.setPadding(Ui.dp(act, 8), Ui.dp(act, 8), Ui.dp(act, 14), 0);   // the same top as the title text, the same distance from the right edge as the title from the left
            return b;
        }
        b.setMinHeight(goalButtonH());
        b.setMinimumHeight(goalButtonH());
        b.setPadding(Ui.dp(act, 8), 0, Ui.dp(act, 8), 0);
        return b;
    }

    private String goalText() {
        long g = goalMs();
        return g > 0 ? ReadTime.fmt(g) : t("未设置");
    }

    private String appGoalText() {
        long g = appGoalMs();
        return g > 0 ? ReadTime.fmt(g) : t("未设置");
    }

    /** Both goals in one line: the head, then for each goal a dot in the shade the check-in calendar uses for it (black reading, grey app) and its text. */
    private TextView goalsLine(String head, String reading, String app) {
        String dot = "\u25CF ";
        String all = head + " " + dot + reading + "   " + dot + app;
        android.text.SpannableString sp = new android.text.SpannableString(all);
        int i = head.length() + 1, j = all.indexOf(dot, i + dot.length());
        sp.setSpan(new android.text.style.ForegroundColorSpan(com.embrith.home.Prefs.libAccent(act)), i, i + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sp.setSpan(new android.text.style.ForegroundColorSpan(0xFF808080), j, j + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        TextView tv = text("", 16, false);
        tv.setText(sp);
        tv.setSingleLine(true);
        tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return tv;
    }

    /** The goal(s) in one line for the check-in page. */
    private String goalSummary() {
        if (!appOn()) return goalText();
        if (!readOn()) return appGoalText();
        return goalText() + " / " + appGoalText();
    }

    // ------------------------------------------------------------------ overview
    private void overview(LinearLayout body) {
        covers(body);
        goalFrame(body);
        week(body);
    }

    /** The title of a frame at its top left; returns the height it takes. */
    private int frameTitle(LinearLayout g, String s) {
        TextView tv = text(t(s), 15, true);
        tv.setPadding(Ui.dp(act, 14), Ui.dp(act, 8), 0, 0);
        int h = Ui.dp(act, 30);
        g.addView(tv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, h));
        return h;
    }

    private static String hhmm(long ms) {
        long m = ms / 60000;
        return String.format(Locale.US, "%02d:%02d", m / 60, m % 60);
    }

    /** 10:3: the covers of the books read last: as many as fit, spread from the left margin to the right margin with equal gaps (fewer books stay in the middle with the same gaps). */
    private void covers(LinearLayout body) {
        LinearLayout g = frame(body, 3);
        g.setOrientation(LinearLayout.VERTICAL);
        int titleH = frameTitle(g, "最近阅读");
        List<File> recent = LibStore.recent();
        List<File> books = new ArrayList<File>();
        if (recent != null) for (File f : recent) if (!f.isDirectory()) books.add(f);
        if (books.isEmpty()) {
            TextView e = text(t("还没有阅读记录"), 16, false);
            e.setGravity(Gravity.CENTER);
            g.addView(e, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            return;
        }
        int edge = Ui.dp(act, 16), minGap = Ui.dp(act, 12), vpad = Ui.dp(act, 4);
        int labH = Ui.dp(act, 22);
        int ch = innerH(3) - titleH - labH - 2 * vpad, cw = ch * 3 / 4;
        int rowW = innerW() - 2 * edge;
        int n = Math.max(1, (rowW + minGap) / (cw + minGap));
        int gap = n > 1 ? (rowW - n * cw) / (n - 1) : 0;
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        for (int i = 0; i < books.size() && i < n; i++) {
            File f = books.get(i);
            LinearLayout cell = new LinearLayout(act);   // the cover and the time read of the book under it
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.addView(cover(f, cw, ch), new LinearLayout.LayoutParams(cw, ch));
            Long ms = data.byBook.get(f.getPath());
            TextView tm = text(ReadTime.fmt(ms == null ? 0 : ms), 13, true);
            tm.setGravity(Gravity.CENTER);
            tm.setSingleLine(true);
            cell.addView(tm, new LinearLayout.LayoutParams(cw, labH));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) lp.leftMargin = gap;
            row.addView(cell, lp);
        }
        g.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    private View cover(final File f, final int cw, final int ch) {
        final FrameLayout box = CoverView.make(act, f, cw, ch, 16, 13);
        box.setClickable(true);
        box.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { act.open(f); }
        });
        box.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { return act.details(f); }
        });
        return box;
    }

    /** 10:6: the goal of today. A ring of 4/5 of a circle filled by the progress, in the middle the label, the time read (HH:MM, bold) and the goal; the button to change the goal under the ring. */
    private void goalFrame(LinearLayout body) {
        LinearLayout g = frame(body, 6);
        g.setOrientation(LinearLayout.VERTICAL);
        // the title row: the title at the left, the button that changes the goal at the right, in the same size
        LinearLayout head = new LinearLayout(act);
        head.setOrientation(LinearLayout.HORIZONTAL);
        TextView title = text(t("今日目标"), 15, true);
        title.setPadding(Ui.dp(act, 14), Ui.dp(act, 8), 0, 0);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        View set = goalButton(true);
        set.setOnClickListener(click(new Runnable() {
            @Override public void run() { goalDialog(); }
        }));
        head.addView(set, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        g.addView(head, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(act, 30)));

        boolean both = readOn() && appOn();
        // two rings: each view is 9/20 of the inner width; a ring view keeps m inside on each side (RingView), so the arcs are ringW - 2m wide;
        // the margin at each edge and the gap between the rings are the same: gap = (width - 2 arcs) / 3
        int m = Ui.dp(act, 12), ringW = innerW() * 9 / 20;
        int gap = (innerW() - 2 * (ringW - 2 * m)) / 3;
        int cap = innerH(6) - Ui.dp(act, 30);   // what the frame leaves for the rings under the title row
        LinearLayout rings = new LinearLayout(act);
        rings.setOrientation(LinearLayout.HORIZONTAL);
        if (both) rings.setPadding(gap - m, 0, gap - m, 0);
        if (readOn()) {
            long goal = goalMs(), today = data.day(ReadTime.today());
            int pct = goal > 0 ? (int) Math.min(100, today * 100 / goal) : 0;
            RingView ring = new RingView(act);
            ring.setMaxHeight(cap);
            ring.set(pct, t("阅读时长"), hhmm(today), t("目标") + " " + goalText());
            rings.addView(ring, both ? new LinearLayout.LayoutParams(ringW, ViewGroup.LayoutParams.WRAP_CONTENT)
                    : new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        if (appOn()) {
            String key = appKey();
            long goal = appGoalMs(), today = key == null ? 0 : data.dayKey(ReadTime.today(), key);
            int pct = goal > 0 ? (int) Math.min(100, today * 100 / goal) : 0;
            String name = key == null ? t("未选择") : ReadTime.name(act, key);
            int cut = readOn() ? 5 : 12;   // the name has to fit over a half ring
            if (name.length() > cut) name = name.substring(0, cut) + "...";
            RingView ring = new RingView(act);
            ring.setMaxHeight(cap);
            ring.set(pct, t("APP 时长") + " (" + name + ")", hhmm(today), t("目标") + " " + appGoalText());
            LinearLayout.LayoutParams lp = both ? new LinearLayout.LayoutParams(ringW, ViewGroup.LayoutParams.WRAP_CONTENT)
                    : new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (both) lp.leftMargin = gap - 2 * m;
            rings.addView(ring, lp);
        }
        // the rings, centred in what is left under the title row, both ways
        LinearLayout mid = new LinearLayout(act);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setGravity(Gravity.CENTER);
        mid.addView(rings, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        g.addView(mid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    /** 10:3: left the bars of the reading time of the seven days of this week, right the summary of this week (time, the app read longest, books read, books finished). */
    private void week(LinearLayout body) {
        LinearLayout f = frame(body, 3);
        f.setOrientation(LinearLayout.VERTICAL);
        frameTitle(f, "本周小结");
        LinearLayout g = new LinearLayout(act);
        g.setOrientation(LinearLayout.HORIZONTAL);
        f.addView(g, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        Calendar c = Calendar.getInstance();
        int todayIdx = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        c.add(Calendar.DAY_OF_YEAR, -todayIdx);
        long[] v = new long[7];
        String[] top = new String[7];
        Map<String, Long> wk = new HashMap<String, Long>();   // this week: book or app -> ms
        long weekMs = 0;
        for (int i = 0; i < 7; i++) {
            v[i] = data.day(fmtDay(c));
            weekMs += v[i];
            Map<String, Long> dm = data.dayBooks.get(fmtDay(c));
            if (dm != null) {
                for (Map.Entry<String, Long> e : dm.entrySet()) {
                    Long o = wk.get(e.getKey());
                    wk.put(e.getKey(), (o == null ? 0 : o) + e.getValue());
                }
            }
            top[i] = v[i] >= 60000 ? String.valueOf(v[i] / 60000) : "";
            c.add(Calendar.DAY_OF_YEAR, 1);
        }
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        bp.setMargins(Ui.dp(act, 10), Ui.dp(act, 2), 0, Ui.dp(act, 4));   // 10 dp at the left of the bars
        g.addView(new BarsView(act, weekLabels(act), v, top, todayIdx), bp);
        int n = 0, done = 0;
        String appKey = null;   // the app read longest this week
        long appMs = 0;
        for (Map.Entry<String, Long> e : wk.entrySet()) {
            String k = e.getKey();
            if (ReadTime.isApp(k)) {
                if (appKey == null || e.getValue() > appMs) {
                    appKey = k;
                    appMs = e.getValue();
                }
                continue;
            }
            n++;
            if (isFinished(k)) done++;
        }
        String appText = appKey == null ? t("APP 时长") + " 0" : ReadTime.name(act, appKey) + " " + ReadTime.fmt(appMs);
        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.dp(act, 15), Ui.dp(act, 2), Ui.dp(act, 10), Ui.dp(act, 4));   // 15 dp from the bars, 10 dp from the frame at the right
        Calendar mon = Calendar.getInstance();
        mon.add(Calendar.DAY_OF_YEAR, -todayIdx);
        Calendar sun = (Calendar) mon.clone();
        sun.add(Calendar.DAY_OF_YEAR, 6);
        java.text.DateFormat df = new SimpleDateFormat(T.english(act) ? "MMM d" : "M月d日", Locale.US);
        TextView range = text(df.format(mon.getTime()) + " - " + df.format(sun.getTime()), 16, true);   // the title of the summary
        range.setGravity(Gravity.CENTER);
        col.addView(range, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout two = new LinearLayout(act);
        two.setOrientation(LinearLayout.HORIZONTAL);
        // the two columns keep their own width and are left aligned inside; the first is at the left edge, the last ends at the right edge, the gap takes the rest
        two.addView(sumCol(t("累计时长") + " " + ReadTime.fmt(weekMs), appText, Gravity.LEFT), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        two.addView(new View(act), new LinearLayout.LayoutParams(0, 1, 1f));
        two.addView(sumCol(t("累计本数") + " " + n, t("完读本数") + " " + done, Gravity.LEFT), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        col.addView(two, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        g.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
    }

    /** Two rows of the summary, centred vertically in their half, at its left or right edge (side). */
    private View sumCol(String a, String b, int side) {
        LinearLayout c = new LinearLayout(act);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(side | Gravity.CENTER_VERTICAL);
        TextView ta = text(a, 14, true);
        ta.setGravity(side);
        ta.setSingleLine(true);
        ta.setEllipsize(android.text.TextUtils.TruncateAt.END);
        c.addView(ta, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView tb = text(b, 14, true);
        tb.setGravity(side);
        tb.setSingleLine(true);
        tb.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tb.setPadding(0, Ui.dp(act, 8), 0, 0);
        c.addView(tb, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return c;
    }

    // ------------------------------------------------------------------ check-in
    private void checkin(LinearLayout body) {
        long goal = readOn() ? goalMs() : 0;
        final String key = appOn() ? appKey() : null;
        long ag = key == null ? 0 : appGoalMs();
        List<String> ds = new ArrayList<String>();   // the days that reached a goal, oldest first
        if (goal > 0 || ag > 0) {
            for (Map.Entry<String, Long> e : data.byDay.entrySet()) {
                if (e.getKey().equals(ReadTime.EARLIER)) continue;
                if ((goal > 0 && e.getValue() >= goal) || (ag > 0 && data.dayKey(e.getKey(), key) >= ag)) ds.add(e.getKey());
            }
        }
        int longest = 0, run = 0;
        long prev = Long.MIN_VALUE;
        for (String d : ds) {
            long ep = epoch(d);
            run = ep == prev + 1 ? run + 1 : 1;
            prev = ep;
            longest = Math.max(longest, run);
        }
        // run is now the streak that ends with the newest checked day; it is the current one if that day is today or yesterday
        int current = !ds.isEmpty() && epoch(ds.get(ds.size() - 1)) >= epoch(ReadTime.today()) - 1 ? run : 0;

        LinearLayout top = frame(body, 3);
        top.setOrientation(LinearLayout.VERTICAL);
        LinearLayout nums = new LinearLayout(act);
        nums.setOrientation(LinearLayout.HORIZONTAL);
        nums.setPadding(Ui.dp(act, 16), 0, Ui.dp(act, 16), 0);   // the first is at the left edge, the last at the right edge, the middle one in the middle
        nums.addView(stat(ds.size(), "累计打卡", Gravity.LEFT), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        nums.addView(stat(longest, "最长连续", Gravity.CENTER_HORIZONTAL), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        nums.addView(stat(current, "当前连续", Gravity.RIGHT), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        top.addView(nums, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout low = new LinearLayout(act);
        low.setOrientation(LinearLayout.HORIZONTAL);
        low.setGravity(Gravity.CENTER_VERTICAL);
        low.setPadding(Ui.dp(act, 14), 0, Ui.dp(act, 8), Ui.dp(act, 4));
        if (readOn() && appOn()) {
            String name = key == null ? t("未选择") : ReadTime.name(act, key);   // key: the app of the goal, set above
            if (name.length() > 6) name = name.substring(0, 6) + "...";
            low.addView(goalsLine(t("当前目标") + ":", goalText(), name + " " + appGoalText()), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        } else {
            low.addView(text(t("当前目标") + ": " + goalSummary(), 16, false), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        View set = goalButton(false);
        set.setOnClickListener(click(new Runnable() {
            @Override public void run() { goalDialog(); }
        }));
        low.addView(set);
        top.addView(low, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 10:6: the month, the arrows turn the month inside the frame
        LinearLayout mf = frame(body, 6);
        mf.setOrientation(LinearLayout.VERTICAL);
        mf.addView(header(String.format(Locale.US, "%04d-%02d", ckYear, ckMonth), new Runnable() {
            @Override public void run() { ckMonth--; if (ckMonth < 1) { ckMonth = 12; ckYear--; } act.show(); }
        }, new Runnable() {
            @Override public void run() { ckMonth++; if (ckMonth > 12) { ckMonth = 1; ckYear++; } act.show(); }
        }), headParams());
        mf.addView(new CalendarView(act, ckYear, ckMonth, data.byDay, CalendarView.CHECK, goal, innerH(6) - Ui.dp(act, HEAD), new CalendarView.OnDay() {
            @Override public void picked(String d) { }
        }, data.dayBooks, key, ag), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 10:3: the check-ins of each month of a year as bars
        LinearLayout yf = frame(body, 3);
        yf.setOrientation(LinearLayout.VERTICAL);
        yf.addView(header(String.valueOf(ckBar), new Runnable() {
            @Override public void run() { ckBar--; act.show(); }
        }, new Runnable() {
            @Override public void run() { ckBar++; act.show(); }
        }), headParams());
        long[] v = new long[12];
        String[] lab = new String[12], tops = new String[12];
        for (String d : ds) {
            if (d.startsWith(String.format(Locale.US, "%04d", ckBar))) v[Integer.parseInt(d.substring(4, 6)) - 1]++;
        }
        int mark = ckBar == Calendar.getInstance().get(Calendar.YEAR) ? Calendar.getInstance().get(Calendar.MONTH) : -1;
        for (int i = 0; i < 12; i++) {
            lab[i] = String.valueOf(i + 1);
            tops[i] = v[i] > 0 ? String.valueOf(v[i]) : "";
        }
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        bp.setMargins(Ui.dp(act, 10), 0, Ui.dp(act, 10), Ui.dp(act, 4));
        yf.addView(new BarsView(act, lab, v, tops, mark), bp);
    }

    /** A number with its unit (days) and a label under it. */
    private View stat(int n, String label, int side) {
        LinearLayout c = new LinearLayout(act);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(side | Gravity.CENTER_VERTICAL);
        TextView a = text(n + t(" 天"), 24, true);
        a.setGravity(side);
        c.addView(a, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView b = text(label, 14, false);
        b.setGravity(side);
        c.addView(b, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return c;
    }

    // ------------------------------------------------------------------ statistics
    private void stats(LinearLayout body) {
        // 10:10: the month as a heat map (the time read under the day), the totals of this week and this month at the bottom
        LinearLayout mf = frame(body, 10);
        mf.setOrientation(LinearLayout.VERTICAL);
        mf.addView(header(String.format(Locale.US, "%04d-%02d", stYear, stMonth), new Runnable() {
            @Override public void run() { stMonth--; if (stMonth < 1) { stMonth = 12; stYear--; } act.show(); }
        }, new Runnable() {
            @Override public void run() { stMonth++; if (stMonth > 12) { stMonth = 1; stYear++; } act.show(); }
        }), headParams());
        int footH = Ui.dp(act, 52);
        mf.addView(new CalendarView(act, stYear, stMonth, data.byDay, CalendarView.HEAT, 0, innerH(10) - Ui.dp(act, HEAD) - footH, new CalendarView.OnDay() {
            @Override public void picked(String d) {
                day = d;
                act.show();
            }
        }), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Calendar c = Calendar.getInstance();
        String today = ReadTime.today();
        c.add(Calendar.DAY_OF_YEAR, -((c.get(Calendar.DAY_OF_WEEK) + 5) % 7));
        long wk = data.range(fmtDay(c), today);
        long mo = data.range(today.substring(0, 6) + "01", today);
        LinearLayout foot = new LinearLayout(act);
        foot.setOrientation(LinearLayout.HORIZONTAL);
        foot.setGravity(Gravity.CENTER_VERTICAL);
        TextView a = text(t("本周阅读") + " " + ReadTime.fmt(wk), 16, true);
        a.setGravity(Gravity.CENTER);
        TextView b = text(t("本月阅读") + " " + ReadTime.fmt(mo), 16, true);
        b.setGravity(Gravity.CENTER);
        foot.addView(a, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        foot.addView(b, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        mf.addView(foot, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, footH));

        // 10:2: the year: the reading time and the books finished; the arrows turn the year
        LinearLayout yf = frame(body, 2);
        yf.setOrientation(LinearLayout.HORIZONTAL);
        yf.setGravity(Gravity.CENTER_VERTICAL);
        String y = String.format(Locale.US, "%04d", stBar);
        long yt = data.range(y + "0101", y + "1231");
        int fin = 0;
        for (String k : data.byBook.keySet()) {
            String last = lastDay.get(k);
            if (isFinished(k) && last != null && last.startsWith(y)) fin++;
        }
        View p = arrow(Icons.CHEVRON_LEFT);
        p.setOnClickListener(click(new Runnable() {
            @Override public void run() { stBar--; act.show(); }
        }));
        View n = arrow(Icons.RIGHT);
        n.setOnClickListener(click(new Runnable() {
            @Override public void run() { stBar++; act.show(); }
        }));
        LinearLayout mid = new LinearLayout(act);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setGravity(Gravity.CENTER);
        mid.addView(text(y, 18, true));
        mid.addView(text(t("年阅读时长") + " " + ReadTime.fmt(yt) + "    " + t("年完结书籍") + " " + fin, 15, false));
        yf.addView(p, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        yf.addView(mid, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        yf.addView(n, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /** The layout of a bar: 12 dp high, side dp from the left and right, 10 dp below. */
    static LinearLayout.LayoutParams barParams(android.content.Context c, int side) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 12));
        p.setMargins(Ui.dp(c, side), 0, Ui.dp(c, side), Ui.dp(c, 10));
        return p;
    }

    /** A bordered bar filled to pct percent. */
    static LinearLayout bar(android.content.Context c, int pct) {
        pct = Math.max(0, Math.min(100, pct));
        LinearLayout b = new LinearLayout(c);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setWeightSum(100);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(android.graphics.Color.WHITE);
        bg.setStroke(Ui.dp(c, 1f), Ui.LINE);
        b.setBackground(bg);
        b.setPadding(Ui.dp(c, 2), Ui.dp(c, 2), Ui.dp(c, 2), Ui.dp(c, 2));
        View fill = new View(c);
        fill.setBackgroundColor(com.embrith.home.Prefs.libAccent(c));
        b.addView(fill, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, pct));
        b.addView(new View(c), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 100 - pct));
        return b;
    }

    // ------------------------------------------------------------------ one day, one book
    private void dayView(LinearLayout body) {
        LinearLayout g = Frames.group(body, LibraryActivity.PAD);
        Ui.add(g, Ui.row(act, "当天合计", ReadTime.fmt(data.day(day)), null));
        Map<String, Long> m = data.dayBooks.get(day);
        if (m != null) for (Map.Entry<String, Long> e : sorted(m)) bookRow(g, e.getKey(), e.getValue());
    }

    private void bookRow(LinearLayout g, final String key, long ms) {
        String name = ReadTime.name(act, key);
        if (ReadTime.isApp(key)) {
            Ui.add(g, Ui.row(act, name, ReadTime.fmt(ms), null));
            return;
        }
        Ui.add(g, Ui.nav(act, name, ReadTime.fmt(ms), new View.OnClickListener() {
            @Override public void onClick(View v) {
                book = key;
                act.show();
            }
        }));
    }

    private void bookView(LinearLayout body) {
        final File f = new File(book);
        LinearLayout g = Frames.group(body, LibraryActivity.PAD);
        Long total = data.byBook.get(book);
        Ui.add(g, Ui.row(act, "累计", ReadTime.fmt(total == null ? 0 : total), null));
        if (f.isFile()) {
            Ui.add(g, Ui.nav(act, "打开这本书", "", new View.OnClickListener() {
                @Override public void onClick(View v) { act.open(f); }
            }));
        }
        List<Map.Entry<String, Long>> days = new ArrayList<Map.Entry<String, Long>>(data.daysOf(book).entrySet());
        Collections.reverse(days);   // newest first
        for (Map.Entry<String, Long> e : days) {
            Ui.add(g, Ui.row(act, e.getKey().equals(ReadTime.EARLIER) ? "更早" : date(e.getKey()), ReadTime.fmt(e.getValue()), null));
        }
    }

    private static List<Map.Entry<String, Long>> sorted(Map<String, Long> m) {
        List<Map.Entry<String, Long>> l = new ArrayList<Map.Entry<String, Long>>(m.entrySet());
        Collections.sort(l, new Comparator<Map.Entry<String, Long>>() {
            @Override public int compare(Map.Entry<String, Long> a, Map.Entry<String, Long> b) { return Long.valueOf(b.getValue()).compareTo(a.getValue()); }
        });
        return l;
    }

    private static String fmtDay(Calendar c) {
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(c.getTime());
    }

    private static String date(String d) {
        return d.substring(0, 4) + "-" + d.substring(4, 6) + "-" + d.substring(6);
    }

    /** The day as a number of days since 1970, for the length of a streak. */
    private static long epoch(String d) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.clear();
        c.set(Integer.parseInt(d.substring(0, 4)), Integer.parseInt(d.substring(4, 6)) - 1, Integer.parseInt(d.substring(6)));
        return c.getTimeInMillis() / 86400000L;
    }
}
