# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Embrith contributors
# host/core/container.sh - start the Android container (cmd_start) and detach the Kindle root from it.
# Sourced by host/embrith.sh.
#
# Referenced project: KOReader, AGPL-3.0, https://github.com/koreader/koreader (COPYING in that project). Files consulted:
#   frontend/device/kindle/device.lua        powerd keeps its own screensaver, goingToScreenSaver / outOfScreenSaver handling
#   frontend/luasettings.lua                 the settings table is read once at start

# WebView switch. Embrith itself uses no WebView; apps do. WEBVIEW in ctl/conf: 0 = old (Chromium 52, AOSP), anything else or unset = new
# (Chromium 119, default). Both stay in the image, webview_apply (modules/install/webview.sh) enables one of them and sets qemu.gles.
# Old: its startup fetches the GPU strings through an EGL pbuffer, our software GL cannot create one and Chromium aborts (WeRead:
#   "gpu_info_collector_android.cc: failed to create a pbuffer surface"). A userdebug system reads /data/local/tmp/webview-command-line:
#   skip the GPU info and do not use the GPU.
# New: aborts on --disable-gpu and needs a GLES 2 context. The stock AOSP EGL loader (ro.kernel.qemu=1) scans /system/lib/egl for
#   processes that start while qemu.gles is 2 (SwiftShader); webview_apply sets it. surfaceflinger loaded its EGL at boot, with 0.
prep_webview() {
  D="$BASE/data/local/tmp"; F="$D/webview-command-line"
  mkdir -p "$D" || return 0
  if [ "$(conf_val WEBVIEW)" = 0 ]; then
     echo "webview --skip-gpu-data-loading --disable-gpu" > "$F"
  else
    echo "webview --enable-logging" > "$F"
  fi
  chmod 644 "$F"
}

# The three system_server crashes of the slim image all happen right after the Zen mode "automatic rules" (schedule / calendar event
# conditions, they use AlarmManager and the calendar provider) get subscribed. Nobody needs them on an e-reader. A notification
# policy file WITHOUT automatic rules makes the system skip all of that. Only written when /data has no such file yet.
prep_zen() {
  D="$BASE/data/system"; F="$D/notification_policy.xml"
  [ -e "$F" ] && return 0
  mkdir -p "$D" && chown 1000:1000 "$BASE/data/system" 2>/dev/null; chmod 775 "$D" 2>/dev/null
  cat > "$F" <<'XML'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<notification-policy version="1">
<zen version="2">
<allow alarms="true" media="true" system="false" calls="true" callsFrom="2" messages="false" messagesFrom="2" reminders="false" events="false" repeatCallers="false" />
</zen>
<ranking version="1">
</ranking>
</notification-policy>
XML
  chown 1000:1000 "$F" 2>/dev/null; chmod 600 "$F" 2>/dev/null
  say "wrote a notification policy without zen automatic rules"
}

