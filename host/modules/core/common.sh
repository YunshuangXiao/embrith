# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Embrith contributors
# host/core/common.sh - helpers shared by every module: logging, container access, small utilities.
# Sourced by host/embrith.sh. Needs ST (state dir) from embrith.sh.

say() { echo "[embrith] $(date +%H:%M:%S) $*"; }

die() { echo "[embrith] ERROR: $*" >&2; exit 1; }

init_pid() { [ -f "$ST/init.pid" ] && cat "$ST/init.pid"; }

alive() { p=$(init_pid); [ -n "$p" ] && [ -d "/proc/$p" ]; }

in_ct() { nsenter -t "$(init_pid)" -m -u -i -n -p -r -w -- "$@"; }

ct_sh() {
  # pm/am/settings are app_process wrappers: they need the same env init gives to services
  BCP=$(sed -n 's/.*export BOOTCLASSPATH //p' "/proc/$(init_pid)/root/init.environ.rc" 2>/dev/null | head -n1)
  in_ct /system/bin/sh -c "export ANDROID_ROOT=/system ANDROID_DATA=/data ANDROID_ASSETS=/system/app ANDROID_STORAGE=/storage EXTERNAL_STORAGE=/sdcard PATH=/system/bin:/system/xbin BOOTCLASSPATH=$BCP; $*"
}

# Interactive shell in the container with the same environment as ct_sh (plus /vendor/bin).
ct_shell() {
  BCP=$(sed -n 's/.*export BOOTCLASSPATH //p' "/proc/$(init_pid)/root/init.environ.rc" 2>/dev/null | head -n1)
  in_ct /system/bin/sh -c "export ANDROID_ROOT=/system ANDROID_DATA=/data ANDROID_ASSETS=/system/app ANDROID_STORAGE=/storage EXTERNAL_STORAGE=/sdcard PATH=/system/bin:/system/xbin:/vendor/bin BOOTCLASSPATH=$BCP; exec /system/bin/sh"
}

# Install an apk that was picked in the launcher (embrith > more > General > Install app, or the "+" on an empty desktop).
# The launcher sees the Kindle's user storage as /dev/kindle; the answer goes to ctl/install_result: busy / ok / fail: <reason>
ct_sh_t() {            # like ct_sh, with a time limit in seconds: ct_sh_t 240 'pm install ...'
  t=$1; shift
  BCP=$(sed -n 's/.*export BOOTCLASSPATH //p' "/proc/$(init_pid)/root/init.environ.rc" 2>/dev/null | head -n1)
  timeout "$t" nsenter -t "$(init_pid)" -m -u -i -n -p -r -w -- /system/bin/sh -c "export ANDROID_ROOT=/system ANDROID_DATA=/data ANDROID_ASSETS=/system/app ANDROID_STORAGE=/storage EXTERNAL_STORAGE=/sdcard PATH=/system/bin:/system/xbin BOOTCLASSPATH=$BCP; $*"
}

ct_sh_long() {
  BCP=$(sed -n 's/.*export BOOTCLASSPATH //p' "/proc/$(init_pid)/root/init.environ.rc" 2>/dev/null | head -n1)
  in_ct_long /system/bin/sh -c "export ANDROID_ROOT=/system ANDROID_DATA=/data ANDROID_ASSETS=/system/app ANDROID_STORAGE=/storage EXTERNAL_STORAGE=/sdcard PATH=/system/bin:/system/xbin BOOTCLASSPATH=$BCP; $*"
}

in_ct_long() { timeout 25 nsenter -t "$(init_pid)" -m -u -i -n -p -r -w -- "$@"; }

in_ct_t() { timeout 5 nsenter -t "$(init_pid)" -m -u -i -n -p -r -w -- "$@"; }

