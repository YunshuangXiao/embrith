/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * ReaderActivity: The text reader: tap zones for previous page, next page and the menus at the top and bottom. */
package com.embrith.library;

import com.embrith.home.Ctl;
import com.embrith.home.IconButton;
import com.embrith.home.Icons;
import com.embrith.home.Prefs;
import com.embrith.home.Ui;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The reader. A tap in the left third of the page = previous page, in the right third = next page, in the middle = the menus: at the top
 * (back, contents, bookmark, bookmarks) and at the bottom (font, size, line spacing, margin, first line indent, jump). The place and the
 * bookmarks are kept per book (see ReadState). The items on the top and bottom line of the page are chosen in the settings (keys rd_top_l,
 * rd_top_r, rd_bot_l, rd_bot_r; values none, title, author, chapter, time, percent, page, battery).
 */
public class ReaderActivity extends Activity {
    static final String EXTRA_PATH = "path";
    private static final float[] LINES = { 1.2f, 1.4f, 1.5f, 1.7f, 2.0f };
    private static final int[] MARGINS = { 10, 18, 26, 36, 48 };   // dp
    private static final String[] M_KEYS = { "rd_margin_t", "rd_margin_b", "rd_margin_l", "rd_margin_r" };   // top, bottom, left, right
    private static final String[] M_NAMES = { "上边距", "下边距", "左边距", "右边距" };
    private static final int MAX_INDENT = 8;   // in half-width characters
    private static final String FONT_DIR = "/dev/kindle/fonts";   // = /mnt/us/fonts on the Kindle

    private static final int P_NONE = 0, P_SIZE = 1, P_LINE = 2, P_MARGIN = 3, P_INDENT = 4;

