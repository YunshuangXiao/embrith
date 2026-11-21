/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * WallpaperActivity: Wallpaper and lock screen settings: desktop or lock screen, current choice, plain white, pick
 * from the Kindle storage, fade, folder change interval. */
package com.embrith.home;

import android.app.Activity;
import android.content.Context;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.graphics.Bitmap;
import android.os.Handler;
import android.widget.EditText;
import android.widget.ImageView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.widget.LinearLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * 壁纸与锁屏. 顶层页: 桌面/锁屏切换, 当前选择, 纯白, 从 Kindle 选择, 褪色, 文件夹切换间隔. 
 * 浏览页(extra dir): 从 Kindle 存储(/dev/kindle, 只读)任意文件夹开始, 逐级进入; 可选单张图片或"使用此文件夹(随机)". 
 * 每个文件夹是一个新页面, 首行 ‹ 返回上一级(同 iOS). 选中后留在当前页, 只移动 ✓. 
 */
public class WallpaperActivity extends Activity {
    static final String KINDLE = "/dev/kindle";
    static final String[] FADE = { "0", "20", "40", "60", "80" };
    static final String[] FADE_TEXT = { "不褪色", "20%", "40%", "60%", "80%（很淡）" };
    static final String[] EVERY = { "0", "1800", "3600", "86400" };
    static final String[] EVERY_TEXT = { "每次回到桌面", "每 30 分钟", "每小时", "每天" };

    private String target = "home";
    private ExecutorService thumbs;
    private final Handler ui = new Handler();
    private boolean gone;
    private File dir;                 // null = 顶层页
    private Ui.FlatButton homeBtn, lockBtn;
    private Ui.Row curRow, whiteRow, fadeRow, everyRow;
    private Ui.SwitchRow libRow;
    private final List<Ui.Row> rows = new ArrayList<Ui.Row>();       // 可选中的行（图片 / 此文件夹）
    private final List<String> paths = new ArrayList<String>();
    private final List<Ui.Row> dirRows = new ArrayList<Ui.Row>();    // 子文件夹（当前选择在其中时标"已选"）
    private final List<String> dirPaths = new ArrayList<String>();

    /** 给用户看的路径: /dev/kindle → Kindle, /dev/wallpapers → Kindle/embrith/wallpapers.  */
    static String shown(String p) {
        if (p.startsWith(KINDLE)) return "Kindle" + p.substring(KINDLE.length());
        if (p.startsWith(Prefs.DIR)) return "Kindle/embrith/wallpapers" + p.substring(Prefs.DIR.length());
        return p;
    }

    static String describe(Context c, String p) {
        if (p.length() == 0) return "纯白";
        File f = new File(p);
        return f.isDirectory() ? Ui.t(c, "文件夹 ") + f.getName() + Ui.t(c, "(随机)") : f.getName();
    }

    private String targetName() {
        return Ui.t(this, "home".equals(target) ? "桌面壁纸" : "锁屏图片");
    }

    private void browse(String path) {
        startActivity(new Intent(this, WallpaperActivity.class).putExtra("dir", path).putExtra("target", target));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        String d = getIntent().getStringExtra("dir");
        String t = getIntent().getStringExtra("target");
        if (t != null) target = t;
        if (d == null) buildTop(); else buildBrowser(new File(d));
    }

