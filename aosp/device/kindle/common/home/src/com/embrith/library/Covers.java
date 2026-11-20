/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * Covers: Book covers for the grid: the cover image of an epub, the first page of a pdf; cached as png under
 * library/covers; call from a worker thread. */
package com.embrith.library;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Book covers for the grid: the cover image of an epub, the first page of a pdf. Slow, so call it from a worker thread. The result is
 * cached as a png in /sdcard/Embrith/library/covers (key = path, time and size of the book, and the width); a book without a cover
 * leaves an empty ".none" file, so it is not opened again. null = no cover (the grid shows the format name instead).
 */
final class Covers {
    private static final File DIR = new File(LibStore.DIR, "covers");
    private static final int MAX_IMG = 8 * 1024 * 1024;

    private Covers() { }

    static Bitmap load(File book, int w, int h) {
        String ext = LibStore.ext(book.getName());
        boolean epub = ext.equals("epub"), comic = ComicFiles.isComic(book.getName());
        boolean mobi = ext.equals("mobi") || ext.equals("azw") || ext.equals("azw3") || ext.equals("prc");
        if (!epub && !comic && !mobi && !ext.equals("pdf")) return null;
        String key = Integer.toHexString((book.getPath() + "|" + book.lastModified() + "|" + book.length()).hashCode()) + "_" + w;
        File png = new File(DIR, key + ".png");
        File none = new File(DIR, key + ".none");
        if (none.exists()) return null;
        if (png.isFile()) {
            Bitmap c = BitmapFactory.decodeFile(png.getPath());
            if (c != null) return c;
        }
        Bitmap b;
        try {
            b = epub ? fromEpub(book, w, h) : comic ? fromComic(book, w, h) : mobi ? fromMobi(book, w, h) : fromPdf(book, w);
        } catch (Throwable t) {
            b = null;   // a broken file, or no pdf renderer in the image: no cover
        }
        DIR.mkdirs();
        try {
            if (b == null) {
                none.createNewFile();
            } else {
                FileOutputStream o = new FileOutputStream(png);
                try {
                    b.compress(Bitmap.CompressFormat.PNG, 100, o);
                } finally {
                    o.close();
                }
            }
        } catch (IOException e) {
            // the cache is not writable: the cover is simply made again next time
        }
        return b;
    }

    static String local(String name) {
        int i = name.indexOf(':');
        return i < 0 ? name : name.substring(i + 1);
    }

