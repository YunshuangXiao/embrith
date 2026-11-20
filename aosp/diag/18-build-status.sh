#!/bin/bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Embrith contributors
# 只读: 编译到底有没有成功?现在 system.img / embrith.raw 是不是新的, 是不是精简版?  在 WSL 里直接运行(不用进容器): 
#   bash diag/18-build-status.sh      输出在 ~/build-status.txt
cd ~/aosp || exit 1
P=out/target/product/pw6
{
echo "== now: $(date)"
echo "== files with a colon in the name under device/kindle (must be none; they break the build)"
find device/kindle -name '*:*' | head -20
echo "== product file (the slim product starts with 'Product: framework + runtime + our own home screen')"
head -8 device/kindle/pw6/kindle_pw6.mk; ls device/kindle/pw6; ls device/kindle/pw6/use-core-mk 2>&1 | head -1
echo
echo "== image and package times"
ls -l --time-style=full-iso $P/system.img pkg/embrith.raw device/kindle/pw6/kindle_pw6.mk 2>&1
echo
echo "== latest build logs"
ls -lt build-*.log 2>/dev/null | head -5
L=$(ls -t build-*.log 2>/dev/null | head -1)
if [ -n "$L" ]; then
  echo "-- last 25 lines of $L"; tail -n 25 "$L"
  echo "-- first errors in $L"; grep -nE "error:|FAILED|No rule to make|Cannot|\*\*\*" "$L" | head -25
fi
echo
echo "== apps inside the built system (slim = a handful)"
echo "app:      $(ls $P/system/app 2>/dev/null | tr '\n' ' ')"
echo "priv-app: $(ls $P/system/priv-app 2>/dev/null | tr '\n' ' ')"
ls -l $P/system/app/KindleHome $P/system/priv-app/KindleHome 2>&1 | grep -v "No such" | head -5
echo
echo "== does installed-files mention the old apps?"
grep -cE "Launcher2|Calendar|Email|DeskClock|Contacts|TeleService" $P/installed-files.txt 2>/dev/null
echo "== system.img size"; ls -lh $P/system.img 2>&1
} > ~/build-status.txt 2>&1
wc -l ~/build-status.txt
