/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * Widgets: The contents of the widgets (picture, text, screen time, reading) and the frame they are drawn in; used by SlotViews. */
package com.embrith.home;

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
 * The pieces the desktop and settings home widgets are made of. Where a widget stands, how big it is and which type it shows is decided
 * by Slots (flow layout) and SlotViews (content types); this class draws the frame (framed), a picture (pictureFrame), custom text
 * (center, editText), the screen time (stats, pickApp) and the book read last (reading). A frame is a black rounded line, transparent inside.
 */
public final class Widgets {
    private Widgets() { }

    /** The desktop wallpaper as a page background for the library and recent (null: the switch wall_lib is off, or the wallpaper is plain white). */
    public static Drawable wallDrawable(Activity a) {
        if (!"1".equals(Prefs.getStr(a, "wall_lib", "0"))) return null;
        return deskWall(a);
    }

    /** The desktop wallpaper whatever wall_lib says (null = none). Decoding it takes time: keep it while wallKey stays the same. */
    public static Drawable deskWall(Activity a) {
        String w = Img.resolve(a, "home");
        if (w.length() == 0) return null;
        DisplayMetrics dm = a.getResources().getDisplayMetrics();
        Bitmap b = Img.load(w, dm.widthPixels, dm.heightPixels, Img.fade(a, "home"));
        return b == null ? null : new BitmapDrawable(a.getResources(), b);
    }

