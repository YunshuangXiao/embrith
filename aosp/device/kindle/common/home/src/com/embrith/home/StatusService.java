/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * StatusService: Service that watches the control directory and reacts to what the Kindle side asks for, in front
 * of every app (pull-down panel, USB page and others). */
package com.embrith.home;

import android.app.Service;
import android.content.Intent;
import android.os.FileObserver;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.io.File;

/**
 * Watches the control directory and reacts to what the Kindle side asks for, in front of every app: the pull-down from the top edge
 * (kgest creates the file ctl/statusbar) opens the pull-down panel (a floating window over the page), or closes it when it is already shown; USB, sleep and wake.
 */
public class StatusService extends Service {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private FileObserver observer;

    @Override
    public IBinder onBind(Intent i) {
        return null;
    }

    @Override
    public int onStartCommand(Intent i, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        SleepLog.end(this, System.currentTimeMillis());   // a sleep still open at start: the device was off meanwhile
        WhiteBg.start(getApplication());
        observer = new FileObserver(Ctl.DIR, FileObserver.CREATE | FileObserver.MOVED_TO | FileObserver.CLOSE_WRITE) {
            @Override
            public void onEvent(int event, String path) {
                if (path == null) return;
                if ("status".equals(path)) { Ui.StatusRow.statusChanged(); return; }
                if (!"statusbar".equals(path) && !"menu".equals(path) && !"sleeping".equals(path) && !"wake".equals(path)
                        && !"usb".equals(path) && !"usb_off".equals(path)) return;
                if (!new File(Ctl.DIR, path).delete()) return;      // CREATE and CLOSE_WRITE both arrive: only the first one finds the file
                if ("sleeping".equals(path)) SleepLog.begin(StatusService.this, System.currentTimeMillis());   // for the screen time: the sleep is not time in front
                else if ("wake".equals(path)) SleepLog.end(StatusService.this, System.currentTimeMillis());
                final String what = path;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        handleRequest(what);
                    }
                });
            }
        };
        observer.startWatching();
        if (new File(Ctl.DIR, "usb").delete()) handleRequest("usb");      // written before this service was up
    }

    @Override
    public void onDestroy() {
        if (observer != null) observer.stopWatching();
        WhiteBg.stop();
        super.onDestroy();
    }

    private void handleRequest(String what) {
        if ("usb".equals(what)) {                     // Kindle side: a computer is connected over USB
            MenuActivity.closeHome();
            UsbActivity.active = true;
            openActivity(UsbActivity.class);
        } else if ("usb_off".equals(what)) {
            UsbActivity.close();
        } else if (UsbActivity.active && !"sleeping".equals(what) && !"wake".equals(what)) {
            return;                                   // the USB page is exclusive: no settings home
        } else if ("statusbar".equals(what)) {
            if (PanelWindow.shown() || MenuActivity.panelShown()) {
                MenuActivity.closePanel();
            } else {
                Ctl.put("turn", System.currentTimeMillis() + " u");   // a UI update, not a page turn
                if (!PanelWindow.show(this)) {                        // a floating window over the page: the page below is not left
                    Intent i = new Intent(this, PanelActivity.class).putExtra("panel", true);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                }
            }
        } else if ("menu".equals(what)) {
            openActivity(MenuActivity.class);
        } else if ("sleeping".equals(what)) {         // Kindle side: going to sleep (power button, cover, menu, idle)
            MenuActivity.closeHome();
            openActivity(SleepActivity.class);
        } else if ("wake".equals(what)) {
            SleepActivity.close();
        }
    }

    private void openActivity(Class<?> cls) {
        Intent i = new Intent(this, cls);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }
}
