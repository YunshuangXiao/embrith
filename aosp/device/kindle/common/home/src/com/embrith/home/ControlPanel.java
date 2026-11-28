/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * ControlPanel: Control area shared by the settings home and the pull-down panel: quick buttons, frontlight and
 * warmth level bars. */
package com.embrith.home;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;

import java.util.Map;

/**
 * The control area shared by the settings home and the pull-down panel: the fixed square on the left (airplane mode, Wi-Fi, Bluetooth,
 * invert), two thin level bars (frontlight, warmth) with their check boxes in the middle, two custom round buttons at the right edge.
 * It only needs a Context, so it also works in the floating window of the pull-down panel (PanelWindow). Gaps between the parts are 12 dp.
 */
final class ControlPanel {
    static final int GAP_H = 12, GAP_V = 14, TILE = 168;
    static final String[] HB_DEF = { "lock", "clear" };

    /** Opens the Wi-Fi / Bluetooth list (kind = wifi or bt). */
    interface Picker {
        void open(String kind);
    }

    private final Context c;
    private final Runnable leave;   // run before this area leaves the app (closes the floating panel); null on the settings home
    private final Picker picker;    // shows the list over the panel; null: the list is a page of its own (NetPickActivity)
    private final Handler handler = new Handler(Looper.getMainLooper());
    private CircleButton qPlane, qWifi, qBt, qInvert;
    private final CircleButton[] qCustom = new CircleButton[2];   // the custom buttons (null: none)
    private final String[] customId = new String[2];
    private BarView flBar, amberBar;
    private int lastFl = -1, lastAmber = -1;
    private int autoTick;

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            // a gesture (two-finger swipe, kgest) changes the level behind the page: show it on the bar
            Map<String, String> st = Ctl.status();
            int fl = Ctl.statusInt(st, "FL", -1), am = Ctl.statusInt(st, "AMBER", -1);
            if (fl >= 0 && fl != lastFl) { lastFl = fl; if (flBar != null && !flBar.isDragging()) flBar.setLevel(fl); }
            if (am >= 0 && am != lastAmber) { lastAmber = am; if (amberBar != null && !amberBar.isDragging()) amberBar.setLevel(am); }
            // auto brightness: powerd changes the level by itself and nothing tells the host, so ask for a fresh status every 3 s while the page is shown
            if ("1".equals(st.get("FL_AUTO")) && ++autoTick % 3 == 0) Ctl.put("status_req", "quick");
            handler.postDelayed(this, 1000);
        }
    };
    private final Runnable refresh = new Runnable() {
        @Override public void run() { update(); }
    };

    ControlPanel(Context c, Runnable leave, Picker picker) {
        this.c = c;
        this.leave = leave;
        this.picker = picker;
    }

    /** Start and stop following the levels that change behind the page (call from onResume / onPause, or when the window is shown / hidden). */
    void start() {
        handler.removeCallbacks(poll);
        handler.postDelayed(poll, 1000);
        update();
    }

    void stop() {
        handler.removeCallbacks(poll);
        handler.removeCallbacks(refresh);
    }

    /** The state of the toggles on the buttons. */
    void update() {
        Map<String, String> st = Ctl.status();
        String w = st.get("WIFI"), b = st.get("BT");
        if (qWifi != null) { qWifi.setOn("1".equals(w)); qWifi.setOff("0".equals(w)); }
        if (qBt != null) { qBt.setOn("1".equals(b)); qBt.setOff("0".equals(b)); }
        if (qPlane != null) qPlane.setOn("0".equals(w) && "0".equals(b));
        if (qInvert != null) qInvert.setOn("1".equals(Ctl.get("INVERT", "0")));
        for (int i = 0; i < qCustom.length; i++) if (qCustom[i] != null) QuickActions.setState(qCustom[i], customId[i]);   // only the toggles have a state
    }

    /** The radios answer slowly: look again after a few seconds. */
    private void later() {
        handler.removeCallbacks(refresh);
        handler.postDelayed(refresh, 4000);
        handler.postDelayed(refresh, 8000);
    }

    private void leaving() {
        if (leave != null) leave.run();
    }

    private void pickList(String kind) {
        if (picker != null) picker.open(kind);
        else NetPickActivity.open(c, kind);
    }

    /** The whole row: the square, the level bars, the custom buttons. */
    View build() {
        LinearLayout ctl = new LinearLayout(c);
        ctl.setOrientation(LinearLayout.HORIZONTAL);
        ctl.setPadding(Frames.edge(c), Ui.dp(c, GAP_V), Frames.edge(c), 0);
        ctl.addView(tile(), new LinearLayout.LayoutParams(Ui.dp(c, TILE), Ui.dp(c, TILE)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(c, TILE), 1f);
        lp.leftMargin = Ui.dp(c, GAP_H);
        lp.rightMargin = Ui.dp(c, GAP_H);
        ctl.addView(levels(), lp);
        ctl.addView(customColumn(), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(c, TILE)));
        return ctl;
    }

    /** The square frame with airplane mode, Wi-Fi (top row), Bluetooth and invert (bottom row). */
    private View tile() {
        LinearLayout t = new LinearLayout(c);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12));
        android.graphics.drawable.GradientDrawable f = new android.graphics.drawable.GradientDrawable();
        f.setColor(android.graphics.Color.TRANSPARENT);
        f.setStroke(Ui.dp(c, 1f), Ui.LINE);
        f.setCornerRadius(Ui.radius(c));
        t.setBackground(f);
        qPlane = circle(Icons.PLANE, new View.OnClickListener() {
            @Override public void onClick(View v) { airplane(); }
        });
        qWifi = circle(Icons.WIFI, new View.OnClickListener() {
            @Override public void onClick(View v) { Ctl.request("wifi"); later(); }
        });
        qWifi.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { pickList("wifi"); return true; }
        });
        qBt = circle(Icons.BT, new View.OnClickListener() {
            @Override public void onClick(View v) { Ctl.request("bt"); later(); }
        });
        qBt.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { pickList("bt"); return true; }
        });
        qInvert = circle(Icons.INVERT, new View.OnClickListener() {
            @Override public void onClick(View v) { run("invert", ""); }
        });
        t.addView(pair(qPlane, qWifi));
        LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        second.topMargin = Ui.dp(c, GAP_H);
        t.addView(pair(qBt, qInvert), second);
        return t;
    }

    private LinearLayout pair(View left, View right) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.addView(left);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(c, CircleButton.SIZE_DP), Ui.dp(c, CircleButton.SIZE_DP));
        lp.leftMargin = Ui.dp(c, GAP_H);
        r.addView(right, lp);
        return r;
    }

    private CircleButton circle(int icon, View.OnClickListener l) {
        CircleButton b = new CircleButton(c, icon);
        b.setOnClickListener(l);
        b.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(c, CircleButton.SIZE_DP), Ui.dp(c, CircleButton.SIZE_DP)));
        return b;
    }

    /** The two level bars one above the other, each followed by its check box. */
    private View levels() {
        Map<String, String> st = Ctl.status();
        int fl = Ctl.statusInt(st, "FL", -1);
        int amber = Ctl.statusInt(st, "AMBER", -1);
        lastFl = fl;
        lastAmber = amber;
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams wide = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        flBar = levelBar(col, "暗", "亮", fl, "light_set");
        if ("1".equals(st.get("FL_AUTO")) || "0".equals(st.get("FL_AUTO"))) col.addView(check("自动亮度", st.get("FL_AUTO"), "fl_auto"), wide);   // no auto brightness on this device: no switch
        amberBar = levelBar(col, "冷", "暖", amber, "warm_set");
        col.addView(Ui.checkRow(c, "按时间自动启用色温", "1".equals(Ctl.get("AMBER_SCHED", "0")), new Ui.OnToggle() {
            @Override public void toggled(boolean on) { Ctl.set("AMBER_SCHED", on ? "1" : "0"); }
        }), wide);
        return col;
    }

    /** One thin level bar with a word at each end; a negative value (not available) leaves the bar inert. */
    private BarView levelBar(LinearLayout col, String low, String high, int value, final String file) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(Ui.text(c, Ui.t(c, low), 15, false));
        BarView bar = new BarView(c);
        bar.setLevel(Math.max(0, value));
        if (value >= 0) {
            bar.setLive(true);
            bar.setListener(new BarView.Listener() {
                @Override public void onLevel(int level) { Ctl.put(file, String.valueOf(level)); }
            });
        }
        row.addView(bar, new LinearLayout.LayoutParams(0, Ui.dp(c, 44), 1f));
        row.addView(Ui.text(c, Ui.t(c, high), 15, false));
        col.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return bar;
    }

    /** "自动调整亮度": value from ctl/status (1/0, -1 or missing = the property is not available here). */
    private View check(String text, String v, final String file) {
        Ui.CheckRow r = Ui.checkRow(c, text, "1".equals(v), new Ui.OnToggle() {
            @Override public void toggled(boolean on) { Ctl.put(file, on ? "1" : "0"); }
        });
        r.setUsable("1".equals(v) || "0".equals(v));
        if (!r.isEnabled()) r.label.setText(Ui.t(c, text) + Ui.t(c, "(不支持)"));
        return r;
    }

    /** Two custom square buttons (hb1, hb2) stacked with a GAP_H gap; the top edge of the first and the bottom edge of the second sit on those of the square frame. A slot set to none stays empty. */
    private View customColumn() {
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < 2; i++) {
            final String id = Prefs.getStr(c, "hb" + (i + 1), HB_DEF[i]);
            final String arg = Prefs.getStr(c, "hb" + (i + 1) + "_arg", "");
            View v;
            if (Actions.find(id) == null) {   // none (or an id the table does not know)
                qCustom[i] = null;
                v = new View(c);
            } else {
                CircleButton cb = circle(QuickActions.icon(id), new View.OnClickListener() {
                    @Override public void onClick(View x) { run(id, arg); }
                });
                QuickActions.setState(cb, id);
                qCustom[i] = cb;
                customId[i] = id;
                cb.setSquare(true);   // custom buttons: square frame; the fixed buttons stay round
                v = cb;
            }
            int side = Ui.dp(c, (TILE - GAP_H) / 2);   // two buttons and the gap fill the height of the square exactly
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(side, side);
            if (i > 0) lp.topMargin = Ui.dp(c, GAP_H);
            col.addView(v, lp);
        }
        return col;
    }

    /** Airplane mode: radios on -> turn the ones that are on off; all off -> turn Wi-Fi back on. */
    private void airplane() {
        QuickActions.airplane();
        later();
    }

    /** The custom buttons: the same actions (and arguments) as the button widgets; the panel stays open unless the action leaves the page. */
    private void run(String id, String arg) {
        Actions.run(c, id, arg, leave);
        later();
        update();
    }
}
