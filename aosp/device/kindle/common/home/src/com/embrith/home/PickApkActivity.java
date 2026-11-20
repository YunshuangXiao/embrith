/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * PickApkActivity: Small file picker on the Kindle storage (/dev/kindle): folders and .apk files; a tap on an apk
 * asks the Kindle side to install it. */
package com.embrith.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

/**
 * A small file picker on the Kindle's own storage (seen as /dev/kindle): folders and .apk files. A tap on an apk asks the Kindle side
 * (embrith.sh) to install it with pm and shows the result.
 */
public class PickApkActivity extends Activity {
    private static final String ROOT = "/dev/kindle";
    private final Handler handler = new Handler();
    private LinearLayout list;
    private TextView where;
    private File dir = new File(ROOT);
    private AlertDialog waiting;
    private int polls;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout body = Ui.page(this, "选择 apk 文件", new View.OnClickListener() {
            @Override public void onClick(View v) { up(); }
        });
        where = Ui.caption(body, "");
        list = Ui.addGroup(body);
    }

    @Override
    protected void onResume() {
        super.onResume();
        show();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (waiting != null) waiting.dismiss();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        up();
    }

    /** One folder up; at the top of the Kindle storage: leave the picker. */
    private void up() {
        if (dir.getPath().equals(ROOT) || dir.getParentFile() == null) finish();
        else {
            dir = dir.getParentFile();
            show();
        }
    }

    private void show() {
        list.removeAllViews();
        where.setText(dir.getPath().replaceFirst("^/dev/kindle", "Kindle"));
        File[] files = dir.listFiles();
        if (files == null) {
            Ui.add(list, Ui.row(this, "这个文件夹打不开 (或者 Kindle 的存储没有接入)", "", null));
            return;
        }
        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        int shown = 0;
        for (final File f : files) {
            String n = f.getName();
            if ((n.startsWith(".") && !Prefs.showHidden(this)) || n.toLowerCase().endsWith(".sdr")) continue;   // hidden (unless shown by the setting), and the ".sdr" folders KOReader keeps next to books
            if (f.isDirectory()) {
                Ui.add(list, Ui.nav(this, n, "", new View.OnClickListener() {
                    @Override public void onClick(View v) { dir = f; show(); }
                }));
                shown++;
            } else if (n.toLowerCase().endsWith(".apk")) {
                Ui.add(list, Ui.row(this, n, f.length() / 1048576 + " MB", new View.OnClickListener() {
                    @Override public void onClick(View v) { confirm(f); }
                }));
                shown++;
            }
            if (shown >= 400) break;
        }
        if (shown == 0) Ui.add(list, Ui.row(this, "这里没有文件夹和 apk 文件", "", null));
    }

    private void confirm(final File apk) {
        String title = "安装";
        String msg = apk.getName();
        PackageManager pm = getPackageManager();
        PackageInfo neu = pm.getPackageArchiveInfo(apk.getPath(), 0);
        if (neu != null) {
            try {
                PackageInfo old = pm.getPackageInfo(neu.packageName, 0);
                title = "覆盖安装";
                msg = apk.getName() + Ui.t(this, "\n\n已安装: ") + old.versionName + " (" + old.versionCode
                    + Ui.t(this, ")\n将安装: ") + neu.versionName + " (" + neu.versionCode + ")"
                    + (neu.versionCode < old.versionCode ? Ui.t(this, "\n版本低于已安装版本, 将降级安装.") : "")
                    + Ui.t(this, "\n应用数据保留.");
            } catch (PackageManager.NameNotFoundException e) {
                // not installed yet: plain install
            }
        }
        new Ui.Builder(this)
            .setTitle(title)
            .setMessage(msg)
            .setPositiveButton(title, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int which) { install(apk); }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void install(File apk) {
        Ctl.remove("install_result");
        Ctl.put("install", apk.getPath());
        polls = 0;
        waiting = new Ui.Builder(this).setTitle("正在安装").setMessage("这可能要一两分钟, 请稍候...").setCancelable(false).show();
        handler.postDelayed(poll, 2000);
    }

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            String r = Ctl.text("install_result");
            polls++;
            if ("ok".equals(r)) {
                finishDialog();
                Ctl.setAppCount(Math.max(0, Ctl.appCount()) + 1);   // a reinstall counts too: HomeActivity corrects it
                Ui.toast(PickApkActivity.this, "安装完成", true);
                finish();
            } else if (r.startsWith("fail")) {
                finishDialog();
                new Ui.Builder(PickApkActivity.this).setTitle("安装失败").setMessage(r).setPositiveButton("好", null).show();
            } else if (polls > ("1".equals(Ctl.get("DEX2OAT", "0")) ? 900 : 150) || (r.length() == 0 && polls > 12)) {
                finishDialog();
                new Ui.Builder(PickApkActivity.this).setTitle("没有响应").setMessage("Kindle 一侧没有回应, 请看 logs/last-run.txt").setPositiveButton("好", null).show();
            } else {
                handler.postDelayed(this, 2000);
            }
        }
    };

    private void finishDialog() {
        if (waiting != null) {
            waiting.dismiss();
            waiting = null;
        }
    }
}