    private void buildTop() {
        LinearLayout body = Ui.page(this, "壁纸与锁屏", null);
        LinearLayout seg = Ui.addGroup(body, Ui.group(this, LinearLayout.HORIZONTAL));
        homeBtn = Ui.button(this, "桌面壁纸").noFrame();
        lockBtn = Ui.button(this, "锁屏图片").noFrame();
        seg.addView(homeBtn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        seg.addView(lockBtn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        homeBtn.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { target = "home"; update(); } });
        lockBtn.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { target = "lock"; update(); } });

        LinearLayout g = Ui.addGroup(body);
        curRow = Ui.nav(this, "当前", "", new View.OnClickListener() {
            @Override public void onClick(View v) {
                String p = Prefs.wall(WallpaperActivity.this, target);
                File f = new File(p);
                File at = p.length() == 0 ? null : f.isDirectory() ? f : f.getParentFile();
                browse(at != null && at.isDirectory() ? at.getAbsolutePath() : KINDLE);
            }
        });
        Ui.add(g, curRow);
        whiteRow = Ui.row(this, "纯白（无图片）", "", new View.OnClickListener() {
            @Override public void onClick(View v) { Prefs.setWall(WallpaperActivity.this, target, ""); update(); }
        });
        Ui.add(g, whiteRow);
        Ui.add(g, Ui.nav(this, "从 Kindle 选择", "", new View.OnClickListener() {
            @Override public void onClick(View v) { browse(KINDLE); }
        }));
        Ui.add(g, Ui.nav(this, "Embrith 壁纸文件夹", "", new View.OnClickListener() {
            @Override public void onClick(View v) { browse(Prefs.DIR); }
        }));
        Ui.note(this, body, "选择图片", "可选内容\t可以选 Kindle 存储里任意一张 png / jpg 图片, 或一个文件夹(随机切换).\n\n"
            + "选取方式\t进入文件夹后, 点\"使用此文件夹\"即按该文件夹随机, 点图片即用这一张. 图片会按屏幕裁切并转成灰度.");

        g = Ui.addGroup(body);
        fadeRow = Ui.nav(this, "褪色", "", new View.OnClickListener() {
            @Override public void onClick(View v) {
                askFade();
            }
        });
        everyRow = Ui.nav(this, "文件夹切换", "", new View.OnClickListener() {
            @Override public void onClick(View v) {
                ChoiceActivity.open(WallpaperActivity.this, "桌面壁纸切换间隔", EVERY, EVERY_TEXT, "pref", "wall_every", "3600",
                    "选了文件夹时，桌面壁纸按这个间隔随机换一张");
            }
        });
        Ui.add(g, fadeRow);
        Ui.add(g, everyRow);
        libRow = Ui.switchRow(this, "全局桌面应用", "1".equals(Prefs.getStr(this, "wall_lib", "0")), new Ui.OnToggle() {
            @Override public void toggled(boolean on) { Prefs.putStr(WallpaperActivity.this, "wall_lib", on ? "1" : "0"); }
        });
        Ui.add(g, libRow);
        Ui.note(this, body, "褪色与切换", "褪色\t褪色对桌面壁纸和锁屏图片分别设置.\n\n"
            + "选了文件夹时\t锁屏图片每次锁屏随机换一张; 桌面壁纸按\"文件夹切换\"的间隔随机换.\n\n"
            + "全局桌面应用\t应用于统计, 打卡, 概览, 主页, 阅读, 书库和设置首页.");
    }

    private void buildBrowser(File d) {
        dir = d;
        boolean root = d.getPath().equals(KINDLE);
        LinearLayout body = Ui.page(this, root ? "Kindle" : d.getName(), null);
        Ui.caption(body, shown(d.getPath()) + Ui.t(this, " · 为") + targetName() + Ui.t(this, "选择"));
        LinearLayout g = Ui.addGroup(body);
        File[] pics = d.isDirectory() ? Img.pics(d) : new File[0];
        if (pics.length > 0) addChoice(g, Ui.t(this, "使用此文件夹(随机,") + pics.length + Ui.t(this, " 张)"), d.getAbsolutePath());
        File[] all = d.listFiles();
        int shownN = 0;
        if (all != null) {
            Arrays.sort(all, new Comparator<File>() {
                @Override public int compare(File a, File b) { return a.getName().compareToIgnoreCase(b.getName()); }
            });
            for (final File f : all) {
                if ((f.getName().startsWith(".") && !Prefs.showHidden(this)) || f.getName().toLowerCase().endsWith(".sdr") || !f.isDirectory()) continue;
                Ui.Row r = Ui.nav(this, f.getName(), "", new View.OnClickListener() {
                    @Override public void onClick(View v) { browse(f.getAbsolutePath()); }
                });
                dirRows.add(r);
                dirPaths.add(f.getAbsolutePath());
                Ui.add(g, r);
                if (++shownN >= 300) break;
            }
        }
        for (File f : pics) {
            addChoice(g, f.getName(), f.getAbsolutePath());
            if (++shownN >= 600) break;
        }
        if (all == null) Ui.add(g, Ui.row(this, "这个文件夹打不开（或者 Kindle 的存储没有接入）", "", null));
        else if (shownN == 0 && pics.length == 0) Ui.add(g, Ui.row(this, "这里没有文件夹和图片", "", null));
    }

    @Override
    protected void onResume() {
        super.onResume();
        update();
    }

    private void addChoice(LinearLayout g, String label, final String path) {
        Ui.Row r = Ui.row(this, label, "", new View.OnClickListener() {
            @Override public void onClick(View v) {
                Prefs.setWall(WallpaperActivity.this, target, path);
                update();
            }
        });
        rows.add(r);
        paths.add(path);
        Ui.add(g, r);
        if (new File(path).isFile()) addThumb(r, path);
    }

    /** A small gray thumbnail at the start of a picture row; decoded one at a time off the UI thread (sampled, so memory stays small). */
    private void addThumb(final Ui.Row r, final String path) {
        final int px = Ui.dp(this, 48);
        final ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px, px);
        lp.rightMargin = Ui.dp(this, 12);
        r.addView(iv, 0, lp);
        if (thumbs == null) thumbs = Executors.newSingleThreadExecutor();
        thumbs.execute(new Runnable() {
            @Override public void run() {
                if (gone) return;
                final Bitmap b = Img.load(path, px, px);
                if (b == null) return;
                ui.post(new Runnable() {
                    @Override public void run() {
                        if (gone) b.recycle(); else iv.setImageBitmap(b);
                    }
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        gone = true;
        if (thumbs != null) thumbs.shutdownNow();
        super.onDestroy();
    }

    /** Preset fade values, or a typed number (0 to 90) with the number keyboard. */
    private void askFade() {
        String[] items = new String[FADE_TEXT.length + 1];
        System.arraycopy(FADE_TEXT, 0, items, 0, FADE_TEXT.length);
        items[FADE_TEXT.length] = "自定义...";
        new Ui.Builder(this).setTitle(targetName() + Ui.gap(this) + Ui.t(this, "褪色")).setItems(items, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                if (which < FADE.length) {
                    Prefs.putStr(WallpaperActivity.this, target + "_fade", FADE[which]);
                    update();
                } else {
                    askCustomFade();
                }
            }
        }).setNegativeButton("取消", null).show();
    }

    private void askCustomFade() {
        final EditText in = new EditText(this);
        in.setInputType(InputType.TYPE_CLASS_NUMBER);
        in.setHint(Ui.t(this, "0 到 90"));
        in.setText(Prefs.getStr(this, target + "_fade", "0"));
        in.setSelectAllOnFocus(true);
        new Ui.Builder(this).setTitle(targetName() + Ui.gap(this) + Ui.t(this, "褪色百分比 (0 到 90)")).setView(in)
            .setPositiveButton("好", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    int n = -1;
                    try { n = Integer.parseInt(in.getText().toString().trim()); } catch (NumberFormatException e) { n = -1; }
                    if (n < 0 || n > 90) {
                        Ui.toast(WallpaperActivity.this, "请输入 0 到 90 的整数", true);
                        return;
                    }
                    Prefs.putStr(WallpaperActivity.this, target + "_fade", String.valueOf(n));
                    update();
                }
            })
            .setNegativeButton("取消", null).show();
    }

    private void update() {
        String p = Prefs.wall(this, target);
        if (dir == null) {
            homeBtn.setInverted("home".equals(target));
            lockBtn.setInverted("lock".equals(target));
            curRow.setValue(describe(this, p));
            whiteRow.setValue(p.length() == 0 ? "✓" : "");
            int f = ChoiceActivity.indexOf(FADE, Prefs.getStr(this, target + "_fade", "0"));
            fadeRow.setValue(f >= 0 ? FADE_TEXT[f] : Prefs.getStr(this, target + "_fade", "0") + "%");
            int e = ChoiceActivity.indexOf(EVERY, Prefs.getStr(this, "wall_every", "3600"));
            everyRow.setValue(EVERY_TEXT[e < 0 ? 2 : e]);
            everyRow.setVisibility("home".equals(target) ? View.VISIBLE : View.GONE);
            libRow.setVisibility("home".equals(target) ? View.VISIBLE : View.GONE);
            return;
        }
        for (int i = 0; i < rows.size(); i++) rows.get(i).setValue(paths.get(i).equals(p) ? "✓" : "");
        for (int i = 0; i < dirRows.size(); i++) {
            String dp = dirPaths.get(i);
            dirRows.get(i).setValue(p.equals(dp) || p.startsWith(dp + "/") ? "已选" : "");
        }
    }
}
