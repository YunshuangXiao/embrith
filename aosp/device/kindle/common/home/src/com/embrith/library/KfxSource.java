/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * KfxSource: A kfx book (bare CONT container, no DRM): the text is read from the content fragments in the order of the
 * sections, one chunk per section that has text. */
package com.embrith.library;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A kfx book as the Send to Kindle service makes it: a CONT container of ion fragments. The reading order (258) lists the sections (260),
 * a section points to storylines (259), and a storyline item points to a string of a content fragment (145). A section without text
 * (the cover) is left out; the chapter names come from the table of contents (391 with type 212), matched to the sections by the position
 * ids of the storyline items and of the position map (264). Images, styles and fonts are not read. A container with DRM or compression
 * (container info or entity header 410 / 411 not 0, or a DRMION payload) is refused. Fragments are read when needed; only the
 * string lengths of the content are kept.
 */
final class KfxSource implements BookSource {
    private static final int MAX_ENTITY = 32 * 1024 * 1024;
    private static final int T_CONTENT = 145, T_STORYLINE = 259, T_SECTION = 260, T_ORDER = 258, T_POSMAP = 264;
    private static final int T_NAV = 389, T_NAVCONT = 391, T_META = 490;
    private static final int NAV_TOC = 212;
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private final RandomAccessFile raf;
    private long hlen;
    private final HashMap<Long, long[]> byKey = new HashMap<Long, long[]>();   // (type << 32 | id) -> id, type, offset, length
    private String title, author;
    private int[][] refs;          // per chunk: pairs of (content id, string index)
    private long[] weights;
    private String[] titles;
    private int cachedId = -1;
    private String[] cached;

    KfxSource(File f) throws IOException {
        this(f, true);
    }

    private KfxSource(File f, boolean full) throws IOException {
        raf = new RandomAccessFile(f, "r");
        boolean ok = false;
        try {
            open();
            readInfo();
            if (full) build();
            ok = true;
        } finally {
            if (!ok) raf.close();
        }
    }

    /** True when the file is a CONT container (an azw file may be one). */
    static boolean sniff(File f) {
        try {
            RandomAccessFile r = new RandomAccessFile(f, "r");
            try {
                byte[] h = new byte[4];
                r.readFully(h);
                return h[0] == 'C' && h[1] == 'O' && h[2] == 'N' && h[3] == 'T';
            } finally {
                r.close();
            }
        } catch (IOException e) {
            return false;
        }
    }

    /** The title and the author (null when missing) without reading the text. */
    static String[] info(File f) throws IOException {
        KfxSource s = new KfxSource(f, false);
        try {
            return new String[] { s.title, s.author };
        } finally {
            s.close();
        }
    }

    // ---- container

    private static long le32(byte[] b, int i) {
        return (b[i] & 0xFFL) | (b[i + 1] & 0xFFL) << 8 | (b[i + 2] & 0xFFL) << 16 | (b[i + 3] & 0xFFL) << 24;
    }

    private static long le64(byte[] b, int i) {
        return le32(b, i) | le32(b, i + 4) << 32;
    }

    private static long key(int type, int id) {
        return ((long) type << 32) | (id & 0xFFFFFFFFL);
    }

    private byte[] readAt(long off, long len) throws IOException {
        if (off < 0 || len < 0 || len > MAX_ENTITY) throw new IOException("kfx 数据损坏");
        byte[] b = new byte[(int) len];
        raf.seek(off);
        raf.readFully(b);
        return b;
    }

    private static IOException drm() {
        return new IOException("这个 kfx 有 DRM 或压缩, 不支持");
    }

