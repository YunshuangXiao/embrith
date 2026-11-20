/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * NetworkActivity: Wi-Fi and Bluetooth settings: the switches ask the Kindle side to toggle the radios; also the
 * DNS choice. */
package com.embrith.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Map;

/**
 * Wi-Fi and Bluetooth. The radios belong to the Kindle: the switches ask the Kindle side (embrith.sh) to switch them, exactly as
 * the Kindle's own menu does. Android reaches the network through the Kindle's Wi-Fi (an "eth0" that the Kindle side provides).
 */
public class NetworkActivity extends Activity {
    private final Handler handler = new Handler();
    private TextView info;
    private Ui.Row dnsRow;

    private static final String[] DNS_NAMES = { "自动 (默认)", "阿里 223.5.5.5", "腾讯 119.29.29.29", "谷歌 8.8.8.8", "自定义..." };
    private static final String[] DNS_VALUES = { "", "223.5.5.5,223.6.6.6", "119.29.29.29,182.254.116.116", "8.8.8.8,8.8.4.4" };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout body = Ui.page(this, "本机网络", null);
        LinearLayout g = Ui.addGroup(body);
        info = Ui.text(this, "", 16, false);
        info.setPadding(Ui.dp(this, 18), Ui.dp(this, 16), Ui.dp(this, 18), Ui.dp(this, 16));
        g.addView(info);
        dnsRow = Ui.nav(this, "DNS 服务器", dnsLabel(), new android.view.View.OnClickListener() {
            @Override public void onClick(android.view.View v) { askDns(); }
        });
        Ui.add(g, dnsRow);
        Ui.note(this, body, "本机网络", "本机网络\t本机网络是安卓容器里的 eth0, 由 Kindle 的 Wi-Fi 转发出去. Wi-Fi 和蓝牙的开关在设置的网络页.");
    }

    /** The DNS of Android: DNS= in ctl/conf (comma separated, empty = automatic); the Kindle side applies it to netd (net.sh dns_apply). */
    private static String dnsLabel() {
        String v = Ctl.get("DNS", "");
        if (v.length() == 0) return DNS_NAMES[0];
        for (int i = 1; i < DNS_VALUES.length; i++) if (DNS_VALUES[i].equals(v)) return DNS_NAMES[i];
        return v.replace(",", " ");
    }

    private void setDns(String v) {
        Ctl.set("DNS", v);
        Ctl.request("dns_apply");
        dnsRow.setValue(dnsLabel());
        Ui.toast(this, "DNS 已设置", false);
    }

    private void askDns() {
        new Ui.Builder(this).setTitle("DNS 服务器").setItems(DNS_NAMES, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                if (which < DNS_VALUES.length) setDns(DNS_VALUES[which]); else askCustomDns();
            }
        }).setNegativeButton("取消", null).show();
    }

    private void askCustomDns() {
        final EditText in = new EditText(this);
        in.setInputType(InputType.TYPE_CLASS_TEXT);
        in.setHint(Ui.t(this, "例如 1.1.1.1 8.8.8.8"));
        String cur = Ctl.get("DNS", "");
        in.setText(cur.replace(",", " "));
        new Ui.Builder(this).setTitle("自定义 DNS (IPv4, 最多 3 个)").setView(in)
            .setPositiveButton("好", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    String[] parts = in.getText().toString().trim().split("[\\s,]+");
                    StringBuilder sb = new StringBuilder();
                    boolean ok = parts.length >= 1 && parts.length <= 3;
                    for (String p : parts) {
                        if (!p.matches("\\d{1,3}(\\.\\d{1,3}){3}")) { ok = false; break; }
                        if (sb.length() > 0) sb.append(',');
                        sb.append(p);
                    }
                    if (ok) setDns(sb.toString()); else Ui.toast(NetworkActivity.this, "请输入 1 到 3 个 IPv4 地址", true);
                }
            })
            .setNegativeButton("取消", null).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        update();
    }

    /** The Kindle side handles a request within about 2 seconds and publishes the new state a few seconds later. */
    private void later() {
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(new Runnable() { @Override public void run() { update(); } }, 4000);
        handler.postDelayed(new Runnable() { @Override public void run() { update(); } }, 8000);
    }

    private void update() {
        String e = ethState();
        if (!e.contentEquals(info.getText())) info.setText(e);
    }

    private String ethState() {
        try {
            NetworkInterface ni = NetworkInterface.getByName("eth0");
            if (ni == null) return "没有这个网卡（Kindle 侧没有提供网络：需要 bin/slirp4netns）";
            StringBuilder sb = new StringBuilder(ni.isUp() ? Ui.t(this, "已启用") : Ui.t(this, "未启用"));
            Enumeration<InetAddress> e = ni.getInetAddresses();
            while (e.hasMoreElements()) {
                InetAddress a = e.nextElement();
                if (!a.isLoopbackAddress() && a.getAddress().length == 4) sb.append(Ui.t(this, ",地址 ")).append(a.getHostAddress());
            }
            return sb.toString();
        } catch (SocketException ex) {
            return "读取失败";
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
