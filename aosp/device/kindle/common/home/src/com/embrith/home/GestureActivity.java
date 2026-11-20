/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * GestureActivity: Settings page to choose what each gesture does; the choices go to ctl/conf (G_*), which kgest
 * on the Kindle side reads at once. */
package com.embrith.home;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;

/** Choose what each gesture does. The choices go to /dev/kdisp/ctl/conf (G_*), where kgest on the Kindle side reads them at once. */
public class GestureActivity extends Activity {
    private static final String[] ACTION_IDS = {
        "none", "clear", "invert", "back", "home", "light_toggle", "light_adjust", "warm_adjust", "wifi_toggle", "statusbar", "menu", "lock", "screenshot",
        "page_next", "page_prev", "light_max", "light_min", "warm_max", "warm_min", "app_switch" };
    private static final String[] ACTION_TEXT = {
        "无", "刷新屏幕(去残影)", "反色", "返回", "回到桌面", "前光开/关", "调节前光亮度", "调节色温", "Wi-Fi 开/关", "下拉状态栏", "打开设置", "休眠", "截图",
        "下一页", "上一页", "前光最亮", "前光关到最暗", "色温最暖", "色温最冷", "切换应用(最近任务)" };

    // the third-level pages: one per kind of finger movement
    private static final String[] CATS = { "双指点按", "双指滑动", "单指点按", "单指滑动" };

    // key, label, default, page (index in CATS), caption (a new group starts here when it is not null)
    private static final String[][] SLOTS = {
        { "G_TAP2", "双指轻点", "none", "0", null },
        { "G_HOLD2", "双指长按 2 秒", "none", "0", null },
        { "G_SWIPE2_H", "双指左右滑", "none", "1", null },
        { "G_SWIPE2_VL", "双指上下滑(左半屏)", "none", "1", null },
        { "G_SWIPE2_VR", "双指上下滑(右半屏)", "none", "1", null },
        { "G_SWIPE2_U", "双指上滑(整屏)", "none", "1", null },
        { "G_SWIPE2_D", "双指下滑(整屏)", "none", "1", null },
        { "G_SWIPE2_NE", "双指向右上滑", "none", "1", "斜向" },
        { "G_SWIPE2_NW", "双指向左上滑", "none", "1", null },
        { "G_SWIPE2_SE", "双指向右下滑", "none", "1", null },
        { "G_SWIPE2_SW", "双指向左下滑", "none", "1", null },
        { "G_PINCH_IN", "双指捏合", "none", "1", "缩放" },
        { "G_PINCH_OUT", "双指张开", "none", "1", null },
        { "G_CORNER_TR", "右上角长按 2 秒", "none", "2", "长按" },
        { "G_CORNER_BL", "左下角长按 2 秒", "none", "2", null },
        { "G_CORNER_BR", "右下角长按 2 秒", "none", "2", null },
        { "G_TAP_TL", "左上角轻点", "none", "2", "轻点" },
        { "G_TAP_TR", "右上角轻点", "none", "2", null },
        { "G_TAP_BL", "左下角轻点", "none", "2", null },
        { "G_TAP_BR", "右下角轻点", "none", "2", null },
        { "G_DTAP_TL", "左上角双击", "none", "2", "双击" },
        { "G_DTAP_TR", "右上角双击", "none", "2", null },
        { "G_DTAP_BL", "左下角双击", "none", "2", null },
        { "G_DTAP_BR", "右下角双击", "none", "2", null },
        { "G_EDGE_L_UP", "沿左边缘上滑", "none", "3", "左边缘" },
        { "G_EDGE_L_DOWN", "沿左边缘下滑", "none", "3", null },
        { "G_EDGE_R_UP", "沿右边缘上滑", "none", "3", "右边缘" },
        { "G_EDGE_R_DOWN", "沿右边缘下滑", "none", "3", null },
        { "G_EDGE_T_L", "顶边左半下滑", "none", "3", "顶边" },
    };
    private static final String[] CAT_NOTE = {
        null,
        "双指滑动\t双指上下滑(整屏)只在左右半屏的设置为无时使用.",
        "单指点按\t角落轻点和双击不拦截触摸, 应用同时也会收到. 一次双击会先触发一次轻点的动作.",
        "单指滑动\t设了动作后, 贴边起滑的触摸不再传给应用; 没设动作的边缘不受影响." };

