/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * LogsActivity: Settings page to ask the Kindle side for a log report of the last minutes and to list the report
 * folder. */
package com.embrith.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * Settings > General > Generate log. "Generate now" asks the Kindle side for a report of the last minutes (ctl/report_gen: the Kindle writes
 * it to logs/report/); the list below is that folder (ctl/report_list); a tap offers to view (ctl/report_view, answer ctl/report_text) or
 * delete (ctl/report_del) one report. It only talks to the Kindle side through ctl/, see docs/PROTOCOL.md.
 */
public class LogsActivity extends Activity {
    private static final int POLL_MS = 800;
    private static final int LIST_TRIES = 150;    // about 2 minutes: making a report reads logcat, dmesg and the daemon logs
    private static final int VIEW_TRIES = 25;

    private final Handler handler = new Handler();
    private LinearLayout list, all;
    private TextView state;
    private boolean busy;
    private int tries;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout body = Ui.page(this, "生成日志", null);
        LinearLayout g = Ui.addGroup(body);
        Ui.add(g, Ui.nav(this, "立即生成", "最近 5 分钟", new View.OnClickListener() {
            @Override public void onClick(View v) { ask("report_gen", null, "正在生成, 请稍等..."); }
        }));
        state = Ui.caption(body, "");
        list = Ui.addGroup(body);
        all = Ui.addGroup(body);
        list.setVisibility(View.GONE);   // shown by show() only when there are log files
        all.setVisibility(View.GONE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ask("report_ls", null, "读取中...");
    }

    @Override
    protected void onPause() {
        handler.removeCallbacksAndMessages(null);
        busy = false;
        super.onPause();
    }

    /** A request that the Kindle side answers with a fresh ctl/report_list. */
    private void ask(String req, String value, String msg) {
        if (busy) return;
        busy = true;
        tries = 0;
        say(msg);
        Ctl.remove("report_list");
        if (value == null) Ctl.request(req); else Ctl.put(req, value);
        handler.postDelayed(poll, POLL_MS);
    }

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            List<String> l = Ctl.lines("report_list");
            if (l != null) { busy = false; show(l); return; }
            if (++tries > LIST_TRIES) { busy = false; say("Kindle 侧没有回应"); return; }
            handler.postDelayed(this, POLL_MS);
        }
    };

    private void show(List<String> l) {
        list.removeAllViews();
        all.removeAllViews();
        int n = 0;
        for (String line : l) {
            String[] p = line.split("\t");
            if (p.length < 2) continue;
            final String name = p[0];
            long bytes = 0;
            try { bytes = Long.parseLong(p[1].trim()); } catch (NumberFormatException e) { bytes = 0; }
            n++;
            Ui.add(list, Ui.row(this, title(name), size(bytes), new View.OnClickListener() {
                @Override public void onClick(View v) { choose(name); }
            }));
        }
        if (n > 0) {
            Ui.add(all, Ui.row(this, "全部删除", n + Ui.t(this, " 个"), new View.OnClickListener() {
                @Override public void onClick(View v) { confirmAll(); }
            }));
        }
        // no log files: the caption and the two boxes are not shown at all
        int vis = n > 0 ? View.VISIBLE : View.GONE;
        list.setVisibility(vis);
        all.setVisibility(vis);
        if (n > 0) say("日志文件, 点一下查看或删除"); else state.setVisibility(View.GONE);
    }

    private void say(String msg) {
        state.setText(msg);
        state.setVisibility(View.VISIBLE);
    }

    private void choose(final String name) {
        new Ui.Builder(this)
            .setTitle(title(name))
            .setPositiveButton("查看", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int which) { view(name); }
            })
            .setNegativeButton("删除", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int which) { ask("report_del", name, "正在删除..."); }
            })
            .setNeutralButton("取消", null)
            .show();
    }

    private void confirmAll() {
        new Ui.Builder(this)
            .setTitle("全部删除")
            .setMessage("删除 logs/report/ 里的全部日志文件")
            .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int which) { ask("report_del", "*", "正在删除..."); }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    /** The Kindle side puts the first 64 KB of the report into ctl/report_text; the full text page is InfoActivity. */
    private void view(final String name) {
        if (busy) return;
        say("读取中...");
        Ctl.remove("report_text");
        Ctl.put("report_view", name);
        final int[] n = { 0 };
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                String t = Ctl.readText("report_text", 70000);
                if (t != null) {
                    Ctl.remove("report_text");
                    InfoActivity.open(LogsActivity.this, title(name), t);
                    return;
                }
                if (++n[0] > VIEW_TRIES) { say("Kindle 侧没有回应"); return; }
                handler.postDelayed(this, POLL_MS);
            }
        }, POLL_MS);
    }

    /** report-20261111-210533.txt -> 2026-11-11 21:05:33 */
    private static String title(String name) {
        if (name.length() != 26 || !name.startsWith("report-")) return name;
        String d = name.substring(7, 15), t = name.substring(16, 22);
        return d.substring(0, 4) + "-" + d.substring(4, 6) + "-" + d.substring(6, 8) + " " + t.substring(0, 2) + ":" + t.substring(2, 4) + ":" + t.substring(4, 6);
    }

    private static String size(long bytes) {
        return bytes < 1024 ? bytes + " B" : (bytes + 512) / 1024 + " KB";
    }
}