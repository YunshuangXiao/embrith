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
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * The screen time per app and local day. Only one app is in front at a time, so the events are matched with a single open slot:
 * a "resumed" event of another package closes the open stay, a "paused" event must belong to the slot package, and the slot closes
 * when none of its activities is resumed. The time the Kindle slept (SleepLog) is taken out, and a stay that crosses local midnight is
 * split between the days.
 *
 * The result is kept: the closed stays are added to per-day totals (pref "usage_days") and the position in the event stream with the
 * open slot is the checkpoint (pref "usage_ck"). A query only reads the events after the checkpoint, past days are never recomputed.
 */
public final class Usage {
    private static final String K_DAYS = "usage_days";   // "day:pkg=ms,pkg=ms;day:..."
    private static final String K_CK = "usage_ck";       // "lastEventTime;pkg;since;cls|cls" (pkg empty: no open slot)
    private static final long KEEP_MS = 14L * 86400000L;
    private static final long FIRST_MS = 7L * 86400000L;  // first run: look this far back

    private Usage() { }

    /** Local midnight of today in milliseconds. */
    public static long dayStart() {
        return dayOf(System.currentTimeMillis());
    }

    private static long dayOf(long t) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(t);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private static long nextDay(long dayStart) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(dayStart);
        cal.add(Calendar.DAY_OF_MONTH, 1);
        return cal.getTimeInMillis();
    }

    /** Package -> milliseconds in the foreground since local midnight (see range). */
    public static Map<String, Long> today(Context c) {
        return range(c, dayStart(), System.currentTimeMillis());
    }

    /**
     * Package -> milliseconds in the foreground over the local days from the day of "from" to "to" (the day of "from" counts whole).
     * A stay still open counts until "to". Empty when nothing is known (no access).
     */
    public static synchronized Map<String, Long> range(Context c, long from, long to) {
        Map<String, Long> out = new HashMap<String, Long>();
        Map<Long, Map<String, Long>> days = load(c);
        String[] ck = sync(c, days, to);
        if (ck != null && ck[1].length() > 0) {   // the open stay is not stored: add it to a copy
            addStay(days, ck[1], Long.parseLong(ck[2]), to, SleepLog.periods(c, System.currentTimeMillis()));
        }
        long d0 = dayOf(from);
        for (Map.Entry<Long, Map<String, Long>> en : days.entrySet()) {
            if (en.getKey() < d0 || en.getKey() > to) continue;
            for (Map.Entry<String, Long> p : en.getValue().entrySet()) {
                Long o = out.get(p.getKey());
                out.put(p.getKey(), o == null ? p.getValue() : o + p.getValue());
            }
        }
        return out;
    }

    /** Reads the events after the checkpoint, stores the closed stays and the new checkpoint. Returns the checkpoint (null: no access). */
    private static String[] sync(Context c, Map<Long, Map<String, Long>> days, long to) {
        UsageStatsManager u = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
        if (u == null) return null;
        long now = System.currentTimeMillis();
        String[] ck = Prefs.getStr(c, K_CK, "").split(";", -1);
        long last = 0, since = 0;
        String cur = null;
        Set<String> resumed = new HashSet<String>();
        if (ck.length == 4) {
            try {
                last = Long.parseLong(ck[0]);
                if (ck[1].length() > 0) {
                    cur = ck[1];
                    since = Long.parseLong(ck[2]);
                    for (String s : ck[3].split("\\|")) if (s.length() > 0) resumed.add(s);
                }
            } catch (NumberFormatException x) {
                last = 0; cur = null; resumed.clear();   // a damaged checkpoint: start again
            }
        }
        if (last == 0) days.clear();
        long begin = last == 0 ? dayOf(now - FIRST_MS) : last + 1;
        UsageEvents ev = u.queryEvents(begin, now);
        if (ev == null) return null;
        long[][] sleeps = SleepLog.periods(c, now);
        UsageEvents.Event e = new UsageEvents.Event();
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e);
            long t = e.getTimeStamp();
            if (t > last) last = t;
            String p = e.getPackageName();
            if (e.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                if (cur != null && !cur.equals(p)) {
                    addStay(days, cur, since, t, sleeps);
                    cur = null;
                    resumed.clear();
                }
                if (cur == null) { cur = p; since = t; }
                resumed.add(String.valueOf(e.getClassName()));
            } else if (e.getEventType() == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                if (cur == null || !cur.equals(p)) continue;
                resumed.remove(String.valueOf(e.getClassName()));
                if (resumed.isEmpty()) { addStay(days, cur, since, t, sleeps); cur = null; }
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append(last).append(';').append(cur == null ? "" : cur).append(';').append(since).append(';');
        if (cur != null) {
            boolean first = true;
            for (String s : resumed) { if (!first) sb.append('|'); sb.append(s); first = false; }
        }
        Prefs.putStr(c, K_CK, sb.toString());
        save(c, days, now);
        return sb.toString().split(";", -1);
    }

    /** Adds the stay from..to to the totals of its days, without the sleep periods; a stay over midnight is split. */
    private static void addStay(Map<Long, Map<String, Long>> days, String p, long from, long to, long[][] sleeps) {
        long d = dayOf(from);
        while (d < to) {
            long nd = nextDay(d);
            long a = from > d ? from : d, b = to < nd ? to : nd;
            long ms = b - a;
            for (long[] s : sleeps) {   // the time the Kindle slept inside this part is not screen time
                long x = s[0] > a ? s[0] : a, y = s[1] < b ? s[1] : b;
                if (y > x) ms -= y - x;
            }
            if (ms > 0) {
                Map<String, Long> m = days.get(d);
                if (m == null) { m = new HashMap<String, Long>(); days.put(d, m); }
                Long o = m.get(p);
                m.put(p, o == null ? ms : o + ms);
            }
            d = nd;
        }
    }

    private static Map<Long, Map<String, Long>> load(Context c) {
        Map<Long, Map<String, Long>> days = new HashMap<Long, Map<String, Long>>();
        for (String part : Prefs.getStr(c, K_DAYS, "").split(";")) {
            int i = part.indexOf(':');
            if (i <= 0) continue;
            try {
                Map<String, Long> m = new HashMap<String, Long>();
                for (String kv : part.substring(i + 1).split(",")) {
                    int j = kv.lastIndexOf('=');
                    if (j > 0) m.put(kv.substring(0, j), Long.parseLong(kv.substring(j + 1)));
                }
                days.put(Long.parseLong(part.substring(0, i)), m);
            } catch (NumberFormatException x) {
                // a damaged day is dropped
            }
        }
        return days;
    }

    private static void save(Context c, Map<Long, Map<String, Long>> days, long now) {
        StringBuilder sb = new StringBuilder();
        for (Iterator<Map.Entry<Long, Map<String, Long>>> it = days.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Long, Map<String, Long>> en = it.next();
            if (en.getKey() < now - KEEP_MS) { it.remove(); continue; }
            if (sb.length() > 0) sb.append(';');
            sb.append(en.getKey()).append(':');
            boolean first = true;
            for (Map.Entry<String, Long> p : en.getValue().entrySet()) {
                if (!first) sb.append(',');
                sb.append(p.getKey()).append('=').append(p.getValue());
                first = false;
            }
        }
        Prefs.putStr(c, K_DAYS, sb.toString());
    }
}
