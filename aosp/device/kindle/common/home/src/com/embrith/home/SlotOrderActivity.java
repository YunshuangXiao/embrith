/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * SlotOrderActivity: The one page of the widgets of an area: a preview of the whole area, how many of each size, and the type, the content and the order of each. */
package com.embrith.home;

import android.app.Activity;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * Settings > Personalize > Settings widgets (or Desktop widgets) > Widget settings: the preview of the whole area on top (SlotPlaceView), then,
 * for the chosen widget, the buttons type and content / back to the tray / remove, then the tray (chosen widgets that have no place), then one row
 * for each size with the buttons minus and plus (plus adds a widget to the tray and chooses it). To place a widget: choose it, tap a cell of the
 * preview, the widget's top left cell goes there (snapped to the grid; refused when it would cover another widget). Tap a placed widget to choose it
 * and a cell to move it. Everything is on one page, so the preview shows every change at once.
 * The Intent extra "set" says which set: sh = settings home, dh = desktop.
 */
public class SlotOrderActivity extends Activity {
    private LinearLayout body;
    private Slots set;
    private final Runnable again = new Runnable() {
        @Override public void run() { fill(); }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        set = Slots.of(getIntent().getStringExtra("set"));
        body = Ui.page(this, set == Slots.HOME ? "桌面组件" : "设置组件", null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        fill();
    }

    private String sel;   // the chosen widget (placed or not), null = none

    private void fill() {
        body.removeAllViews();
        final Slots.Metrics m = set.metrics(this);
        set.migrate(this, m);
        final List<String> ids = set.order(this);
        List<int[]> pos = set.place(this, ids, m);
        if (sel != null && !ids.contains(sel)) sel = null;
        Ui.caption(body, "上方是整个区域 (缩小). 在下方选一个组件, 再点预览里的格子, 组件的左上角放在点的格子上; 点预览里的组件可以选中它, 再点别的格子即移动. 没被覆盖的格子保持空白");
        SlotPlaceView pv = new SlotPlaceView(this, set, m, ids, pos, sel, new SlotPlaceView.OnCell() {
            @Override public void tap(int col, int row, String hit) { tapCell(m, col, row, hit); }
        }, again);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        plp.topMargin = Ui.dp(this, 14);
        plp.bottomMargin = Ui.dp(this, 8);
        body.addView(pv, plp);
        if (sel != null) actions(pos.get(ids.indexOf(sel)) != null);
        List<String> tray = set.tray(this, m);
        if (tray.isEmpty()) {
            Ui.caption(body, "没有待放组件. 在下方添加组件");
        } else {
            Ui.caption(body, "待放组件 (点一下选中, 再点预览里的格子)");
            LinearLayout tg = Ui.addGroup(body);
            for (String id : tray) Ui.add(tg, rowOf(id, m));
        }
        Ui.caption(body, "添加组件 (加进来先在待放区, 选中后点预览放置)");
        LinearLayout cg = Ui.addGroup(body);
        for (int sp = 0; sp < set.keys.length; sp++) Ui.add(cg, countRow(sp, m));
    }

    private void tapCell(Slots.Metrics m, int col, int row, String hit) {
        if (sel != null) {
            if (sel.equals(hit)) {
                sel = null;
            } else {
                int[] n = set.cells(m, set.specOf(sel));
                int c = Math.max(0, Math.min(col, m.cols - n[0])), r = Math.max(0, Math.min(row, m.rows - n[1]));
                if (set.put(this, m, sel, c, r)) sel = null;
                else if (hit != null) sel = hit;
                else Ui.toast(this, "放不下: 和其他组件重叠", false);
            }
        } else if (hit != null) {
            sel = hit;
        }
        fill();
    }

    /** The buttons for the chosen widget: type and content, take it off the grid (only when it is placed), remove it. */
    private void actions(boolean placed) {
        final String id = sel;
        LinearLayout seg = Ui.addGroup(body, Ui.group(this, LinearLayout.HORIZONTAL));
        Ui.FlatButton type = Ui.button(this, "类型和内容").noFrame();
        type.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pick(id); }
        });
        seg.addView(type, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if (placed) {
            Ui.FlatButton off = Ui.button(this, "放回待放区").noFrame();
            off.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    set.unplace(SlotOrderActivity.this, id);
                    fill();
                }
            });
            seg.addView(off, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        Ui.FlatButton del = Ui.button(this, "删除").noFrame();
        del.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                set.remove(SlotOrderActivity.this, id);
                sel = null;
                fill();
            }
        });
        seg.addView(del, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    }

    private View countRow(final int spec, final Slots.Metrics m) {
        int n = set.count(set.order(this), spec);
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(Ui.dp(this, 64));
        r.setPadding(Ui.dp(this, 18), Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10));
        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.addView(Ui.text(this, set.names[spec], 18, false));
        mid.addView(Ui.text(this, set.descs[spec], 14, false));
        r.addView(mid, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        IconButton minus = new IconButton(this, Icons.MINUS, "", false, 24, 12);
        IconButton plus = new IconButton(this, Icons.PLUS, "", false, 24, 12);
        minus.setEnabled(n > 0);
        TextView val = Ui.text(this, String.valueOf(n), 18, true);
        val.setGravity(Gravity.CENTER);
        minus.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String id = set.lastOf(SlotOrderActivity.this, m, spec);
                if (id != null) set.remove(SlotOrderActivity.this, id);
                if (id != null && id.equals(sel)) sel = null;
                fill();
            }
        });
        plus.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String id = set.add(SlotOrderActivity.this, spec);
                if (id == null) Ui.toast(SlotOrderActivity.this, "组件太多了", false);
                else sel = id;
                fill();
            }
        });
        r.addView(minus, new LinearLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 52)));
        r.addView(val, new LinearLayout.LayoutParams(Ui.dp(this, 56), LinearLayout.LayoutParams.WRAP_CONTENT));
        r.addView(plus, new LinearLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 52)));
        return r;
    }

    /** One chosen widget that is not placed: a reduced picture of it, its size and content; a check mark at the end when it is the chosen one. */
    private View rowOf(final String id, final Slots.Metrics m) {
        int spec = set.specOf(id);
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
        r.setClickable(true);
        r.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                sel = id.equals(sel) ? null : id;
                fill();
            }
        });
        int[] sz = set.size(m, spec);
        View slot = SlotViews.build(this, set, id, sz[0], sz[1], false, again, null);
        float s = Math.min(Ui.dp(this, 80) / (float) sz[0], Ui.dp(this, 50) / (float) sz[1]);
        r.addView(SlotArea.scaled(this, slot, sz[0], sz[1], s));
        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 6), 0);
        mid.addView(Ui.text(this, Ui.t(this, set.names[spec]), 17, true));
        mid.addView(Ui.text(this, SlotViews.summary(this, set, id), 14, false));
        r.addView(mid, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if (id.equals(sel)) r.addView(Ui.text(this, "\u2713", 20, true));
        return r;
    }

    /**
     * The type of the widget (and, below the types, its content settings). The dialog stays open when a type is chosen: the current type has a check
     * mark at the end of its row and the entry "set content" follows the type at once (it is there only for a type that has something to set up). Choose "Done" or press back
     * to close it. It is as wide as most of the screen, so that the rows are easy to hit.
     */
    private void pick(final String id) {
        final String[] types = set.types(set.specOf(id));
        final int[] cur = { -1 };   // the row of the current type
        final android.widget.ArrayAdapter<String> ad = new android.widget.ArrayAdapter<String>(this, android.R.layout.select_dialog_item) {
            @Override public View getView(int pos, View conv, android.view.ViewGroup parent) {
                LinearLayout row = new LinearLayout(SlotOrderActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setMinimumHeight(Ui.dp(SlotOrderActivity.this, 56));
                row.setPadding(Ui.dp(SlotOrderActivity.this, 20), 0, Ui.dp(SlotOrderActivity.this, 20), 0);
                row.addView(Ui.text(SlotOrderActivity.this, getItem(pos), 18, false), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                if (pos == cur[0]) row.addView(Ui.text(SlotOrderActivity.this, "\u2713", 20, true));
                return row;
            }
        };
        final boolean[] more = new boolean[1];
        final Runnable list = new Runnable() {
            @Override public void run() {
                String now = set.type(SlotOrderActivity.this, id);
                ad.clear();
                cur[0] = -1;
                for (int i = 0; i < types.length; i++) {
                    if (types[i].equals(now)) cur[0] = i;
                    ad.add(Ui.t(SlotOrderActivity.this, Slots.typeName(types[i])));
                }
                more[0] = SlotViews.hasConfig(now);
                if (more[0]) ad.add(Ui.t(SlotOrderActivity.this, "设置内容"));
                ad.notifyDataSetChanged();
            }
        };
        list.run();
        final android.app.AlertDialog dlg = new Ui.Builder(this).setTitle("组件类型").setAdapter(ad, null).setNegativeButton("完成", null).create();
        dlg.show();
        dlg.getListView().setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override public void onItemClick(android.widget.AdapterView<?> p, View v, int which, long row) {
                if (which < types.length) {
                    set.setType(SlotOrderActivity.this, id, types[which]);
                    list.run();
                    fill();
                } else {
                    SlotViews.config(SlotOrderActivity.this, set, id, new Runnable() {
                        @Override public void run() { list.run(); fill(); }
                    });
                }
            }
        });
        android.view.Window w = dlg.getWindow();
        if (w != null) w.setLayout(getResources().getDisplayMetrics().widthPixels * 85 / 100, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
