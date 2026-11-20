/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * SshAuthActivity: Login method page for one SSH source: key file, password, or no password. */
package com.embrith.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Map;

/**
 * 更多 › 网络 › SSH 设置 › 来源的登录方式(Intent extra "src": embrith 缺省 / koreader): 密钥文件 / 密码 / 不使用密码. KOReader 的 dropbear 没有自定义密码, 密码就是系统密码(Kindle root). 
 * Embrith: 写入 ctl/ssh_auth("key", "none", "password [$k$盐$sha256hex]"); KOReader: 写入 ctl/ssh_auth_k("key" / "password" / "none"). embrith.sh 交给 ssh-toggle.sh 保存并在运行中时重启这个来源. 
 * 自定义密码只以哈希形式离开本页, 格式与 host/tools/dropbear/embrith-auth.patch 一致: sha256(盐的 hex 文本 + 密码 UTF-8). 
 */
public class SshAuthActivity extends Activity {
    private static final String[] IDS = { "key", "password", "none" };
    private static final String[] TEXT = { "使用密钥文件", "使用密码", "不使用密码" };

    private final Handler handler = new Handler();
    private Ui.Row[] rows;
    private Ui.Row curRow;
    private String pending;     // 已提交、等待 Kindle 侧生效的选择
    private String src = "embrith";
    private boolean emb = true;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if ("koreader".equals(getIntent().getStringExtra("src"))) { src = "koreader"; emb = false; }
        LinearLayout body = Ui.page(this, emb ? "登录方式" : "登录方式 · KOReader", null);
        LinearLayout g = Ui.addGroup(body);
        rows = new Ui.Row[IDS.length];
        for (int i = 0; i < IDS.length; i++) {
            final String id = IDS[i];
            rows[i] = Ui.row(this, !emb && i == 1 ? "使用系统密码（Kindle root）" : TEXT[i], "", new View.OnClickListener() {
                @Override public void onClick(View v) { choose(id); }
            });
            Ui.add(g, rows[i]);
        }
        g = Ui.addGroup(body);
        curRow = Ui.row(this, "当前", "", null);
        Ui.add(g, curRow);
        if (emb) {
            Ui.note(this, body, "登录方式", "密钥文件\t电脑公钥放到 /mnt/us/.ssh/authorized_keys(或 embrith/ssh/).\n\n"
                + "密码\t留空用 Kindle root 密码, 或设自定义密码(只以哈希保存).\n\n"
                + "不使用密码\t同一网络的任何人都能登录, 只建议临时使用.");
        } else {
            Ui.note(this, body, "登录方式", "密钥文件\t公钥放到 /mnt/us/koreader/settings/SSH/authorized_keys.\n\n"
                + "系统密码\tKindle root 的密码, 不能自定义.\n\n"
                + "不使用密码\t同一网络的任何人都能登录, 只建议临时使用.");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        update();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    /** 只更新 ✓ / "正在应用..." 和"当前"一行.  */
    private void update() {
        Map<String, String> st = Ctl.status();
        String cur = st.get(emb ? "SSH_AUTH" : "SSH_AUTH_K");
        if (cur == null || cur.length() == 0) cur = "password";
        if (pending != null && pending.equals(cur)) pending = null;
        for (int i = 0; i < IDS.length; i++) {
            rows[i].setValue(IDS[i].equals(pending) ? "正在应用…" : IDS[i].equals(cur) && pending == null ? "✓" : "");
        }
        String hint;
        if ("key".equals(cur)) {
            String k = SshActivity.keyFile(src);
            hint = k != null ? Ui.t(this, "公钥:") + k : "没有公钥文件,将无法登录";
        } else if ("none".equals(cur)) {
            hint = "任何人都能以 root 登录";
        } else {
            hint = emb && "1".equals(st.get("SSH_PW")) ? "自定义密码" : "Kindle root 密码";
        }
        curRow.setValue(hint);
    }

    private void choose(String id) {
        if ("key".equals(id)) {
            if (SshActivity.keyFile(src) == null) {
                new Ui.Builder(this)
                    .setTitle("没有找到公钥文件")
                    .setMessage(Ui.t(this, "请先把电脑上的公钥(如 id_ed25519.pub)复制为 Kindle 的 ") + SshActivity.keyHint(src) + Ui.t(this, ",否则切换后无法登录.仍然切换?"))
                    .setPositiveButton("切换", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int w) { submit("key"); }
                    })
                    .setNegativeButton("取消", null)
                    .show();
            } else {
                submit("key");
            }
        } else if ("password".equals(id)) {
            if (emb) askPassword(); else submit("password");
        } else {
            new Ui.Builder(this)
                .setTitle("不使用密码")
                .setMessage("同一网络里的任何人都能不输密码以 root 登录这台 Kindle。只建议在自己的网络里临时使用。确定？")
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { submit("none"); }
                })
                .setNegativeButton("取消", null)
                .show();
        }
    }

    private void askPassword() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint(Ui.t(this, "留空 = 使用 Kindle root 密码"));
        new Ui.Builder(this)
            .setTitle("设置 SSH 密码")
            .setView(input)
            .setPositiveButton("好", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    String p = input.getText().toString();
                    if (p.length() == 0) {
                        submit("password");
                    } else if (p.length() < 4) {
                        Ui.toast(SshAuthActivity.this, "密码至少 4 位", true);
                    } else {
                        String h = hash(p);
                        if (h == null) Ui.toast(SshAuthActivity.this, "无法生成密码哈希", true);
                        else submit("password " + h);
                    }
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void submit(String value) {
        Ctl.put(emb ? "ssh_auth" : "ssh_auth_k", value);
        int sp = value.indexOf(' ');
        pending = sp > 0 ? value.substring(0, sp) : value;
        update();
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(new Runnable() { @Override public void run() { update(); } }, 4000);
        handler.postDelayed(new Runnable() { @Override public void run() { update(); } }, 9000);
    }

    /** $k$<16 位 hex 盐>$<sha256(盐 + 密码) 的小写 hex> */
    static String hash(String password) {
        try {
            byte[] s = new byte[8];
            new SecureRandom().nextBytes(s);
            String salt = hex(s);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt.getBytes("US-ASCII"));
            md.update(password.getBytes("UTF-8"));
            return "$k$" + salt + "$" + hex(md.digest());
        } catch (Exception e) {
            return null;
        }
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02x", x & 0xff));
        return sb.toString();
    }
}
