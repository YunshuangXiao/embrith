/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * LibStore: Library data: which files are listed, and the files the reader keeps under /sdcard/Embrith/library
 * (recent.tsv). */
package com.embrith.library;

import com.embrith.home.Prefs;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The library data: which files are listed, and the files the reader keeps in /sdcard/Embrith/library/
 * (recent.tsv: one line per book, "time-in-ms TAB path", newest last).
 */
final class LibStore {
    static final File ROOT = new File("/sdcard");
    static final File DIR = new File(ROOT, "Embrith/library");
    private static final String[] EXT = { "txt", "epub", "mobi", "azw3", "azw", "kfx", "cbz", "cbt", "cbr", "cb7" };
    private static final File FOLDERS = new File(DIR, "folders.txt");   // one folder per line (relative to ROOT); a first line "#locked" from older versions is ignored

    private LibStore() { }

    /** Never listed: the "<book>.sdr" folders where KOReader keeps its reading state. */
    static boolean hidden(String name) {
        return name.toLowerCase().endsWith(".sdr");
    }

    /** Not listed: the .sdr folders, and hidden files and folders (names that start with a dot) unless the setting "show hidden files" is on. */
    static boolean hidden(Context c, String name) {
        return hidden(name) || (name.startsWith(".") && !Prefs.showHidden(c));
    }

    static String ext(String name) {
        int i = name.lastIndexOf('.');
        return i < 0 ? "" : name.substring(i + 1).toLowerCase();
    }

    static boolean isBook(String name) {
        String e = ext(name);
        for (String x : EXT) if (x.equals(e)) return true;
        return false;
    }

