/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * ShareFoldersActivity: Chooses the Kindle folders that are shared read-only with Android; the choice is kept in
 * ctl/conf as KINDLE_SHARED_SEL. */
package com.embrith.home;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Chooses the Kindle folders that are shared with Android (read-only, under /data/media/0/Kindle). The Kindle's user storage is seen as
 * /dev/kindle; the choice is kept in ctl/conf as KINDLE_SHARED_SEL (":"-separated, relative to /mnt/us) and built by the host at the next start.
 */
public class ShareFoldersActivity extends Activity {
    private static final String ROOT = "/dev/kindle";
    private static final String KEY = "KINDLE_SHARED_SEL";
    private LinearLayout top, list;
    private TextView where;
    private File dir = new File(ROOT);

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout body = Ui.page(this, "共享文件夹", new View.OnClickListener() {
            @Override public void onClick(View v) { up(); }
        });
        where = Ui.caption(body, "");
        top = Ui.addGroup(body);
        list = Ui.addGroup(body);
        Ui.note(this, body, "共享文件夹", "共享方式\t把选中的 Kindle 文件夹以只读方式共享给安卓. 安卓里的路径是 /data/media/0/Kindle/ 加上它在 Kindle 上的位置, 例如 documents/weread.\n\n"
            + "生效时间\t改动在下次启动或重启 Embrith 后生效.\n\n"
            + "注意事项\t共享是只读的, 安卓应用不能修改或删除里面的文件; 选中上级文件夹后, 它下面的子文件夹不用再选; "
            + "不能共享整个存储, system 和 embrith 文件夹不会出现在这里; 共享出去的文件夹, 有存储权限的安卓应用都能读取, 隐私文件请不要共享.");
    }

    @Override
    protected void onResume() {
        super.onResume();
        show();
    }

    @Override
    public void onBackPressed() {
        up();
    }

    private void up() {
        if (dir.getPath().equals(ROOT) || dir.getParentFile() == null) finish();
        else {
            dir = dir.getParentFile();
            show();
        }
    }

    private static List<String> selection() {
        List<String> r = new ArrayList<String>();
        String s = Ctl.get(KEY, "");
        for (String p : s.split(":")) if (p.length() > 0) r.add(p);
        return r;
    }

    private static void save(List<String> sel) {
        StringBuilder b = new StringBuilder();
        for (String p : sel) {
            if (b.length() > 0) b.append(':');
            b.append(p);
        }
        Ctl.set(KEY, b.toString());
    }

    /** The selected folder that contains rel (or is rel itself), or null. */
    private static String covering(List<String> sel, String rel) {
        for (String p : sel) if (rel.equals(p) || rel.startsWith(p + "/")) return p;
        return null;
    }

    private static boolean hasBelow(List<String> sel, String rel) {
        for (String p : sel) if (p.startsWith(rel + "/")) return true;
        return false;
    }

    private static String rel(File f) {
        String p = f.getPath();
        return p.length() <= ROOT.length() ? "" : p.substring(ROOT.length() + 1);
    }

    /** Names the host would refuse (or that cannot be stored in the ":"-separated list), and the ".sdr" folders KOReader keeps next to books. */
    private boolean skip(File f) {
        String n = f.getName();
        if (n.startsWith(".") || n.indexOf(':') >= 0 || n.toLowerCase().endsWith(".sdr")) return true;
        if (dir.getPath().equals(ROOT)) {
            String l = n.toLowerCase();
            if (l.equals("system") || l.equals("embrith")) return true;
        }
        return false;
    }

    private void show() {
        top.removeAllViews();
        list.removeAllViews();
        where.setText(dir.getPath().replaceFirst("^/dev/kindle", "Kindle"));
        final List<String> sel = selection();
        final String here = rel(dir);
        if (here.length() == 0) {
            Ui.add(top, Ui.row(this, "不能共享整个存储", "请进入文件夹后选择", null));
        } else {
            String cov = covering(sel, here);
            if (cov != null && !cov.equals(here)) {
                Ui.add(top, Ui.row(this, "已随上级文件夹共享", cov, null));
            } else {
                Ui.add(top, Ui.switchRow(this, "共享这个文件夹", cov != null, new Ui.OnToggle() {
                    @Override public void toggled(boolean on) {
                        List<String> s = selection();
                        List<String> out = new ArrayList<String>();
                        for (String p : s) if (!p.equals(here) && !p.startsWith(here + "/")) out.add(p);
                        if (on) out.add(here);
                        save(out);
                    }
                }));
            }
        }
        File[] files = dir.listFiles();
        if (files == null) {
            Ui.add(list, Ui.row(this, "这个文件夹打不开 (或者 Kindle 的存储没有接入)", "", null));
            return;
        }
        Arrays.sort(files, new Comparator<File>() {
            @Override public int compare(File a, File b) { return a.getName().compareToIgnoreCase(b.getName()); }
        });
        int shown = 0;
        for (final File f : files) {
            if (!f.isDirectory() || skip(f)) continue;
            String r = rel(f);
            String state = "";
            if (covering(sel, r) != null) state = "已共享";
            else if (hasBelow(sel, r)) state = "含已共享的子文件夹";
            Ui.add(list, Ui.nav(this, f.getName(), state, new View.OnClickListener() {
                @Override public void onClick(View v) { dir = f; show(); }
            }));
            if (++shown >= 400) break;
        }
        if (shown == 0) Ui.add(list, Ui.row(this, "这里没有子文件夹", "", null));
    }
}
