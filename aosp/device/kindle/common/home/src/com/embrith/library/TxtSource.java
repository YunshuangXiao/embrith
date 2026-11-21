/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * TxtSource: A text file read at once (UTF-8, UTF-16 with a mark, otherwise GB18030) and cut into chapters at
 * heading lines. */package com.embrith.library;

import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A text file: read at once (UTF-8 or UTF-16 with a mark, strict UTF-8, otherwise GB18030) and cut into chapters at heading lines
 * (a short line alone, like "chapter 3" or a numbered section in Chinese). With fewer than two headings the whole text is one chunk.
 * The headings are looked for in three tiers, the first that gives two or more is used: (1) keyword headings (Chinese chapter words, chapter / part /
 * volume / book with a number or a number word, the Russian glava / chast / tom, prologue and the like); (2) numbered lines
 * (one, 1. 1, a roman numeral, bare numbers and roman numerals) that follow an empty line; (3) layout: a short line with an empty line before and
 * after it (and text after that), when that gives chapters of sensible length.
 * A regular expression of the user (Settings > Library > TXT chapters, Prefs txt_regex) comes before the three tiers: a line (without the blanks around
 * it, at most MAX_CUSTOM characters) is a heading when the expression is found in it. If it is not valid or gives fewer than two chapters, the tiers are used.
 */
final class TxtSource implements BookSource {
    private static final long MAX_BYTES = 24L * 1024 * 1024;
    private static final int MAX_CUSTOM = 100;   // a line longer than this is never a heading of the expression of the user
    private static final int MAX_HEAD = 40;      // a heading line is at most this long
    private static final int MIN_CHAPTER = 30;   // a heading with less text than this after it is a line of a contents list
    private static final String ZH_NUM = "[0-9\uff10-\uff19\u96f6\u3007\u4e00\u4e8c\u4e09\u56db\u4e94\u516d\u4e03\u516b\u4e5d\u5341\u767e\u5343\u4e07\u4e24\u58f9\u8d30\u53c1\u8086\u4f0d\u9646\u67d2\u634c\u7396\u62fe\u4f70\u4edf]+";
    private static final String EN_NUM = "(?:[0-9]+|[ivxlcdm]+|(?:twenty|thirty|forty|fifty|sixty|seventy|eighty|ninety)(?:[- ](?:one|two|three|four|five|six|seven|eight|nine))?"
        + "|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen|hundred"
        + "|first|second|third|fourth|fifth|sixth|seventh|eighth|ninth|tenth|final|last)";
    // tier 1: keyword headings
    private static final Pattern STRONG = Pattern.compile(
        "([\\[\u3010(\uff08\u300a]?\\s*\u7b2c\\s*" + ZH_NUM + "\\s*[\u7ae0\u8282\u56de\u5377\u96c6\u90e8\u7bc7\u6298\u5e55\u8bdd\u8a71\u8bb2]\\s*[\\]\u3011)\uff09\u300b]?.{0,40})"
        + "|([\u4e0a\u4e2d\u4e0b][\u5377\u90e8\u7bc7]([\\s\u3000:\uff1a.\u3001_-].{0,30})?)"
        + "|(\u5377\\s*" + ZH_NUM + "([\\s\u3000:\uff1a.\u3001_-].{0,30})?)"
        + "|((\u5e8f\u7ae0|\u5e8f\u8a00|\u5e8f\u5e55|\u5e8f|\u6954\u5b50|\u5f15\u5b50|\u524d\u8a00|\u540e\u8bb0|\u540e\u5e8f|\u8dcb|\u5c3e\u58f0|\u7ec8\u7ae0|\u7ec8\u5e55|\u5927\u7ed3\u5c40|\u5b8c\u7ed3\u611f\u8a00|\u756a\u5916|\u9644\u5f55|\u5199\u5728\u524d\u9762|\u5916\u4f20|\u5185\u5bb9\u7b80\u4ecb|\u4f5c\u54c1\u76f8\u5173|\u5b8c\u672c\u611f\u8a00)"
        + "[0-9\u4e00\u4e8c\u4e09\u56db\u4e94\u516d\u4e03\u516b\u4e5d\u5341\u7bc7]*([\\s\u3000:\uff1a._-].{0,30})?)"
        + "|((?iu:chapter|part|book|volume|vol|section|episode|\u0433\u043b\u0430\u0432\u0430|\u0447\u0430\u0441\u0442\u044c|\u0442\u043e\u043c)\\.?\\s*(?i:" + EN_NUM + ")(?![\\p{L}\\p{N}]).{0,40})"
        + "|((?i:prologue|epilogue|preface|afterword|foreword|introduction|appendix|interlude)([:.\\s-].{0,30})?)");
    // a line that starts like a heading but is body text (from the exclude rules of the novel readers: a department, a lesson, a returning)
    private static final Pattern EXCLUDE = Pattern.compile(
        "[\\[\u3010(\uff08\u300a]?\\s*\u7b2c?" + ZH_NUM + "\\s*(\u90e8\u95e8|\u90e8\u961f|\u90e8\u5c5e|\u90e8\u5206|\u90e8\u4ef6|\u90e8\u843d|\u8282\u8bfe|\u96c6\u56e2|\u96c6\u5408|\u96c6\u4e2d"
        + "|\u56de\u6765|\u56de\u53bb|\u56de\u5bb6|\u56de\u5230|\u56de\u7b54|\u56de\u5fc6|\u56de\u5934|\u8282\u76ee|\u8282\u65e5|\u8282\u7701|\u8282\u7ea6|\u7bc7\u5e45).*");
    // tier 2: numbered lines (after an empty line)
    private static final Pattern NUMBERED = Pattern.compile(
        "[\u4e00\u4e8c\u4e09\u56db\u4e94\u516d\u4e03\u516b\u4e5d\u5341\u767e\u5343]+\\s*[\u3001.\uff0e]\\s*\\S.{0,35}"
        + "|[0-9\uff10-\uff19]{1,4}\\s*[\u3001.\uff0e:\uff1a)\uff09]\\s*\\S.{0,35}"
        + "|[0-9]{1,4}[ \u3000]+\\S.{0,30}"
        + "|" + ZH_NUM + "[.\u3001\\s]\\s*[\u7ae0\u56de\u8282\u5377\u96c6\u90e8\u7bc7].{0,30}"
        + "|[0-9]+(\\.[0-9]+)*\\.?"
        + "|(?=[IVX])X{0,3}(IX|IV|V?I{0,3})\\.?");
    private static final Pattern AUTHOR = Pattern.compile(
        "(?m)^[ \\t\u3000]*(?:作者|著|Author)[:\uff1a \\t\u3000]+(\\S[^\\n]{0,29})$");

