/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * PageView: One page of a chunk of text: the chunk is laid out once, cut into pages by lines, with a line of items
 * at the top and bottom. */
package com.embrith.library;

import com.embrith.home.Ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.LeadingMarginSpan;
import android.text.style.ReplacementSpan;
import android.view.MotionEvent;
import android.view.View;

import java.util.Arrays;

/**
 * One page of a chunk of text. The chunk is laid out once (StaticLayout) and cut into pages by lines; the view draws the lines of the
 * current page, with a line of items at the top and one at the bottom. A tap in the left third = previous page, in the right third = next page, in the middle = menu;
 * a swipe to the left = next page, a swipe to the right = previous page.
 */
final class PageView extends View {
    static final int LAST = Integer.MAX_VALUE;   // setContent: start at the last page

    interface Listener {
        void left();

        void right();

        void center();
    }

    private final TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint foot = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint head = new TextPaint(Paint.ANTI_ALIAS_FLAG);   // the top line is bold
    private final int tapSlop, swipeMin;
    private int padL, padR, padTop, padBot;
    private int indent;                       // first line indent, in half-width characters (a Chinese character is two)
    private CharSequence text;
    private StaticLayout layout;
    private int[] firstLines = new int[0];   // the first line of every page
    private int page;
    private int want;                         // the character that has to be on the page
    private float size = 40f, mult = 1.5f;
    private String topL = "", topR = "", botL = "", botR = "";
    private Listener listener;
    private float downX, downY;

    PageView(Context c) {
        super(c);
        paint.setColor(Color.BLACK);
        foot.setColor(Color.BLACK);
        foot.setTextSize(Ui.dp(c, 13));
        head.setColor(Color.BLACK);
        head.setTextSize(Ui.dp(c, 13));
        head.setFakeBoldText(true);
        padL = padR = Ui.dp(c, 26);
        padTop = Ui.dp(c, 40);
        padBot = Ui.dp(c, 44);
        tapSlop = Ui.dp(c, 20);
        swipeMin = Ui.dp(c, 60);
        setBackgroundColor(Color.WHITE);
    }

    void setListener(Listener l) { listener = l; }

    /** The four items around the page: top left, top right, bottom left, bottom right. */
    void setBars(String tl, String tr, String bl, String br) {
        topL = tl;
        topR = tr;
        botL = bl;
        botR = br;
        invalidate();
    }

    /**
     * Text size in pixels, the line spacing factor, the font (null: default), the four margins in pixels (top, bottom, left, right; the text
     * area starts exactly there, the top and bottom lines are drawn at a fixed distance from the screen edge) and the first line indent in
     * half-width characters (a Chinese character is two). The page keeps its first character.
     */
    void setStyle(float px, float lineMult, Typeface tf, int top, int bottom, int left, int right, int indentChars) {
        if (layout != null) want = pageStart();
        size = px;
        mult = lineMult;
        paint.setTypeface(tf);
        padL = left;
        padR = right;
        padTop = top;
        padBot = bottom;
        indent = indentChars;
        build();
        place();
        invalidate();
    }

    /** Show a new chunk, with character off (or LAST) on the page. */
    void setContent(CharSequence t, int off) {
        text = t;
        want = off;
        build();
        place();
        invalidate();
    }

    int textLength() { return text == null ? 0 : text.length(); }

    int page() { return page; }

    int pageCount() { return firstLines.length; }

    int pageStart() { return layout == null || firstLines.length == 0 ? 0 : layout.getLineStart(firstLines[page]); }

    int pageEnd() {
        if (layout == null || firstLines.length == 0) return 0;
        return page + 1 < firstLines.length ? layout.getLineStart(firstLines[page + 1]) : text.length();
    }

    /** The first characters of the page, for the name of a bookmark. */
    String snippet(int max) {
        if (text == null) return "";
        int a = pageStart();
        int b = Math.min(text.length(), a + max);
        return text.subSequence(a, b).toString().replace('\n', ' ').replace('\r', ' ').trim();
    }

    boolean nextPage() {
        if (page + 1 >= firstLines.length) return false;
        page++;
        invalidate();
        return true;
    }

    boolean prevPage() {
        if (page <= 0) return false;
        page--;
        invalidate();
        return true;
    }