# Kindle folders shared with Android (read-only bind mounts under /data/media/0/Kindle, tree kept: documents/weread -> Kindle/documents/weread)
# KINDLE_SHARED_SEL = chosen in Settings, ":"-separated, relative to /mnt/us. KINDLE_SHARED_MOUNTED = what the last start really built (for cleaning).
share_ok() {                      # relative path: no "..", no leading or trailing "/", first level is not system or embrith
  case "$1" in ''|/*|*/|*..*|*//*) return 1 ;; esac
  case "$(printf '%s' "${1%%/*}" | tr 'A-Z' 'a-z')" in system|embrith) return 1 ;; esac
  return 0
}
share_clean() {                   # remove the empty skeleton folders of the last start (rmdir only: nothing is mounted in this namespace)
  (
    IFS=:; set -f
    for p in $(set_get KINDLE_SHARED_MOUNTED ""); do
      share_ok "$p" || continue
      d="$SHARE_ROOT/$p"
      while [ "$d" != "$SHARE_ROOT" ]; do rmdir "$d" 2>/dev/null || break; d=${d%/*}; done
    done
    rmdir "$SHARE_ROOT" 2>/dev/null
  )
  return 0
}
share_prep() {                    # start: clean the old skeleton, check KINDLE_SHARED_SEL, record it in KINDLE_SHARED_MOUNTED first, then build the skeleton; sets KD_SHARES
  SHARE_ROOT=$BASE/data/media/0/Kindle
  share_clean
  oi=$IFS; IFS=:; set -f
  ok=
  for p in $(set_get KINDLE_SHARED_SEL ""); do
    if share_ok "$p"; then ok="${ok:+$ok:}$p"; else say "WARNING: shared folder skipped: $p"; fi
  done
  set_put KINDLE_SHARED_MOUNTED "$ok"; set_flush
  for p in $ok; do
    mkdir -p "$SHARE_ROOT/$p" || continue
    d=$SHARE_ROOT/$p
    while :; do
      chown 1023:1023 "$d"; chmod 770 "$d"
      [ "$d" = "$SHARE_ROOT" ] && break
      d=${d%/*}
    done
  done
  IFS=$oi; set +f
  KD_SHARES=$ok; export KD_SHARES
  [ -z "$ok" ] || say "shared Kindle folders: $ok"
}

cmd_start() {
  # stages: init = Android init only (+logd)   core = + servicemanager/surfaceflinger/healthd/lmkd   full = everything
  STAGE=${1:-init}
  case "$STAGE" in
    init) LIMIT=${2:-60} ;;
    core) LIMIT=${2:-120} ;;
    full) LIMIT=${2:-$RUN_LIMIT} ;;
    *) die "unknown stage '$STAGE' (init|core|full)" ;;
  esac
  # 每次仅启动一次（锁的有效期持续到此次启动返回或失败为止；超过10分钟的锁即视为过期）
  if ! mkdir "$ST/starting" 2>/dev/null; then
    lt=$(stat -c %Y "$ST/starting" 2>/dev/null); case "$lt" in ''|*[!0-9]*) lt=0;; esac
    if [ $(( $(date +%s) - lt )) -lt 600 ]; then say "start ignored: another start is running"; return 0; fi
    say "removing a stale start lock"; rmdir "$ST/starting" 2>/dev/null; mkdir "$ST/starting" 2>/dev/null
  fi
  trap 'rmdir "$ST/starting" 2>/dev/null' EXIT
  alive && { say "already running (init pid $(init_pid))"; rmdir "$ST/starting" 2>/dev/null; return 0; }

  # 退出时那一下点击会被 Kindle 界面收到(落在 embrith 书目上就会再次启动): 刚停止或正在停止时不启动. 重启走 EMBRITH_FORCE=1.  [disabled: touch is grabbed by kgest, the Kindle sees no exit tap]
#  if [ -z "$EMBRITH_FORCE" ]; then
#    now=$(date +%s)
#    t=$(cat "$ST/stopping" 2>/dev/null); case "$t" in ''|*[!0-9]*) t=0;; esac
#    if [ "$t" -gt 0 ] && [ $((now - t)) -lt 120 ]; then say "start ignored: embrith is stopping (EMBRITH_FORCE=1 to override)"; return 0; fi
#    t=$(cat "$ST/stopped_at" 2>/dev/null); case "$t" in ''|*[!0-9]*) t=0;; esac
#    if [ "$t" -gt 0 ] && [ $((now - t)) -lt 20 ]; then say "start ignored: stopped $((now - t))s ago, probably the exit tap (EMBRITH_FORCE=1 to override)"; return 0; fi
#  fi

  say "embrith (exit page, apk install, ssh) stage=$STAGE limit=${LIMIT}s"
  # a previous container that is still going away holds its loop device: clear it first, never start beside it
  if [ "$(fz_tasks)" -gt 0 ]; then
    say "previous container still has $(fz_tasks) processes: killing them"
    fz_drain 15 || die "previous container did not go away ($(fz_tasks) processes left): not starting, try again in a minute or restart the Kindle"
  fi
  sleep_state_clear
  rm -f "$ST/selftest.done" "$ST/homediag.done" "$ST/ct_broken" "$ST/report.busy"
  # 08-package.sh 生成单个文件 embrith.raw: 第 0 扇区是头部文本 "EMBRITH1 SYS_OFF=.. SYS_LEN=.. ROOT_OFF=.. ROOT_LEN=..". 
  # system 是 ext4 镜像(SYS_OFF 处, 长度 SYS_LEN); root 是 tar 字节流(ROOT_OFF 处, 长度 ROOT_LEN), 不解开, 直接从镜像里读. 
  [ -f "$BASE/embrith.raw" ] || die "missing $BASE/embrith.raw"
  HDR=$(head -c 512 "$BASE/embrith.raw" | tr -d '\000' | head -n 1)
  case "$HDR" in EMBRITH1\ *) ;; *) die "$BASE/embrith.raw: no EMBRITH1 header";; esac
  SYS_OFF= SYS_LEN= ROOT_OFF= ROOT_LEN=
  for kv in $HDR; do
    case "$kv" in
      SYS_OFF=*) SYS_OFF=${kv#*=};; SYS_LEN=*) SYS_LEN=${kv#*=};;
      ROOT_OFF=*) ROOT_OFF=${kv#*=};; ROOT_LEN=*) ROOT_LEN=${kv#*=};;
    esac
  done
  for v in "$SYS_OFF" "$SYS_LEN" "$ROOT_OFF" "$ROOT_LEN"; do
    case "$v" in ''|*[!0-9]*) die "bad header in $BASE/embrith.raw";; esac
  done
  emb_root() { tail -c +$((ROOT_OFF + 1)) "$BASE/embrith.raw" | head -c "$ROOT_LEN"; }
  [ -f "$BIN/kdispd" ]      || die "missing $BIN/kdispd (run tools/build.sh on the Mac and copy bin/ to the Kindle)"
  mkdir -p "$RUNBIN" && cp "$BIN"/* "$RUNBIN"/ && chmod 755 "$RUNBIN"/*
  emb_root | tar -tf - 2>/dev/null | grep -q kindle-init-patched \
    || die "embrith.raw: root was built without the SELinux-safe init patch (no kindle-init-patched marker). Refusing to start: Android's init could load a policy into the Kindle kernel."
  grep -q " /mnt/base-us ext4" /proc/mounts && case "$BASE" in /mnt/base-us/*) ;; *) say "WARNING: $BASE is not on /mnt/base-us (ext4); /data needs a real filesystem";; esac

  # 0. Android's init logs through /dev/kmsg; the kernel rate-limits that by default
  if [ -w /proc/sys/kernel/printk_devkmsg ]; then
    cat /proc/sys/kernel/printk_devkmsg | sed 's/.*\[\(.*\)\].*/\1/' > "$ST/devkmsg" 2>/dev/null
    echo on > /proc/sys/kernel/printk_devkmsg 2>/dev/null
  fi

  # 1. compressed swap
  [ "$ZRAM_MB" = auto ] && ZRAM_MB=$(( $(hw_ram_mb) / 2 ))
  if [ "$ZRAM_MB" -gt 0 ] 2>/dev/null && [ -x "$RUNBIN/kutil" ]; then
    # a zram left over from an earlier run keeps its old size (the swap size in /proc/swaps is MB*1024-4 kB): swap it off and size it again
    cur=$(awk '$1=="/dev/zram0"{print $3}' /proc/swaps)
    [ -n "$cur" ] && [ "$cur" != "$((ZRAM_MB * 1024 - 4))" ] && "$RUNBIN/kutil" swapoff /dev/zram0
    grep -q zram0 /proc/swaps || "$RUNBIN/kutil" swap /dev/zram0 "$ZRAM_MB" || say "zram swap failed (continuing)"
  fi

  if [ -f "$ST/restarting" ]; then    # restart from Android: the Kindle UI is still paused and the host is still protected (cmd_stop left them alone)
    rm -f "$ST/restarting"   # only this start is a restart: a later exit must take the normal path in cmd_stop
    [ -f "$ST/ui_paused" ] && UI_MODE=$(cat "$ST/ui_paused" 2>/dev/null)
    # a stale "pause" (written while lab126_gui was already stopped) must not hide that nobody answers frameworkStarted: it is a stop
    if [ "$UI_MODE" = pause ] && initctl status lab126_gui 2>/dev/null | grep -q stop/; then UI_MODE=stop; echo stop > "$ST/ui_paused"; fi
    say "restart: Kindle UI stays paused (UI_MODE=$UI_MODE)"
  else
  # 2. Kindle UI and host protection
  # powerd keeps its own screensaver, as in KOReader: goingToScreenSaver is handled by power/sleep.sh
  # KOReader style: the system passcode holds back outOfScreenSaver until it is typed, which embrith cannot do
  # (it owns all input). Removed while embrith runs, restored by cmd_stop.
  if [ -f /var/local/system/userpasswdenabled ]; then rm -f /var/local/system/userpasswdenabled; : > "$ST/passcode_disabled"; say "system passcode disabled (restored on stop)"; fi
  protect_host
  ui_pause
  fi

  # 3. display daemon
  mkdir -p /var/tmp/kdisp
  SPLASH=""; [ -f "$MOD/assets/splash.raw" ] && SPLASH="--splash $MOD/assets/splash.raw"
  [ -f "$MOD/assets/exit.raw" ] && SPLASH="$SPLASH --exitpage $MOD/assets/exit.raw"
  [ -f "$MOD/assets/restart.raw" ] && SPLASH="$SPLASH --restartpage $MOD/assets/restart.raw"
  set_load   # KOReader: the table is read once, here
  app_count_refresh
  app_count_refresh
  app_count_refresh
  "$RUNBIN/kdispd" --fbink "$FBINK" --conf "$SET/display.conf" --shots "$SHOTS" $SPLASH >> "$LOGD/kdispd.log" 2>&1 &
  icons_prepare
  lipc_props_detect
  echo $! > "$ST/kdispd.pid"
  oom_guard "$(cat "$ST/kdispd.pid")" kdispd   # started after protect_host, so it is guarded here
  n=0; while [ ! -f /var/tmp/kdisp/frame ] && [ $n -lt 50 ]; do sleep 0.1 2>/dev/null || sleep 1; n=$((n+1)); done
  [ -f /var/tmp/kdisp/frame ] || say "WARNING: kdispd did not create the frame file (see logs/kdispd.log)"
  set_restore_ctl   # the saved table wins over kdispd's fresh ctl/conf
  rm -f /var/tmp/kdisp/ctl/exit

  # 3b. gestures + virtual keyboard (BACK/HOME). The keyboard has to exist before the device nodes are copied.
  rm -f /var/tmp/kdisp/keys.ready
  if [ -x "$RUNBIN/kgest" ]; then
    TN0=$(touch_node)
    if [ -n "$TN0" ]; then
      G=""; [ "${TOUCH_GRAB:-1}" = 1 ] && G="--grab"   # exclusive touch: the Kindle sees nothing, Android gets embrith-touch
      [ "$UI_MODE" = stop ] && G="$G --kaf"   # no framework: kgest answers frameworkStarted for powerd
      "$RUNBIN/kgest" --touch "$TN0" --dir /var/tmp/kdisp $G >> "$LOGD/kgest.log" 2>&1 &
      echo $! > "$ST/kgest.pid"
      oom_guard "$(cat "$ST/kgest.pid")" kgest
      n=0; while [ ! -f /var/tmp/kdisp/keys.ready ] && [ $n -lt 30 ]; do sleep 0.1 2>/dev/null || sleep 1; n=$((n+1)); done
      sleep 0.3 2>/dev/null
    fi
  else
    say "no bin/kgest (touch gestures and BACK/HOME keys are off): run tools/build.sh on the Mac again"
  fi

  # 4. system image on a free loop device (11..16 are unused on this firmware)
  LOOP=
  for i in 11 12 13 14 15 16; do
    if losetup -r -o "$SYS_OFF" /dev/loop/$i "$BASE/embrith.raw" 2>/dev/null; then LOOP=/dev/loop/$i; break; fi
  done
  [ -n "$LOOP" ] || die "no free loop device for embrith.raw"
  echo "$LOOP" > "$ST/loop"
  say "system on $LOOP (embrith.raw +$SYS_OFF)"

  # 4a. ext4 loopback /data image when user storage is not ext4 (e.g. VFAT on Scribe)
  if ! grep -q " /mnt/base-us ext4" /proc/mounts && ! grep -q " $BASE/data ext4" /proc/mounts; then
    if [ ! -f "$BASE/data.img" ]; then
      DM=${DATA_IMG_MB:-1024}
      case "$DM" in ''|*[!0-9]*) DM=1024 ;; esac
      say "creating ${DM} MB ext4 data image ($BASE/data.img)"
      dd if=/dev/zero of="$BASE/data.img" bs=1M count="$DM" 2>/dev/null || die "cannot create $BASE/data.img"
      /sbin/mkfs.ext4 -F -q -E nodiscard -m 1 "$BASE/data.img" || die "mkfs.ext4 failed on $BASE/data.img"
    fi
    DLOOP=
    for i in 11 12 13 14 15 16; do
      if losetup /dev/loop/$i "$BASE/data.img" 2>/dev/null; then DLOOP=/dev/loop/$i; break; fi
    done
    [ -n "$DLOOP" ] || die "no free loop device for $BASE/data.img"
    echo "$DLOOP" > "$ST/dataloop"
    mkdir -p "$BASE/data"
    mount -t ext4 -o rw,noatime "$DLOOP" "$BASE/data" || { losetup -d "$DLOOP" 2>/dev/null; rm -f "$ST/dataloop"; die "cannot mount $DLOOP on $BASE/data"; }
    say "data on $DLOOP ($BASE/data.img, ext4)"
  fi
  if [ -f "$BASE/media.img" ] && ! grep -q " $BASE/data/media ext4" /proc/mounts; then
    MLOOP=
    for i in 11 12 13 14 15 16; do
      if losetup /dev/loop/$i "$BASE/media.img" 2>/dev/null; then MLOOP=/dev/loop/$i; break; fi
    done
    [ -n "$MLOOP" ] || die "no free loop device for $BASE/media.img"
    echo "$MLOOP" > "$ST/medialoop"
    mkdir -p "$BASE/data/media"
    mount -t ext4 -o rw,noatime "$MLOOP" "$BASE/data/media" || { losetup -d "$MLOOP" 2>/dev/null; rm -f "$ST/medialoop"; die "cannot mount $MLOOP on $BASE/data/media"; }
    chown 1023:1023 "$BASE/data/media" 2>/dev/null; chmod 770 "$BASE/data/media" 2>/dev/null
    say "media on $MLOOP ($BASE/media.img, ext4)"
  fi

  # 4b. folder for wallpaper / lock screen pictures (visible over USB, mounted into the container as /dev/wallpapers)
  mkdir -p "$WALL" && chmod 755 "$WALL" && chmod -R a+rX "$WALL" 2>/dev/null

  # 5. private copies of the device nodes (Android chmods these, never the Kindle's own)
  make_devices
  KD_BINDER_VER=
  [ -x "$RUNBIN/kutil" ] && [ -e "$DEVDIR/binder" ] && KD_BINDER_VER=$("$RUNBIN/kutil" binder-ver "$DEVDIR/binder" 2>/dev/null | tr -dc '0-9')
  if [ -n "$KD_BINDER_VER" ]; then
    echo "$KD_BINDER_VER" > "$ST/binder_ver"
    say "binder protocol version: $KD_BINDER_VER"
  else
    rm -f "$ST/binder_ver"
  fi

  # 6. namespaces + Android init
  prep_zen
  prep_webview
  freezer_setup || say "WARNING: cgroup freezer unavailable, Android cannot be frozen on sleep"
  share_prep
  # hardware parameters for the container: panel size (framebuffer) and pixel density (model table), see core/hwinfo.sh
  KD_PANEL=$(hw_panel | tr ' ' x); [ -n "$KD_PANEL" ] || { report_auto; cmd_stop; die "cannot read the panel size from the framebuffer"; }
  KD_PPI=$(hw_ppi); [ -n "$KD_PPI" ] && say "panel $KD_PANEL, $KD_PPI ppi" || say "WARNING: panel $KD_PANEL, model $(hw_devcode) not in conf/models.conf, the density of the image stays"
  say "starting Android (log: $LOGD/enter.log)"
  export KD_PANEL="$KD_PANEL" KD_PPI="$KD_PPI" KD_BINDER_VER="$KD_BINDER_VER"
  export KD_BASE="$BASE" KD_SET="$SET" KD_WALL="$WALL" KD_IMG="$BASE/embrith.raw" KD_ROOT_OFF="$ROOT_OFF" KD_ROOT_LEN="$ROOT_LEN" KD_LOOP="$LOOP" KD_STAGE="$STAGE" KD_TOUCH_NAMES="$KD_TOUCH_NAMES" KD_STYLUS_NAMES="$KD_STYLUS_NAMES"
  # the subshell puts itself into the freezer group, then execs unshare: every process of the container is inherited into it
  sh -c 'echo $$ > "$1/tasks" 2>/dev/null; shift; exec "$@"' sh "$FZ" unshare -m -p -i -u -n -f --propagation private \
      sh "$MOD/lib/enter.sh" >> "$LOGD/enter.log" 2>&1 &
  U=$!
  n=0; ip=
  while [ $n -lt 50 ]; do ip=$(pgrep -P "$U" 2>/dev/null | head -n1); [ -n "$ip" ] && break; sleep 0.2 2>/dev/null || sleep 1; n=$((n+1)); done
  [ -n "$ip" ] || { tail -n 20 "$LOGD/enter.log"; report_auto; cmd_stop; die "container did not start"; }
  echo "$ip" > "$ST/init.pid"
  say "container init pid (host view): $ip"
  progress 3
  net_start
  hide_host &
  sh "$DIR/embrith.sh" supervise "$LIMIT" >> "$LOGD/supervise.log" 2>&1 &
  say "watchdog started (limit ${LIMIT}s; create an empty file named STOP in /mnt/us/embrith to stop early)"

  [ "$STAGE" = full ] || { say "stage $STAGE started; the watchdog will stop it after ${LIMIT}s"; return 0; }

  # 7. wait for boot (test mode: also give up early when zygote keeps dying, and stop a little after boot completed)
  # Count zygote deaths since THIS container started, by kernel timestamp. (Counting lines in the ring buffer does not work:
  # earlier runs fill it with the same lines, so new deaths just push old ones out and the difference stays near zero.)
  ZYG_T0=$(cut -d' ' -f1 /proc/uptime)
  zyg_deaths() {
    dmesg 2>/dev/null | awk -v t0="$ZYG_T0" '/init: Service .zygote. \(pid [0-9]+\) killed/ { ts=$0; sub(/^\[ */,"",ts); sub(/\].*/,"",ts); if (ts+0 >= t0+0) n++ } END { print n+0 }'
  }
  t=0
  BOOT_EST=$(cat "$SET/boot_secs" 2>/dev/null); case "$BOOT_EST" in ''|*[!0-9]*) BOOT_EST=45;; esac; [ "$BOOT_EST" -lt 15 ] && BOOT_EST=15   # progress bar scale: the last boot time
  while [ $t -lt "$BOOT_TIMEOUT" ] && { [ "$LIMIT" -le 0 ] || [ $t -lt "$LIMIT" ]; }; do
    alive || { say "container died during boot"; cmd_log | tail -n 40; cmd_stop; return 1; }
    v=$(in_ct /system/bin/getprop sys.boot_completed 2>/dev/null | tr -d '\r\n')
    [ "$v" = 1 ] && break
    zd=$(zyg_deaths)
    if [ "${zd:-0}" -ge 4 ]; then
      say "zygote died $zd times since start (system_server crash loop): stopping so that the Kindle comes back, logs are saved"
      touch "$STOPF"; return 1
    fi
    sleep 5; t=$((t+5)); [ $((t % 30)) -eq 0 ] && say "booting... ${t}s"
    pct=$((t * 100 / BOOT_EST)); [ "$pct" -gt 95 ] && pct=95; progress "$pct"
  done
  if [ "$v" = 1 ]; then
    say "boot completed after ${t}s"
    sh "$MOD/install/update.sh" --commit    # this version started: delete the .old copies kept for the rollback
    [ "$(conf_val NET_UPDATE_AUTO)" = 1 ] && ( sh "$MOD/install/netupdate.sh" auto ) </dev/null >/dev/null 2>&1 &    # auto update: look for a newer GitHub release (at most once per 20 h)
    echo "$t" > "$SET/boot_secs" 2>/dev/null
    progress 100
    # e-ink: no window / transition / animator animations (every animation frame is one more partial refresh, like KOReader has none)
    ( ct_sh_t 30 'settings put global window_animation_scale 0; settings put global transition_animation_scale 0; settings put global animator_duration_scale 0' ) </dev/null >/dev/null 2>&1 &
    if [ "$LIMIT" -gt 0 ] && [ "$LIMIT" -le 300 ] && [ "${STAY_AFTER_BOOT:-60}" -gt 0 ]; then
      say "test mode: stopping ${STAY_AFTER_BOOT:-60}s after boot completed"
      ( sleep "${STAY_AFTER_BOOT:-60}"; touch "$STOPF" ) >/dev/null 2>&1 &
    fi
    ( sleep 25; shot ) >/dev/null 2>&1 &
    # e-ink: no animations, never turn the screen off, portrait, Chinese input method when it is installed
    ct_sh 'settings put global device_provisioned 1; settings put secure user_setup_complete 1
           settings put system screen_off_timeout 2147483647; svc power stayon true
           settings put global window_animation_scale 0; settings put global transition_animation_scale 0; settings put global animator_duration_scale 0
           settings put system accelerometer_rotation 0; settings put system user_rotation 0
           settings put secure show_ime_with_hard_keyboard 1
           settings put global captive_portal_mode 0; settings put global captive_portal_detection_enabled 0
           settings put global activity_manager_constants max_cached_processes=3
           settings put secure install_non_market_apps 1
           pm grant com.embrith.home android.permission.READ_EXTERNAL_STORAGE
           pm grant com.embrith.home android.permission.WRITE_EXTERNAL_STORAGE
           true' >/dev/null 2>&1
    # what is installed (verify that the slim image really is the one running), and switch off leftovers that only cost memory
    [ "$DEV_MODE" = 1 ] && ct_sh 'pm list packages' > "$LOGDIR/packages.txt" 2>&1
    # one-time (kept in /data): switch off components that only cost memory. Components only, never a whole package (see below)
    if [ ! -f "$BASE/data/local/slim-components.done" ]; then
      ct_sh_t 60 'pm disable android.ext.services/.notification.Ranker; pm disable com.android.providers.media/.MediaScannerService; pm disable com.android.providers.media/.MediaScannerReceiver' >/dev/null 2>&1 \
        && touch "$BASE/data/local/slim-components.done"
    fi
    dexopt_restore; rm -f "$K_CTL/dexopt"; dexopt_apply
    webview_apply
    fulldirty_apply
    # (an earlier version switched packages off with 'pm disable-user' here. That is stored in /data and disabling StatementService,
    #  the intent filter verifier, made system_server die on the next boot. The slim system image is the right way to save memory.)
  else
    if [ "$LIMIT" -le 0 ]; then
      say "boot did not complete within ${BOOT_TIMEOUT}s: stopping so that the Kindle UI comes back (DEV_MODE=1 keeps logs/live.txt)"
      report_auto
      touch "$STOPF"
    else
      say "boot did not complete within ${BOOT_TIMEOUT}s (container still running; use: embrith.sh logcat)"
    fi
  fi
}

# After init's early-init the container no longer needs the Kindle's root (/host): detach it, so that nothing running
# inside can see or remount the Kindle's filesystems.
hide_host() {
  w=0
  while [ "$w" -lt 30 ]; do
    alive || return 0
    [ -e "/proc/$(init_pid)/root/host/var/tmp/embrith-bin/kutil" ] && break
    sleep 1
    w=$((w + 1))
  done
  sleep 4
  alive || return 0
  nsenter -t "$(init_pid)" -m -r -- /host/var/tmp/embrith-bin/kutil umount-lazy /host 2>>"$LOGD/enter.log" \
    && say "Kindle's mounts hidden from the container" || say "WARNING: could not hide /host from the container"
}