    private void open() throws IOException {
        byte[] h = readAt(0, 18);
        if (h[0] != 'C' || h[1] != 'O' || h[2] != 'N' || h[3] != 'T') throw new IOException("不是 kfx 文件");
        hlen = le32(h, 6);
        byte[] ci = readAt(le32(h, 10), le32(h, 14));
        Map<Integer, Object> info = map(parse(ci, 0, ci.length));
        if (info == null) throw new IOException("kfx 数据损坏");
        if (num(info.get(410), 0) != 0 || num(info.get(411), 0) != 0) throw drm();
        byte[] t = readAt(num(info.get(413), 18), num(info.get(414), 0));
        for (int i = 0; i + 24 <= t.length; i += 24) {
            long[] e = { le32(t, i), le32(t, i + 4), le64(t, i + 8), le64(t, i + 16) };
            byKey.put(key((int) e[1], (int) e[0]), e);
        }
    }

    private Object entity(long[] e) throws IOException {
        byte[] b = readAt(hlen + e[2], e[3]);
        int p = 0;
        if (b.length >= 10 && b[0] == 'E' && b[1] == 'N' && b[2] == 'T' && b[3] == 'Y') {
            int eh = (int) le32(b, 6);
            if (eh < 10 || eh > b.length) throw new IOException("kfx 数据损坏");
            Map<Integer, Object> ei = map(parse(b, 10, eh));
            if (ei != null && (num(ei.get(410), 0) != 0 || num(ei.get(411), 0) != 0)) throw drm();
            p = eh;
        }
        if (p < b.length && (b[p] & 0xFF) == 0xEA) throw drm();
        return parse(b, p, b.length);
    }

    private long[] first(int type) {
        for (long[] e : byKey.values()) if (e[1] == type) return e;
        return null;
    }

    private Object fragment(int type, int id) throws IOException {
        long[] e = byKey.get(key(type, id));
        return e == null ? null : entity(e);
    }

    // ---- book

    private void readInfo() throws IOException {
        long[] me = first(T_META);
        if (me == null) return;
        Map<Integer, Object> m = map(entity(me));
        if (m == null) return;
        for (Object c : list(m.get(491))) {
            Map<Integer, Object> cm = map(c);
            if (cm == null || !"kindle_title_metadata".equals(cm.get(495))) continue;
            for (Object kv : list(cm.get(258))) {
                Map<Integer, Object> e = map(kv);
                if (e == null || !(e.get(307) instanceof String)) continue;
                String k = String.valueOf(e.get(492)), v = (String) e.get(307);
                if (k.equals("title") && title == null) title = v;
                else if (k.equals("author")) author = author == null ? v : author + ", " + v;
            }
        }
    }

