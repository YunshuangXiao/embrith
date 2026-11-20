/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * MobiPages: Pages of a comic kept in a mobi, azw or azw3 file: the image records of the book, in the order of the
 * records. */
package com.embrith.library;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

/**
 * The pages of a comic kept in a mobi / azw / azw3 file: the image records of the book, in the order of the records (the thumbnail of
 * the cover, EXTH 202, is left out). A book counts as a comic when it has at least MIN_PAGES images and little text for them (the
 * markup of a page of a comic is short); a book with a DRM is not read. Records are not decoded here, an image record is the image file.
 */
final class MobiPages implements ComicSource, ComicToc {
    private static final int MIN_PAGES = 5;
    private static final int TEXT_PER_PAGE = 4000;       // bytes of unpacked text per image, a comic has less
    private static final int MAX_IMG = 48 * 1024 * 1024;

    private final RandomAccessFile raf;
    private final int[] off;    // start of each record, plus the end of the file
    private final int[] pages;  // record numbers of the images
    private final File file;
    private final int first;    // record number of the first image (recindex 1)

    private MobiPages(File file, RandomAccessFile raf, int[] off, int[] pages, int first) {
        this.file = file;
        this.raf = raf;
        this.off = off;
        this.pages = pages;
        this.first = first;
    }

