/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * EmbrithIME: Plain keyboard for the e-ink screen (InputMethodService): offline simplified pinyin, letters,
 * digits, symbols, shift, delete, space, enter. */
/*
 * Referenced project: RetyOS, MIT, Copyright (c) 2026 RetyOs contributors. File consulted:
 *   sources/stage4/minimal/ReaderInputMethod.java   design of the plain e-ink keyboard (offline pinyin, no popups)
 * assets/pinyin.tsv is the same file as sources/stage4/minimal/assets/pinyin.tsv there (Apache-2.0 data, see
 * PINYIN-AUTHORS.txt and PINYIN-LICENSE.txt in assets/). MIT notice: see third_party/README.md.
 */
package com.embrith.home;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.text.InputType;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A plain keyboard for the e-ink screen, after RetyOS ReaderInputMethod: offline simplified pinyin (assets/pinyin.tsv, loaded on first use),
 * letters, digits and symbols, shift (one letter), delete (repeats), space, enter. No suggestions, gestures, popups or animation.
 * It runs in the KindleHome process (no process of its own).
 */
public class EmbrithIME extends InputMethodService {
    private SoftKeys keys;
    private Map<String, String[]> dict;                       // pinyin -> candidates, null until the first Chinese letter
    private final StringBuilder composing = new StringBuilder();
    private final List<String> choices = new ArrayList<String>();
    private boolean kbdShown;                                 // the keyboard window is on screen (ctl/kbd is written)
    private String matched = "";                              // the part of composing the candidates stand for
    private int page;
    private boolean chinese = true, password, quoteOpen;

    /** Chinese mode: full-width punctuation on the symbol pages (iOS Chinese layout). */
    private String cn(String k, boolean symbols) {
        if (!chinese || !symbols || k.length() != 1) return k;
        switch (k.charAt(0)) {
            case '.': return "。";
            case ',': return "，";
            case '?': return "？";
            case '!': return "！";
            case '\'': return "、";
            case ':': return "：";
            case ';': return "；";
            case '(': return "（";
            case ')': return "）";
            case '"': return "“";
            default: return k;
        }
    }

    @Override
    public View onCreateInputView() {
        keys = new SoftKeys();
        return keys;
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        return false;
    }

    // 键盘在屏幕上时把自己的高度(像素)写进 ctl/kbd: 显示端把键盘区域内的变化一律当局部刷新, 按键不算翻页
    @Override
    public void onWindowShown() {
        super.onWindowShown();
        kbdShown = true;
        if (keys != null && keys.getHeight() > 0) Ctl.put("kbd", String.valueOf(keys.getHeight()));
    }

    @Override
    public void onWindowHidden() {
        kbdShown = false;
        Ctl.remove("kbd");
        super.onWindowHidden();
    }

    @Override
    public void onDestroy() {
        Ctl.remove("kbd");
        super.onDestroy();
    }

    @Override
    public void onStartInputView(EditorInfo ei, boolean restarting) {
        super.onStartInputView(ei, restarting);
        composing.setLength(0);
        quoteOpen = false;
        int cls = ei.inputType & InputType.TYPE_MASK_CLASS, var = ei.inputType & InputType.TYPE_MASK_VARIATION;
        password = var == InputType.TYPE_TEXT_VARIATION_PASSWORD || var == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            || var == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD;
        chinese = cls == InputType.TYPE_CLASS_TEXT && !password && var != InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS && var != InputType.TYPE_TEXT_VARIATION_URI;
        updateChoices();
        if (keys != null) keys.reset(cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE);
    }

    @Override
    public void onFinishInput() {
        composing.setLength(0);
        super.onFinishInput();
    }

