# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Embrith contributors
# host/core/stop.sh - stop the container and give the Kindle back its state (cmd_stop).
# Sourced by host/embrith.sh.
#
# Referenced projects (their licenses stay with them):
#  - KOReader, AGPL-3.0, https://github.com/koreader/koreader (COPYING in that project). Files consulted:
#      frontend/ui/uimanager.lua            SaveState (the settings table is written at stop)
#  - VeraDroid, GPL-3.0-or-later, Copyright (C) 2026 the VeraDroid contributors. File consulted:
#      host/pw6/boot/stop.sh                the "restarting" marker file

# Power-event watcher and warmth timer of this session ($ST/*.pid), and every "lipc-wait-event -m com.lab126.powerd
# goingToScreenSaver..." (also of earlier sessions; this busybox may have no pkill). The Kindle's own lipc-wait-event stay.
stop_power_watchers() {
  for f in power amber; do [ -f "$ST/$f.pid" ] && { kill "$(cat "$ST/$f.pid")" 2>/dev/null; rm -f "$ST/$f.pid"; }; done
  for d in /proc/[0-9]*; do
    c=; read -r c 2>/dev/null < "$d/comm" || continue
    [ "$c" = lipc-wait-event ] || continue
    case "$(tr '\0' ' ' < "$d/cmdline" 2>/dev/null)" in *"-m com.lab126.powerd goingToScreenSaver"*) kill "${d#/proc/}" 2>/dev/null ;; esac
  done
}

# Leftovers of this and of earlier sessions: power watchers, every "embrith.sh supervise" shell (old watchdogs, their power
# subshells and background jobs; never this shell or its parent), inactive SSH sessions (ssh-toggle.sh clean keeps the
# session this runs in). $1 = idle: embrith is not running, so also its daemons (kdispd, kgest, slirp4netns) without pid files,
# processes left in the freezer group, state files, and the host settings saved at start (sysrq, oom, vm, core_pattern).
cleanup_strays() {
  stop_power_watchers
  n=0
  for d in /proc/[0-9]*; do
    p=${d#/proc/}; [ "$p" = "$$" ] || [ "$p" = "$PPID" ] && continue
    c=; read -r c 2>/dev/null < "$d/comm" || continue
    case "$c" in
      sh) case "$(tr '\0' ' ' < "$d/cmdline" 2>/dev/null)" in *"embrith.sh supervise"*) kill "$p" 2>/dev/null && n=$((n + 1)) ;; esac ;;
      kdispd|kgest|slirp4netns) [ "$1" = idle ] && kill "$p" 2>/dev/null && n=$((n + 1)) ;;
    esac
  done
  [ "$n" -gt 0 ] && say "cleanup: $n leftover processes ended"
  if [ "$1" = idle ]; then
    [ "$(fz_tasks)" -gt 0 ] && { fz_drain 10 || say "WARNING: $(fz_tasks) container processes still alive"; }
    sleep_state_clear
    rm -f "$ST/init.pid" "$ST/kdispd.pid" "$ST/kgest.pid" "$ST/slirp.pid" "$ST/stopping" "$ST/restarting" "$K_CTL/cmd"
    unprotect_host
  fi
  ssh_clean
}

