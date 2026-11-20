/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * NowReading: For the desktop: the book opened last, with its title, author, cover and the time spent reading it. */
package com.embrith.library;

import android.graphics.Bitmap;

import java.io.File;
import java.util.List;

/** For the desktop: the book opened last (the newest line of recent.tsv) with its title, author, cover and the time spent reading it. */
public final class NowReading {
    public static final class Info {
        public String title = "", author = "", ext = "";
        public long ms;          // time spent reading the book
        public Bitmap cover;     // null when the book has none
    }

    private NowReading() { }

    /** Slow (reads the book once, decodes the cover): not on the UI thread. null when nothing was opened yet. */
    public static Info latest(int coverW, int coverH) {
        List<File> l = LibStore.recent();
        if (l.isEmpty()) return null;
        File b = l.get(0);
        Meta m = Meta.get(b);
        Meta.flush();
        String name = b.getName();
        int dot = name.lastIndexOf('.');
        Info i = new Info();
        i.title = m.title.length() > 0 ? m.title : dot > 0 ? name.substring(0, dot) : name;
        i.author = m.author;
        i.ext = LibStore.ext(name).toUpperCase();
        i.ms = ReadTime.get(b);
        i.cover = Covers.load(b, coverW, coverH);
        return i;
    }
}