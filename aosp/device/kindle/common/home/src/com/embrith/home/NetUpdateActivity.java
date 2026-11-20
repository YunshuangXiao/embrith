/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * NetUpdateActivity: Check for update: shows the newest GitHub release; the host does the work through ctl/netupd*
 * and the page offers to update now. */
package com.embrith.home;

import android.app.Activity;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.LinearLayout;

import java.util.List;

/**
 * Check for update: the newest GitHub release. The host does the work (netupdate.sh: ctl/netupd_check, ctl/netupd_get) and answers in ctl/netupd
 * (state, cur, new, msg) and ctl/netupd_log (the change log); "update now" restarts Embrith with restart_update_net. It is a channel of its
 * own: the manual package in embrith/src (UpdateActivity) is not touched.
 */
public class NetUpdateActivity extends Activity {
    private static final int POLL_MS = 2000;
    private final Handler handler = new Handler();
    private LinearLayout group;
    private Ui.Row curRow, newRow, checkRow, nowRow, logRow;
    private boolean logAdded;
    private boolean installAfter;      // "update now" on a release that is not downloaded yet: restart when it is
    private String state = "", cur = "", nver = "", msg = "";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout body = Ui.page(this, "检查更新", new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        LinearLayout g = Ui.addGroup(body);
        curRow = Ui.row(this, "当前版本", "", null);
        Ui.add(g, curRow);
        newRow = Ui.row(this, "最新版本", "", null);
        Ui.add(g, newRow);
        group = Ui.addGroup(body);
        checkRow = Ui.nav(this, "检查更新", "", new View.OnClickListener() {
            @Override public void onClick(View v) { check(); }
        });
        Ui.add(group, checkRow);
        nowRow = Ui.nav(this, "现在更新", "", new View.OnClickListener() {
            @Override public void onClick(View v) { updateNow(); }
        });
        Ui.add(group, nowRow);
        logRow = Ui.nav(this, "更新日志", "", new View.OnClickListener() {
            @Override public void onClick(View v) { showLog(); }
        });
        g = Ui.addGroup(body);
        Ui.add(g, Ui.switchRow(this, "自动更新", "1".equals(Ctl.get("NET_UPDATE_AUTO", "0")), new Ui.OnToggle() {
            @Override public void toggled(boolean on) { Ctl.set("NET_UPDATE_AUTO", on ? "1" : "0"); }
        }));
        Ui.note(this, body, "检查更新", "GitHub 更新\t从 GitHub 的最新发布(release)更新 Embrith, 和重启并更新里的本地手动更新互不影响.\n\n"
            + "现在更新\t下载新版本, 然后重启 Embrith 安装, 约需几分钟.\n\n"
            + "自动更新\t开启后, 每次启动 Embrith 时(20 小时内最多一次)检查并下载新版本, 在下次从 Kindle 启动 Embrith 时安装, 不会自动重启.\n\n"
            + "网络条件\t需要先连上 Wi-Fi.");
    }

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            refresh();
            handler.postDelayed(this, POLL_MS);
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        poll.run();
    }

    @Override
    protected void onPause() {
        handler.removeCallbacksAndMessages(null);
        super.onPause();
    }

    private void read() {
        List<String> l = Ctl.lines("netupd");
        if (l == null) return;
        String s = "", c = "", n = "", m = "";
        for (String x : l) {
            int i = x.indexOf('=');
            if (i < 0) continue;
            String k = x.substring(0, i), v = x.substring(i + 1).trim();
            if (k.equals("state")) s = v;
            else if (k.equals("cur")) c = v;
            else if (k.equals("new")) n = v;
            else if (k.equals("msg")) m = v;
        }
        state = s; cur = c; nver = n; msg = m;
    }

    private static String errorText(String code) {
        if (code.equals("nocurl")) return "Kindle 上没有 curl";
        if (code.equals("nounzip")) return "Kindle 上没有 unzip";
        if (code.equals("nonet")) return "无法连接 GitHub, 请检查 Wi-Fi";
        if (code.equals("norelease")) return "没有找到发布的版本";
        if (code.equals("badtag")) return "发布的版本号格式不对";
        if (code.equals("noasset")) return "发布里没有 .zip 安装包";
        if (code.equals("badpkg")) return "安装包不完整";
        return "出错";
    }

    private void refresh() {
        read();
        String c = cur.length() > 0 ? cur : Ctl.status().get("VER");
        curRow.setValue(c == null ? "" : c);
        newRow.setValue(nver.length() > 0 && !state.equals("error") ? nver : "");
        boolean found = state.equals("new") || state.equals("ready") || state.equals("downloading");
        if (state.equals("checking")) checkRow.setValue("正在检查...");
        else if (state.equals("latest")) checkRow.setValue("已是最新版本");
        else if (found) checkRow.setValue("发现新版本");
        else if (state.equals("error")) checkRow.setValue(errorText(msg));
        else checkRow.setValue("");
        nowRow.setValue(state.equals("downloading") ? "正在下载..." : state.equals("ready") ? "已下载" : "");
        if (found && !logAdded) {
            logAdded = true;
            Ui.add(group, logRow);
        }
        if (installAfter && state.equals("ready")) {
            installAfter = false;
            restart();
        } else if (installAfter && state.equals("error")) {
            installAfter = false;
        }
    }

    private void check() {
        if (state.equals("checking") || state.equals("downloading")) return;
        Ctl.remove("netupd");
        Ctl.remove("netupd_log");
        Ctl.request("netupd_check");
        state = "checking";
        checkRow.setValue("正在检查...");
    }

    private void updateNow() {
        if (state.equals("downloading")) {
            Ui.toast(this, "正在下载...", false);
            return;
        }
        final boolean ready = state.equals("ready");
        if (!ready && !state.equals("new")) {
            Ui.toast(this, state.equals("latest") ? "已是最新版本" : "请先检查更新", false);
            return;
        }
        new Ui.Builder(this).setTitle("现在更新")
            .setMessage(Ui.t(this, ready ? "新版本 " : "下载新版本 ") + nver
                + Ui.t(this, ready ? " 已下载. 重启 Embrith 并安装它, 约需一分钟." : ", 然后重启 Embrith 并安装. 下载需要几分钟, 请保持 Wi-Fi 连接."))
            .setPositiveButton(ready ? "重启并更新" : "下载并更新", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    if (ready) {
                        restart();
                    } else {
                        installAfter = true;
                        Ctl.request("netupd_get");
                        state = "downloading";
                        refresh();
                    }
                }
            })
            .setNegativeButton("取消", null).show();
    }

    private void restart() {
        Ui.toast(this, "正在重启并更新 Embrith...", false);
        Ctl.request("restart_update_net");
    }

    private void showLog() {
        String t = Ctl.readText("netupd_log", 20000);
        if (t == null || t.trim().length() == 0) t = Ui.t(this, "这个版本没有写更新日志.");
        new Ui.Builder(this).setTitle(Ui.t(this, "更新日志") + " " + nver).setMessage(t).setPositiveButton("好", null).show();
    }
}