    private void build() throws IOException {
        long[] oe = first(T_ORDER);
        if (oe == null) throw new IOException("kfx 里没有阅读顺序");
        Map<Integer, Object> order = map(entity(oe));
        List<Object> secs = null;
        for (Object o : list(order == null ? null : order.get(169))) {
            Map<Integer, Object> om = map(o);
            if (om != null) {
                secs = list(om.get(170));
                break;
            }
        }
        if (secs == null) throw new IOException("kfx 里没有阅读顺序");

        ArrayList<int[]> chunks = new ArrayList<int[]>();
        HashMap<Integer, Integer> idToChunk = new HashMap<Integer, Integer>();
        HashMap<Integer, Integer> secToChunk = new HashMap<Integer, Integer>();
        for (Object so : secs) {
            int sid = symId(so);
            Map<Integer, Object> sm = map(fragment(T_SECTION, sid));
            if (sm == null) continue;
            ArrayList<Integer> r = new ArrayList<Integer>();
            ArrayList<Integer> ids = new ArrayList<Integer>();
            for (Object pg : list(sm.get(141))) {
                Map<Integer, Object> pm = map(pg);
                if (pm == null) continue;
                ids.add((int) num(pm.get(155), -1));
                Map<Integer, Object> lm = map(fragment(T_STORYLINE, symId(pm.get(176))));
                if (lm != null) walk(list(lm.get(146)), r, ids, 0);
            }
            if (r.isEmpty()) continue;
            int ci = chunks.size();
            int[] a = new int[r.size()];
            for (int i = 0; i < a.length; i++) a[i] = r.get(i);
            chunks.add(a);
            secToChunk.put(sid, ci);
            for (int id : ids) if (!idToChunk.containsKey(id)) idToChunk.put(id, ci);
        }
        if (chunks.isEmpty()) throw new IOException("kfx 里没有读到文字");
        refs = chunks.toArray(new int[0][]);

        long[] pe = first(T_POSMAP);     // section -> position ids: a single id, or [first id, count]
        if (pe != null) {
            for (Object s : list(entity(pe))) {
                Map<Integer, Object> sm = map(s);
                Integer ci = sm == null ? null : secToChunk.get(symId(sm.get(174)));
                if (ci == null) continue;
                for (Object p : list(sm.get(181))) {
                    Object id = p instanceof List ? (((List<?>) p).isEmpty() ? null : ((List<?>) p).get(0)) : p;
                    int v = (int) num(id, -1);
                    if (v >= 0 && !idToChunk.containsKey(v)) idToChunk.put(v, ci);
                }
            }
        }

        HashMap<Integer, int[]> lens = new HashMap<Integer, int[]>();
        weights = new long[refs.length];
        for (int i = 0; i < refs.length; i++) {
            for (int k = 0; k < refs[i].length; k += 2) {
                int[] l = lens.get(refs[i][k]);
                if (l == null && !lens.containsKey(refs[i][k])) {
                    String[] s = strings(refs[i][k]);
                    l = new int[s == null ? 0 : s.length];
                    for (int j = 0; j < l.length; j++) l[j] = s[j].length();
                    lens.put(refs[i][k], l);
                }
                if (l != null && refs[i][k + 1] >= 0 && refs[i][k + 1] < l.length) weights[i] += l[refs[i][k + 1]] + 1;
            }
        }
        cachedId = -1;
        cached = null;

        titles = new String[refs.length];
        long[] ne = first(T_NAV);
        if (ne != null) {
            for (Object c : list(entity(ne))) {
                Map<Integer, Object> cm = map(c);
                if (cm == null) continue;
                for (Object nid : list(cm.get(392))) {
                    Map<Integer, Object> nm = map(fragment(T_NAVCONT, symId(nid)));
                    if (nm != null && symId(nm.get(235)) == NAV_TOC) toc(list(nm.get(247)), idToChunk, 0);
                }
            }
        }
    }

    /** The storyline items: the ids of the positions, and the references to strings of the content fragments. */
    private void walk(List<Object> items, ArrayList<Integer> r, ArrayList<Integer> ids, int depth) {
        if (depth > 64) return;
        for (Object o : items) {
            Map<Integer, Object> m = map(o);
            if (m == null) continue;
            if (m.get(155) != null) ids.add((int) num(m.get(155), -1));
            Map<Integer, Object> c = map(m.get(145));
            if (c != null && symId(c.get(4)) >= 0) {
                r.add(symId(c.get(4)));
                r.add((int) num(c.get(403), -1));
            }
            walk(list(m.get(146)), r, ids, depth + 1);
        }
    }

    private void toc(List<Object> entries, HashMap<Integer, Integer> idToChunk, int depth) {
        if (depth > 16) return;
        for (Object o : entries) {
            Map<Integer, Object> m = map(o);
            if (m == null) continue;
            Map<Integer, Object> t = map(m.get(241)), target = map(m.get(246));
            Integer ci = target == null ? null : idToChunk.get((int) num(target.get(155), -1));
            if (ci != null && titles[ci] == null && t != null && t.get(244) instanceof String && ((String) t.get(244)).trim().length() > 0) {
                titles[ci] = ((String) t.get(244)).trim();
            }
            toc(list(m.get(247)), idToChunk, depth + 1);
        }
    }

    /** The strings of a content fragment (the last one read is kept). */
    private String[] strings(int id) throws IOException {
        if (id == cachedId) return cached;
        Map<Integer, Object> m = map(fragment(T_CONTENT, id));
        List<Object> l = list(m == null ? null : m.get(146));
        String[] s = new String[l.size()];
        for (int i = 0; i < s.length; i++) s[i] = l.get(i) instanceof String ? (String) l.get(i) : "";
        cachedId = id;
        cached = s;
        return s;
    }

