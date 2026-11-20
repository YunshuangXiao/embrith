/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * SshActivity: SSH settings page: for each source (Embrith, KOReader) a switch, a port, a login method and a
 * connection command. */
package com.embrith.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import java.io.File;
import java.util.Map;

/**
 * 更多 › 网络 › SSH 设置: 按来源(Embrith, KOReader)分组, 每组一个开关, 一个端口, 一个登录方式, 一条连接命令(登录方式各来源各存各的).
 * 状态来自 ctl/status 的 SSH_E / SSH_K (1 开, 0 关, -1 没有安装) / PORT_E / PORT_K / SSH_AUTH + SSH_PW (Embrith) / SSH_AUTH_K (KOReader) / IP.
 * 请求: ctl/ssh_src (来源名, 切换这个来源的开关), ctl/ssh_port ("来源 端口").
 */
public class SshActivity extends Activity {
    /** Kindle 用户存储在容器里只读挂在 /dev/kindle(= Kindle 的 /mnt/us).  */
    static final String[] KEY_FILES = { "/dev/kindle/.ssh/authorized_keys", "/dev/kindle/embrith/ssh/authorized_keys" };

    private final Handler handler = new Handler();
    private Ui.SwitchRow eSw, kSw;
    private Ui.Row ePort, kPort, eCmd, kCmd, eAuth, kAuth, eExtra, kExtra;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout body = Ui.page(this, "SSH 设置", null);
        Ui.caption(body, "Embrith");
        LinearLayout g = Ui.addGroup(body);
        eSw = Ui.switchRow(this, "SSH 远程登录", false, toggle("embrith"));
        ePort = Ui.nav(this, "端口", "", portClick("embrith"));
        eAuth = Ui.nav(this, "登录方式", "", authClick("embrith"));
        eExtra = Ui.row(this, "", "", null);
        eCmd = Ui.row(this, "连接命令", "", null);
        Ui.add(g, eSw);
        Ui.add(g, ePort);
        Ui.add(g, eAuth);
        Ui.add(g, eExtra);
        Ui.add(g, eCmd);
        Ui.caption(body, "KOReader");
        g = Ui.addGroup(body);
        kSw = Ui.switchRow(this, "SSH 远程登录", false, toggle("koreader"));
        kPort = Ui.nav(this, "端口", "", portClick("koreader"));
        kAuth = Ui.nav(this, "登录方式", "", authClick("koreader"));
        kExtra = Ui.row(this, "", "", null);
        kCmd = Ui.row(this, "连接命令", "", null);
        Ui.add(g, kSw);
        Ui.add(g, kPort);
        Ui.add(g, kAuth);
        Ui.add(g, kExtra);
        Ui.add(g, kCmd);
        Ui.note(this, body, "SSH 远程登录", "来源与端口\t切换后几秒内生效. 两个来源可以同时开, 但端口不能相同; 改端口时正在运行的会自动重启.\n\n"
            + "关闭\t关闭只停监听, 已连接的会话保留.\n\n"
            + "登录方式\t登录方式按各来源的 dropbear 提供: Embrith 有 密钥文件 / 密码(可自定义) / 免密, KOReader 有 密钥文件 / 系统密码(Kindle root) / 免密, 没有自定义密码.\n\n"
            + "书库里的开关\t不启动安卓时, 在 Kindle 书库里点 SSH 书目也能开关.");
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

    private void later() {
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(new Runnable() { @Override public void run() { update(); } }, 4000);
        handler.postDelayed(new Runnable() { @Override public void run() { update(); } }, 8000);
    }

    private static boolean emb(String src) { return "embrith".equals(src); }
    private static String label(String src) { return emb(src) ? "Embrith" : "KOReader"; }
    private static String stKey(String src) { return emb(src) ? "SSH_E" : "SSH_K"; }
    private static String portKey(String src) { return emb(src) ? "PORT_E" : "PORT_K"; }
    private Ui.SwitchRow sw(String src) { return emb(src) ? eSw : kSw; }

    private Ui.OnToggle toggle(final String src) {
        return new Ui.OnToggle() {
            @Override public void toggled(boolean on) {
                if ("-1".equals(Ctl.status().get(stKey(src)))) {
                    sw(src).setOn(false);
                    Ui.toast(SshActivity.this, Ui.t(SshActivity.this, "Kindle 上没有找到 ") + label(src) + Ui.t(SshActivity.this, " 的 dropbear"), true);
                } else {
                    Ctl.put("ssh_src", src);
                    later();
                }
            }
        };
    }

