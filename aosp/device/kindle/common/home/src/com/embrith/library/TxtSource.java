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
 */
final class TxtSource implements BookSource {
    private static final long MAX_BYTES = 24L * 1024 * 1024;
    private static final int MAX_HEAD = 40;      // a heading line is at most this long
    private static final int MIN_CHAPTER = 30;   // a heading with less text than this after it is a line of a contents list
    private static final Pattern HEAD = Pattern.compile(
        "(第\\s*[0-9零一二三四五六七八九十百千万两〇]+\\s*[章节回卷集部篇折幕].{0,30})"
        + "|((序章|序言|序|楔子|引子|前言|后记|尾声|终章|番外)"
        + "[0-9一二三四五六七八九十]*([\\s\u3000:._-].{0,20})?)"
        + "|((?i:chapter)\\s*[0-9ivxlc]+\\b.{0,40})|((?i:prologue|epilogue|preface|afterword))");
    private static final Pattern AUTHOR = Pattern.compile(
        "(?m)^[ \\t\u3000]*(?:作者|著|Author)[:\uff1a \\t\u3000]+(\\S[^\\n]{0,29})$");

    private final String text;
    private final int[] starts;        // start of every chunk, and the length of the text at the end
    private final String[] names;      // the heading of every chunk
    private final boolean[] headed;    // the chunk starts with its heading line (not the whole text, not the part before the first heading)

    TxtSource(File f) throws IOException {
        if (f.length() > MAX_BYTES) throw new IOException("文件太大, 超过 24 MB");
        text = decode(readAll(f)).replace("\r\n", "\n").replace('\r', '\n');
        int len = text.length();
        ArrayList<Integer> hp = new ArrayList<Integer>();
        ArrayList<String> hn = new ArrayList<String>();
        int p = 0;
        while (p < len) {
            int e = text.indexOf('\n', p);
            if (e < 0) e = len;
            int a = p, b = e;
            while (a < b && blank(text.charAt(a))) a++;
            while (b > a && blank(text.charAt(b - 1))) b--;
            if (b > a && b - a <= MAX_HEAD) {
                String s = text.substring(a, b);
                if (!sentenceEnd(s.charAt(s.length() - 1)) && HEAD.matcher(s).matches()) {
                    hp.add(p);
                    hn.add(s);
                }
            }
            p = e + 1;
        }
        ArrayList<Integer> kp = new ArrayList<Integer>();
        ArrayList<String> kn = new ArrayList<String>();
        for (int i = 0; i < hp.size(); i++) {
            int next = i + 1 < hp.size() ? hp.get(i + 1) : len;
            if (next - hp.get(i) >= MIN_CHAPTER) {
                kp.add(hp.get(i));
                kn.add(hn.get(i));
            }
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
        return c == ' ' || c == '\t' || c == '\u3000';
    }

    private static boolean sentenceEnd(char c) {
        return c == ',' || c == ';' || c == '\u3002' || c == '\uff0c' || c == '\uff1b' || c == '\uff01' || c == '\uff1f';
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