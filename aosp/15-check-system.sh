#!/bin/bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Embrith contributors
# 编译后在 WSL 里运行: bash 03-container.sh run './15-check-system.sh'   看字体 / 断字数据 / 键盘布局是否进了镜像
cd "$(dirname "$0")"; P=out/target/product/pw6/system
echo "== fonts.xml:";           ls -l $P/etc/fonts.xml 2>&1
echo "== fonts: $(ls $P/fonts 2>/dev/null | wc -l) files, $(du -sh $P/fonts 2>/dev/null | cut -f1)"; ls $P/fonts 2>/dev/null | head -40
echo "== hyphen-data: $(ls $P/usr/hyphen-data 2>/dev/null | wc -l) files"
echo "== keylayout: $(ls $P/usr/keylayout 2>/dev/null | wc -l) files;  keychars: $(ls $P/usr/keychars 2>/dev/null | wc -l)"
echo "== CJK fonts mentioned in fonts.xml and present?"
for f in $(grep -oE '[A-Za-z0-9_-]+\.(ttf|ttc|otf)' $P/etc/fonts.xml 2>/dev/null | sort -u); do [ -e $P/fonts/$f ] && echo "ok      $f" || echo "MISSING $f"; done | sort | head -60
echo "== system image size"; ls -lh out/target/product/pw6/system.img 2>&1

echo "== installed apps (system/app + system/priv-app): the slim image should have only a handful"
for d in app priv-app; do echo "-- $d: $(ls $P/$d 2>/dev/null | wc -l)"; ls $P/$d 2>/dev/null | tr '\n' ' '; echo; done
echo "== home screen and input method"
ls -l $P/app/KindleHome/KindleHome.apk $P/priv-app/KindleHome/KindleHome.apk 2>&1 | grep -v "No such" || echo "KindleHome.apk MISSING"
ls -d $P/app/PinyinIME $P/priv-app/PinyinIME 2>/dev/null || echo "PinyinIME not in this image (the default input method is whatever the framework offers)"
echo "== native daemons that init starts"
for f in logd debuggerd servicemanager surfaceflinger bootanimation vold vdc netd ndc installd keystore gatekeeperd audioserver mediaserver sdcard iptables toolbox toybox am pm settings ime input svc wm app_process; do
  [ -e $P/bin/$f ] && echo "ok      bin/$f" || echo "MISSING bin/$f"; done
[ -e out/target/product/pw6/root/sbin/healthd ] && echo "ok      sbin/healthd" || echo "MISSING sbin/healthd"
[ -e $P/bin/lmkd ] && echo "ok      bin/lmkd" || echo "MISSING bin/lmkd"
echo "== touch idc dir"; ls $P/usr/idc 2>&1 | head -5
echo "== ethernet feature (the container's network shows up as Ethernet)"
ls $P/etc/permissions/android.hardware.ethernet.xml 2>&1
echo "== KindleHome files (the apk itself is small and has no classes.dex: dex pre-optimisation moves the code to oat/arm/KindleHome.odex)"
ls -lR $P/app/KindleHome 2>&1 | head -12
ls -l out/target/common/obj/APPS/KindleHome_intermediates/classes.dex 2>&1
