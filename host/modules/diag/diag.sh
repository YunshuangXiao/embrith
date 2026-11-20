# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Embrith contributors
# host/diag/diag.sh - diagnostics: snapshots, self test, home screen check, flight recorder, display and fbink tests.
# Sourced by host/embrith.sh.

# Two pictures of the screen as .pgm files in logs/: screen-android.pgm = what Android has drawn (the display bridge's source buffer),
# screen-fb0.pgm = what is in the e-ink framebuffer (what fbink has put on the panel). If the first one shows the home screen and the
# second one does not, the problem is in the display bridge; if neither does, Android did not draw it.
shot() {
  mkdir -p "$LOGDIR"
  rm -f "$K_CTL/screen.pgm"; touch "$K_CTL/dump"; sleep 1
  [ -f "$K_CTL/screen.pgm" ] && cp "$K_CTL/screen.pgm" "$LOGDIR/screen-android.pgm"
  PNL=$("$RUNBIN/kutil" panel 2>/dev/null)   # "W H" from the framebuffer
  if [ -r /dev/fb0 ] && [ -n "$PNL" ]; then { printf 'P5\n%s\n255\n' "$PNL"; dd if=/dev/fb0 bs="${PNL% *}" count="${PNL#* }" 2>/dev/null; } > "$LOGDIR/screen-fb0.pgm"; fi
  sync
}

# one-shot state dump (never blocks for long)
snapshot() {
  [ "$DEV_MODE" = 1 ] || return 0      # without DEV_MODE the live state is collected only by report_gen
  {
    echo "== snapshot $(date) elapsed=${1:-?}s"
    grep -E "MemTotal|MemFree|MemAvailable|SwapTotal|SwapFree" /proc/meminfo
    echo "-- container processes (host view)"; ct_ps | head -n 60
    if [ ! -f "$ST/ct_broken" ]; then
      echo "-- init services"; in_ct_t /system/bin/getprop 2>&1 | grep -E "init.svc|sys.boot_completed|dev.bootcomplete" | head -n 60
      in_ct_t /system/bin/logcat -d -v time > "$LOGDIR/logcat-live.txt" 2>&1
      echo "-- logcat (last 60 of $(wc -l < "$LOGDIR/logcat-live.txt" 2>/dev/null) lines; full copy in logcat-live.txt)"; tail -n 60 "$LOGDIR/logcat-live.txt"
    else
      echo "(running commands inside the container does not work: see nstest.txt)"
    fi
    echo "-- dmesg (filtered)"; cmd_log | tail -n 80
  } > "$LOGDIR/live.txt.tmp" 2>&1
  mv "$LOGDIR/live.txt.tmp" "$LOGDIR/live.txt"
  sync
}

# One-time check of the different ways to run a command inside the container (logs/nstest.txt).
# If plain nsenter hangs we mark the container "ct_broken" so that the watchdog never waits on it again.
selftest() {
  P=$(init_pid)
  tt() { n=$1; shift; s0=$(date +%s); o=$(timeout 3 "$@" 2>&1 </dev/null); rc=$?; echo "[$n] rc=$rc $(( $(date +%s) - s0 ))s out=$(echo "$o" | head -c 160 | tr '\n' '|')"; }
  {
    echo "== nstest $(date) init_pid=$P"
    echo "-- container root: $(ls /proc/$P/root 2>&1 | tr '\n' ' ' | cut -c1-200)"
    echo "-- system/bin present: $(ls /proc/$P/root/system/bin 2>&1 | head -n 3 | tr '\n' ' ')"
    tt A-m          nsenter -t $P -m /system/bin/sh -c 'echo A'
    tt C-m-p        nsenter -t $P -m -p /system/bin/sh -c 'echo C'
    tt E-all        nsenter -t $P -m -u -i -n -p -r -w /system/bin/sh -c 'echo E'
    tt F-all-env    nsenter -t $P -m -u -i -n -p -r -w /system/bin/env -i PATH=/system/bin /system/bin/sh -c 'echo F'
    tt G-getprop    nsenter -t $P -m -u -i -n -p -r -w /system/bin/getprop ro.embrith.stage
    tt H-ls         nsenter -t $P -m -u -i -n -p -r -w /system/bin/ls /dev
    echo "-- boot image present? $(ls -l /proc/$P/root/system/framework/arm 2>&1 | head -n 8 | tr '\n' ';' | cut -c1-400)"
    echo "-- kernel messages about killed/crashed processes"
    dmesg | grep -iE "killed process|segfault|oom-kill|out of memory|nsenter|sh\[[0-9]+\]|unhandled" | tail -n 15
  } > "$LOGD/nstest.txt" 2>&1
  grep -q '^\[E-all\] .*out=E' "$LOGD/nstest.txt" || touch "$ST/ct_broken"
  sync
}

