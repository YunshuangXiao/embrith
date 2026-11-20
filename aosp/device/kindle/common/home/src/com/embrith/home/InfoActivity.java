/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * InfoActivity: Shows the full text of a note that Ui.note folded into a short form. */
package com.embrith.home;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 说明全文(由 Ui.note 折叠后的"详情 ›"进入).  */
public class InfoActivity extends Activity {
    static void open(Activity a, String title, String text) {
        a.startActivity(new Intent(a, InfoActivity.class).putExtra("title", title).putExtra("text", text));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout body = Ui.page(this, getIntent().getStringExtra("title"), null);
        LinearLayout g = Ui.addGroup(body);
        TextView t = Ui.text(this, getIntent().getStringExtra("text"), 17, false);
        t.setLineSpacing(Ui.dp(this, 4), 1f);
        t.setPadding(Ui.dp(this, 18), Ui.dp(this, 16), Ui.dp(this, 18), Ui.dp(this, 16));
        g.addView(t);
    }
}
