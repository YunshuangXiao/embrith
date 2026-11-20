/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * UpdateActivity: Restart and update page: shows the update package on the Kindle and restarts Embrith with a
 * normal or a forced update. */
package com.embrith.home;

import android.app.Activity;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

/**
 * Restart and update: look at the update package on the Kindle (embrith/src, seen as /dev/kindle/embrith/src), then restart Embrith with a
 * normal (verified) update or a forced one (no check, no version compare). The host does the install between stop and start (update.sh --restart).
 */
public class UpdateActivity extends Activity {
    private static final File SRC = new File("/dev/kindle/embrith/src");
    private Ui.Row checkRow;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout body = Ui.page(this, "重启并更新", new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        LinearLayout g = Ui.addGroup(body);
        checkRow = Ui.nav(this, "检查本地更新", "", new View.OnClickListener() {
            @Override public void onClick(View v) { showCheck(); }
        });
        Ui.add(g, checkRow);
        Ui.add(g, Ui.nav(this, "校验更新", "", new View.OnClickListener() {
            @Override public void onClick(View v) { confirm(false); }
        }));
        Ui.add(g, Ui.nav(this, "强制更新", "", new View.OnClickListener() {
            @Override public void onClick(View v) { confirm(true); }
        }));
        Ui.note(this, body, "重启并更新", "检查本地更新\t看 Kindle 上 embrith 文件夹的 src 里有没有更新包, 以及它的版本号.\n\n"
            + "校验更新\t重启前按常规规则安装; 更新包必须完整, 校验通过, 并且版本号高于当前版本, 否则不安装, 照常重启.\n\n"
            + "强制更新\t重启前不校验, 不比较版本号, 直接覆盖安装 src 里的文件; 版本更低, 不完整或损坏的包也会被装上, 可能导致 Embrith 无法启动, 只对这一次生效.\n\n"
            + "重启与还原\t两种更新都会重启 Embrith, 约需一分钟; 安装失败或启动失败时, 下次运行会自动还原.");
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkRow.setValue(summary());
    }

    /** The version of the package in embrith/src (first line of VERSION), "" when there is none, null when the folder has files but no VERSION. */
    private static String packageVersion() {
        String[] names = SRC.list();
        if (names == null || names.length == 0) return "";
        try {
            BufferedReader r = new BufferedReader(new FileReader(new File(SRC, "VERSION")));
            try {
                String l = r.readLine();
                return l == null ? null : l.trim();
            } finally {
                r.close();
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean newer(String a, String b) {
        String[] x = a.split("\\."), y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? num(x[i]) : 0, q = i < y.length ? num(y[i]) : 0;
            if (p != q) return p > q;
        }
        return false;
    }

    private static int num(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    private String installed() {
        String v = Ctl.status().get("VER");
        return v == null ? "" : v;
    }

    private String summary() {
        String v = packageVersion();
        if (v != null && v.length() == 0) return Ui.t(this, "没有更新包");
        if (v == null) return Ui.t(this, "缺少 VERSION");
        return v;
    }

    private void showCheck() {
        String v = packageVersion(), cur = installed();
        String msg;
        if (v != null && v.length() == 0) {
            msg = Ui.t(this, "Kindle 的 embrith/src 里没有更新包.");
        } else if (v == null) {
            msg = Ui.t(this, "embrith/src 里有文件, 但没有读到 VERSION, 校验更新不会安装它.");
        } else {
            boolean raw = new File(SRC, "embrith.raw").exists();
            msg = Ui.t(this, "更新包版本: ") + v + (raw ? Ui.t(this, " (含 embrith.raw)") : Ui.t(this, " (纯代码, 不含 embrith.raw)"))
                + Ui.t(this, "\n当前版本: ") + (cur.length() == 0 ? Ui.t(this, "未知") : cur) + "\n";
            if (cur.length() > 0 && !newer(v, cur)) msg += Ui.t(this, "版本不高于当前版本: 校验更新会跳过它, 需要覆盖请用强制更新.");
            else msg += Ui.t(this, "校验更新可以安装它.");
        }
        new Ui.Builder(this).setTitle("检查本地更新").setMessage(msg).setPositiveButton("好", null).show();
    }

    private void confirm(final boolean force) {
        String msg = force
            ? "重启 Embrith, 并在重启前把 embrith/src 里的文件直接覆盖安装: 不校验是否完整, 不比较版本号, 只覆盖放进 src 的文件. 版本更低, 不完整或损坏的包也会被装上, 可能导致 Embrith 无法启动. 被替换的文件会保留 .old 副本, 启动失败时下次运行自动还原. 只对这一次生效, 不改变开发者模式的设置."
            : "重启 Embrith, 并在重启前安装 Kindle 上 embrith/src 里的更新包. 更新包必须完整, 校验通过, 并且版本号高于当前版本, 否则不安装, 照常重启. 重启约需一分钟.";
        new Ui.Builder(this).setTitle(force ? "强制更新" : "校验更新").setMessage(msg)
            .setPositiveButton("重启并更新", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    Ui.toast(UpdateActivity.this, "正在重启并更新 Embrith...", false);
                    Ctl.request(force ? "restart_update_force" : "restart_update");
                }
            })
            .setNegativeButton("取消", null).show();
    }
}