# ---- warmth schedule (our own, like the Kindle's "schedule"): ctl/conf AMBER_SCHED=1, AMBER_ON / AMBER_OFF = HHMM, AMBER_LEVEL 0..24.
# Inside the window the warmth is set to AMBER_LEVEL (the level before is kept in $ST/amber_prev and restored when the window ends).
conf_val() { sed -n "s/^$1=//p" "$K_CTL/conf" 2>/dev/null | tr -dc '0-9'; }
conf_put() { # KEY VALUE: ctl/conf (set_sync moves it into the settings table)
  [ -f "$K_CTL/conf" ] || return 0
  grep -v "^$1=" "$K_CTL/conf" > "$K_CTL/conf.tmp" 2>/dev/null; printf '%s=%s\n' "$1" "$2" >> "$K_CTL/conf.tmp" && mv "$K_CTL/conf.tmp" "$K_CTL/conf"
}

# boot progress for the splash bar (ctl/progress, 0..100)
progress() { [ -d "$K_CTL" ] || return 0; echo "$1" > "$K_CTL/progress.tmp" 2>/dev/null && chmod 666 "$K_CTL/progress.tmp" 2>/dev/null && mv "$K_CTL/progress.tmp" "$K_CTL/progress" 2>/dev/null; }

# processes of the container as seen from the host (compares pid-namespace links; needs no nsenter)
ct_ps() {
  NS=$(readlink "/proc/$(init_pid)/ns/pid" 2>/dev/null)
  [ -n "$NS" ] || { echo "(no container)"; return; }
  for d in /proc/[0-9]*; do
    [ "$(readlink "$d/ns/pid" 2>/dev/null)" = "$NS" ] || continue
    printf '%s %s rss=%skB %s\n' "${d#/proc/}" "$(cat "$d/comm" 2>/dev/null)" "$(awk '/VmRSS/{print $2}' "$d/status" 2>/dev/null)" "$(tr '\0' ' ' < "$d/cmdline" 2>/dev/null | cut -c1-90)"
  done
}

# the Kindle's own kernel chatter (touch, battery, moisture sensor...) would drown Android's init messages
cmd_log() { dmesg | grep -Ev "healthd: battery|detect_moist|moist_det|max20342|cc_int|cc_status|res_int2|Finite/Open|\[ktch\]|peak:|wdtk|bd71827|opt3001" | tail -n 300; }

# The debug logs of the daemons live in memory ($LOGD = $ST/ring) unless DEV_MODE=1, and they are size-limited: a *.log over 160 KB is cut to
# its last 80 KB (in place, same inode: the writers keep appending; the first, cut line is dropped), and the whole folder stays under 1.5 MB.
# Called by the watchdog every minute.
ring_trim() {
  [ "$DEV_MODE" = 1 ] && return 0
  for rt_f in "$LOGD"/*.log; do
    [ -f "$rt_f" ] || continue
    rt_s=$(wc -c < "$rt_f" 2>/dev/null); [ "${rt_s:-0}" -gt 163840 ] || continue
    tail -c 81920 "$rt_f" | sed 1d > "$rt_f.cut" && cat "$rt_f.cut" > "$rt_f"; rm -f "$rt_f.cut"
  done
  rt_k=$(du -sk "$LOGD" 2>/dev/null | cut -f1)
  if [ "${rt_k:-0}" -gt 1536 ]; then       # still too much: every log to its last 32 KB
    for rt_f in "$LOGD"/*.log; do
      [ -f "$rt_f" ] || continue
      tail -c 32768 "$rt_f" | sed 1d > "$rt_f.cut" && cat "$rt_f.cut" > "$rt_f"; rm -f "$rt_f.cut"
    done
  fi
}

cmd_status() {
  if alive; then
    echo "running, init pid $(init_pid), boot_completed=$(in_ct /system/bin/getprop sys.boot_completed 2>/dev/null | tr -d '\r\n')"
    grep -E "MemFree|MemAvailable|SwapFree" /proc/meminfo
  else echo "not running"; fi
}