    private static String decode(String s) {
        try {
            return URLDecoder.decode(s.replace("+", "%2B"), "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    /** The zip path of href, which is relative to the folder of the opf file. */
    static String resolve(String opf, String href) {
        int s = opf.lastIndexOf('/');
        String path = (s < 0 ? "" : opf.substring(0, s + 1)) + decode(href);
        ArrayList<String> out = new ArrayList<String>();
        for (String x : path.split("/")) {
            if (x.length() == 0 || x.equals(".")) continue;
            if (x.equals("..")) {
                if (!out.isEmpty()) out.remove(out.size() - 1);
            } else {
                out.add(x);
            }
        }
        StringBuilder b = new StringBuilder();
        for (String x : out) {
            if (b.length() > 0) b.append('/');
            b.append(x);
        }
        return b.toString();
    }

    private static Bitmap fromEpub(File book, int w, int h) throws Exception {
        ZipFile z = new ZipFile(book);
        try {
            ZipEntry ce = z.getEntry("META-INF/container.xml");
            if (ce == null) return null;
            String opf = null;
            InputStream in = z.getInputStream(ce);
            try {
                XmlPullParser p = Xml.newPullParser();
                p.setInput(in, null);
                for (int t = p.getEventType(); t != XmlPullParser.END_DOCUMENT && opf == null; t = p.next()) {
                    if (t == XmlPullParser.START_TAG && local(p.getName()).equals("rootfile")) opf = p.getAttributeValue(null, "full-path");
                }
            } finally {
                in.close();
            }
            if (opf == null) return null;
            ZipEntry oe = z.getEntry(opf);
            if (oe == null) return null;

            Map<String, String> items = new HashMap<String, String>();
            String coverId = null, coverProp = null, guess = null;
            in = z.getInputStream(oe);
            try {
                XmlPullParser p = Xml.newPullParser();
                p.setInput(in, null);
                for (int t = p.getEventType(); t != XmlPullParser.END_DOCUMENT; t = p.next()) {
                    if (t != XmlPullParser.START_TAG) continue;
                    String n = local(p.getName());
                    if (n.equals("meta")) {
                        if ("cover".equals(p.getAttributeValue(null, "name"))) coverId = p.getAttributeValue(null, "content");
                    } else if (n.equals("item")) {
                        String id = p.getAttributeValue(null, "id");
                        String href = p.getAttributeValue(null, "href");
                        String mt = p.getAttributeValue(null, "media-type");
                        String pr = p.getAttributeValue(null, "properties");
                        if (id == null || href == null) continue;
                        items.put(id, href);
                        if (pr != null && (" " + pr + " ").contains(" cover-image ")) coverProp = href;
                        else if (guess == null && mt != null && mt.startsWith("image/") && (id + href).toLowerCase().contains("cover")) guess = href;
                    }
                }
            } finally {
                in.close();
            }
            String href = coverProp;
            if (href == null && coverId != null) href = items.get(coverId);
            if (href == null) href = guess;
            if (href == null) return null;
            ZipEntry img = z.getEntry(resolve(opf, href));
            if (img == null) return null;
            return decodeEntry(z, img, w, h);
        } finally {
            z.close();
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                if (out.size() > MAX_IMG) return null;
            }
            return out.toByteArray();
        } finally {
            in.close();
        }
    }

    /** The cover of a mobi book; a mobi comic without a marked cover shows its first page. */
    private static Bitmap fromMobi(File book, int w, int h) throws Exception {
        Bitmap b = decodeBytes(MobiPages.cover(book), w, h);
        return b != null || !ComicFiles.isComic(book) ? b : fromComic(book, w, h);
    }

    private static Bitmap fromComic(File book, int w, int h) throws Exception {
        ComicSource s = ComicFiles.open(book);
        try {
            return s.count() == 0 ? null : decodeBytes(s.read(0), w, h);
        } finally {
            s.close();
        }
    }

    private static Bitmap decodeEntry(ZipFile z, ZipEntry e, int w, int h) throws IOException {
        return decodeBytes(readAll(z.getInputStream(e)), w, h);
    }

    private static Bitmap decodeBytes(byte[] data, int w, int h) {
        if (data == null) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        int s = 1;
        while (o.outWidth / (s * 2) >= w && o.outHeight / (s * 2) >= h) s *= 2;
        o.inJustDecodeBounds = false;
        o.inSampleSize = s;
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap b = BitmapFactory.decodeByteArray(data, 0, data.length, o);
        if (b == null) return null;
        if (b.getWidth() > w) {
            Bitmap r = Bitmap.createScaledBitmap(b, w, Math.max(1, b.getHeight() * w / b.getWidth()), true);
            if (r != b) b.recycle();
            b = r;
        }
        return b;
    }

    private static Bitmap fromPdf(File book, int w) throws Exception {
        ParcelFileDescriptor fd = ParcelFileDescriptor.open(book, ParcelFileDescriptor.MODE_READ_ONLY);
        try {
            PdfRenderer r = new PdfRenderer(fd);
            try {
                if (r.getPageCount() < 1) return null;
                PdfRenderer.Page page = r.openPage(0);
                try {
                    int bh = Math.max(1, page.getHeight() * w / Math.max(1, page.getWidth()));
                    Bitmap b = Bitmap.createBitmap(w, bh, Bitmap.Config.ARGB_8888);
                    b.eraseColor(Color.WHITE);
                    page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    return b;
                } finally {
                    page.close();
                }
            } finally {
                r.close();
            }
        } finally {
            fd.close();
        }
    }
}