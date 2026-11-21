/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * StatusBus: Tells the components that show a value of ctl/status that this value changed. */
package com.embrith.home;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A component that shows a value of the Kindle (battery, Wi-Fi, bluetooth, ...) listens for the keys of ctl/status it shows, while it is on the screen.
 * StatusService calls changed() when the host writes the file; a component is called only when one of its own keys has another value than the last
 * time, so a status file that changed other things does not redraw it. Without a listener nothing is read and nothing is drawn.
 */
final class StatusBus {
    interface Listener {
        void changed();
    }

    private static final class Entry {
        final Listener l;
        final String[] keys;
        final String[] last;

        Entry(Listener l, String[] keys, String[] last) {
            this.l = l;
            this.keys = keys;
            this.last = last;
        }
    }

    private static final Handler H = new Handler(Looper.getMainLooper());
    private static final List<Entry> LIST = new ArrayList<Entry>();   // main thread only

    private StatusBus() { }

    private static String[] read(Map<String, String> st, String[] keys) {
        String[] v = new String[keys.length];
        for (int i = 0; i < keys.length; i++) v[i] = st.get(keys[i]);
        return v;
    }

    /** From now on l is told when one of keys changes (main thread). */
    static void listen(Listener l, String... keys) {
        unlisten(l);
        LIST.add(new Entry(l, keys, read(Ctl.status(), keys)));
    }

    static void unlisten(Listener l) {
        for (int i = LIST.size() - 1; i >= 0; i--) if (LIST.get(i).l == l) LIST.remove(i);
    }

    /** The status file was replaced (any thread). */
    static void changed() {
        H.post(new Runnable() {
            @Override public void run() {
                if (LIST.isEmpty()) return;
                Map<String, String> st = Ctl.status();
                for (Entry e : new ArrayList<Entry>(LIST)) {
                    String[] now = read(st, e.keys);
                    if (java.util.Arrays.equals(now, e.last)) continue;
                    System.arraycopy(now, 0, e.last, 0, now.length);
                    e.l.changed();
                }
            }
        });
    }
}
