# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Embrith contributors
# host/ui/kindle_ui.sh - Kindle UI: pause/resume (lab126_gui stays running), host protection, icons and powerd properties.
# Sourced by host/embrith.sh.
#
# Referenced projects (their licenses stay with them):
#  - KOReader, AGPL-3.0, https://github.com/koreader/koreader (COPYING in that project). Files consulted:
#      platform/kindle/koreader.sh          pillow disableEnablePillow, STOP of awesome and volumd, wait after stopping lab126_gui,
#                                           appmgrd start of the home booklet
#      frontend/device/kindle/device.lua    frameworkStopped(): unloading of the blanket splash and screensaver modules
#  - VeraDroid, GPL-3.0-or-later, Copyright (C) 2026 the VeraDroid contributors. Files consulted, code adapted:
#      host/pw6/boot/lib/memory.sh          swappiness 40 with compressed swap on a ~1 GB device; the read_ahead_kb raise of the
#                                           eMMC and loop devices (protect_host: same loop, READAHEAD_KB default 128, readahead.saved)
#                                           Adapted from readahead_on(): the read_ahead_kb loop in protect_host (device list, checks,
#                                           readahead.saved format). Copyright (C) 2026 the VeraDroid contributors remains on that part.

# ---- Kindle UI: pause (not stop) the way KOReader does it on FW 5.x ----
# Source of the sequence: KOReader platform/kindle/koreader.sh (AGPL-3.0, public): pillow disableEnablePillow disable (FW >= 5.6.5),
# killall -STOP awesome (FW >= 5.7.2), killall -STOP volumd; on exit: -CONT volumd, -CONT awesome, pillow enable, appmgrd start home.
# pillow (status bar, popups) is switched off and the window manager + volume daemon are frozen, so the Kindle
# stops drawing on the screen while Android runs. The framework itself keeps running (power key, sleep handling).
kindle_home() {   # back to the Kindle home screen: the same single call KOReader makes when it hands the screen back
  lipc-set-prop com.lab126.appmgrd start app://com.lab126.booklet.home 2>/dev/null
}

ui_pause() {
  case "$UI_MODE" in
    stop)     # the whole Kindle framework (X, awesome, pillow, the Java framework) is stopped: more RAM, no touches reach it
      if initctl status lab126_gui 2>/dev/null | grep -q start/; then
        lipc-set-prop -s com.lab126.blanket unload splash 2>/dev/null; lipc-set-prop -s com.lab126.blanket unload screensaver 2>/dev/null   # KOReader frameworkStopped()
        stop lab126_gui >/dev/null 2>&1; usleep 1250000 2>/dev/null || sleep 2   # KOReader: wait for the framework teardown
        killall -STOP awesome 2>/dev/null   # awesome survives the stop and draws the Kindle's own USB page; volumd keeps running (MTP needs it)
        # x stays running on purpose: on the Colorsoft (FW 5.18.6) com.lab126.blanket disappears once x is stopped, powerd then
        # logs "Splash screen is on. Ignoring power button" and sleep does nothing (on-device test; the cause is not proven)
        echo stop > "$ST/ui_paused"; say "Kindle framework stopped (lab126_gui)"
      elif initctl status lab126_gui 2>/dev/null | grep -q stop/; then
        # already stopped (an earlier session or a test left it so): the same state as after our own stop. Pause would leave nobody to
        # answer powerd's frameworkStarted (the power key is ignored) and ui_resume would never start lab126_gui again.
        killall -STOP awesome 2>/dev/null
        echo stop > "$ST/ui_paused"; say "Kindle framework was already stopped (lab126_gui)"
      else say "no upstart job lab126_gui, falling back to pause"; UI_MODE=pause; ui_pause; fi
      return 0 ;;
    pause) ;;
    *) return 0 ;;
  esac
  cat /dev/fb0 > "$ST/fb.dump" 2>/dev/null   # KOReader: dump the fb first, so something useful can be put back on resume
  lipc-set-prop com.lab126.pillow disableEnablePillow disable 2>/dev/null   # KOReader: "Disabling pillow" (FW >= 5.6.5)
  killall -STOP awesome volumd 2>/dev/null   # KOReader: "Stopping awesome" (FW >= 5.7.2) and "Stopping volumd"
  : > "$ST/services.stopped"
  for s in $UI_SERVICES; do
    initctl status "$s" 2>/dev/null | grep -q start/ && { echo "$s" >> "$ST/services.stopped"; stop "$s" >/dev/null 2>&1; }
  done
  echo pause > "$ST/ui_paused"
  say "Kindle UI paused"
}

