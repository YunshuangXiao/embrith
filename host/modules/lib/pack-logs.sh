#!/bin/sh
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Embrith contributors
# 在 Kindle 上运行: 把 embrith 的日志压缩成一个很小的 diag.txt(默认 ≤6KB), 排障时只看它, 不必翻整套日志.
#   sh ./pack-logs.sh            生成 ./logs/diag.txt
#   sh pack-logs.sh -a                        另外带上 nstest 的精简片段(只有被问到时才用)
#   sh pack-logs.sh -m 9000                   改最大字节数
#   sh pack-logs.sh -d /某/日志目录           指定日志目录(在电脑上测试时用)
#   sh pack-logs.sh -o 文件                   输出到这个文件(默认 日志目录/diag.txt)
# 只用 busybox 里有的命令(sh grep sed awk tail head cut tr sort wc). 日志文件缺了的段落会写"(没有 xxx)".
D=${EMBRITH_DIR:-.}   # the program folder (VERSION); EMBRITH_SET = the settings folder (default $D/settings)
MAX=6144; ALL=0; LOGS=""; OUTF=""
while [ $# -gt 0 ]; do
  case "$1" in
    -a) ALL=1 ;;
    -m) MAX=$2; shift ;;
    -d) LOGS=$2; shift ;;
    -o) OUTF=$2; shift ;;
    -h|--help) sed -n '2,8p' "$0"; exit 0 ;;
    *) echo "未知参数: $1(-h 看用法)"; exit 1 ;;
  esac
  shift
done
[ -n "$LOGS" ] || LOGS="$D/logs"
[ -d "$LOGS" ] || { echo "没有日志目录: $LOGS"; exit 1; }
OUT="$LOGS/diag.txt"; [ -n "$OUTF" ] && OUT=$OUTF; TMP="$OUT.tmp"

# batt.log 各列见 power/sleep.sh batt_sample; 亮屏 = 前一行 B/A/W 到本行 A; 休眠 = S 到下一行 W
# 耗电按状态(WIFI BT FL)分组; 各项 = 两组平均电流之差(只在两种状态都出现过时给出); CPU 按进程组分摊
batt_report() {
  awk '
    function ma(q, t) { return t > 0 ? sprintf("%.1f", q / t * 3.6) : "-" }
    function pc(a, b) { return b > 0 ? sprintf("%.1f%%", a * 100 / b) : "-" }
    function d(x) { return x > 0 ? x : 0 }
    function diff(name, k1, k0) { if (st1[k1] > 0 && st1[k0] > 0) printf "  %s: 约 %.1f mA\n", name, (q1[k1] / st1[k1] - q1[k0] / st1[k0]) * 3.6 }
    {
      if (n && $3 <= pq) {
        dt = $1 - pt; dq = pq - $3; full = (NF >= 17 && pn >= 17)
        if ($2 == "A" && (pg == "A" || pg == "B" || pg == "W")) {
          k = "wifi=" pw " bt=" pb " fl=" pf; at[k] += dt; aq[k] += dq; tt += dt; tq += dq
          f = (pf + 0 > 0) ? "fl1" : "fl0"; st1["w" pw] += dt; q1["w" pw] += dq; st1["b" pb] += dt; q1["b" pb] += dq; st1[f] += dt; q1[f] += dq
          if (full) {
            ab[k] += d($9 - P[9]); aa[k] += d($10 - P[10]); ca += d($10 - P[10]); cb += d($9 - P[9])
            if (P[11] != "-" && $11 != "-") for (i = 11; i <= 16; i++) G[i] += d($i - P[i])   # S/W lines have no process columns
          }
        }
        if ($2 == "W" && pg == "S") {
          c++; sl += dt; sq += dq; e = ""
          if (full) { e = " CPU忙 " pc(d($9 - P[9]), d($10 - P[10])); if ($17 != "-" && P[17] != "-") e = e " 唤醒 " ($17 - P[17]) " 次" }
          L[c] = sprintf("  %s 休眠 %d 分 掉 %d mAh = %s mA (%d%%->%d%%)%s", strftime("%m-%d %H:%M", pt), dt / 60, dq / 1000, ma(dq, dt), pp, $5, e)
        }
      }
      n = 1; pt = $1; pg = $2; pq = $3; pp = $5; pw = $6; pf = $7; pb = (NF >= 8 ? $8 : "-"); pn = NF
      for (i = 9; i <= NF; i++) P[i] = $i
    }
    END {
      printf "亮屏 合计 %d 分, 平均 %s mA, CPU忙 %s\n", tt / 60, ma(tq, tt), pc(cb, ca)
      for (k in at) printf "  %s: %d 分, %s mA, CPU忙 %s\n", k, at[k] / 60, ma(aq[k], at[k]), pc(ab[k], aa[k])
      print "分项估算(两状态平均电流差, 其他条件未控制)"
      diff("Wi-Fi", "w1", "w0"); diff("蓝牙", "b1", "b0"); diff("前光(开 vs 关)", "fl1", "fl0")
      print "亮屏 CPU 占比(占全部 CPU 时间)"
      split("安卓 kdispd kgest ssh 内核线程 Kindle其余", N, " ")
      for (i = 11; i <= 16; i++) printf "  %s %s", N[i - 10], pc(G[i], ca); print ""
      printf "休眠 %d 次, 合计 %d 分, 平均 %s mA\n", c, sl / 60, ma(sq, sl)
      for (i = (c > 5 ? c - 4 : 1); i <= c; i++) print L[i]
    }' "$1"
}

