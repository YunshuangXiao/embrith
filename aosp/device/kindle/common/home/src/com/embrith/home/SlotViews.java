/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors
 *
 * SlotViews: The content types of the settings home widgets: button, picture, text, clock, blank; and how each one is set up. */
package com.embrith.home;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.view.View;
import android.widget.FrameLayout;

import java.util.TimeZone;

/**
 * Builds the view of a slot from its type. The size of the slot only changes the area given to the content (w x h). The picture and the text
 * reuse the pieces of the desktop widgets (Widgets.pictureFrame, Widgets.center, Widgets.editText, Widgets.stats). Long press
 * sets the content up: button = the action, picture = the file, text = the words, clock = style and time zone, screen time of one app = the app.
 */
final class SlotViews {
    private SlotViews() { }

    static View build(final Activity a, final Slots set, final String id, int w, int h, boolean live, final Runnable redraw, final SlotArea area) {
        String type = set.type(a, id);
        View v;
        if (Slots.IMAGE.equals(type)) {
            v = Widgets.pictureFrame(a, set.imgKey(id), w, h);
        } else if (Slots.STATS.equals(type) || Slots.STATS_APP.equals(type)) {
            FrameLayout f = Widgets.framed(a);
            Widgets.stats(a, f, set.key(id, "st"), Slots.STATS_APP.equals(type), h);
            v = f;
        } else if (Slots.TEXT.equals(type)) {
            FrameLayout f = Widgets.framed(a);
            String s = Prefs.getStr(a, set.key(id, "text"), "");
            android.widget.TextView tv = s.length() == 0 ? Widgets.hint(a, "长按编辑文字") : Widgets.center(a, s, Math.min(w, h) >= Ui.dp(a, 150) ? 28 : 22, true);
            if (s.length() > 0) tv.setLineSpacing(0, 1.5f);   // the line spacing of a text widget is 1.5
            f.addView(tv, new FrameLayout.LayoutParams(-1, -1));
            v = f;
        } else if (Slots.CLOCK.equals(type)) {
            FrameLayout f = Widgets.framed(a);
            f.addView(new ClockView(a, ClockView.style(Prefs.getStr(a, set.key(id, "style"), "")), Prefs.getStr(a, set.key(id, "tz"), "")),
                new FrameLayout.LayoutParams(-1, -1));
            v = f;
        } else if (Slots.BUTTON.equals(type)) {
            final String act = action(a, set, id), arg = Prefs.getStr(a, set.key(id, "arg"), "");
            CircleButton b = new CircleButton(a, QuickActions.icon(act));
            b.setSquare(true);
            QuickActions.setState(b, act);
            if (live) {
                b.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View x) {
                        Actions.run(a, act, arg, null);
                        area.later();
                    }
                });
                area.track(b, act);
            }
            v = b;
        } else if (live) {
            v = new View(a);   // blank: no frame, no background, only takes the place
        } else {
            v = Widgets.framed(a);   // blank in a preview: the frame shows where the slot is
        }
        if (live) {
            v.setLongClickable(true);
            v.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View x) {
                    config(a, set, id, redraw);
                    return true;
                }
            });
        }
        return v;
    }

    /** The action of a button slot (default: Wi-Fi). */
    static String action(Activity a, Slots set, String id) {
        String act = Prefs.getStr(a, set.key(id, "act"), "wifi");
        return QuickActions.index(act) < 0 ? "wifi" : act;
    }

    /** True if the type has anything to set up. */
    static boolean hasConfig(String type) {
        return Slots.BUTTON.equals(type) || Slots.IMAGE.equals(type) || Slots.TEXT.equals(type) || Slots.CLOCK.equals(type) || Slots.STATS_APP.equals(type);
    }

    /** One line about the content of a slot, for the list of the slots. */
    static String summary(Activity a, Slots set, String id) {
        String type = set.type(a, id);
        String s = Ui.t(a, Slots.typeName(type));
        if (Slots.BUTTON.equals(type)) s += " / " + Ui.t(a, QuickActions.name(action(a, set, id))) + (Prefs.getStr(a, set.key(id, "arg"), "").length() > 0 ? " " + Prefs.getStr(a, set.key(id, "arg"), "") : "");
        else if (Slots.CLOCK.equals(type)) {
            String st = ClockView.style(Prefs.getStr(a, set.key(id, "style"), ""));
            for (int i = 0; i < ClockView.STYLES.length; i++) if (ClockView.STYLES[i].equals(st)) s += " / " + Ui.t(a, ClockView.NAMES[i]);
        }
        return s;
    }

    // ------------------------------------------------------------------ set up
    static void config(final Activity a, final Slots set, final String id, final Runnable redraw) {
        String type = set.type(a, id);
        if (Slots.IMAGE.equals(type)) {
            a.startActivity(new Intent(a, WallpaperActivity.class).putExtra("dir", WallpaperActivity.KINDLE).putExtra("target", set.imgKey(id)));
        } else if (Slots.TEXT.equals(type)) {
            Widgets.editText(a, set.key(id, "text"), redraw);
        } else if (Slots.STATS_APP.equals(type)) {
            Widgets.pickApp(a, set.key(id, "st"), redraw);
        } else if (Slots.BUTTON.equals(type)) {
            pickAction(a, set, id, redraw);
        } else if (Slots.CLOCK.equals(type)) {
            String[] items = { Ui.t(a, "样式"), Ui.t(a, "时区") };
            new Ui.Builder(a).setTitle("时钟").setItems(items, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int which) {
                    if (which == 0) pickStyle(a, set, id, redraw); else pickZone(a, set, id, redraw);
                }
            }).show();
        } else {
            Ui.toast(a, "此组件没有可设置项, 到 个性化 > 设置组件 里选择类型", false);
        }
    }

    /** The action of a button: first the sub-menu, then the action; an action with an argument asks for it. */
    private static void pickAction(final Activity a, final Slots set, final String id, final Runnable redraw) {
        pickAction(a, "按钮动作", prefSink(a, set.key(id, "act"), set.key(id, "arg")), false, Actions.BTN, redraw);
    }

    /** Where a chosen action and its argument go. */
    interface Sink {
        void put(String act, String arg);
    }

    /** A sink that stores the action and the argument in Prefs. */
    static Sink prefSink(final Context c, final String actKey, final String argKey) {
        return new Sink() {
            @Override public void put(String act, String arg) {
                Prefs.putStr(c, actKey, act);
                Prefs.putStr(c, argKey, arg);
            }
        };
    }

    /** The choice of an action for a button or a gesture (kind = Actions.BTN, TAP or SWP): the same sub-menus and arguments. withNone adds a first entry "none". */
    static void pickAction(final Activity a, String title, final Sink sink, final boolean withNone, final int kind, final Runnable redraw) {
        final int off = withNone ? 1 : 0;
        String[] cats = new String[Actions.CATS.length + off];
        if (withNone) cats[0] = Ui.t(a, "无");
        for (int i = 0; i < Actions.CATS.length; i++) cats[i + off] = Ui.t(a, Actions.CATS[i]);
        new Ui.Builder(a).setTitle(title).setItems(cats, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int pos) {
                if (pos < off) {
                    sink.put("none", "");
                    redraw.run();
                    return;
                }
                final int cat = pos - off;
                final java.util.List<Actions.Def> l = Actions.of(cat, kind);
                String[] names = new String[l.size()];
                for (int i = 0; i < names.length; i++) names[i] = Ui.t(a, l.get(i).name);
                new Ui.Builder(a).setTitle(Actions.CATS[cat]).setItems(names, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d2, int which) {
                        final Actions.Def def = l.get(which);
                        if (def.opts == null) {
                            sink.put(def.id, "");
                            redraw.run();
                            return;
                        }
                        new Ui.Builder(a).setTitle(def.paramTitle).setItems(def.opts, new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d3, int k) {
                                sink.put(def.id, def.opts[k]);
                                redraw.run();
                            }
                        }).show();
                    }
                }).show();
            }
        }).show();
    }

    private static void pickStyle(final Activity a, final Slots set, final String id, final Runnable redraw) {
        String[] items = new String[ClockView.NAMES.length];
        for (int i = 0; i < items.length; i++) items[i] = Ui.t(a, ClockView.NAMES[i]);
        new Ui.Builder(a).setTitle("时钟样式").setItems(items, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                Prefs.putStr(a, set.key(id, "style"), ClockView.STYLES[which]);
                redraw.run();
            }
        }).show();
    }

    private static void pickZone(final Activity a, final Slots set, final String id, final Runnable redraw) {
        final String[] ids = new String[1 + MenuActivity.TZ.length];
        String[] items = new String[ids.length];
        ids[0] = "";
        items[0] = Ui.t(a, "跟随系统时区");
        long now = System.currentTimeMillis();
        for (int i = 0; i < MenuActivity.TZ.length; i++) {
            ids[i + 1] = MenuActivity.TZ[i];
            int m = TimeZone.getTimeZone(MenuActivity.TZ[i]).getOffset(now) / 60000;
            items[i + 1] = Ui.t(a, MenuActivity.TZ_CITY[i]) + " (UTC" + (m < 0 ? "-" : "+") + Math.abs(m) / 60 + (m % 60 != 0 ? ":" + Math.abs(m) % 60 : "") + ")";
        }
        new Ui.Builder(a).setTitle("时区").setItems(items, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                Prefs.putStr(a, set.key(id, "tz"), ids[which]);
                redraw.run();
            }
        }).show();
    }
}
