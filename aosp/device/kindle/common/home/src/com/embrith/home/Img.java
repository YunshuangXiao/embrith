/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * Img: Loads a picture for the e-ink screen: scaled to fill, centre cropped and turned to gray, with optional
 * fade; a folder gives a random picture. */
package com.embrith.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Loads a picture for the e-ink screen: scaled to fill w x h (centre crop) and turned into gray.
 * 壁纸/锁屏可以是一张图片或一个文件夹(随机取一张); fade 为褪色百分比(向白色混合). 
 */
final class Img {
    private Img() { }

    static boolean isPic(String name) {
        String n = name.toLowerCase();
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".webp") || n.endsWith(".bmp");
    }

    /** 文件夹里的图片(不含子文件夹), 按名字排序.  */
    static File[] pics(File dir) {
        File[] all = dir.listFiles();
        List<File> out = new ArrayList<File>();
        if (all != null) for (File f : all) if (f.isFile() && !f.getName().startsWith(".") && isPic(f.getName())) out.add(f);
        File[] r = out.toArray(new File[out.size()]);
        Arrays.sort(r);
        return r;
    }

    /**
     * 这次要显示的图片路径("" = 纯白). 设的是文件夹时随机取一张: 锁屏每次都换; 桌面按 wall_every(秒, 0 = 每次回到桌面)换, 
     * 上次的选择存在 <which>_pick / <which>_pick_t. 
     */
    static String resolve(Context c, String which) {
        String p = Prefs.wall(c, which);
        if (p.length() == 0) return "";
        File f = new File(p);
        if (!f.isDirectory()) return p;
        File[] l = pics(f);
        if (l.length == 0) return "";
        String last = Prefs.getStr(c, which + "_pick", "");
        long t = 0, every = "lock".equals(which) ? 0 : 3600;
        try { t = Long.parseLong(Prefs.getStr(c, which + "_pick_t", "0")); } catch (NumberFormatException e) { t = 0; }
        if (!"lock".equals(which)) {
            try { every = Long.parseLong(Prefs.getStr(c, "wall_every", "3600")); } catch (NumberFormatException e) { every = 3600; }
        }
        long now = System.currentTimeMillis();
        if (every > 0 && last.startsWith(p + "/") && new File(last).isFile() && now - t >= 0 && now - t < every * 1000) return last;
        String pick = l[new Random().nextInt(l.length)].getAbsolutePath();
        if (l.length > 1) while (pick.equals(last)) pick = l[new Random().nextInt(l.length)].getAbsolutePath();
        Prefs.putStr(c, which + "_pick", pick);
        Prefs.putStr(c, which + "_pick_t", String.valueOf(now));
        return pick;
    }

    static int fade(Context c, String which) {
        try { return Math.max(0, Math.min(90, Integer.parseInt(Prefs.getStr(c, which + "_fade", "0")))); } catch (NumberFormatException e) { return 0; }
    }

    static Bitmap load(String path, int w, int h) {
        return load(path, w, h, 0);
    }

    static Bitmap load(String path, int w, int h, int fade) {
        if (path == null || path.length() == 0) return null;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, o);
            if (o.outWidth <= 0 || o.outHeight <= 0) return null;
            int s = 1;
            while (o.outWidth / (s * 2) >= w && o.outHeight / (s * 2) >= h) s *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = s;
            Bitmap src = BitmapFactory.decodeFile(path, o);
            if (src == null) return null;
            Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);   // opaque, the panel has 16 gray levels
            Canvas c = new Canvas(out);
            c.drawColor(Color.WHITE);
            float scale = Math.max((float) w / src.getWidth(), (float) h / src.getHeight());
            float dw = src.getWidth() * scale;
            float dh = src.getHeight() * scale;
            Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);   // no gray filter: colors stay, the HWC turns the frame into gray when the panel needs it
            c.drawBitmap(src, null, new RectF((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2), p);
            src.recycle();
            if (fade > 0) c.drawColor(Color.argb(fade * 255 / 100, 255, 255, 255));   // 褪色：盖一层半透明白
            return out;
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    /**
     * An app icon in pure black and white: transparent parts stay transparent. A pixel stays white only when all three channels are
     * high (white and near white); any colour or dark pixel turns black, so a green part does not come out as mid gray on the panel.
     */
    static Bitmap blackWhite(Drawable d, int px) {
        Bitmap b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        d.setBounds(0, 0, px, px);
        d.draw(new Canvas(b));
        int[] a = new int[px * px];
        b.getPixels(a, 0, px, 0, 0, px, px);
        for (int i = 0; i < a.length; i++) {
            int p = a[i];
            if (Color.alpha(p) < 128) { a[i] = Color.TRANSPARENT; continue; }
            int low = Math.min(Color.red(p), Math.min(Color.green(p), Color.blue(p)));
            a[i] = low >= 170 ? Color.WHITE : Color.BLACK;
        }
        b.setPixels(a, 0, px, 0, 0, px, px);
        return b;
    }
}