sec() { echo; echo "## $1"; }
have() { [ -s "$LOGS/$1" ]; }
tailf() { if have "$1"; then tail -n "$2" "$LOGS/$1" | cut -c1-170; else echo "(没有 $1)"; fi; }
grepf() {            # grepf 文件 正则 最多行数
  if have "$1"; then grep -E "$2" "$LOGS/$1" 2>/dev/null | cut -c1-170 | tail -n "$3"; else echo "(没有 $1)"; fi
}

{
  echo "# embrith diag $(date '+%Y-%m-%d %H:%M')"
  echo "Copyright (C) 2026 Embrith Contributors | GPL-3.0-or-later"
  VER=$(head -n1 "$D/VERSION" 2>/dev/null | tr -d '\r\n '); [ -n "$VER" ] || VER=未知
  FW=$(head -n1 /etc/prettyversion.txt 2>/dev/null | cut -c1-60); [ -n "$FW" ] || FW=$(head -n1 /etc/version.txt 2>/dev/null | cut -c1-60); [ -n "$FW" ] || FW=未知
  echo "版本 $VER | 内核 $(uname -r 2>/dev/null) | 固件 $FW"
  echo "现在内存: $(grep -E '^MemAvailable' /proc/meminfo 2>/dev/null | tr -s ' ')"

  sec "last-run(最后 25 行)";        tailf last-run.txt 25
  sec "显示(fbink 回退 / 启动画面 / 退出页)"; grepf kdispd.log 'fbink variant|splash|exit page|stopping|WARNING' 8
  sec "翻页判定(class / swipe)"; grepf kdispd.log '^class:|^swipe:|^overlay:' 24
  sec "手势";                          tailf kgest.log 6
  sec "看门狗";                        tailf supervise.log 6
  if have power.log; then sec "休眠/唤醒(power.log 最后 12 行)"; tailf power.log 12; fi
  sec "USB";                           grepf supervise.log ' usb: ' 6
  if have ssh.log || [ -s "${EMBRITH_SET:-$D/settings}/custom.conf" ]; then
    sec "SSH"; tailf ssh.log 4
    # login mode and ports of the sources; SSH_HASH is left out on purpose (a password hash)
    grep -E '^SSH_(MODE|MODE_koreader|PORT_embrith|PORT_koreader)=' "${EMBRITH_SET:-$D/settings}/custom.conf" 2>/dev/null | tr '\n' ' '; echo
  fi
  if have batt.log; then sec "耗电(mA, 由 charge_now 差值算; 充电段跳过)"; batt_report "$LOGS/batt.log"; fi

  sec "内存(最近一次快照)"
  if have live.txt; then
    grep -E '^(MemTotal|MemFree|MemAvailable|SwapFree)' "$LOGS/live.txt" | tr -s ' ' | head -n 4
    echo "进程 RSS 前 8(kB 名字):"
    grep ' rss=' "$LOGS/live.txt" | sed 's/^[0-9]* \([^ ]*\) rss=\([0-9]*\)kB.*/\2 \1/' | sort -rn | head -n 8
  else echo "(没有 live.txt)"; fi

  sec "安卓状态"
  if have live.txt; then
    grep -E 'sys.boot_completed|dev.bootcomplete' "$LOGS/live.txt" | head -n 2
    echo "不在运行的服务(stopped 是正常的, 不列出):"
    grep 'init.svc' "$LOGS/live.txt" | grep -v -e running -e stopped -e adbd | head -n 6
  else echo "(没有 live.txt)"; fi
  have packages.txt && echo "已安装的包: $(wc -l < "$LOGS/packages.txt") 个"

  sec "logcat(只留关键行)"
  if have logcat-live.txt; then
    grep -E 'FATAL EXCEPTION|Fatal signal|No home screen|ANR in|Watchdog|There must be|Failure starting system services|Displayed |START u0 .*HOME' "$LOGS/logcat-live.txt" \
      | grep -v 'Fatal signal 6.*(sh)' | cut -c1-170 | tail -n 12
    echo "-- 崩溃堆栈(前 3 次, 每次 4 行):"
    awk '/FATAL EXCEPTION/ {n++; c=4} c>0 && n<=3 {print; c--}' "$LOGS/logcat-live.txt" | cut -c1-170 | head -n 12
  else echo "(没有 logcat-live.txt)"; fi

  sec "内核(上次停止时)"
  if have last-stop-dmesg.txt; then
    echo "zygote 被杀 $(grep -c "Service 'zygote'.*killed by signal" "$LOGS/last-stop-dmesg.txt") 次(正常停止 0~1 次; 3 次以上是 system_server 崩溃循环)"
    grep -E 'segfault|Out of memory|Oops|unhandled' "$LOGS/last-stop-dmesg.txt" | cut -c1-170 | tail -n 6
  else echo "(没有 last-stop-dmesg.txt)"; fi
  if have homediag.txt; then sec "homediag"; grepf homediag.txt 'boot_completed|^Starting:|^Error: [a-z]|Exception|Error type' 6; fi
  if have fbdiag.txt;   then sec "fbdiag";   grepf fbdiag.txt 'rc=|Segmentation|variant' 8; fi

  if [ "$ALL" = 1 ]; then
    sec "(-a)nstest";      tailf nstest.txt 8
  fi
} > "$TMP" 2>/dev/null

SIZE=$(wc -c < "$TMP")
if [ "$SIZE" -gt "$MAX" ]; then
  head -c "$MAX" "$TMP" > "$OUT"; printf '\n... (超过 %s 字节, 已截断; 原来 %s 字节)\n' "$MAX" "$SIZE" >> "$OUT"
else
  cp "$TMP" "$OUT"
fi
rm -f "$TMP"
echo "已生成 $OUT ($(wc -c < "$OUT") 字节, $(wc -l < "$OUT") 行). "
