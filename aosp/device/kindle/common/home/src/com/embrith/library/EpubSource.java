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
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * An epub: one chunk per item of the spine (the reading order of the opf file), turned into styled text with Html.fromHtml (images are
 * dropped). The names of the chunks come from the table of contents of the book (ncx, or the nav document of epub 3); a spine item the
 * table does not name has no name. The zip stays open while the book is read, a chunk is read from it when it is shown.
 */
final class EpubSource implements BookSource {
    private static final long MAX_ITEM = 8L * 1024 * 1024;
    private static final Pattern STRIP = Pattern.compile("<(head|style|script|svg)\\b.*?</\\1\\s*>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private final ZipFile zip;
    private String[] paths, names;
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
        paths = ps.toArray(new String[0]);
        sizes = new long[ps.size()];
        for (int i = 0; i < sizes.length; i++) sizes[i] = ss.get(i);

        Map<String, String> toc = new HashMap<String, String>();
        try {
            String[] ncx = tocId == null ? null : items.get(tocId);
            if (ncx != null) readNcx(Covers.resolve(opf, ncx[0]), toc);
            else if (navHref != null) readNav(Covers.resolve(opf, navHref), toc);
        } catch (Exception e) {
            // no names for the chapters
        }
        names = new String[paths.length];
        for (int i = 0; i < names.length; i++) names[i] = toc.get(paths[i]);
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

    private static String file(String src) {
        int h = src.indexOf('#');
        return h < 0 ? src : src.substring(0, h);
    }

    /** epub 2: navPoint / navLabel / text and content src. */
    private void readNcx(String ncx, Map<String, String> toc) throws Exception {
        ZipEntry e = zip.getEntry(ncx);
        if (e == null) return;
        InputStream in = zip.getInputStream(e);
        try {
            XmlPullParser p = Xml.newPullParser();
            p.setInput(in, null);
            boolean inLabel = false;
            String label = null;
            for (int t = p.getEventType(); t != XmlPullParser.END_DOCUMENT; t = p.next()) {
                if (t == XmlPullParser.START_TAG) {
                    String n = Covers.local(p.getName());
                    if (n.equals("navLabel")) {
                        inLabel = true;
                    } else if (n.equals("text") && inLabel) {
                        label = p.nextText().trim();
                    } else if (n.equals("content")) {
                        String src = p.getAttributeValue(null, "src");
                        if (label != null && label.length() > 0 && src != null) {
                            String key = Covers.resolve(ncx, file(src));
                            if (!toc.containsKey(key)) toc.put(key, label);
                        }
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

    /** epub 3: the links of the nav element of type toc. */
    private void readNav(String nav, Map<String, String> toc) throws Exception {
        ZipEntry e = zip.getEntry(nav);
        if (e == null) return;
        InputStream in = zip.getInputStream(e);
        try {
            XmlPullParser p = Xml.newPullParser();
            p.setInput(in, null);
            boolean inToc = false, inA = false;
            String href = null;
            StringBuilder sb = new StringBuilder();
            for (int t = p.getEventType(); t != XmlPullParser.END_DOCUMENT; t = p.next()) {
                if (t == XmlPullParser.START_TAG) {
                    String n = Covers.local(p.getName());
                    if (n.equals("nav")) {
                        for (int i = 0; i < p.getAttributeCount(); i++) {
                            if (Covers.local(p.getAttributeName(i)).equals("type") && p.getAttributeValue(i).contains("toc")) inToc = true;
                        }
                    } else if (n.equals("a") && inToc) {
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
                        if (href != null && label.length() > 0) {
                            String key = Covers.resolve(nav, file(href));
                            if (!toc.containsKey(key)) toc.put(key, label);
                        }
                    } else if (n.equals("nav")) {
                        inToc = false;
                    }
                }
            }
        } finally {
            in.close();
        }
    }

    @Override public int count() { return paths.length; }

    @Override public CharSequence chunk(int i) throws IOException {
        ZipEntry e = zip.getEntry(paths[i]);
        if (e == null) throw new IOException("epub 里缺少 " + paths[i]);
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
        return render(new String(out.toByteArray(), "UTF-8"));
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
