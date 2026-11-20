/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * LibraryActivity: The library pages (overview, check-in, statistics, reading, library), opened from the buttons
 * of the bottom bar (Dock). */
package com.embrith.library;

import com.embrith.home.Ctl;
import com.embrith.home.Frames;
import com.embrith.home.IconButton;
import com.embrith.home.Icons;
import com.embrith.home.MenuActivity;
import com.embrith.home.Prefs;
import com.embrith.home.Dock;
import com.embrith.home.Ui;
import com.embrith.home.Widgets;
import com.embrith.language.T;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.io.File;
import java.text.Collator;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The library pages: overview, check-in and statistics of the reading record, reading (now reading and recent), library. They are the buttons
 * of the bottom bar (Dock), which is shared with the desktop. The top bar: logo and the power button.
 * The library home is like the desktop: with no folders it shows a big "+" that opens the folder choice; with chosen folders it lists
 * them, and from there the folders below them.
 */
public class LibraryActivity extends Activity implements Dock.Pages {
    public static final String EXTRA_PAGE = "page";   // the page to show first: ov, ck, st, reading, shelf
    private int margin;
    static final int PAD = 16;   // dp: the padding of the page body; a frame gets Frames.EDGE - PAD of margin

    private LinearLayout dock;
    private String page = "";             // the page shown: ov, ck, st, shelf, reading
    private boolean recentMode;           // the board being drawn is "recent" (its books are named and described differently)
    private LogBoard logBoard;
    private boolean grid;
    private int cols = 3;                 // grid: books per row (lib_cols)
    private int rows = 0;                 // grid: rows per screen, 0 = cover 3:4 and scroll (lib_rows)
    private String listInfo = "full";     // list: what follows a book (lib_info: full = format and size, fmt, none)
    private boolean showParent;           // "recent": also the folder a book is in (lib_parent)
    private boolean askOpen;              // ask before a book opens (lib_ask)
    private boolean showName = true;      // grid: the file name under the cover (lib_name)
    private File dir;   // null = the library home
    private LinearLayout body;
    private TextView where;
    ExecutorService coverPool() { return pool; }

