/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * EpubSource: An epub as chunks: one chunk for each spine item, turned into styled text; chunk names come from the
 * table of contents. */
package com.embrith.library;

import android.text.Html;
import android.text.SpannableStringBuilder;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * An epub: one chunk per item of the spine (the reading order of the opf file), turned into styled text with Html.fromHtml (images are
 * dropped). The names of the chunks come from the table of contents of the book (ncx, or the nav document of epub 3). When the table
 * points to several places inside one file (anchors), that file is cut at those places, one chunk for each; a spine file the table does not
 * list gets the name of the chunk before it with a mark for the rest of the chapter. A book whose table has fewer than two entries gets its
 * chapters from the heading lines of its text (TxtSource.headings: chapter words, numbered lines), a file cut at them when it holds several. The zip stays open while the book is read, a chunk is read from it when it is shown.
 */
final class EpubSource implements BookSource {
    private static final long MAX_ITEM = 8L * 1024 * 1024;
    private static final Pattern STRIP = Pattern.compile("<(head|style|script|svg)\\b.*?</\\1\\s*>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private final ZipFile zip;
    private String[] paths, names;   // for every chunk: the file, the name (from the table of contents)
    private int[] from, to;           // for every chunk: the part of the file (character offsets), -1 = the whole file
    private boolean[] textCut;        // the offsets are in the text of the rendered file, not in its html
    private String cachePath;         // the file rendered last (chunks cut from the text of one file are shown one after the other)
    private CharSequence cacheText;
    private long[] sizes;
    private String title, author;

    EpubSource(File f) throws IOException {
        zip = new ZipFile(f);
        try {
            load();
        } catch (IOException e) {
            zip.close();
            throw e;
        } catch (Exception e) {
            zip.close();
            throw new IOException("epub 文件格式不对");
        }
    }

    private void load() throws Exception {
        String opf = findOpf();
        ZipEntry oe = opf == null ? null : zip.getEntry(opf);
        if (oe == null) throw new IOException("epub 里找不到内容清单");

        Map<String, String[]> items = new HashMap<String, String[]>();   // id -> href, media type
        List<String> spine = new ArrayList<String>();
        String tocId = null, navHref = null;
        InputStream in = zip.getInputStream(oe);
        try {
            XmlPullParser p = Xml.newPullParser();
            p.setInput(in, null);
            for (int t = p.getEventType(); t != XmlPullParser.END_DOCUMENT; t = p.next()) {
                if (t != XmlPullParser.START_TAG) continue;
                String n = Covers.local(p.getName());
                if (n.equals("title") && title == null) {
                    title = p.nextText().trim();
                } else if (n.equals("creator") && author == null) {
                    author = p.nextText().trim();
                } else if (n.equals("item")) {
                    String id = p.getAttributeValue(null, "id"), href = p.getAttributeValue(null, "href");
                    if (id == null || href == null) continue;
                    items.put(id, new String[] { href, p.getAttributeValue(null, "media-type") });
                    String pr = p.getAttributeValue(null, "properties");
                    if (pr != null && (" " + pr + " ").contains(" nav ")) navHref = href;
                } else if (n.equals("spine")) {
                    tocId = p.getAttributeValue(null, "toc");
                } else if (n.equals("itemref")) {
                    String idref = p.getAttributeValue(null, "idref");
                    if (idref != null) spine.add(idref);
                }
            }
        } finally {
            in.close();
        }

        List<String> ps = new ArrayList<String>();
        List<Long> ss = new ArrayList<Long>();
        for (String id : spine) {
            String[] it = items.get(id);
            if (it == null || it[1] == null || !(it[1].startsWith("application/xhtml") || it[1].startsWith("text/html"))) continue;
            String path = Covers.resolve(opf, it[0]);
            ZipEntry e = zip.getEntry(path);
            if (e == null) continue;
            ps.add(path);
            ss.add(Math.max(1L, e.getSize()));
        }
        if (ps.isEmpty()) throw new IOException("这本书没有可读的内容");

        // the table of contents, in its own order: {file, fragment, label}; the ncx first (epub 2), the nav document when it gave nothing (epub 3)
        List<String[]> toc = new ArrayList<String[]>();
        String[] ncx = tocId == null ? null : items.get(tocId);
        if (ncx == null) {
            for (String[] it : items.values()) if (it[1] != null && it[1].contains("dtbncx")) { ncx = it; break; }
        }
        try {
            if (ncx != null) readNcx(Covers.resolve(opf, ncx[0]), toc);
        } catch (Exception e) {
            // a table that could not be read to the end: what was read stays
        }
        if (toc.isEmpty() && navHref != null) {
            try {
                readNav(Covers.resolve(opf, navHref), toc);
            } catch (Exception e) {
                // no names for the chapters
            }
        }

        // the chunks: one for each spine file, or, when the table points to several places inside one file (anchors), one for each of them
        List<String> cp = new ArrayList<String>(), cn = new ArrayList<String>();
        List<Integer> cf = new ArrayList<Integer>(), ct = new ArrayList<Integer>();
        List<Boolean> cx = new ArrayList<Boolean>();
        List<Long> cs = new ArrayList<Long>();
        String prev = null;
        boolean poor = toc.size() < 2;   // no usable table: the headings of the text itself
        for (int i = 0; i < ps.size(); i++) {
            String path = ps.get(i);
            List<String[]> es = new ArrayList<String[]>();
            for (String[] t : toc) if (t[0].equals(path)) es.add(t);
            if (es.size() >= 2) {
                String html = null;
                try { html = readText(path); } catch (IOException e) { html = null; }
                if (html != null) {
                    java.util.TreeMap<Integer, String> cuts = new java.util.TreeMap<Integer, String>();
                    for (String[] t : es) {
                        int off = t[1].length() == 0 ? 0 : anchorAt(html, t[1]);
                        if (off >= 0 && !cuts.containsKey(off)) cuts.put(off, t[2]);
                    }
                    if (cuts.size() >= 2) {
                        Integer[] at = cuts.keySet().toArray(new Integer[0]);
                        for (int k = 0; k < at.length; k++) {
                            int f = k == 0 ? 0 : at[k], e2 = k + 1 < at.length ? at[k + 1] : html.length();
                            cp.add(path);
                            cf.add(f);
                            ct.add(e2);
                            cx.add(false);
                            cs.add(Math.max(1L, (long) (e2 - f)));
                            prev = cuts.get(at[k]);
                            cn.add(prev);
                        }
                        continue;
                    }
                }
            }
            if (poor && es.isEmpty()) {
                String txt = null;
                try { txt = render(readText(path)).toString(); } catch (IOException e) { txt = null; }
                if (txt != null) {
                    ArrayList<Integer> hp = new ArrayList<Integer>();
                    ArrayList<String> hn = new ArrayList<String>();
                    TxtSource.headings(txt, hp, hn);
                    if (hp.size() >= 2) {
                        for (int k = 0; k < hp.size(); k++) {
                            int f = k == 0 ? 0 : hp.get(k), e2 = k + 1 < hp.size() ? hp.get(k + 1) : txt.length();
                            cp.add(path);
                            cf.add(f);
                            ct.add(e2);
                            cx.add(true);
                            cs.add(Math.max(1L, (long) (e2 - f)));
                            prev = hn.get(k);
                            cn.add(prev);
                        }
                        continue;
                    }
                    if (hp.size() == 1 && hp.get(0) <= 300) {
                        es.add(new String[] { path, "", hn.get(0) });   // one heading at the start: the name of the file
                    }
                }
            }
            cp.add(path);
            cf.add(-1);
            ct.add(-1);
            cx.add(false);
            cs.add(ss.get(i));
            if (!es.isEmpty()) prev = es.get(0)[2];
            else if (prev != null && !prev.endsWith(CONT)) prev = prev + CONT;   // a file the table does not list: the rest of the chapter before it
            cn.add(es.isEmpty() ? prev : es.get(0)[2]);
        }
        int n = cp.size();
        paths = cp.toArray(new String[0]);
        names = cn.toArray(new String[0]);
        from = new int[n];
        to = new int[n];
        textCut = new boolean[n];
        sizes = new long[n];
        for (int i = 0; i < n; i++) {
            from[i] = cf.get(i);
            to[i] = ct.get(i);
            textCut[i] = cx.get(i);
            sizes[i] = cs.get(i);
        }
    }

    private static final String CONT = " (\u7eed)";

    /** The file as text (UTF-8); an IOException when it is missing or too big. */
    private String readText(String path) throws IOException {
        ZipEntry e = zip.getEntry(path);
        if (e == null) throw new IOException("epub 里缺少 " + path);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        InputStream in = zip.getInputStream(e);
        try {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                if (out.size() > MAX_ITEM) throw new IOException("这一章太大");
            }
        } finally {
            in.close();
        }
        return new String(out.toByteArray(), "UTF-8");
    }

