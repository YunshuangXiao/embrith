/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * MobiSource: A mobi (or old azw) book: the PalmDB records are unpacked into one html text, which is cut into
 * chapters at the targets of the contents links. */
package com.embrith.library;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A mobi (or an old azw) book: the PalmDB records are unpacked (no compression or PalmDoc) into one html text, which is cut into
 * chapters at the targets of the links of the table of contents (a run of close links with a filepos), else at the page breaks; a
 * long chapter is cut again. KF8 (azw3 without the old part) is joined again from its skeleton and fragment tables (a page break marker
 * before each part, the chapter marks come from its ncx); DRM and HUFF/CDIC compression are not read. The text is kept as a string
 * with one char per byte, so the offsets of filepos stay the same; a chapter is decoded when it is shown.
 */
final class MobiSource implements BookSource {
    private static final int MAX_TEXT = 24 * 1024 * 1024;
    private static final int MIN_CHAPTER = 30;     // bytes
    private static final int HARD = 100 * 1024;    // a longer chapter is cut near every multiple of this
    private static final int NEAR = 400;           // links closer than this (bytes) belong to one table of contents
    private static final Pattern LINK = Pattern.compile("<a\\s+filepos=\"?0*(\\d{1,9})\"?[^>]*>(.*?)</a>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern TAGS = Pattern.compile("<[^>]*>");
    private static final Pattern IMG = Pattern.compile("recindex=\"?0*(\\d{1,6})", Pattern.CASE_INSENSITIVE);

    private final String raw;
    private Charset cs = Charset.forName("windows-1252");
    private static final Charset LATIN = Charset.forName("ISO-8859-1");
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final byte[] BREAK = "<mbp:pagebreak/>".getBytes(LATIN);

    private String title, author;
    private TreeMap<Integer, String> kf8marks;    // chapter marks of a KF8 book (position in the joined text -> name)
    private final int[] starts;
    private final String[] names;

    MobiSource(File f) throws IOException {
        byte[] text;
        RandomAccessFile r = new RandomAccessFile(f, "r");
        try {
            text = read(r);
        } finally {
            r.close();
        }
        raw = new String(text, "ISO-8859-1");
        int len = raw.length();

        List<int[]> links = new ArrayList<int[]>();    // target, start of the link, end of the link
        List<String> labels = new ArrayList<String>();
        Matcher m = LINK.matcher(raw);
        while (m.find()) {
            int pos = Integer.parseInt(m.group(1));
            if (pos <= 0 || pos >= len) continue;
            links.add(new int[] { pos, m.start(), m.end() });
            labels.add(m.group(2));
        }
        // the table of contents = the longest run of links that follow each other closely
        int bestA = 0, bestN = 0, a = 0;
        for (int i = 1; i <= links.size(); i++) {
            if (i == links.size() || links.get(i)[1] - links.get(i - 1)[2] > NEAR) {
                if (i - a > bestN) {
                    bestA = a;
                    bestN = i - a;
                }
                a = i;
            }
        }
        TreeMap<Integer, String> marks = new TreeMap<Integer, String>();
        if (kf8marks != null) {
            marks.putAll(kf8marks);
        } else if (bestN >= 3) {
            for (int i = bestA; i < bestA + bestN; i++) {
                int pos = snap(links.get(i)[0]);
                if (marks.containsKey(pos)) continue;
                String label = new String(TAGS.matcher(labels.get(i)).replaceAll("").getBytes("ISO-8859-1"), cs).trim();
                marks.put(pos, label.length() == 0 ? null : label);
            }
        }
        ArrayList<Integer> cp = new ArrayList<Integer>();
        ArrayList<String> cn = new ArrayList<String>();
        if (marks.size() >= 2) {
            List<Integer> ks = new ArrayList<Integer>(marks.keySet());
            for (int i = 0; i < ks.size(); i++) {
                int next = i + 1 < ks.size() ? ks.get(i + 1) : len;
                if (next - ks.get(i) >= MIN_CHAPTER) {
                    cp.add(ks.get(i));
                    cn.add(marks.get(ks.get(i)));
                }
            }
            if (!cp.isEmpty() && TAGS.matcher(raw.substring(0, cp.get(0))).replaceAll("").trim().length() >= 20) {
                cp.add(0, 0);
                cn.add(0, "开头");
            }
        }
        if (cp.size() < 2) {
            cp.clear();
            cn.clear();
            String low = raw.toLowerCase(Locale.US);
            int p = 0;
            while (p < len) {
                int q = low.indexOf("<mbp:pagebreak", p + 1);
                if (q < 0) q = len;
                if (q - p >= MIN_CHAPTER) {
                    cp.add(p);
                    cn.add(null);
                }
                p = q;
            }
        }
        ArrayList<Integer> sp = new ArrayList<Integer>();
        ArrayList<String> sn = new ArrayList<String>();
        for (int i = 0; i < cp.size(); i++) {
            int from = cp.get(i), to = i + 1 < cp.size() ? cp.get(i + 1) : len;
            sp.add(from);
            sn.add(cn.get(i));
            int p = from;
            while (to - p > 2 * HARD) {
                int q = raw.indexOf("</p>", p + HARD);
                if (q < 0 || q - p > HARD + 20000) q = raw.indexOf('>', p + HARD);
                if (q < 0 || q >= to - HARD) break;
                p = q + 1;
                sp.add(p);
                sn.add(null);
            }
        }
        sp.add(len);
        starts = new int[sp.size()];
        for (int i = 0; i < starts.length; i++) starts[i] = sp.get(i);
        names = sn.toArray(new String[0]);
        if (starts.length < 2) throw new IOException("这本书是空的");
    }

    /** A filepos should point at a tag; if it does not, go back to the start of the tag before it. */
    private int snap(int pos) {
        int q = pos;
        while (q > 0 && pos - q < 200 && raw.charAt(q) != '<') q--;
        return raw.charAt(q) == '<' ? q : pos;
    }

    private static int u16(byte[] b, int o) {
        return ((b[o] & 0xFF) << 8) | (b[o + 1] & 0xFF);
    }

    private static long u32(byte[] b, int o) {
        return ((long) u16(b, o) << 16) | u16(b, o + 2);
    }

    private static byte[] record(RandomAccessFile r, int[] off, int i) throws IOException {
        int n = off[i + 1] - off[i];
        if (n < 0 || off[i] < 0) throw new IOException("mobi 文件损坏");
        byte[] d = new byte[n];
        r.seek(off[i]);
        r.readFully(d);
        return d;
    }

    private byte[] read(RandomAccessFile r) throws IOException {
        r.seek(76);
        int n = r.readUnsignedShort();
        if (n < 2) throw new IOException("不是 mobi 文件");
        int[] off = new int[n + 1];
        r.seek(78);
        for (int i = 0; i < n; i++) {
            off[i] = r.readInt();
            r.skipBytes(4);
        }
        off[n] = (int) r.length();
        byte[] h = record(r, off, 0);
        if (h.length < 132 || h[16] != 'M' || h[17] != 'O' || h[18] != 'B' || h[19] != 'I') throw new IOException("不是 mobi 文件");
        int comp = u16(h, 0), nrec = u16(h, 8);
        if (u16(h, 12) != 0) throw new IOException("这本书有 DRM, 不能阅读");
        if (comp != 1 && comp != 2) throw new IOException("不支持这种压缩: " + comp);
        long hlen = u32(h, 20);
        boolean kf8 = u32(h, 36) >= 8;
        cs = Charset.forName(u32(h, 28) == 65001 ? "UTF-8" : "windows-1252");
        int flags = hlen >= 228 && h.length >= 244 ? u16(h, 242) : 0;

        long no = u32(h, 84), nl = u32(h, 88);
        if (no > 0 && nl > 0 && no + nl <= h.length) title = new String(h, (int) no, (int) nl, cs).trim();
        if ((u32(h, 128) & 0x40) != 0) {
            int e = (int) (16 + hlen);
            if (e + 12 <= h.length && h[e] == 'E' && h[e + 1] == 'X' && h[e + 2] == 'T' && h[e + 3] == 'H') {
                long cnt = u32(h, e + 8);
                int p = e + 12;
                for (long k = 0; k < cnt && p + 8 <= h.length; k++) {
                    long type = u32(h, p), l = u32(h, p + 4);
                    if (l < 8 || p + l > h.length) break;
                    String s = new String(h, p + 8, (int) l - 8, cs).trim();
                    if (type == 100 && author == null && s.length() > 0) author = s;
                    else if (type == 503 && s.length() > 0) title = s;
                    p += (int) l;
                }
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 1; i <= nrec && i < n; i++) {
            byte[] d = record(r, off, i);
            int end = d.length - trailing(d, flags);
            if (end <= 0) continue;
            if (comp == 2) {
                byte[] u = unpack(d, end);
                out.write(u, 0, u.length);
            } else {
                out.write(d, 0, end);
            }
            if (out.size() > MAX_TEXT) throw new IOException("文件太大");
        }
        return kf8 ? kf8Join(r, off, h, out.toByteArray()) : out.toByteArray();
    }

    /** One entry of an index: its label and the values of its tags (tag number to values). */
    private static final class Ent {
        String label;
        final HashMap<Integer, int[]> tag = new HashMap<Integer, int[]>();
    }

    /** A variable width integer, the last byte has the high bit set. */
    private static int vwi(byte[] d, int[] p) {
        int v = 0;
        while (true) {
            int b = d[p[0]++] & 0xFF;
            v = (v << 7) | (b & 0x7F);
            if ((b & 0x80) != 0) return v;
        }
    }

    /** One entry of an index record: d[a..] is the label (a length byte and the text), then the control bytes, then the tag values. */
    private static Ent kf8Entry(byte[] d, int a, List<int[]> tagx, int ctrl) {
        Ent e = new Ent();
        int ll = d[a] & 0xFF;
        e.label = new String(d, a + 1, ll, LATIN);
        int cp = a + 1 + ll;
        int[] p = { cp + ctrl };
        List<int[]> plan = new ArrayList<int[]>();    // tag, values per entry, entry count (-1: by byte size), byte size
        int ci = 0;
        for (int[] t : tagx) {
            if (t[3] == 1) {
                ci++;
                continue;
            }
            int mask = t[2], v = (d[cp + ci] & 0xFF) & mask;
            if (v == 0) continue;
            int count = -1, size = 0;
            if (v == mask) {
                if (Integer.bitCount(mask) > 1) size = vwi(d, p);
                else count = 1;
            } else {
                count = v >> Integer.numberOfTrailingZeros(mask);
            }
            plan.add(new int[] { t[0], t[1], count, size });
        }
        for (int[] t : plan) {
            ArrayList<Integer> vals = new ArrayList<Integer>();
            if (t[2] >= 0) {
                for (int k = 0; k < t[2] * t[1]; k++) vals.add(vwi(d, p));
            } else {
                int end = p[0] + t[3];
                while (p[0] < end) vals.add(vwi(d, p));
            }
            int[] v = new int[vals.size()];
            for (int k = 0; k < v.length; k++) v[k] = vals.get(k);
            e.tag.put(t[0], v);
        }
        return e;
    }

    /** The entries of the index whose first record is idx (none when idx is not a record); the cncx records after it go to cncx. */
    private static List<Ent> kf8Index(RandomAccessFile r, int[] off, long idx, List<byte[]> cncx) throws IOException {
        List<Ent> list = new ArrayList<Ent>();
        int n = off.length - 1;
        if (idx <= 0 || idx >= n) return list;
        int i0 = (int) idx;
        byte[] h = record(r, off, i0);
        if (h.length < 192 || h[0] != 'I' || h[1] != 'N' || h[2] != 'D' || h[3] != 'X') throw new IOException("KF8 索引损坏");
        int hl = (int) u32(h, 4), count = (int) u32(h, 24), ncncx = (int) u32(h, 52);
        int tp = hl;
        if (tp < 0 || tp + 12 > h.length || h[tp] != 'T' || h[tp + 1] != 'A' || h[tp + 2] != 'G' || h[tp + 3] != 'X') tp = (int) u32(h, 180);
        if (tp < 0 || tp + 12 > h.length || h[tp] != 'T' || h[tp + 1] != 'A' || h[tp + 2] != 'G' || h[tp + 3] != 'X') throw new IOException("KF8 索引损坏");
        int tl = (int) u32(h, tp + 4), ctrl = (int) u32(h, tp + 8);
        List<int[]> tagx = new ArrayList<int[]>();
        for (int p = tp + 12; p + 4 <= tp + tl && p + 4 <= h.length; p += 4) {
            tagx.add(new int[] { h[p] & 0xFF, h[p + 1] & 0xFF, h[p + 2] & 0xFF, h[p + 3] & 0xFF });
        }
        for (int k = 1; k <= count; k++) {
            if (i0 + k >= n) throw new IOException("KF8 索引损坏");
            byte[] d = record(r, off, i0 + k);
            if (d.length < 28 || d[0] != 'I' || d[1] != 'N' || d[2] != 'D' || d[3] != 'X') throw new IOException("KF8 索引损坏");
            int is = (int) u32(d, 20), ne = (int) u32(d, 24);
            if (is < 0 || ne < 0 || is + 4 + 2 * ne > d.length) throw new IOException("KF8 索引损坏");
            for (int e = 0; e < ne; e++) list.add(kf8Entry(d, u16(d, is + 4 + 2 * e), tagx, ctrl));
        }
        for (int k = count + 1; k <= count + ncncx && i0 + k < n; k++) cncx.add(record(r, off, i0 + k));
        return list;
    }

    /** A string of the cncx records: o is record * 0x10000 + the place in it; a length (vwi) and utf-8 text. */
    private static String cncxText(List<byte[]> c, int o) {
        byte[] d = c.get(o >> 16);
        int[] p = { o & 0xFFFF };
        int len = vwi(d, p);
        return new String(d, p[0], len, UTF8).trim();
    }

    /**
     * Joins the parts of a KF8 book: text is all the unpacked text records; the skeleton table says where each part (a skeleton, the
     * html without the bodies of its elements) is, and the fragment table says where each piece lies that goes into it (after the
     * skeleton, in the order of the table) and at which position of the part. The text after the last part (css, svg) is not used.
     */
    private byte[] kf8Join(RandomAccessFile r, int[] off, byte[] h, byte[] text) throws IOException {
        if (h.length < 264) throw new IOException("KF8 缺少索引");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<byte[]> cncx = new ArrayList<byte[]>();
        List<Integer> fragAt = new ArrayList<Integer>();    // where each fragment starts in the joined text
        try {
            List<Ent> skel = kf8Index(r, off, u32(h, 252), new ArrayList<byte[]>());
            List<Ent> frag = kf8Index(r, off, u32(h, 248), new ArrayList<byte[]>());
            if (!skel.isEmpty() && !skel.get(0).label.startsWith("SKEL")) {
                List<Ent> t = skel;
                skel = frag;
                frag = t;
            }
            if (skel.isEmpty()) throw new IOException("KF8 缺少索引");
            int fp = 0;
            for (Ent s : skel) {
                int[] g = s.tag.get(6), c = s.tag.get(1);
                int pos = g[0], len = g[1], cnt = c == null ? 0 : c[0];
                if (pos < 0 || len < 0 || pos + len > text.length) throw new IOException("KF8 索引损坏");
                byte[] part = Arrays.copyOfRange(text, pos, pos + len);
                int base = pos + len;
                int[] at = new int[cnt];
                for (int j = 0; j < cnt; j++, fp++) {
                    Ent f = frag.get(fp);
                    int ins = Integer.parseInt(f.label) - pos, fl = f.tag.get(6)[1];
                    if (ins < 0 || ins > part.length || fl < 0 || base + fl > text.length) throw new IOException("KF8 索引损坏");
                    byte[] np = new byte[part.length + fl];
                    System.arraycopy(part, 0, np, 0, ins);
                    System.arraycopy(text, base, np, ins, fl);
                    System.arraycopy(part, ins, np, ins + fl, part.length - ins);
                    part = np;
                    base += fl;
                    at[j] = ins;
                }
                out.write(BREAK, 0, BREAK.length);
                for (int j = 0; j < cnt; j++) fragAt.add(out.size() + at[j]);
                out.write(part, 0, part.length);
                if (out.size() > MAX_TEXT) throw new IOException("文件太大");
            }
        } catch (RuntimeException e) {
            throw new IOException("KF8 索引损坏");
        }
        // the table of contents (ncx): a position is a fragment and a place in it; a broken one only costs the chapter names
        try {
            TreeMap<Integer, String> marks = new TreeMap<Integer, String>();
            for (Ent e : kf8Index(r, off, u32(h, 244), cncx)) {
                int[] pf = e.tag.get(6), lb = e.tag.get(3);
                if (pf == null || pf.length < 2 || lb == null || pf[0] < 0 || pf[0] >= fragAt.size()) continue;
                int pos = fragAt.get(pf[0]) + pf[1];
                if (pos < 0 || pos >= out.size() || marks.containsKey(pos)) continue;
                String name = cncxText(cncx, lb[0]);
                marks.put(pos, name.length() == 0 ? null : name);
            }
            if (!marks.isEmpty()) kf8marks = marks;
        } catch (Exception e) {
            kf8marks = null;
        }
        return out.toByteArray();
    }

    /** The size of the extra data after the text of a record (the flags of the mobi header say which kinds there are). */
    private static int trailing(byte[] d, int flags) {
        int num = 0;
        for (int f = flags >> 1; f != 0; f >>= 1) {
            if ((f & 1) != 0) {
                int size = d.length - num, result = 0, bit = 0;
                while (size > 0) {
                    int v = d[size - 1] & 0xFF;
                    result |= (v & 0x7F) << bit;
                    bit += 7;
                    size--;
                    if ((v & 0x80) != 0 || bit >= 28) break;
                }
                num += result;
            }
        }
        if ((flags & 1) != 0 && d.length - num - 1 >= 0) num += (d[d.length - num - 1] & 0x3) + 1;
        return num;
    }

    /** PalmDoc LZ77. */
    private static byte[] unpack(byte[] in, int end) {
        byte[] o = new byte[8192];
        int op = 0, i = 0;
        while (i < end) {
            if (op + 16 > o.length) o = Arrays.copyOf(o, o.length * 2);
            int c = in[i++] & 0xFF;
            if (c == 0 || (c >= 9 && c <= 0x7F)) {
                o[op++] = (byte) c;
            } else if (c <= 8) {
                for (int k = 0; k < c && i < end; k++) o[op++] = in[i++];
            } else if (c >= 0xC0) {
                o[op++] = ' ';
                o[op++] = (byte) (c ^ 0x80);
            } else {
                if (i >= end) break;
                int v = (c << 8) | (in[i++] & 0xFF);
                int dist = (v >> 3) & 0x7FF, n = (v & 7) + 3;
                if (dist == 0 || dist > op) continue;
                for (int k = 0; k < n; k++, op++) o[op] = o[op - dist];
            }
        }
        return Arrays.copyOf(o, op);
    }

    /** The named chapters: names gets the name, the result the number (1 based, as in recindex of the html) of the first image at or after the chapter. */
    List<Integer> imageMarks(List<String> names2) {
        List<Integer> r = new ArrayList<Integer>();
        Matcher m = IMG.matcher(raw);
        for (int i = 0; i < names.length; i++) {
            if (names[i] == null || !m.find(starts[i])) continue;
            names2.add(names[i]);
            r.add(Integer.parseInt(m.group(1)));
        }
        return r;
    }

    @Override public int count() { return starts.length - 1; }

    @Override public CharSequence chunk(int i) throws IOException {
        return EpubSource.render(new String(raw.substring(starts[i], starts[i + 1]).getBytes("ISO-8859-1"), cs));
    }

    @Override public long weight(int i) { return starts[i + 1] - starts[i]; }

    @Override public String chapterTitle(int i) { return names[i]; }

    @Override public String bookTitle() { return title; }

    @Override public String author() { return author; }

    @Override public void close() { }
}
