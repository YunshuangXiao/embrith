/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * NetPick: Content that switches the Wi-Fi network or the Bluetooth device; used by NetPickActivity and by the
 * floating panel window. */
package com.embrith.home;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The content that switches the Wi-Fi network or the Bluetooth device. Shown by NetPickActivity (network settings page) and as a floating
 * window over the pull-down panel (long press on the Wi-Fi or Bluetooth button, see PanelWindow). The radios belong to the Kindle, so this
 * only talks to the Kindle side through ctl/: it asks for a list (net_list = wifi or bt), reads the answer (wifi_list or bt_list, see
 * docs/PROTOCOL.md) and sends the choice (wifi_join, bt_connect, bt_disconnect). A Wi-Fi password is only asked for when the user turned on
 * WIFI_PSK_INPUT (settings, network); it is typed into a form in this content, sent once through ctl/wifi_add (owner-only file) and never stored here.
 */
final class NetPick {
    /** What the window around this content has to provide. */
    interface Host {
        void close();
        void focus(boolean on);   // the password form needs the keyboard: a window that normally takes no focus has to take it for a moment
    }

    private final Context c;
    private final Host host;
    private final String kind;           // "wifi" or "bt"
    private final boolean wifi;
    private final String file;           // answer file: wifi_list or bt_list
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final LinearLayout box;
    private Ui.SwitchRow radio;
    private TextView state;
    private LinearLayout list;
    private int tries;