    private final String text;
    private final int[] starts;        // start of every chunk, and the length of the text at the end
    private final String[] names;      // the heading of every chunk
    private final boolean[] headed;    // the chunk starts with its heading line (not the whole text, not the part before the first heading)

    TxtSource(File f) throws IOException {
        this(f, "");
    }

    /** custom = the regular expression of the user ("" = none). */
    TxtSource(File f, String custom) throws IOException {
        if (f.length() > MAX_BYTES) throw new IOException("文件太大, 超过 24 MB");
        text = decode(readAll(f)).replace("\r\n", "\n").replace('\r', '\n');
        int len = text.length();
        int[][] ln = lines(text);
        ArrayList<Integer> kp = new ArrayList<Integer>();
        ArrayList<String> kn = new ArrayList<String>();
        Pattern mine = null;
        if (custom != null && custom.trim().length() > 0) {
            try { mine = Pattern.compile(custom.trim()); } catch (RuntimeException e) { mine = null; }   // not valid: the tiers
        }
        for (int tier = mine == null ? 1 : 0; tier <= 3; tier++) {
            kp.clear();
            kn.clear();
            detect(text, ln, tier, mine, kp, kn);
            if (kp.size() >= 2 && (tier < 3 || kp.size() * 400 <= len)) break;
            kp.clear();
            kn.clear();
        }
        ArrayList<Integer> sp = new ArrayList<Integer>();
        ArrayList<String> sn = new ArrayList<String>();
        ArrayList<Boolean> sh = new ArrayList<Boolean>();
        if (len == 0) {
            // no chunk at all
        } else if (kp.size() < 2) {
            sp.add(0);
            sn.add("全文");
            sh.add(false);
        } else {
            if (text.substring(0, kp.get(0)).trim().length() > 0) {
                sp.add(0);
                sn.add("开头");
                sh.add(false);
            }
            sp.addAll(kp);
            sn.addAll(kn);
            for (int i = 0; i < kp.size(); i++) sh.add(true);
        }
        sp.add(len);
        starts = new int[sp.size()];
        for (int i = 0; i < starts.length; i++) starts[i] = sp.get(i);
        names = sn.toArray(new String[0]);
        headed = new boolean[sh.size()];
        for (int i = 0; i < headed.length; i++) headed[i] = sh.get(i);
    }

    private static boolean blank(char c) {
        return c == ' ' || c == '\t' || c == '\u3000' || c == '\u00a0' || c == '\ufeff' || c == '\u2002' || c == '\u2003';
    }

    /** A comma, semicolon or full stop at the end: the line is a sentence, not a heading (a question or exclamation mark is fine in a title). */
    private static boolean sentenceEnd(char c) {
        return c == ',' || c == ';' || c == '\u3002' || c == '\uff0c' || c == '\uff1b';
    }

    /** The same, and also the end of a quotation or a question: a line like that is dialogue, not a heading without keyword. */
    private static boolean lineEnd(char c) {
        return sentenceEnd(c) || c == '!' || c == '?' || c == '\uff01' || c == '\uff1f' || c == '\u201d' || c == '\u300d' || c == '"' || c == '\u2019'
            || c == ':' || c == '\uff1a' || c == '\u2026';
    }

