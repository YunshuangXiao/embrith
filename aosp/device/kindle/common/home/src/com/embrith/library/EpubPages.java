/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * EpubPages: Pages of a comic kept in an epub: the images of the spine, in reading order. */
package com.embrith.library;

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
 * The pages of a comic kept in an epub: the images of the spine, in reading order (a spine item is an image file, or a page of
 * markup that shows one or more images: img, or image inside svg). An epub counts as a comic when it has at least MIN_PAGES
 * images and at most one spine item in five carries text (TEXT_MIN chars or more); text in the pages of a comic is not shown.
 */
final class EpubPages implements ComicSource {
    private static final int MIN_PAGES = 5;
    private static final int TEXT_MIN = 300;
    private static final int MAX_IMG = 48 * 1024 * 1024;
    private static final long MAX_PAGE = 2L * 1024 * 1024;     // size of a page of markup that is read
    private static final Pattern IMG = Pattern.compile("<(?:img|image)\\b[^>]*?\\b(?:src|href)\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern HEAD = Pattern.compile("<(head|style|script)\\b.*?</\\1\\s*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TAG = Pattern.compile("<[^>]*>", Pattern.DOTALL);

    private final ZipFile zip;
    private final List<String> pages = new ArrayList<String>();   // zip paths of the images
    private int spineCount, textItems;

    private EpubPages(ZipFile zip) {
        this.zip = zip;
    }

    /** True when the file is a comic in epub format (reads the spine, not on the UI thread when the book is big). */
    static boolean matches(File f) {
        try {
            EpubPages p = open(f);
            if (p == null) return false;
            p.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** The comic in this file, or null when it is not one. */
    static EpubPages open(File f) throws IOException {
        ZipFile z = new ZipFile(f);
        EpubPages p = new EpubPages(z);
        boolean ok = false;
        try {
            p.load();
            ok = p.pages.size() >= MIN_PAGES && p.textItems * 5 <= p.spineCount;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("epub 文件格式不对");
        } finally {
            if (!ok) z.close();
        }
        return ok ? p : null;
    }

    private void load() throws Exception {
        String opf = findOpf();
        ZipEntry oe = opf == null ? null : zip.getEntry(opf);
        if (oe == null) throw new IOException("epub 里找不到内容清单");
        Map<String, String[]> items = new HashMap<String, String[]>();   // id -> href, media type
        List<String> spine = new ArrayList<String>();
        InputStream in = zip.getInputStream(oe);
        try {
            XmlPullParser p = Xml.newPullParser();
            p.setInput(in, null);
            for (int t = p.getEventType(); t != XmlPullParser.END_DOCUMENT; t = p.next()) {
                if (t != XmlPullParser.START_TAG) continue;
                String n = Covers.local(p.getName());
                if (n.equals("item")) {
                    String id = p.getAttributeValue(null, "id"), href = p.getAttributeValue(null, "href");
                    if (id != null && href != null) items.put(id, new String[] { href, p.getAttributeValue(null, "media-type") });
                } else if (n.equals("itemref")) {
                    String idref = p.getAttributeValue(null, "idref");
                    if (idref != null) spine.add(idref);
                }
            }
        } finally {
            in.close();
        }
        for (String id : spine) {
            String[] it = items.get(id);
            if (it == null || it[1] == null) continue;
            String path = Covers.resolve(opf, it[0]);
            if (it[1].startsWith("image/")) {
                if (zip.getEntry(path) != null) pages.add(path);
                spineCount++;
            } else if (it[1].startsWith("application/xhtml") || it[1].startsWith("text/html")) {
                spineCount++;
                scan(path);
            }
        }
    }

    /** The images shown by one page of markup, and whether the page has text. */
    private void scan(String path) throws IOException {
        ZipEntry e = zip.getEntry(path);
        if (e == null || e.getSize() > MAX_PAGE) return;
        String html = new String(readAll(zip.getInputStream(e), MAX_PAGE), "UTF-8");
        Matcher m = IMG.matcher(html);
        while (m.find()) {
            String img = Covers.resolve(path, m.group(1));
            if (zip.getEntry(img) != null) pages.add(img);
        }
        String text = TAG.matcher(HEAD.matcher(html).replaceAll("")).replaceAll("").replaceAll("\\s+", " ").trim();
        if (text.length() >= TEXT_MIN) textItems++;
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

    private static byte[] readAll(InputStream in, long max) throws IOException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                if (out.size() > max) throw new IOException("这一页太大");
            }
            return out.toByteArray();
        } finally {
            in.close();
        }
    }

    @Override public int count() { return pages.size(); }

    @Override public byte[] read(int i) throws IOException {
        ZipEntry e = zip.getEntry(pages.get(i));
        if (e == null) throw new IOException("epub 里缺少 " + pages.get(i));
        return readAll(zip.getInputStream(e), MAX_IMG);
    }

    @Override public void close() {
        try {
            zip.close();
        } catch (IOException e) {
            // closing only
        }
    }
}