#!/bin/sh
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Embrith contributors
# embrith - run Android 7.1.2 in a container on the Kindle PW6.
#   embrith.sh start | stop | status | shell [cmd...] | logcat | log | install <apk> | launch <package>
#   debugging: panic | shot | disptest | fbdiag | icons | ctps | supervise (used by the scripts themselves)
#
# Referenced project: KOReader, AGPL-3.0, https://github.com/koreader/koreader (COPYING in that project). File consulted:
#   platform/kindle/koreader.sh              the --framework_stop option
# The code is in modules/ (one subfolder per function, see the first line of each file):
#   modules/core/     common helpers, device nodes, start, stop
#   modules/ui/       Kindle UI pause/resume, launcher requests (ctl/), icons, powerd properties
#   modules/power/    sleep, wake, idle, power events (sleep.sh)
#   modules/net/      network (slirp4netns), SSH
#   modules/install/  apk install; update.sh = install and update of embrith itself (run by documents/embrith-start.sh before "start")
#   modules/diag/     snapshots, self test, flight recorder, display and fbink tests
#   modules/watchdog/ the background watchdog of a running container
# modules/conf and modules/lib hold the settings and the helper scripts that run on the Kindle; modules/assets the pictures.
# This file only loads them and dispatches the command.
DIR=$(cd "$(dirname "$0")" && pwd)
# the program folder is read through the real ext4 behind /mnt/us (the images and /data need a real filesystem)
case "$DIR" in /mnt/us/*) [ -d "/mnt/base-us${DIR#/mnt/us}" ] && DIR=/mnt/base-us${DIR#/mnt/us} ;; esac
MOD=$DIR/modules
UI_MODE=stop; UI_SERVICES=""; TOUCH_GRAB=1; ZRAM_MB=auto; BOOT_TIMEOUT=300; RUN_LIMIT=300; STAY_AFTER_BOOT=60; BINDER_DEV=auto
[ -f "$MOD/conf/default.conf" ] && . "$MOD/conf/default.conf"
# Start from a neutral OOM score: a shell started from an ssh session inherits the protected value of its daemon (see protect_host), and so would the
# container started from it. Only the processes listed in HOST_PROTECT, kdispd and kgest get the low value, set one by one (oom_guard).
[ "$(cat /proc/$$/oom_score_adj 2>/dev/null)" -lt 0 ] 2>/dev/null && echo 0 > /proc/$$/oom_score_adj 2>/dev/null

# BASE = the program folder (/mnt/us/system/embrith): bin, modules, embrith.raw, data/. SRC = the user folder (/mnt/us/embrith): the install package
# (src/), settings, logs, wallpapers, screenshots, android, STOP. Both are read through /mnt/base-us (see modules/install/update.sh).
BASE=$DIR
SRC=/mnt/base-us/embrith; [ -d /mnt/base-us ] || SRC=/mnt/us/embrith
SET=$SRC/settings; WALL=$SRC/wallpapers; SHOTS=$SRC/screenshots; STOPF=$SRC/STOP
BIN=$DIR/bin
RUNBIN=/var/tmp/embrith-bin
ST=/var/tmp/embrith-state
LOGDIR=$SRC/logs
# DEV_MODE=1 in settings/custom.conf (developer mode): the debug logs are files in $LOGDIR (flash), as before, and update.sh installs whatever is in
# $SRC/src without checking it. Otherwise the logs go to a size-capped folder in memory ($ST/ring, cut by ring_trim) and "embrith.sh report" collects
# the last minutes (Settings > General > Generate log).
DEV_MODE=$(sed -n 's/^DEV_MODE=//p' "$SET/custom.conf" 2>/dev/null | head -n1 | tr -dc '01')
if [ "$DEV_MODE" = 1 ]; then LOGD=$LOGDIR; else LOGD=$ST/ring; fi
FBINK=/mnt/us/libkh/bin/fbink
mkdir -p "$ST" "$LOGDIR" "$LOGD" "$SET" "$WALL" "$SHOTS" "$SRC/android"

for m in core/common core/hwinfo core/devices core/settings core/container core/stop ui/kindle_ui ui/ctl power/sleep net/net net/ssh install/install install/dexopt install/webview diag/diag watchdog/supervise; do  . "$MOD/$m.sh"
done

# Run "start" fully detached from the launcher (see src/kdetach.c): the library starts scripts with the Kindle's own
# user as real uid, and pausing Kindle services could otherwise kill this script, the watchdog and the display daemon.
if [ "$1" = --detached ]; then shift
elif [ "$1" = start ]; then
  mkdir -p "$RUNBIN" && cp "$BIN"/* "$RUNBIN"/ 2>/dev/null && chmod 755 "$RUNBIN"/* 2>/dev/null
  [ -x "$RUNBIN/kdetach" ] && exec "$RUNBIN/kdetach" sh "$DIR/embrith.sh" --detached "$@"
fi

case "$1" in
  start)
    case "$2" in --framework_stop) UI_MODE=stop; set -- start "$3" "$4" ;; esac   # KOReader --framework_stop
    cmd_start "$2" "$3" ;;
  stop)      cmd_stop ;;
  restart)   alive || die "not running"; : > "$K_CTL/restart"; chmod 666 "$K_CTL/restart" 2>/dev/null; say "restart requested: the watchdog restarts Android" ;;
  panic)     cmd_panic ;;
  supervise) cmd_supervise "$2" ;;
  status)    cmd_status ;;
  ctps)      ct_ps ;;
  log)       cmd_log ;;
  report)    report_gen "$2"; report_list; say "report: $LOGDIR/report/" ;;
  logcat)    alive || die "not running"; ct_sh "logcat -d -v time" ;;
  shell)     alive || die "not running"; shift; if [ $# -gt 0 ]; then ct_sh "$*"; else ct_shell; fi ;;
  install)   cmd_install "$2" ;;
  disptest)  cmd_disptest ;;
  icons)     cmd_icons ;;
  fbdiag)    cmd_fbdiag ;;
  shot)      alive || die "not running"; shot; say "saved logs/screen-android.pgm and logs/screen-fb0.pgm" ;;
  launch)    alive || die "not running"; ct_sh "monkey -p $2 -c android.intent.category.LAUNCHER 1" ;;
  *) echo "usage: $0 start|stop|restart|status|log|report [minutes]|logcat|shot|disptest|fbdiag|icons|shell [cmd]|install <apk>|launch <package>"; exit 2 ;;
esac