    private void build() {
        int w = getWidth() - padL - padR, h = getHeight() - padTop - padBot;
        if (text == null || w <= 0 || h <= 0) {
            layout = null;
            firstLines = new int[0];
            return;
        }
        paint.setTextSize(size);
        CharSequence src = text;
        if (indent > 0) {
            SpannableString sp = new SpannableString(text);
            int n = text.length(), a = 0;
            while (a < n) {
                int e = a;
                while (e < n && text.charAt(e) != '\n') e++;
                int end = Math.min(n, e + 1);
                int b = a;
                while (b < e && isBlank(text.charAt(b))) b++;   // the blanks the text starts the paragraph with: the indent of the setting replaces them
                if (b > a) sp.setSpan(new NoWidth(), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (e > a) sp.setSpan(new LeadingMarginSpan.Standard((int) (indent * size / 2), 0), a, end, Spanned.SPAN_PARAGRAPH);
                a = end;
            }
            src = sp;
        }
        layout = new StaticLayout(src, paint, w, Layout.Alignment.ALIGN_NORMAL, mult, 0f, false);
        int n = layout.getLineCount();
        int[] fl = new int[n + 1];
        int cnt = 0, line = 0;
        while (line < n) {
            fl[cnt++] = line;
            int top = layout.getLineTop(line);
            int end = layout.getLineForVertical(top + h);
            if (layout.getLineBottom(end) > top + h) end--;
            if (end < line) end = line;
            line = end + 1;
        }
        firstLines = Arrays.copyOf(fl, cnt);
    }

    private void place() {
        page = 0;
        if (layout == null || firstLines.length == 0) return;
        if (want == LAST) {
            page = firstLines.length - 1;
            return;
        }
        int line = layout.getLineForOffset(Math.max(0, Math.min(want, text.length())));
        for (int p = 0; p < firstLines.length; p++) {
            if (firstLines[p] <= line) page = p;
            else break;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        build();
        place();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (layout == null || firstLines.length == 0) {
            if (text == null) {
                foot.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("正在打开...", getWidth() / 2f, getHeight() / 2f, foot);
            }
            return;
        }
        int start = firstLines[page];
        int endLine = page + 1 < firstLines.length ? firstLines[page + 1] - 1 : layout.getLineCount() - 1;
        int top = layout.getLineTop(start), bottom = layout.getLineBottom(endLine);
        canvas.save();
        canvas.clipRect(padL, padTop, getWidth() - padR, padTop + (bottom - top));
        canvas.translate(padL, padTop - top);
        layout.draw(canvas);
        canvas.restore();
        bar(canvas, head, topL, topR, Ui.dp(getContext(), 18));
        bar(canvas, foot, botL, botR, getHeight() - Ui.dp(getContext(), 10));
    }

    /** A line with one item at the left and one at the right; a long item is cut with "..." at its end. */
    private void bar(Canvas canvas, TextPaint foot, String l, String r, float y) {
        float w = getWidth() - padL - padR, gap = Ui.dp(getContext(), 12);
        foot.setTextAlign(Paint.Align.LEFT);
        CharSequence rs = TextUtils.ellipsize(r, foot, w * 0.45f, TextUtils.TruncateAt.END);
        float rw = rs.length() == 0 ? 0 : foot.measureText(rs, 0, rs.length());
        CharSequence ls = TextUtils.ellipsize(l, foot, Math.max(0f, w - rw - gap), TextUtils.TruncateAt.END);
        canvas.drawText(ls, 0, ls.length(), padL, y, foot);
        canvas.drawText(rs, 0, rs.length(), getWidth() - padR - rw, y, foot);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                return true;
            case MotionEvent.ACTION_UP:
                if (listener != null && Math.abs(e.getX() - downX) < tapSlop && Math.abs(e.getY() - downY) < tapSlop) {
                    float x = e.getX(), w = getWidth();
                    if (x < w / 3) listener.left();
                    else if (x > w * 2 / 3) listener.right();
                    else listener.center();
                } else if (listener != null) {
                    // a swipe: finger to the left = next page, finger to the right = previous page
                    float dx = e.getX() - downX, dy = e.getY() - downY;
                    if (Math.abs(dx) >= swipeMin && Math.abs(dx) > 2 * Math.abs(dy)) {
                        if (dx < 0) listener.right();
                        else listener.left();
                    }
                }
                return true;
            default:
                return true;
        }
    }

    private static boolean isBlank(char c) {
        return c == ' ' || c == '\t' || c == '\u3000' || c == '\u00a0';
    }

    /** Takes no room and draws nothing: the characters stay in the text (so every offset in it stays the same) but are not seen. */
    private static final class NoWidth extends ReplacementSpan {
        @Override public int getSize(Paint p, CharSequence t, int start, int end, Paint.FontMetricsInt fm) { return 0; }

        @Override public void draw(Canvas c, CharSequence t, int start, int end, float x, int top, int y, int bottom, Paint p) { }
    }
}