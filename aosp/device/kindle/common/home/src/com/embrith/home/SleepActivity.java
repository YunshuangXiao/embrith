/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * SleepActivity: Sleep screen that shows only the chosen sleep picture; opened by the Kindle side, writes
 * ctl/sleep_ready once it is drawn. */
package com.embrith.home;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * 休眠画面: 只有选定的休眠图片(壁纸与锁屏 › 锁屏图片), 没有时间, 日期和提示. 
 * 由 Kindle 侧打开: 电源键 / 磁吸保护套 / 菜单"休眠" / 熄屏时间到 → powerd 进入屏保 → embrith.sh 创建 ctl/sleeping. 
 * 画好后写 ctl/sleep_ready, embrith.sh 随后冻结安卓, 由 powerd 让设备休眠; 唤醒时 ctl/wake 关掉本页. 
 */
public class SleepActivity extends Activity {
    private static SleepActivity current;
    private final Handler handler = new Handler();

    /** ctl/wake: 关闭休眠画面.  */
    static void close() {
        if (current != null) current.finish();
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        current = this;
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.WHITE);
        String path = Img.resolve(this, "lock");
        DisplayMetrics dm = getResources().getDisplayMetrics();
        Bitmap b = path.length() == 0 ? null : Img.load(path, dm.widthPixels, dm.heightPixels, Img.fade(this, "lock"));
        if (b != null) {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.FIT_XY);
            iv.setImageBitmap(b);
            frame.addView(iv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        setContentView(frame);
    }

    @Override
    public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (!focus) return;
        // 第一帧已提交给 SurfaceFlinger; 稍等一下让它到达 kdispd, 再告诉 Kindle 侧可以冻结
        handler.postDelayed(new Runnable() { @Override public void run() { Ctl.put("sleep_ready", ""); } }, 400);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (current == this) current = null;
        super.onDestroy();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        return true;                 // 只能用电源键 / 打开保护套唤醒
    }

    @Override
    public void onBackPressed() {
    }
}