    @Override public int count() { return refs.length; }

    @Override public CharSequence chunk(int i) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < refs[i].length; k += 2) {
            String[] s = strings(refs[i][k]);
            int j = refs[i][k + 1];
            if (s == null || j < 0 || j >= s.length) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(s[j]);
        }
        return sb.toString();
    }

    @Override public long weight(int i) { return weights[i]; }

    @Override public String chapterTitle(int i) { return titles[i]; }

    @Override public String bookTitle() { return title; }

    @Override public String author() { return author; }

    @Override public void close() {
        try {
            raf.close();
        } catch (IOException e) {
            // already closed
        }
    }

    // ---- ion (binary): struct -> Map<Integer, Object> (the field is the symbol id), list -> List, int -> Long, string -> String,
    // symbol -> Sym; the other types are skipped (null)

    private static final class Sym {
        final int id;

        Sym(int id) { this.id = id; }
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, Object> map(Object o) {
        return o instanceof Map ? (Map<Integer, Object>) o : null;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object o) {
        return o instanceof List ? (List<Object>) o : new ArrayList<Object>();
    }

    private static int symId(Object o) {
        return o instanceof Sym ? ((Sym) o).id : -1;
    }

    private static long num(Object o, long def) {
        return o instanceof Long ? (Long) o : def;
    }

    private static Object parse(byte[] b, int from, int to) throws IOException {
        try {
            return new Ion(b, from, to).top();
        } catch (RuntimeException e) {
            throw new IOException("kfx 数据损坏");
        }
    }

    private static final class Ion {
        private final byte[] b;
        private final int end;
        private int p;

        Ion(byte[] b, int from, int to) {
            this.b = b;
            this.p = from;
            this.end = to;
        }

        Object top() {
            if (end - p >= 4 && (b[p] & 0xFF) == 0xE0 && b[p + 1] == 1 && b[p + 2] == 0 && (b[p + 3] & 0xFF) == 0xEA) p += 4;
            while (p < end) {
                Object v = value();
                if (v != null) return v;
            }
            return null;
        }

        private int varuint() {
            int v = 0;
            while (true) {
                int c = b[p++] & 0xFF;
                v = (v << 7) | (c & 0x7F);
                if ((c & 0x80) != 0) return v;
            }
        }

        private long uint(int from, int to) {
            long v = 0;
            for (int i = from; i < to; i++) v = (v << 8) | (b[i] & 0xFF);
            return v;
        }

        private Object value() {
            int t = b[p++] & 0xFF, code = t >> 4, ln = t & 15;
            if (code == 1) return ln == 1 ? Boolean.TRUE : (ln == 0 ? Boolean.FALSE : null);
            if (ln == 15) return null;
            if (ln == 14 || (code == 13 && ln == 1)) ln = varuint();
            int q = p + ln;
            if (q > end) throw new IllegalStateException();
            Object out = null;
            switch (code) {
                case 2:
                    out = Long.valueOf(uint(p, q));
                    break;
                case 3:
                    out = Long.valueOf(-uint(p, q));
                    break;
                case 7:
                    out = new Sym((int) uint(p, q));
                    break;
                case 8:
                    out = new String(b, p, ln, UTF8);
                    break;
                case 11:
                case 12: {
                    ArrayList<Object> l = new ArrayList<Object>();
                    while (p < q) l.add(value());
                    out = l;
                    break;
                }
                case 13: {
                    HashMap<Integer, Object> m = new HashMap<Integer, Object>();
                    while (p < q) {
                        int k = varuint();
                        m.put(k, value());
                    }
                    out = m;
                    break;
                }
                case 14: {
                    int n = varuint();     // the annotation ids are skipped, the value is the one after them
                    p += n;
                    out = value();
                    break;
                }
                default:
                    break;
            }
            p = q;
            return out;
        }
    }
}