ui_resume() {
  usb_rearm
  if [ -f "$ST/x_stopped" ]; then
    start x >/dev/null 2>&1
    rm -f "$ST/x_stopped"
  fi
  [ -f "$ST/ui_paused" ] || return 0
  if [ "$(cat "$ST/ui_paused" 2>/dev/null)" = stop ]; then
    killall -CONT awesome volumd 2>/dev/null
    rm -f "$ST/ui_paused"; lipc-set-prop -s com.lab126.blanket load screensaver 2>/dev/null; start lab126_gui >/dev/null 2>&1; say "Kindle framework started again (lab126_gui)"; return 0
  fi
  rm -f "$ST/ui_paused"
  killall -CONT awesome volumd 2>/dev/null
  [ -s "$ST/fb.dump" ] && cat "$ST/fb.dump" > /dev/fb0 2>/dev/null   # KOReader: put the saved screen back before pillow and home redraw
  rm -f "$ST/fb.dump"
  lipc-set-prop com.lab126.pillow disableEnablePillow enable 2>/dev/null
  kindle_home
  for s in $(cat "$ST/services.stopped" 2>/dev/null); do start "$s" >/dev/null 2>&1; done
  rm -f "$ST/services.stopped"
  initctl status lab126_gui 2>/dev/null | grep -q stop/ && { start lab126_gui >/dev/null 2>&1; say "lab126_gui was stopped, started again"; }
  say "Kindle UI resumed"
}

# ---- USB: Embrith's own full-screen page (UsbActivity) instead of the Kindle's ----
# A computer that enumerates the Kindle puts the USB controller into "configured"; a charger never does. MTP keeps running
# (/mnt/us stays mounted), so files can be copied while the page is up. The button "弹出连接" (ctl/usb_eject) takes the
# controller off the bus (soft_connect): the cable only charges from then on. Without soft_connect the mtp job is stopped
# instead, in the background: with a host session open that can block. Once the cable is pulled (powerd isCharging = 0)
# the change is undone. State: $ST/usb_shown (the page is up), $ST/usb_ejected (content: udc or mtp = what was done).
usb_configured() {    # shell builtins only: called on every watchdog tick
  for u in /sys/class/udc/*/state; do
    [ -r "$u" ] || continue
    read -r s < "$u" && [ "$s" = configured ] && return 0
  done
  return 1
}

usb_note() { : > "$K_CTL/$1" && chmod 666 "$K_CTL/$1" 2>/dev/null; }    # ctl/usb, ctl/usb_off: taken by StatusService

usb_rearm() {
  rm -f "$ST/usb_shown"
  [ -f "$ST/usb_ejected" ] || return 0
  case "$(cat "$ST/usb_ejected" 2>/dev/null)" in
    udc) for u in /sys/class/udc/*/soft_connect; do [ -w "$u" ] && echo connect > "$u" 2>/dev/null; done ;;
    mtp) start mtp >/dev/null 2>&1 ;;
  esac
  rm -f "$ST/usb_ejected"; say "usb: data connection available again"
}