    /** What the desktop wallpaper is now (the picture and its fade): deskWall has to be called again when it changes. */
    public static String wallKey(Activity a) {
        return Img.resolve(a, "home") + "|" + Img.fade(a, "home");
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
    static Bitmap rounded(Bitmap src, float radius) {
        Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        new Canvas(out).drawRoundRect(new RectF(0, 0, src.getWidth(), src.getHeight()), radius, radius, p);
        src.recycle();
        return out;
    }


    /** An empty widget frame: transparent, black rounded line, the content follows the rounded corners. */
    static FrameLayout framed(Activity a) {
        FrameLayout f = new FrameLayout(a);
        f.setBackground(border(a, false));
        f.setClipToOutline(true);
        f.setForeground(border(a, false));
        return f;
    }

    /** The size of the hints "long press to ..." in every widget. */
    static final int HINT_SP = 16;

    /** The hint of a widget that has no content yet: the same font and size in every widget. */
    static TextView hint(Context c, String s) {
        return center(c, s, HINT_SP, false);
    }

    /** A framed picture widget for the picture chosen as "which" (see Img.resolve), or the hint when there is none. */
    static FrameLayout pictureFrame(Activity a, String which, int w, int h) {
        FrameLayout f = framed(a);
        Bitmap b = Img.load(Img.resolve(a, which), w, h, 0);
        if (b != null) {
            ImageView iv = new ImageView(a);
            iv.setScaleType(ImageView.ScaleType.FIT_XY);
            iv.setImageBitmap(rounded(b, Ui.radius(a)));
            f.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        } else {
            f.addView(hint(a, "长按选择图片"), new FrameLayout.LayoutParams(-1, -1));
        }
        return f;
    }

    /** The dialog that edits the custom text kept in the Prefs key. */
    static void editText(final Activity a, final String key, final Runnable redraw) {
        final EditText in = new EditText(a);
        in.setText(Prefs.getStr(a, key, ""));
        new Ui.Builder(a).setTitle("自定义文字").setView(in)
            .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int which) { Prefs.putStr(a, key, in.getText().toString()); redraw.run(); }
            }).setNegativeButton("取消", null).show();
    }

    static TextView center(Context c, String s, int sp, boolean bold) {
        TextView t = Ui.text(c, s, sp, bold);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.dp(c, 10), Ui.dp(c, 6), Ui.dp(c, 10), Ui.dp(c, 6));
        return t;
    }

    // ------------------------------------------------------------------ reading
    /** The book opened last: cover, title, author and the time spent reading it. The book is read in the background. */
    static void reading(final Activity a, final FrameLayout f, int h) {
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
    static void stats(final Activity a, FrameLayout f, final String key, final boolean one, int h) {
        final boolean compact = h > 0 && h < Ui.dp(a, 100);   // a thin bar: the title and the value on one line, the buttons side by side
        final TextView title = Ui.text(a, "", 15, false);
        final TextView value = Ui.text(a, "", compact ? 22 : 28, true);
        final TextView per = Ui.text(a, "", compact ? 12 : 14, false);
        LinearLayout left = new LinearLayout(a);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setGravity(Gravity.CENTER_VERTICAL);
        left.setPadding(Ui.dp(a, 12), 0, 0, 0);
        if (compact) {
            title.setSingleLine(true);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            per.setSingleLine(true);
            LinearLayout line = new LinearLayout(a);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.BOTTOM);
            line.addView(value);
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-2, -2);
            plp.leftMargin = Ui.dp(a, 8);
            plp.bottomMargin = Ui.dp(a, 3);
            line.addView(per, plp);
            left.addView(title);
            left.addView(line);
        } else {
            left.addView(title);
            left.addView(value);
            left.addView(per);
        }
        LinearLayout right = new LinearLayout(a);
        right.setOrientation(compact ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        right.setGravity(Gravity.CENTER);
        View.OnClickListener up = new View.OnClickListener() {
            @Override public void onClick(View v) { Prefs.putStr(a, "w_" + key + "_mode", "w"); fill(a, key, one, title, value, per); }
        };
        View.OnClickListener down = new View.OnClickListener() {
            @Override public void onClick(View v) { Prefs.putStr(a, "w_" + key + "_mode", "d"); fill(a, key, one, title, value, per); }
        };
        IconButton bu = new IconButton(a, Icons.UP, "", false, 28, 12);
        IconButton bd = new IconButton(a, Icons.DOWN, "", false, 28, 12);
        bu.setOnClickListener(up);
        bd.setOnClickListener(down);
        int bs = Ui.dp(a, compact ? 44 : 56);
        right.addView(bu, new LinearLayout.LayoutParams(bs, bs));
        right.addView(bd, new LinearLayout.LayoutParams(bs, bs));
        // the screen time is read again at every full minute, only while this widget is on the screen (a page without it has no timer)
        LinearLayout row = new LinearLayout(a) {
            private final Tick tick = new Tick(new Runnable() {
                @Override public void run() { fill(a, key, one, title, value, per); }
            }, 60000);

            @Override protected void onWindowVisibilityChanged(int visibility) {
                super.onWindowVisibilityChanged(visibility);
                if (visibility == VISIBLE) tick.start(); else tick.stop();
            }

            @Override protected void onDetachedFromWindow() {
                tick.stop();
                super.onDetachedFromWindow();
            }
        };
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(left, new LinearLayout.LayoutParams(0, -1, 1f));
        row.addView(right, new LinearLayout.LayoutParams(-2, -1));
        f.addView(row, new FrameLayout.LayoutParams(-1, -1));
        fill(a, key, one, title, value, per);
    }

    private static void fill(Context c, String key, boolean one, TextView title, TextView value, TextView per) {
        boolean week = "w".equals(Prefs.getStr(c, "w_" + key + "_mode", "d"));
        Calendar cal = Calendar.getInstance();
        long now = cal.getTimeInMillis();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (week) cal.add(Calendar.DAY_OF_YEAR, -6);
        String pkg = Prefs.getStr(c, "w_" + key + "_pkg", "");
        PackageManager pm = c.getPackageManager();
        String name = "屏幕时间";
        if (one) {
            name = "长按选择应用";
            try { if (pkg.length() > 0) name = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0))); } catch (PackageManager.NameNotFoundException e) { pkg = ""; }
        }
        title.setText(name);
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, one && pkg.length() == 0 ? HINT_SP : 15);   // the hint has the size of every hint
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

    // ------------------------------------------------------------------ set up
    /** The dialog that chooses the app of a screen time widget (the Prefs "w_<key>_pkg"). */
    static void pickApp(final Activity a, final String key, final Runnable redraw) {
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
            @Override public void onClick(DialogInterface d, int which) { Prefs.putStr(a, "w_" + key + "_pkg", pk.get(which)); redraw.run(); }
        }).show();
    }
}
