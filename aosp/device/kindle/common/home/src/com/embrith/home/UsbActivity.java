/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * UsbActivity: Full-screen USB connection page shown instead of the Kindle's own page, with an eject button. */
package com.embrith.home;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * USB 连接页: 电脑连上 Kindle 时全屏显示, 代替 Kindle 自带的 USB 页面.
 * 布局: 顶部横向 logo (res/drawable/embrith_logo.png), 中间 MTP 说明和警告, 底部"弹出连接".
 * 由 Kindle 侧打开和关闭: ctl/usb (StatusService 收到后打开), ctl/usb_off (拔线后关闭).
 * 显示期间电脑可以读写 Kindle 上的文件; 点"弹出连接"写 ctl/usb_eject, Kindle 侧断开数据连接, 之后只充电.
 * 返回键, 状态栏, 菜单和回到桌面都不能离开本页 (HomeActivity.onResume 会重新打开).
 */
public class UsbActivity extends Activity {
    /** 页面应当显示: ctl/usb 置位, 弹出或 ctl/usb_off 清除. */
    static boolean active;
    private static UsbActivity current;

    /** ctl/usb_off: 关闭本页. */
    static void close() {
        active = false;
        if (current != null) current.finish();
    }

    private TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.BLACK);
        t.setTextSize(sp);
        t.setGravity(Gravity.CENTER);
        t.getPaint().setFakeBoldText(bold);
        t.setLineSpacing(0, 1.3f);
        return t;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        current = this;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Color.WHITE);
        int pad = Ui.dp(this, 40);
        root.setPadding(pad, Ui.dp(this, 72), pad, Ui.dp(this, 72));

        // top: the horizontal logo
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.embrith_logo);
        logo.setAdjustViewBounds(true);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(logo, new LinearLayout.LayoutParams(Ui.dp(this, 320), LinearLayout.LayoutParams.WRAP_CONTENT));

        // middle: what MTP does, and the warning
        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setGravity(Gravity.CENTER);
        mid.addView(text(Ui.t(this, "USB 已连接"), 26, true));
        TextView info = text(Ui.t(this, "电脑正通过 MTP 访问 Kindle 的存储, 现在可以传输文件.\n"
                + "Embrith 仍在运行. 传输完成后点\"弹出连接\", 或直接拔线."), 18, false);
        info.setPadding(0, Ui.dp(this, 24), 0, 0);
        mid.addView(info);
        LinearLayout warn = new LinearLayout(this);
        warn.setOrientation(LinearLayout.VERTICAL);
        warn.setBackground(Ui.outline(this));
        int wp = Ui.dp(this, 20);
        warn.setPadding(wp, wp, wp, wp);
        warn.addView(text(Ui.t(this, "警告"), 20, true));
        TextView wt = text(Ui.t(this, "不要修改, 移动或删除 system 文件夹里的任何内容.\n"
                + "Embrith 的系统镜像和数据在其中, 运行期间改动会损坏数据."), 18, false);
        wt.setPadding(0, Ui.dp(this, 8), 0, 0);
        warn.addView(wt);
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        wl.topMargin = Ui.dp(this, 40);
        mid.addView(warn, wl);
        root.addView(mid, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // bottom: the only way out besides pulling the cable
        Ui.FlatButton eject = Ui.button(this, Ui.t(this, "弹出连接"));
        eject.setInverted(true);
        eject.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Ctl.request("usb_eject");
                close();
            }
        });
        root.addView(eject, new LinearLayout.LayoutParams(Ui.dp(this, 240), Ui.dp(this, 64)));
        setContentView(root);
    }

    @Override
    public void onBackPressed() {
        // 只能用"弹出连接"或拔线离开
    }

    @Override
    protected void onDestroy() {
        if (current == this) current = null;
        super.onDestroy();
    }
}