usb_eject() {
  m=
  for u in /sys/class/udc/*/soft_connect; do [ -w "$u" ] && echo disconnect > "$u" 2>/dev/null && m=udc; done
  if [ -z "$m" ] && initctl status mtp 2>/dev/null | grep -q start/; then
    ( timeout 10 stop mtp ) </dev/null >/dev/null 2>&1 &
    m=mtp
  fi
  echo "$m" > "$ST/usb_ejected"; rm -f "$ST/usb_shown"; USB_T=${el:-0}
  say "usb: ejected (${m:-nothing to disconnect})"
}

usb_check() {
  [ -f "$ST/asleep" ] || [ -d "$ST/sleeplock" ] && return 0
  if [ -f "$ST/usb_ejected" ]; then      # wait for the cable to be pulled: one lipc call every 6 s, only in this state
    [ $((${el:-0} - ${USB_T:-0})) -ge 6 ] || return 0
    USB_T=${el:-0}; charging || usb_rearm
    return 0
  fi
  if usb_configured; then
    [ -f "$ST/usb_shown" ] || { : > "$ST/usb_shown"; usb_note usb; say "usb: computer connected, page shown"; }
  elif [ -f "$ST/usb_shown" ]; then
    rm -f "$ST/usb_shown"; usb_note usb_off; say "usb: disconnected, page closed"
  fi
}

# oom_guard PID NAME: lower the OOM score of one host process and remember the old value in oom.saved ("pid old name"; unprotect_host reads it)
oom_guard() {
  [ -n "$OOM_ADJ" ] && [ -w "/proc/$1/oom_score_adj" ] || return 0
  grep -q "^$1 " "$ST/oom.saved" 2>/dev/null && return 0   # already guarded (restart path)
  echo "$1 $(cat /proc/$1/oom_score_adj) $2" >> "$ST/oom.saved"; echo "$OOM_ADJ" > "/proc/$1/oom_score_adj"
}

# ---- keep the Kindle alive while Android is hungry ----
# - when memory runs out the kernel's OOM killer must pick Android, not the Kindle's power/Wi-Fi/SSH daemons
# - crash dumps of paused Kindle services would be written into the 64MB /var tmpfs and fill it: throw them away
# - compressed swap: swappiness 40 (as veradroid on a ~1 GB Kindle: 100 also swaps out pages of the running app), no swap read-ahead
# - storage read-ahead raised (4 KB makes every page fault of an app's odex/apk a single-page read); the old values are restored on stop
protect_host() {
  # Android's reboot/shutdown path (android_reboot, also reached by init's "Security failure") first asks the kernel to
  # remount all filesystems read-only through /proc/sysrq-trigger. SysRq is global, a container does not isolate it:
  # the whole Kindle ends up read-only until the next reboot. Switch SysRq off for the duration of the run when present
  # (kernels built without CONFIG_MAGIC_SYSRQ, e.g. Kindle Scribe 4.9.77, cannot trigger SysRq at all).
  if [ -e /proc/sys/kernel/sysrq ]; then
    cat /proc/sys/kernel/sysrq > "$ST/sysrq.saved" 2>/dev/null
    echo 0 > /proc/sys/kernel/sysrq 2>/dev/null
    [ "$(cat /proc/sys/kernel/sysrq 2>/dev/null)" = 0 ] || { die "cannot disable SysRq: refusing to start (Android could turn the Kindle's filesystems read-only)"; }
  fi
  : > "$ST/oom.saved"
  # only the Kindle processes Embrith itself talks to (HOST_PROTECT and OOM_ADJ in conf/default.conf, no second copy here); kdispd and kgest start later and are guarded by container.sh
  for n in $HOST_PROTECT; do
    for p in $(pidof "$n" 2>/dev/null); do oom_guard "$p" "$n"; done
  done
  cat /proc/sys/kernel/core_pattern > "$ST/core_pattern" 2>/dev/null; echo '|/bin/true' > /proc/sys/kernel/core_pattern 2>/dev/null
  echo "$(cat /proc/sys/vm/swappiness) $(cat /proc/sys/vm/page-cluster)" > "$ST/vm.saved" 2>/dev/null
  echo "${VM_SWAPPINESS:-40}" > /proc/sys/vm/swappiness 2>/dev/null; echo 0 > /proc/sys/vm/page-cluster 2>/dev/null
  : > "$ST/readahead.saved"
  for b in /sys/block/mmcblk0 /sys/block/loop[0-9]*; do
    f=$b/queue/read_ahead_kb
    [ -w "$f" ] || continue
    case "$b" in */loop*) [ -s "$b/loop/backing_file" ] || continue ;; esac
    old=$(cat "$f" 2>/dev/null); [ -n "$old" ] || continue
    [ "$old" -lt "${READAHEAD_KB:-128}" ] 2>/dev/null || continue
    echo "$b $old" >> "$ST/readahead.saved"; echo "${READAHEAD_KB:-128}" > "$f" 2>/dev/null
  done
  sync; echo 3 > /proc/sys/vm/drop_caches 2>/dev/null
}

