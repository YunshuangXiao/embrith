/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * Tick: The timer of one component that shows the time (clock, status row, screen time): it runs only while that component is on the screen. */
package com.embrith.home;

import android.os.Handler;
import android.os.Looper;

/**
 * The refresh of a component belongs to the component: it owns a Tick, starts it when its window is visible and stops it when it is not (or when it
 * is detached). A page that has no such component has no timer at all, so nothing refreshes it, neither a part of the screen nor all of it.
 * The beat is aligned to the period (a minute: just after the minute changes), so the display changes once per period and at the right moment.
 */
final class Tick {
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable fn;
    private final long period;
    private boolean on;
    private final Runnable loop = new Runnable() {
        @Override public void run() {
            if (!on) return;
            fn.run();
            next();
        }
    };

    Tick(Runnable fn, long periodMs) {
        this.fn = fn;
        this.period = periodMs;
    }

    /** Runs fn once now (the content may be old) and then at every beat. */
    void start() {
        stop();
        on = true;
        fn.run();
        next();
    }

    void stop() {
        on = false;
        h.removeCallbacks(loop);
    }

    private void next() {
        h.postDelayed(loop, period - System.currentTimeMillis() % period + 100);
    }
}
