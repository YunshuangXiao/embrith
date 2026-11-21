/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * Ctl: Access to the control directory /dev/kdisp/ctl that is shared with the Kindle side (kdispd, kgest,
 * embrith.sh). */
package com.embrith.home;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.Map;

/** The control directory shared with the Kindle side (kdispd, kgest, embrith.sh): /dev/kdisp/ctl. */
public final class Ctl {
    static final String DIR = "/dev/kdisp/ctl";

    private Ctl() { }

    /** Wakes the host watchdog at once (a line into the FIFO ctl/cmd); without it a request waits up to 2 s. */
    private static void wake() {
        File f = new File(DIR, "cmd");
        if (!f.exists()) return;
        try {
            FileOutputStream o = new FileOutputStream(f);
            try {
                o.write('\n');
            } finally {
                o.close();
            }
        } catch (IOException e) {
            // the host falls back to its 2 s scan
        }
    }

    /** Drop a request file: "clear" (flashing refresh), "exit" (leave Android). */
    public static void request(String name) {
        try {
            File f = new File(DIR, name);
            new FileOutputStream(f).close();
            f.setReadable(true, false);
            f.setWritable(true, false);
            wake();
        } catch (IOException e) {
            // the control directory is missing: nothing listens on the other side
        }
    }

    /** Write a small request file with a value (light_set, warm_set): the Kindle side reads it and deletes it. */
    public static void put(String name, String value) {
        File tmp = new File(DIR, name + ".app");
        try {
            Writer w = new OutputStreamWriter(new FileOutputStream(tmp), "UTF-8");
            try {
                w.write(value + "\n");
            } finally {
                w.close();
            }
            tmp.setReadable(true, false);
            tmp.setWritable(true, false);
            tmp.renameTo(new File(DIR, name));
            wake();
        } catch (IOException e) {
            // see request()
        }
    }

    /** Declare how the screen change that follows is refreshed (ctl/refresh): mode is a2, fast, ui, partial, flashui, flashpartial or full, for the whole screen. The display side only reads it for 1.5 s. */
    public static void refresh(String mode) {
        put("refresh", System.currentTimeMillis() + " " + mode + " 0 0 0 0 0");
    }

    /** Like put(), for a secret (a Wi-Fi password): the file is owner-only from the moment it exists; the Kindle side (root) reads it once and deletes it. */
    static void putSecret(String name, String value) {
        File tmp = new File(DIR, name + ".app");
        try {
            tmp.delete();
            if (!tmp.createNewFile()) return;
            tmp.setReadable(false, false);
            tmp.setWritable(false, false);
            tmp.setReadable(true, true);
            tmp.setWritable(true, true);
            Writer w = new OutputStreamWriter(new FileOutputStream(tmp), "UTF-8");
            try {
                w.write(value + "\n");
            } finally {
                w.close();
            }
            tmp.renameTo(new File(DIR, name));
        } catch (IOException e) {
            // see request()
        }
    }

    /** The first line of a small answer file (install_result), or "" when there is none. */
    static String text(String name) {
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(new File(DIR, name)), "UTF-8"));
            try {
                String l = r.readLine();
                return l == null ? "" : l.trim();
            } finally {
                r.close();
            }
        } catch (IOException e) {
            return "";
        }
    }

    /** All lines of an answer file (wifi_list, bt_list), or null when it does not exist (yet). An empty file gives an empty list. */
    static java.util.List<String> lines(String name) {
        File f = new File(DIR, name);
        if (!f.exists()) return null;
        java.util.List<String> out = new java.util.ArrayList<String>();
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            try {
                String l;
                while ((l = r.readLine()) != null) if (l.length() > 0) out.add(l);
            } finally {
                r.close();
            }
        } catch (IOException e) {
            return null;
        }
        return out;
    }

    /** The whole answer file as text (report_text), at most max chars, or null when it does not exist (yet). */
    static String readText(String name, int max) {
        File f = new File(DIR, name);
        if (!f.exists()) return null;
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            try {
                StringBuilder sb = new StringBuilder();
                char[] buf = new char[4096];
                int n;
                while (sb.length() < max && (n = r.read(buf)) > 0) sb.append(buf, 0, n);
                return sb.toString();
            } finally {
                r.close();
            }
        } catch (IOException e) {
            return null;
        }
    }

    static void remove(String name) {
        new File(DIR, name).delete();
    }

    static int statusInt(Map<String, String> st, String key, int def) {
        try {
            String v = st.get(key);
            return v == null ? def : Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    static Map<String, String> read() {
        return readFile("conf");
    }

    /** Written by embrith.sh on the Kindle every few seconds: WIFI=0/1 BT=0/1 (the Kindle's real radios). */
    static Map<String, String> status() {
        return readFile("status");
    }

    private static Map<String, String> readFile(String name) {
        Map<String, String> m = new LinkedHashMap<String, String>();
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(new File(DIR, name)), "UTF-8"));
            try {
                String line;
                while ((line = r.readLine()) != null) {
                    int eq = line.indexOf('=');
                    if (eq > 0) m.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
                }
            } finally {
                r.close();
            }
        } catch (IOException e) {
            // no configuration yet: defaults apply
        }
        return m;
    }

    static String get(String key, String def) {
        String v = read().get(key);
        return v == null ? def : v;
    }

    /** True while the host is compiling apps: ctl/dexopt says state=running and was written in the last minute (a dead host does not lock the screen). */
    static boolean dexoptRunning() {
        File f = new File(DIR, "dexopt");
        if (System.currentTimeMillis() - f.lastModified() > 60000) return false;
        java.util.List<String> l = lines("dexopt");
        if (l == null) return false;
        for (String s : l) if (s.trim().equals("state=running")) return true;
        return false;
    }

    /** How many apps the user installed, as last known (-1 = never counted); kept in the settings table (APP_COUNT), the host also refreshes it. */
    static int appCount() {
        try {
            return Integer.parseInt(get("APP_COUNT", "-1").trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    static void setAppCount(int n) {
        set("APP_COUNT", String.valueOf(n < 0 ? 0 : n));
    }

    /** Rewrites the whole file through a temporary name, so that the other side never sees half a file. */
    static void set(String key, String value) {
        Map<String, String> m = read();
        m.put(key, value);
        File tmp = new File(DIR, "conf.app");
        try {
            Writer w = new OutputStreamWriter(new FileOutputStream(tmp), "UTF-8");
            try {
                for (Map.Entry<String, String> e : m.entrySet()) w.write(e.getKey() + "=" + e.getValue() + "\n");
            } finally {
                w.close();
            }
            tmp.setReadable(true, false);
            tmp.setWritable(true, false);
            tmp.renameTo(new File(DIR, "conf"));
        } catch (IOException e) {
            // see request()
        }
    }
}
