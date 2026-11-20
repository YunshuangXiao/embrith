/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * LibTools: Upkeep actions of the library for the settings page: clear the recent list, clear the cover cache. */
package com.embrith.library;

import java.io.File;

/** Upkeep actions of the library for the settings page: clear the recent list, clear the cover cache. */
public final class LibTools {
    private LibTools() { }

    /** Forgets which books were opened recently (reading places and bookmarks are kept). */
    public static void clearRecent() {
        new File(LibStore.DIR, "recent.tsv").delete();
    }

    /** Deletes the cover cache; the covers are made again when a grid is shown. Returns how many files went. */
    public static int clearCovers() {
        int n = 0;
        File[] fs = new File(LibStore.DIR, "covers").listFiles();
        if (fs != null) for (File f : fs) if (f.delete()) n++;
        return n;
    }
}