unprotect_host() {
  if [ -f "$ST/sysrq.saved" ]; then
    [ -e /proc/sys/kernel/sysrq ] && cat "$ST/sysrq.saved" > /proc/sys/kernel/sysrq 2>/dev/null
    rm -f "$ST/sysrq.saved"
  fi
  [ -f "$ST/oom.saved" ] && while read -r p v n; do [ -n "$p" ] && echo "$v" > "/proc/$p/oom_score_adj" 2>/dev/null; done < "$ST/oom.saved"; rm -f "$ST/oom.saved"
  [ -f "$ST/core_pattern" ] && { cat "$ST/core_pattern" > /proc/sys/kernel/core_pattern 2>/dev/null; rm -f "$ST/core_pattern"; }
  if [ -f "$ST/readahead.saved" ]; then
    while read -r b old; do [ -n "$b" ] && [ -w "$b/queue/read_ahead_kb" ] && echo "$old" > "$b/queue/read_ahead_kb" 2>/dev/null; done < "$ST/readahead.saved"
    rm -f "$ST/readahead.saved"
  fi
  if [ -f "$ST/vm.saved" ]; then read -r sw pc < "$ST/vm.saved"; echo "$sw" > /proc/sys/vm/swappiness 2>/dev/null; echo "$pc" > /proc/sys/vm/page-cluster 2>/dev/null; rm -f "$ST/vm.saved"; fi
}

# ---- the Kindle's own icons for KindleHome: icons.map (embrith folder) lines "name=/path/on/the/Kindle.png", names in Icons.java
# (wifi wifi_off bluetooth bluetooth_off settings sleep invert home brightness exit plus back close refresh more). Copied to
# /var/tmp/kdisp/icons (= /dev/kdisp/icons in the container). Without the map, KindleHome draws its own. `embrith.sh icons` lists candidates.
icons_prepare() {
  D=/var/tmp/kdisp/icons; rm -rf "$D"; mkdir -p "$D"
  M="$BASE/icons.map"; [ -f "$M" ] || M="$DIR/icons.map"
  [ -f "$M" ] || { say "no icons.map: KindleHome draws its own icons"; return 0; }
  n=0
  while IFS='=' read -r k p; do
    case "$k" in ''|\#*) continue ;; esac
    k=$(echo "$k" | tr -dc 'a-z0-9_'); p=$(echo "$p" | tr -d '\r')
    if [ -f "$p" ]; then cp "$p" "$D/$k.png" && n=$((n+1)); else say "icon $k: not found ($p)"; fi
  done < "$M"
  chmod 755 "$D"; chmod 644 "$D"/* 2>/dev/null
  say "Kindle icons: $n copied from $M"
}

cmd_icons() {         # candidates for icons.map, and the properties of powerd -> logs/icons-found.txt, logs/lipc-powerd.txt
  L="$LOGDIR/icons-found.txt"; mkdir -p "$LOGDIR"
  { echo "# embrith.sh icons $(date)"
    for r in /usr/share /app /opt; do find "$r" -iname '*.png' 2>/dev/null; done \
      | grep -iE 'wifi|wireless|bluetooth|batt|light|bright|warm|amber|setting|gear|sync|airplane|lock|home|sleep|refresh|chevron|arrow|close|back|plus|add|more' \
      | head -n 500; } > "$L"
  timeout 10 lipc-probe -v com.lab126.powerd > "$LOGDIR/lipc-powerd.txt" 2>&1
  say "$(grep -vc '^#' "$L") icon files listed in $L; powerd properties in $LOGDIR/lipc-powerd.txt"
}

# powerd property name for "auto brightness" differs between firmwares: conf/default.conf FL_AUTO_PROP wins, otherwise the name is
# guessed from `lipc-probe -v`. Empty = not available (the panel shows it greyed out).
lipc_props_detect() {
  P=$(timeout 10 lipc-probe -v com.lab126.powerd 2>/dev/null | tr -s ' \t:,' '\n\n\n\n' | grep -E '^[A-Za-z][A-Za-z0-9_]+$' | sort -u)
  fa=${FL_AUTO_PROP:-$(echo "$P" | grep -iE 'auto.?bright|bright.?auto|auto.?(fl|light)|(fl|light).?auto' | grep -viE '^als' | head -n 1)}
  printf 'FL_AUTO_PROP=%s\n' "$fa" > "$ST/lipcprops"
  say "powerd: auto brightness '${fa:-none}' (FL_AUTO_PROP in conf/default.conf overrides)"
}

# last resort: give the Kindle its UI back even if the state files are gone
cmd_panic() {
  touch "$ST/ui_paused"; ui_resume
  initctl status lab126_gui 2>/dev/null | grep -q stop/ && start lab126_gui >/dev/null 2>&1
  killall -CONT awesome volumd 2>/dev/null
  lipc-set-prop com.lab126.pillow disableEnablePillow enable 2>/dev/null
  say "panic: UI resumed"
}