    /**
     * The folders and books in dir, in the order of the library settings (lib_sort: name, natural, pinyin, date, size, type or read; lib_rev: reversed;
     * lib_dirfirst: folders before books, default on); null when the folder cannot be read.
     */
    static List<File> list(Context ctx, File dir) {
        File[] files = dir.listFiles();
        if (files == null) return null;
        final String by = Prefs.getStr(ctx, "lib_sort", "name");
        final boolean rev = "1".equals(Prefs.getStr(ctx, "lib_rev", "0"));
        final boolean dirFirst = !"0".equals(Prefs.getStr(ctx, "lib_dirfirst", "1"));
        final Collator pinyin = Collator.getInstance(Locale.CHINA);
        final Map<String, Integer> rank = new HashMap<String, Integer>();   // "read": 0 = opened last
        if (by.equals("read")) {
            List<File> rec = recent();
            for (int i = 0; i < rec.size(); i++) rank.put(rec.get(i).getPath(), i);
        }
        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                if (dirFirst && a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                int r = 0;
                if (by.equals("natural")) r = natural(a.getName(), b.getName());
                else if (by.equals("pinyin")) r = pinyin.compare(a.getName(), b.getName());
                else if (by.equals("read")) r = rankOf(rank, a) - rankOf(rank, b);
                else if (by.equals("date")) r = Long.compare(b.lastModified(), a.lastModified());
                else if (by.equals("size")) r = Long.compare(b.length(), a.length());
                else if (by.equals("type")) r = ext(a.getName()).compareTo(ext(b.getName()));
                if (r == 0) r = a.getName().compareToIgnoreCase(b.getName());
                return rev ? -r : r;
            }
        });
        List<File> out = new ArrayList<File>();
        for (File f : files) {
            String n = f.getName();
            if (hidden(ctx, n)) continue;
            if (f.isDirectory() || isBook(n)) out.add(f);
        }
        return out;
    }

    /** Place of a book in the recent list (0 = opened last); folders and books never opened come after all of them. */
    private static int rankOf(Map<String, Integer> rank, File f) {
        Integer i = rank.get(f.getPath());
        return i == null ? Integer.MAX_VALUE / 2 : i;
    }

    /** Compares names ignoring case, with runs of digits compared as numbers (2 before 10). */
    static int natural(String a, String b) {
        int i = 0, j = 0;
        while (i < a.length() && j < b.length()) {
            char x = a.charAt(i), y = b.charAt(j);
            if (Character.isDigit(x) && Character.isDigit(y)) {
                int si = i, sj = j;
                while (i < a.length() && Character.isDigit(a.charAt(i))) i++;
                while (j < b.length() && Character.isDigit(b.charAt(j))) j++;
                String p = a.substring(si, i).replaceFirst("^0+", ""), q = b.substring(sj, j).replaceFirst("^0+", "");
                if (p.length() != q.length()) return p.length() < q.length() ? -1 : 1;
                int c = p.compareTo(q);
                if (c != 0) return c;
            } else {
                int c = Character.toLowerCase(x) - Character.toLowerCase(y);
                if (c != 0) return c;
                i++;
                j++;
            }
        }
        return (a.length() - i) - (b.length() - j);
    }

    /** Path of f relative to ROOT, "" for ROOT itself. */
    static String rel(File f) {
        String p = f.getPath();
        return p.length() <= ROOT.getPath().length() ? "" : p.substring(ROOT.getPath().length() + 1);
    }

    static List<String> readLines(File f) {
        List<String> out = new ArrayList<String>();
        if (!f.isFile()) return out;
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            try {
                String line;
                while ((line = r.readLine()) != null) if (line.length() > 0) out.add(line);
            } finally {
                r.close();
            }
        } catch (IOException e) {
            // unreadable: what was read so far
        }
        return out;
    }

    /** The chosen folders, relative to ROOT. */
    static List<String> folderRels() {
        List<String> l = readLines(FOLDERS);
        if (!l.isEmpty() && l.get(0).equals("#locked")) l.remove(0);
        return l;
    }

    /** True when d is one of the chosen folders or lies inside one. */
    static boolean inFolders(File d) {
        String r = rel(d);
        for (String p : folderRels()) if (r.equals(p) || r.startsWith(p + "/")) return true;
        return false;
    }

    /** The chosen folders that still exist. */
    static List<File> folders() {
        List<File> out = new ArrayList<File>();
        for (String p : folderRels()) {
            File f = new File(ROOT, p);
            if (f.isDirectory()) out.add(f);
        }
        return out;
    }

    static boolean saveFolders(List<String> rels) {
        try {
            DIR.mkdirs();
            Writer w = new OutputStreamWriter(new FileOutputStream(FOLDERS), "UTF-8");
            try {
                for (String p : rels) w.write(p + "\n");
            } finally {
                w.close();
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Puts book at the end of recent.tsv (newest last); the last 50 are kept. */
    static void touchRecent(File book) {
        File f = new File(DIR, "recent.tsv");
        List<String> keep = new ArrayList<String>();
        for (String line : readLines(f)) {
            int t = line.indexOf('\t');
            if (t >= 0 && !line.substring(t + 1).equals(book.getPath())) keep.add(line);
        }
        keep.add(System.currentTimeMillis() + "\t" + book.getPath());
        while (keep.size() > 50) keep.remove(0);
        try {
            DIR.mkdirs();
            Writer w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
            try {
                for (String line : keep) w.write(line + "\n");
            } finally {
                w.close();
            }
        } catch (IOException e) {
            // the list is only a convenience
        }
    }

    /** Recently opened books, newest first; books that are gone are skipped. */
    static List<File> recent() {
        List<File> out = new ArrayList<File>();
        File f = new File(DIR, "recent.tsv");
        if (!f.isFile()) return out;
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            try {
                String line;
                while ((line = r.readLine()) != null) {
                    int t = line.indexOf('\t');
                    if (t < 0) continue;
                    File b = new File(line.substring(t + 1));
                    if (b.isFile() && !hidden(b.getName()) && !out.contains(b)) out.add(b);
                }
            } finally {
                r.close();
            }
        } catch (IOException e) {
            // unreadable: show what was read so far
        }
        Collections.reverse(out);
        return out;
    }
}