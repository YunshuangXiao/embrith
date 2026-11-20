/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * ReadTime: The time spent reading, kept per day in library/readlog.tsv. */
package com.embrith.library;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The time spent reading (the time the reader or the comic page was in front), kept per day:
 * /sdcard/Embrith/library/readlog.tsv, one line "yyyyMMdd TAB milliseconds TAB path" for each stay.
 * The totals of the older time.tsv ("milliseconds TAB path") count as the day "00000000" (earlier).
 * The screen time of the installed apps counts as reading time too: a line with "app:" and the package name in place of the path
 * (written by syncApps from the usage statistics of the system, which only keeps the last days).
 */
final class ReadTime {
    static final String EARLIER = "00000000";
    static final String APP = "app:";
    private static final File FILE = new File(LibStore.DIR, "readlog.tsv");
    private static final File OLD = new File(LibStore.DIR, "time.tsv");

    private ReadTime() { }

    /** All the records of the file, summed up. */
    static final class Data {
        final Map<String, Long> byDay = new TreeMap<String, Long>();                  // day -> ms
        final Map<String, Long> byBook = new HashMap<String, Long>();                 // path -> ms
        final Map<String, Map<String, Long>> dayBooks = new HashMap<String, Map<String, Long>>();   // day -> path -> ms
        long total;

        private void put(String day, String path, long ms) {
            sum(byDay, day, ms);
            sum(byBook, path, ms);
            Map<String, Long> m = dayBooks.get(day);
            if (m == null) dayBooks.put(day, m = new TreeMap<String, Long>());
            sum(m, path, ms);
            total += ms;
        }

        long day(String day) {
            Long v = byDay.get(day);
            return v == null ? 0 : v;
        }

        /** The time of one key (a book path or "app:" and a package) on a day. */
        long dayKey(String day, String key) {
            Map<String, Long> m = dayBooks.get(day);
            Long v = m == null ? null : m.get(key);
            return v == null ? 0 : v;
        }

        /** The sum of the days from..to (both included, as yyyyMMdd). */
        long range(String from, String to) {
            long s = 0;
            for (Map.Entry<String, Long> e : byDay.entrySet()) {
                if (e.getKey().compareTo(from) >= 0 && e.getKey().compareTo(to) <= 0) s += e.getValue();
            }
            return s;
        }

        /** The days a book was read, day -> ms. */
        Map<String, Long> daysOf(String path) {
            Map<String, Long> out = new TreeMap<String, Long>();
            for (Map.Entry<String, Map<String, Long>> e : dayBooks.entrySet()) {
                Long v = e.getValue().get(path);
                if (v != null) out.put(e.getKey(), v);
            }
            return out;
        }
    }

    private static void sum(Map<String, Long> m, String k, long ms) {
        Long v = m.get(k);
        m.put(k, (v == null ? 0 : v) + ms);
    }

    static boolean isApp(String key) {
        return key.startsWith(APP);
    }

