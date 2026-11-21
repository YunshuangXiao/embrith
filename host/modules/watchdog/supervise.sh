# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Embrith contributors
# host/watchdog/supervise.sh - background watchdog for a running container (cmd_supervise).
# Sourced by host/embrith.sh.

# Hot update: embrith.raw replaced while the container runs. Stop at once (before Android reads the new bytes and crashes),
# wait until the upload has finished (size unchanged for 10 s, EMBRITH1 header), then start again. Gives up after 15 min.
img_size() { ls -l "$BASE/embrith.raw" 2>/dev/null | awk '{print $5}'; }
img_wait_restart() {
  n=0; same=0; prev=
  while [ $n -lt 450 ]; do
    sleep 2; n=$((n + 1))
    cur=$(img_size)
    if [ -n "$cur" ] && [ "$cur" = "$prev" ]; then same=$((same + 1)); else same=0; fi
    prev=$cur
    if [ $same -ge 5 ]; then
      case "$(head -c 512 "$BASE/embrith.raw" 2>/dev/null | tr -d '\000' | head -n 1)" in
        EMBRITH1\ *) say "hot update: embrith.raw complete ($cur bytes), starting again"
                     ( EMBRITH_FORCE=1 setsid sh "$DIR/embrith.sh" start full "$RUN_LIMIT" </dev/null >>"$LOGDIR/last-run.txt" 2>&1 & )
                     return 0 ;;
      esac
    fi
  done
  say "hot update: embrith.raw not complete after 15 min, not starting"
}