    private ExecutorService pool;   // makes the covers one after the other
    private ExecutorService metaPool;   // reads the title, author, series and tags of the books one after the other
    private volatile int gen;       // counts the pages shown: a background read only refreshes the page it was started for
    private String labelMode = "file";   // what a book is called (lib_label: file, title, title_author, author_title)
    private String group = "none";       // how the books of a folder are grouped (lib_group: none, author, series, format, initial, tag)
    private String groupKey;             // inside a virtual folder (one group): its name
    private LinearLayout tools;          // the floating column: list or grid, sorting, grouping, then the page buttons
    private Ui.Pager pager;              // the page buttons, kept in the column
    private static final String[] G_IDS = { "none", "author", "series", "format", "initial", "tag" };
    private static final String[] G_TEXT = { "不分组", "作者", "系列", "格式", "首字母", "标签" };
    private static final String[] S_IDS = { "name", "natural", "pinyin", "date", "size", "type", "read" };
    private static final String[] S_TEXT = { "名称", "自然数字", "拼音", "修改时间", "文件大小", "类型", "最近阅读" };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        margin = Frames.edgeDp(this);
        String first = getIntent().getStringExtra(EXTRA_PAGE);
        page = first != null ? first : Prefs.getStr(this, "lib_page", "shelf");
        logBoard = new LogBoard(this);
        grid = "grid".equals(Prefs.getStr(this, "lib_view", "list"));
        // the library opens on the page it was left on (a folder inside the chosen ones), else on its home
        String last = Prefs.getStr(this, "lib_last", "");
        File d = new File(LibStore.ROOT, last);
        if (last.length() > 0 && d.isDirectory() && LibStore.inFolders(d)) dir = d;
        pool = Executors.newSingleThreadExecutor();
        metaPool = Executors.newSingleThreadExecutor();
        FrameLayout bar = new FrameLayout(this);
        TextView logo = Ui.text(this, "Embrith · 余烬", 26, true);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.LEFT | Gravity.CENTER_VERTICAL);
        lp.leftMargin = Frames.edge(this);   // the page margin, like the frames below
        bar.addView(logo, lp);

        // right side: only the power button (sleep, the same as the power key)
        IconButton power = new IconButton(this, Icons.LOCK, "", true, 30, 11);
        power.setContentDescription("休眠");
        power.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        power.setGravity(Gravity.CENTER);
        power.setNoFade();
        power.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Ctl.request("sleep"); }
        });
        // refresh button: full screen flash to clear ghosting (left of the power button)
        IconButton refresh = new IconButton(this, Icons.REFRESH, "", true, 30, 11);
        refresh.setContentDescription("刷新");
        refresh.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        refresh.setGravity(Gravity.CENTER);
        refresh.setNoFade();
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Ctl.request("clear"); }
        });
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.HORIZONTAL);
        // clean-up button: free the background apps, the same as Settings > System > Memory management (between refresh and sleep)
        IconButton clean = new IconButton(this, Icons.BROOM, "", true, 30, 11);
        clean.setContentDescription("清理");
        clean.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        clean.setGravity(Gravity.CENTER);
        clean.setNoFade();
        clean.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { MenuActivity.freeMemory(LibraryActivity.this, null); }
        });
        right.addView(refresh, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        right.addView(clean, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        right.addView(power, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        FrameLayout.LayoutParams rp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        rp.rightMargin = Frames.edge(this) - Ui.dp(this, 12);   // the page margin minus the 12 dp padding of the icon
        bar.addView(right, rp);

        dock = Dock.build(this, page);
        body = Ui.pageOf(this, bar, null, true, dock);
        body.setPadding(Ui.dp(this, PAD), 0, Ui.dp(this, PAD), Frames.edge(this) - Frames.half(this));   // frames: the page margin from the left, right and bottom edge
        where = Ui.caption(body, "");
        buildTools();
    }

    /** One column at the bottom right: from the bottom up the page buttons (when there is something to scroll), grouping, sorting, list or grid. */
    private void buildTools() {
        tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.VERTICAL);
        View root = (View) ((View) body.getParent()).getParent();
        FrameLayout wrap = (FrameLayout) root.getParent();
        pager = Ui.pager(root);
        if (pager != null) wrap.removeView(pager);   // the page buttons move into the column: it grows and shrinks with them
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.RIGHT | Gravity.BOTTOM);
        tp.rightMargin = Ui.dp(this, 16);
        tp.bottomMargin = Ui.dp(this, 16 + Dock.HEIGHT);
        wrap.addView(tools, tp);
    }

    /** The buttons for the page shown (only the library page has them; the grouping one only inside a folder), the page buttons last. */
    private void refreshTools() {
        tools.removeAllViews();
        if (page.equals("shelf")) {
            tools.addView(Ui.floatButton(this, grid ? Icons.LIST : Icons.GRID, new View.OnClickListener() {
                @Override public void onClick(View v) {
                    Prefs.putStr(LibraryActivity.this, "lib_view", grid ? "list" : "grid");
                    show();
                }
            }, null));
            tools.addView(Ui.floatButton(this, Icons.SORT, new View.OnClickListener() {
                @Override public void onClick(View v) { cycle("lib_sort", "排序", S_IDS, S_TEXT, Prefs.getStr(LibraryActivity.this, "lib_sort", "name")); }
            }, new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) { pop(v, "lib_sort", S_IDS, S_TEXT, Prefs.getStr(LibraryActivity.this, "lib_sort", "name")); return true; }
            }), gapParams());
            if (dir != null) {
                tools.addView(Ui.floatButton(this, Icons.GROUP, new View.OnClickListener() {
                    @Override public void onClick(View v) { cycle("lib_group", "分组", G_IDS, G_TEXT, group); }
                }, new View.OnLongClickListener() {
                    @Override public boolean onLongClick(View v) { pop(v, "lib_group", G_IDS, G_TEXT, group); return true; }
                }), gapParams());
            }
        }
        if (pager != null) tools.addView(pager, tools.getChildCount() > 0 ? gapParams() : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private LinearLayout.LayoutParams gapParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 10);
        return lp;
    }

    private void choose(String key, String name, String[] ids, String[] text, int i) {
        Prefs.putStr(this, key, ids[i]);
        if (key.equals("lib_group")) groupKey = null;
        Ui.toast(this, T.t(this, name) + ": " + T.t(this, text[i]), false);
        show();
    }

    /** A click: the next value of the setting. */
    private void cycle(String key, String name, String[] ids, String[] text, String cur) {
        int i = 0;
        for (int k = 0; k < ids.length; k++) if (ids[k].equals(cur)) i = k;
        choose(key, name, ids, text, (i + 1) % ids.length);
    }

    /** A long press: a list of all the values, next to the button. */
    private void pop(View anchor, final String key, final String[] ids, final String[] text, String cur) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setStroke(Ui.dp(this, 1), Color.BLACK);
        bg.setCornerRadius(Ui.radius(this));
        box.setBackground(bg);
        final PopupWindow pw = new PopupWindow(box, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        pw.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
        pw.setAnimationStyle(0);
        for (int k = 0; k < ids.length; k++) {
            final int i = k;
            TextView t = Ui.text(this, (ids[k].equals(cur) ? "> " : "") + text[k], 17, ids[k].equals(cur));
            t.setPadding(Ui.dp(this, 18), Ui.dp(this, 11), Ui.dp(this, 18), Ui.dp(this, 11));
            t.setClickable(true);
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    pw.dismiss();
                    choose(key, key.equals("lib_group") ? "分组" : "排序", ids, text, i);
                }
            });
            box.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        box.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int[] loc = new int[2];
        anchor.getLocationOnScreen(loc);
        int x = Math.max(Ui.dp(this, 8), loc[0] - box.getMeasuredWidth() - Ui.dp(this, 8));
        int y = Math.max(Ui.dp(this, 8), loc[1] + anchor.getHeight() - box.getMeasuredHeight());
        pw.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (margin != Frames.edgeDp(this)) { recreate(); return; }   // the page margin setting changed
        Dock.refresh(this, dock);
        applyWall();
        show();
        new Thread(new Runnable() {
            @Override public void run() {
                if (!ReadTime.syncApps(LibraryActivity.this)) return;
                runOnUiThread(new Runnable() {
                    @Override public void run() { if (!isFinishing()) show(); }
                });
            }
        }).start();   // the screen time of the apps joins the reading time
    }

    /** The desktop wallpaper behind the page when wall_lib is on (the page root is the parent of the scroll view). */
    private void applyWall() {
        View root = (View) ((View) body.getParent()).getParent();
        Drawable d = Widgets.wallDrawable(this);
        root.setBackground(d != null ? d : new ColorDrawable(Color.WHITE));
    }

    @Override
    protected void onPause() {
        Dock.noSwipe();
        super.onPause();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        boolean r = super.dispatchTouchEvent(e);
        Dock.pageTouch(this, dock, e);   // a horizontal swipe switches the page
        return r;
    }

    @Override
    protected void onDestroy() {
        pool.shutdownNow();
        metaPool.shutdownNow();
        super.onDestroy();
    }

    /** A button of the bottom bar: show that page. */
    @Override
    public void goPage(String id) {
        page = id;
        if (isStats(id)) logBoard.setTab(statsIndex(id));   // also leaves a day or a book that is open
        show();
    }

    @Override
    public void onBackPressed() {
        if (isStats(page) && logBoard.back()) {
            show();   // back from a day or a book of the reading statistics
        } else if (page.equals("shelf") && dir != null && groupKey != null) {
            groupKey = null;   // back from a group goes to the folder
            show();
        } else if (page.equals("shelf") && dir != null) {
            // a chosen folder is the top of its tree: back from it goes to the library home
            dir = LibStore.folderRels().contains(LibStore.rel(dir)) ? null : dir.getParentFile();
            show();
        } else {
            finish();
        }
    }

    /** What an entry is called: on the library home the path of the chosen folder; a book by file name, title, title - author or author - title (lib_label), as far as it is read. */
    private String label(File f) {
        if (!recentMode && dir == null) return LibStore.tidy(LibStore.rel(f));
        String name = LibStore.tidy(f.getName());
        if (f.isDirectory() || labelMode.equals("file")) return name;
        Meta m = Meta.peek(f);
        if (m == null) return name;
        String t = m.title.length() > 0 ? m.title : LibStore.stem(name);
        if (m.author.length() == 0) return t;
        if (labelMode.equals("author_title")) return m.author + " - " + t;
        return labelMode.equals("title_author") ? t + " - " + m.author : t;
    }

    /** A virtual folder: the books of a folder that share an author, series, format, first letter or tag. */
    private static final class Group extends File {
        final String key;
        private final String shown;

        Group(File dir, String key, String shown) {
            super(dir, "group");
            this.key = key;
            this.shown = shown;
        }

        @Override public boolean isDirectory() { return true; }

        @Override public String getName() { return shown; }
    }

    private static final String PINYIN = "\u554a\u82ad\u64e6\u642d\u86fe\u53d1\u5676\u54c8\u51fb\u5580\u5783\u5988\u62ff\u54e6\u556a\u671f\u7136\u6492\u584c\u6316\u6614\u538b\u531d";
    private static final String PINYIN_LETTERS = "ABCDEFGHJKLMNOPQRSTWXYZ";

    /** The first letter of a name: A to Z (Chinese by pinyin) or # for the rest. */
    private static String initial(String s) {
        String t = s.trim();
        if (t.length() == 0) return "#";
        char c = t.charAt(0);
        if (c >= 'a' && c <= 'z') return String.valueOf((char) (c - 32));
        if (c >= 'A' && c <= 'Z') return String.valueOf(c);
        if (c >= 0x4e00 && c <= 0x9fff) {
            Collator col = Collator.getInstance(Locale.CHINA);
            String x = String.valueOf(c);
            for (int i = PINYIN.length() - 1; i >= 0; i--) {
                if (col.compare(x, PINYIN.substring(i, i + 1)) >= 0) return PINYIN_LETTERS.substring(i, i + 1);
            }
        }
        return "#";
    }

    /** The groups a book belongs to (a book with several tags is in several groups). */
    private List<String> keysOf(File b) {
        List<String> k = new ArrayList<String>();
        Meta m = Meta.peek(b);
        if (group.equals("author")) k.add(m != null && m.author.length() > 0 ? m.author : T.t(this, "未知作者"));
        else if (group.equals("series")) k.add(m != null && m.series.length() > 0 ? m.series : T.t(this, "无系列"));
        else if (group.equals("format")) k.add(LibStore.ext(b.getName()).toUpperCase());
        else if (group.equals("initial")) k.add(initial(label(b)));
        else {
            if (m != null) k.addAll(m.tags);
            if (k.isEmpty()) k.add(T.t(this, "无标签"));
        }
        return k;
    }

    /**
     * The folders of the list stay; the books become virtual folders, one per group (name and number of books). Inside a group (groupKey)
     * only the books of that group are listed, a series in the order of its numbers.
     */
    private List<File> groupItems(List<File> items) {
        List<File> out = new ArrayList<File>();
        Map<String, List<File>> map = new LinkedHashMap<String, List<File>>();
        for (File f : items) {
            if (f.isDirectory()) {
                if (groupKey == null) out.add(f);
                continue;
            }
            for (String k : keysOf(f)) {
                List<File> l = map.get(k);
                if (l == null) {
                    l = new ArrayList<File>();
                    map.put(k, l);
                }
                l.add(f);
            }
        }
        if (groupKey != null) {
            List<File> l = map.get(groupKey);
            if (l == null) {
                groupKey = null;   // the group is gone (the settings or the files changed)
                return groupItems(items);
            }
            if (group.equals("series")) {
                Collections.sort(l, new Comparator<File>() {
                    @Override public int compare(File a, File b) { return Float.compare(seriesNo(a), seriesNo(b)); }
                });
            }
            return l;
        }
        List<String> keys = new ArrayList<String>(map.keySet());
        final Collator col = Collator.getInstance(Locale.CHINA);
        final String[] last = { T.t(this, "未知作者"), T.t(this, "无系列"), T.t(this, "无标签"), "#" };
        Collections.sort(keys, new Comparator<String>() {
            @Override public int compare(String a, String b) {
                boolean la = java.util.Arrays.asList(last).contains(a), lb = java.util.Arrays.asList(last).contains(b);
                if (la != lb) return la ? 1 : -1;
                return col.compare(a, b);
            }
        });
        for (String k : keys) out.add(new Group(dir, k, k + " (" + map.get(k).size() + ")"));
        return out;
    }

    private static float seriesNo(File f) {
        Meta m = Meta.peek(f);
        return m == null || m.index < 0 ? Float.MAX_VALUE : m.index;
    }

    /** Starts reading what is missing for the books in the list; true when a read was started (the page is shown again when it is done). */
    private boolean loadMeta(List<File> items) {
        final List<File> miss = new ArrayList<File>();
        for (File f : items) if (!f.isDirectory() && Meta.peek(f) == null) miss.add(f);
        if (miss.isEmpty()) return false;
        final int g = gen;
        metaPool.execute(new Runnable() {
            @Override public void run() {
                for (File f : miss) {
                    if (g != gen) return;
                    Meta.get(f);
                }
                Meta.flush();
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (g == gen && !isFinishing()) show();
                    }
                });
            }
        });
        return true;
    }

    void show() {
        gen++;
        // keep the caption (child 0), drop the old boards
        while (body.getChildCount() > 1) body.removeViewAt(1);
        ((android.widget.ScrollView) body.getParent()).setFillViewport(false);   // only the "+" page fills the screen
        pagerRow(true);
        grid = "grid".equals(Prefs.getStr(this, "lib_view", "list"));
        cols = num(Prefs.getStr(this, "lib_cols", "3"), 3);
        rows = num(Prefs.getStr(this, "lib_rows", "0"), 0);
        showName = !"0".equals(Prefs.getStr(this, "lib_name", "1"));
        listInfo = Prefs.getStr(this, "lib_info", "full");
        showParent = "1".equals(Prefs.getStr(this, "lib_parent", "0"));
        askOpen = "1".equals(Prefs.getStr(this, "lib_ask", "0"));
        labelMode = Prefs.getStr(this, "lib_label", "file");
        group = Prefs.getStr(this, "lib_group", "none");
        if (!isStats(page) && !page.equals("reading")) page = "shelf";
        if (!page.equals(Prefs.getStr(this, "lib_page", ""))) Prefs.putStr(this, "lib_page", page);
        tabBar();
        if (page.equals("shelf")) {
            where.setVisibility(View.VISIBLE);
            showLib(false);
        } else if (page.equals("reading")) {
            where.setVisibility(View.GONE);   // two parts, each with a caption
            Ui.caption(body, "正在阅读");
            showNow();
            Ui.caption(body, "最近阅读");
            showRecent(true);
        } else {
            int k = statsIndex(page);
            if (logBoard.tab() != k) logBoard.setTab(k);
            String t = logBoard.title();
            if (t == null) where.setVisibility(View.GONE);
            else {
                where.setVisibility(View.VISIBLE);
                where.setText(t);
            }
            logBoard.render(body);
        }
    }

    private static boolean isStats(String id) {
        return id.equals("ov") || id.equals("ck") || id.equals("st");
    }

    /** The tab of the reading statistics a page stands for. */
    private static int statsIndex(String id) {
        return id.equals("ck") ? 1 : id.equals("st") ? 2 : 0;
    }

    /** The "now reading" board, one row: the cover on the left; on the right the title - author, a progress bar, the time read / the time left. */
    private void showNow() {
        recentMode = true;
        List<File> l = LibStore.recent();
        if (l == null || l.isEmpty()) {   // no frame around this board
            TextView none = Ui.text(this, "还没有阅读记录", 16, false);
            none.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
            body.addView(none, Frames.params(this, ViewGroup.LayoutParams.WRAP_CONTENT, PAD, false));
            return;
        }
        final File b = l.get(0);
        final int ch = Ui.dp(this, 190), cw = ch * 3 / 4;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));   // no side padding: the cover and the text line up with the edges of the frames
        row.setClickable(true);
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { open(b); }
        });
        row.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { return details(b); }
        });
        final FrameLayout box = CoverView.make(this, b, cw, ch, 20, 15);
        row.addView(box, new LinearLayout.LayoutParams(cw, ch));
        final LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_VERTICAL);
        col.setPadding(Ui.dp(this, 20), 0, 0, 0);
        final TextView title = Ui.text(this, label(b), 24, true);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(title);
        final TextView stat = Ui.text(this, "", 19, false);
        col.addView(stat);
        row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        body.addView(row, Frames.params(this, ViewGroup.LayoutParams.WRAP_CONTENT, PAD, false));
        final ThinBar bar = new ThinBar(this);
        col.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 32)));
        final int g0 = gen;
        metaPool.execute(new Runnable() {
            @Override public void run() {
                Meta m = Meta.get(b);
                Meta.flush();
                String name = b.getName();
                int dot = name.lastIndexOf('.');
                final String t = (m.title.length() > 0 ? m.title : dot > 0 ? name.substring(0, dot) : name) + (m.author.length() > 0 ? " - " + m.author : "");
                long ms = ReadTime.get(b);
                final double pct = ReadState.load(b).pct;
                // the time left is the time read so far scaled to the part of the book that is still ahead (the reader's own pace)
                final String left = pct >= 1 ? "~" + ReadTime.fmt((long) (ms * (100 - pct) / pct)) : "--";
                final String s = T.t(LibraryActivity.this, "已读 ") + ReadTime.fmt(ms) + " / " + T.t(LibraryActivity.this, "剩余 ") + left;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (isFinishing() || g0 != gen) return;
                        title.setText(t);
                        stat.setText(s);
                        bar.set(pct < 0 ? 0 : (int) Math.round(pct));
                    }
                });
            }
        });
    }

    /** The details of a folder or a book in a dialog (long press); false for a virtual group, which has none. */
    boolean details(final File f) {
        if (f instanceof Group) return false;
        new Thread(new Runnable() {
            @Override public void run() {
                StringBuilder sb = new StringBuilder();
                line(sb, "路径", LibStore.rel(f));
                if (f.isDirectory()) {
                    List<File> in = LibStore.list(LibraryActivity.this, f);
                    int dirs = 0, books = 0;
                    if (in != null) for (File x : in) { if (x.isDirectory()) dirs++; else books++; }
                    line(sb, "包含", dirs + T.t(LibraryActivity.this, " 个文件夹") + ", " + books + T.t(LibraryActivity.this, " 本书"));
                } else {
                    line(sb, "格式", LibStore.ext(f.getName()).toUpperCase());
                    line(sb, "大小", sizeOf(f));
                    Meta m = Meta.get(f);
                    Meta.flush();
                    if (m.title.length() > 0) line(sb, "书名", m.title);
                    if (m.author.length() > 0) line(sb, "作者", m.author);
                    if (m.series.length() > 0) line(sb, "系列", m.series + (m.index >= 0 ? " " + (m.index == (int) m.index ? String.valueOf((int) m.index) : String.valueOf(m.index)) : ""));
                    if (!m.tags.isEmpty()) {
                        StringBuilder tg = new StringBuilder();
                        for (String x : m.tags) tg.append(tg.length() > 0 ? ", " : "").append(x);
                        line(sb, "标签", tg.toString());
                    }
                    long ms = ReadTime.get(f);
                    if (ms > 0) line(sb, "已读", ReadTime.fmt(ms));
                    double pct = ReadState.load(f).pct;
                    if (pct >= 0) line(sb, "进度", Math.round(pct) + "%");
                }
                line(sb, "修改", new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date(f.lastModified())));
                final String msg = sb.toString().trim();
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (isFinishing()) return;
                        Ui.Builder d = new Ui.Builder(LibraryActivity.this);
                        d.setTitle(f.getName()).setMessage(msg).setNegativeButton("关闭", null);
                        if (!f.isDirectory()) {
                            d.setPositiveButton("打开", new DialogInterface.OnClickListener() {
                                @Override public void onClick(DialogInterface dlg, int w) { open(f); }
                            });
                            d.setNeutralButton("编辑属性", new DialogInterface.OnClickListener() {
                                @Override public void onClick(DialogInterface dlg, int w) { editProps(f); }
                            });
                        }
                        d.show();
                    }
                });
            }
        }).start();
        return true;
    }

    private EditText field(LinearLayout col, String label, String value, boolean number) {
        col.addView(Ui.text(this, label, 14, false));
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setTextColor(Color.BLACK);
        e.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16);
        e.setText(value);
        if (number) e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        col.addView(e, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return e;
    }

    /** Edits the properties of a book by hand: title, author, series, place in the series and tags (long press, then Edit). */
    private void editProps(final File f) {
        Meta m = Meta.peek(f);
        String t = m != null ? m.title : "", a = m != null ? m.author : "", s = m != null ? m.series : "";
        String ix = m == null || m.index < 0 ? "" : m.index == (int) m.index ? String.valueOf((int) m.index) : String.valueOf(m.index);
        StringBuilder tg = new StringBuilder();
        if (m != null) for (String x : m.tags) tg.append(tg.length() > 0 ? ", " : "").append(x);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), Ui.dp(this, 8));
        final EditText et = field(col, "书名", t, false);
        final EditText ea = field(col, "作者", a, false);
        final EditText es = field(col, "系列", s, false);
        final EditText ei = field(col, "系列序号", ix, true);
        final EditText eg = field(col, "标签 (逗号分隔)", tg.toString(), false);
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(col);
        Ui.Builder d = new Ui.Builder(this);
        d.setTitle("编辑属性").setView(sv).setNegativeButton("取消", null);
        d.setPositiveButton("保存", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dlg, int w) {
                final String v1 = et.getText().toString(), v2 = ea.getText().toString(), v3 = es.getText().toString();
                final String v4 = ei.getText().toString(), v5 = eg.getText().toString();
                new Thread(new Runnable() {
                    @Override public void run() {
                        Meta.edit(f, v1, v2, v3, v4, v5);
                        Meta.flush();
                        redraw();
                    }
                }).start();
            }
        });
        if (Meta.edited(f)) d.setNeutralButton("还原", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dlg, int w) {
                new Thread(new Runnable() {
                    @Override public void run() {
                        Meta.reset(f);
                        Meta.flush();
                        redraw();
                    }
                }).start();
            }
        });
        d.show();
    }

    private void redraw() {
        runOnUiThread(new Runnable() {
            @Override public void run() { if (!isFinishing()) show(); }
        });
    }

    private void line(StringBuilder sb, String label, String value) {
        sb.append(T.t(this, label)).append(": ").append(value).append('\n');
    }

    private String libTitle() {
        return dir == null ? "书库" : "书库 / " + LibStore.rel(dir) + (groupKey != null ? " / " + groupKey : "");
    }

    /** The "recent" board: the whole list or grid on its own; sharing the screen, a strip of covers (1 or 2 rows, as set). */
    private void showRecent(boolean multi) {
        recentMode = true;
        if (!multi) where.setText("最近");
        List<File> items = LibStore.recent();
        if (items == null || items.isEmpty()) {
            Ui.add(Frames.group(body, PAD), Ui.row(this, "还没有阅读记录", "", null));
            return;
        }
        if (multi) {
            int n = Math.min(items.size(), cols * Screens.recentRows(this));
            items = new ArrayList<File>(items.subList(0, n));
        }
        if (CoverView.needsMeta(this)) loadMeta(items);
        if (multi) showGrid(items, false);
        else if (grid) showGrid(items, true);
        else showList(items);
    }

    /** The library board: the chosen folders, then the folders and books below them. */
    private void showLib(boolean multi) {
        recentMode = false;
        if (dir != null && !LibStore.inFolders(dir)) dir = null;   // the page it was left on is no longer in a chosen folder
        Prefs.putStr(this, "lib_last", dir == null ? "" : LibStore.rel(dir));
        List<File> items;
        if (dir == null) {
            if (!multi) where.setText("书库");
            items = LibStore.folders();
            if (items.isEmpty()) {
                if (multi) {
                    Ui.add(Frames.group(body, PAD), Ui.nav(this, "添加文件夹", "", new View.OnClickListener() {
                        @Override public void onClick(View v) { startActivity(new Intent(LibraryActivity.this, FolderPickActivity.class)); }
                    }));
                } else {
                    showPlus();
                }
                return;
            }
        } else {
            if (!multi) where.setText(libTitle());
            items = LibStore.list(this, dir);
        }
        if (items == null) {
            Ui.add(Frames.group(body, PAD), Ui.row(this, "这个文件夹打不开", "", null));
            return;
        }
        boolean grouping = dir != null && !group.equals("none");
        boolean groupMeta = grouping && (group.equals("author") || group.equals("series") || group.equals("tag"));
        if ((CoverView.needsMeta(this) || groupMeta) && loadMeta(items) && groupMeta) {
            Ui.add(Frames.group(body, PAD), Ui.row(this, "正在读取书籍信息...", "", null));
            return;
        }
        if (grouping) items = groupItems(items);
        if (items.isEmpty()) {
            Ui.add(Frames.group(body, PAD), Ui.row(this, "这里没有文件夹和书籍", "", null));
            return;
        }
        if (grid) showGrid(items, !multi); else showList(items);
    }

    /**
     * The row of the page buttons, above the bottom bar (the tab pages of the reading statistics are 12 units high and fill the screen, so no
     * page buttons there; they come back for a day or a book of it), and the page that is bold in the bottom bar.
     */
    private void tabBar() {
        boolean detail = isStats(page) && logBoard.title() != null;
        pagerRow(detail || (!isStats(page) && !page.equals("reading")));   // overview, check-in, statistics and reading are fixed pages: no scroll, no page buttons
        Dock.mark(dock, detail ? null : page);   // the page shown has the bold text
        refreshTools();
    }

    /** The floating page buttons: allowed or not (the tab pages of the reading statistics fill the screen). */
    private void pagerRow(boolean on) {
        Ui.Pager pg = Ui.pager((View) ((View) body.getParent()).getParent());
        if (pg != null) pg.setAllowed(on);
        if (body.getParent() instanceof Ui.LockScroll) ((Ui.LockScroll) body.getParent()).setLocked(!on);   // a page without page buttons is a fixed page
    }

    /** No folders chosen (or none of them exists): the same big "+" as the empty desktop. */
    private void showPlus() {
        // the frame takes all the room that is left under the caption, the same frame as the app block on the desktop
        ((android.widget.ScrollView) body.getParent()).setFillViewport(true);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackground(Widgets.border(this, false));
        IconButton plus = new IconButton(this, Icons.PLUS, "添加文件夹", true, 72, 18);
        plus.setPadding(Ui.dp(this, 40), Ui.dp(this, 26), Ui.dp(this, 40), Ui.dp(this, 26));
        plus.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(LibraryActivity.this, FolderPickActivity.class)); }
        });
        box.addView(plus, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        int n = LibStore.folderRels().size();
        TextView hint = Ui.text(this, n > 0 ? "已选 " + n + " 个文件夹, 但都找不到" : "选择放书的文件夹, 这里只显示它们", 15, false);
        hint.setGravity(Gravity.CENTER);
        box.addView(hint);
        body.addView(box, Frames.params(this, 0, PAD, false));   // the same margins as every frame (the page margin from the edge)
        ((LinearLayout.LayoutParams) box.getLayoutParams()).weight = 1f;
    }

    void open(final File f) {
        if (f instanceof Group) {
            groupKey = ((Group) f).key;
            show();
        } else if (f.isDirectory()) {
            dir = f;
            groupKey = null;
            show();
        } else if (askOpen) {
            new Ui.Builder(this).setTitle("打开这本书").setMessage(f.getName())
                .setPositiveButton("打开", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { openBook(f); }
                })
                .setNegativeButton("取消", null).show();
        } else {
            openBook(f);
        }
    }

    void openBook(File f) {
        Class<?> reader = ComicFiles.isComic(f) ? ComicActivity.class : ReaderActivity.class;
        startActivity(new Intent(this, reader).putExtra(ReaderActivity.EXTRA_PATH, f.getPath()));
    }

    /** The grey text after a book in the list: the folder it is in ("recent" only, if asked), then format and size as set. */
    private String listValue(File f) {
        String info = listInfo.equals("none") ? "" : LibStore.ext(f.getName()).toUpperCase() + (listInfo.equals("fmt") ? "" : " " + sizeOf(f));
        if (recentMode && showParent && f.getParentFile() != null) return info.length() == 0 ? f.getParentFile().getName() : f.getParentFile().getName() + " | " + info;
        return info;
    }

    private static int num(String s, int def) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }

    private String sizeOf(File f) {
        long n = f.length();
        return n >= 1048576 ? n / 1048576 + " MB" : Math.max(1, n / 1024) + " KB";
    }

    private void showList(List<File> items) {
        LinearLayout g = Frames.group(body, PAD);
        for (final File f : items) {
            View.OnClickListener l = new View.OnClickListener() {
                @Override public void onClick(View v) { open(f); }
            };
            View r = f.isDirectory() ? Ui.nav(this, label(f), "", l) : Ui.row(this, label(f), listValue(f), l);
            r.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) { return details(f); }
            });
            Ui.add(g, r);
        }
    }

    /** useRows: the fixed rows per screen (lib_rows) apply; off for a strip of covers that shares the screen with other boards. */
    private void showGrid(List<File> items, boolean useRows) {
        // A cover is always 3:4. The width comes from the columns (the first and the last cover are the page margin from the edges, a cell has
        // 6 dp on each side); with a fixed number of rows the height of a row is the screen split by rows and the cover is also limited by it.
        // The space that is left over is spread evenly: columns of equal width with the cover in the middle, rows of equal height with the cover (and name) in the middle.
        int cw = (getResources().getDisplayMetrics().widthPixels - 2 * Frames.edge(this) + Ui.dp(this, 12)) / cols - Ui.dp(this, 12);
        int rowSide = Frames.edge(this) - Ui.dp(this, PAD + 6);   // body has PAD dp padding, a cell 6 dp margin: the row makes up the rest
        int cellH = 0;
        if (useRows && rows > 0) {
            // the screen minus header, caption, bottom row and padding, split by rows; a cell also has margins and the name
            cellH = (getResources().getDisplayMetrics().heightPixels - Ui.dp(this, 68 + 64 + 44 + 24)) / rows;
            int maxCh = cellH - Ui.dp(this, 16) - (showName ? Ui.dp(this, 4 + 40) : 0);
            cw = Math.min(cw, maxCh * 3 / 4);
        }
        cw = Math.max(Ui.dp(this, 40), cw);
        int ch = cw * 4 / 3;
        for (int i = 0; i < items.size(); i += cols) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.TOP);   // the covers share one top edge, with or without the names
            for (int c = 0; c < cols; c++) {
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                cp.setMargins(Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8));
                if (i + c < items.size()) row.addView(cell(items.get(i + c), cw, ch), cp);
                else row.addView(new View(this), cp);
            }
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, cellH > 0 ? cellH : ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.leftMargin = rowSide;
            rlp.rightMargin = rowSide;
            body.addView(row, rlp);
        }
    }

    /** The cover scaled to fill w x h (the middle is kept, the overflow is cut), with rounded corners cut out of the picture itself (the same radius as the frame). */
    static Bitmap fillRounded(Bitmap src, int w, int h, float radius) {
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        float k = Math.max(w / (float) src.getWidth(), h / (float) src.getHeight());
        Matrix m = new Matrix();
        m.setScale(k, k);
        m.postTranslate((w - src.getWidth() * k) / 2f, (h - src.getHeight() * k) / 2f);
        BitmapShader sh = new BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        sh.setLocalMatrix(m);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setShader(sh);
        new Canvas(out).drawRoundRect(new RectF(0, 0, w, h), radius, radius, p);
        src.recycle();
        return out;
    }

    /** A grid cell: the cover (or the kind, folder or format, in a frame) and the name under it. */
    private View cell(final File f, final int cw, final int ch) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setClickable(true);
        final FrameLayout box = CoverView.make(this, f, cw, ch, 20, 15);
        c.addView(box, new LinearLayout.LayoutParams(cw, ch));
        if (showName) {
            TextView name = Ui.text(this, label(f), 14, false);
            name.setTextColor(Color.BLACK);
            name.setGravity(Gravity.CENTER);
            name.setMaxLines(2);
            name.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            np.topMargin = Ui.dp(this, 4);
            c.addView(name, np);
        }
        c.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { open(f); }
        });
        c.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { return details(f); }
        });
        return c;
    }
}