    private File book;
    private BookSource src;
    private ReadState st;
    private PageView pv;
    private View topBar, botBar;
    private Ui.FlatButton markBtn;
    private LinearLayout panel;
    private TextView panelName, panelValue;
    private int panelMode = P_NONE;
    private int chunkIdx;
    private long[] prefix;   // prefix[i] = the sizes of chunks 0..i-1
    private int turns;
    private int sizeSp, lineIdx, indent;
    private final int[] marginIdx = new int[4];   // top, bottom, left, right
    private LinearLayout marginBox;
    private final TextView[] marginVal = new TextView[4];
    private String fontName;
    private Typeface face;
    private final String[] slot = new String[4];   // top left, top right, bottom left, bottom right

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        book = new File(getIntent().getStringExtra(EXTRA_PATH));
        sizeSp = parse(Prefs.getStr(this, "rd_size", "20"), 20, 12, 48);
        lineIdx = parse(Prefs.getStr(this, "rd_line", "2"), 2, 0, LINES.length - 1);
        int oldMargin = parse(Prefs.getStr(this, "rd_margin", "2"), 2, 0, MARGINS.length - 1);   // the single margin of older versions
        for (int i = 0; i < 4; i++) {
            marginIdx[i] = parse(Prefs.getStr(this, M_KEYS[i], String.valueOf(oldMargin)), oldMargin, 0, MARGINS.length - 1);
        }
        int oldIndent = parse(Prefs.getStr(this, "rd_indent", "0"), 0, 0, 4) * 2;   // older versions counted Chinese characters
        indent = parse(Prefs.getStr(this, "rd_indent_c", String.valueOf(oldIndent)), oldIndent, 0, MAX_INDENT);
        fontName = Prefs.getStr(this, "rd_font", "");
        face = loadFont(fontName);
        slot[0] = Prefs.getStr(this, "rd_top_l", "title");
        slot[1] = Prefs.getStr(this, "rd_top_r", "time");
        slot[2] = Prefs.getStr(this, "rd_bot_l", "percent");
        slot[3] = Prefs.getStr(this, "rd_bot_r", "page");

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);
        pv = new PageView(this);
        setPvStyle();
        pv.setListener(new PageView.Listener() {
            @Override public void left() { if (!hideMenu()) turn(false); }
            @Override public void right() { if (!hideMenu()) turn(true); }
            @Override public void center() {
                if (hideMenu()) return;
                showMenu();
                Ctl.put("turn", System.currentTimeMillis() + " c");
            }
        });
        root.addView(pv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        topBar = buildTop();
        topBar.setVisibility(View.GONE);
        root.addView(topBar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));
        botBar = buildBottom();
        botBar.setVisibility(View.GONE);
        root.addView(botBar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        setContentView(root);

        LibStore.touchRecent(book);
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    final BookSource s = open(book);
                    runOnUiThread(new Runnable() {
                        @Override public void run() { opened(s); }
                    });
                } catch (final Throwable t) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() { failed(t); }
                    });
                }
            }
        }).start();
    }

    private static int parse(String s, int def, int min, int max) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(s)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private float px(int sp) {
        return sp * getResources().getDisplayMetrics().scaledDensity;
    }

    private static Typeface loadFont(String name) {
        if (name == null || name.length() == 0) return null;
        try {
            return Typeface.createFromFile(new File(FONT_DIR, name));
        } catch (RuntimeException e) {
            return null;   // the file is gone or damaged: the default font
        }
    }

    private BookSource open(File f) throws Exception {
        String ext = LibStore.ext(f.getName());
        if (ext.equals("txt")) return new TxtSource(f, Prefs.getStr(this, "txt_regex", ""));
        if (ext.equals("epub")) return new EpubSource(f);
        if (ext.equals("kfx") || (ext.equals("azw") && KfxSource.sniff(f))) return new KfxSource(f);
        if (ext.equals("mobi") || ext.equals("azw") || ext.equals("azw3")) return new MobiSource(f);
        throw new java.io.IOException("这个格式的阅读还没有实现: " + ext);
    }

    private void failed(Throwable t) {
        String m = t.getMessage();
        Ui.toast(this, m == null || m.length() == 0 ? "打不开这本书" : m, true);
        finish();
    }

    private void opened(BookSource s) {
        src = s;
        int n = s.count();
        if (n == 0) {
            Ui.toast(this, "这本书是空的", false);
            finish();
            return;
        }
        prefix = new long[n + 1];
        for (int i = 0; i < n; i++) prefix[i + 1] = prefix[i] + Math.max(1, s.weight(i));
        st = ReadState.load(book);
        load(Math.max(0, Math.min(st.chunk, n - 1)), st.off, 0);
    }

    /** Show chunk c with character off on the page (off &lt; 0: frac of the way into the chunk, PageView.LAST: the last page). */
    private void load(int c, int off, double frac) {
        chunkIdx = c;
        try {
            CharSequence t = src.chunk(c);
            pv.setContent(t, off >= 0 || off == PageView.LAST ? off : (int) (frac * t.length()));
        } catch (java.io.IOException e) {
            failed(e);
            return;
        }
        bars();
    }

    private double percent() {
        int len = Math.max(1, pv.textLength());
        double f = Math.min(1.0, (double) pv.pageStart() / len);
        return 100.0 * (prefix[chunkIdx] + Math.max(1, src.weight(chunkIdx)) * f) / prefix[prefix.length - 1];
    }

    // ------------------------------------------------------------------ the items around the page

    private void bars() {
        pv.setBars(item(slot[0]), item(slot[1]), item(slot[2]), item(slot[3]));
    }

    private String item(String id) {
        if (id == null) return "";
        switch (id) {
            case "title": {
                String t = src.bookTitle();
                if (t != null && t.length() > 0) return t;
                String n = book.getName();
                int dot = n.lastIndexOf('.');
                return dot > 0 ? n.substring(0, dot) : n;
            }
            case "author": {
                String a = src.author();
                return a == null ? "" : a;
            }
            case "chapter": {
                String c = src.chapterTitle(chunkIdx);
                return c == null ? "" : c;
            }
            case "time":
                return new SimpleDateFormat("1".equals(Prefs.getStr(this, "clock24", "1")) ? "HH:mm" : "h:mm", Locale.US).format(new Date());
            case "percent":
                return String.format(Locale.US, "%.1f%%", percent());
            case "page":
                return (pv.page() + 1) + " / " + pv.pageCount();
            case "battery": {
                Intent b = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                int level = b == null ? -1 : b.getIntExtra("level", -1), scale = b == null ? 100 : b.getIntExtra("scale", 100);
                return level < 0 || scale <= 0 ? "" : (100 * level / scale) + "%";
            }
            default:
                return "";
        }
    }

    // ------------------------------------------------------------------ turning pages

    private void showMenu() {
        markBtn.setText(markHere() != null ? "取消书签" : "添加书签");
        topBar.setVisibility(View.VISIBLE);
        botBar.setVisibility(View.VISIBLE);
    }

    private boolean hideMenu() {
        if (topBar.getVisibility() != View.VISIBLE) return false;
        topBar.setVisibility(View.GONE);
        botBar.setVisibility(View.GONE);
        return true;
    }

    private void turn(boolean next) {
        if (src == null) return;
        boolean ok = next ? pv.nextPage() : pv.prevPage();
        if (!ok) {
            int c = chunkIdx + (next ? 1 : -1);
            if (c < 0 || c >= src.count()) {
                Ui.toast(this, next ? "已经是最后一页" : "已经是第一页", false);
                return;
            }
            load(c, next ? 0 : PageView.LAST, 0);
        }
        Ctl.put("turn", System.currentTimeMillis() + (next ? " N" : " P"));
        bars();
        if (++turns % 10 == 0) saveState();
    }

    private void saveState() {
        if (src == null || st == null) return;
        st.chunk = chunkIdx;
        st.off = pv.pageStart();
        st.pct = Math.max(0, Math.min(100, percent()));
        st.save();
    }

    private long shownAt;    // when the book came to the front, for the time spent reading it

    @Override
    protected void onResume() {
        super.onResume();
        shownAt = android.os.SystemClock.elapsedRealtime();
    }

    @Override
    protected void onPause() {
        saveState();
        if (shownAt > 0) ReadTime.add(book, android.os.SystemClock.elapsedRealtime() - shownAt);
        shownAt = 0;
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (src != null) src.close();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (!hideMenu()) finish();
    }

    // ------------------------------------------------------------------ menus

    private Ui.FlatButton btn(String label, View.OnClickListener l) {
        Ui.FlatButton b = Ui.button(this, label);
        b.setOnClickListener(l);
        return b;
    }

    /** A square icon button (minus, plus) added to a row. */
    private void addIcon(LinearLayout row, int icon, View.OnClickListener l) {
        IconButton b = new IconButton(this, icon, "", false, 24, 12);
        b.setOnClickListener(l);
        row.addView(b, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
    }

    private View buildTop() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(Color.WHITE);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams w = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        row.addView(btn("返回", new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        }), w);
        row.addView(btn("目录", new View.OnClickListener() {
            @Override public void onClick(View v) { tocDialog(); }
        }), w);
        row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        markBtn = btn("添加书签", new View.OnClickListener() {
            @Override public void onClick(View v) {
                toggleMark();
                markBtn.setText(markHere() != null ? "取消书签" : "添加书签");
            }
        });
        row.addView(markBtn, w);
        row.addView(btn("书签", new View.OnClickListener() {
            @Override public void onClick(View v) { markDialog(); }
        }), w);

        box.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        box.addView(Ui.line(this));
        return box;
    }

    private View buildBottom() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(Color.WHITE);
        box.addView(Ui.line(this));

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        panel.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        panelName = new TextView(this);
        panelName.setTextColor(Color.BLACK);
        panelName.setTextSize(16);
        panel.addView(panelName, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        addIcon(panel, Icons.MINUS, new View.OnClickListener() {
            @Override public void onClick(View v) { step(-1); }
        });
        panelValue = new TextView(this);
        panelValue.setTextColor(Color.BLACK);
        panelValue.setTextSize(16);
        panelValue.setGravity(Gravity.CENTER);
        panel.addView(panelValue, new LinearLayout.LayoutParams(Ui.dp(this, 80), ViewGroup.LayoutParams.WRAP_CONTENT));
        addIcon(panel, Icons.PLUS, new View.OnClickListener() {
            @Override public void onClick(View v) { step(1); }
        });
        panel.setVisibility(View.GONE);
        box.addView(panel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        marginBox = new LinearLayout(this);
        marginBox.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            LinearLayout mr = new LinearLayout(this);
            mr.setOrientation(LinearLayout.HORIZONTAL);
            mr.setGravity(Gravity.CENTER_VERTICAL);
            mr.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
            TextView name = new TextView(this);
            name.setTextColor(Color.BLACK);
            name.setTextSize(16);
            name.setText(M_NAMES[i]);
            mr.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            addIcon(mr, Icons.MINUS, new View.OnClickListener() {
                @Override public void onClick(View v) { stepMargin(idx, -1); }
            });
            marginVal[i] = new TextView(this);
            marginVal[i].setTextColor(Color.BLACK);
            marginVal[i].setTextSize(16);
            marginVal[i].setGravity(Gravity.CENTER);
            mr.addView(marginVal[i], new LinearLayout.LayoutParams(Ui.dp(this, 80), ViewGroup.LayoutParams.WRAP_CONTENT));
            addIcon(mr, Icons.PLUS, new View.OnClickListener() {
                @Override public void onClick(View v) { stepMargin(idx, 1); }
            });
            marginBox.addView(mr, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        marginBox.setVisibility(View.GONE);
        box.addView(marginBox, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams w = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(btn("字体", new View.OnClickListener() {
            @Override public void onClick(View v) { fontDialog(); }
        }), w);
        row.addView(btn("字号", new View.OnClickListener() {
            @Override public void onClick(View v) { openPanel(P_SIZE); }
        }), w);
        row.addView(btn("行距", new View.OnClickListener() {
            @Override public void onClick(View v) { openPanel(P_LINE); }
        }), w);
        row.addView(btn("页边距", new View.OnClickListener() {
            @Override public void onClick(View v) { openPanel(P_MARGIN); }
        }), w);
        row.addView(btn("首行缩进", new View.OnClickListener() {
            @Override public void onClick(View v) { openPanel(P_INDENT); }
        }), w);
        row.addView(btn("进度", new View.OnClickListener() {
            @Override public void onClick(View v) { jumpDialog(); }
        }), w);
        box.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return box;
    }

    /** The adjust line above the buttons: the same button again closes it. */
    private void openPanel(int mode) {
        if (panelMode == mode) {
            panelMode = P_NONE;
            panel.setVisibility(View.GONE);
            marginBox.setVisibility(View.GONE);
            return;
        }
        panelMode = mode;
        panel.setVisibility(mode == P_MARGIN ? View.GONE : View.VISIBLE);
        marginBox.setVisibility(mode == P_MARGIN ? View.VISIBLE : View.GONE);
        refreshPanel();
    }

    private void refreshPanel() {
        switch (panelMode) {
            case P_SIZE:
                panelName.setText("字号");
                panelValue.setText(String.valueOf(sizeSp));
                break;
            case P_LINE:
                panelName.setText("行距");
                panelValue.setText(String.valueOf(LINES[lineIdx]));
                break;
            case P_MARGIN:
                for (int i = 0; i < 4; i++) marginVal[i].setText((marginIdx[i] + 1) + " / " + MARGINS.length);
                break;
            case P_INDENT:
                panelName.setText("首行缩进");
                panelValue.setText(indent == 0 ? "无" : indent + " 字符");
                break;
            default:
                break;
        }
    }

    private void step(int d) {
        switch (panelMode) {
            case P_SIZE:
                sizeSp = Math.max(12, Math.min(48, sizeSp + 2 * d));
                Prefs.putStr(this, "rd_size", String.valueOf(sizeSp));
                break;
            case P_LINE:
                lineIdx = Math.max(0, Math.min(LINES.length - 1, lineIdx + d));
                Prefs.putStr(this, "rd_line", String.valueOf(lineIdx));
                break;
            case P_INDENT:
                indent = Math.max(0, Math.min(MAX_INDENT, indent + d));
                Prefs.putStr(this, "rd_indent_c", String.valueOf(indent));
                break;
            default:
                return;
        }
        refreshPanel();
        applyStyle();
    }

    private int marginPx(int i) { return Ui.dp(this, MARGINS[marginIdx[i]]); }

    private void setPvStyle() {
        pv.setStyle(px(sizeSp), LINES[lineIdx], face, marginPx(0), marginPx(1), marginPx(2), marginPx(3), indent);
    }

    private void stepMargin(int i, int d) {
        marginIdx[i] = Math.max(0, Math.min(MARGINS.length - 1, marginIdx[i] + d));
        Prefs.putStr(this, M_KEYS[i], String.valueOf(marginIdx[i]));
        refreshPanel();
        applyStyle();
    }

    private void applyStyle() {
        setPvStyle();
        if (src != null) bars();
    }

    private void fontDialog() {
        final List<String> ids = new ArrayList<String>();
        ids.add("");
        File[] fs = new File(FONT_DIR).listFiles();
        if (fs != null) {
            Arrays.sort(fs);
            for (File f : fs) {
                String n = f.getName().toLowerCase(Locale.US);
                if (f.isFile() && (!n.startsWith(".") || Prefs.showHidden(this)) && (n.endsWith(".ttf") || n.endsWith(".otf"))) ids.add(f.getName());
            }
        }
        CharSequence[] items = new CharSequence[ids.size()];
        for (int i = 0; i < items.length; i++) {
            String id = ids.get(i);
            items[i] = (id.equals(fontName) ? "> " : "") + (id.length() == 0 ? "系统默认" : id);
        }
        new Ui.Builder(this).setTitle("字体").setItems(items, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                fontName = ids.get(which);
                Prefs.putStr(ReaderActivity.this, "rd_font", fontName);
                face = loadFont(fontName);
                applyStyle();
            }
        }).show();
    }

    private void tocDialog() {
        if (src == null) return;
        int n = src.count();
        CharSequence[] items = new CharSequence[n];
        for (int i = 0; i < n; i++) {
            String t = src.chapterTitle(i);
            items[i] = (i == chunkIdx ? "> " : "") + (t == null || t.length() == 0 ? "第 " + (i + 1) + " 段" : t);
        }
        android.app.AlertDialog dlg = new Ui.Builder(this).setTitle("目录").setItems(items, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                load(which, 0, 0);
                Ctl.put("turn", System.currentTimeMillis() + " N");
                hideMenu();
            }
        }).create();
        dlg.show();
        // smaller than the system default: 80% of the width; a long list is cut at 55% of the height and scrolls
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        android.view.WindowManager.LayoutParams lp = dlg.getWindow().getAttributes();
        lp.width = dm.widthPixels * 8 / 10;
        if (n > 8) lp.height = dm.heightPixels * 55 / 100;
        dlg.getWindow().setAttributes(lp);
    }

    private void jumpDialog() {
        if (src == null) return;
        final CharSequence[] items = new CharSequence[11];
        for (int i = 0; i <= 10; i++) items[i] = (i * 10) + "%";
        new Ui.Builder(this).setTitle("跳到").setItems(items, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                int pct = which * 10;
                long target = prefix[prefix.length - 1] * pct / 100;
                int c = 0;
                while (c + 1 < src.count() && prefix[c + 1] <= target) c++;
                double frac = pct >= 100 ? 0.999 : (double) (target - prefix[c]) / Math.max(1, src.weight(c));
                load(c, -1, frac);
                Ctl.put("turn", System.currentTimeMillis() + " N");
            }
        }).show();
    }

    private ReadState.Mark markHere() {
        if (st == null) return null;
        int a = pv.pageStart(), b = pv.pageEnd();
        for (ReadState.Mark m : st.marks) if (m.chunk == chunkIdx && m.off >= a && m.off < b) return m;
        return null;
    }

    private void toggleMark() {
        if (src == null || st == null) return;
        ReadState.Mark m = markHere();
        if (m != null) {
            st.marks.remove(m);
            Ui.toast(this, "已取消这页的书签", false);
        } else {
            st.marks.add(new ReadState.Mark(chunkIdx, pv.pageStart(), pv.snippet(24)));
            Ui.toast(this, "已加书签", false);
        }
        st.save();
    }

    private void markDialog() {
        if (src == null || st == null) return;
        if (st.marks.isEmpty()) {
            Ui.toast(this, "还没有书签", false);
            return;
        }
        final ReadState.Mark[] ms = st.marks.toArray(new ReadState.Mark[0]);
        CharSequence[] items = new CharSequence[ms.length];
        for (int i = 0; i < ms.length; i++) {
            double pct = 100.0 * prefix[ms[i].chunk] / prefix[prefix.length - 1];   // where its section starts
            items[i] = String.format(Locale.US, "%.0f%%  ", pct) + (ms[i].text.length() == 0 ? "(空白)" : ms[i].text);
        }
        new Ui.Builder(this).setTitle("书签").setItems(items, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                load(ms[which].chunk, ms[which].off, 0);
                Ctl.put("turn", System.currentTimeMillis() + " N");
                hideMenu();
            }
        }).show();
    }
}
