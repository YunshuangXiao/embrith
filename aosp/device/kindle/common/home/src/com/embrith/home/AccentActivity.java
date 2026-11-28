/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * AccentActivity: The theme color (settings, display) and the theme color of the library (settings, library): a few preset colors
 * and a color value of your own. */
package com.embrith.home;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

/**
 * The accent color is kept in Prefs "accent" as "#RRGGBB" (default black) or "multi" (the multicolor mode: each switch and mark takes one color of the palette, see Prefs.accent). It colors the on state of the switches and the marks of
 * the selected items (Prefs.accent) and, in the dialogs, the button texts and the input fields (Ui.Builder); only while the color display is on.
 * Started with the extra "key" = "lib_accent" the page sets the theme color of the library instead (Prefs.libAccent): the first choice
 * "follow the theme color" is the empty value.
 */
public class AccentActivity extends Activity {
    private static final String DEF = "#000000";
    private String key = "accent";
    private String[] ids;   // (the library: follow the theme color,) multicolor, black, and the five colors of the palette last
    private Ui.Row[] rows;
    private Ui.Row custom;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        String extra = getIntent().getStringExtra("key");
        boolean cal = "cal_check_accent".equals(extra) || "cal_heat_accent".equals(extra);   // a calendar: no multicolor (its gradient is one color)
        boolean lib = cal || "lib_accent".equals(extra);
        if (lib) key = extra;
        ids = new String[(lib ? 8 : 7) - (cal ? 1 : 0)];
        String[] text = new String[ids.length];
        int o = 0;
        if (lib) { ids[o] = ""; text[o++] = cal ? "跟随书库主题色(默认)" : "跟随主题色(默认)"; }
        if (!cal) { ids[o] = Prefs.MULTI_ID; text[o++] = "多彩"; }
        ids[o] = DEF; text[o++] = "黑色(默认)";
        for (int k = 0; k < 5; k++) text[o + k] = "颜色" + (k + 1);
        refill();
        LinearLayout body = Ui.page(this, cal ? ("cal_check_accent".equals(extra) ? "打卡日历颜色" : "统计日历颜色") : lib ? "书库主题色" : "主题色", null);
        LinearLayout g = Ui.addGroup(body);
        rows = new Ui.Row[ids.length];
        for (int i = 0; i < ids.length; i++) {
            final int k = i;
            rows[i] = Ui.row(this, text[i], "", new View.OnClickListener() {
                @Override public void onClick(View v) { save(ids[k]); }
            });
            Ui.add(g, rows[i]);
        }
        custom = Ui.row(this, "自定义", "", new View.OnClickListener() {
            @Override public void onClick(View v) { ask(); }
        });
        Ui.add(g, custom);
        Ui.add(g, Ui.nav(this, "编辑色板", "", new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(AccentActivity.this, PaletteActivity.class)); }
        }));
        Ui.note(this, body, "", cal ? "日历的颜色, 默认跟随书库主题色. 只在开启彩色显示时生效. 自定义的色值格式是 #RRGGBB, 例如 #0050C8." : lib
            ? "书库主题色用于书库里封面的角标, 日历, 柱状图, 进度环和进度条. 只在开启彩色显示时生效. 自定义的色值格式是 #RRGGBB, 例如 #0050C8."
            : "主题色只在开启彩色显示时生效, 用于开关的开启状态, 选中项的标记, 以及弹窗里按钮的文字和输入框的下划线, 选中文字的底色. 自定义的色值格式是 #RRGGBB, 例如 #0050C8.");
        mark();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refill();
        if (rows != null) mark();
    }

    /** The palette rows hold the current palette colors (they can be edited on the next page). */
    private void refill() {
        for (int k = 0; k < 5; k++) ids[ids.length - 5 + k] = Prefs.paletteHex(this, k);
    }

    private void save(String hex) {
        Prefs.putStr(this, key, hex);
        mark();
    }

    private void mark() {
        String cur = Prefs.getStr(this, key, "accent".equals(key) ? DEF : "");
        int idx = ChoiceActivity.indexOf(ids, cur);
        String shown = cur.length() == 0 ? Prefs.getStr(this, "accent", DEF) : cur;
        int col = Prefs.MULTI_ID.equals(shown) ? Prefs.paletteColor(this, 0) : Prefs.parseColor(shown, Color.BLACK);
        for (int i = 0; i < rows.length; i++) {
            String tick = i == idx ? " ✓" : "";
            if (Prefs.MULTI_ID.equals(ids[i])) {                 // the preview: the five palette colors
                SpannableStringBuilder sb = new SpannableStringBuilder();
                for (int k = 0; k < 5; k++) {
                    int s = sb.length();
                    sb.append("■");
                    sb.setSpan(new ForegroundColorSpan(Prefs.paletteColor(this, k)), s, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    sb.setSpan(new RelativeSizeSpan(2f), s, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);   // a large preview square
                }
                sb.append(tick);
                rows[i].value.setText(sb);
                rows[i].value.setTextColor(col);
            } else if (ids[i].length() > 0) {                    // the preview: a square in the color of the row
                SpannableStringBuilder sq = new SpannableStringBuilder("■");
                sq.setSpan(new RelativeSizeSpan(2f), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);   // a large preview square
                sq.append(tick);
                rows[i].value.setText(sq);
                rows[i].value.setTextColor(Prefs.parseColor(ids[i], Color.BLACK));
            } else {
                rows[i].setValue(i == idx ? "✓" : "");
                rows[i].value.setTextColor(i == idx ? col : Color.BLACK);
            }
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
