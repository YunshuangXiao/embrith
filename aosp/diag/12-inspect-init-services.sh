#!/bin/bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Embrith contributors
# 只读: 看 init 怎么读 /system/etc/init, 怎么给服务算 SELinux 上下文. 输出到 ~/init-services.txt
cd ~/aosp/system/core/init || exit 1
{
echo "== who reads /system/etc/init"
grep -rn "etc/init\|ParseConfigDir\|is_dir" *.cpp *.h ../rootdir/init.rc | head -30
echo
echo "== getcon / is_selinux_enabled / setexeccon / setsockcreatecon in init"
grep -n "getcon\|is_selinux_enabled\|setexeccon\|setsockcreatecon\|security_compute_create\|selabel_lookup" *.cpp | head -40
echo
echo "== Service::Start (context part)"
grep -n "could not get context" -B25 -A45 service.cpp | head -140
echo
echo "== init.cpp: lines after ParseConfig"
grep -n "ParseConfig" -B3 -A6 init.cpp | head -60
} > ~/init-services.txt 2>&1
wc -c ~/init-services.txt; echo "已写入 ~/init-services.txt"