    NetPick(Context c, String kind, Host host) {
        this.c = c;
        this.host = host;
        this.kind = "bt".equals(kind) ? "bt" : "wifi";
        this.wifi = "wifi".equals(this.kind);
        this.file = wifi ? "wifi_list" : "bt_list";

        box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 8));

        TextView title = Ui.text(c, wifi ? "选择 Wi-Fi 网络" : "选择蓝牙设备", 22, true);
        title.setPadding(Ui.dp(c, 6), Ui.dp(c, 4), 0, Ui.dp(c, 8));
        box.addView(title);

        radio = Ui.switchRow(c, wifi ? "Wi-Fi" : "蓝牙", false, new Ui.OnToggle() {
            @Override public void toggled(boolean on) { Ctl.request(NetPick.this.kind.equals("wifi") ? "wifi" : "bt"); relistLater(6000); relistLater(10000); }
        });
        LinearLayout g0 = Ui.group(c);
        Ui.add(g0, radio);
        Ui.addGroup(box, g0);

        state = Ui.text(c, "", 15, false);
        state.setPadding(Ui.dp(c, 6), Ui.dp(c, 4), 0, Ui.dp(c, 4));
        box.addView(state);

        ScrollView sv = new ScrollView(c);
        list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        box.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView note = Ui.text(c, wifi ? ("1".equals(Ctl.get("WIFI_PSK_INPUT", "0"))
                                              ? "列表来自 Kindle 最近一次扫描. 点未保存的加密网络可输入密码; 企业网络不支持. "
                                              : "列表来自 Kindle 最近一次扫描. 未保存的加密网络请先在 Kindle 的设置里连接一次, 或在设置的网络页允许输入 Wi-Fi 密码; 企业网络不支持. ")
                                          : "只列出已配对的设备. 新设备请先在 Kindle 的设置里配对一次. ", 13, false);
        note.setPadding(Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6), 0);
        box.addView(note);

        LinearLayout bar = new LinearLayout(c);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        Ui.FlatButton refresh = Ui.button(c, "刷新");
        refresh.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { request(); } });
        Ui.FlatButton close = Ui.button(c, "关闭");
        close.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { NetPick.this.host.close(); } });
        bar.addView(refresh, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        bar.addView(close, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(bar);
    }

    View view() {
        return box;
    }

    /** Ask for a fresh list (call when the content is shown) and stop all waiting (call when it goes away). */
    void start() {
        request();
    }

    void stop() {
        handler.removeCallbacksAndMessages(null);
    }

    private void relistLater(long ms) {
        handler.postDelayed(new Runnable() { @Override public void run() { request(); } }, ms);
    }

    /** Ask the Kindle side for a fresh list and wait for the answer file (its watchdog runs about every 2 seconds). */
    private void request() {
        Map<String, String> st = Ctl.status();
        String on = st.get(wifi ? "WIFI" : "BT");
        radio.setOn("1".equals(on));
        Ctl.remove(file);
        Ctl.put("net_list", kind);
        tries = 0;
        state.setText("正在读取...");
        handler.removeCallbacks(poll);
        handler.postDelayed(poll, 800);
    }

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            List<String> ls = Ctl.lines(file);
            if (ls != null) { show(ls); return; }
            if (++tries > 12) { state.setText("Kindle 侧没有响应"); return; }
            handler.postDelayed(this, 800);
        }
    };

    private static final class Item {
        boolean connected, known, secured, enterprise;
        int signal;
        String id, name;                 // id: essid (Wi-Fi) or MAC (Bluetooth)
    }

    private void show(List<String> ls) {
        List<Item> items = new ArrayList<Item>();
        for (String l : ls) {
            String[] f = l.split("\t", wifi ? 6 : 3);
            Item it = new Item();
            try {
                if (wifi && f.length == 6) {
                    it.connected = "1".equals(f[0]); it.known = "1".equals(f[1]); it.secured = "1".equals(f[2]);
                    it.enterprise = "1".equals(f[3]); it.signal = Integer.parseInt(f[4]); it.id = f[5]; it.name = f[5];
                } else if (!wifi && f.length == 3) {
                    it.connected = "1".equals(f[0]); it.known = true; it.id = f[1]; it.name = f[2].length() > 0 ? f[2] : f[1];
                } else {
                    continue;
                }
            } catch (NumberFormatException e) {
                continue;
            }
            items.add(it);
        }
        Collections.sort(items, new Comparator<Item>() {
            @Override public int compare(Item a, Item b) {
                if (a.connected != b.connected) return a.connected ? -1 : 1;
                if (a.known != b.known) return a.known ? -1 : 1;
                return b.signal - a.signal;
            }
        });
        list.removeAllViews();
        boolean on = "1".equals(Ctl.status().get(wifi ? "WIFI" : "BT"));
        if (items.isEmpty()) {
            state.setText(!on ? (wifi ? "Wi-Fi 已关闭" : "蓝牙已关闭") : (wifi ? "没有找到网络" : "没有已配对的设备"));
            return;
        }
        state.setText((wifi ? Ui.t(c, "网络(") : Ui.t(c, "设备(")) + items.size() + ")");
        LinearLayout g = Ui.group(c);
        for (final Item it : items) {
            Ui.add(g, Ui.row(c, it.name, value(it), new View.OnClickListener() {
                @Override public void onClick(View v) { pick(it); }
            }));
        }
        Ui.addGroup(list, g);
    }

    /** WPA passphrase: 8 to 63 characters. A form in the place of the list; the text is handed to ctl/wifi_add and not kept. */
    private void askPassword(final Item it) {
        handler.removeCallbacks(poll);
        list.removeAllViews();
        state.setText(it.name);
        final EditText in = new EditText(c);
        in.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        in.setHint(Ui.t(c, "密码(8 到 63 个字符)"));
        list.addView(in, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout bar = new LinearLayout(c);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        Ui.FlatButton ok = Ui.button(c, "连接");
        ok.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String p = in.getText().toString();
                if (p.length() < 8 || p.length() > 63 || p.indexOf('\n') >= 0) { Ui.toast(c, "密码长度应为 8 到 63 个字符", true); return; }
                if ((p + it.id).indexOf('"') >= 0 || (p + it.id).indexOf('\\') >= 0) { Ui.toast(c, "暂不支持含引号或反斜杠的网络名或密码", true); return; }
                host.focus(false);
                Ctl.putSecret("wifi_add", it.id + "\n" + p);
                Ui.toast(c, Ui.t(c, "正在连接 ") + it.name + "...", false);
                request();
                relistLater(8000);
                relistLater(16000);
            }
        });
        Ui.FlatButton no = Ui.button(c, "取消");
        no.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { host.focus(false); request(); }
        });
        bar.addView(ok, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        bar.addView(no, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        list.addView(bar);
        host.focus(true);
        in.requestFocus();
    }

    private String value(Item it) {
        if (!wifi) return it.connected ? "已连接" : "已配对";
        StringBuilder sb = new StringBuilder();
        if (it.connected) sb.append(Ui.t(c, "已连接  "));
        else if (it.known) sb.append(Ui.t(c, "已保存  "));
        else if (it.enterprise) sb.append(Ui.t(c, "企业  "));
        else if (it.secured) sb.append(Ui.t(c, "加密  "));
        sb.append(it.signal).append("/5");
        return sb.toString();
    }

    private void pick(Item it) {
        if (wifi) {
            if (it.connected) { Ui.toast(c, Ui.t(c, "已经连接到 ") + it.name, false); return; }
            if (it.enterprise) { Ui.toast(c, "不支持企业网络", false); return; }
            if (it.secured && !it.known) {
                if ("1".equals(Ctl.get("WIFI_PSK_INPUT", "0"))) askPassword(it);
                else Ui.toast(c, "请先在 Kindle 的设置里连接一次这个网络, 或在设置的网络页允许输入 Wi-Fi 密码", true);
                return;
            }
            Ctl.put("wifi_join", it.id);
            Ui.toast(c, Ui.t(c, "正在连接 ") + it.name + "...", false);
            relistLater(6000);
            relistLater(14000);
        } else {
            Ctl.put(it.connected ? "bt_disconnect" : "bt_connect", it.id);
            Ui.toast(c, (it.connected ? Ui.t(c, "正在断开 ") : Ui.t(c, "正在连接 ")) + it.name + "...", false);
            relistLater(5000);
            relistLater(10000);
        }
    }
}