# Why is there no home screen? Runs once, 100 s after the start. Writes logs/homediag.txt and, when the
# system has not finished booting because no HOME activity was found, starts KindleHome by name: that both lets the boot finish and
# prints the real error if the app itself cannot start.
homediag() {
  {
    echo "== homediag $(date)"
    echo "-- sys.boot_completed: $(in_ct_long /system/bin/getprop sys.boot_completed 2>&1)"
    echo "-- activities that answer to HOME:"
    ct_sh_long "pm query-intent-activities -a android.intent.action.MAIN -c android.intent.category.HOME" 2>&1 | head -n 30
    echo "-- is KindleHome installed:"
    ct_sh_long "pm list packages -f com.embrith" 2>&1 | head -n 5
    echo "-- dumpsys package com.embrith.home (first 80 lines):"
    in_ct_long /system/bin/dumpsys package com.embrith.home 2>&1 | head -n 80
    echo "-- users:"
    in_ct_long /system/bin/dumpsys user 2>&1 | head -n 20
    if [ "$(in_ct_long /system/bin/getprop sys.boot_completed 2>/dev/null | tr -d '\r\n')" != 1 ]; then
      echo "-- boot has not completed: starting KindleHome by name"
      ct_sh_long "am start -n com.embrith.home/.HomeActivity" 2>&1 | head -n 15
    fi
  } > "$LOGD/homediag.txt" 2>&1
  sync
}

