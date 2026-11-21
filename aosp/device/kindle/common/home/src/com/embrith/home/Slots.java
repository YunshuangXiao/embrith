/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * Slots: The widget areas (settings home, desktop): the size tables, the grid layout and the stored choices. */
package com.embrith.home;

import android.content.Context;
import android.util.DisplayMetrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A set of widget slots: one for the settings home (SETTINGS, Prefs prefix sh) and one for the desktop (HOME, prefix dh). Both use the same
 * grid layout, the same storage and the same setting page; they differ in the size table, the types a size may show and the metrics.
 * A slot is "sm1", "bg2", ... (size key and number). The slots chosen are listed in the Prefs key <prefix>_order. Each slot has its own place:
 * <prefix>_<id>_pos = "column,row" in cells (the top left cell). A slot without a place, or whose place no longer fits (the grid got smaller,
 * or it overlaps an earlier slot of the order), is not shown on the page; the setting page lists it as "not placed yet". There is no blank type:
 * cells that no slot covers are empty. Old settings (no place at all) get the places the former first-fit flow gave them, once (migrate).
 * Per slot: <prefix>_<id>_type and the content settings of that type.
 *
 * The area is a grid of square cells: cols cells wide, rows cells high, a gap g between two cells. A widget covers whole cells, n cells are
 * n * cell + (n - 1) * g, so every edge of every widget lies on the edge of the area or one gap from another widget: the left, right, top and
 * bottom edges and the edges between the widgets are all aligned. Cells nobody covers stay empty (the same look as a blank widget).
 * Settings home: cols is even (8 cells = 4 + 4 on the desktop), the cell is about as high as a first-level entry, rows = what the room allows
 * after the six entries (more on a large screen, fewer on a small one).
 * Desktop: 8 cells wide, rows = what fits above one row of apps (H`); the apps take what the widgets leave.
 * Pixel values are measured by SettingsHomeBox and HomeBody and kept in Prefs (sh_h, sh_hmax, dh_h, dh_hmax).
 */
abstract class Slots {
    static final String BUTTON = "button", IMAGE = "image", TEXT = "text", CLOCK = "clock",
        STATS = "stats", STATS_APP = "statsapp";
    static final String[] TYPE_IDS = { BUTTON, IMAGE, TEXT, CLOCK, STATS, STATS_APP };
    static final String[] TYPE_NAMES = { "按钮", "图片", "文字", "时钟", "屏幕时间: 总计", "屏幕时间: 单个应用" };

    /** The grid: w = width of the area, g = gap, cols x rows cells, h = the height of one row of apps (desktop, else 0). */
    static final class Metrics {
        final int w, g, cols, rows, h;
        final float cell;

        Metrics(int w, int g, int cols, int rows, int h) {
            this.w = w;
            this.g = g;
            this.cols = Math.max(1, cols);
            this.rows = Math.max(0, rows);
            this.h = h;
            this.cell = (w - (this.cols - 1) * g) / (float) this.cols;
        }

        /** The edge before cell number i (0 = the edge of the area). */
        int edge(int i) {
            return Math.round(i * (cell + g));
        }

        /** The edge after n cells that start at cell number i. */
        int end(int i, int n) {
            if (n <= 0) return edge(i);
            return Math.round(i * (cell + g) + n * cell + (n - 1) * g);
        }

        /** The height of n rows. */
        int height(int n) {
            return n <= 0 ? 0 : end(0, n);
        }

        /** The most rows that fit into this height. */
        int rowsIn(int px) {
            return px < cell ? 0 : (int) ((px + g) / (cell + g));
        }
    }

    // ------------------------------------------------------------------ the two sets
    static final int SMALL = 0, BIG = 1, HALF_S = 2, HALF_L = 3, WIDE = 4;

    static final Slots SETTINGS = new Slots("sh", new String[] { "sm", "bg", "hs", "hl", "wd" },
        new String[] { "小方块", "大方块", "半宽小条", "半宽大条", "通栏" },
        new String[] { "与一级入口等高的正方形", "两个入口高的正方形", "半行宽, 一个入口高", "半行宽, 两个入口高", "整行宽, 两个入口高" }, "wd1", false) {
        @Override int[] cells(Metrics m, int spec) {
            switch (spec) {
                case SMALL: return new int[] { 1, 1 };
                case BIG: return new int[] { 2, 2 };
                case HALF_S: return new int[] { m.cols / 2, 1 };
                case HALF_L: return new int[] { m.cols / 2, 2 };
                default: return new int[] { m.cols, 2 };
            }
        }

        @Override String[] types(int spec) {
            switch (spec) {
                case SMALL: return new String[] { BUTTON, IMAGE };
                case BIG: case HALF_L: return new String[] { IMAGE, TEXT, CLOCK };
                default: return new String[] { IMAGE, TEXT };
            }
        }

        @Override Metrics metrics(Context c) {
            DisplayMetrics dm = c.getResources().getDisplayMetrics();
            int g = gap(c), w = dm.widthPixels - 2 * Frames.edge(c), h = stored(c, "sh_h"), cap = stored(c, "sh_hmax");
            if (h <= 0) h = Math.max(Ui.dp(c, 60), (dm.heightPixels - Ui.dp(c, 178) - 7 * g) / 8);
            if (cap <= 0) cap = 2 * h + g;
            return forEntry(w, g, h, cap);
        }
    };

    /**
     * The grid of the settings home: an even number of columns, the fewest that make the cell no higher than an entry (h), and as many rows as
     * fit into cap, the room that is left for the area after the six entries. A cell is never higher than an entry, so two rows always fit
     * where the plain split (area = two entries and a gap) fits them. Even columns, so that a half-width bar is a whole number of cells.
     */
    static Metrics forEntry(int w, int g, int h, int cap) {
        int best = 2;
        for (int k = 2; k <= 24; k += 2) {
            float cell = (w - (k - 1) * g) / (float) k;
            if (cell < 1) break;
            best = k;
            if (cell <= h) break;
        }
        Metrics probe = new Metrics(w, g, best, 0, 0);
        return new Metrics(w, g, best, probe.rowsIn(cap), 0);
    }

    /** The rows that the placed slots (the result of place) cover: the area is only this high. */
    static int usedRows(List<int[]> pos, Metrics m) {
        int bottom = 0;
        for (int[] p : pos) if (p != null) bottom = Math.max(bottom, p[1] + p[3]);
        int r = 0;
        while (r < m.rows && m.height(r) < bottom) r++;
        return r;
    }

    // the order of the sizes on the settings page: wide before narrow, high before low
    static final int FULL_TALL = 0, HALF_TALL = 1, FULL_ROW = 2, FULL_THIN = 3, HALF_34 = 4, HALF_SQ = 5, HALF_43 = 6, HALF_ROW = 7, HALF_41 = 8, QUARTER = 9;

    static final Slots HOME = new Slots("dh", new String[] { "ft", "ht", "fr", "fn", "hv", "sq", "hw", "hr", "hn", "qs" },
        new String[] { "整屏组件", "半屏竖条", "通栏条", "通栏细条", "半宽竖图", "大方块", "半宽横图", "半宽条", "半宽细条", "小方块" },
        new String[] { "整行宽, 占满除一行应用外的全部高度", "半行宽, 占满除一行应用外的全部高度", "整行宽, 2 格高", "整行宽, 1 格高",
            "半行宽, 3:4 竖向", "半行宽的正方形", "半行宽, 4:3 横向", "半行宽, 2 格高", "半行宽, 4:1 细长", "四分之一行宽, 2 格高" }, "", true) {
        @Override int[] cells(Metrics m, int spec) {
            switch (spec) {
                case FULL_TALL: return new int[] { m.cols, m.rows };
                case HALF_TALL: return new int[] { m.cols / 2, m.rows };
                case FULL_ROW: return new int[] { m.cols, 2 };
                case FULL_THIN: return new int[] { m.cols, 1 };
                case HALF_SQ: return new int[] { m.cols / 2, m.cols / 2 };
                case HALF_ROW: return new int[] { m.cols / 2, 2 };
                case HALF_43: return new int[] { m.cols / 2, m.cols * 3 / 8 };
                case HALF_41: return new int[] { m.cols / 2, 1 };
                case QUARTER: return new int[] { m.cols / 4, 2 };
                default: return new int[] { m.cols / 2, m.cols * 5 / 8 };   // HALF_34
            }
        }

        @Override String[] types(int spec) {
            switch (spec) {
                case FULL_TALL: case HALF_TALL: case HALF_34: return new String[] { IMAGE };
                case FULL_ROW: case HALF_ROW: return new String[] { IMAGE, TEXT, CLOCK, STATS, STATS_APP };
                case FULL_THIN: case HALF_41: return new String[] { IMAGE, TEXT, STATS, STATS_APP };
                default: return new String[] { IMAGE, TEXT, CLOCK };   // HALF_SQ, HALF_43, QUARTER
            }
        }

        @Override Metrics metrics(Context c) {
            DisplayMetrics dm = c.getResources().getDisplayMetrics();
            int g = gap(c), w = dm.widthPixels - 2 * Frames.edge(c), h = stored(c, "dh_h"), top = stored(c, "dh_hmax");
            if (h <= 0) h = Ui.dp(c, 150);
            if (top <= 0) top = Math.max(h, dm.heightPixels - Ui.dp(c, 68 + 100) - 2 * Frames.edge(c) - g - h);
            return forTop(w, g, h, top);
        }
    };

    /** The grid of the desktop: 8 columns, as many rows as fit into the height top (H`). */
    static Metrics forTop(int w, int g, int h, int top) {
        Metrics probe = new Metrics(w, g, 8, 0, h);
        return new Metrics(w, g, 8, probe.rowsIn(top), h);
    }

    /** The set for the extra "set" of an Intent (sh or dh). */
    static Slots of(String name) {
        return "dh".equals(name) ? HOME : SETTINGS;
    }

    // ------------------------------------------------------------------ one set
    final String prefix;
    final String[] keys, names, descs;
    final boolean apps;   // the area shares the page with the apps (desktop): the previews show where they are
    private final String orderDef;

    private Slots(String prefix, String[] keys, String[] names, String[] descs, String orderDef, boolean apps) {
        this.prefix = prefix;
        this.keys = keys;
        this.names = names;
        this.descs = descs;
        this.orderDef = orderDef;
        this.apps = apps;
    }

    /** {columns, rows} of cells a size covers. */
    abstract int[] cells(Metrics m, int spec);

    /** The types a size may show. */
    abstract String[] types(int spec);

    /** The metrics as the page draws them (the measured values of the last time the page was shown). */
    abstract Metrics metrics(Context c);

    /** {width, height} in pixels of a size. */
    int[] size(Metrics m, int spec) {
        int[] n = cells(m, spec);
        return new int[] { m.end(0, n[0]), m.end(0, n[1]) };
    }

    static int gap(Context c) {
        return 2 * Frames.half(c);
    }

    static int stored(Context c, String key) {
        try { return Integer.parseInt(Prefs.getStr(c, key, "0")); } catch (NumberFormatException e) { return 0; }
    }

    static String typeName(String type) {
        for (int i = 0; i < TYPE_IDS.length; i++) if (TYPE_IDS[i].equals(type)) return TYPE_NAMES[i];
        return type;
    }

    /** The size of a slot id, -1 when the id is not valid. */
    int specOf(String id) {
        int n = id.length();
        while (n > 0 && Character.isDigit(id.charAt(n - 1))) n--;
        if (n == 0 || n == id.length()) return -1;
        String k = id.substring(0, n);
        for (int i = 0; i < keys.length; i++) if (keys[i].equals(k)) return i;
        return -1;
    }

    String key(String id, String what) {
        return prefix + "_" + id + "_" + what;
    }

    /** The Prefs name of the picture of a slot (see Img.resolve). */
    String imgKey(String id) {
        return key(id, "img");
    }

    String type(Context c, String id) {
        String[] ok = types(specOf(id));
        String t = Prefs.getStr(c, key(id, "type"), ok[0]);
        for (String s : ok) if (s.equals(t)) return t;
        return ok[0];
    }

    void setType(Context c, String id, String type) {
        Prefs.putStr(c, key(id, "type"), type);
    }

    List<String> order(Context c) {
        List<String> l = new ArrayList<String>();
        for (String s : Prefs.getStr(c, prefix + "_order", orderDef).split(",")) if (specOf(s) >= 0 && !l.contains(s)) l.add(s);
        return l;
    }

    private void save(Context c, List<String> l) {
        StringBuilder sb = new StringBuilder();
        for (String s : l) sb.append(sb.length() > 0 ? "," : "").append(s);
        Prefs.putStr(c, prefix + "_order", sb.toString());
    }

    int count(List<String> l, int spec) {
        int n = 0;
        for (String s : l) if (specOf(s) == spec) n++;
        return n;
    }

    // ------------------------------------------------------------------ places
    /** The stored place {column, row} of a slot in cells, null when it has none. */
    int[] pos(Context c, String id) {
        String v = Prefs.getStr(c, key(id, "pos"), "");
        int k = v.indexOf(',');
        if (k <= 0) return null;
        try { return new int[] { Integer.parseInt(v.substring(0, k)), Integer.parseInt(v.substring(k + 1)) }; } catch (NumberFormatException e) { return null; }
    }

    private void setPos(Context c, String id, int col, int row) {
        Prefs.putStr(c, key(id, "pos"), col < 0 ? "" : col + "," + row);
    }

    /**
     * The place {x, y, width, height} in pixels of every slot of ids, in the order of ids; null for a slot that has no place, does not fit into
     * the grid, or overlaps a slot before it. skip (may be null) is left out: its cells count as free.
     */
    List<int[]> place(Context c, List<String> ids, Metrics m) {
        return place(c, ids, m, null);
    }

    private List<int[]> place(Context c, List<String> ids, Metrics m, String skip) {
        List<int[]> out = new ArrayList<int[]>();
        occupy(c, ids, m, skip, out);
        return out;
    }

    /** The cells covered by the slots of ids (out, when not null, gets the pixel place of each, null if it has none). */
    private boolean[][] occupy(Context c, List<String> ids, Metrics m, String skip, List<int[]> out) {
        boolean[][] used = new boolean[m.rows][m.cols];
        for (String id : ids) {
            int spec = specOf(id);
            int[] p = spec < 0 || id.equals(skip) ? null : pos(c, id);
            int[] n = spec < 0 ? null : cells(m, spec);
            if (p == null || !inside(m, n, p[0], p[1]) || !free(used, p[1], p[0], n[0], n[1])) {
                if (out != null) out.add(null);
                continue;
            }
            for (int i = p[1]; i < p[1] + n[1]; i++) for (int j = p[0]; j < p[0] + n[0]; j++) used[i][j] = true;
            if (out != null) out.add(new int[] { m.edge(p[0]), m.edge(p[1]), m.end(p[0], n[0]) - m.edge(p[0]), m.end(p[1], n[1]) - m.edge(p[1]) });
        }
        return used;
    }

    private static boolean inside(Metrics m, int[] n, int col, int row) {
        return n[0] > 0 && n[1] > 0 && col >= 0 && row >= 0 && col + n[0] <= m.cols && row + n[1] <= m.rows;
    }

    private static boolean free(boolean[][] used, int r, int c, int cw, int ch) {
        for (int i = r; i < r + ch; i++) for (int j = c; j < c + cw; j++) if (used[i][j]) return false;
        return true;
    }

    /** Once: settings from before the places existed get the places of the former first-fit flow (what the user sees does not change). */
    void migrate(Context c, Metrics m) {
        if ("1".equals(Prefs.getStr(c, prefix + "_pos_v", "0")) || m.rows <= 0) return;
        boolean[][] used = new boolean[m.rows][m.cols];
        for (String id : order(c)) {
            int[] n = cells(m, specOf(id));
            if (n[0] <= 0 || n[1] <= 0 || n[0] > m.cols || n[1] > m.rows) continue;
            for (int r = 0; r + n[1] <= m.rows; r++) {
                boolean done = false;
                for (int col = 0; col + n[0] <= m.cols && !done; col++) {
                    if (!free(used, r, col, n[0], n[1])) continue;
                    for (int i = r; i < r + n[1]; i++) for (int j = col; j < col + n[0]; j++) used[i][j] = true;
                    setPos(c, id, col, r);
                    done = true;
                }
                if (done) break;
            }
        }
        Prefs.putStr(c, prefix + "_pos_v", "1");
    }

    /** The slots that are chosen but have no place on the grid now. */
    List<String> tray(Context c, Metrics m) {
        List<String> ids = order(c), out = new ArrayList<String>();
        List<int[]> pos = place(c, ids, m);
        for (int i = 0; i < ids.size(); i++) if (pos.get(i) == null) out.add(ids.get(i));
        return out;
    }

    /** Puts a slot with its top left cell at (col, row); false if it does not fit there (outside the grid, or over another slot). */
    boolean put(Context c, Metrics m, String id, int col, int row) {
        int spec = specOf(id);
        if (spec < 0) return false;
        int[] n = cells(m, spec);
        if (!inside(m, n, col, row)) return false;
        boolean[][] used = occupy(c, order(c), m, id, null);
        if (!free(used, row, col, n[0], n[1])) return false;
        setPos(c, id, col, row);
        return true;
    }

    /** Takes a slot off the grid; it stays chosen. */
    void unplace(Context c, String id) {
        setPos(c, id, -1, 0);
    }

    // ------------------------------------------------------------------ the chosen slots
    private String newId(List<String> l, int spec) {
        for (int n = 1;; n++) {
            String id = keys[spec] + n;
            if (!l.contains(id)) return id;
        }
    }

    /** Chooses one more slot of this size (not placed yet); its id, or null when there are too many. */
    String add(Context c, int spec) {
        List<String> l = order(c);
        if (l.size() >= 40) return null;
        String id = newId(l, spec);
        setPos(c, id, -1, 0);
        setType(c, id, types(spec)[0]);
        l.add(id);
        save(c, l);
        return id;
    }

    /** Removes a slot. */
    void remove(Context c, String id) {
        List<String> l = order(c);
        if (!l.remove(id)) return;
        setPos(c, id, -1, 0);
        save(c, l);
    }

    /** The slot to remove for the minus button of a size: one that is not placed, else the last one of that size; null if there is none. */
    String lastOf(Context c, Metrics m, int spec) {
        List<String> tray = tray(c, m);
        String last = null;
        for (String s : order(c)) if (specOf(s) == spec && (last == null || !tray.contains(last) || tray.contains(s))) last = s;
        return last;
    }
}