    /** True when the file is a comic in mobi format (reads the record headers, not on the UI thread when the book is big). */
    static boolean matches(File f) {
        try {
            MobiPages p = open(f);
            if (p == null) return false;
            p.close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** The comic in this file, or null when it is not one. */
    static MobiPages open(File f) throws IOException {
        RandomAccessFile r = new RandomAccessFile(f, "r");
        boolean ok = false;
        try {
            if (r.length() < 78 + 16) return null;
            byte[] id = new byte[8];
            r.seek(60);
            r.readFully(id);
            if (!new String(id, "ISO-8859-1").equals("BOOKMOBI")) return null;
            r.seek(76);
            int n = r.readUnsignedShort();
            if (n < 3) return null;
            int[] off = new int[n + 1];
            r.seek(78);
            for (int i = 0; i < n; i++) {
                off[i] = r.readInt();
                r.skipBytes(4);
            }
            off[n] = (int) r.length();
            int hn = off[1] - off[0];
            if (off[0] < 0 || hn < 132 || hn > (1 << 20)) return null;
            byte[] h = new byte[hn];
            r.seek(off[0]);
            r.readFully(h);
            if (h[16] != 'M' || h[17] != 'O' || h[18] != 'B' || h[19] != 'I' || u16(h, 12) != 0) return null;
            long first = u32(h, 108), text = u32(h, 4);
            if (first <= 0 || first >= n) return null;
            long thumb = -1;
            if ((u32(h, 128) & 0x40) != 0) {
                int e = (int) (16 + u32(h, 20));
                if (e + 12 <= h.length && h[e] == 'E' && h[e + 1] == 'X' && h[e + 2] == 'T' && h[e + 3] == 'H') {
                    long cnt = u32(h, e + 8);
                    int p = e + 12;
                    for (long k = 0; k < cnt && p + 12 <= h.length; k++) {
                        long type = u32(h, p), l = u32(h, p + 4);
                        if (l < 8 || p + l > h.length) break;
                        if (type == 202 && l == 12) thumb = u32(h, p + 8);
                        p += (int) l;
                    }
                }
            }
            List<Integer> imgs = new ArrayList<Integer>();
            byte[] m = new byte[12];
            for (int i = (int) first; i < n; i++) {
                if (i == first + thumb || off[i + 1] - off[i] < 100) continue;
                r.seek(off[i]);
                r.readFully(m);
                if (isImage(m)) imgs.add(i);
            }
            if (imgs.size() < MIN_PAGES || text >= (long) TEXT_PER_PAGE * imgs.size()) return null;
            int[] pages = new int[imgs.size()];
            for (int i = 0; i < pages.length; i++) pages[i] = imgs.get(i);
            ok = true;
            return new MobiPages(f, r, off, pages, (int) first);
        } finally {
            if (!ok) r.close();
        }
    }

    static boolean isImage(byte[] m) {
        if ((m[0] & 0xFF) == 0xFF && (m[1] & 0xFF) == 0xD8 && (m[2] & 0xFF) == 0xFF) return true;                       // jpeg
        if ((m[0] & 0xFF) == 0x89 && m[1] == 'P' && m[2] == 'N' && m[3] == 'G') return true;                              // png
        if (m[0] == 'G' && m[1] == 'I' && m[2] == 'F' && m[3] == '8') return true;                                        // gif
        if (m[0] == 'R' && m[1] == 'I' && m[2] == 'F' && m[3] == 'F' && m[8] == 'W' && m[9] == 'E' && m[10] == 'B') return true;   // webp
        return m[0] == 'B' && m[1] == 'M';                                                                                // bmp
    }

    private static int u16(byte[] b, int o) {
        return ((b[o] & 0xFF) << 8) | (b[o + 1] & 0xFF);
    }

    private static long u32(byte[] b, int o) {
        return ((long) u16(b, o) << 16) | u16(b, o + 2);
    }

    /**
     * The cover image of a mobi / azw / azw3 book (the old part of it, MOBI6): EXTH 201 is the number of the cover among the images,
     * else EXTH 202 (the thumbnail), else the first image record. null when the book has no image.
     */
    static byte[] cover(File f) throws IOException {
        RandomAccessFile r = new RandomAccessFile(f, "r");
        try {
            if (r.length() < 78 + 16) return null;
            byte[] id = new byte[8];
            r.seek(60);
            r.readFully(id);
            if (!new String(id, "ISO-8859-1").equals("BOOKMOBI")) return null;
            r.seek(76);
            int n = r.readUnsignedShort();
            if (n < 3) return null;
            int[] off = new int[n + 1];
            r.seek(78);
            for (int i = 0; i < n; i++) {
                off[i] = r.readInt();
                r.skipBytes(4);
            }
            off[n] = (int) r.length();
            int hn = off[1] - off[0];
            if (off[0] < 0 || hn < 132 || hn > (1 << 20)) return null;
            byte[] h = new byte[hn];
            r.seek(off[0]);
            r.readFully(h);
            if (h[16] != 'M' || h[17] != 'O' || h[18] != 'B' || h[19] != 'I') return null;
            long first = u32(h, 108);
            if (first <= 0 || first >= n) return null;
            long c201 = -1, c202 = -1;
            if ((u32(h, 128) & 0x40) != 0) {
                int e = (int) (16 + u32(h, 20));
                if (e + 12 <= h.length && h[e] == 'E' && h[e + 1] == 'X' && h[e + 2] == 'T' && h[e + 3] == 'H') {
                    long cnt = u32(h, e + 8);
                    int p = e + 12;
                    for (long k = 0; k < cnt && p + 12 <= h.length; k++) {
                        long type = u32(h, p), l = u32(h, p + 4);
                        if (l < 8 || p + l > h.length) break;
                        if (type == 201 && l == 12) c201 = u32(h, p + 8);
                        if (type == 202 && l == 12) c202 = u32(h, p + 8);
                        p += (int) l;
                    }
                }
            }
            long[] tries = { c201, c202 };
            for (long t : tries) {
                if (t < 0 || first + t >= n) continue;
                byte[] d = image(r, off, (int) (first + t));
                if (d != null) return d;
            }
            for (int i = (int) first; i < n; i++) {
                byte[] d = image(r, off, i);
                if (d != null) return d;
            }
            return null;
        } finally {
            r.close();
        }
    }

    /** The record i when it is an image file (not bigger than MAX_IMG), else null. */
    private static byte[] image(RandomAccessFile r, int[] off, int i) throws IOException {
        int len = off[i + 1] - off[i];
        if (off[i] < 0 || len < 100 || len > MAX_IMG) return null;
        byte[] d = new byte[len];
        r.seek(off[i]);
        r.readFully(d);
        return isImage(d) ? d : null;
    }

    /** The table of contents of the book text, mapped to the pages: a chapter is the page of the first image after its start. */
    @Override public List<Object[]> toc() throws IOException {
        MobiSource ms = new MobiSource(file);
        List<String> names = new ArrayList<String>();
        List<Integer> marks = ms.imageMarks(names);
        List<Object[]> out = new ArrayList<Object[]>();
        int last = -1;
        for (int i = 0; i < marks.size(); i++) {
            int rec = first + marks.get(i) - 1, page = -1;
            for (int j = 0; j < pages.length; j++) {
                if (pages[j] == rec) {
                    page = j;
                    break;
                }
            }
            if (page < 0 || page == last) continue;
            last = page;
            out.add(new Object[] { names.get(i), Integer.valueOf(page) });
        }
        return out;
    }

    @Override public int count() { return pages.length; }

    @Override public synchronized byte[] read(int i) throws IOException {
        int rec = pages[i], n = off[rec + 1] - off[rec];
        if (n < 0 || off[rec] < 0) throw new IOException("mobi 文件损坏");
        if (n > MAX_IMG) throw new IOException("这一页太大");
        byte[] d = new byte[n];
        raf.seek(off[rec]);
        raf.readFully(d);
        return d;
    }

    @Override public void close() {
        try {
            raf.close();
        } catch (IOException e) {
            // closing only
        }
    }
}