/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * ComicSource: Interface for the pages of a comic: image files in an archive, in reading order. */
package com.embrith.library;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** The pages of a comic: image files in an archive, in reading order. */
interface ComicSource {
    int count();

    /** The bytes of the image file of page i. */
    byte[] read(int i) throws IOException;

    void close();
}

/** A comic that has a table of contents (a mobi comic); load it off the UI thread, it may unpack the book text. */
interface ComicToc {
    /** The entries as {name, page index}, in the order of the book; empty when the book has none. */
    List<Object[]> toc() throws IOException;
}

/**
 * Opens a comic archive. The kind is taken from the first bytes of the file and not from its name (many .cbr files are zip files):
 * zip (cbz) and tar (cbt) are read; rar (cbr) and 7z (cb7) are named in the message, because there is no decoder for them.
 * The images are sorted by name, with the numbers in the names counted as numbers.
 */
final class ComicFiles {
    private static final String[] COMIC = { "cbz", "cbt", "cbr", "cb7" };
    private static final String[] IMAGE = { "jpg", "jpeg", "png", "webp", "gif", "bmp" };
    private static final int MAX_IMG = 48 * 1024 * 1024;

    private ComicFiles() { }

    static boolean isComic(String name) {
        String e = LibStore.ext(name);
        for (String x : COMIC) if (x.equals(e)) return true;
        return false;
    }

    /** A comic by its name, or a mobi / azw3 / epub book made of pages (reads the file). */
    static boolean isComic(File f) {
        return isComic(f.getName()) || (isMobi(f.getName()) && MobiPages.matches(f)) || (isEpub(f.getName()) && EpubPages.matches(f));
    }

    private static boolean isEpub(String name) {
        return LibStore.ext(name).equals("epub");
    }

    private static boolean isMobi(String name) {
        String e = LibStore.ext(name);
        return e.equals("mobi") || e.equals("azw") || e.equals("azw3");
    }

    static boolean isImage(String name) {
        String n = name.toLowerCase();
        if (n.endsWith("/") || n.contains("__macosx/")) return false;
        String base = n.substring(n.lastIndexOf('/') + 1);
        if (base.startsWith(".")) return false;
        String e = LibStore.ext(base);
        for (String x : IMAGE) if (x.equals(e)) return true;
        return false;
    }

    static ComicSource open(File f) throws IOException {
        if (isMobi(f.getName())) {
            ComicSource p = MobiPages.open(f);
            if (p != null) return p;
        }
        if (isEpub(f.getName())) {
            ComicSource p = EpubPages.open(f);
            if (p == null) throw new IOException("这本 epub 不是漫画");
            return p;
        }
        byte[] m = new byte[8];
        RandomAccessFile r = new RandomAccessFile(f, "r");
        try {
            if (r.length() >= 262) {
                r.seek(257);
                byte[] u = new byte[5];
                r.readFully(u);
                if (new String(u, "ISO-8859-1").equals("ustar")) return new Tar(f);
            }
            r.seek(0);
            if (r.read(m) < 4) throw new IOException("不是漫画文件");
        } finally {
            r.close();
        }
        if (m[0] == 'P' && m[1] == 'K') return new Zip(f);
        if (m[0] == 'R' && m[1] == 'a' && m[2] == 'r') throw new IOException("RAR (cbr) 格式暂不支持, 请先转成 cbz");
        if (m[0] == '7' && m[1] == 'z') throw new IOException("7z (cb7) 格式暂不支持, 请先转成 cbz");
        if (LibStore.ext(f.getName()).equals("cbt")) return new Tar(f);
        throw new IOException("不认识这种漫画格式");
    }

    /** Names compared with the digit runs as numbers: page2 before page10. */
    static int natural(String a, String b) {
        int i = 0, j = 0;
        while (i < a.length() && j < b.length()) {
            char x = a.charAt(i), y = b.charAt(j);
            if (Character.isDigit(x) && Character.isDigit(y)) {
                int i2 = i, j2 = j;
                while (i2 < a.length() && Character.isDigit(a.charAt(i2))) i2++;
                while (j2 < b.length() && Character.isDigit(b.charAt(j2))) j2++;
                String da = a.substring(i, i2).replaceFirst("^0+", ""), db = b.substring(j, j2).replaceFirst("^0+", "");
                if (da.length() != db.length()) return da.length() - db.length();
                int c = da.compareTo(db);
                if (c != 0) return c;
                i = i2;
                j = j2;
            } else {
                if (x != y) return x - y;
                i++;
                j++;
            }
        }
        return (a.length() - i) - (b.length() - j);
    }