cmd_stop() {
  # embrith is not running (no container, no display daemon, Kindle UI not paused by us): only clean up. If the Kindle UI
  # itself is down (an earlier session that did not end cleanly), bring it back too.
  if ! alive && [ ! -f "$ST/init.pid" ] && [ ! -f "$ST/kdispd.pid" ] && [ ! -f "$ST/ui_paused" ]; then
    cleanup_strays idle
    if initctl status lab126_gui 2>/dev/null | grep -q start/; then say "not running: cleaned up"
    else say "not running, Kindle UI is down: starting it"; echo stop > "$ST/ui_paused"; ui_resume; fi
    return 0
  fi
  # "returning to Kindle" page first (kdispd draws it at once), then the real shutdown. Also used by the "exit embrith" button.
  date +%s > "$ST/stopping" 2>/dev/null
  set_sync; app_count_refresh; set_flush   # KOReader SaveState: write the table at stop
  # the power-event process and the warmth timer belong to this session: a restart would otherwise keep the old ones
  # (two power watchers handle every powerd event twice)
  stop_power_watchers
  # always thaw: a stop may come while Android is frozen or half way into sleep (no asleep file yet)
  thaw_android; sleep_state_clear
  # Restart from Android: the supervisor wrote $ST/restarting before calling cmd_stop. The helper processes and the container go
  # away and are started again, but the Kindle UI stays paused and the host protection stays. No "returning to Kindle" page, the
  # panel never shows the Kindle in between. The marker is removed by the next start (container.sh).
  # Same approach as VeraDroid boot/stop.sh ("restarting" marker file, GPL-3.0).
  RS=; [ -f "$ST/restarting" ] && RS=1
  if [ -z "$RS" ] && [ -f "$ST/kdispd.pid" ] && [ -d "$K_CTL" ]; then touch "$K_CTL/exiting" 2>/dev/null; sleep 1; fi
  [ "$DEV_MODE" = 1 ] && cmd_log > "$LOGDIR/last-stop-dmesg.txt" 2>/dev/null
  sync
  if alive; then
    # No "sys.powerctl shutdown": that goes through android_reboot (remount read-only). A PID-namespace init ignores
    # SIGTERM from outside, SIGKILL makes the kernel take down every process of the container.
    say "stopping the container"
    kill -9 "$(init_pid)" 2>/dev/null
    n=0; while alive && [ $n -lt 10 ]; do sleep 1; n=$((n+1)); done
  fi
  # init gone is not enough: wait until no process of the container is left (they still hold the system loop device)
  fz_drain 30 || say "WARNING: $(fz_tasks) container processes still alive after 30 s"
  rm -f "$ST/init.pid"
  if [ -f "$ST/devkmsg" ]; then echo "$(cat "$ST/devkmsg")" > /proc/sys/kernel/printk_devkmsg 2>/dev/null; rm -f "$ST/devkmsg"; fi
  if [ -f "$ST/loop" ]; then
    L=$(cat "$ST/loop")
    losetup -d "$L" 2>/dev/null || say "WARNING: could not detach $L (still in use)"
    rm -f "$ST/loop"
  fi
  rm -rf "$DEVDIR"
  [ -f "$ST/slirp.pid" ] && { kill -TERM "$(cat "$ST/slirp.pid")" 2>/dev/null; rm -f "$ST/slirp.pid"; }
  [ -f "$ST/kgest.pid" ] && { kill -TERM "$(cat "$ST/kgest.pid")" 2>/dev/null; rm -f "$ST/kgest.pid"; }
  if [ -f "$ST/kdispd.pid" ]; then
    kill -TERM "$(cat "$ST/kdispd.pid")" 2>/dev/null; sleep 2; rm -f "$ST/kdispd.pid"
  fi
  date +%s > "$ST/stopped_at" 2>/dev/null
  if [ -n "$RS" ]; then
    say "restart: the Kindle UI stays paused"
  else
  if [ "$(cat "$ST/ui_paused" 2>/dev/null)" = stop ]; then
    # framework stopped: the exit page stays on the panel until lab126_gui draws the Kindle home over it
    :
  else
    # pause mode: full flashing clear, so the exit page leaves no ghost under the Kindle UI
    [ -x "$FBINK" ] && "$FBINK" -q -c -f 2>/dev/null
  fi
  ui_resume
  unprotect_host
  if [ -f "$ST/passcode_disabled" ]; then touch /var/local/system/userpasswdenabled; rm -f "$ST/passcode_disabled"; say "system passcode restored"; fi
  [ -x "$RUNBIN/kutil" ] && grep -q zram0 /proc/swaps && "$RUNBIN/kutil" swapoff /dev/zram0
  fi
  PL="$MOD/lib/pack-logs.sh"   # 生成 logs/diag.txt
  [ "$DEV_MODE" = 1 ] && [ -f "$PL" ] && EMBRITH_DIR=$BASE EMBRITH_SET=$SET sh "$PL" -d "$LOGDIR" >> "$LOGDIR/last-run.txt" 2>&1
  cleanup_strays   # old watchdogs and their jobs, inactive SSH sessions
  rm -f "$ST/stopping"
  say "stopped"
}
