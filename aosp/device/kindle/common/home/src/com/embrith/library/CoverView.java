/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * CoverView: The cover of a book wherever the library shows one (grid, now reading, overview): the picture, or the text of the
 * library settings when there is none, and the badge. One place, so every page follows the same settings. */
package com.embrith.library;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.embrith.home.Prefs;
import com.embrith.home.Ui;
import com.embrith.language.T;

import java.io.File;

/**
 * Settings read here: lib_nocover (ext, title, both, ta, blank: the text of a book without a picture), lib_badge (on, off) and
 * lib_label (file, title, title_author, author_title: what a book is called).
 */
final class CoverView {
    private CoverView() { }

    /**
     * A framed cover of w x h: the picture when the book has one, else the text of lib_nocover; the badge: a diagonal ribbon in the top right corner. Click and
     * long press are the business of the caller. A folder gets its name and nothing else. extSp / textSp: the text size for the format name /
     * for the other texts.
     */
    static FrameLayout make(final LibraryActivity act, final File f, final int cw, final int ch, int extSp, int textSp) {
        final FrameLayout box = new FrameLayout(act);
        box.setBackground(Ui.outline(act));
        final TextView kind = Ui.text(act, text(act, f), isExt(act, f) ? extSp : textSp, true);
        kind.setGravity(Gravity.CENTER);
        kind.setPadding(Ui.dp(act, 6), 0, Ui.dp(act, 6), 0);
        kind.setMaxLines(6);
        kind.setEllipsize(TextUtils.TruncateAt.END);
        kind.setLineSpacing(Ui.dp(act, 6), 1f);   // title and author (or name and format) on two lines: some air between them
        box.addView(kind, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (f.isDirectory()) return box;
        final ImageView iv = new ImageView(act);
        iv.setScaleType(ImageView.ScaleType.FIT_XY);   // the bitmap is already made at the size of the frame
        box.addView(iv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        final Ribbon tag = new Ribbon(act);
        tag.slot = f.getName().hashCode();   // the multicolor mode: one palette color per book
        tag.setVisibility(View.GONE);
        int side = Math.max(Ui.dp(act, 44), cw * 38 / 100);
        box.addView(tag, new FrameLayout.LayoutParams(side, side, Gravity.RIGHT | Gravity.TOP));
        act.coverPool().execute(new Runnable() {
            @Override public void run() {
                final String bt = badge(act, f);
                if (bt != null) act.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (act.isFinishing()) return;
                        tag.setText(bt);
                        tag.setVisibility(View.VISIBLE);
                    }
                });
                Bitmap raw = Covers.load(f, cw, ch);
                if (raw == null) {
                    if (!needsMeta(act)) return;
                    Meta.get(f);   // the title and the author, for the text in the empty cover
                    act.runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (!act.isFinishing()) kind.setText(text(act, f));
                        }
                    });
                    return;
                }
                final Bitmap b = LibraryActivity.fillRounded(raw, cw, ch, Ui.radius(act));
                act.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (act.isFinishing()) return;
                        iv.setImageBitmap(b);
                        box.setForeground(Ui.outline(act));   // the frame line over the cover
                        kind.setVisibility(View.GONE);
                    }
                });
            }
        });
        return box;
    }

    /** True when the text of a cover without a picture, or the name of a book, needs the title and the author read from the book. */
    static boolean needsMeta(Context c) {
        return !Prefs.getStr(c, "lib_label", "file").equals("file") || Prefs.getStr(c, "lib_nocover", "ext").equals("ta");
    }

    private static boolean isExt(Context c, File f) {
        return !f.isDirectory() && Prefs.getStr(c, "lib_nocover", "ext").equals("ext");
    }

    /** What a cover without a picture shows: the format, the title, both, the title and the author, or nothing (a folder always shows its name). */
    static String text(Context c, File f) {
        if (f.isDirectory()) return f.getName();
        String mode = Prefs.getStr(c, "lib_nocover", "ext");
        String label = Prefs.getStr(c, "lib_label", "file");
        String name = LibStore.stem(f.getName());
        if (!label.equals("file")) {
            Meta m = Meta.peek(f);
            if (m != null) {
                if (m.title.length() > 0) name = m.title;
                if (m.author.length() > 0) {
                    if (label.equals("title_author")) name = name + "\n" + m.author;
                    else if (label.equals("author_title")) name = m.author + "\n" + name;
                }
            }
        }
        String ext = LibStore.ext(f.getName()).toUpperCase();
        if (mode.equals("ta")) {
            Meta m = Meta.peek(f);
            String t = m != null && m.title.length() > 0 ? m.title : LibStore.stem(f.getName());
            return m != null && m.author.length() > 0 ? t + "\n" + m.author : t;
        }
        if (mode.equals("title")) return name;
        if (mode.equals("both")) return name + "\n" + ext;
        if (mode.equals("blank")) return "";
        return ext;
    }

    /** The badge of a book (lib_badge): "new" for a book never opened, the percent read once it has a progress; null for none. Not on the UI thread. */
    private static String badge(Context c, File f) {
        if (Prefs.getStr(c, "lib_badge", "on").equals("off")) return null;
        if (!ReadState.opened(f)) return T.t(c, "新");
        double p = ReadState.load(f).pct;
        return p >= 1 ? Math.round(p) + "%" : null;
    }

    /** A diagonal ribbon across the top right corner of a square: black band, white text turned 45 degrees (the corner mark of KOReader). */
    private static final class Ribbon extends View {
        private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private String text = "";
        int slot;

        Ribbon(Context c) {
            super(c);
            band.setColor(Color.BLACK);
            ink.setColor(Color.WHITE);
            ink.setTextAlign(Paint.Align.CENTER);
            ink.setFakeBoldText(true);
        }

        void setText(String s) {
            text = s;
            invalidate();
        }

        @Override protected void onDraw(Canvas cv) {
            band.setColor(Prefs.libAccent(getContext(), slot));   // the theme color of the library (black unless the color display is on)
            float s = getWidth();
            float c1 = s * 0.40f, c2 = s * 0.88f;   // the band lies between the lines x - y = c1 and x - y = c2
            path.reset();
            path.moveTo(c1, 0);
            path.lineTo(c2, 0);
            path.lineTo(s, s - c2);
            path.lineTo(s, s - c1);
            path.close();
            cv.drawPath(path, band);
            float mid = (c1 + c2) / 2f;
            float cx = (mid + s) / 2f, cy = (s - mid) / 2f;   // the middle of the band
            ink.setTextSize(s * 0.17f);
            cv.save();
            cv.rotate(45f, cx, cy);
            cv.drawText(text, cx, cy - (ink.ascent() + ink.descent()) / 2f, ink);
            cv.restore();
        }
    }
}