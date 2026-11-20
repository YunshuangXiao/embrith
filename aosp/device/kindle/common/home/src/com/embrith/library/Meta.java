/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * Meta: What the library knows about a book: title, author, series and tags, read from the file once. */
package com.embrith.library;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * What the library knows about a book: title, author, series (with the place in it) and tags. It is read from the file once (epub: the
 * package file; mobi: the header record; txt: an author line near the start) and kept in meta.tsv beside recent.tsv, one line per book:
 * path, size, time, title, author, series, index, tags. A line is used only while the size and the modified time of the file still match.
 * What the reader edits by hand is kept apart in meta-user.tsv, by path only, and wins over what is read from the file.
 */
final class Meta {
    private static final File CACHE = new File(LibStore.DIR, "meta.tsv");
    private static final File USER = new File(LibStore.DIR, "meta-user.tsv");   // what the reader typed in: path, title, author, series, index, tags
    private static final String SEP = "\u001f";
    private static final int MAX_TAGS = 12;
    private static final Pattern AUTHOR = Pattern.compile(
        "(?m)^[ \\t\u3000]*(?:\u4f5c\u8005|\u8457|Author)[:\uff1a \\t\u3000]+(\\S[^\\n]{0,29})$");
    private static final Map<String, Meta> MAP = new HashMap<String, Meta>();
    private static final Map<String, String[]> EDITS = new HashMap<String, String[]>();
    private static boolean loaded, dirty, userDirty;

    String title = "", author = "", series = "";
    float index = -1;                       // place in the series, -1 when unknown
    final List<String> tags = new ArrayList<String>();
    private long size, time;

    private Meta() { }

    /** The cached entry of a book, or null when it was never read or the file changed since. Quick: safe on the UI thread. */
    static synchronized Meta peek(File f) {
        load();
        Meta m = MAP.get(f.getPath());
        return m != null && m.size == f.length() && m.time == f.lastModified() ? m : null;
    }

    /** The entry of a book, read from the file when it is not cached (slow: not on the UI thread). A book that cannot be read gets an empty entry. */
    static Meta get(File f) {
        Meta m = peek(f);
        if (m != null) return m;
        m = new Meta();
        m.size = f.length();
        m.time = f.lastModified();
        try {
            String e = LibStore.ext(f.getName());
            if (e.equals("epub")) readEpub(f, m);
            else if (e.equals("txt")) readTxt(f, m);
            else if (e.equals("kfx")) readKfx(f, m);
            else readMobi(f, m);
        } catch (Exception e) {
            // an unreadable book keeps an empty entry, so it is not tried again
        }
        synchronized (Meta.class) {
            String[] u = EDITS.get(f.getPath());
            if (u != null) apply(m, u);
            MAP.put(f.getPath(), m);
            dirty = true;
        }
        return m;
    }

    private static void apply(Meta m, String[] u) {
        m.title = u[0];
        m.author = u[1];
        m.series = u[2];
        m.index = number(u[3]);
        m.tags.clear();
        if (u[4].length() > 0) for (String s : u[4].split(SEP)) m.tags.add(s);
    }

    /** True when the reader edited the properties of this book by hand. */
    static synchronized boolean edited(File f) {
        load();
        return EDITS.containsKey(f.getPath());
    }

    /** Sets the properties of a book by hand (tags: separated by commas); they win over the file until reset. Not on the UI thread. */
    static void edit(File f, String title, String author, String series, String index, String tags) {
        Meta m = get(f);
        Meta t = new Meta();
        for (String s : tags.split("[,;\uff0c\uff1b\u3001]")) addTag(t, s);
        StringBuilder tg = new StringBuilder();
        for (String s : t.tags) tg.append(tg.length() > 0 ? SEP : "").append(s);
        float ix = number(index);
        String[] u = { clean(title), clean(author), clean(series), String.valueOf(ix), tg.toString() };
        synchronized (Meta.class) {
            EDITS.put(f.getPath(), u);
            apply(m, u);
            dirty = true;
            userDirty = true;
        }
    }

    /** Drops the hand-made properties of a book and reads it again. Not on the UI thread. */
    static void reset(File f) {
        synchronized (Meta.class) {
            load();
            EDITS.remove(f.getPath());
            MAP.remove(f.getPath());
            dirty = true;
            userDirty = true;
        }
        get(f);
    }

