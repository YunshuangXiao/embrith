/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * Ui: Black and white building blocks in the style of iOS settings: grouped rounded frames, back row, switches; a
 * change updates only its own control. */
package com.embrith.home;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.embrith.language.T;

/**
 * 黑白基础件, 风格参考 iOS 设置: 圆角外框分组(只画框不填色), 首行返回, 开关按钮. 
 * 所有页面统一用 page() 建立: 首行是状态栏首行(设备名, 日期时间, 电量), 整行可点返回, 空白处点击无动作. 
 * 值变化只改对应控件(setValue / setOn), 不重建列表, 让屏幕只刷新变化的小块. 
 */
public final class Ui {
    private Ui() { }

    /** The corner radius of every outer frame (res/values/dimens.xml: embrith_radius). */
    public static int radius(Context c) {
        return c.getResources().getDimensionPixelSize(R.dimen.embrith_radius);
    }

    public static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    /** An invisible background (the default look of cells and buttons: no box). */
    static GradientDrawable box(Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.TRANSPARENT);
        return d;
    }

    /** The colour of all frame lines (a soft dark gray): frames are drawn 1 dp thin with it, the round buttons and level boxes 1.5 dp. */
    public static final int LINE = Color.rgb(0x55, 0x55, 0x55);

    /** A frame, for the few places that need one (a bar track, an input field). */
    static GradientDrawable frame(Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.WHITE);
        d.setStroke(dp(c, 1f), LINE);
        d.setCornerRadius(dp(c, 3));
        return d;
    }

    /** 分组外框: 圆角, 只有黑色描边, 不填底色.  */
    public static GradientDrawable outline(Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.TRANSPARENT);
        d.setStroke(dp(c, 1f), LINE);
        d.setCornerRadius(radius(c));
        return d;
    }

    /**
     * 选中/按下标记: 内缩的圆角黑框(不填色, 避免整行瞬间变黑). 必须用 setForeground: InsetDrawable 带内边距, 
     * 设为 background 会改掉控件的 padding, 内容跳动, 看起来像放大. 
     */
    static android.graphics.drawable.Drawable sel(Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.TRANSPARENT);
        d.setStroke(dp(c, 2), Color.BLACK);
        d.setCornerRadius(Math.max(0, radius(c) - dp(c, 3)));   // inside the frame: a little smaller
        int i = dp(c, 4);
        return new android.graphics.drawable.InsetDrawable(d, i, i, i, i);
    }

    /**
     * 按下反馈: 前景(文字/图标)按屏幕坐标做 50% 棋盘格变淡, 背景不动; 松开恢复原样.
     * 棋盘相位取屏幕坐标奇偶, 恢复后每个像素回到原值. DU 即可显示, 不需要灰阶波形.
     */
    public static void fade(View v, boolean on) {
        if (v instanceof IconView) {
            ((IconView) v).setFade(on);
        } else if (v instanceof TextView) {
            TextView t = (TextView) v;
            if (on) {
                int[] xy = new int[2];
                t.getLocationOnScreen(xy);
                t.getPaint().setShader(checker((xy[0] + xy[1]) & 1, false));
            } else {
                t.getPaint().setShader(null);
            }
            t.invalidate();
        } else if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) fade(g.getChildAt(i), on);
        }
    }

    /** 2x2 棋盘格着色器: 偶数格黑, 奇数格透明; erase 为真时相反(给 DST_OUT 擦除用). phase 是屏幕坐标奇偶. */
    static android.graphics.Shader checker(int phase, boolean erase) {
        android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(2, 2, android.graphics.Bitmap.Config.ARGB_8888);
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 2; x++) {
                boolean even = ((x + y) & 1) == 0;
                b.setPixel(x, y, even != erase ? Color.BLACK : Color.TRANSPARENT);
            }
        }
        android.graphics.BitmapShader s = new android.graphics.BitmapShader(b, android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT);
        android.graphics.Matrix m = new android.graphics.Matrix();
        m.setTranslate(phase, 0);
        s.setLocalMatrix(m);
        return s;
    }

    /** The text in the current language (Chinese in the code, English from the language table). */
    static String t(Context c, String s) {
        return T.t(c, s);
    }

    /** A space in English, nothing in Chinese: for texts joined from pieces of different languages. */
    static String gap(Context c) {
        return T.english(c) ? " " : "";
    }

    static java.util.Locale locale(Context c) {
        return T.locale(c);
    }

    /** TextView that sends every text set on it through the language table. */
    static final class TView extends TextView {
        TView(Context c) {
            super(c);
        }

        @Override
        public void setText(CharSequence text, BufferType type) {
            super.setText(text instanceof String ? T.t(getContext(), (String) text) : text, type);
        }
    }

    /** AlertDialog.Builder that sends titles, messages, buttons and list items through the language table. */
    public static final class Builder extends android.app.AlertDialog.Builder {
        private final Context ctx;

        public Builder(Context c) {
            super(c);
            ctx = c;
        }

        private CharSequence tr(CharSequence s) {
            return s instanceof String ? T.t(ctx, (String) s) : s;
        }

        @Override
        public android.app.AlertDialog.Builder setTitle(CharSequence title) {
            return super.setTitle(tr(title));
        }

        @Override
        public android.app.AlertDialog.Builder setMessage(CharSequence message) {
            return super.setMessage(tr(message));
        }

        @Override
        public android.app.AlertDialog.Builder setPositiveButton(CharSequence text, android.content.DialogInterface.OnClickListener l) {
            return super.setPositiveButton(tr(text), l);
        }

        @Override
        public android.app.AlertDialog.Builder setNegativeButton(CharSequence text, android.content.DialogInterface.OnClickListener l) {
            return super.setNegativeButton(tr(text), l);
        }

        @Override
        public android.app.AlertDialog.Builder setNeutralButton(CharSequence text, android.content.DialogInterface.OnClickListener l) {
            return super.setNeutralButton(tr(text), l);
        }

        /** Two buttons: side by side in one row, confirm (positive) on the left, cancel (negative) on the right (the default layout stacks them or puts cancel first). */
        @Override
        public android.app.AlertDialog create() {
            final android.app.AlertDialog d = super.create();
            d.setOnShowListener(new android.content.DialogInterface.OnShowListener() {
                @Override public void onShow(android.content.DialogInterface di) {
                    sideBySide(d);
                    plain(d);
                    if ("1".equals(Ctl.get("COLOR", "0"))) heavy(d);
                    View m = d.findViewById(android.R.id.message);
                    if (m instanceof TextView) ((TextView) m).setLineSpacing(dp(ctx, 4), 1f);   // some air between the lines of the message
                }
            });
            return d;
        }

        /** Color display: the texts of the dialog (title, message, list items, input) are black and have a heavier stroke, like Ui.text; the buttons keep their color. */
        private static void heavy(android.app.AlertDialog d) {
            heavy(d.getWindow().getDecorView());
            final android.widget.ListView lv = d.getListView();
            if (lv != null) lv.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener() {
                @Override public void onChildViewAdded(View parent, View child) { heavy(child); }   // the rows are made when the list is laid out
                @Override public void onChildViewRemoved(View parent, View child) { }
            });
        }

        private static void heavy(View v) {
            if (v instanceof TextView) {
                TextView t = (TextView) v;
                t.getPaint().setFakeBoldText(true);
                if (!(v instanceof android.widget.Button)) t.setTextColor(Color.BLACK);
            }
            if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v;
                for (int i = 0; i < g.getChildCount(); i++) heavy(g.getChildAt(i));
            }
        }

        /**
         * The buttons and the list of a dialog have no ripple and no gray highlight (every animation frame is a refresh of the e-ink panel):
         * while pressed the text fades, like on every other button, and nothing else changes.
         */
        private static void plain(android.app.AlertDialog d) {
            int[] ids = { android.content.DialogInterface.BUTTON_POSITIVE, android.content.DialogInterface.BUTTON_NEGATIVE,
                android.content.DialogInterface.BUTTON_NEUTRAL };
            accent(d, ids);
            for (int id : ids) {
                android.widget.Button b = d.getButton(id);
                if (b == null) continue;
                b.setBackground(null);
                b.setOnTouchListener(new View.OnTouchListener() {
                    @Override public boolean onTouch(View v, MotionEvent e) {
                        int act = e.getActionMasked();
                        if (act == MotionEvent.ACTION_DOWN) fade(v, true);
                        else if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) fade(v, false);
                        return false;
                    }
                });
            }
            final android.widget.ListView lv = d.getListView();
            if (lv == null) return;
            lv.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            lv.setOnTouchListener(new View.OnTouchListener() {
                private View cur;

                @Override public boolean onTouch(View v, MotionEvent e) {
                    int act = e.getActionMasked();
                    if (act == MotionEvent.ACTION_DOWN) {
                        int pos = lv.pointToPosition((int) e.getX(), (int) e.getY());
                        cur = pos < 0 ? null : lv.getChildAt(pos - lv.getFirstVisiblePosition());
                        if (cur != null) fade(cur, true);
                    } else if ((act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) && cur != null) {
                        fade(cur, false);
                        cur = null;
                    }
                    return false;
                }
            });
        }

        /** The accent color (settings, display) also goes to the AOSP side of a dialog: the button texts and the input fields (underline, selection). */
        private static void accent(android.app.AlertDialog d, int[] ids) {
            int a = Prefs.accent(d.getContext());
            if (a == Color.BLACK) return;   // black (or the color display off): the dialog theme already is black
            for (int id : ids) {
                android.widget.Button b = d.getButton(id);
                if (b != null) b.setTextColor(a);
            }
            tintInputs(d.getWindow().getDecorView(), a);
        }

        private static void tintInputs(View v, int a) {
            if (v instanceof android.widget.EditText) {
                android.widget.EditText e = (android.widget.EditText) v;
                e.setHighlightColor((a & 0xFFFFFF) | 0x55000000);
                if (e.getBackground() != null) e.getBackground().mutate().setColorFilter(a, android.graphics.PorterDuff.Mode.SRC_ATOP);
            } else if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v;
                for (int i = 0; i < g.getChildCount(); i++) tintInputs(g.getChildAt(i), a);
            }
        }

        private static void sideBySide(android.app.AlertDialog d) {
            android.widget.Button p = d.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
            android.widget.Button n = d.getButton(android.content.DialogInterface.BUTTON_NEGATIVE);
            android.widget.Button u = d.getButton(android.content.DialogInterface.BUTTON_NEUTRAL);
            if (p == null || n == null || p.getVisibility() != View.VISIBLE || n.getVisibility() != View.VISIBLE) return;
            if (u != null && u.getVisibility() == View.VISIBLE) return;        // three buttons: left as the system lays them out
            if (!(p.getParent() instanceof ViewGroup) || p.getParent() != n.getParent()) return;
            ViewGroup bar = (ViewGroup) p.getParent();
            LinearLayout row = new LinearLayout(d.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            bar.removeAllViews();
            p.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.LEFT);     // confirm at the left edge, cancel at the right edge
            n.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.RIGHT);
            row.addView(p, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(n, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            bar.addView(row, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        @Override
        public android.app.AlertDialog.Builder setItems(CharSequence[] items, android.content.DialogInterface.OnClickListener l) {
            CharSequence[] out = new CharSequence[items.length];
            for (int i = 0; i < items.length; i++) out[i] = tr(items[i]);
            return super.setItems(out, l);
        }
    }

    public static TextView text(Context c, String s, int sp, boolean bold) {
        TextView t = new TView(c);
        t.setText(s);
        t.setTextColor(Color.BLACK);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (bold || "1".equals(Ctl.get("COLOR", "0"))) t.getPaint().setFakeBoldText(true);   // color display: thin strokes turn into gray edge pixels, so every text gets a heavier stroke
        return t;
    }

    /** A thin black separator line. */
    public static View line(Context c) {
        View v = new View(c);
        v.setBackgroundColor(Color.BLACK);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 1))));
        return v;
    }

    // ------------------------------------------------------------------ cards

    /**
     * A framed card: an icon, a title and a sub line (empty = none). small: everything centred, icon above the title (for the
     * quick buttons); otherwise the icon is on the left and the texts on the right (for the entries). Fades while pressed.
     */
    public static final class Card extends LinearLayout {
        private final IconView icon;
        private final TextView sub;

        Card(Context c, int type, String title, String subText, boolean small) {
            super(c);
            setOrientation(small ? VERTICAL : HORIZONTAL);
            setGravity(small ? Gravity.CENTER_HORIZONTAL : Gravity.CENTER);   // an entry: icon and texts centred as a whole in the frame
            setClickable(true);
            setBackground(outline(c));
            int pad = dp(c, 10);
            setPadding(pad, pad, pad, pad);
            int is = dp(c, small ? 32 : 40);
            icon = new IconView(c, type);
            addView(icon, new LayoutParams(is, is));
            LinearLayout col = new LinearLayout(c);
            col.setOrientation(VERTICAL);
            col.setGravity(small ? Gravity.CENTER_HORIZONTAL : Gravity.LEFT);
            TextView t = text(c, title, small ? 16 : 19, true);
            t.setGravity(small ? Gravity.CENTER : Gravity.LEFT);
            col.addView(t);
            sub = text(c, "", 13, false);
            sub.setGravity(small ? Gravity.CENTER : Gravity.LEFT);
            // the sub line of an entry takes at most two lines (cut with an ellipsis when longer); entry heights are set by the parent weight;
            // the sub line of a quick button is a state, one line
            if (small) {
                sub.setMaxLines(1);
            } else {
                sub.setMaxLines(2);
            }
            sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
            col.addView(sub);
            LayoutParams lp = small ? new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    : new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (small) lp.topMargin = dp(c, 4); else lp.leftMargin = dp(c, 12);
            addView(col, lp);
            setSub(subText);
            setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent e) {
                    int act = e.getActionMasked();
                    if (act == MotionEvent.ACTION_DOWN) fade(Card.this, true);
                    else if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) fade(Card.this, false);
                    return false;
                }
            });
        }

        /** The sub line; empty hides it. */
        public void setSub(String s) {
            sub.setText(s == null ? "" : t(getContext(), s));
            sub.setVisibility(s == null || s.length() == 0 ? GONE : VISIBLE);
        }

        public void setOff(boolean off) {
            icon.setOff(off);
        }
    }

    public static Card card(Context c, int icon, String title, String sub, boolean small, View.OnClickListener l) {
        Card k = new Card(c, icon, title, sub, small);
        k.setOnClickListener(l);
        return k;
    }

    /** One row of equal-width cells; a null cell is an empty slot. */
    public static LinearLayout cardRow(Context c, int cols, View... cells) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setBaselineAligned(false);
        for (int i = 0; i < cols; i++) {
            View v = i < cells.length ? cells[i] : null;
            if (v == null) v = new View(c);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            lp.setMargins(dp(c, 5), dp(c, 5), dp(c, 5), dp(c, 5));
            r.addView(v, lp);
        }
        return r;
    }

    // ------------------------------------------------------------------ 页面与分组

    /**
     * 建立一个统一页面并 setContentView: 首行是状态栏首行(整行可点, back 为 null 时 finish; title 不再显示), 下面是可滚动的内容区. 
     * 返回内容区, 往里 add 分组. 
     */
    public static LinearLayout page(final Activity a, String title, View.OnClickListener back) {
        return page(a, title, back, null, true);
    }

    /**
     * Like page, with a fixed block under the header (top: it does not scroll) and, when pager is set, floating page up and page down
     * buttons on top of the content at the right edge (shown only when the content is longer than the area).
     */
    public static LinearLayout page(final Activity a, String title, View.OnClickListener back, View top, boolean pager) {
        return pageOf(a, header(a, title, back), top, pager, null, HEADER_DP);
    }

    /** The height of the header of the pages (name and time on one line); the first block of a page starts right under it. */
    static final int HEADER_DP = 48;

    /** Like page, with a header the caller built (68 dp high). */
    public static LinearLayout pageOf(final Activity a, View head, View top, boolean pager) {
        return pageOf(a, head, top, pager, null);
    }

    /** Like pageOf, with head null for a page without a header, and a fixed view at the very bottom (below the page buttons). */
    public static LinearLayout pageOf(final Activity a, View head, View top, boolean pager, View bottom) {
        return pageOf(a, head, top, pager, bottom, 68);
    }

    /** Like the one before, with the height of the header in dp. */
    public static LinearLayout pageOf(final Activity a, View head, View top, boolean pager, View bottom, int headDp) {
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        if (head != null) root.addView(head, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(a, headDp)));
        if (top != null) {
            LinearLayout wrap = new LinearLayout(a);
            wrap.setPadding(dp(a, 16), dp(a, 6), dp(a, 16), dp(a, 6));
            wrap.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            root.addView(wrap, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        final ScrollView sv = new LockScroll(a);
        final LinearLayout body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(a, 16), 0, dp(a, 16), dp(a, 20));
        sv.addView(body, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        if (bottom != null) root.addView(bottom, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        android.widget.FrameLayout wrap = new android.widget.FrameLayout(a);
        wrap.addView(root, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (pager) {
            Pager pg = new Pager(a, sv, body);
            android.widget.FrameLayout.LayoutParams pp = new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.RIGHT | Gravity.BOTTOM);
            FloatPos.place(a, pp);   // the stored place, the same on every page, by default at the right edge
            wrap.addView(pg, pp);
            FloatPos.attach(a, pg, pp, pg.grip());
            root.setTag(pg);
        }
        a.setContentView(wrap);
        return body;
    }

    /** The floating page buttons of a page built by pageOf with pager set (root = the page root, the parent of the scroll view); null when none. */
    public static Pager pager(View root) {
        Object t = root.getTag();
        return t instanceof Pager ? (Pager) t : null;
    }

    /** A round floating button like the page buttons; a long press does ll when it is not null. */
    public static View floatButton(Activity a, int icon, View.OnClickListener l, View.OnLongClickListener ll) {
        View v = Pager.round(a, icon, l);
        if (ll != null) v.setOnLongClickListener(ll);
        return v;
    }

    /** Page up above page down, round light background with a high transparency, on top of the content. */
    public static final class Pager extends LinearLayout {
        private final ScrollView sv;
        private final View body;
        private boolean allowed = true;
        private final View grip;

        Pager(final Activity a, final ScrollView sv, View body) {
            super(a);
            this.sv = sv;
            this.body = body;
            setOrientation(VERTICAL);
            grip = FloatPos.grip(a);
            addView(grip, FloatPos.gripParams(a));
            addView(floatButton(a, Icons.UP, new View.OnClickListener() {
                @Override public void onClick(View v) { sv.scrollBy(0, -(sv.getHeight() - dp(a, 40))); }
            }, new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) { sv.scrollTo(0, 0); return true; }   // to the top
            }));
            View down = floatButton(a, Icons.DOWN, new View.OnClickListener() {
                @Override public void onClick(View v) { sv.scrollBy(0, sv.getHeight() - dp(a, 40)); }
            }, new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) { sv.scrollTo(0, Math.max(0, Pager.this.body.getHeight() - sv.getHeight())); return true; }   // to the bottom
            });
            LayoutParams dlp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dlp.topMargin = dp(a, 10);
            addView(down, dlp);
            setVisibility(GONE);
            sv.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                @Override public void onGlobalLayout() { update(); }
            });
        }

        /** The grip on top of the buttons (FloatPos.attach makes it move the column). */
        public View grip() {
            return grip;
        }

        /** The grip is hidden when another column (the library tools) has a grip of its own. */
        public void setGripVisible(boolean on) {
            grip.setVisibility(on ? VISIBLE : GONE);
        }

        /** Turn the buttons off for a page that fills the screen (they still need a content longer than the area to show). */
        public void setAllowed(boolean on) {
            allowed = on;
            update();
        }

        private void update() {
            int vis = allowed && body.getHeight() > sv.getHeight() ? VISIBLE : GONE;
            if (getVisibility() != vis) setVisibility(vis);
        }

        private static View round(Activity a, int icon, View.OnClickListener l) {
            final android.widget.FrameLayout f = new android.widget.FrameLayout(a);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(Color.argb(0x66, 0xF0, 0xF0, 0xF0));
            f.setBackground(bg);
            f.setClickable(true);
            f.setOnClickListener(l);
            f.setOnTouchListener(new View.OnTouchListener() {   // pressed: the icon fades, the round background stays
                @Override public boolean onTouch(View v, MotionEvent e) {
                    int act = e.getActionMasked();
                    if (act == MotionEvent.ACTION_DOWN) fade(f, true);
                    else if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) fade(f, false);
                    return false;
                }
            });
            f.addView(new IconView(a, icon), new android.widget.FrameLayout.LayoutParams(dp(a, 30), dp(a, 30), Gravity.CENTER));
            f.setMinimumWidth(dp(a, 56));
            f.setMinimumHeight(dp(a, 56));
            return f;
        }
    }

    /** The scroll area of a page; locked = the page is one fixed screen and does not follow a vertical drag (like the desktop). */
    public static final class LockScroll extends ScrollView {
        private boolean locked;

        public LockScroll(Context c) { super(c); }

        public void setLocked(boolean locked) {
            this.locked = locked;
            if (locked) scrollTo(0, 0);
        }

        @Override public boolean onInterceptTouchEvent(MotionEvent e) { return !locked && super.onInterceptTouchEvent(e); }

        @Override public boolean onTouchEvent(MotionEvent e) { return !locked && super.onTouchEvent(e); }
    }

    /**
     * 状态栏首行: 左边设备名, 下面日期和时间, 右边电量百分比和图标; 时间在窗口可见时每分钟刷新一次, 电量只在状态变化时刷新.
     * full = false (the header of the pages): the name on the left, only the time (HH:mm, the size of the former second row) on the right.
     */
    static final class StatusRow extends LinearLayout {
        private final boolean full;
        private final TextView when;
        private TextView batt;
        private IconView battIcon;
        private final Tick tick = new Tick(new Runnable() {
            @Override public void run() { refresh(); }
        }, 60000);
        private final StatusBus.Listener bus = new StatusBus.Listener() {
            @Override public void changed() { if (getWindowVisibility() == VISIBLE) refresh(); }
        };

        StatusRow(Context c) {
            this(c, true);
        }

        StatusRow(Context c, boolean full) {
            super(c);
            this.full = full;
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(Frames.edge(c), 0, Frames.edge(c), 0);   // the row ends on the frame lines (page margin)
            LinearLayout info = new LinearLayout(c);
            info.setOrientation(VERTICAL);
            info.addView(text(c, Prefs.deviceName(c), 18, true));
            when = text(c, "", 15, false);
            if (full) info.addView(when);
            addView(info, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (!full) {
                addView(when, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                refresh();
                return;
            }
            batt = text(c, "", 15, false);
            batt.setIncludeFontPadding(false);   // the line box is ascent..descent: the centre of the digits follows from the font metrics
            android.graphics.Rect tb = new android.graphics.Rect();
            batt.getPaint().getTextBounds("0", 0, 1, tb);   // a digit: its height and its centre
            android.graphics.Paint.FontMetrics fm = batt.getPaint().getFontMetrics();
            batt.setTranslationY((fm.ascent + fm.descent) / 2f - (tb.top + tb.bottom) / 2f);   // the centre of the digits on the centre of the row
            addView(batt);
            battIcon = new IconView(c, Icons.BATTERY);
            int side = Math.round(tb.height() * 24f / (14f * 0.92f));   // the body of the icon (14 of 24, drawn at 0.92 of the view) is as high as a digit
            LayoutParams bp = new LayoutParams(side, side);
            bp.leftMargin = dp(c, 6);
            addView(battIcon, bp);
            refresh();
        }

        void refresh() {
            Context c = getContext();
            if (full) {
                java.util.Map<String, String> st = Ctl.status();
                int pct = Ctl.statusInt(st, "BATT", -1);
                batt.setText(pct >= 0 ? pct + "%" : "");
                battIcon.setLevel(pct);
                battIcon.setCharging("1".equals(st.get("CHG")));
            }
            String fmt = full ? t(c, Prefs.clock24(c) ? "M月d日 EEEE  HH:mm" : "M月d日 EEEE  h:mm a") : (Prefs.clock24(c) ? "HH:mm" : "h:mm a");
            String now = new java.text.SimpleDateFormat(fmt, locale(c)).format(new java.util.Date());
            if (!now.contentEquals(when.getText())) when.setText(now);
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (full) StatusBus.listen(bus, "BATT", "CHG");   // the battery: only when its values change (plug, unplug, a percent)
        }

        @Override protected void onWindowVisibilityChanged(int visibility) {
            super.onWindowVisibilityChanged(visibility);
            if (visibility == VISIBLE) tick.start(); else tick.stop();   // the time: a beat at every full minute, only while the row is on the screen
        }

        @Override protected void onDetachedFromWindow() {
            StatusBus.unlisten(bus);
            tick.stop();
            super.onDetachedFromWindow();
        }
    }

    /** 子页面首行: 状态栏首行, 整行可点返回(back 为 null 时 finish); 按下时变淡. title 不再显示. */
    static LinearLayout header(final Activity a, String title, View.OnClickListener back) {
        final LinearLayout h = new StatusRow(a, false);
        h.setClickable(true);
        h.setOnClickListener(back != null ? back : new View.OnClickListener() {
            @Override public void onClick(View v) { a.finish(); }
        });
        h.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                int act = e.getActionMasked();
                if (act == MotionEvent.ACTION_DOWN) fade(h, true);
                else if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) fade(h, false);
                return false;
            }
        });
        return h;
    }

    /** 分组: 圆角外框, 子项按圆角裁切. orientation 用 LinearLayout 常量.  */
    static LinearLayout group(Context c, int orientation) {
        LinearLayout g = new LinearLayout(c);
        g.setOrientation(orientation);
        g.setBackground(outline(c));
        g.setClipToOutline(true);
        g.setTag("group");
        int p = dp(c, 1.5f);
        g.setPadding(p, p, p, p);
        return g;
    }

    static LinearLayout group(Context c) {
        return group(c, LinearLayout.VERTICAL);
    }

    /** 把分组加到页面内容区(上下留白).  */
    public static LinearLayout addGroup(LinearLayout body, LinearLayout g) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = body.getChildCount() == 0 ? 0 : dp(body.getContext(), 6);   // the first block touches the header: no gap
        lp.bottomMargin = dp(body.getContext(), 10);
        body.addView(g, lp);
        return g;
    }

    /** 新建竖向分组并加到页面.  */
    public static LinearLayout addGroup(LinearLayout body) {
        return addGroup(body, group(body.getContext()));
    }

    /** 分组标题(框外上方小字).  */
    public static TextView caption(LinearLayout body, String s) {
        TextView t = text(body.getContext(), s, 15, false);
        t.setPadding(dp(body.getContext(), 18), body.getChildCount() == 0 ? 0 : dp(body.getContext(), 12), dp(body.getContext(), 18), 0);
        body.addView(t);
        return t;
    }

    /** Builds {first body, full text with bold subtitles} from infos; an info is "subtitle" + TAB + "body" or just "body". */
    private static CharSequence[] compose(Activity a, String... infos) {
        android.text.SpannableStringBuilder full = new android.text.SpannableStringBuilder();
        String first = null;
        for (String s : infos) {
            if (s == null || s.length() == 0) continue;
            int tab = s.indexOf('\t');
            String text = t(a, tab >= 0 ? s.substring(tab + 1) : s);
            if (full.length() > 0) {   // paragraph gap: an empty line at a small text size, less than a full blank line
                full.append("\n");
                int g0 = full.length();
                full.append(" ");
                full.setSpan(new android.text.style.AbsoluteSizeSpan(8, true), g0, full.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                full.append("\n");
            }
            if (tab > 0) {
                int from = full.length();
                full.append(t(a, s.substring(0, tab)));
                full.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), from, full.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                full.append("\n");
            }
            full.append(text);
            if (first == null) first = text;
        }
        return new CharSequence[] { first, full };
    }

    /**
     * 分组标题栏加圆角框: 左边标题, 有说明(infos)时框下是脚注(见 footnote; 多段用空行隔开, 每段各自走语言表). 返回圆角框(已加入 body).
     * 一段说明可以写成 "小标题" + TAB + "正文": 弹窗里小标题加粗放在正文上面, 标明这一段说的是哪个功能; 框下的一行显示第一段的正文.
     */
    public static LinearLayout section(final Activity a, LinearLayout body, final String title, String... infos) {
        CharSequence[] fp = compose(a, infos);
        CharSequence full = fp[1];
        String first = fp[0] == null ? null : fp[0].toString();
        LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(android.view.Gravity.CENTER_VERTICAL);
        head.setPadding(dp(a, 18), dp(a, 8), dp(a, 10), 0);
        head.addView(text(a, title, 15, false), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        body.addView(head, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout g = addGroup(body);
        if (full.length() > 0) {
            ((LinearLayout.LayoutParams) g.getLayoutParams()).bottomMargin = dp(a, 4);
            body.addView(footnote(a, title, first, full));
        }
        return g;
    }

    /** 分组说明(框外下方小字).  */
    static TextView footer(LinearLayout body, String s) {
        TextView t = text(body.getContext(), s, 14, false);
        t.setPadding(dp(body.getContext(), 18), 0, dp(body.getContext(), 18), dp(body.getContext(), 8));
        body.addView(t);
        return t;
    }

    /**
     * 脚注(框下小字, 参考 iOS): 一行放得下就显示全文; 放不下或有多段时只显示第一行, 右边出现"详情 >", 点击弹出全文.
     * msg 已经过语言表.
     */
    static View footnote(final Activity a, final String title, final String msg) {
        return footnote(a, title, msg, msg);
    }

    /** msg is the text of the line under the box, full is shown in the pop-up (it may carry bold subtitles, see section). */
    static View footnote(final Activity a, final String title, final String msg, final CharSequence full) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(a, 18), 0, dp(a, 18), dp(a, 8));
        final TextView tv = text(a, msg, 14, false);
        tv.setMaxLines(1);
        tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final TextView more = text(a, "详情 >", 14, true);
        more.setPadding(dp(a, 10), 0, 0, 0);
        more.setVisibility(View.GONE);
        row.addView(more);
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (more.getVisibility() != View.VISIBLE) return;
                new Builder(a).setTitle(title == null || title.length() == 0 ? "说明" : title).setMessage(full).setPositiveButton("好", null).show();
            }
        });
        tv.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                android.text.Layout l = tv.getLayout();
                if (l == null || tv.getWidth() == 0 || more.getVisibility() == View.VISIBLE) return;
                if (full.toString().indexOf('\n') >= 0 || l.getEllipsisCount(0) > 0) more.setVisibility(View.VISIBLE);
            }
        });
        return row;
    }

    /** 页面说明: 脚注形式放在它前面那个框的下面; 同一页可以有多条. title 只用作弹窗标题(空则"说明"). s 里多段用空行隔开, 每段可写成 "小标题" + TAB + "正文". */
    public static TextView note(Activity a, LinearLayout body, String title, String s) {
        int n = body.getChildCount();
        if (n > 0 && "group".equals(body.getChildAt(n - 1).getTag())) {
            ((LinearLayout.LayoutParams) body.getChildAt(n - 1).getLayoutParams()).bottomMargin = dp(a, 4);
        }
        CharSequence[] fp = compose(a, s.split("\n\n"));
        body.addView(footnote(a, title, fp[0] == null ? "" : fp[0].toString(), fp[1]));
        return null;
    }

    /** 加一行到分组; 第二行起上面画一条很浅的横线, 两边各缩进一点, 不顶到框; 相邻行带 > 时右端停在 > 之前. */
    public static void add(LinearLayout g, View row) {
        if ("group".equals(g.getTag()) && g.getOrientation() == LinearLayout.VERTICAL && g.getChildCount() > 0) {
            Context c = g.getContext();
            View d = new View(c);
            d.setBackgroundColor(Color.rgb(0xEC, 0xEC, 0xEC));
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 1)));
            dlp.leftMargin = dp(c, 16);
            dlp.rightMargin = hasChevron(g.getChildAt(g.getChildCount() - 1)) || hasChevron(row) ? chevInset(c) : dp(c, 16);
            g.addView(d, dlp);
        }
        g.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /** Like add, without the line above (a slider under its title or under another row). */
    public static void addPlain(LinearLayout g, View row) {
        g.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /** Distance from the right edge of a row to the left edge of its arrow icon: the row padding, the margin before it and the icon itself. The line stops there, so it covers the value text. */
    private static int chevInset(Context c) {
        return dp(c, 18) + dp(c, 6) + dp(c, 20);
    }

    private static boolean hasChevron(View v) {
        return v instanceof Row && ((Row) v).chev != null && ((Row) v).chev.getVisibility() == View.VISIBLE;    }

    // ------------------------------------------------------------------ 按钮

    /** 平面文字按钮: 无框; 按下或选中(setInverted, 沿用旧名)时显示内缩圆角黑框, 选中时文字加粗.  */
    public static final class FlatButton extends Button {
        private boolean inverted;
        private boolean noFrame;

        @Override
        public void setText(CharSequence text, BufferType type) {
            super.setText(text instanceof String ? T.t(getContext(), (String) text) : text, type);
        }

        FlatButton(Context c) {
            super(c);
        }

        void setInverted(boolean on) {
            inverted = on;
            paint(isPressed());
        }

        /** 选中时文字加粗, 底部加一个小黑点, 不画圆角框. */
        FlatButton noFrame() {
            noFrame = true;
            return this;
        }

        /** 选中(inverted)仍画圆角框; 按下只让文字变淡. */
        private void paint(boolean pressed) {
            getPaint().setFakeBoldText(inverted);
            setForeground(inverted && !noFrame ? sel(getContext()) : null);
            fade(this, pressed);
            invalidate();
        }

        @Override
        public void setPressed(boolean pressed) {
            super.setPressed(pressed);
            paint(pressed);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (inverted && noFrame) {
                float d = getResources().getDisplayMetrics().density;
                canvas.drawCircle(getWidth() / 2f, getHeight() - 6 * d, 2.5f * d, DOT);
            }
        }
    }

    private static final Paint DOT = new Paint(Paint.ANTI_ALIAS_FLAG);

    static {
        DOT.setColor(Color.BLACK);
    }

    public static FlatButton button(Context c, String s) {
        FlatButton b = new FlatButton(c);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(Color.BLACK);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        b.setGravity(Gravity.CENTER);
        b.setBackground(null);
        b.setStateListAnimator(null);
        b.setMinHeight(dp(c, 48));
        b.setMinimumHeight(dp(c, 48));
        b.setPadding(dp(c, 10), dp(c, 6), dp(c, 10), dp(c, 6));
        return b;
    }

    // ------------------------------------------------------------------ 行

    /** 整行可点的行: 手指拖动超过 slop*2 后取消按下状态, 横滑翻页或竖滑滚动都不会触发单击. */
    static class TapRow extends LinearLayout {
        private float downX, downY;
        private boolean dragged;

        TapRow(Context c) {
            super(c);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                downX = e.getX();
                downY = e.getY();
                dragged = false;
            } else if (a == MotionEvent.ACTION_MOVE && !dragged) {
                int s = android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop() * 2;
                if (Math.abs(e.getX() - downX) > s || Math.abs(e.getY() - downY) > s) {
                    dragged = true;
                    MotionEvent c = MotionEvent.obtain(e);
                    c.setAction(MotionEvent.ACTION_CANCEL);
                    super.onTouchEvent(c);
                    c.recycle();
                }
            }
            return dragged || super.onTouchEvent(e);
        }
    }

    /** 设置行: 左标签, 右值(可选 ›), 按下显示圆角框. setValue 只改右侧文字.  */
    public static final class Row extends TapRow {
        final TextView label, value;
        View chev;

        Row(Context c) {
            super(c);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setMinimumHeight(dp(c, 64));
            setPadding(dp(c, 18), dp(c, 10), dp(c, 18), dp(c, 10));
            label = text(c, "", 18, false);
            value = text(c, "", 16, false);
            value.setGravity(Gravity.RIGHT);
            LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            vp.leftMargin = dp(c, 12);
            addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            addView(value, vp);
        }

        public void setValue(String s) {
            String n = t(getContext(), s == null ? "" : s);
            if (!n.contentEquals(value.getText())) value.setText(n);
        }

        public void setLabel(String s) {
            String n = t(getContext(), s);
            if (!n.contentEquals(label.getText())) label.setText(n);
        }

        public void setChevron(boolean on) {
            if (on && chev == null) {
                chev = new IconView(getContext(), Icons.RIGHT);
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(getContext(), 20), dp(getContext(), 20));
                cp.leftMargin = dp(getContext(), 6);
                addView(chev, cp);
            } else if (chev != null) {
                chev.setVisibility(on ? VISIBLE : GONE);
            }
        }

        private void paint(boolean on) {
            fade(this, on);
        }

        public void setListener(View.OnClickListener l) {
            if (l == null) return;
            setClickable(true);
            setOnClickListener(l);
            setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent e) {
                    int act = e.getActionMasked();
                    if (act == MotionEvent.ACTION_DOWN) paint(true);
                    else if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) paint(false);
                    return false;
                }
            });
        }
    }

    /** A settings row: label on the left, value on the right, a rounded frame while touched. A null listener makes it an info row. */
    public static Row row(Context c, String label, String value, View.OnClickListener l) {
        Row r = new Row(c);
        r.setLabel(label);
        r.setValue(value);
        r.setListener(l);
        return r;
    }

    /** 进入下一级的行(右侧带 ›).  */
    public static Row nav(Context c, String label, String value, View.OnClickListener l) {
        Row r = row(c, label, value, l);
        r.setChevron(true);
        return r;
    }

    // ------------------------------------------------------------------ 开关

    /** iOS 式开关: 开 = 黑色轨道 + 右侧白色圆钮; 关 = 描边轨道 + 左侧描边圆钮.  */
    static final class Toggle extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private boolean on;

        Toggle(Context c) {
            super(c);
        }

        boolean isOn() {
            return on;
        }

        void setOn(boolean v) {
            if (v == on) return;
            on = v;
            invalidate();
        }

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension(dp(getContext(), 54), dp(getContext(), 32));
        }

        @Override
        protected void onDraw(Canvas c) {
            float s = dp(getContext(), 1f);
            float w = getWidth(), h = getHeight();
            r.set(s / 2, s / 2, w - s / 2, h - s / 2);
            float rad = r.height() / 2;
            float kr = rad - dp(getContext(), 3);
            float cx = on ? r.right - rad : r.left + rad, cy = h / 2;
            paint.setStrokeWidth(s);
            if (on) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Prefs.accent(getContext()));
                c.drawRoundRect(r, rad, rad, paint);
                paint.setColor(Color.WHITE);
                c.drawCircle(cx, cy, kr, paint);
            } else {
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(LINE);
                c.drawRoundRect(r, rad, rad, paint);
                c.drawCircle(cx, cy, kr, paint);
            }
        }
    }

    /** 勾选框(Kindle 风格的方框 + 勾), 点整行切换; 回调给出新状态. enabled=false 时只显示, 不可点(属性不支持).  */
    static final class CheckRow extends TapRow {
        private final View box;
        private boolean on;
        final TextView label;

        CheckRow(Context c, String text) {
            super(c);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(dp(c, 4), dp(c, 6), dp(c, 4), dp(c, 6));
            box = new View(c) {
                private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                @Override protected void onDraw(Canvas cv) {
                    float sw = dp(getContext(), 1f), w = getWidth(), h = getHeight();
                    p.setColor(isEnabled() ? LINE : Color.GRAY);
                    p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(sw);
                    p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
                    cv.drawRoundRect(new RectF(sw, sw, w - sw, h - sw), dp(getContext(), 3), dp(getContext(), 3), p);
                    p.setColor(isEnabled() ? Prefs.accent(getContext()) : Color.GRAY);   // the mark inside: the accent color (black unless the color display is on)
                    if (on && index > 0) {
                        p.setStyle(Paint.Style.FILL);
                        p.setTextSize(h * 0.62f);
                        p.setTextAlign(Paint.Align.CENTER);
                        cv.drawText(String.valueOf(index), w / 2, h / 2 - (p.ascent() + p.descent()) / 2, p);
                    } else if (on) {
                        p.setStrokeWidth(dp(getContext(), 2.4f));
                        android.graphics.Path ck = new android.graphics.Path();
                        ck.moveTo(w * 0.24f, h * 0.52f); ck.lineTo(w * 0.43f, h * 0.70f); ck.lineTo(w * 0.77f, h * 0.30f);
                        cv.drawPath(ck, p);
                    }
                }
            };
            addView(box, new LinearLayout.LayoutParams(dp(c, 24), dp(c, 24)));
            label = text(c, text, 15, false);
            label.setPadding(dp(c, 10), 0, 0, 0);
            addView(label);
        }

        boolean isOn() { return on; }

        private int index;
        TextView info;

        /** Instead of a check mark the box shows this number (0 = the check mark). */
        void setIndex(int i) {
            if (i != index) { index = i; box.invalidate(); }
        }

        /** The look of a settings row: 18sp label, 64dp high, a small text on the right. */
        void roomy(String infoText) {
            Context c = getContext();
            setMinimumHeight(dp(c, 64));
            setPadding(dp(c, 18), dp(c, 10), dp(c, 18), dp(c, 10));
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            label.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            info = text(c, infoText, 16, false);
            addView(info);
        }

        void setOn(boolean v) {
            if (v == on) return;
            on = v;
            box.invalidate();
        }

        void setUsable(boolean u) {
            setEnabled(u);
            box.setEnabled(u);
            label.setTextColor(u ? Color.BLACK : Color.GRAY);
            box.invalidate();
        }
    }

    /** A check row in the look of a settings row, with an info text on the right (used by the widget list). */
    static CheckRow indexRow(Context c, String text, String info, boolean on, OnToggle cb) {
        CheckRow r = checkRow(c, text, on, cb);
        r.roomy(info);
        return r;
    }

    static CheckRow checkRow(Context c, String text, boolean on, final OnToggle cb) {
        final CheckRow r = new CheckRow(c, text);
        r.setOn(on);
        r.setClickable(true);
        r.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!r.isEnabled()) return;
                boolean n = !r.isOn();
                r.setOn(n);
                if (cb != null) cb.toggled(n);
            }
        });
        return r;
    }

    /** 提示条: 代替系统 Toast(半透明深色), 纯白不透明圆角框, 黑字, 与弹出框同一风格.  */
    public static void toast(Context c, String msg, boolean longer) {
        TextView t = text(c, msg, 16, false);
        t.setBackgroundResource(R.drawable.embrith_toast);
        t.setPadding(dp(c, 20), dp(c, 12), dp(c, 20), dp(c, 12));
        t.setLineSpacing(dp(c, 4), 1f);
        android.widget.Toast to = new android.widget.Toast(c.getApplicationContext());
        to.setView(t);
        to.setDuration(longer ? android.widget.Toast.LENGTH_LONG : android.widget.Toast.LENGTH_SHORT);
        to.show();
    }

    public interface OnToggle {
        void toggled(boolean on);
    }

    /** 开关行: 点整行切换开关(行不反白), 回调给出新状态. setNote 在开关左侧放一段小字(如"未安装").  */
    public static final class SwitchRow extends TapRow {
        final TextView label, note;
        final Toggle toggle;

        SwitchRow(Context c) {
            super(c);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setMinimumHeight(dp(c, 64));
            setPadding(dp(c, 18), dp(c, 10), dp(c, 18), dp(c, 10));
            label = text(c, "", 18, false);
            note = text(c, "", 16, false);
            toggle = new Toggle(c);
            addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            np.rightMargin = dp(c, 12);
            addView(note, np);
            addView(toggle);
        }

        boolean isOn() {
            return toggle.isOn();
        }

        void setOn(boolean v) {
            toggle.setOn(v);
        }

        void setNote(String s) {
            String n = t(getContext(), s == null ? "" : s);
            if (!n.contentEquals(note.getText())) note.setText(n);
        }
    }

    public static SwitchRow switchRow(Context c, String label, boolean on, final OnToggle cb) {
        final SwitchRow r = new SwitchRow(c);
        r.label.setText(label);
        r.toggle.setOn(on);
        r.setClickable(true);
        r.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean n = !r.isOn();
                r.setOn(n);
                if (cb != null) cb.toggled(n);
            }
        });
        return r;
    }
}
