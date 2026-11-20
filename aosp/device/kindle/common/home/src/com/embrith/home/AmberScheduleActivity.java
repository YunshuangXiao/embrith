/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * AmberScheduleActivity: Warmth and brightness settings page: brightness with auto adjust, the global warmth
 * default, and the warmth schedule (on and off times). */
package com.embrith.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 色温设置: opened from the status panel (色温 > 时间设置 ›) and from 设置 > 个性化 > 色温设置.
 * Brightness value and "自动调整亮度" (fl_auto request), the global warmth WARM_DEFAULT (0..24),
 * and "色温启动时间设置": AMBER_SCHED(0/1), AMBER_ON / AMBER_OFF (HHMM), AMBER_LEVEL (warmth inside the window).
 * All values are written to ctl/conf; embrith.sh (ui/ctl.sh amber_sched) applies them and set_sync keeps them.
 */
public class AmberScheduleActivity extends Activity {
    private TextView brightValue, warmValue;
    private BarView flBar, warmBar;
    private int lastFl = -1, lastAmber = -1;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable levelPoll = new Runnable() {
        @Override public void run() {
            // a gesture (two-finger swipe, kgest) changes the level behind this page: show it
            java.util.Map<String, String> st = Ctl.status();
            int fl = Ctl.statusInt(st, "FL", -1), am = Ctl.statusInt(st, "AMBER", -1);
            if (fl >= 0 && fl != lastFl) { lastFl = fl; if (flBar != null && !flBar.isDragging()) { flBar.setLevel(fl); brightValue.setText(String.valueOf(fl)); } }
            if (am >= 0 && am != lastAmber) { lastAmber = am; if (warmBar != null && !warmBar.isDragging()) { warmBar.setLevel(am); warmValue.setText(String.valueOf(am)); } }
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(levelPoll);
        handler.postDelayed(levelPoll, 1000);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(levelPoll);
        super.onPause();
    }
    private Ui.SwitchRow enableRow;
    private Ui.Row onRow, offRow;
    private LinearLayout periodGroup;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout body = Ui.page(this, "色温设置", null);

        // brightness: value (read from ctl/status, FL) and the global auto-brightness switch
        LinearLayout g = Ui.addGroup(body);
        java.util.Map<String, String> st = Ctl.status();
        int fl = Ctl.statusInt(st, "FL", -1);
        lastFl = fl;
        lastAmber = Ctl.statusInt(st, "AMBER", -1);
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Ui.dp(this, 18), Ui.dp(this, 8), Ui.dp(this, 18), 0);
        head.addView(Ui.text(this, "亮度", 18, false), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        brightValue = Ui.text(this, fl >= 0 ? String.valueOf(fl) : "不可用", 18, true);
        head.addView(brightValue);
        Ui.add(g, head);
        final BarView bar = new BarView(this);
        flBar = bar;
        bar.setLevel(Math.max(0, fl));
        bar.setLive(true);
        bar.setListener(new BarView.Listener() {
            @Override public void onLevel(int level) { Ctl.put("light_set", String.valueOf(level)); brightValue.setText(String.valueOf(level)); }
        });
        LinearLayout barRow = new LinearLayout(this);
        barRow.setPadding(Ui.dp(this, 18), 0, Ui.dp(this, 18), 0);
        barRow.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 52)));
        Ui.addPlain(g, barRow);
        Ui.add(g, Ui.switchRow(this, "自动调整亮度", "1".equals(st.get("FL_AUTO")), new Ui.OnToggle() {
            @Override public void toggled(boolean on) { Ctl.put("fl_auto", on ? "1" : "0"); }
        }));

        // global warmth (the default; also the status panel slider)
        g = Ui.addGroup(body);
        LinearLayout whead = new LinearLayout(this);
        whead.setOrientation(LinearLayout.HORIZONTAL);
        whead.setGravity(Gravity.CENTER_VERTICAL);
        whead.setPadding(Ui.dp(this, 18), Ui.dp(this, 8), Ui.dp(this, 18), 0);
        whead.addView(Ui.text(this, "色温", 18, false), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        warmValue = Ui.text(this, String.valueOf(level("WARM_DEFAULT", 16)), 18, true);
        whead.addView(warmValue);
        Ui.add(g, whead);
        LinearLayout wrow = new LinearLayout(this);
        wrow.setPadding(Ui.dp(this, 18), 0, Ui.dp(this, 18), 0);
        final BarView warm = new BarView(this);
        warmBar = warm;
        warm.setLevel(level("WARM_DEFAULT", 16));
        warm.setLive(true);
        warm.setListener(new BarView.Listener() {
            @Override public void onLevel(int level) { Ctl.put("warm_set", String.valueOf(level)); warmValue.setText(String.valueOf(level)); }
        });
        wrow.addView(warm, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 52)));
        Ui.addPlain(g, wrow);

        // 色温启动时间设置 (same group as the warmth slider: no gap between them)
        enableRow = Ui.switchRow(this, "色温启动时间设置", "1".equals(Ctl.get("AMBER_SCHED", "0")), new Ui.OnToggle() {
            @Override public void toggled(boolean on) {
                Ctl.set("AMBER_SCHED", on ? "1" : "0");
                periodGroup.setVisibility(on ? View.VISIBLE : View.GONE);
            }
        });
        Ui.add(g, enableRow);

        periodGroup = Ui.addGroup(body);
        onRow = Ui.nav(this, "开始时间", "", new View.OnClickListener() {
            @Override public void onClick(View v) { pick("AMBER_ON", "2100", onRow); }
        });
        offRow = Ui.nav(this, "结束时间", "", new View.OnClickListener() {
            @Override public void onClick(View v) { pick("AMBER_OFF", "0700", offRow); }
        });
        Ui.add(periodGroup, onRow);
        Ui.add(periodGroup, offRow);
        LinearLayout prow = new LinearLayout(this);
        prow.setPadding(Ui.dp(this, 18), 0, Ui.dp(this, 18), 0);
        final BarView period = new BarView(this);
        period.setLevel(level("AMBER_LEVEL", 16));
        period.setListener(new BarView.Listener() {
            @Override public void onLevel(int level) { Ctl.set("AMBER_LEVEL", String.valueOf(level)); }
        });
        prow.addView(period, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 52)));
        Ui.addPlain(periodGroup, prow);

        Ui.note(this, body, "色温启动时间设置", "时段色温\t开启后, 在开始和结束时间之间使用时段色温; 其余时间(或关闭时)使用上面的默认色温.\n\n"
            + "跨过午夜\t开始晚于结束表示跨过午夜(如 21:00 到次日 07:00).");
        update();
    }

    private static String fmt(String hhmm) {
        String v = hhmm.length() == 3 ? "0" + hhmm : hhmm;
        return v.length() == 4 ? v.substring(0, 2) + ":" + v.substring(2) : hhmm;
    }

    private static int level(String key, int def) {
        try { return Math.max(0, Math.min(24, Integer.parseInt(Ctl.get(key, String.valueOf(def))))); } catch (NumberFormatException e) { return def; }
    }

    /** A two digit number field for the time dialog. */
    private EditText timeField(int v) {
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setFilters(new InputFilter[] { new InputFilter.LengthFilter(2) });
        e.setText(String.format(java.util.Locale.US, "%02d", v));
        e.setGravity(Gravity.CENTER);
        e.setTextSize(24);
        e.setSelectAllOnFocus(true);
        return e;
    }

    private static int num(EditText e) {
        try { return Integer.parseInt(e.getText().toString().trim()); } catch (NumberFormatException x) { return -1; }
    }

    /** Time typed in two fields, in the same dialog style as the other ones. 12/24 hour follows Prefs.clock24 (the clock on the status bar). */
    private void pick(final String key, String def, final Ui.Row row) {
        String v = Ctl.get(key, def);
        if (v.length() == 3) v = "0" + v;
        int h = 21, m = 0;
        try { h = Integer.parseInt(v.substring(0, 2)); m = Integer.parseInt(v.substring(2, 4)); } catch (Exception e) { h = 21; m = 0; }
        final boolean h24 = Prefs.clock24(this);
        final EditText eh = timeField(h24 ? h : (h % 12 == 0 ? 12 : h % 12));
        final EditText em = timeField(m);
        final boolean[] pm = { h >= 12 };
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(Ui.dp(this, 20), Ui.dp(this, 12), Ui.dp(this, 20), Ui.dp(this, 4));
        if (!h24) {
            final Ui.FlatButton am = Ui.button(this, "上午").noFrame();
            final Ui.FlatButton pmb = Ui.button(this, "下午").noFrame();
            am.setInverted(!pm[0]);
            pmb.setInverted(pm[0]);
            am.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View x) { pm[0] = false; am.setInverted(true); pmb.setInverted(false); }
            });
            pmb.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View x) { pm[0] = true; am.setInverted(false); pmb.setInverted(true); }
            });
            box.addView(am);
            box.addView(pmb);
        }
        box.addView(eh, new LinearLayout.LayoutParams(Ui.dp(this, 72), ViewGroup.LayoutParams.WRAP_CONTENT));
        box.addView(Ui.text(this, ":", 24, true));
        box.addView(em, new LinearLayout.LayoutParams(Ui.dp(this, 72), ViewGroup.LayoutParams.WRAP_CONTENT));
        new Ui.Builder(this).setTitle(row.label.getText().toString()).setView(box)
            .setPositiveButton("好", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    int hh = num(eh), mm = num(em);
                    boolean ok = mm >= 0 && mm <= 59 && (h24 ? (hh >= 0 && hh <= 23) : (hh >= 1 && hh <= 12));
                    if (!ok) {
                        Ui.toast(AmberScheduleActivity.this, h24 ? "请输入有效时间 (0 到 23 时, 0 到 59 分)" : "请输入有效时间 (1 到 12 时, 0 到 59 分)", true);
                        return;
                    }
                    if (!h24) hh = (hh % 12) + (pm[0] ? 12 : 0);
                    Ctl.set(key, String.format(java.util.Locale.US, "%02d%02d", hh, mm));
                    update();
                }
            })
            .setNegativeButton("取消", null).show();
    }

    private void update() {
        boolean on = "1".equals(Ctl.get("AMBER_SCHED", "0"));
        enableRow.setOn(on);
        periodGroup.setVisibility(on ? View.VISIBLE : View.GONE);
        onRow.setValue(fmt(Ctl.get("AMBER_ON", "2100")));
        offRow.setValue(fmt(Ctl.get("AMBER_OFF", "0700")));
    }

    /** Summary shown on the status panel after "时间设置". */
    static String summary() {
        if (!"1".equals(Ctl.get("AMBER_SCHED", "0"))) return "关";
        return fmt(Ctl.get("AMBER_ON", "2100")) + "–" + fmt(Ctl.get("AMBER_OFF", "0700"));
    }
}