    private View.OnClickListener authClick(final String src) {
        return new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(SshActivity.this, SshAuthActivity.class).putExtra("src", src)); }
        };
    }

    private View.OnClickListener portClick(final String src) {
        return new View.OnClickListener() {
            @Override public void onClick(View v) { askPort(src); }
        };
    }

    private void askPort(final String src) {
        String cur = Ctl.status().get(portKey(src));
        final EditText in = new EditText(this);
        in.setInputType(InputType.TYPE_CLASS_NUMBER);
        in.setText(cur == null || cur.length() == 0 ? "2222" : cur);
        in.setSelectAllOnFocus(true);
        new Ui.Builder(this)
            .setTitle(label(src) + Ui.t(this, " 端口"))
            .setView(in)
            .setPositiveButton("好", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    int n;
                    try { n = Integer.parseInt(in.getText().toString().trim()); } catch (NumberFormatException e) { n = 0; }
                    String other = Ctl.status().get(portKey(emb(src) ? "koreader" : "embrith"));
                    if (n < 1 || n > 65535) {
                        Ui.toast(SshActivity.this, "端口范围 1 到 65535", true);
                    } else if (String.valueOf(n).equals(other)) {
                        Ui.toast(SshActivity.this, "这个端口已被另一个来源使用", true);
                    } else {
                        Ctl.put("ssh_port", src + " " + n);
                        later();
                    }
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    static String authText(String mode, String pw) {
        if ("key".equals(mode)) return "密钥文件";
        if ("none".equals(mode)) return "不使用密码";
        return "1".equals(pw) ? "密码（自定义）" : "密码（Kindle root）";
    }

    /** KOReader 的 dropbear 读它自己目录下的 settings/SSH/authorized_keys.  */
    static final String[] KO_KEY_FILES = { "/dev/kindle/koreader/settings/SSH/authorized_keys" };

    /** 公钥文件应该放的位置(Kindle 侧路径).  */
    static String keyHint(String src) {
        return emb(src) ? "/mnt/us/.ssh/authorized_keys" : "/mnt/us/koreader/settings/SSH/authorized_keys";
    }

    static String keyFile() { return keyFile("embrith"); }

    /** 这个来源第一个存在的公钥文件(Kindle 侧路径), 没有返回 null.  */
    static String keyFile(String src) {
        for (String f : emb(src) ? KEY_FILES : KO_KEY_FILES) {
            File k = new File(f);
            if (k.isFile() && k.length() > 0) return f.replace("/dev/kindle", "/mnt/us");
        }
        return null;
    }

    private void show(String src, Ui.SwitchRow s, Ui.Row port, Ui.Row cmd, Map<String, String> st, String ip) {
        String v = st.get(stKey(src));
        String p = st.get(portKey(src));
        if (p == null || p.length() == 0) p = "2222";
        s.setOn("1".equals(v));
        s.setNote("1".equals(v) || "0".equals(v) ? "" : "未安装");
        port.setValue(p);
        cmd.setValue(ip != null && ip.length() > 0 ? "ssh -p " + p + " root@" + ip : "Wi-Fi 未连接");
    }

    /** 登录方式一行, 以及密钥文件 / 免密的提示行.  */
    private void auth(String src, Ui.Row authRow, Ui.Row extraRow, String mode, String pw) {
        authRow.setValue(authText(mode, pw));
        if ("key".equals(mode)) {
            String k = keyFile(src);
            extraRow.setLabel("公钥文件");
            extraRow.setValue(k != null ? k : Ui.t(this, "未找到(放到 ") + keyHint(src) + Ui.t(this, ")"));
            extraRow.setVisibility(View.VISIBLE);
        } else if ("none".equals(mode)) {
            extraRow.setLabel("注意");
            extraRow.setValue("同一网络里任何人都能以 root 登录");
            extraRow.setVisibility(View.VISIBLE);
        } else {
            extraRow.setVisibility(View.GONE);
        }
    }

    /** 只更新各行的值.  */
    private void update() {
        Map<String, String> st = Ctl.status();
        String ip = st.get("IP");
        show("embrith", eSw, ePort, eCmd, st, ip);
        show("koreader", kSw, kPort, kCmd, st, ip);
        auth("embrith", eAuth, eExtra, st.get("SSH_AUTH"), st.get("SSH_PW"));
        auth("koreader", kAuth, kExtra, st.get("SSH_AUTH_K"), "0");
    }
}