    /** Where the element with this id (or name) starts in the html: the "<" before the attribute; -1 when there is none. */
    private static int anchorAt(String html, String frag) {
        String[] tries = { frag, decodeFrag(frag) };
        for (String f : tries) {
            Matcher m = Pattern.compile("\\b(?:id|name)\\s*=\\s*[\"']" + Pattern.quote(f) + "[\"']").matcher(html);
            if (m.find()) {
                int lt = html.lastIndexOf('<', m.start());
                return lt >= 0 ? lt : m.start();
            }
        }
        return -1;
    }

    private static String decodeFrag(String f) {
        try { return java.net.URLDecoder.decode(f.replace("+", "%2B"), "UTF-8"); } catch (Exception e) { return f; }
    }

    /** The parser, knowing the few html entities that show up in the tables of epubs. */
    private static XmlPullParser parser(InputStream in) throws Exception {
        XmlPullParser p = Xml.newPullParser();
        p.setInput(in, null);
        try {
            String[][] ent = { { "nbsp", "\u00a0" }, { "mdash", "\u2014" }, { "ndash", "\u2013" }, { "hellip", "\u2026" }, { "ldquo", "\u201c" },
                { "rdquo", "\u201d" }, { "lsquo", "\u2018" }, { "rsquo", "\u2019" }, { "copy", "\u00a9" } };
            for (String[] e : ent) p.defineEntityReplacementText(e[0], e[1]);
        } catch (Exception e) {
            // the parser does not take the definitions: the standard entities only
        }
        return p;
    }

