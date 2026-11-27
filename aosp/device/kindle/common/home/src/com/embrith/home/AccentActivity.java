/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * AccentActivity: The theme color (settings, display) and the theme color of the library (settings, library): a few preset colors
 * and a color value of your own. */
package com.embrith.home;

import android.app.Activity;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

/**
 * The accent color is kept in Prefs "accent" as "#RRGGBB" (default black). It colors the on state of the switches and the marks of
 * the selected items (Prefs.accent) and, in the dialogs, the button texts and the input fields (Ui.Builder); only while the color display is on.
 * Started with the extra "key" = "lib_accent" the page sets the theme color of the library instead (Prefs.libAccent): the first choice
 * "follow the theme color" is the empty value.
 */
public class AccentActivity extends Activity {
    private static final String DEF = "#000000";
    private static final String[] IDS = { "#000000", "#C00000", "#E06000", "#008000", "#0050C8", "#7000B0" };
    private static final String[] TEXT = { "黑色(默认)", "红色", "橙色", "绿色", "蓝色", "紫色" };
    private String key = "accent";
    private String[] ids = IDS;
    private Ui.Row[] rows;
    private Ui.Row custom;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        boolean lib = "lib_accent".equals(getIntent().getStringExtra("key"));
        String[] text = TEXT;
        if (lib) {
            key = "lib_accent";
            ids = new String[IDS.length + 1];
            text = new String[TEXT.length + 1];
            ids[0] = "";
            text[0] = "跟随主题色(默认)";
            System.arraycopy(IDS, 0, ids, 1, IDS.length);
            System.arraycopy(TEXT, 0, text, 1, TEXT.length);
        }
        LinearLayout body = Ui.page(this, lib ? "书库主题色" : "主题色", null);
        LinearLayout g = Ui.addGroup(body);
        rows = new Ui.Row[ids.length];
        for (int i = 0; i < ids.length; i++) {
            final String id = ids[i];
            rows[i] = Ui.row(this, text[i], "", new View.OnClickListener() {
                @Override public void onClick(View v) { save(id); }
            });
            Ui.add(g, rows[i]);
        }
        custom = Ui.row(this, "自定义", "", new View.OnClickListener() {
            @Override public void onClick(View v) { ask(); }
        });
        Ui.add(g, custom);
        Ui.note(this, body, "", lib
            ? "书库主题色用于书库里封面的角标, 日历, 柱状图, 进度环和进度条. 只在开启彩色显示时生效. 自定义的色值格式是 #RRGGBB, 例如 #0050C8."
            : "主题色只在开启彩色显示时生效, 用于开关的开启状态, 选中项的标记, 以及弹窗里按钮的文字和输入框的下划线, 选中文字的底色. 自定义的色值格式是 #RRGGBB, 例如 #0050C8.");
        mark();
    }

    private void save(String hex) {
        Prefs.putStr(this, key, hex);
        mark();
    }

    private void mark() {
        String cur = Prefs.getStr(this, key, "accent".equals(key) ? DEF : "");
        int idx = ChoiceActivity.indexOf(ids, cur);
        int col = Prefs.parseColor(cur.length() == 0 ? Prefs.getStr(this, "accent", DEF) : cur, Color.BLACK);
        for (int i = 0; i < rows.length; i++) {
            rows[i].setValue(i == idx ? "✓" : "");
            rows[i].value.setTextColor(i == idx ? col : Color.BLACK);
        }
        custom.setValue(idx < 0 ? cur + " ✓" : "");
        custom.value.setTextColor(idx < 0 ? col : Color.BLACK);
    }

    private void ask() {
        final EditText in = new EditText(this);
        in.setInputType(InputType.TYPE_CLASS_TEXT);
        in.setHint(Ui.t(this, "#RRGGBB"));
        String cur = Prefs.getStr(this, key, "accent".equals(key) ? DEF : "");
        if (ChoiceActivity.indexOf(ids, cur) < 0) in.setText(cur);
        new Ui.Builder(this).setTitle("自定义主题色").setView(in)
            .setPositiveButton("好", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    String v = in.getText().toString().trim();
                    if (v.matches("#?[0-9A-Fa-f]{6}")) save("#" + v.replace("#", "").toUpperCase());
                    else Ui.toast(AccentActivity.this, "请输入 #RRGGBB 格式的色值", true);
                }
            })
            .setNegativeButton("取消", null).show();
    }
}
