/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * HomeActivity: The desktop: title, power button, the apps the user installed and the bottom bar; with no apps a
 * big plus opens an apk picker. */
package com.embrith.home;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.embrith.library.LibraryActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The desktop: "embrith" (bold) top left, the power button top right, the apps the user installed and the bottom bar (Dock). With no apps there is a big "+":
 * it opens a file picker on the Kindle's storage to install an apk. The settings home is pulled down from the top edge.
 */
public class HomeActivity extends LibraryActivity {
    private final List<ResolveInfo> apps = new ArrayList<ResolveInfo>();
    private BaseAdapter adapter;
    private View emptyBox;
    private HomeBody homeBody;   // the widget area and the app block (block 1) below the top row (block 0)
    // before the user is unlocked (Direct Boot) the package manager does not list the apps the user installed: draw them when it is unlocked
    private final BroadcastReceiver unlocked = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { refresh(); }
    };

    private String lang;
    private int margin;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        lang = Prefs.getStr(this, "lang", "zh");
        margin = Frames.edgeDp(this);
        // the header, the page area and the bar are the library's (the same for all seven pages); the desktop is one of the pages

        // no apps yet: a big "+"
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        IconButton plus = new IconButton(this, Icons.PLUS, "安装应用", true, 72, 18);
        plus.setPadding(Ui.dp(this, 40), Ui.dp(this, 26), Ui.dp(this, 40), Ui.dp(this, 26));
        plus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(HomeActivity.this, PickApkActivity.class));
            }
        });
        box.addView(plus, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView hint = Ui.text(this, "从 Kindle 的文件夹里选择 apk 文件", 15, false);
        hint.setGravity(Gravity.CENTER);
        box.addView(hint);
        emptyBox = box;
        // it takes the place of the app block (block 1): same frame, same size; either this or the grid is shown
        box.setBackground(Widgets.border(this, false));

        GridView grid = new GridView(this);
        grid.setVisibility(View.GONE);   // shown by refresh() once there are apps (the "+" block is shown until then)
        applyCols(grid);
        grid.setHorizontalSpacing(Ui.dp(this, 6));
        grid.setVerticalSpacing(Ui.dp(this, 18));
        grid.setStretchMode(GridView.STRETCH_SPACING);   // icons flush with both ends, the free width goes between the columns
        grid.setBackground(Widgets.border(this, false)); // block 1
        grid.setSelector(new ColorDrawable(Color.TRANSPARENT));
        grid.setCacheColorHint(Color.TRANSPARENT);
        grid.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
        adapter = new AppsAdapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                launch(apps.get(position));
            }
        });
        homeBody = new HomeBody(this, emptyBox, grid);   // the widgets, and block 1 below them, take the rest of the height
        attachDesk(homeBody);
        gridView = grid;

        // stored count > 0: no "install" page, draw the grid right away without waiting for onResume
        if (Ctl.appCount() > 0) {
            emptyBox.setVisibility(View.GONE);
            grid.setVisibility(View.VISIBLE);
        }

        useEmbrithIme();
        registerReceiver(unlocked, new IntentFilter(Intent.ACTION_USER_UNLOCKED));
        startService(new Intent(this, StatusService.class));   // the service watches the control directory (pull-down, USB, sleep)
        if (Ctl.appCount() > 0) refresh();
    }

    private GridView gridView;

    /** The height of one row of apps (the grid padding, the cell, the icon, a name of two lines): this much always stays for the apps. */
    int appRowPx() {
        return Ui.dp(this, 24 + 8 + 8) + iconPx + Math.round(2 * 1.25f * labelSp * getResources().getDisplayMetrics().scaledDensity);
    }

    private int iconPx, labelSp = 16;   // the icon and the name of an app, they follow the number of apps per row (applyCols)

    /**
     * Apps per row of the desktop: 4 (default), 5 or 6 (the setting home_cols). The column is 9/10 of the cell (the grid width / n), the
     * free width is spread between the columns (STRETCH_SPACING); the icon is half a cell wide (40..72 dp), the name gets smaller from 5 per row.
     */
    private void applyCols(GridView g) {
        String v = Prefs.getStr(this, "home_cols", "4");
        int n = "6".equals(v) ? 6 : "5".equals(v) ? 5 : 4;
        int avail = getResources().getDisplayMetrics().widthPixels - 2 * Frames.edge(this) - 2 * Ui.dp(this, 12);   // the grid width minus its padding
        int cell = avail / n;
        g.setNumColumns(n);
        g.setColumnWidth(cell * 9 / 10);
        iconPx = Math.max(Ui.dp(this, 40), Math.min(Ui.dp(this, 72), cell / 2));
        labelSp = n == 6 ? 13 : n == 5 ? 14 : 16;
    }

    @Override
    protected void onDestroy() {
        try { unregisterReceiver(unlocked); } catch (IllegalArgumentException e) { /* not registered */ }
        super.onDestroy();
    }

    private SettingsHomeBox settingsBox;   // built just after the first resume, or when first asked for, then kept

    @Override
    protected String firstPage() {
        return "home";
    }

    @Override
    protected void needSettings() {
        settingsBox = MenuActivity.buildHomeBox(this);
        attachSettings(settingsBox);
    }

    /** In place switch to the desktop or the settings home: only the content that may have changed is drawn again. */
    @Override
    protected void pageShown(String id) {
        if ("settings".equals(id)) settingsBox.refresh(); else loadWidgets();
    }

    /** BACK on the first library page or on the settings home: to the desktop. */
    @Override
    protected void leave() {
        openPage("home");
    }

    @Override
    public void onBackPressed() {
        if ("home".equals(pageId())) return;   // this is the home screen: BACK does nothing
        super.onBackPressed();
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        String p = i.getStringExtra(EXTRA_PAGE);
        openPage(p != null ? p : "home");   // a start from the bar of a page that is not the host, or the home key
    }

    @Override
    protected void onResume() {
        super.onResume();   // the library: margin check, bar, wallpaper, the page shown
        if (recreating) return;
        if (!Prefs.getStr(this, "lang", "zh").equals(lang)) { recreate(); return; }
        if (Ctl.dexoptRunning()) { startActivity(new android.content.Intent(this, DexoptActivity.class)); return; }
        if (UsbActivity.active) { startActivity(new android.content.Intent(this, UsbActivity.class)); return; }
        Dock.noSwipe();   // 桌面和其他页面互切不是翻页, 通知显示端这次不用翻页动画 (底部栏切换除外)
        if ("home".equals(pageId())) {
            applyCols(gridView);
            homeBody.requestLayout();   // the row of apps may have changed height (apps per row)
            if (adapter != null) adapter.notifyDataSetChanged();
            com.embrith.library.Screens.syncUsage(this);
            refresh();
        } else if ("settings".equals(pageId())) {
            settingsBox.refresh();
        }
        if (settingsBox == null) {
            // build the settings home while the desktop is idle: the first switch is then as fast as the next
            getWindow().getDecorView().post(new Runnable() {
                @Override public void run() { if (settingsBox == null && !isFinishing()) needSettings(); }
            });
        }
    }

    /** Draws the widgets again (their content may have changed); the apps below move up or down to fill the height. */
    private void loadWidgets() {
        homeBody.area().reload();
    }

    /** Make EmbrithIME the enabled and the default input method (the stock keyboards are not in the image). */
    private void useEmbrithIme() {
        try {
            String id = getPackageName() + "/.EmbrithIME";
            android.content.ContentResolver cr = getContentResolver();
            String en = android.provider.Settings.Secure.getString(cr, android.provider.Settings.Secure.ENABLED_INPUT_METHODS);
            if (en == null || !en.equals(id)) android.provider.Settings.Secure.putString(cr, android.provider.Settings.Secure.ENABLED_INPUT_METHODS, id);
            String cur = android.provider.Settings.Secure.getString(cr, android.provider.Settings.Secure.DEFAULT_INPUT_METHOD);
            if (cur == null || !cur.equals(id)) android.provider.Settings.Secure.putString(cr, android.provider.Settings.Secure.DEFAULT_INPUT_METHOD, id);
        } catch (SecurityException e) {
            // no WRITE_SECURE_SETTINGS (not a priv-app): the input method has to be chosen by hand
        }
    }

    private void refresh() {
        useEmbrithIme();   // checked on every redraw: the setting is written again if something else changed it
        loadWidgets();
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> list = pm.queryIntentActivities(main, 0);
        Collections.sort(list, new ResolveInfo.DisplayNameComparator(pm));
        apps.clear();
        for (ResolveInfo ri : list) {
            ApplicationInfo ai = ri.activityInfo.applicationInfo;
            if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) == 0) apps.add(ri);   // only what the user installed
        }
        // which page to draw depends only on the stored count (the host refreshes it at start and stop); the query corrects it when it finds
        // apps, and while the count is still unknown (-1) the query decides
        int cnt = Ctl.appCount();
        if (!apps.isEmpty() && apps.size() != cnt) { cnt = apps.size(); Ctl.setAppCount(cnt); }
        else if (cnt < 0) { cnt = 0; Ctl.setAppCount(0); }
        emptyBox.setVisibility(cnt > 0 ? View.GONE : View.VISIBLE);
        gridView.setVisibility(cnt > 0 ? View.VISIBLE : View.GONE);
        adapter.notifyDataSetChanged();
    }

    private void launch(ResolveInfo ri) {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        i.setComponent(new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        WhiteBg.launched(ri.activityInfo.packageName);
        startActivity(i);
    }

    private final class AppsAdapter extends BaseAdapter {
        @Override public int getCount() { return apps.size(); }
        @Override public Object getItem(int position) { return apps.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convert, ViewGroup parent) {
            ResolveInfo ri = apps.get(position);
            PackageManager pm = getPackageManager();
            LinearLayout cell = new LinearLayout(HomeActivity.this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(Ui.dp(HomeActivity.this, 4), Ui.dp(HomeActivity.this, 4), Ui.dp(HomeActivity.this, 4), Ui.dp(HomeActivity.this, 4));

            ImageView icon = new ImageView(HomeActivity.this);
            icon.setImageBitmap(Img.blackWhite(ri.loadIcon(pm), iconPx));
            cell.addView(icon, new LinearLayout.LayoutParams(iconPx, iconPx));

            TextView label = Ui.text(HomeActivity.this, String.valueOf(ri.loadLabel(pm)), labelSp, false);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(2);
            label.setShadowLayer(5f, 0f, 0f, Color.WHITE);   // a white halo keeps the name readable on a wallpaper
            label.setPadding(0, Ui.dp(HomeActivity.this, 8), 0, 0);
            cell.addView(label);
            return cell;
        }
    }
}
