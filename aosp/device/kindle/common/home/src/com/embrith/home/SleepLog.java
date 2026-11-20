/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * SleepLog: The periods in which the Kindle slept, kept so that the screen time does not count them. */
package com.embrith.home;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

/**
 * While the Kindle sleeps the container is frozen, so an app that was in front does not always get its "moved to background" event
 * before that. The wall clock keeps running, and the whole sleep would count as time in front. StatusService records when the sleep
 * began (ctl/sleeping) and ended (ctl/wake), and the screen time takes these periods out.
 * Stored in the preference "sleep_log" as "start-end,start-end,start-": a last entry without an end is a sleep that is still going on.
 */
public final class SleepLog {
    private static final String KEY = "sleep_log";
    private static final long KEEP_MS = 9L * 86400000L;

    private SleepLog() { }

    private static List<long[]> load(Context c) {
        List<long[]> l = new ArrayList<long[]>();
        String s = Prefs.getStr(c, KEY, "");
        for (String part : s.split(",")) {
            int i = part.indexOf('-');
            if (i <= 0) continue;
            try {
                long a = Long.parseLong(part.substring(0, i));
                String e = part.substring(i + 1);
                l.add(new long[] { a, e.length() == 0 ? 0 : Long.parseLong(e) });   // 0: not ended yet
            } catch (NumberFormatException x) {
                // a damaged entry is dropped
            }
        }
        return l;
    }

    private static void save(Context c, List<long[]> l, long now) {
        StringBuilder sb = new StringBuilder();
        for (long[] p : l) {
            if (p[1] != 0 && p[1] < now - KEEP_MS) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(p[0]).append('-').append(p[1] == 0 ? "" : String.valueOf(p[1]));
        }
        Prefs.putStr(c, KEY, sb.toString());
    }

    /** The Kindle goes to sleep at time t (ignored while a sleep is still open). */
    public static synchronized void begin(Context c, long t) {
        List<long[]> l = load(c);
        if (!l.isEmpty() && l.get(l.size() - 1)[1] == 0) return;
        l.add(new long[] { t, 0 });
        save(c, l, t);
    }

    /** The Kindle wakes up at time t (ignored when no sleep is open). */
    public static synchronized void end(Context c, long t) {
        List<long[]> l = load(c);
        if (l.isEmpty() || l.get(l.size() - 1)[1] != 0) return;
        l.get(l.size() - 1)[1] = t < l.get(l.size() - 1)[0] ? l.get(l.size() - 1)[0] : t;
        save(c, l, t);
    }

    /** A period that is already over (the time between the end of Android and its next start). */
    public static synchronized void addClosed(Context c, long a, long b) {
        if (b <= a) return;
        List<long[]> l = load(c);
        l.add(new long[] { a, b });
        save(c, l, b);
    }

    /** The periods as {start, end}; a sleep that is still going on ends at "now". */
    public static synchronized long[][] periods(Context c, long now) {
        List<long[]> l = load(c);
        long[][] out = new long[l.size()][];
        for (int i = 0; i < out.length; i++) out[i] = new long[] { l.get(i)[0], l.get(i)[1] == 0 ? now : l.get(i)[1] };
        return out;
    }
}