    private static final class Ent {
        final String name;
        final ZipEntry ze;       // zip
        final long off, size;    // tar

        Ent(String name, ZipEntry ze, long off, long size) {
            this.name = name;
            this.ze = ze;
            this.off = off;
            this.size = size;
        }
    }

    private static void sort(List<Ent> l) {
        Collections.sort(l, new Comparator<Ent>() {
            @Override public int compare(Ent a, Ent b) { return natural(a.name.toLowerCase(), b.name.toLowerCase()); }
        });
    }

    private static final class Zip implements ComicSource {
        private final ZipFile zip;
        private final List<Ent> ents = new ArrayList<Ent>();

        Zip(File f) throws IOException {
            zip = new ZipFile(f);
            Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (!e.isDirectory() && isImage(e.getName())) ents.add(new Ent(e.getName(), e, 0, 0));
            }
            sort(ents);
        }

        @Override public int count() { return ents.size(); }

        @Override public byte[] read(int i) throws IOException {
            InputStream in = zip.getInputStream(ents.get(i).ze);
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    if (out.size() > MAX_IMG) throw new IOException("这一页太大");
                }
                return out.toByteArray();
            } finally {
                in.close();
            }
        }

        @Override public void close() {
            try {
                zip.close();
            } catch (IOException e) {
                // closing only
            }
        }
    }

    /** A tar file: the headers are read once, the images are read by their place in the file. Names of 100 chars or more (ustar prefix, GNU long names, pax paths) are handled. */
    private static final class Tar implements ComicSource {
        private final RandomAccessFile raf;
        private final List<Ent> ents = new ArrayList<Ent>();

        Tar(File f) throws IOException {
            raf = new RandomAccessFile(f, "r");
            try {
                scan();
            } catch (IOException e) {
                raf.close();
                throw e;
            }
            sort(ents);
        }

        private void scan() throws IOException {
            byte[] h = new byte[512];
            long pos = 0, len = raf.length();
            String pending = null;
            while (pos + 512 <= len) {
                raf.seek(pos);
                raf.readFully(h);
                boolean zero = true;
                for (int k = 0; k < 512 && zero; k++) zero = h[k] == 0;
                if (zero) break;
                String name = text(h, 0, 100);
                if (text(h, 257, 5).equals("ustar")) {
                    String pre = text(h, 345, 155);
                    if (pre.length() > 0) name = pre + "/" + name;
                }
                long size = number(h, 124, 12);
                int type = h[156];
                long data = pos + 512;
                if (type == 'L' || type == 'x') {
                    if (size > 0 && size <= 65536) {
                        byte[] d = new byte[(int) size];
                        raf.seek(data);
                        raf.readFully(d);
                        String s = new String(d, "UTF-8");
                        if (type == 'L') {
                            int z = s.indexOf('\0');
                            pending = z < 0 ? s : s.substring(0, z);
                        } else {
                            int at = s.indexOf(" path=");
                            if (at >= 0) {
                                int end = s.indexOf('\n', at);
                                pending = s.substring(at + 6, end < 0 ? s.length() : end);
                            }
                        }
                    }
                } else if (type == '0' || type == 0) {
                    if (pending != null) name = pending;
                    pending = null;
                    if (size > 0 && isImage(name)) ents.add(new Ent(name, null, data, size));
                } else if (type != 'g') {
                    pending = null;
                }
                pos = data + ((size + 511) / 512) * 512;
            }
        }

        private static String text(byte[] b, int off, int max) throws IOException {
            int n = 0;
            while (n < max && b[off + n] != 0) n++;
            return new String(b, off, n, "UTF-8");
        }

        /** An octal number, or (high bit of the first byte set) a big endian binary number. */
        private static long number(byte[] b, int off, int max) {
            long v = 0;
            if ((b[off] & 0x80) != 0) {
                for (int k = 1; k < max; k++) v = (v << 8) | (b[off + k] & 0xFF);
                return v;
            }
            for (int k = 0; k < max; k++) {
                int c = b[off + k];
                if (c >= '0' && c <= '7') v = v * 8 + (c - '0');
                else if (v != 0 || c != ' ') break;
            }
            return v;
        }

        @Override public int count() { return ents.size(); }

        @Override public synchronized byte[] read(int i) throws IOException {
            Ent e = ents.get(i);
            if (e.size > MAX_IMG) throw new IOException("这一页太大");
            byte[] d = new byte[(int) e.size];
            raf.seek(e.off);
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
}
