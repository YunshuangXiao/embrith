/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * ComicActivity: The comic reader (cbz, cbt and others): one image is one page, tap zones turn the page or open
 * the menu. */
package com.embrith.library;

import com.embrith.home.Ctl;
import com.embrith.home.Prefs;
import com.embrith.home.Ui;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.text.InputType;
import android.util.LruCache;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The comic reader (cbz, cbt; see ComicFiles). One image is one page. A tap in the left third / right third turns the page (the other
 * way round when reading right to left), in the middle opens the menus. Settings (kept for all comics): the direction (cm_rtl), "whole
 * page" or "page width" (cm_fit: page or width; a page that is longer than the screen is read in steps from top to bottom) and
 * "split spreads" (cm_split: a page that is wider than it is high is shown as two halves, the right half first when reading right to
 * left). The place and the bookmarks are kept per book (see ReadState: chunk = page, off = the step in the page).
 */
public class ComicActivity extends Activity {
    private static final int LAST = -1;

    private File book;
    private ComicSource src;
    private ReadState st;
    private ComicView cv;
    private View topBar, botBar;
    private Ui.FlatButton markBtn, dirBtn, fitBtn, splitBtn;
    private TextView info;
    private int page, turns, req;
    private volatile String err = "";
    private boolean rtl, fitWidth, split, forward = true;
    private final LruCache<Integer, Bitmap> cache = new LruCache<Integer, Bitmap>(3);
    private final Set<Integer> loading = new HashSet<Integer>();   // only used on the ui thread

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        book = new File(getIntent().getStringExtra(ReaderActivity.EXTRA_PATH));
        rtl = "1".equals(Prefs.getStr(this, "cm_rtl", "0"));
        fitWidth = "width".equals(Prefs.getStr(this, "cm_fit", "page"));
        split = "1".equals(Prefs.getStr(this, "cm_split", "0"));

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);
        cv = new ComicView(this);
        cv.setListener(new ComicView.Listener() {
            @Override public void left() { if (!hideMenu()) turn(rtl); }
            @Override public void right() { if (!hideMenu()) turn(!rtl); }
            @Override public void center() {
                if (hideMenu()) return;
                showMenu();
                Ctl.put("turn", System.currentTimeMillis() + " c");
            }
        });
        root.addView(cv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
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
                    final ComicSource s = ComicFiles.open(book);
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

    private void failed(Throwable t) {
        String m = t.getMessage();
        Ui.toast(this, m == null || m.length() == 0 ? "打不开这本漫画" : m, true);
        finish();
    }

    private void opened(ComicSource s) {
        src = s;
        if (s.count() == 0) {
            Ui.toast(this, "里面没有图片", false);
            finish();
            return;
        }
        st = ReadState.load(book);
        show(Math.max(0, Math.min(st.chunk, s.count() - 1)), st.off);
    }

    // ------------------------------------------------------------------ pages

    /** The image of page p, scaled down (never up) by a power of two so that it is still as big as the screen needs. null: err says why. */
    private Bitmap decode(int p) {
        err = "";
        byte[] d;
        try {
            d = src.read(p);
        } catch (Throwable t) {
            err = "读取失败: " + t;
            return null;
        }
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(d, 0, d.length, o);
        if (o.outWidth <= 0 || o.outHeight <= 0) {
            err = "不是能解码的图片 (" + o.outMimeType + ", " + d.length + " 字节)";
            return null;
        }
        int vw = getResources().getDisplayMetrics().widthPixels, vh = getResources().getDisplayMetrics().heightPixels;
        boolean wide = split && o.outWidth > o.outHeight * 1.1;
        int tw = wide ? 2 * vw : vw;
        int s = 1;
        while (o.outWidth / (s * 2) >= tw && (fitWidth || o.outHeight / (s * 2) >= vh)) s *= 2;
        for (int tries = 0; tries < 4; tries++, s *= 2) {   // too little memory: again at half the size
            try {
                BitmapFactory.Options f = new BitmapFactory.Options();
                f.inSampleSize = s;
                f.inPreferredConfig = Bitmap.Config.RGB_565;
                Bitmap b = BitmapFactory.decodeByteArray(d, 0, d.length, f);
                if (b != null) return b;
                err = "解码失败 (" + o.outMimeType + " " + o.outWidth + "x" + o.outHeight + ")";
            } catch (OutOfMemoryError e) {
                err = "内存不够 (" + o.outWidth + "x" + o.outHeight + ")";
            } catch (RuntimeException e) {
                err = "解码出错: " + e;
            }
        }
        return null;
    }

    /** Show page p, at step unit (LAST: the last step). */
    private void show(final int p, final int unit) {
        page = p;
        final int token = ++req;
        Bitmap b = cache.get(p);
        if (b != null) {
            apply(b, unit);
            return;
        }
        cv.setPage(null, false, rtl, fitWidth);
        cv.setMessage("正在打开...");
        new Thread(new Runnable() {
            @Override public void run() {
                final Bitmap d = decode(p);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (d != null) cache.put(p, d);
                        if (token == req) apply(d, unit);
                    }
                });
            }
        }).start();
    }

    private void apply(Bitmap b, int unit) {
        if (b == null) cv.setMessage("这一页打不开: " + err);
        cv.setPage(b, split && b != null && b.getWidth() > b.getHeight() * 1.1, rtl, fitWidth);
        cv.setUnit(unit == LAST ? cv.units() - 1 : unit);
        bars();
        prefetch();
    }

    /** The next page (the previous one when turning back) is made while the reader looks at this one. */
    private void prefetch() {
        final int q = page + (forward ? 1 : -1);
        if (q < 0 || q >= src.count() || cache.get(q) != null || !loading.add(q)) return;
        new Thread(new Runnable() {
            @Override public void run() {
                final Bitmap d = decode(q);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        loading.remove(q);
                        if (d != null) cache.put(q, d);
                    }
                });
            }
        }).start();
    }

    private void turn(boolean next) {
        if (src == null) return;
        forward = next;
        if (!cv.step(next)) {
            int p = page + (next ? 1 : -1);
            if (p < 0 || p >= src.count()) {
                Ui.toast(this, next ? "已经是最后一页" : "已经是第一页", false);
                return;
            }
            show(p, next ? 0 : LAST);
        } else {
            bars();
        }
        Ctl.put("turn", System.currentTimeMillis() + (next ? " N" : " P"));
        if (++turns % 10 == 0) saveState();
    }

    private void bars() {
        if (src == null) return;
        String s = "第 " + (page + 1) + " / " + src.count() + " 页";
        if (cv.units() > 1) s += "  (" + (cv.unit() + 1) + " / " + cv.units() + ")";
        info.setText(s);
    }

    private void saveState() {
        if (src == null || st == null) return;
        st.chunk = page;
        st.off = cv.unit();
        st.pct = src.count() > 0 ? 100.0 * (page + 1) / src.count() : -1;
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
        req++;
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

    private void showMenu() {
        markBtn.setText(markHere() != null ? "取消书签" : "添加书签");
        bars();
        topBar.setVisibility(View.VISIBLE);
        botBar.setVisibility(View.VISIBLE);
    }

    private boolean hideMenu() {
        if (topBar.getVisibility() != View.VISIBLE) return false;
        topBar.setVisibility(View.GONE);
        botBar.setVisibility(View.GONE);
        return true;
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
        info = new TextView(this);
        info.setTextColor(Color.BLACK);
        info.setTextSize(16);
        info.setGravity(Gravity.CENTER);
        info.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 4));
        box.addView(info, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams w = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        dirBtn = btn("", new View.OnClickListener() {
            @Override public void onClick(View v) {
                rtl = !rtl;
                Prefs.putStr(ComicActivity.this, "cm_rtl", rtl ? "1" : "0");
                labels();
                reload();
            }
        });
        fitBtn = btn("", new View.OnClickListener() {
            @Override public void onClick(View v) {
                fitWidth = !fitWidth;
                Prefs.putStr(ComicActivity.this, "cm_fit", fitWidth ? "width" : "page");
                cache.evictAll();
                labels();
                reload();
            }
        });
        splitBtn = btn("", new View.OnClickListener() {
            @Override public void onClick(View v) {
                split = !split;
                Prefs.putStr(ComicActivity.this, "cm_split", split ? "1" : "0");
                cache.evictAll();
                labels();
                reload();
            }
        });
        labels();
        row.addView(dirBtn, w);
        row.addView(fitBtn, w);
        row.addView(splitBtn, w);
        row.addView(btn("跳页", new View.OnClickListener() {
            @Override public void onClick(View v) { jumpDialog(); }
        }), w);
        row.addView(btn("目录", new View.OnClickListener() {
            @Override public void onClick(View v) { tocDialog(); }
        }), w);
        box.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return box;
    }

    private void labels() {
        dirBtn.setText(rtl ? "方向: 右到左" : "方向: 左到右");
        fitBtn.setText(fitWidth ? "适应: 适宽" : "适应: 整页");
        splitBtn.setText(split ? "拆分跨页: 开" : "拆分跨页: 关");
    }

    private void reload() {
        if (src != null) show(page, 0);
    }

    private void jumpDialog() {
        if (src == null) return;
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setFilters(new android.text.InputFilter[] { new android.text.InputFilter.LengthFilter(6) });
        input.setText(String.valueOf(page + 1));
        input.setSelectAllOnFocus(true);
        new Ui.Builder(this)
            .setTitle("跳页")
            .setMessage("输入 1 到 " + src.count() + " 的页码")
            .setView(input)
            .setPositiveButton("好", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int which) {
                    int n;
                    try { n = Integer.parseInt(input.getText().toString().trim()); } catch (NumberFormatException e) { n = -1; }
                    if (n < 1 || n > src.count()) { Ui.toast(ComicActivity.this, "页码不对", true); return; }
                    show(n - 1, 0);
                    Ctl.put("turn", System.currentTimeMillis() + " N");
                    hideMenu();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    /** The table of contents of a mobi comic: read in a worker thread (the book text is unpacked), then shown as a list. */
    private void tocDialog() {
        if (src == null) return;
        if (!(src instanceof ComicToc)) {
            Ui.toast(this, "这本书没有目录", false);
            return;
        }
        final ComicToc t = (ComicToc) src;
        new Thread(new Runnable() {
            @Override public void run() {
                List<Object[]> r;
                try {
                    r = t.toc();
                } catch (Throwable e) {
                    r = new ArrayList<Object[]>();
                }
                final List<Object[]> list = r;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (isFinishing() || src == null) return;
                        if (list.isEmpty()) {
                            Ui.toast(ComicActivity.this, "这本书没有目录", false);
                            return;
                        }
                        CharSequence[] items = new CharSequence[list.size()];
                        for (int i = 0; i < items.length; i++) items[i] = list.get(i)[0] + "  (" + (((Integer) list.get(i)[1]) + 1) + ")";
                        new Ui.Builder(ComicActivity.this).setTitle("目录").setItems(items, new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int which) {
                                show((Integer) list.get(which)[1], 0);
                                Ctl.put("turn", System.currentTimeMillis() + " N");
                                hideMenu();
                            }
                        }).show();
                    }
                });
            }
        }).start();
    }

    private ReadState.Mark markHere() {
        if (st == null) return null;
        for (ReadState.Mark m : st.marks) if (m.chunk == page) return m;
        return null;
    }

    private void toggleMark() {
        if (src == null || st == null) return;
        ReadState.Mark m = markHere();
        if (m != null) {
            st.marks.remove(m);
            Ui.toast(this, "已取消这页的书签", false);
        } else {
            st.marks.add(new ReadState.Mark(page, cv.unit(), "第 " + (page + 1) + " 页"));
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
        for (int i = 0; i < ms.length; i++) items[i] = ms[i].text;
        new Ui.Builder(this).setTitle("书签").setItems(items, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                show(Math.max(0, Math.min(ms[which].chunk, src.count() - 1)), ms[which].off);
                Ctl.put("turn", System.currentTimeMillis() + " N");
                hideMenu();
            }
        }).show();
    }

    // ------------------------------------------------------------------ the page

    /**
     * Draws one image. A page is cut into steps: the two halves of a spread (when split), and in "page width" mode the parts of a half that
     * is longer than the screen (each part starts 90 percent of a screen height after the one before, so a little of the picture is seen twice).
     */
    static final class ComicView extends View {
        interface Listener {
            void left();

            void right();

            void center();
        }

        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final Paint note = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int tapSlop, swipeMin;
        private Bitmap bmp;
        private boolean wide, rtl, fitWidth;
        private int halves = 1, segs = 1, unit;
        private Listener listener;
        private float downX, downY;
        private String message = "正在打开...";

        ComicView(Context c) {
            super(c);
            note.setColor(Color.BLACK);
            note.setTextSize(Ui.dp(c, 16));
            note.setTextAlign(Paint.Align.CENTER);
            tapSlop = Ui.dp(c, 20);
            swipeMin = Ui.dp(c, 60);
            setBackgroundColor(Color.WHITE);
        }

        void setListener(Listener l) { listener = l; }

        void setMessage(String m) {
            message = m;
            invalidate();
        }

        void setPage(Bitmap b, boolean wide, boolean rtl, boolean fitWidth) {
            bmp = b;
            this.wide = wide;
            this.rtl = rtl;
            this.fitWidth = fitWidth;
            unit = 0;
            count();
            invalidate();
        }

        private void count() {
            halves = wide ? 2 : 1;
            segs = 1;
            if (bmp != null && fitWidth && getWidth() > 0 && getHeight() > 0) {
                float scale = getWidth() / (bmp.getWidth() / (float) halves);
                float sh = bmp.getHeight() * scale;
                if (sh > getHeight() * 1.02f) segs = 1 + (int) Math.ceil((sh - getHeight()) / (getHeight() * 0.9f));
            }
            unit = Math.max(0, Math.min(unit, halves * segs - 1));
        }

        int units() { return halves * segs; }

        int unit() { return unit; }

        void setUnit(int u) {
            unit = Math.max(0, Math.min(u, units() - 1));
            invalidate();
        }

        /** To the next (or previous) step of the page; false when there is none. */
        boolean step(boolean next) {
            int u = unit + (next ? 1 : -1);
            if (u < 0 || u >= units()) return false;
            unit = u;
            invalidate();
            return true;
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            count();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float vw = getWidth(), vh = getHeight();
            if (bmp == null) {
                float y = vh / 2f, lh = note.getTextSize() * 1.4f;
                String m = message;
                while (m.length() > 0) {
                    int n = Math.max(1, note.breakText(m, true, vw - Ui.dp(getContext(), 40), null));
                    canvas.drawText(m, 0, n, vw / 2f, y, note);
                    m = m.substring(n);
                    y += lh;
                }
                return;
            }
            int k = unit / segs, seg = unit % segs;
            int half = wide ? (rtl ? 1 - k : k) : 0;
            float hw = bmp.getWidth() / (float) halves, bh = bmp.getHeight();
            float scale = fitWidth ? vw / hw : Math.min(vw / hw, vh / bh);
            float srcTop = 0, srcH = bh;
            if (fitWidth && segs > 1) {
                float topS = Math.min(seg * vh * 0.9f, bh * scale - vh);
                srcTop = topS / scale;
                srcH = vh / scale;
            }
            float dw = hw * scale, dh = srcH * scale;
            float dl = (vw - dw) / 2f, dt = segs > 1 ? 0 : (vh - dh) / 2f;
            int sl = Math.round(half * hw);
            Rect from = new Rect(sl, (int) srcTop, Math.round(sl + hw), (int) Math.min(bh, srcTop + srcH));
            canvas.drawBitmap(bmp, from, new RectF(dl, dt, dl + dw, dt + dh), paint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getX();
                    downY = e.getY();
                    return true;
                case MotionEvent.ACTION_UP:
                    float dx = e.getX() - downX, dy = e.getY() - downY;
                    if (listener != null && Math.abs(dx) >= swipeMin && Math.abs(dx) > 2 * Math.abs(dy)) {
                        if (dx < 0) listener.right();
                        else listener.left();
                        return true;
                    }
                    if (listener != null && Math.abs(e.getX() - downX) < tapSlop && Math.abs(e.getY() - downY) < tapSlop) {
                        float x = e.getX(), w = getWidth();
                        if (x < w / 3) listener.left();
                        else if (x > w * 2 / 3) listener.right();
                        else listener.center();
                    }
                    return true;
                default:
                    return true;
            }
        }
    }
}
