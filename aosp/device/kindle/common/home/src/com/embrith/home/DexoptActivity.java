/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * DexoptActivity: Full-screen progress page for dex2oat compilation; ignores touch and back, leaves when the run
 * ends or fails; progress comes from ctl/dexopt. */
package com.embrith.home;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * 优化: dex2oat 的全屏进度页: 不响应触摸和返回键, 只在编译结束或出错时自动退出.
 * 进度来自 ctl/dexopt (embrith.sh 的 dexopt_run 写入): state, total, done, cur, msg.
 */
public class DexoptActivity extends Activity {
    private final Handler handler = new Handler();
    private TextView detail, msg;
    private ProgressBar bar;
    private int waited = 0;
    private boolean ending = false;
    private String labelPkg = "", labelText = "";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.WHITE);
        int pad = Ui.dp(this, 40);
        root.setPadding(pad, pad, pad, pad);
        TextView title = new TextView(this);
        title.setText(Ui.t(this, "正在优化应用 (dex2oat)"));
        title.setTextColor(Color.BLACK);
        title.setTextSize(26);
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 24));
        lp.topMargin = Ui.dp(this, 32);
        root.addView(bar, lp);
        detail = new TextView(this);
        detail.setTextColor(Color.BLACK);
        detail.setTextSize(20);
        detail.setGravity(Gravity.CENTER);
        detail.setPadding(0, Ui.dp(this, 20), 0, 0);
        root.addView(detail);
        msg = new TextView(this);
        msg.setTextColor(Color.BLACK);
        msg.setTextSize(16);
        msg.setGravity(Gravity.CENTER);
        msg.setPadding(0, Ui.dp(this, 20), 0, 0);
        msg.setText(Ui.t(this, "编译期间已停掉其他应用, 请不要操作, 完成后自动返回"));
        root.addView(msg);
        setContentView(root);
        handler.post(poll);
    }

    private static int parse(String v) {
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** The app name for a package name (the name the launcher shows); the package name itself when the app is not installed (yet). */
    private String label(String pkg) {
        if (pkg.length() == 0 || pkg.equals(labelPkg)) return pkg.length() == 0 ? pkg : labelText;
        String t = pkg;
        try {
            PackageManager pm = getPackageManager();
            t = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (PackageManager.NameNotFoundException e) {
            // keep the package name
        }
        labelPkg = pkg;
        labelText = t;
        return t;
    }

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            java.util.List<String> l = Ctl.lines("dexopt");
            String state = "", cur = "", m = "";
            int total = 0, done = 0;
            if (l != null) {
                for (String s : l) {
                    int i = s.indexOf('=');
                    if (i < 0) continue;
                    String k = s.substring(0, i), v = s.substring(i + 1);
                    if (k.equals("state")) state = v.trim();
                    else if (k.equals("cur")) cur = v.trim();
                    else if (k.equals("msg")) m = v.trim();
                    else if (k.equals("total")) total = parse(v);
                    else if (k.equals("done")) done = parse(v);
                }
            }
            if (state.equals("done")) { finish(); return; }
            if (state.equals("fail")) {
                if (!ending) {
                    ending = true;
                    msg.setText(Ui.t(DexoptActivity.this, "出错: ") + m);
                    handler.postDelayed(new Runnable() { @Override public void run() { finish(); } }, 5000);
                }
                return;
            }
            if (state.length() == 0 && ++waited > 20) { finish(); return; }   // the host never answered
            bar.setProgress(total > 0 ? done * 100 / total : 0);
            detail.setText(total > 0 ? (done + " / " + total + "\n" + label(cur)) : Ui.t(DexoptActivity.this, "准备中..."));
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    public void onBackPressed() {
        // blocked while compiling
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        return true;
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}