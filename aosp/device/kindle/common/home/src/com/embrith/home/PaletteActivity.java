/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * PaletteActivity: the five colors of the palette (color 1 to 5), each a color value of your own with a preview on the right.
 * The multicolor theme color takes its colors from this palette. */
package com.embrith.home;

import android.app.Activity;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

public class PaletteActivity extends Activity {
    private final Ui.Row[] rows = new Ui.Row[5];

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout body = Ui.page(this, "色板", null);
        LinearLayout g = Ui.addGroup(body);
        for (int i = 0; i < rows.length; i++) {
            final int k = i;
            rows[i] = Ui.row(this, "颜色" + (i + 1), "", new View.OnClickListener() {
                @Override public void onClick(View v) { ask(k); }
            });
            Ui.add(g, rows[i]);
        }
        Ui.add(g, Ui.row(this, "恢复默认", "", new View.OnClickListener() {
            @Override public void onClick(View v) { reset(); }
        }));
        Ui.note(this, body, "", "色板里的颜色用于主题色的多彩模式, 也可以直接选作主题色. 色值格式是 #RRGGBB, 例如 #0050C8.");
        show();
    }

    private void show() {
        for (int i = 0; i < rows.length; i++) {
            SpannableStringBuilder sq = new SpannableStringBuilder("■");
            sq.setSpan(new RelativeSizeSpan(2f), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);   // a large preview square
            sq.append(" " + Prefs.paletteHex(this, i));
            rows[i].value.setText(sq);
            rows[i].value.setTextColor(Prefs.paletteColor(this, i));
        }
    }

    private void reset() {
        for (int i = 0; i < rows.length; i++) Prefs.putStr(this, "palette" + (i + 1), Prefs.PALETTE_DEF[i]);
        show();
    }

    private void ask(final int k) {
        final EditText in = new EditText(this);
        in.setInputType(InputType.TYPE_CLASS_TEXT);
        in.setHint(Ui.t(this, "#RRGGBB"));
        in.setText(Prefs.paletteHex(this, k));
        new Ui.Builder(this).setTitle("颜色" + (k + 1)).setView(in)
            .setPositiveButton("好", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    String v = in.getText().toString().trim();
                    if (v.matches("#?[0-9A-Fa-f]{6}")) {
                        Prefs.putStr(PaletteActivity.this, "palette" + (k + 1), "#" + v.replace("#", "").toUpperCase());
                        show();
                    } else Ui.toast(PaletteActivity.this, "请输入 #RRGGBB 格式的色值", true);
                }
            })
            .setNegativeButton("取消", null).show();
    }
}