# Which fbink refresh modes really change the panel? Writes five lines of text, each with a different refresh, 4 s apart.
# Look at the screen while it runs and tell which lines are visible. Result and fbink messages: logs/disptest.txt
cmd_disptest() {
  L="$LOGDIR/disptest.txt"; mkdir -p "$LOGDIR"
  [ -x "$FBINK" ] || die "no fbink at $FBINK"
  PNL=$("$RUNBIN/kutil" panel 2>/dev/null); PNL_W=${PNL% *}; PNL_H=${PNL#* }   # panel size from the framebuffer
  ui_pause
  { echo "== disptest $(date)"
    "$FBINK" -e 2>&1 | head -n 8
    "$FBINK" -c -f 2>&1; sleep 3
    echo "-- A: GC16 flashing";  "$FBINK" -f -W GC16 -y 4  "A  GC16 mit Flash 1111" 2>&1; echo "rc=$?"; sleep 4
    echo "-- B: GL16 partial";   "$FBINK" -W GL16 -y 8      "B  GL16 ohne Flash 2222" 2>&1; echo "rc=$?"; sleep 4
    echo "-- C: DU partial";     "$FBINK" -W DU -y 12       "C  DU ohne Flash 3333" 2>&1; echo "rc=$?"; sleep 4
    echo "-- D: A2 partial";     "$FBINK" -W A2 -y 16       "D  A2 ohne Flash 4444" 2>&1; echo "rc=$?"; sleep 4
    echo "-- E: region refresh GL16 of the whole screen"; "$FBINK" -s "top=0,left=0,width=${PNL_W:-0},height=${PNL_H:-0}" -W GL16 2>&1; echo "rc=$?"; sleep 4
    echo "== done"; } > "$L" 2>&1
  sleep 6; ui_resume
  say "disptest finished: $L"
}

# Why does fbink crash? (logs/fbdiag.txt) Needs no Android. Every call below is made without pausing the Kindle UI.
cmd_fbdiag() {
  L="$LOGDIR/fbdiag.txt"; mkdir -p "$LOGDIR"
  K="$RUNBIN/ktrace"; [ -x "$K" ] || K="$BASE/bin/ktrace"
  { echo "== fbdiag $(date)  ($(uname -r))"
    echo "-- the binary"; ls -l "$FBINK" 2>&1; ls -l /mnt/us/libkh/bin 2>&1 | head -n 20
    echo "-- environment of this script"; env | sort | grep -E "^(PATH|LD_|HOME|USER|FBINK|TERM|PWD)" 
    echo "-- who has /dev/fb0 open"
    for p in /proc/[0-9]*; do ls -l "$p/fd" 2>/dev/null | grep -q "/dev/fb0" && echo "$p $(tr '\0' ' ' < $p/cmdline 2>/dev/null | cut -c1-60)"; done
    echo "-- /dev/fb0"; ls -l /dev/fb0 /dev/graphics/fb0 2>&1; cat /sys/class/graphics/fb0/virtual_size /sys/class/graphics/fb0/bits_per_pixel 2>&1
    echo "-- fbink -V";  "$FBINK" -V 2>&1; echo "rc=$?"
    echo "-- fbink -e";  "$FBINK" -e 2>&1; echo "rc=$?"
    echo "-- fbink -s (refresh the whole screen)"; "$FBINK" -s 2>&1; echo "rc=$?"
    echo "-- the last system calls of fbink -e (ktrace)"
    [ -x "$K" ] && "$K" -n 45 "$FBINK" -e 2>&1 | tail -n 60 || echo "no ktrace"
    echo "-- the Kindle's own eips"; ls -l /usr/sbin/eips /usr/bin/eips 2>&1 | head -n 3; eips 3 3 "eips test" 2>&1; echo "rc=$?"
    echo "-- kernel messages about crashes"; dmesg 2>/dev/null | tail -n 40 | grep -iE "fbink|segv|unhandled|sigsegv|abort|fault"
    echo "-- limits"; ulimit -a 2>&1 | head -n 12
    echo "== done"; } > "$L" 2>&1
  say "fbdiag finished: $L"
}

# ---- report: the last minutes of everything, for bug analysis ----------------------------------------------------------------
# logs/report/report-YYYYMMDD-HHMMSS.txt. Made by hand: "embrith.sh report [minutes]", the Embrith Diag booklet, or the launcher
# (Settings > General > Generate log, ctl/report_gen). Without DEV_MODE nothing writes debug logs to the flash all the time: the daemons write
# to $LOGD (memory, cut by ring_trim), the container's logcat and the kernel's dmesg are rings by themselves, and this reads all of them once.
RPT=$LOGDIR/report

logcat_recent() {      # stdin: logcat -v time. Keeps the last $1 minutes, by the clock of the log itself (the newest line is "now")
  awk -v m="$1" '
    { L[NR] = $0; split($2, t, /[:.]/); S[NR] = t[1] * 3600 + t[2] * 60 + t[3] }
    END {
      if (!NR) exit
      last = S[NR]; cut = last - m * 60
      for (i = 1; i <= NR; i++) { d = S[i]; if (d > last) d -= 86400; if (d >= cut) print L[i] }
    }'
}

rp_tail() {            # file lines title
  echo; echo "## $3"
  if [ -s "$1" ]; then tail -n "$2" "$1" | cut -c1-400; else echo "(none)"; fi
}

report_gen() {
  rp_min=${1:-5}; case "$rp_min" in ''|*[!0-9]*) rp_min=5 ;; esac; [ "$rp_min" -ge 1 ] || rp_min=5
  [ -f "$ST/report.busy" ] && return 0
  : > "$ST/report.busy"
  rp_t=$(date +%Y%m%d-%H%M%S); rp_work=$ST/report.work
  if [ -n "$2" ]; then rp_out=$2; rp_cap=200000                      # $2 = a file in memory (report_auto): small
  else mkdir -p "$RPT"; rp_out=$RPT/report-$rp_t.txt; rp_cap=900000; fi
  rm -rf "$rp_work"; mkdir -p "$rp_work"
  # the files pack-logs.sh reads, under the same names as in logs/
  for f in kdispd.log kgest.log supervise.log power.log enter.log net.log batt.log nstest.txt homediag.txt; do
    [ -s "$LOGD/$f" ] && cp "$LOGD/$f" "$rp_work/$f"
  done
  [ -s "$LOGDIR/ssh.log" ] && cp "$LOGDIR/ssh.log" "$rp_work/ssh.log"
  [ -s "$LOGDIR/last-run.txt" ] && tail -n 200 "$LOGDIR/last-run.txt" > "$rp_work/last-run.txt"
  cmd_log > "$rp_work/last-stop-dmesg.txt" 2>/dev/null
  # suspend and resume lines of the whole dmesg ring, counted by lines (the filtered newest 300 lines above hold only the last minutes)
  dmesg 2>/dev/null | grep -E "PM: suspend (entry|exit)|Resume caused by|bd71828_set_alarm, rtc alarm|pt_touch_write_ioctl::command=(un)?lock|PMIC irq" | cut -c1-160 | tail -n 150 > "$rp_work/kernel-pm.txt"
  {
    echo "== live $(date)"
    grep -E "MemTotal|MemFree|MemAvailable|SwapTotal|SwapFree" /proc/meminfo
    echo "-- container processes (host view)"; ct_ps | head -n 60
    if alive && [ ! -f "$ST/ct_broken" ]; then
      echo "-- init services"; in_ct_t /system/bin/getprop 2>&1 | grep -E "init.svc|sys.boot_completed|dev.bootcomplete" | head -n 60
    fi
  } > "$rp_work/live.txt" 2>&1
  if alive && [ ! -f "$ST/ct_broken" ]; then
    in_ct_t /system/bin/logcat -d -v time > "$rp_work/logcat-all.txt" 2>&1
    logcat_recent "$rp_min" < "$rp_work/logcat-all.txt" | tail -c 400000 > "$rp_work/logcat-live.txt"
    # the crashes of the whole logcat ring, not only of the last minutes
    grep -E 'FATAL EXCEPTION|Fatal signal|ANR in|Watchdog' "$rp_work/logcat-all.txt" | cut -c1-200 | tail -n 40 > "$rp_work/logcat-key.txt"
    ct_sh_t 20 'pm list packages' > "$rp_work/packages.txt" 2>&1
  fi
  EMBRITH_DIR=$BASE EMBRITH_SET=$SET sh "$MOD/lib/pack-logs.sh" -d "$rp_work" -o "$rp_work/summary.txt" -m 12000 >/dev/null 2>&1
  {
    echo "# embrith report $rp_t (logcat: last $rp_min min; power.log and kernel suspend lines: by count; the other daemon logs: their newest lines) DEV_MODE=${DEV_MODE:-0}"
    cat "$rp_work/summary.txt" 2>/dev/null
    [ -z "$2" ] && [ -s "$LOGD/auto-report.txt" ] && { echo; echo "## last abnormal stop (written to memory when it happened)"; tail -c 120000 "$LOGD/auto-report.txt"; }
    rp_tail "$rp_work/power.log" 300 "power.log (sleep and wake, newest 300 lines, not by minutes)"
    rp_tail "$rp_work/kernel-pm.txt" 150 "kernel suspend and resume (whole dmesg ring)"
    rp_tail "$rp_work/kdispd.log" 400 "kdispd.log"
    rp_tail "$rp_work/kgest.log" 300 "kgest.log"
    rp_tail "$rp_work/supervise.log" 200 "supervise.log"
    rp_tail "$rp_work/enter.log" 100 "enter.log"
    rp_tail "$LOGDIR/install.log" 40 "install.log (update and rollback)"
    rp_tail "$rp_work/net.log" 60 "net.log"
    rp_tail "$rp_work/last-run.txt" 120 "last-run.txt"
    rp_tail "$rp_work/logcat-key.txt" 40 "logcat key lines (whole ring)"
    echo; echo "## logcat (last $rp_min min)"
    if [ -s "$rp_work/logcat-live.txt" ]; then cat "$rp_work/logcat-live.txt"; else echo "(none: the container is not running)"; fi
    echo; echo "## dmesg (filtered, newest 300 lines)"; cat "$rp_work/last-stop-dmesg.txt"
    echo; echo "## live"; cat "$rp_work/live.txt"
  } 2>/dev/null | head -c "$rp_cap" > "$rp_out"
  rm -rf "$rp_work" "$ST/report.busy"
  [ -n "$2" ] || sync
}

