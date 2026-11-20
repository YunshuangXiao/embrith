/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * Widgets: Desktop widgets: width and height units, which ones are checked and in what order, and how they are
 * placed. */package com.embrith.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.embrith.library.NowReading;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Desktop widgets. The usable width is 10 units (2 columns of 5); a widget is 3 or 6 units high. The checked ids are kept in the order of the list
 * (Prefs "widgets", comma separated, moved in WidgetOrderActivity) and are packed one by one into the first free place of the grid
 * (columns of 5 units, rows of 3 units), so a later widget fills a gap left beside a 5:6 one, and two 5:6 can stand staggered.
 * img4 img5 (5:6) and img6 (10:6) are pictures like img; reading (10:3) = the book opened last, with cover, title, author and reading time.
 * sp1 sp2 (5:3) and sp3 (10:3) are blank: transparent, no frame. Content: img = a picture (Prefs w_img_wall, chosen in WallpaperActivity with target w_img), t1 and t2 = custom text (Prefs w_t1, w_t2),
 * st_all = screen time of all user apps, st_app and st_app2 = screen time of one app each (Prefs w_st_app_pkg, w_st_app2_pkg). Long press a widget to set it up.
 */
public final class Widgets {
    static final String[] IDS = { "img", "img2", "img3", "t1", "t2", "st_all", "st_app", "st_app2", "sp1", "sp2", "sp3", "img4", "img5", "img6", "reading" };
    static final String[] NAMES = { "图片 1", "图片 2", "图片 3", "自定义文字 1", "自定义文字 2", "屏幕时间: 总计", "屏幕时间: 单个应用 1", "屏幕时间: 单个应用 2", "空白 1", "空白 2", "空白 3", "图片 4", "图片 5", "图片 6", "正在阅读" };
    static final String[] SIZES = { "5:3", "5:3", "10:3", "5:3", "10:3", "5:3", "5:3", "5:3", "5:3", "5:3", "10:3", "5:6", "5:6", "10:6", "10:3" };
    static final int[] WIDTH = { 5, 5, 10, 5, 10, 5, 5, 5, 5, 5, 10, 5, 5, 10, 10 };
    static final int[] HEIGHT = { 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 6, 6, 6, 3 };
    static final String[] GROUPS = { "图片", "文字", "屏幕时间", "阅读", "空白" };   // the groups of the list in the settings
    static final int[] GROUP = { 0, 0, 0, 1, 1, 2, 2, 2, 4, 4, 4, 0, 0, 0, 3 };   // group of each id
    static final int ROW_H = 3;   // the unit of the heights
    static final int MAX_H = 9;   // the checked widgets may be 9 units high in total (3 rows)

    private Widgets() { }

