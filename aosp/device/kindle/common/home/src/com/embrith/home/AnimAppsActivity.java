/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * AnimAppsActivity: Settings page that lists the apps the user installed and lets the page turn animation be
 * switched off for each one. */
package com.embrith.home;

import java.util.List;

/** Settings > Display > Page turn animation: every app has the animation on; an unticked app (installed by the user) is listed as off. */
public class AnimAppsActivity extends WhiteAppsActivity {
    @Override protected String title() { return "翻页动画"; }
    @Override protected String note() { return "翻页动画\t每个应用默认都打开翻页动画. 取消勾选后, 从桌面进入这个应用时翻页不使用翻页动画, 改为按自动整屏闪的间隔刷新. 翻页动画不自然或黑块太多时可以关掉. 只对从桌面启动的应用有效."; }
    @Override protected List<String> load() { return WhiteBg.noAnim(this); }
    @Override protected void save(List<String> l) { WhiteBg.saveNoAnim(this, l); }
    @Override protected boolean isOn(List<String> l, String pkg) { return !l.contains(pkg); }
    @Override protected void setOn(List<String> l, String pkg, boolean on) {
        if (!on && !l.contains(pkg)) l.add(pkg);
        if (on) l.remove(pkg);
    }
}
