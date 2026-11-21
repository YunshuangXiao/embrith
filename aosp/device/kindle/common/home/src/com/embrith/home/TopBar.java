/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * TopBar: The top bar of the desktop and of the settings home: the name on the left, three buttons on the right (refresh, clean up, sleep). */
package com.embrith.home;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** The same bar on the desktop and on the settings home (68 dp high when it is added: the caller gives the height). */
final class TopBar {
    private TopBar() { }

    static FrameLayout build(final Activity a) {
        // top bar: embrith (bold) | power
        FrameLayout bar = new FrameLayout(a);
        TextView title = Ui.text(a, "Embrith · 余烬", 26, true);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.LEFT | Gravity.CENTER_VERTICAL);
        tp.leftMargin = Frames.edge(a);   // the title starts on the left frame line (page margin)
        bar.addView(title, tp);
        // power button: sleep only (same as the power key)
        IconButton power = new IconButton(a, Icons.LOCK, "", true, 30, 11);
        power.setContentDescription("休眠");
        power.setPadding(Ui.dp(a, 12), 0, Ui.dp(a, 12), 0);
        power.setGravity(Gravity.CENTER);
        power.setNoFade();
        power.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { Ctl.request("sleep"); }
        });
        // refresh button: full screen flash to clear ghosting (left of the power button)
        IconButton refresh = new IconButton(a, Icons.REFRESH, "", true, 30, 11);
        refresh.setContentDescription("刷新");
        refresh.setPadding(Ui.dp(a, 12), 0, Ui.dp(a, 12), 0);
        refresh.setGravity(Gravity.CENTER);
        refresh.setNoFade();
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Ctl.request("clear"); }
        });
        // right side: refresh and power buttons
        LinearLayout right = new LinearLayout(a);
        right.setOrientation(LinearLayout.HORIZONTAL);
        // clean-up button: free the background apps, the same as Settings > System > Memory management (between refresh and sleep)
        IconButton clean = new IconButton(a, Icons.BROOM, "", true, 30, 11);
        clean.setContentDescription("清理");
        clean.setPadding(Ui.dp(a, 12), 0, Ui.dp(a, 12), 0);
        clean.setGravity(Gravity.CENTER);
        clean.setNoFade();
        clean.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { MenuActivity.freeMemory(a, null); }
        });
        right.addView(refresh, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        right.addView(clean, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        right.addView(power, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        FrameLayout.LayoutParams mp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        mp.rightMargin = Frames.edge(a) - Ui.dp(a, 12);   // minus the 12 padding of the icon: the icon ends on the right frame line
        bar.addView(right, mp);

        return bar;
    }
}