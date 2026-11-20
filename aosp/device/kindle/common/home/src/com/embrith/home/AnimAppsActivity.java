/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * AnimAppsActivity: Settings page that lists the apps the user installed and lets the page turn animation be
 * switched off for each one. */
package com.embrith.home;

import android.view.View;
import android.widget.LinearLayout;

import java.util.List;

/** Settings > Display > Page turn animation: every app has the animation on; an unticked app (installed by the user) is listed as off. */
public class AnimAppsActivity extends WhiteAppsActivity {
    @Override protected String title() { return "翻页动画"; }
    @Override protected String note() { return "翻页动画\t每个应用默认都打开翻页动画. 取消勾选后, 从桌面进入这个应用时翻页不使用翻页动画, 改为按自动整屏闪的间隔刷新. 翻页动画不自然或黑块太多时可以关掉. 只对从桌面启动的应用有效."; }
    private static final String[] SYS_IDS = { "auto", "anim", "local" };
    private static final String[] SYS_TEXT = { "自动(默认)", "始终动画", "始终局部刷新" };

    /** The first row sets the policy for the pages of Embrith itself (WhiteBg.sysLocal). */
    @Override protected void extra(LinearLayout body) {
        String cur = Prefs.getStr(this, "sys_anim", "auto");
        int i = ChoiceActivity.indexOf(SYS_IDS, cur);
        LinearLayout g = Ui.addGroup(body);
        Ui.add(g, Ui.nav(this, "系统页面", SYS_TEXT[i >= 0 ? i : 0], new View.OnClickListener() {
            @Override public void onClick(View v) {
                ChoiceActivity.open(AnimAppsActivity.this, "系统页面翻页", SYS_IDS, SYS_TEXT, "pref", "sys_anim", "auto",
                    "自动\t设了壁纸并打开全局桌面应用时, 系统页面翻页改用局部刷新(只刷变化的文字, 不用翻页动画), 避免壁纸出现黑影; 没有壁纸时用翻页动画.\n\n始终动画\t一律用翻页动画.\n\n始终局部刷新\t一律不用翻页动画.");
            }
        }));
    }

    @Override protected List<String> load() { return WhiteBg.noAnim(this); }
    @Override protected void save(List<String> l) { WhiteBg.saveNoAnim(this, l); }
    @Override protected boolean isOn(List<String> l, String pkg) { return !l.contains(pkg); }
    @Override protected void setOn(List<String> l, String pkg, boolean on) {
        if (!on && !l.contains(pkg)) l.add(pkg);
        if (on) l.remove(pkg);
    }
}
