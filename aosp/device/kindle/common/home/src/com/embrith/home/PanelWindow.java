/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * PanelWindow: The pull-down panel as a floating window of the service over whatever is on screen, so the page
 * below is not paused. */
package com.embrith.home;

import android.content.Context;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;

/**
 * The pull-down panel as a floating window over whatever is on the screen (an app, the desktop): a window of the service, not an activity,
 * so the page below is not paused, stopped or left. It takes no focus (the app keeps the keyboard and the back key); a tap below the
 * panel or on the ^ bar closes it. A long press on the Wi-Fi or Bluetooth button shows the list as a second floating window over the panel
 * (the panel stays open). The same content is used by MenuActivity when the window cannot be added (panel mode of the settings).
 */
final class PanelWindow {
    private static View view;
    private static ControlPanel cp;
    private static WindowManager wm;
    private static View pickView;
    private static NetPick pick;
    private static WindowManager.LayoutParams pickLp;

    private PanelWindow() { }

    static boolean shown() { return view != null; }

    /** The panel: status row, the control area, the ^ bar, a line, and a transparent cover over the rest of the screen. */
    static View content(Context c, ControlPanel panel, final Runnable close) {
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(android.graphics.Color.WHITE);
        root.addView(new Ui.StatusRow(c), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 68)));
        root.addView(panel.build(), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout up = new LinearLayout(c);
        up.setGravity(Gravity.CENTER);
        up.setClickable(true);
        up.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { close.run(); } });
        up.addView(new IconView(c, Icons.CHEVRON_DOWN), new LinearLayout.LayoutParams(Ui.dp(c, 40), Ui.dp(c, 30)));
        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 60));
        ulp.topMargin = Ui.dp(c, ControlPanel.GAP_V / 2);
        root.addView(up, ulp);

        LinearLayout outer = new LinearLayout(c);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.addView(root, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        View line = new View(c);
        line.setBackgroundColor(android.graphics.Color.BLACK);
        outer.addView(line, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(c, 2))));
        View cover = new View(c);
        cover.setClickable(true);
        cover.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { close.run(); } });
        outer.addView(cover, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        return outer;
    }

    /** Adds the floating panel; false when the system refuses the window (the caller then falls back to the panel activity). */
    static boolean show(Context c) {
        if (view != null) return true;
        final Context app = c.getApplicationContext();
        Runnable close = new Runnable() { @Override public void run() { hide(); } };
        wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);   // the window manager of the service, as in the 2.0.4 panel
        ControlPanel panel = new ControlPanel(app, close, new ControlPanel.Picker() {
            @Override public void open(String kind) { showPick(app, kind); }
        });
        View v = content(app, panel, close);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP;
        lp.windowAnimations = 0;   // every animation frame is a refresh of the e-ink panel
        lp.setTitle("embrith_panel");
        try {
            wm.addView(v, lp);
        } catch (RuntimeException e) {
            return false;
        }
        view = v;
        cp = panel;
        cp.start();
        return true;
    }

    /** The Wi-Fi / Bluetooth list in a window of its own over the panel. */
    static void showPick(Context c, String kind) {
        hidePick();
        Context app = c.getApplicationContext();
        android.util.DisplayMetrics dm = app.getResources().getDisplayMetrics();
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams((int) (dm.widthPixels * 0.92f), (int) (dm.heightPixels * 0.78f),
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.CENTER;
        lp.windowAnimations = 0;
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        lp.setTitle("embrith_pick");
        NetPick np = new NetPick(app, kind, new NetPick.Host() {
            @Override public void close() { hidePick(); }
            @Override public void focus(boolean on) {
                if (pickView == null) return;
                if (on) pickLp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                else pickLp.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                try { wm.updateViewLayout(pickView, pickLp); } catch (RuntimeException e) { }
            }
        });
        android.widget.FrameLayout w = new android.widget.FrameLayout(app);
        w.setBackgroundResource(R.drawable.embrith_dialog);   // the same white frame as the dialogs
        w.addView(np.view());
        try {
            wm.addView(w, lp);
        } catch (RuntimeException e) {
            return;
        }
        pickView = w;
        pick = np;
        pickLp = lp;
        np.start();
    }

    static void hidePick() {
        if (pickView == null) return;
        if (pick != null) pick.stop();
        try { wm.removeView(pickView); } catch (RuntimeException e) { }
        pickView = null;
        pick = null;
        pickLp = null;
    }

    static void hide() {
        hidePick();
        if (view == null) return;
        if (cp != null) cp.stop();
        try { wm.removeView(view); } catch (RuntimeException e) { }
        view = null;
        cp = null;
    }
}