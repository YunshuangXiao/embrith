/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * PowerMenuActivity: The menu of the top left corner hold (kgest writes ctl/power_menu, StatusService opens this page). */
package com.embrith.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;

/**
 * Transparent page that shows one dialog with three entries: restart Embrith, exit Embrith, restart the Kindle. A click runs the entry at
 * once; Cancel or the back key closes the page. If Android never gets here the watchdog on the Kindle side exits Embrith after about
 * 4 s (the file ctl/power_menu is still there), so the corner hold stays a way out.
 */
public class PowerMenuActivity extends Activity {
    private AlertDialog dialog;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        final String[] items = { "重启 Embrith", "退出 Embrith", "重启 Kindle" };
        dialog = new Ui.Builder(this).setTitle("Embrith").setItems(items, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                if (which == 0) {
                    Ui.toast(PowerMenuActivity.this, "正在重启 Embrith...", false);
                    Ctl.request("restart");
                } else if (which == 1) {
                    Ui.toast(PowerMenuActivity.this, "正在回到 Kindle...", false);
                    Ctl.request("exit");
                } else {
                    Ui.toast(PowerMenuActivity.this, "正在重启 Kindle...", false);
                    Ctl.request("kindle_reboot");
                }
            }
        }).setNegativeButton("取消", null).create();
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override public void onDismiss(DialogInterface d) { finish(); }
        });
        dialog.show();
    }

    @Override
    protected void onDestroy() {
        if (dialog != null && dialog.isShowing()) dialog.dismiss();
        super.onDestroy();
    }
}
