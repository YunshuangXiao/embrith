/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * WhiteAppsActivity: Settings list of the apps the user installed whose pages are shown on pure white (reading
 * background filter). */
package com.embrith.home;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.widget.LinearLayout;

import java.util.Collections;
import java.util.List;

/** Settings > Advanced > Reading background filter: the apps (installed by the user) whose pages are shown on pure white. AnimAppsActivity reuses the list with other hooks. */
public class WhiteAppsActivity extends Activity {
    /** hooks for AnimAppsActivity: page title, note, and how an app's switch is read and stored */
    protected String title() { return "阅读背景过滤"; }
    protected String note() { return "阅读背景过滤\t打开后, 从桌面进入这个应用时, 浅灰和米色的背景会显示为纯白, 深色字不变. 只对从桌面启动的应用有效. 灰度阈值在显示设置里调."; }
    protected List<String> load() { return WhiteBg.chosen(this); }
    protected void save(List<String> l) { WhiteBg.save(this, l); }
    protected boolean isOn(List<String> l, String pkg) { return l.contains(pkg); }
    protected void setOn(List<String> l, String pkg, boolean on) {
        if (on && !l.contains(pkg)) l.add(pkg);
        if (!on) l.remove(pkg);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout body = Ui.page(this, title(), null);
        LinearLayout list = Ui.addGroup(body);
        final List<String> chosen = load();
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(main, 0);
        Collections.sort(apps, new ResolveInfo.DisplayNameComparator(pm));
        int n = 0;
        for (ResolveInfo ri : apps) {
            ApplicationInfo ai = ri.activityInfo.applicationInfo;
            if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
            n++;
            final String pkg = ri.activityInfo.packageName;
            Ui.add(list, Ui.switchRow(this, String.valueOf(ri.loadLabel(pm)), isOn(chosen, pkg), new Ui.OnToggle() {
                @Override public void toggled(boolean on) {
                    setOn(chosen, pkg, on);
                    save(chosen);
                }
            }));
        }
        if (n == 0) Ui.add(list, Ui.row(this, "没有自己安装的应用", "", null));
        Ui.note(this, body, title(), note());
    }
}