    private static void add(List<String[]> toc, String base, String src, String label) {
        int h = src.indexOf('#');
        String file = Covers.resolve(base, h < 0 ? src : src.substring(0, h)), frag = h < 0 ? "" : src.substring(h + 1);
        for (String[] t : toc) if (t[0].equals(file) && t[1].equals(frag)) return;
        toc.add(new String[] { file, frag, label });
    }

    private String findOpf() throws Exception {
        ZipEntry ce = zip.getEntry("META-INF/container.xml");
        if (ce == null) return null;
        InputStream in = zip.getInputStream(ce);
        try {
            XmlPullParser p = Xml.newPullParser();
            p.setInput(in, null);
            for (int t = p.getEventType(); t != XmlPullParser.END_DOCUMENT; t = p.next()) {
                if (t == XmlPullParser.START_TAG && Covers.local(p.getName()).equals("rootfile")) return p.getAttributeValue(null, "full-path");
            }
        } finally {
            in.close();
        }
        return null;
    }

    /** epub 2: navPoint / navLabel / text and content src. */
    private void readNcx(String ncx, List<String[]> toc) throws Exception {
        ZipEntry e = zip.getEntry(ncx);
        if (e == null) return;
        InputStream in = zip.getInputStream(e);
        try {
            XmlPullParser p = parser(in);
            boolean inLabel = false;
            String label = null;
            for (int t = p.getEventType(); t != XmlPullParser.END_DOCUMENT; t = p.next()) {
                if (t == XmlPullParser.START_TAG) {
                    String n = Covers.local(p.getName());
                    if (n.equals("navPoint")) {
                        label = null;
                    } else if (n.equals("navLabel")) {
                        inLabel = true;
                    } else if (n.equals("text") && inLabel && label == null) {
                        label = p.nextText().replaceAll("\\s+", " ").trim();
                    } else if (n.equals("content")) {
                        String src = p.getAttributeValue(null, "src");
                        if (label != null && label.length() > 0 && src != null) add(toc, ncx, src, label);
                        label = null;
                    }
                } else if (t == XmlPullParser.END_TAG && Covers.local(p.getName()).equals("navLabel")) {
                    inLabel = false;
                }
            }
        } finally {
            in.close();
        }
    }