# An abnormal stop (boot timeout, memory emergency, the container died or did not start): its state is gone with the container, so a short report
# goes to memory now: $LOGD/auto-report.txt, one file of at most 200 KB, replaced each time. Reports made by hand include it.
# DEV_MODE=1 writes flash files instead (snapshot).
report_auto() {
  [ "$DEV_MODE" = 1 ] && return 0
  report_gen 5 "$LOGD/auto-report.txt"
}

# ctl/report_list: one line per report, newest first: name TAB bytes (written atomically, an empty file = no reports)
report_list() {
  o="$K_CTL/report_list.tmp"; [ -d "$K_CTL" ] || return 0
  { for f in $(ls -1t "$RPT" 2>/dev/null); do
      case "$f" in report-*.txt) printf '%s\t%s\n' "$f" "$(wc -c < "$RPT/$f")" ;; esac
    done; } > "$o" && chmod 666 "$o" && mv "$o" "$K_CTL/report_list"
}

rp_name_ok() { case "$1" in report-*.txt) case "$1" in *[!A-Za-z0-9._-]*|*..*) return 1 ;; esac; return 0 ;; esac; return 1; }

report_del() {         # a name, or * for all: only files that report_gen made
  case "$1" in
    \*) rm -f "$RPT"/report-*.txt ;;
    *) rp_name_ok "$1" && rm -f "$RPT/$1" ;;
  esac
}

report_view() {        # ctl/report_text = the first 64 KB of one report
  o="$K_CTL/report_text.tmp"
  if rp_name_ok "$1" && [ -f "$RPT/$1" ]; then head -c 65536 "$RPT/$1" > "$o"; else echo "(file not found)" > "$o"; fi
  chmod 666 "$o" && mv "$o" "$K_CTL/report_text"
}