    /** Writes the cache file when something was added. */
    static synchronized void flush() {
        if (!dirty) return;
        try {
            LibStore.DIR.mkdirs();
            Writer w = new OutputStreamWriter(new FileOutputStream(CACHE), "UTF-8");
            try {
                for (Map.Entry<String, Meta> e : MAP.entrySet()) {
                    Meta m = e.getValue();
                    StringBuilder t = new StringBuilder();
                    for (String s : m.tags) t.append(t.length() > 0 ? SEP : "").append(s);
                    w.write(clean(e.getKey()) + "\t" + m.size + "\t" + m.time + "\t" + m.title + "\t" + m.author + "\t" + m.series + "\t" + m.index + "\t" + t + "\n");
                }
            } finally {
                w.close();
            }
            dirty = false;
            if (userDirty) {
                Writer uw = new OutputStreamWriter(new FileOutputStream(USER), "UTF-8");
                try {
                    for (Map.Entry<String, String[]> e : EDITS.entrySet()) {
                        String[] u = e.getValue();
                        uw.write(clean(e.getKey()) + "\t" + u[0] + "\t" + u[1] + "\t" + u[2] + "\t" + u[3] + "\t" + u[4] + "\n");
                    }
                } finally {
                    uw.close();
                }
                userDirty = false;
            }
        } catch (Exception e) {
            // the cache is only a convenience
        }
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        loadEdits();
        if (!CACHE.isFile()) return;
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(CACHE), "UTF-8"));
            try {
                String line;
                while ((line = r.readLine()) != null) {
                    String[] p = line.split("\t", -1);
                    if (p.length < 8) continue;
                    Meta m = new Meta();
                    m.size = Long.parseLong(p[1]);
                    m.time = Long.parseLong(p[2]);
                    m.title = p[3];
                    m.author = p[4];
                    m.series = p[5];
                    m.index = Float.parseFloat(p[6]);
                    if (p[7].length() > 0) for (String s : p[7].split(SEP)) m.tags.add(s);
                    MAP.put(p[0], m);
                }
            } finally {
                r.close();
            }
        } catch (Exception e) {
            // a damaged cache is rebuilt as the books are read
        }
    }

    private static void loadEdits() {
        if (!USER.isFile()) return;
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(USER), "UTF-8"));
            try {
                String line;
                while ((line = r.readLine()) != null) {
                    String[] p = line.split("\t", -1);
                    if (p.length >= 6) EDITS.put(p[0], new String[] { p[1], p[2], p[3], p[4], p[5] });
                }
            } finally {
                r.close();
            }
        } catch (Exception e) {
            // a damaged file only loses the hand-made properties
        }
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').replace(SEP, " ").trim();
    }

    private static float number(String s) {
        try {
            return Float.parseFloat(s.trim());
        } catch (Exception e) {
            return -1;
        }
    }

    private static void addTag(Meta m, String s) {
        String t = clean(s);
        if (t.length() > 0 && t.length() <= 40 && m.tags.size() < MAX_TAGS && !m.tags.contains(t)) m.tags.add(t);
    }

    private static void readEpub(File f, Meta m) throws Exception {
        ZipFile z = new ZipFile(f);
        try {
            ZipEntry ce = z.getEntry("META-INF/container.xml");
            if (ce == null) return;
            String opf = null;
            InputStream in = z.getInputStream(ce);
            try {
                XmlPullParser p = Xml.newPullParser();
                p.setInput(in, null);
                for (int t = p.getEventType(); t != XmlPullParser.END_DOCUMENT && opf == null; t = p.next()) {
                    if (t == XmlPullParser.START_TAG && Covers.local(p.getName()).equals("rootfile")) opf = p.getAttributeValue(null, "full-path");
                }
            } finally {
                in.close();
            }
            ZipEntry oe = opf == null ? null : z.getEntry(opf);
            if (oe == null) return;
            String collection = "";
            float position = -1;
            in = z.getInputStream(oe);
            try {
                XmlPullParser p = Xml.newPullParser();
                p.setInput(in, null);
                for (int t = p.getEventType(); t != XmlPullParser.END_DOCUMENT; t = p.next()) {
                    if (t != XmlPullParser.START_TAG) continue;
                    String n = Covers.local(p.getName());
                    if (n.equals("title") && m.title.length() == 0) {
                        m.title = clean(p.nextText());
                    } else if (n.equals("creator") && m.author.length() == 0) {
                        m.author = clean(p.nextText());
                    } else if (n.equals("subject")) {
                        addTag(m, p.nextText());
                    } else if (n.equals("meta")) {
                        String name = p.getAttributeValue(null, "name");
                        String prop = p.getAttributeValue(null, "property");
                        if ("calibre:series".equals(name)) m.series = clean(p.getAttributeValue(null, "content"));
                        else if ("calibre:series_index".equals(name)) m.index = number(p.getAttributeValue(null, "content"));
                        else if ("belongs-to-collection".equals(prop) && collection.length() == 0) collection = clean(p.nextText());
                        else if ("group-position".equals(prop)) position = number(p.nextText());
                    }
                }
            } finally {
                in.close();
            }
            if (m.series.length() == 0 && collection.length() > 0) {
                m.series = collection;
                if (m.index < 0) m.index = position;
            }
        } finally {
            z.close();
        }
    }

    private static int u16(byte[] b, int o) {
        return ((b[o] & 0xFF) << 8) | (b[o + 1] & 0xFF);
    }

    private static long u32(byte[] b, int o) {
        return ((long) u16(b, o) << 16) | u16(b, o + 2);
    }

    /** The first record of a mobi file holds the title and the EXTH block (author 100, subject 105, updated title 503). */
    private static void readMobi(File f, Meta m) throws Exception {
        RandomAccessFile r = new RandomAccessFile(f, "r");
        try {
            r.seek(76);
            int n = r.readUnsignedShort();
            if (n < 1) return;
            r.seek(78);
            int o0 = r.readInt();
            r.skipBytes(4);
            int o1 = n > 1 ? r.readInt() : (int) r.length();
            int len = o1 - o0;
            if (o0 < 0 || len < 132 || len > (1 << 20)) return;
            byte[] h = new byte[len];
            r.seek(o0);
            r.readFully(h);
            if (h[16] != 'M' || h[17] != 'O' || h[18] != 'B' || h[19] != 'I') return;
            long hlen = u32(h, 20);
            Charset cs = Charset.forName(u32(h, 28) == 65001 ? "UTF-8" : "windows-1252");
            long no = u32(h, 84), nl = u32(h, 88);
            if (no > 0 && nl > 0 && no + nl <= h.length) m.title = clean(new String(h, (int) no, (int) nl, cs));
            if ((u32(h, 128) & 0x40) != 0) {
                int e = (int) (16 + hlen);
                if (e + 12 <= h.length && h[e] == 'E' && h[e + 1] == 'X' && h[e + 2] == 'T' && h[e + 3] == 'H') {
                    long cnt = u32(h, e + 8);
                    int p = e + 12;
                    for (long k = 0; k < cnt && p + 8 <= h.length; k++) {
                        long type = u32(h, p), l = u32(h, p + 4);
                        if (l < 8 || p + l > h.length) break;
                        String s = clean(new String(h, p + 8, (int) l - 8, cs));
                        if (type == 100 && m.author.length() == 0) m.author = s;
                        else if (type == 503 && s.length() > 0) m.title = s;
                        else if (type == 105) for (String t : s.split("[;,]")) addTag(m, t);
                        p += (int) l;
                    }
                }
            }
        } finally {
            r.close();
        }
    }

    /** A kfx file: the title and the author from the metadata fragment. */
    private static void readKfx(File f, Meta m) throws Exception {
        String[] t = KfxSource.info(f);
        m.title = clean(t[0]);
        m.author = clean(t[1]);
    }

    /** A text file has no tags: only an author line ("Author: x") in the first few KB is looked for. */
    private static void readTxt(File f, Meta m) throws Exception {
        byte[] b = new byte[8192];
        int n = 0;
        InputStream in = new FileInputStream(f);
        try {
            int k;
            while (n < b.length && (k = in.read(b, n, b.length - n)) > 0) n += k;
        } finally {
            in.close();
        }
        String s = null;
        if (n >= 2 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xFE) s = new String(b, 0, n, "UTF-16LE");
        else if (n >= 2 && (b[0] & 0xFF) == 0xFE && (b[1] & 0xFF) == 0xFF) s = new String(b, 0, n, "UTF-16BE");
        for (int cut = 0; s == null && cut < 4 && cut < n; cut++) {      // the end of the sample may cut a character in two
            try {
                s = Charset.forName("UTF-8").newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(b, 0, n - cut)).toString();
            } catch (Exception e) {
                s = null;
            }
        }
        if (s == null) s = new String(b, 0, n, "GB18030");
        Matcher a = AUTHOR.matcher(s.replace("\r\n", "\n").replace('\r', '\n'));
        if (a.find()) m.author = clean(a.group(1));
    }
}