    /** epub 3: the links of the nav element of type toc (the first nav element when none has that type). */
    private void readNav(String nav, List<String[]> toc) throws Exception {
        ZipEntry e = zip.getEntry(nav);
        if (e == null) return;
        InputStream in = zip.getInputStream(e);
        try {
            XmlPullParser p = parser(in);
            boolean inNav = false, isToc = false, inA = false;
            List<String[]> cur = new ArrayList<String[]>(), first = null;
            String href = null;
            StringBuilder sb = new StringBuilder();
            for (int t = p.getEventType(); t != XmlPullParser.END_DOCUMENT; t = p.next()) {
                if (t == XmlPullParser.START_TAG) {
                    String n = Covers.local(p.getName());
                    if (n.equals("nav")) {
                        inNav = true;
                        isToc = false;
                        cur = new ArrayList<String[]>();
                        for (int i = 0; i < p.getAttributeCount(); i++) {
                            if (Covers.local(p.getAttributeName(i)).equals("type") && p.getAttributeValue(i).contains("toc")) isToc = true;
                        }
                    } else if (n.equals("a") && inNav) {
                        inA = true;
                        href = p.getAttributeValue(null, "href");
                        sb.setLength(0);
                    }
                } else if (t == XmlPullParser.TEXT && inA) {
                    sb.append(p.getText());
                } else if (t == XmlPullParser.END_TAG) {
                    String n = Covers.local(p.getName());
                    if (n.equals("a") && inA) {
                        inA = false;
                        String label = sb.toString().replaceAll("\\s+", " ").trim();
                        if (href != null && label.length() > 0 && !href.startsWith("#")) add(cur, nav, href, label);
                    } else if (n.equals("nav") && inNav) {
                        inNav = false;
                        if (isToc) {
                            toc.addAll(cur);
                            return;
                        }
                        if (first == null) first = cur;
                    }
                }
            }
            if (first != null) toc.addAll(first);
        } finally {
            in.close();
        }
    }

    @Override public int count() { return paths.length; }

    @Override public CharSequence chunk(int i) throws IOException {
        if (textCut[i]) {
            if (!paths[i].equals(cachePath)) {
                cacheText = render(readText(paths[i]));
                cachePath = paths[i];
            }
            int len = cacheText.length();
            return cacheText.subSequence(Math.min(from[i], len), Math.min(Math.max(from[i], to[i]), len));
        }
        String html = readText(paths[i]);
        if (from[i] >= 0) html = html.substring(Math.min(from[i], html.length()), Math.min(Math.max(from[i], to[i]), html.length()));
        return render(html);
    }

    /** Html (a chapter of an epub or of a mobi) to styled text; images, the head and scripts are dropped. */
    static CharSequence render(String html) {
        html = STRIP.matcher(html).replaceAll("");
        SpannableStringBuilder b = new SpannableStringBuilder(Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT));
        for (int k = b.length() - 1; k >= 0; k--) {
            char c = b.charAt(k);
            if (c == '\uFFFC' || c == '\uFEFF') b.delete(k, k + 1);
        }
        while (b.length() > 0 && Character.isWhitespace(b.charAt(0))) b.delete(0, 1);
        while (b.length() > 0 && Character.isWhitespace(b.charAt(b.length() - 1))) b.delete(b.length() - 1, b.length());
        return b;
    }

    @Override public long weight(int i) { return sizes[i]; }

    @Override public String chapterTitle(int i) { return names[i]; }

    @Override public String bookTitle() { return title; }

    @Override public String author() { return author; }

    @Override public void close() {
        try {
            zip.close();
        } catch (IOException e) {
            // closing only
        }
    }
}
