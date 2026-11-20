/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * T: UI language switch: the texts in the code are Chinese; with the language set to English every text goes
 * through the English table. */
package com.embrith.language;

import android.content.Context;

import java.util.Locale;

/**
 * UI language switch. The texts in the code are Chinese; with the language set to English (Prefs key "lang" = "en"
 * in the "embrith" preferences) every text goes through the table in English and comes back unchanged when it has no entry.
 */
public final class T {
    private T() { }

    /** True when the UI language is English. */
    public static boolean english(Context c) {
        try {
            return "en".equals(c.getSharedPreferences("embrith", Context.MODE_PRIVATE).getString("lang", "zh"));
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Locale for dates: English names in English, the system locale otherwise. */
    public static Locale locale(Context c) {
        return english(c) ? Locale.ENGLISH : Locale.getDefault();
    }

    /** The text in the current language. A string without an entry in the table is returned as it is. */
    public static String t(Context c, String zh) {
        if (zh == null || zh.length() == 0 || !english(c)) return zh;
        String en = English.get(norm(zh));
        return en != null ? en : zh;
    }

    /** CJK punctuation to ASCII, the same rule as the keys of the table (see English). */
    private static String norm(String s) {
        StringBuilder b = null;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            String r = null;
            switch (ch) {
                case '\uff0c':
                case '\u3001': r = ","; break;
                case '\uff1b': r = ";"; break;
                case '\uff1a': r = ":"; break;
                case '\u3002': r = "."; break;
                case '\uff08': r = "("; break;
                case '\uff09': r = ")"; break;
                case '\uff1f': r = "?"; break;
                case '\uff01': r = "!"; break;
                case '\uff5e': r = "~"; break;
                case '\uff0b': r = "+"; break;
                case '\uff0d': r = "-"; break;
                case '\u2026': r = "..."; break;
                default: break;
            }
            if (r != null && b == null) {
                b = new StringBuilder(s.length() + 8);
                b.append(s, 0, i);
            }
            if (b != null) b.append(r != null ? r : String.valueOf(ch));
        }
        return b == null ? s : b.toString();
    }
}