    private Ui.SwitchRow enableRow;
    private Ui.Row[] slotRows;
    private String[][] shown;   // the slots of the page shown (third level)
    private int cat = -1;       // -1: the first page of the gestures

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        cat = getIntent().getIntExtra("cat", -1);
        if (cat >= 0 && cat < CATS.length) {
            buildCategory();
            return;
        }
        cat = -1;
        LinearLayout body = Ui.page(this, "手势", null);
        LinearLayout g = Ui.addGroup(body);
        enableRow = Ui.switchRow(this, "启用手势", true, new Ui.OnToggle() {
            @Override public void toggled(boolean on) { Ctl.set("GESTURES", on ? "1" : "0"); }
        });
        Ui.add(g, enableRow);
        Ui.note(this, body, "手势", "自定义手势\t只控制以下支持自定义的手势. 固定手势不受此开关影响, 始终有效.");

        Ui.caption(body, "自定义手势");
        g = Ui.addGroup(body);
        for (int i = 0; i < CATS.length; i++) {
            final int idx = i;
            Ui.add(g, Ui.nav(this, CATS[i], "", new View.OnClickListener() {
                @Override public void onClick(View v) {
                    startActivity(new Intent(GestureActivity.this, GestureActivity.class).putExtra("cat", idx));
                }
            }));
        }
        g = Ui.addGroup(body);
        Ui.add(g, Ui.row(this, "恢复默认手势", "", new View.OnClickListener() {
            @Override public void onClick(View v) {
                for (int i = 0; i < SLOTS.length; i++) Ctl.set(SLOTS[i][0], SLOTS[i][2]);
            }
        }));

        Ui.caption(body, "固定手势(不可改)");
        g = Ui.addGroup(body);
        Ui.add(g, Ui.row(this, "单指从顶边右半向下滑", "下拉面板", null));
        Ui.add(g, Ui.row(this, "单指从底边左半向上滑", "回到桌面", null));
        Ui.add(g, Ui.row(this, "单指从底边右半向上滑", "返回", null));
        Ui.add(g, Ui.row(this, "单指按住左上角 3 秒", "退出 Embrith", null));
        Ui.add(g, Ui.row(this, "同时按左上角和右下角", "截图", null));
        Ui.add(g, Ui.row(this, "同时按左下角和右上角", "截图", null));
    }

    /** The third-level page of one kind of finger movement: a row for each position, with the action chosen for it. */
    private void buildCategory() {
        LinearLayout body = Ui.page(this, CATS[cat], null);
        if (CAT_NOTE[cat] != null) Ui.note(this, body, CATS[cat], CAT_NOTE[cat]);
        java.util.ArrayList<Ui.Row> rows = new java.util.ArrayList<Ui.Row>();
        java.util.ArrayList<String[]> mine = new java.util.ArrayList<String[]>();
        LinearLayout g = null;
        for (int i = 0; i < SLOTS.length; i++) {
            if (!SLOTS[i][3].equals(String.valueOf(cat))) continue;
            if (g == null || SLOTS[i][4] != null) {
                if (SLOTS[i][4] != null) Ui.caption(body, SLOTS[i][4]);
                g = Ui.addGroup(body);
            }
            final String key = SLOTS[i][0];
            final String label = SLOTS[i][1];
            final String def = SLOTS[i][2];
            Ui.Row r = Ui.nav(this, label, "", new View.OnClickListener() {
                @Override public void onClick(View v) {
                    ChoiceActivity.open(GestureActivity.this, label, ACTION_IDS, ACTION_TEXT, "ctl", key, def, null);
                }
            });
            Ui.add(g, r);
            rows.add(r);
            mine.add(SLOTS[i]);
        }
        slotRows = rows.toArray(new Ui.Row[0]);
        shown = mine.toArray(new String[0][]);
    }

    @Override
    protected void onResume() {
        super.onResume();
        update();
    }

    private static String actionText(String id) {
        int i = ChoiceActivity.indexOf(ACTION_IDS, id);
        return i >= 0 ? ACTION_TEXT[i] : id;
    }

    private void update() {
        if (cat < 0) {
            enableRow.setOn("1".equals(Ctl.get("GESTURES", "1")));
            return;
        }
        for (int i = 0; i < shown.length; i++) slotRows[i].setValue(actionText(Ctl.get(shown[i][0], shown[i][2])));
    }
}
