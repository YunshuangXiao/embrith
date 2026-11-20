/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * WhiteBg: Reading background filter and page turn animation switches for chosen apps; the display daemon (kdispd)
 * reads ctl/white. */
package com.embrith.home;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Reading background filter (white background) and page turn animation switch for chosen apps. The display daemon (kdispd) turns every light gray of the picture into pure white while
 * ctl/white says 1 (see WHITE_AT in docs/PROTOCOL.md). This class decides when: an app is in front when none of our own activities is
 * resumed and the home screen started an app (HomeActivity.launch), and the flag is 1 when that app is in the chosen list (Prefs "white_pkgs",
 * package names joined with ":"). The same lifecycle decides ctl/noanim (1 while an app with the page turn animation switched off is in
 * front; Prefs "noanim_pkgs", same format: apps are listed here only when switched off, so every app starts with the animation on). No polling: only the activity lifecycle of this process is watched, so an app that was not
 * started from the home screen is not seen.
 */
final class WhiteBg {
    private static final String KEY = "white_pkgs";
    private static final String KEY_NOANIM = "noanim_pkgs";
    private static final Handler H = new Handler(Looper.getMainLooper());
    private static Application app;
    private static Application.ActivityLifecycleCallbacks cb;
    private static Activity top;        // one of our own activities is resumed (null: an app is in front, or the screen is off)
    private static String launched;     // the package the home screen started last, null once the home screen is back

    private WhiteBg() { }

    private static List<String> load(Context c, String key) {
        List<String> l = new ArrayList<String>();
        String s = Prefs.getStr(c, key, "");
        for (String p : s.split(":")) if (p.length() > 0) l.add(p);
        return l;
    }

    private static void store(Context c, String key, List<String> l) {
        StringBuilder b = new StringBuilder();
        for (String p : l) b.append(b.length() > 0 ? ":" : "").append(p);
        Prefs.putStr(c, key, b.toString());
    }

    static List<String> chosen(Context c) {
        return load(c, KEY);
    }

    static void save(Context c, List<String> l) {
        store(c, KEY, l);
    }

    /** The apps whose page turn animation is switched off; apps that are no longer installed are dropped from the list (and from the stored one). */
    static List<String> noAnim(Context c) {
        List<String> l = load(c, KEY_NOANIM);
        PackageManager pm = c.getPackageManager();
        boolean cut = false;
        for (Iterator<String> it = l.iterator(); it.hasNext();) {
            try {
                pm.getPackageInfo(it.next(), 0);
            } catch (PackageManager.NameNotFoundException e) {
                it.remove();
                cut = true;
            }
        }
        if (cut) store(c, KEY_NOANIM, l);
        return l;
    }

    static void saveNoAnim(Context c, List<String> l) {
        store(c, KEY_NOANIM, l);
        apply();
    }

    /** Called by the home screen when it starts an app. */
    static void launched(String pkg) {
        launched = pkg;
    }

    private static final Runnable CHECK = new Runnable() {
        @Override public void run() {
            apply();
        }
    };

    private static void apply() {
        boolean front = app != null && top == null && launched != null;
        Ctl.put("white", front && chosen(app).contains(launched) ? "1" : "0");
        Ctl.put("noanim", front && noAnim(app).contains(launched) ? "1" : "0");
    }

    static void start(Application a) {
        if (cb != null) return;
        app = a;
        cb = new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityResumed(Activity act) {
                top = act;
                if (act instanceof HomeActivity) launched = null;
                H.removeCallbacks(CHECK);
                apply();
            }
            @Override public void onActivityPaused(Activity act) {
                if (top == act) top = null;
                H.removeCallbacks(CHECK);
                H.postDelayed(CHECK, 400);   // the next own activity resumes within this time, otherwise an app is in front
            }
            @Override public void onActivityCreated(Activity act, Bundle state) { }
            @Override public void onActivityStarted(Activity act) { }
            @Override public void onActivityStopped(Activity act) { }
            @Override public void onActivitySaveInstanceState(Activity act, Bundle state) { }
            @Override public void onActivityDestroyed(Activity act) { }
        };
        a.registerActivityLifecycleCallbacks(cb);
    }

    static void stop() {
        if (cb == null) return;
        app.unregisterActivityLifecycleCallbacks(cb);
        cb = null;
        H.removeCallbacks(CHECK);
        Ctl.put("white", "0");
        Ctl.put("noanim", "0");
    }
}