    private void loadDict() {
        dict = new HashMap<String, String[]>();
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(getAssets().open("pinyin.tsv"), "UTF-8"));
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    int at = line.indexOf('\t');
                    if (at > 0) dict.put(line.substring(0, at), line.substring(at + 1).split(" "));
                }
            } finally {
                in.close();
            }
        } catch (Exception e) {
            Log.e("EmbrithIME", "pinyin.tsv not loaded", e);
        }
    }

    /** Candidates for composing: the whole input, else the longest start of it (up to 6 letters) that the dictionary knows. */
    private void updateChoices() {
        choices.clear();
        matched = "";
        String in = composing.toString();
        if (in.length() > 0) {
            if (dict == null) loadDict();
            String[] v = dict.get(in);
            if (v != null) {
                matched = in;
                Collections.addAll(choices, v);
            } else {
                for (int n = Math.min(6, in.length()); n > 0; n--) {
                    v = dict.get(in.substring(0, n));
                    if (v != null) {
                        matched = in.substring(0, n);
                        Collections.addAll(choices, v);
                        break;
                    }
                }
            }
        }
        if (page * 5 >= choices.size()) page = 0;
        if (keys != null) keys.strip();
    }

    private void setComposing() {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.setComposingText(composing, 1);
        page = 0;
        updateChoices();
    }

    private void commit(String s) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.commitText(s, 1);
    }

    /** What was typed as letters stays as it is. */
    private void finishRaw() {
        if (composing.length() > 0) {
            commit(composing.toString());
            composing.setLength(0);
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) ic.finishComposingText();
        }
        page = 0;
        updateChoices();
    }

    private void commitChoice(int index) {
        if (index < 0 || index >= choices.size()) return;
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.commitText(choices.get(index), 1);
        composing.delete(0, Math.min(matched.length(), composing.length()));
        if (composing.length() > 0) {
            setComposing();
        } else {
            if (ic != null) ic.finishComposingText();
            page = 0;
            updateChoices();
        }
    }

    private void enter() {
        EditorInfo ei = getCurrentInputEditorInfo();
        InputConnection ic = getCurrentInputConnection();
        int act = ei == null ? EditorInfo.IME_ACTION_NONE : ei.imeOptions & EditorInfo.IME_MASK_ACTION;
        boolean noAction = ei == null || (ei.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
            || act == EditorInfo.IME_ACTION_NONE || act == EditorInfo.IME_ACTION_UNSPECIFIED;
        if (ic != null && !noAction) ic.performEditorAction(act);
        else sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
    }

    private void press(String k, boolean shifted, boolean symbols) {
        if ("DEL".equals(k)) {
            if (composing.length() > 0) {
                composing.deleteCharAt(composing.length() - 1);
                setComposing();
            } else {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
            }
        } else if ("ENTER".equals(k)) {
            finishRaw();
            enter();
        } else if ("SPACE".equals(k)) {
            if (composing.length() > 0) {
                if (choices.isEmpty()) finishRaw(); else commitChoice(0);
            } else {
                commit(" ");
            }
        } else if ("MODE".equals(k)) {
            finishRaw();
            chinese = !chinese;
        } else if (chinese && !symbols && !password && k.length() == 1 && k.charAt(0) >= 'a' && k.charAt(0) <= 'z' && !shifted) {
            if (composing.length() < 32) {
                composing.append(k);
                setComposing();
            }
        } else {
            finishRaw();
            if (chinese && symbols && "\"".equals(k)) {
                commit(quoteOpen ? "”" : "“");
                quoteOpen = !quoteOpen;
            } else commit(shifted ? k.toUpperCase() : cn(k, symbols));
        }
    }

    private final class SoftKeys extends View {
        private final String[][] letters = {
            { "q", "w", "e", "r", "t", "y", "u", "i", "o", "p" },
            { "a", "s", "d", "f", "g", "h", "j", "k", "l" },
            { "SHIFT", "z", "x", "c", "v", "b", "n", "m", "DEL" },
            { "SYM", "MODE", "SPACE", "ENTER" } };
        private final String[][] symbols = {                 // iOS "123" page
            { "1", "2", "3", "4", "5", "6", "7", "8", "9", "0" },
            { "-", "/", ":", ";", "(", ")", "$", "&", "@", "\"" },
            { "MORE", ".", ",", "?", "!", "'", "DEL" },
            { "ABC", "MODE", "SPACE", "ENTER" } };
        private final String[][] symbols2 = {                // iOS "#+=" page
            { "[", "]", "{", "}", "#", "%", "^", "*", "+", "=" },
            { "_", "\\", "|", "~", "<", ">", "€", "£", "¥", "•" },
            { "NUM", ".", ",", "?", "!", "'", "DEL" },
            { "ABC", "MODE", "SPACE", "ENTER" } };
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Handler h = new Handler();
        private final List<RectF> rects = new ArrayList<RectF>();
        private final List<String> names = new ArrayList<String>();
        private boolean sym, more, shift;
        private int down = -1;
        private boolean repeated;   // the delete key already repeated: nothing more on release
        private final Runnable repeat = new Runnable() {
            @Override public void run() {
                if (down >= 0 && "DEL".equals(names.get(down))) { repeated = true; press("DEL", false, sym); h.postDelayed(this, 80); }
            }
        };

        SoftKeys() {
            super(EmbrithIME.this);
            setBackgroundColor(Color.WHITE);
        }

        private boolean showStrip() {
            return chinese && !sym && !password;
        }

        private int stripH() {
            return showStrip() ? Ui.dp(EmbrithIME.this, 28 + 48) : 0;
        }

        void reset(boolean symbols) {
            sym = symbols;
            more = false;
            shift = false;
            requestLayout();
            layout();
            invalidate();
        }

        /** The candidates or the mode changed. */
        void strip() {
            requestLayout();
            layout();
            invalidate();
        }

        /** Key width in units of one letter key (a letter row is 10 units, as on iOS). */
        private float weight(String k) {
            if ("SPACE".equals(k)) return 5f;
            if ("ENTER".equals(k)) return 2.5f;
            if ("SYM".equals(k) || "ABC".equals(k) || "MODE".equals(k)) return 1.25f;
            if ("SHIFT".equals(k) || "DEL".equals(k) || "MORE".equals(k) || "NUM".equals(k)) return 1.5f;
            return 1f;
        }

        private void layout() {
            rects.clear();
            names.clear();
            int top = stripH();
            if (top > 0) {                                      // candidates: 5 per page and a next-page button
                float cw = getWidth() / 5.6f, y0 = Ui.dp(EmbrithIME.this, 28), y1 = top;
                int begin = page * 5;
                for (int i = 0; i < 5 && begin + i < choices.size(); i++) {
                    rects.add(new RectF(i * cw, y0, (i + 1) * cw, y1));
                    names.add("C" + i);
                }
                if (choices.size() > 5) {
                    rects.add(new RectF(5 * cw, y0, getWidth(), y1));
                    names.add("NEXT");
                }
            }
            String[][] pg = sym ? (more ? symbols2 : symbols) : letters;
            float rowH = (getHeight() - top) / (float) pg.length;
            for (int r = 0; r < pg.length; r++) {
                // every row spans 10 units: the 9-letter row is inset by half a key on both sides, the keys between the
                // side keys of the third row share the rest, and the space bar sits in the middle of the bottom row
                float fixed = 0;
                int fill = 0;
                for (String k : pg[r]) { if (k.length() > 1) fixed += weight(k); else fill++; }
                float unit = r == 3 || pg[r].length == 9 || fill == 0 ? 1f : (10f - fixed) / fill;
                float x = 0;
                for (String k : pg[r]) {
                    float w = getWidth() * (k.length() > 1 ? weight(k) : unit) / 10f;
                    rects.add(new RectF(x, top + r * rowH, x + w, top + (r + 1) * rowH));
                    names.add(k);
                    x += w;
                }
            }
        }

        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            setMeasuredDimension(MeasureSpec.getSize(wSpec), stripH() + Ui.dp(EmbrithIME.this, 4 * 58 + 8));
        }

        @Override
        protected void onSizeChanged(int w, int h2, int ow, int oh) {
            layout();
            if (kbdShown && h2 > 0) Ctl.put("kbd", String.valueOf(h2));   // 候选栏出现或消失时高度会变
        }

        private String label(String k) {
            if ("SHIFT".equals(k)) return "⇧";
            if ("DEL".equals(k)) return "⌫";
            if ("ENTER".equals(k)) return "↵";
            if ("SYM".equals(k)) return "?123";
            if ("ABC".equals(k)) return "abc";
            if ("NUM".equals(k)) return "123";
            if ("MORE".equals(k)) return "#+=";
            if ("MODE".equals(k)) return chinese ? String.valueOf((char) 0x4e2d) : "EN";
            if ("SPACE".equals(k)) return "";
            if ("NEXT".equals(k)) return ">";
            if (k.charAt(0) == 'C' && k.length() == 2 && Character.isDigit(k.charAt(1))) return choices.get(page * 5 + (k.charAt(1) - '0'));
            return shift && !sym ? k.toUpperCase() : cn(k, sym);
        }

        private boolean isCandidate(String k) {
            return "NEXT".equals(k) || (k.length() == 2 && k.charAt(0) == 'C' && Character.isDigit(k.charAt(1)));
        }

        @Override
        protected void onDraw(Canvas c) {
            float m = Ui.dp(EmbrithIME.this, 3), sw = Ui.dp(EmbrithIME.this, 1.5f), rad = Ui.dp(EmbrithIME.this, 8);
            p.setTextAlign(Paint.Align.LEFT);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.BLACK);
            if (stripH() > 0) {                                  // the letters typed so far
                p.setTextSize(Ui.dp(EmbrithIME.this, 16));
                c.drawText(composing.toString(), Ui.dp(EmbrithIME.this, 10), Ui.dp(EmbrithIME.this, 20), p);
            }
            p.setTextAlign(Paint.Align.CENTER);
            for (int i = 0; i < rects.size(); i++) {
                String k = names.get(i);
                RectF r = rects.get(i);
                boolean cand = isCandidate(k);
                RectF b = new RectF(r.left + m, r.top + m, r.right - m, r.bottom - m);
                boolean on = i == down || ("SHIFT".equals(k) && shift);
                p.setTextSize(Ui.dp(EmbrithIME.this, cand ? 24 : 22));
                if (!cand || on) {
                    p.setStyle(on ? Paint.Style.FILL : Paint.Style.STROKE);
                    p.setStrokeWidth(sw);
                    p.setColor(Color.BLACK);
                    c.drawRoundRect(b, rad, rad, p);
                }
                p.setStyle(Paint.Style.FILL);
                p.setColor(on ? Color.WHITE : Color.BLACK);
                c.drawText(label(k), b.centerX(), b.centerY() - (p.ascent() + p.descent()) / 2, p);
            }
        }

        private int hit(float x, float y) {
            for (int i = 0; i < rects.size(); i++) if (rects.get(i).contains(x, y)) return i;
            return -1;
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            int act = e.getActionMasked();
            if (act == MotionEvent.ACTION_DOWN || act == MotionEvent.ACTION_MOVE) {
                int i = hit(e.getX(), e.getY());
                if (i != down) {
                    h.removeCallbacks(repeat);
                    down = i;
                    repeated = false;
                    invalidate();
                    if (i >= 0 && "DEL".equals(names.get(i))) h.postDelayed(repeat, 450);
                }
            } else if (act == MotionEvent.ACTION_UP) {
                int i = down;
                down = -1;
                h.removeCallbacks(repeat);
                if (i >= 0 && !repeated) fire(names.get(i));
                invalidate();
            } else if (act == MotionEvent.ACTION_CANCEL) {
                down = -1;
                h.removeCallbacks(repeat);
                invalidate();
            }
            return true;
        }

        private void fire(String k) {
            if ("SHIFT".equals(k)) {
                shift = !shift;
            } else if ("SYM".equals(k)) {
                finishRaw();
                sym = true;
                more = false;
                strip();
            } else if ("MORE".equals(k)) {
                more = true;
                strip();
            } else if ("NUM".equals(k)) {
                more = false;
                strip();
            } else if ("ABC".equals(k)) {
                sym = false;
                more = false;
                strip();
            } else if ("NEXT".equals(k)) {
                page++;
                if (page * 5 >= choices.size()) page = 0;
                strip();
            } else if (isCandidate(k)) {
                commitChoice(page * 5 + (k.charAt(1) - '0'));
            } else if ("MODE".equals(k)) {
                press(k, false, sym);
                strip();
            } else {
                press(k, shift && !sym, sym);
                if (shift && k.length() == 1) shift = false;
            }
        }
    }
}