/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * BackupActivity: Export and import of settings. The host (backup.sh) packs the files; the launcher Prefs and the
 * usage statistics are handed over as tsv files in /sdcard/Embrith/backup/stage. */
package com.embrith.home;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.LinearLayout;

import com.embrith.language.T;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Map;

/** Export / import of the settings: the types are chosen with switches; the backup file is embrith/backup/embrith-DATE.tar (visible over USB). */
public class BackupActivity extends Activity {
    private static final File STAGE = new File("/sdcard/Embrith/backup/stage");
    private static final String[] IDS = { "sys", "disp", "widget", "ssh", "lib", "usage" };
    private static final String[] NAMES = { "系统设置", "显示设置", "桌面与个性化(含壁纸路径)", "SSH 设置", "阅读记录", "使用统计" };
    private static final String USAGE_KEY = "usage_days";
    /** Layout caches that are rebuilt by themselves: not part of a backup. */
    private static final String[] SKIP = { "sh_h", "sh_hmax", "dh_h", "dh_hmax", "usage_ck", USAGE_KEY };
    private final boolean[] on = { true, true, true, true, true, true };
    private final Handler handler = new Handler();
    private String reqId = "";
    private boolean importing;
    private int tries;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout body = Ui.page(this, "备份与恢复", new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        LinearLayout g = Ui.addGroup(body);
        for (int i = 0; i < IDS.length; i++) {
            final int k = i;
            Ui.add(g, Ui.switchRow(this, NAMES[i], true, new Ui.OnToggle() {
                @Override public void toggled(boolean v) { on[k] = v; }
            }));
        }
        g = Ui.addGroup(body);
        Ui.add(g, Ui.nav(this, "导出", "", new View.OnClickListener() {
            @Override public void onClick(View v) { start(false); }
        }));
        Ui.add(g, Ui.nav(this, "导入(最新的备份文件)", "", new View.OnClickListener() {
            @Override public void onClick(View v) { start(true); }
        }));
        Ui.note(this, body, "备份与恢复", "导出\t把勾选的类型打包到 embrith/backup/embrith-日期.tar, 连 USB 即可拷走.\n\n"
            + "导入\t读取 embrith/backup 里最新的 embrith-*.tar, 只导入勾选且备份里有的类型. 导入前当前状态会先存成 pre-import-日期.tar.\n\n"
            + "系统设置\t不含 SSH 项, 也不含应用数量和开发模式.\n\n"
            + "显示设置\t下次启动 Embrith 时生效.\n\n"
            + "桌面与个性化\t只保存壁纸的路径, 不含图片本身.\n\n"
            + "SSH 设置\t端口, 登录方式, 密码哈希, 以及 /mnt/us/.ssh 里的密钥和 authorized_keys. 备份文件含这些内容, 请妥善保管.\n\n"
            + "阅读记录\t每本书的进度, 最近阅读, 书库文件夹和书籍信息缓存.\n\n"
            + "使用统计\t应用使用时长, 以及阅读时长的完整记录(readlog).\n\n"
            + "换设备或重装后\t先把 embrith/backup 里的 tar 放回新设备同一位置, 再全选导入, 最后重启 Embrith.");
    }

    private String cats() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < IDS.length; i++) if (on[i]) sb.append(sb.length() > 0 ? "," : "").append(IDS[i]);
        return sb.toString();
    }

    private boolean has(String c, String id) { return ("," + c + ",").contains("," + id + ","); }

    private void start(boolean imp) {
        String c = cats();
        if (c.length() == 0) { Ui.toast(this, "没有勾选任何类型", false); return; }
        importing = imp;
        reqId = "r" + System.currentTimeMillis();
        tries = 0;
        if (!imp) {
            STAGE.mkdirs();
            if (has(c, "widget")) writeTsv(new File(STAGE, "prefs.tsv"), false);
            if (has(c, "usage")) writeTsv(new File(STAGE, "usage.tsv"), true);
        }
        Ctl.put(imp ? "backup_import" : "backup_export", reqId + " " + c);
        Ui.toast(this, imp ? "正在导入..." : "正在导出...", false);
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(poll, 1000);
    }

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            String t = Ctl.text("backup");
            String id = "", state = "", msg = "";
            if (t != null) for (String line : t.split("\n")) {
                if (line.startsWith("id=")) id = line.substring(3).trim();
                else if (line.startsWith("state=")) state = line.substring(6).trim();
                else if (line.startsWith("msg=")) msg = line.substring(4).trim();
            }
            if (reqId.equals(id) && ("done".equals(state) || "err".equals(state))) {
                if ("err".equals(state)) { Ui.toast(BackupActivity.this, T.t(BackupActivity.this, "失败: ") + msg, true); return; }
                if (importing) applyPrefs(msg);
                Ui.toast(BackupActivity.this, importing ? "已导入, 重启 Embrith 后全部生效" : T.t(BackupActivity.this, "已导出: ") + msg, true);
                return;
            }
            if (++tries < 90) handler.postDelayed(this, 1000);
            else Ui.toast(BackupActivity.this, "宿主没有回应", true);
        }
    };

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private SharedPreferences sp() { return getSharedPreferences("embrith", MODE_PRIVATE); }

    private static boolean skipped(String k) {
        for (String s : SKIP) if (s.equals(k)) return true;
        return false;
    }

    /** prefs.tsv: every string key except the caches; usage.tsv: the usage_days key only. One "key TAB value" per line. */
    private void writeTsv(File f, boolean usage) {
        try {
            Writer w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
            for (Map.Entry<String, ?> e : sp().getAll().entrySet()) {
                String k = e.getKey();
                if (!(e.getValue() instanceof String)) continue;
                if (usage != USAGE_KEY.equals(k)) continue;
                if (!usage && skipped(k)) continue;
                String v = (String) e.getValue();
                if (v.indexOf('\t') >= 0 || v.indexOf('\n') >= 0) continue;
                w.write(k + "\t" + v + "\n");
            }
            w.close();
            f.setReadable(true, false);
        } catch (Exception e) {
            // the export goes on without this file
        }
    }

    private void readTsv(File f, boolean usage, SharedPreferences.Editor ed) {
        if (!f.isFile()) return;
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            String line;
            while ((line = r.readLine()) != null) {
                int t = line.indexOf('\t');
                if (t <= 0) continue;
                String k = line.substring(0, t);
                if (usage != USAGE_KEY.equals(k) || (!usage && skipped(k))) continue;
                ed.putString(k, line.substring(t + 1));
            }
            r.close();
        } catch (Exception e) {
            // a broken file changes nothing more
        }
    }

    private void applyPrefs(String done) {
        SharedPreferences.Editor ed = sp().edit();
        if (has(done, "widget")) readTsv(new File(STAGE, "prefs.tsv"), false, ed);
        if (has(done, "usage")) readTsv(new File(STAGE, "usage.tsv"), true, ed);
        ed.commit();
    }
}
