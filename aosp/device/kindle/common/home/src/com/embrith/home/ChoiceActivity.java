/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * ChoiceActivity: Generic single-choice settings page: marks the current item and saves the choice at once (Prefs,
 * ctl/conf, a ctl request or the system time zone). */
package com.embrith.home;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;

/**
 * 通用多选一页面(三级菜单): 当前项打 ✓, 点其他项只移动 ✓, 立即保存, 不自动返回. 
 * 值存到 Prefs(store=pref), ctl/conf(store=ctl), 或存 Prefs 并把值写成 ctl/<key> 请求交给 Kindle 侧(store=preq), 
 * 或存 Prefs 并设为系统时区(store=tz). 
 */
public class ChoiceActivity extends Activity {
    private static final String X_TITLE = "title", X_IDS = "ids", X_TEXT = "text", X_STORE = "store", X_KEY = "key", X_DEF = "def", X_NOTE = "note";

    private String[] ids;
    private Ui.Row[] rows;
    private String store, key, def;

    static void open(Activity a, String title, String[] ids, String[] text, String store, String key, String def, String note) {
        Intent i = new Intent(a, ChoiceActivity.class);
        i.putExtra(X_TITLE, title).putExtra(X_IDS, ids).putExtra(X_TEXT, text)
            .putExtra(X_STORE, store).putExtra(X_KEY, key).putExtra(X_DEF, def).putExtra(X_NOTE, note);
        a.startActivity(i);
    }

    /** ids 里与 cur 相同(不分大小写)的下标, 找不到返回 -1.  */
    static int indexOf(String[] ids, String cur) {
        for (int i = 0; i < ids.length; i++) if (ids[i].equalsIgnoreCase(cur)) return i;
        return -1;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Intent in = getIntent();
        ids = in.getStringArrayExtra(X_IDS);
        String[] text = in.getStringArrayExtra(X_TEXT);
        store = in.getStringExtra(X_STORE);
        key = in.getStringExtra(X_KEY);
        def = in.getStringExtra(X_DEF);
        LinearLayout body = Ui.page(this, in.getStringExtra(X_TITLE), null);
        if (ids == null || text == null || key == null) return;
        LinearLayout g = Ui.addGroup(body);
        rows = new Ui.Row[ids.length];
        for (int i = 0; i < ids.length; i++) {
            final String id = ids[i];
            rows[i] = Ui.row(this, text[i], "", new View.OnClickListener() {
                @Override public void onClick(View v) { save(id); mark(); }
            });
            Ui.add(g, rows[i]);
        }
        String note = in.getStringExtra(X_NOTE);
        if (note != null && note.length() > 0) Ui.note(this, body, "", note);
        mark();
    }

    private String current() {
        return "ctl".equals(store) ? Ctl.get(key, def) : Prefs.getStr(this, key, def);
    }

    private void save(String id) {
        if ("tz".equals(store)) {
            try {
                ((android.app.AlarmManager) getSystemService(ALARM_SERVICE)).setTimeZone(id);
            } catch (SecurityException e) {
                Ui.toast(this, "无法更改时区", false);
                return;   // not allowed: keep the old choice
            }
        }
        if ("ctl".equals(store)) Ctl.set(key, id);
        else Prefs.putStr(this, key, id);
        if ("preq".equals(store)) Ctl.put(key, id);
    }

    private void mark() {
        if (rows == null) return;
        int cur = indexOf(ids, current());
        for (int i = 0; i < rows.length; i++) rows[i].setValue(i == cur ? "✓" : "");
    }
}