    /** The installed apps that are not part of the system, as {package, name}, by name (the apps syncApps counts). */
    static List<String[]> userApps(Context c) {
        List<String[]> out = new java.util.ArrayList<String[]>();
        for (ApplicationInfo ai : c.getPackageManager().getInstalledApplications(0)) {
            if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0 || ai.packageName.equals(c.getPackageName())) continue;
            out.add(new String[] { ai.packageName, name(c, APP + ai.packageName) });
        }
        java.util.Collections.sort(out, new java.util.Comparator<String[]>() {
            @Override public int compare(String[] a, String[] b) { return a[1].compareToIgnoreCase(b[1]); }
        });
        return out;
    }

    /** What a key is called: the app name for an app, the file name without the extension for a book. */
    static String name(Context c, String key) {
        if (isApp(key)) {
            String pkg = key.substring(APP.length());
            try {
                PackageManager pm = c.getPackageManager();
                // the name of the launcher entry, as the desktop shows it; the label of the application is the package name when it has none
                android.content.Intent in = pm.getLaunchIntentForPackage(pkg);
                android.content.pm.ResolveInfo ri = in == null ? null : pm.resolveActivity(in, 0);
                if (ri != null) {
                    String s = String.valueOf(ri.loadLabel(pm));
                    if (s.length() > 0 && !s.equals(pkg)) return s;
                }
                return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
            } catch (PackageManager.NameNotFoundException e) {
                return pkg;
            }
        }
        String n = new File(key).getName();
        int i = n.lastIndexOf('.');
        return i > 0 ? n.substring(0, i) : n;
    }

    /**
     * Writes the screen time of the installed apps of the last days (what the system still keeps) into the log, a day of an app once
     * (the longest value seen wins, as the system may already have dropped part of an old day). Slow: not on the UI thread.
     * Returns true when the log changed.
     */
    static synchronized boolean syncApps(Context c) {
        PackageManager pm = c.getPackageManager();
        SimpleDateFormat f = new SimpleDateFormat("yyyyMMdd", Locale.US);
        Map<String, Long> fresh = new TreeMap<String, Long>();   // "day TAB path" -> ms
        // the days before today, from the events cut at local midnight and without the time the Kindle slept (the system buckets would count it)
        for (int d = 1; d <= 7; d++) {
            Calendar from = Calendar.getInstance();
            from.setTimeInMillis(com.embrith.home.Usage.dayStart());
            from.add(Calendar.DAY_OF_YEAR, -d);
            Calendar to = (Calendar) from.clone();
            to.add(Calendar.DAY_OF_YEAR, 1);
            for (Map.Entry<String, Long> en : com.embrith.home.Usage.range(c, from.getTimeInMillis(), to.getTimeInMillis()).entrySet()) {
                long ms = en.getValue();
                String pkg = en.getKey();
                if (ms < 1000 || pkg.equals(c.getPackageName())) continue;
                try {
                    if ((pm.getApplicationInfo(pkg, 0).flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
                } catch (PackageManager.NameNotFoundException e) {
                    continue;
                }
                fresh.put(f.format(from.getTime()) + "\t" + APP + pkg, ms);
            }
        }
        // today comes from the events cut at local midnight (the same as the screen time widget), not from the system buckets
        String today = today();
        Map<String, Long> todayMs = new TreeMap<String, Long>();
        for (Map.Entry<String, Long> en : com.embrith.home.Usage.today(c).entrySet()) {
            String pkg = en.getKey();
            if (en.getValue() < 1000 || pkg.equals(c.getPackageName())) continue;
            try {
                if ((pm.getApplicationInfo(pkg, 0).flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
            } catch (PackageManager.NameNotFoundException e) {
                continue;
            }
            todayMs.put(today + "\t" + APP + pkg, en.getValue());
        }
        boolean haveToday = !todayMs.isEmpty();
        if (haveToday) {
            java.util.Iterator<String> it = fresh.keySet().iterator();
            while (it.hasNext()) if (it.next().startsWith(today + "\t")) it.remove();
        }
        if (fresh.isEmpty() && !haveToday) return false;
        List<String> keep = new java.util.ArrayList<String>();
        Map<String, Long> have = new TreeMap<String, Long>();
        for (String line : LibStore.readLines(FILE)) {
            String[] p = line.split("\t", 3);
            if (p.length == 3 && p[2].startsWith(APP)) have.put(p[0] + "\t" + p[2], parse(p[1]));
            else keep.add(line);
        }
        boolean changed = false;
        if (haveToday) {   // the value of today is replaced, not only raised
            java.util.Iterator<Map.Entry<String, Long>> it = have.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Long> e = it.next();
                if (e.getKey().startsWith(today + "\t") && !todayMs.containsKey(e.getKey())) { it.remove(); changed = true; }
            }
            for (Map.Entry<String, Long> e : todayMs.entrySet()) {
                Long old = have.get(e.getKey());
                if (old == null || !old.equals(e.getValue())) { have.put(e.getKey(), e.getValue()); changed = true; }
            }
        }
        for (Map.Entry<String, Long> e : fresh.entrySet()) {
            Long old = have.get(e.getKey());
            if (old == null || old < e.getValue()) {
                have.put(e.getKey(), e.getValue());
                changed = true;
            }
        }
        if (!changed) return false;
        try {
            LibStore.DIR.mkdirs();
            File tmp = new File(LibStore.DIR, "readlog.tmp");
            Writer w = new OutputStreamWriter(new FileOutputStream(tmp), "UTF-8");
            try {
                for (String line : keep) w.write(line + "\n");
                for (Map.Entry<String, Long> e : have.entrySet()) {
                    int t = e.getKey().indexOf('\t');
                    w.write(e.getKey().substring(0, t) + "\t" + e.getValue() + "\t" + e.getKey().substring(t + 1) + "\n");
                }
            } finally {
                w.close();
            }
            return tmp.renameTo(FILE);
        } catch (IOException e) {
            return false;
        }
    }

    static String today() {
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
    }

    static synchronized Data load() {
        Data d = new Data();
        for (String line : LibStore.readLines(OLD)) {
            int t = line.indexOf('\t');
            if (t > 0) d.put(EARLIER, line.substring(t + 1), parse(line.substring(0, t)));
        }
        for (String line : LibStore.readLines(FILE)) {
            String[] p = line.split("\t", 3);
            if (p.length == 3 && p[0].length() == 8) d.put(p[0], p[2], parse(p[1]));
        }
        return d;
    }

    private static long parse(String s) {
        try {
            return Math.max(0, Long.parseLong(s));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static long get(File book) {
        Long v = load().byBook.get(book.getPath());
        return v == null ? 0 : v;
    }

    /** Adds the time one book was in front, to the day it ends on (less than a second is not counted). */
    static synchronized void add(File book, long ms) {
        if (ms < 1000) return;
        try {
            LibStore.DIR.mkdirs();
            Writer w = new OutputStreamWriter(new FileOutputStream(FILE, true), "UTF-8");
            try {
                w.write(today() + "\t" + ms + "\t" + book.getPath() + "\n");
            } finally {
                w.close();
            }
        } catch (IOException e) {
            // the time is only a convenience
        }
    }

    /** "1h 05m", "35m" or "<1m". */
    static String fmt(long ms) {
        long m = ms / 60000;
        if (m >= 60) return (m / 60) + "h " + (m % 60 < 10 ? "0" : "") + (m % 60) + "m";
        return m > 0 ? m + "m" : (ms > 0 ? "<1m" : "0");
    }
}