# Background watchdog. Every 2 s only shell builtins run (no process is started): request files in ctl/, exit, STOP,
# hot update, memory, the container. Waiting is "read -t 2" on the FIFO ctl/cmd, so a line written there is handled at once
# (nobody writes to it yet: the 2 s scan of ctl/ stays as the fallback). Every 20 s: idle timer.
# Status for Android (ctl/status) is published on events only: ctl/status_req, a state change (STAT_LAST=0), wake-up,
# and every 60 s without SSH until the launcher asks with status_req itself.
# Snapshots (live.txt) only at moments worth keeping: boot done (100 s), before every stop, memory emergency.
uptime_s() { read -r up _ < /proc/uptime; up=${up%.*}; }       # boot time in seconds, counts suspend too
mem_avail() { MA=; while read -r k v _; do [ "$k" = MemAvailable: ] && { MA=$v; return 0; }; done < /proc/meminfo; }
conf_text() { CT=; while IFS= read -r l; do CT="$CT$l|"; done 2>/dev/null < "$K_CTL/conf"; }
conf_cache() {  # values the watchdog needs every tick, read once per change of ctl/conf
  IDLE_MIN=15
  while IFS='=' read -r k v; do case "$k" in SLEEP_MIN) IDLE_MIN=$v ;; esac; done 2>/dev/null < "$K_CTL/conf"
  case "$IDLE_MIN" in ''|*[!0-9]*) IDLE_MIN=15 ;; esac
}
cmd_supervise() {
  RUN_LIMIT=${1:-$RUN_LIMIT}
  uptime_s; U0=$up; PUP=$up; low=0; bl=0; sl=0; rl=0; STAT_LAST=1
  rm -f "$ST/asleep" "$ST/woke" "$ST/frozen"; rmdir "$ST/sleeplock" 2>/dev/null; rm -f "$ST/wd_last" "$ST/amber_tick" "$ST/usb_shown"
  : > "$ST/woke.seen"
  touch -r "$BASE/embrith.raw" "$ST/img.ref" 2>/dev/null
  ( power_watch ) </dev/null >/dev/null 2>&1 &
  echo $! > "$ST/power.pid"
  batt_sample B
  INITP=$(init_pid)
  rm -f "$K_CTL/cmd"; mkfifo "$K_CTL/cmd" 2>/dev/null && chmod 666 "$K_CTL/cmd" 2>/dev/null
  if [ -p "$K_CTL/cmd" ]; then exec 3<>"$K_CTL/cmd"; else exec 3</dev/null; fi
  conf_text; CT_LAST=$CT; conf_cache; amber_conf
  publish_status
  while [ -n "$INITP" ] && [ -d "/proc/$INITP" ]; do
    c=; read -t 2 -r c <&3 2>/dev/null || [ -n "$c" ] || [ -p "$K_CTL/cmd" ] || sleep 2
    uptime_s; el=$((up - U0))
    # the watchdog did not run for > 20 s while asleep = the device was suspended and has resumed. A resume is not an unlock (RTC alarm,
    # PMIC interrupt, failed suspend): only powerd's outOfScreenSaver ends the sleep (power_event). The watchdog only writes down what it saw.
    if [ -f "$ST/asleep" ] && [ $((up - PUP)) -gt 20 ]; then
      say "watchdog gap $((up - PUP)) s while asleep: resume without unlock, staying locked"
      wake_note "$((up - PUP))"
    fi
    PUP=$up
    if [ -f "$ST/img.ref" ] && [ "$BASE/embrith.raw" -nt "$ST/img.ref" ]; then
      say "hot update: embrith.raw changed, stopping"; snapshot $el; cmd_stop; img_wait_restart; return
    fi
    ctl_requests
    usb_check
    conf_text; if [ "$CT" != "$CT_LAST" ]; then set_sync; conf_text; CT_LAST=$CT; conf_cache; amber_conf; fi
    [ -f "$ST/amber_tick" ] && { rm -f "$ST/amber_tick"; amber_apply; amber_arm; }
    # woke up (sleep_leave runs in the power-event process): warmth may have passed a switch time, status may be old
    [ "$ST/woke" -nt "$ST/woke.seen" ] && { : > "$ST/woke.seen"; amber_apply; amber_arm; STAT_LAST=0; }
    if [ "${STAT_LAST:-0}" = 0 ] || [ $((el - STAT_LAST)) -ge 60 ]; then STAT_LAST=$el; [ "$el" = 0 ] && STAT_LAST=1; publish_status quick; fi
    # follow-up publishes 3 s and 10 s after a change that takes a moment to settle: Wi-Fi / Bluetooth (QDUE, quick), SSH (SDUE, full)
    if [ -n "$QDUE" ] && [ "$el" -ge "${QDUE%% *}" ]; then QDUE=${QDUE#"${QDUE%% *}"}; QDUE=${QDUE# }; publish_status quick; fi
    if [ -n "$SDUE" ] && [ "$el" -ge "${SDUE%% *}" ]; then SDUE=${SDUE#"${SDUE%% *}"}; SDUE=${SDUE# }; publish_status; fi
    if [ -f "$K_CTL/power_menu" ]; then   # top left held: Android takes the file and shows the menu. Still there 4 s after it was first seen: Android does not answer, exit as before
      if [ -z "${PM_SEEN:-}" ]; then PM_SEEN=$el
      elif [ $((el - PM_SEEN)) -ge 4 ]; then say "power menu not taken by Android: exit"; rm -f "$K_CTL/power_menu"; PM_SEEN=; snapshot $el; cmd_stop; return; fi
    else PM_SEEN=; fi
    if [ -f "$K_CTL/kindle_reboot" ]; then say "Kindle reboot requested from Android"; rm -f "$K_CTL/kindle_reboot"; snapshot $el; cmd_stop; sync; say "rebooting the Kindle"; { reboot || /sbin/reboot || busybox reboot; } >/dev/null 2>&1; return; fi
    if [ -f /var/tmp/kdisp/ctl/exit ]; then say "exit requested from Android (launcher button, or bottom right corner held)"; rm -f /var/tmp/kdisp/ctl/exit; snapshot $el; cmd_stop; return; fi
    if [ -f "$STOPF" ]; then
      say "STOP file found"; rm -f "$STOPF"; snapshot $el
      if [ "${RESTART_REQ:-0}" -ge 1 ]; then : > "$ST/restarting"; fi      # restart from Android: keep kdispd, kgest and the paused Kindle UI (cmd_stop and the next start read this marker)
      cmd_stop
      if [ "$RESTART_REQ" = 2 ]; then say "update before starting again"; sh "$MOD/install/update.sh" --restart >>"$LOGDIR/last-run.txt" 2>&1; RESTART_REQ=1; fi
      if [ "$RESTART_REQ" = 3 ]; then say "forced update before starting again"; sh "$MOD/install/update.sh" --restart-force >>"$LOGDIR/last-run.txt" 2>&1; RESTART_REQ=1; fi
      if [ "$RESTART_REQ" = 4 ]; then say "update from the GitHub release before starting again"; sh "$MOD/install/update.sh" --restart-net >>"$LOGDIR/last-run.txt" 2>&1; RESTART_REQ=1; fi
      if [ "$RESTART_REQ" = 1 ]; then RESTART_REQ=0; say "starting again"; ( EMBRITH_FORCE=1 setsid sh "$DIR/embrith.sh" start full "$RUN_LIMIT" </dev/null >>"$LOGDIR/last-run.txt" 2>&1 & ); fi
      return
    fi
    if [ "$RUN_LIMIT" -gt 0 ] && [ "$el" -ge "$RUN_LIMIT" ]; then say "run limit ${RUN_LIMIT}s reached"; snapshot $el; cmd_stop; return; fi
    [ "$el" -ge 8 ] && [ ! -f "$ST/selftest.done" ] && { : > "$ST/selftest.done"; selftest; }
    [ "$el" -ge 100 ] && [ ! -f "$ST/homediag.done" ] && { : > "$ST/homediag.done"; homediag; snapshot $el; }
    mem_avail
    if [ "${MA:-999999}" -lt 40000 ]; then low=$((low+1)); else low=0; fi
    if [ "$low" -ge 7 ]; then say "memory critical (${MA} kB available): emergency stop"; snapshot $el; report_auto; cmd_stop; return; fi
    if [ $((el - sl)) -ge 20 ]; then sl=$el; idle_check "$up"; fi
    if [ $((el - rl)) -ge 60 ]; then rl=$el; ring_trim; fi
    if [ $((el - bl)) -ge 60 ] && [ ! -f "$ST/asleep" ] && [ ! -d "$ST/sleeplock" ]; then bl=$el; batt_sample A; fi
  done
  say "container ended by itself"; report_auto; cmd_stop
}
