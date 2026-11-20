# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Embrith contributors
# host/modules/core/hwinfo.sh - 随设备变化的参数, 每次启动时从硬件读取 (内存, 屏幕尺寸, 像素密度, 型号代码).
# 由 host/embrith.sh 加载. 读不到时函数不输出内容, 由调用方决定怎么处理.
#
# Referenced project: KOReader, AGPL-3.0, https://github.com/koreader/koreader (COPYING in that project). File consulted:
#   frontend/device/kindle/device.lua        model code taken from the serial number in /proc/usid (kindle_sn)

hw_ram_mb() {         # 内存总量 (MB)
  awk '/^MemTotal:/ { printf "%d", $2 / 1024; exit }' /proc/meminfo
}

hw_panel() {          # 屏幕尺寸 "W H": 读帧缓冲 (kutil panel)
  "$RUNBIN/kutil" panel 2>/dev/null
}

hw_devcode() {        # 型号代码: /proc/usid 序列号里的型号段 (KOReader 也用它识别机型): 旧序列号 B 或 9 开头取第 3-4 位, 其余取第 4-6 位
  usid=$(cat /proc/usid 2>/dev/null); [ -n "$usid" ] || return 1
  case "$usid" in B*|9*) echo "$usid" | cut -c3-4 ;; *) echo "$usid" | cut -c4-6 ;; esac
}

hw_ppi() {            # 像素密度: 按型号代码查 modules/conf/models.conf, 表里没有这个型号时不输出
  c=$(hw_devcode) || return 1
  case "$c" in ""|*[!0-9A-Za-z]*) return 1 ;; esac
  sed -n "s/^$c[ 	][ 	]*\([0-9][0-9]*\).*/\1/p" "$MOD/conf/models.conf" 2>/dev/null | head -n1
}
