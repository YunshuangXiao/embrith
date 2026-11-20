/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * FolderPickActivity: Chooses the folders of the library (several, on the Android storage); a choice is kept at
 * once. */
package com.embrith.library;

import com.embrith.home.Ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
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
 * Chooses the folders of the library (several, on the Android storage; the shared Kindle folders are under Kindle/). A choice is kept at
 * once, and the library home lists exactly these folders. Same layout as the shared-folder page.
 */
public class FolderPickActivity extends Activity {
    private LinearLayout lockBox, top, list;
    private TextView where;
    private Ui.Row lockRow;
    private boolean sub;   // the page of the chosen folders (lock switch + list)
    private File dir = LibStore.ROOT;
    private List<String> sel = new ArrayList<String>();

    /** For the settings page: how many folders are chosen. */
    public static int chosen() {
        return LibStore.folderRels().size();
    }

    /** Opens the choice. */
    public static void open(Context c) {
        c.startActivity(new Intent(c, FolderPickActivity.class));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout body = Ui.page(this, "选择文件夹", new View.OnClickListener() {
            @Override public void onClick(View v) { up(); }
        });
        where = Ui.caption(body, "");
        lockBox = Ui.addGroup(body);
        top = Ui.addGroup(body);
        list = Ui.addGroup(body);
        Ui.note(this, body, "选择文件夹", "选择文件夹\t选中放书的文件夹, 可以选多个; 选中上级文件夹后, 它下面的子文件夹不用再选.\n\n"
            + "共享\tKindle 上的文件夹要先在 设置 > 共享文件夹 里共享, 共享后会出现在 Kindle 文件夹下.");
    }

    @Override
    protected void onResume() {
        super.onResume();
        sel = LibStore.folderRels();
        show();
    }

    @Override
    public void onBackPressed() {
        up();
    }

    private void up() {
        if (sub) {
            sub = false;
            show();
        } else if (dir.equals(LibStore.ROOT) || dir.getParentFile() == null) {
            finish();
        } else {
            dir = dir.getParentFile();
            show();
        }
    }

    private String count() {
        return sel.size() + " 个文件夹";
    }

    /** The selected folder that contains rel (or is rel itself), or null. */
    private String covering(String rel) {
        for (String p : sel) if (rel.equals(p) || rel.startsWith(p + "/")) return p;
        return null;
    }

    private boolean hasBelow(String rel) {
        for (String p : sel) if (p.startsWith(rel + "/")) return true;
        return false;
    }

    private void show() {
        lockBox.removeAllViews();
        top.removeAllViews();
        list.removeAllViews();
        lockBox.setVisibility(sub ? View.GONE : View.VISIBLE);
        top.setVisibility(sub ? View.GONE : View.VISIBLE);
        if (sub) {
            showSub();
            return;
        }
        final String here = LibStore.rel(dir);
        where.setText(here.length() == 0 ? "存储" : "存储 / " + here);
        lockRow = Ui.nav(this, "已选文件夹", count(), new View.OnClickListener() {
            @Override public void onClick(View v) {
                sub = true;
                show();
            }
        });
        Ui.add(lockBox, lockRow);

        if (here.length() == 0) {
            Ui.add(top, Ui.row(this, "不能选择整个存储", "请进入文件夹后选择", null));
        } else {
            String cov = covering(here);
            if (cov != null && !cov.equals(here)) {
                Ui.add(top, Ui.row(this, "已随上级文件夹选中", cov, null));
            } else {
                Ui.add(top, Ui.switchRow(this, "选择这个文件夹", cov != null, new Ui.OnToggle() {
                    @Override public void toggled(boolean on) {
                        List<String> out = new ArrayList<String>();
                        for (String p : sel) if (!p.equals(here) && !p.startsWith(here + "/")) out.add(p);
                        if (on) out.add(here);
                        sel = out;
                        LibStore.saveFolders(sel);
                        lockRow.setValue(count());
                    }
                }));
            }
        }

        File[] files = dir.listFiles();
        if (files == null) {
            Ui.add(list, Ui.row(this, "这个文件夹打不开", "", null));
            return;
        }
        Arrays.sort(files, new Comparator<File>() {
            @Override public int compare(File a, File b) { return a.getName().compareToIgnoreCase(b.getName()); }
        });
        int shown = 0;
        for (final File f : files) {
            if (!f.isDirectory() || LibStore.hidden(this, f.getName())) continue;
            String r = LibStore.rel(f);
            String state = "";
            if (covering(r) != null) state = "已选";
            else if (hasBelow(r)) state = "含已选的子文件夹";
            Ui.add(list, Ui.nav(this, f.getName(), state, new View.OnClickListener() {
                @Override public void onClick(View v) {
                    dir = f;
                    show();
                }
            }));
            if (++shown >= 400) break;
        }
        if (shown == 0) Ui.add(list, Ui.row(this, "这里没有子文件夹", "", null));
    }

    /** The chosen folders. */
    private void showSub() {
        where.setText("已选文件夹");
        for (String p : sel) Ui.add(list, Ui.row(this, p, "", null));
        if (sel.isEmpty()) Ui.add(list, Ui.row(this, "还没有选择文件夹", "", null));
    }
}