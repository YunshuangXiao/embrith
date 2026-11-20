/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * NetPickActivity: Pop-up page that switches the Wi-Fi network or the Bluetooth device, opened from the network
 * settings page. */
package com.embrith.home;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.Window;

/**
 * Pop-up that switches the Wi-Fi network or the Bluetooth device, opened from the network settings page. (The long press on the Wi-Fi or
 * Bluetooth button of the pull-down panel shows the same content as a floating window, see PanelWindow.) The content is NetPick.
 */
public class NetPickActivity extends Activity {
    private static final String X_KIND = "kind";

    private NetPick pick;

    static void open(Context c, String kind) {
        Intent i = new Intent(c, NetPickActivity.class);
        i.putExtra(X_KIND, kind);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        pick = new NetPick(this, getIntent().getStringExtra(X_KIND), new NetPick.Host() {
            @Override public void close() { finish(); }
            @Override public void focus(boolean on) { }   // an activity window always has the focus
        });
        setContentView(pick.view());
        Window w = getWindow();
        DisplayMetrics dm = getResources().getDisplayMetrics();
        w.setLayout((int) (dm.widthPixels * 0.92f), (int) (dm.heightPixels * 0.78f));
        w.setGravity(Gravity.CENTER);
    }

    @Override
    protected void onResume() {
        super.onResume();
        pick.start();
    }

    @Override
    protected void onDestroy() {
        pick.stop();
        super.onDestroy();
    }
}