/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * Usage: The screen time (time in front) of an app over a range, without the time the Kindle slept. */
package com.embrith.home;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/**
 * The screen time of today, from the start of the local day to now. The daily statistics of the system are buckets that do not start at
 * local midnight, and a query over a range adds up every bucket that overlaps it, whole. The events are cut exactly at the range instead.
 */
public final class Usage {
    private Usage() { }

    /** Local midnight of today in milliseconds. */
    public static long dayStart() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    /** Package -> milliseconds in the foreground since local midnight (see range). */
    public static Map<String, Long> today(Context c) {
        return range(c, dayStart(), System.currentTimeMillis());
    }

    /**
     * Package -> milliseconds in the foreground from "from" to "to". A package is in front while at least one of its activities is
     * resumed; a stay that began before "from" is counted from the first event of the range on. The periods in which the Kindle slept
     * (SleepLog) are taken out. Empty when nothing is known (no access).
     */
    public static Map<String, Long> range(Context c, long from, long to) {
        Map<String, Long> out = new HashMap<String, Long>();
        UsageStatsManager u = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
        if (u == null) return out;
        long now = to;
        long[][] sleeps = SleepLog.periods(c, System.currentTimeMillis());
        UsageEvents ev = u.queryEvents(from, to);
        if (ev == null) return out;
        Map<String, Integer> depth = new HashMap<String, Integer>();   // package -> resumed activities
        Map<String, Long> since = new HashMap<String, Long>();         // package -> when the first one was resumed
        UsageEvents.Event e = new UsageEvents.Event();
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e);
            String p = e.getPackageName();
            if (e.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                Integer d = depth.get(p);
                if (d == null || d == 0) since.put(p, e.getTimeStamp());
                depth.put(p, d == null ? 1 : d + 1);
            } else if (e.getEventType() == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                Integer d = depth.get(p);
                if (d == null || d == 0) continue;
                depth.put(p, d - 1);
                if (d == 1) add(out, p, since.get(p), e.getTimeStamp(), sleeps);
            }
        }
        for (Map.Entry<String, Integer> en : depth.entrySet()) {   // still in front now
            if (en.getValue() > 0) add(out, en.getKey(), since.get(en.getKey()), now, sleeps);
        }
        return out;
    }

    private static void add(Map<String, Long> m, String p, long from, long to, long[][] sleeps) {
        long ms = to - from;
        for (long[] s : sleeps) {   // the time the Kindle slept inside this stay is not screen time
            long a = s[0] > from ? s[0] : from, b = s[1] < to ? s[1] : to;
            if (b > a) ms -= b - a;
        }
        if (ms <= 0) return;
        Long o = m.get(p);
        m.put(p, o == null ? ms : o + ms);
    }
}