    /** Every line as {start of the line, first and end of its text without the blanks around it}. */
    private static int[][] lines(String text) {
        ArrayList<int[]> l = new ArrayList<int[]>();
        int len = text.length(), p = 0;
        while (p < len) {
            int e = text.indexOf('\n', p);
            if (e < 0) e = len;
            int a = p, b = e;
            while (a < b && blank(text.charAt(a))) a++;
            while (b > a && blank(text.charAt(b - 1))) b--;
            l.add(new int[] { p, a, b });
            p = e + 1;
        }
        return l.toArray(new int[0][]);
    }

    /**
     * The headings of a text that is not a txt book (an epub without a usable table of contents): the start of every heading line and its text.
     * The keyword tier gives a result from one heading on; the weaker tiers need two.
     */
    static void headings(String text, ArrayList<Integer> pos, ArrayList<String> names) {
        int[][] ln = lines(text);
        for (int tier = 1; tier <= 3; tier++) {
            pos.clear();
            names.clear();
            detect(text, ln, tier, null, pos, names);
            if (tier == 1 ? pos.size() >= 1 : pos.size() >= 2 && (tier < 3 || pos.size() * 400 <= text.length())) return;
        }
        pos.clear();
        names.clear();
    }

    private static boolean empty(int[][] ln, int i) {
        return i < 0 || i >= ln.length || ln[i][2] <= ln[i][1];
    }

    /** The headings of one tier (see the class comment), without those that have too little text after them (a contents list). */
    private static void detect(String text, int[][] ln, int tier, Pattern mine, ArrayList<Integer> kp, ArrayList<String> kn) {
        ArrayList<Integer> hp = new ArrayList<Integer>();
        ArrayList<String> hn = new ArrayList<String>();
        for (int i = 0; i < ln.length; i++) {
            int a = ln[i][1], b = ln[i][2], n = b - a;
            if (n <= 0) continue;
            String s = text.substring(a, b);
            char last = s.charAt(n - 1);
            boolean ok;
            if (tier == 0) {
                if (n <= MAX_CUSTOM) {
                    try {
                        if (mine.matcher(s).find()) {
                            hp.add(ln[i][0]);
                            hn.add(s);
                        }
                    } catch (RuntimeException e) {
                        return;   // the expression failed on this line (for example a stack overflow): no result for it
                    }
                }
                continue;
            }
            if (s.indexOf('\u3002') >= 0 || EXCLUDE.matcher(s).lookingAt()) continue;   // a full stop inside: body text
            if (tier == 1) ok = n <= MAX_HEAD && !sentenceEnd(last) && STRONG.matcher(s).matches();
            else if (tier == 2) ok = n <= 40 && empty(ln, i - 1) && !lineEnd(last) && NUMBERED.matcher(s).matches();
            else ok = n <= 30 && empty(ln, i - 1) && empty(ln, i + 1) && !empty(ln, i + 2) && !lineEnd(last);
            if (ok) {
                hp.add(ln[i][0]);
                hn.add(s);
            }
        }
        int len = text.length();
        for (int i = 0; i < hp.size(); i++) {
            int next = i + 1 < hp.size() ? hp.get(i + 1) : len;
            if (next - hp.get(i) >= MIN_CHAPTER) {
                kp.add(hp.get(i));
                kn.add(hn.get(i));
            }
        }
    }

    private static byte[] readAll(File f) throws IOException {
        InputStream in = new FileInputStream(f);
        try {
            byte[] buf = new byte[(int) f.length()];
            int n = 0;
            while (n < buf.length) {
                int r = in.read(buf, n, buf.length - n);
                if (r < 0) break;
                n += r;
            }
            return n == buf.length ? buf : java.util.Arrays.copyOf(buf, n);
        } finally {
            in.close();
        }
    }

    private static String decode(byte[] b) throws IOException {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xEF && (b[1] & 0xFF) == 0xBB && (b[2] & 0xFF) == 0xBF) return new String(b, 3, b.length - 3, "UTF-8");
        if (b.length >= 2 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xFE) return new String(b, 2, b.length - 2, "UTF-16LE");
        if (b.length >= 2 && (b[0] & 0xFF) == 0xFE && (b[1] & 0xFF) == 0xFF) return new String(b, 2, b.length - 2, "UTF-16BE");
        try {
            return Charset.forName("UTF-8").newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(b)).toString();
        } catch (CharacterCodingException e) {
            return new String(b, "GB18030");
        }
    }

    @Override public int count() { return starts.length - 1; }

    @Override public CharSequence chunk(int i) {
        String t = text.substring(starts[i], starts[i + 1]);
        if (!headed[i]) return t;
        int e = t.indexOf('\n');
        if (e < 0) e = t.length();
        SpannableString sp = new SpannableString(t);
        sp.setSpan(new StyleSpan(Typeface.BOLD), 0, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sp;
    }

    @Override public long weight(int i) { return starts[i + 1] - starts[i]; }

    @Override public String chapterTitle(int i) { return names[i]; }

    @Override public String bookTitle() { return null; }

    @Override public String author() {
        Matcher m = AUTHOR.matcher(text.substring(0, Math.min(text.length(), 3000)));
        return m.find() ? m.group(1).trim() : null;
    }

    @Override public void close() { }
}