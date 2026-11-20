/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * AppsActivity: Lists the apps the user installed; a tap offers to uninstall one through the system confirmation
 * screen. */
package com.embrith.home;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;

import java.util.Collections;
import java.util.List;

/** The apps the user installed; a tap offers to uninstall one (the system's own confirmation screen). */
public class AppsActivity extends Activity {
    private LinearLayout list;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout body = Ui.page(this, "应用管理", null);
        list = Ui.addGroup(body);
    }

    @Override
    protected void onResume() {
        super.onResume();
        list.removeAllViews();
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
            Ui.add(list, Ui.row(this, String.valueOf(ri.loadLabel(pm)), "卸载", new View.OnClickListener() {
                @Override public void onClick(View v) {
                    startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg)));
                }
            }));
        }
        Ctl.setAppCount(n);   // coming back from the uninstall screen: the exact number, also 0
        Ctl.setAppCount(n);   // coming back from the uninstall screen: the exact number, also 0
        Ctl.setAppCount(n);   // coming back from the uninstall screen: the exact number, also 0
        if (n == 0) Ui.add(list, Ui.row(this, "没有自己安装的应用", "", null));
    }
}
