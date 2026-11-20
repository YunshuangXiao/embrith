#!/bin/bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Embrith contributors
# 只读, 在 WSL 宿主里运行: 把写启动器需要的 init 脚本收集成一个文件 ~/aosp/pkg/init-dump.txt
P=~/aosp/out/target/product/pw6
OUT=~/aosp/pkg/init-dump.txt
mkdir -p ~/aosp/pkg
{
echo "######## pkg 目录"; ls -lh ~/aosp/pkg
echo "######## system/etc/init 列表"; ls -l $P/system/etc/init/
for f in init.rc init.environ.rc init.zygote32.rc; do echo; echo "######## root/$f"; cat $P/root/$f; done
for f in $P/system/etc/init/*.rc; do echo; echo "######## system/etc/init/$(basename $f)"; cat "$f"; done
echo; echo "######## ueventd.rc 头部"; head -40 $P/root/ueventd.rc
echo; echo "######## system/build.prop (ro.* 部分)"; grep -E "^ro\." $P/system/build.prop | head -80
} > $OUT 2>&1
wc -c $OUT; echo "已写入 $OUT"
