/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * IconButton: An icon with a label (below it or next to it); the icon and the label fade while it is pressed. */
package com.embrith.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/** An icon with a label (below it, or next to it). No frame; the icon and the label fade while it is pressed. Content is centred. */
public final class IconButton extends LinearLayout {
    private final IconView icon;
    private final TextView label;
    private boolean noFade;
    private boolean dot;
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public IconButton(Context c, int iconType, String text, boolean vertical, int iconDp, int textSp) {
        super(c);
        setOrientation(vertical ? VERTICAL : HORIZONTAL);
        setGravity(vertical ? Gravity.CENTER_HORIZONTAL : Gravity.CENTER_VERTICAL);
        setClickable(true);
        int pad = Ui.dp(c, vertical ? 6 : 8);
        setPadding(pad, pad, pad, pad);
        icon = new IconView(c, iconType);
        addView(icon, new LayoutParams(Ui.dp(c, iconDp), Ui.dp(c, iconDp)));
        label = new TextView(c);
        label.setText(Ui.t(c, text));
        label.setTextColor(Color.BLACK);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp);
        label.setGravity(Gravity.CENTER);
        LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (vertical) lp.topMargin = Ui.dp(c, 4); else lp.leftMargin = Ui.dp(c, 6);
        addView(label, lp);
        if (text == null || text.length() == 0) label.setVisibility(GONE);
    }

    /** No press feedback (for buttons that sit on the wallpaper: the checker fade on gray makes the refresh stripy). */
    public void setNoFade() {
        noFade = true;
    }

    void setLabel(String s) {
        label.setText(Ui.t(getContext(), s));
    }

    /** The text under the icon in bold (the tab shown). */
    public void setBold(boolean bold) {
        label.getPaint().setFakeBoldText(bold);
        label.invalidate();
    }

    /** A small black dot under the button (the tab shown), like the running mark of the macOS Dock. */
    public void setDot(boolean on) {
        if (dot == on) return;
        dot = on;
        invalidate();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (dot) {
            float d = getResources().getDisplayMetrics().density;
            dotPaint.setColor(Color.BLACK);
            canvas.drawCircle(icon.getLeft() + icon.getWidth() / 2f, getHeight() - 7 * d, 2.5f * d, dotPaint);
        }
    }

    void setOff(boolean off) {
        icon.setOff(off);
    }

    /** A disabled button shows its icon gray. */
    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        icon.setColor(enabled ? Color.BLACK : Color.GRAY);
    }

    public void setIcon(int type) {
        icon.setType(type);
    }

    @Override
    public void setPressed(boolean pressed) {
        super.setPressed(pressed);
        if (!noFade) Ui.fade(this, pressed);   // 前景棋盘格变淡, 背景(壁纸)不动
    }
}