    static int width(String id) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return WIDTH[i];
        return 0;
    }

    static int height(String id) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return HEIGHT[i];
        return 0;
    }

    static String name(String id) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return NAMES[i];
        return id;
    }

    static String size(String id) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return SIZES[i];
        return "";
    }

    /** One unit in pixels: the width of the desktop (without the margins of the widget frames) divided by 10. */
    static int unit(Context c) {
        return (c.getResources().getDisplayMetrics().widthPixels - 2 * (Frames.edge(c) - Frames.half(c))) / 10;
    }

    /**
     * The flow layout: each widget, in list order, takes the first free place of the grid (2 columns of 5 units, rows of 3 units),
     * looking row by row and from left to right. Returns {column, row, columns, rows} for every widget of l.
     */
    static List<int[]> place(List<String> l) {
        List<int[]> out = new ArrayList<int[]>();
        List<boolean[]> used = new ArrayList<boolean[]>();   // used.get(r)[c]: the cell is taken
        for (String s : l) {
            int cw = width(s) / 5, rh = height(s) / ROW_H;
            int r = 0, c = 0;
            search:
            for (;; r++) {
                for (c = 0; c + cw <= 2; c++) if (free(used, r, c, cw, rh)) break search;
            }
            while (used.size() < r + rh) used.add(new boolean[2]);
            for (int i = r; i < r + rh; i++) for (int j = c; j < c + cw; j++) used.get(i)[j] = true;
            out.add(new int[] { c, r, cw, rh });
        }
        return out;
    }

    private static boolean free(List<boolean[]> used, int r, int c, int cw, int rh) {
        for (int i = r; i < r + rh; i++) {
            if (i >= used.size()) break;
            for (int j = c; j < c + cw; j++) if (used.get(i)[j]) return false;
        }
        return true;
    }

    /** The height of the layout in units: the lowest edge of the placed widgets. */
    static int totalHeight(List<String> l) {
        int m = 0;
        for (int[] p : place(l)) m = Math.max(m, p[1] + p[3]);
        return m * ROW_H;
    }

    static List<String> enabled(Context c) {
        List<String> l = new ArrayList<String>();
        for (String s : Prefs.getStr(c, "widgets", "").split(",")) if (s.length() > 0 && width(s) > 0 && !l.contains(s)) l.add(s);
        return l;
    }

    /** True if checking id keeps the flow layout within MAX_H (same placing rule as HomeActivity.loadWidgets). */
    static boolean fits(Context c, String id) {
        List<String> l = enabled(c);
        if (!l.contains(id)) l.add(id);
        return totalHeight(l) <= MAX_H;
    }

    static boolean isOn(Context c, String id) {
        return enabled(c).contains(id);
    }

    static void setOn(Context c, String id, boolean on) {
        List<String> l = enabled(c);
        l.remove(id);
        if (on) l.add(id);
        save(c, l);
    }

    /** Moves a checked widget one place up (d = -1) or down (d = 1) in the order. */
    static void move(Context c, String id, int d) {
        List<String> l = enabled(c);
        int i = l.indexOf(id), j = i + d;
        if (i < 0 || j < 0 || j >= l.size()) return;
        Collections.swap(l, i, j);
        save(c, l);
    }

    private static void save(Context c, List<String> l) {
        StringBuilder sb = new StringBuilder();
        for (String s : l) sb.append(sb.length() > 0 ? "," : "").append(s);
        Prefs.putStr(c, "widgets", sb.toString());
    }

    /** The desktop wallpaper as a page background for the library and recent (null: the switch wall_lib is off, or the wallpaper is plain white). */
    public static Drawable wallDrawable(Activity a) {
        if (!"1".equals(Prefs.getStr(a, "wall_lib", "0"))) return null;
        String w = Img.resolve(a, "home");
        if (w.length() == 0) return null;
        DisplayMetrics dm = a.getResources().getDisplayMetrics();
        Bitmap b = Img.load(w, dm.widthPixels, dm.heightPixels, Img.fade(a, "home"));
        return b == null ? null : new BitmapDrawable(a.getResources(), b);
    }

    /** A black rounded border (the same as Ui.outline); fill = white inside (widgets), otherwise transparent (the app grid block). */
    public static GradientDrawable border(Context c, boolean fill) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill ? Color.WHITE : Color.TRANSPARENT);
        d.setStroke(Ui.dp(c, 1f), Ui.LINE);
        d.setCornerRadius(Ui.radius(c));
        return d;
    }

    /** The picture with rounded corners (transparent outside), the same radius as the frame. */
    private static Bitmap rounded(Bitmap src, float radius) {
        Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        new Canvas(out).drawRoundRect(new RectF(0, 0, src.getWidth(), src.getHeight()), radius, radius, p);
        src.recycle();
        return out;
    }

    static View build(final Activity a, final String id, int w, int h, final Runnable redraw) {
        if (id.startsWith("sp")) return new View(a);   // blank widget: no frame, no background, only takes the place
        FrameLayout f = new FrameLayout(a);
        f.setBackground(border(a, false));       // transparent: the wallpaper shows through
        f.setClipToOutline(true);                // the content follows the rounded corners of the frame
        f.setForeground(border(a, false));       // the frame line is drawn over the content
        f.setLongClickable(true);
        f.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { config(a, id, redraw); return true; }
        });
        if (id.startsWith("img")) {
            ImageView iv = new ImageView(a);
            iv.setScaleType(ImageView.ScaleType.FIT_XY);
            Bitmap b = Img.load(Img.resolve(a, "w_" + id), w, h, 0);
            if (b != null) {
                b = rounded(b, Ui.radius(a));   // the corners are cut out of the picture itself: no dependence on the renderer
                iv.setImageBitmap(b);
                f.addView(iv, new FrameLayout.LayoutParams(-1, -1));
            } else {
                f.addView(center(a, "长按选择图片", 16, false), new FrameLayout.LayoutParams(-1, -1));
            }
        } else if ("t1".equals(id) || "t2".equals(id)) {
            f.addView(center(a, Prefs.getStr(a, "w_" + id, "长按编辑文字"), "t1".equals(id) ? 22 : 28, true), new FrameLayout.LayoutParams(-1, -1));
        } else if ("reading".equals(id)) {
            reading(a, f, h);
        } else {
            stats(a, f, id);
        }
        return f;
    }

    private static TextView center(Context c, String s, int sp, boolean bold) {
        TextView t = Ui.text(c, s, sp, bold);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.dp(c, 10), Ui.dp(c, 6), Ui.dp(c, 10), Ui.dp(c, 6));
        return t;
    }

    // ------------------------------------------------------------------ reading
    /** The book opened last: cover, title, author and the time spent reading it. The book is read in the background. */
    private static void reading(final Activity a, final FrameLayout f, int h) {
        final int pad = Ui.dp(a, 10), ch = h - 2 * pad, cw = ch * 3 / 4;
        f.addView(center(a, "还没有阅读记录", 16, false), new FrameLayout.LayoutParams(-1, -1));
        new Thread(new Runnable() {
            @Override public void run() {
                final NowReading.Info i = NowReading.latest(cw, ch);
                a.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (i == null || a.isDestroyed()) return;
                        f.removeAllViews();
                        f.addView(readingRow(a, i, pad, cw, ch), new FrameLayout.LayoutParams(-1, -1));
                    }
                });
            }
        }).start();
    }

    private static View readingRow(Context c, NowReading.Info i, int pad, int cw, int ch) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(pad, pad, pad, pad);
        View cover;
        if (i.cover != null) {
            ImageView iv = new ImageView(c);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setImageBitmap(i.cover);
            cover = iv;
        } else {
            TextView t = center(c, i.ext, 14, true);   // no cover: the format name in a frame
            t.setBackground(border(c, false));
            cover = t;
        }
        row.addView(cover, new LinearLayout.LayoutParams(cw, ch));
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_VERTICAL);
        col.setPadding(Ui.dp(c, 14), 0, 0, 0);
        TextView title = Ui.text(c, i.title, 20, true);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        col.addView(title);
        if (i.author.length() > 0) {
            TextView author = Ui.text(c, i.author, 15, false);
            author.setSingleLine(true);
            author.setEllipsize(android.text.TextUtils.TruncateAt.END);
            col.addView(author);
        }
        col.addView(Ui.text(c, readTime(c, i.ms), 15, false));
        row.addView(col, new LinearLayout.LayoutParams(0, -1, 1f));
        return row;
    }

    private static String readTime(Context c, long ms) {
        long min = ms / 60000;
        return Ui.t(c, "已读 ") + (min >= 60 ? (min / 60) + Ui.t(c, " 小时 ") + (min % 60) + Ui.t(c, " 分") : min + Ui.t(c, " 分钟"));
    }

    // ------------------------------------------------------------------ screen time
    private static void stats(final Activity a, FrameLayout f, final String id) {
        final TextView title = Ui.text(a, "", 15, false);
        final TextView value = Ui.text(a, "", 28, true);
        final TextView per = Ui.text(a, "", 14, false);
        LinearLayout left = new LinearLayout(a);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setGravity(Gravity.CENTER_VERTICAL);
        left.setPadding(Ui.dp(a, 12), 0, 0, 0);
        left.addView(title);
        left.addView(value);
        left.addView(per);
        LinearLayout right = new LinearLayout(a);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.CENTER);
        View.OnClickListener up = new View.OnClickListener() {
            @Override public void onClick(View v) { Prefs.putStr(a, "w_" + id + "_mode", "w"); fill(a, id, title, value, per); }
        };
        View.OnClickListener down = new View.OnClickListener() {
            @Override public void onClick(View v) { Prefs.putStr(a, "w_" + id + "_mode", "d"); fill(a, id, title, value, per); }
        };
        IconButton bu = new IconButton(a, Icons.UP, "", false, 28, 12);
        IconButton bd = new IconButton(a, Icons.DOWN, "", false, 28, 12);
        bu.setOnClickListener(up);
        bd.setOnClickListener(down);
        right.addView(bu, new LinearLayout.LayoutParams(Ui.dp(a, 56), Ui.dp(a, 56)));
        right.addView(bd, new LinearLayout.LayoutParams(Ui.dp(a, 56), Ui.dp(a, 56)));
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(left, new LinearLayout.LayoutParams(0, -1, 1f));
        row.addView(right, new LinearLayout.LayoutParams(-2, -1));
        f.addView(row, new FrameLayout.LayoutParams(-1, -1));
        fill(a, id, title, value, per);
    }

    private static void fill(Context c, String id, TextView title, TextView value, TextView per) {
        boolean week = "w".equals(Prefs.getStr(c, "w_" + id + "_mode", "d"));
        Calendar cal = Calendar.getInstance();
        long now = cal.getTimeInMillis();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (week) cal.add(Calendar.DAY_OF_YEAR, -6);
        String pkg = Prefs.getStr(c, "w_" + id + "_pkg", "");
        PackageManager pm = c.getPackageManager();
        boolean one = id.startsWith("st_app");
        String name = "屏幕时间";
        if (one) {
            name = "长按选择应用";
            try { if (pkg.length() > 0) name = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0))); } catch (PackageManager.NameNotFoundException e) { pkg = ""; }
        }
        title.setText(name);
        long ms = 0;
        boolean any = false;
        {   // the events of the range (today or the last 7 days), cut exactly, without the time the Kindle slept (see Usage)
            Map<String, Long> t = Usage.range(c, cal.getTimeInMillis(), now);
            any = !t.isEmpty();
            for (Map.Entry<String, Long> en : t.entrySet()) {
                if (one) {
                    if (en.getKey().equals(pkg)) ms += en.getValue();
                } else if (!en.getKey().equals(c.getPackageName()) && !isSystem(pm, en.getKey())) {
                    ms += en.getValue();
                }
            }
        }
        long min = ms / 60000;
        value.setText(!any ? "--" : min >= 60 ? (min / 60) + Ui.t(c, " 小时 ") + (min % 60) + Ui.t(c, " 分") : min + Ui.t(c, " 分钟"));
        per.setText((week ? Ui.t(c, "近 7 天") : Ui.t(c, "今日")) + (any ? "" : Ui.t(c, " (未授权或无数据)")));
    }

    private static boolean isSystem(PackageManager pm, String pkg) {
        try { return (pm.getApplicationInfo(pkg, 0).flags & ApplicationInfo.FLAG_SYSTEM) != 0; } catch (PackageManager.NameNotFoundException e) { return true; }
    }

    // ------------------------------------------------------------------ long press: set up
    private static void config(final Activity a, final String id, final Runnable redraw) {
        if (id.startsWith("img")) {
            a.startActivity(new Intent(a, WallpaperActivity.class).putExtra("dir", WallpaperActivity.KINDLE).putExtra("target", "w_" + id));
        } else if ("t1".equals(id) || "t2".equals(id)) {
            final EditText in = new EditText(a);
            in.setText(Prefs.getStr(a, "w_" + id, ""));
            new Ui.Builder(a).setTitle("自定义文字").setView(in)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) { Prefs.putStr(a, "w_" + id, in.getText().toString()); redraw.run(); }
                }).setNegativeButton("取消", null).show();
        } else if (id.startsWith("st_app")) {
            final PackageManager pm = a.getPackageManager();
            Intent main = new Intent(Intent.ACTION_MAIN);
            main.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> list = pm.queryIntentActivities(main, 0);
            Collections.sort(list, new ResolveInfo.DisplayNameComparator(pm));
            final List<String> pk = new ArrayList<String>();
            List<String> lb = new ArrayList<String>();
            for (ResolveInfo ri : list) {
                if ((ri.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0 || pk.contains(ri.activityInfo.packageName)) continue;
                pk.add(ri.activityInfo.packageName);
                lb.add(String.valueOf(ri.loadLabel(pm)));
            }
            new Ui.Builder(a).setTitle("选择应用").setItems(lb.toArray(new String[0]), new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int which) { Prefs.putStr(a, "w_" + id + "_pkg", pk.get(which)); redraw.run(); }
            }).show();
        } else {
            Ui.toast(a, "此组件没有可设置项", false);
        }
    }
}