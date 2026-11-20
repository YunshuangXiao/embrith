/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * ReadState: Where the reader stopped in a book, and its bookmarks, kept per book under library/state. */
package com.embrith.library;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

/**
 * Where the reader stopped in a book, and its bookmarks: /sdcard/Embrith/library/state/&lt;hash of the path&gt;.txt, lines "c=chunk", "o=offset",
 * "m=chunk,offset,text" (one per bookmark).
 */
final class ReadState {
    static final class Mark {
        final int chunk, off;
        final String text;

        Mark(int chunk, int off, String text) {
            this.chunk = chunk;
            this.off = off;
            this.text = text;
        }
    }

    private static final File DIR = new File(LibStore.DIR, "state");

    int chunk, off;
    double pct = -1;   // how far the reader is in the book, 0..100; -1 = not known (the book was last read before this was kept)
    final List<Mark> marks = new ArrayList<Mark>();
    private final File file;

    private ReadState(File file) {
        this.file = file;
    }

    /** True when the book was opened before (the state file exists). */
    static boolean opened(File book) {
        return new File(DIR, Integer.toHexString(book.getPath().hashCode()) + ".txt").isFile();
    }

    static ReadState load(File book) {
        ReadState s = new ReadState(new File(DIR, Integer.toHexString(book.getPath().hashCode()) + ".txt"));
        for (String line : LibStore.readLines(s.file)) {
            try {
                if (line.startsWith("c=")) {
                    s.chunk = Integer.parseInt(line.substring(2));
                } else if (line.startsWith("o=")) {
                    s.off = Integer.parseInt(line.substring(2));
                } else if (line.startsWith("p=")) {
                    s.pct = Double.parseDouble(line.substring(2));
                } else if (line.startsWith("m=")) {
                    String[] p = line.substring(2).split(",", 3);
                    if (p.length == 3) s.marks.add(new Mark(Integer.parseInt(p[0]), Integer.parseInt(p[1]), p[2]));
                }
            } catch (NumberFormatException e) {
                // a damaged line: skipped
            }
        }
        return s;
    }

    void save() {
        try {
            DIR.mkdirs();
            Writer w = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
            try {
                w.write("c=" + chunk + "\n");
                w.write("o=" + off + "\n");
                if (pct >= 0) w.write("p=" + String.format(java.util.Locale.US, "%.2f", pct) + "\n");
                for (Mark m : marks) w.write("m=" + m.chunk + "," + m.off + "," + m.text.replace('\n', ' ').replace('\r', ' ') + "\n");
            } finally {
                w.close();
            }
        } catch (IOException e) {
            // the place is only a convenience
        }
    }
}