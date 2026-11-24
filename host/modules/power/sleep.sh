# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Embrith contributors
# host/power/sleep.sh - sleep, wake and idle. Sourced by embrith.sh; needs ST, BASE, DIR, K_CTL, FBINK, LOGDIR, say from there.
#
# One path for every trigger (menu, idle timer, power key, magnetic cover): anything that wants the device asleep only sends
# powerButton to powerd, which is what KOReader does. powerd then broadcasts goingToScreenSaver: we freeze the container
# (cgroup freezer, memory kept), draw a screensaver picture, and powerd suspends the device natively.
# outOfScreenSaver: thaw the container and ask the display to redraw the Android page.
# suspending: powerd is about to suspend. A frozen container cannot read its uevent sockets, which keeps the kernel wakeup source
# NETLINK active and blocks the suspend, so the container is thawed here (the kernel freezes every process right after).
# This file never reads the input devices: the power key and the magnetic cover are powerd events.
# The idle timer (SLEEP_MIN in ctl/conf, minutes, 0 = never) is ours, as in KOReader's autosuspend: t1_reset keeps
# powerd's own screensaver timer from firing first (powerd's preventScreenSaver is not touched).
#
# Referenced project: KOReader, AGPL-3.0, https://github.com/koreader/koreader (COPYING in that project). Files consulted:
#   frontend/device/kindle/powerd.lua        powerButton, isCharging
#   frontend/device/kindle/device.lua        goingToScreenSaver / outOfScreenSaver events and their order
#   frontend/ui/screensaver.lua              closing of the picture on wake
#   plugins/autosuspend.koplugin/main.lua    idle timer, periodic reset of the powerd t1 timer (every 4 min)
#   frontend/sort.lua                        natural sort of picture names
#   frontend/ui/uimanager.lua                SaveState (the settings table is written before sleep)
#
# Referenced project: VeraDroid, GPL-3.0-or-later, Copyright (C) 2026 the VeraDroid contributors. File consulted:
#   host/pw6/boot/lib/sleep.sh               collecting the screensaver pictures of a folder into a list file (saver.list)

FZ_MNT=$ST/freezer
FZ=$FZ_MNT/android

# power.log (in $LOGD, cut by ring_trim like the other logs): one line per sleep or wake fact, with the date. It is kept by lines, not by minutes,
# so the report can show the last sleeps whenever it is made. kernel_pm copies the kernel's own words about the last suspend and resume
# (the dmesg ring is cut within minutes by a busy kernel, so they are written here at the moment they happen).
plog() { echo "$(date '+%m-%d %H:%M:%S') $*" >> "$LOGD/power.log"; }
kernel_pm() { dmesg 2>/dev/null | grep -E "PM: suspend (entry|exit)|Resume caused by" | tail -n "${1:-3}" | cut -c1-110 | tr '\n' ';'; }

# cgroup v1 freezer. The container is started inside this group (cmd_start), so every container process is frozen together.
freezer_setup() {
  mkdir -p "$FZ_MNT"
  grep -q " $FZ_MNT " /proc/mounts || mount -t cgroup -o freezer freezer "$FZ_MNT" || return 1
  mkdir -p "$FZ"
  [ -f "$FZ/freezer.state" ]
}
freeze_android() {     # returns 1 when the group did not freeze (nothing is frozen then)
  [ -f "$FZ/freezer.state" ] || return 1
  sync
  echo FROZEN > "$FZ/freezer.state"
  i=0; while [ "$(cat "$FZ/freezer.state" 2>/dev/null)" != FROZEN ] && [ $i -lt 50 ]; do sleep 0.1 2>/dev/null || sleep 1; i=$((i + 1)); done
  [ "$(cat "$FZ/freezer.state" 2>/dev/null)" = FROZEN ]
}
thaw_android() {
  [ -f "$ST/shutdown.pid" ] && { kill "$(cat "$ST/shutdown.pid")" 2>/dev/null; rm -f "$ST/shutdown.pid"; }
  [ -f "$FZ/freezer.state" ] && echo THAWED > "$FZ/freezer.state"
}
# Every container process is in the freezer group, so its tasks file says whether a container is really gone.
fz_tasks() { c=$(grep -c . "$FZ/tasks" 2>/dev/null); echo "${c:-0}"; }
fz_drain() {          # $1 = seconds: thaw, SIGKILL whatever is left in the group, wait until it is empty
  [ -f "$FZ/tasks" ] || return 0
  thaw_android
  n=0
  while [ "$(fz_tasks)" -gt 0 ] && [ $n -lt $(($1 * 2)) ]; do
    for p in $(cat "$FZ/tasks" 2>/dev/null); do kill -9 "$p" 2>/dev/null; done
    sleep 0.5 2>/dev/null || sleep 1; n=$((n + 1))
  done
  [ "$(fz_tasks)" -eq 0 ]
}
# sleep state files: never carried over a stop
sleep_state_clear() { rm -f "$ST/asleep" "$ST/frozen" "$ST/wake_pending" "$K_CTL/freeze"; rmdir "$ST/sleeplock" 2>/dev/null; return 0; }

# lock screen picture: the "lock" setting of the Android home app (WallpaperActivity, saved in its preference file
# shared_prefs/embrith.xml as lock_wall; the Android side is not changed). Empty = blank, a file = that picture,
# a folder = a picture of this folder only, one per sleep: shuffled order (like python random.shuffle, every picture once per round),
# or SCREENSAVER_CYCLE=1: natural order.
# Android paths: /dev/wallpapers = $WALL, /dev/kindle = the Kindle's user storage (/mnt/us).
lock_path() {
  x="$BASE/data/user_de/0/com.embrith.home/shared_prefs/embrith.xml"   # the real location on the Kindle side (found on the device)
  p=$(sed -n 's/.*<string name="lock_wall">\([^<]*\)<\/string>.*/\1/p' "$x" 2>/dev/null | head -n1)
  case "$p" in
    /dev/wallpapers*) p="$WALL${p#/dev/wallpapers}" ;;
    /dev/kindle*)     p="/mnt/us${p#/dev/kindle}" ;;
  esac
  printf '%s' "$p"
}
screensaver_show() {
  p=$(lock_path)
  if [ -z "$p" ]; then
    [ -x "$FBINK" ] && "$FBINK" -q -c -f 2>/dev/null; say "lock screen: blank (white)"; return 0
  fi
  if [ -d "$p" ]; then
    list=$ST/saver.list; : > "$list"
    for f in "$p"/*.png "$p"/*.jpg "$p"/*.jpeg "$p"/*.bmp "$p"/*.PNG "$p"/*.JPG "$p"/*.JPEG "$p"/*.BMP; do
      [ -f "$f" ] && echo "$f" >> "$list"
    done
    head -n 256 "$list" > "$list.tmp" && mv "$list.tmp" "$list"
    n=$(wc -l < "$list" 2>/dev/null | tr -d ' ')
    if [ "${n:-0}" -le 0 ]; then
      [ -x "$FBINK" ] && "$FBINK" -q -c -f 2>/dev/null; say "lock screen: folder has no pictures, blank (white)"; return 0
    fi
    cyc=$(sed -n 's/^SCREENSAVER_CYCLE=//p' "$K_CTL/conf" 2>/dev/null | tr -dc '01')
    if [ "$cyc" = 1 ]; then
      sort -V "$list" > "$list.sorted" 2>/dev/null || sort "$list" > "$list.sorted"   # KOReader: natural sort, z2 < z11
      i=$(cat "$ST/saver.index" 2>/dev/null | tr -dc '0-9'); [ -n "$i" ] || i=0
      i=$(( (i % n) + 1 )); echo "$i" > "$ST/saver.index"
      img=$(sed -n "${i}p" "$list.sorted")
    else
      # shuffle: Fisher-Yates over the file list, saved in saver.order; saver.ridx = how many were shown this round
      ord=$ST/saver.order; ri=$ST/saver.ridx
      sort "$list" > "$list.sorted"
      j=$(cat "$ri" 2>/dev/null | tr -dc '0-9'); [ -n "$j" ] || j=0
      if [ "$j" -ge "$n" ] || [ ! -f "$ord" ] || [ "$(sort "$ord" 2>/dev/null)" != "$(cat "$list.sorted")" ]; then
        awk -v seed="$(date +%s)" 'BEGIN { srand(seed) } { a[NR] = $0 } END { for (i = NR; i > 1; i--) { r = int(rand() * i) + 1; t = a[i]; a[i] = a[r]; a[r] = t } for (i = 1; i <= NR; i++) print a[i] }' "$list" > "$ord"
        j=0
      fi
      j=$(( j + 1 )); echo "$j" > "$ri"
      img=$(sed -n "${j}p" "$ord")
    fi
  elif [ -f "$p" ]; then
    img=$p
  else
    [ -x "$FBINK" ] && "$FBINK" -q -c -f 2>/dev/null; say "lock screen: picture not found ($p), blank (white)"; return 0
  fi
  [ -n "$img" ] && [ -x "$FBINK" ] && saver_draw "$img"
}
# saver_draw FILE: clear and draw one picture. fbink splits its -g argument at commas, so a comma in the file name cut the path:
# the picture is reached through a link with a plain name. When it cannot be drawn (decode error, unsupported file) the screen stays white.
saver_draw() {
  ln -sf "$1" "$ST/saver.img" 2>/dev/null
  "$FBINK" -q -c -f -g "file=$ST/saver.img,w=-2,halign=center,valign=center,dither" 2>/dev/null && return 0
  say "lock screen: cannot draw $1, blank (white)"
  "$FBINK" -q -c -f 2>/dev/null
  return 0
}

# goingToScreenSaver: freeze first (no more Android writes), then the picture. powerd suspends after this returns.
sleep_enter() {
  [ -f "$ST/asleep" ] && return 0
  mkdir "$ST/sleeplock" 2>/dev/null || return 0        # one handler at a time
  set_sync; set_flush   # KOReader SaveState: write the table before sleeping
  say "sleep: freeze + picture"
  # kdispd draws nothing while ctl/freeze exists: the lock screen stays on the panel even when Android runs during the lock
  # (thawed on suspending, or a resume that is not an unlock), until outOfScreenSaver removes it in sleep_leave
  : > "$K_CTL/freeze"; chmod 666 "$K_CTL/freeze" 2>/dev/null
  if freeze_android; then
    touch "$ST/frozen"
    ( while sleep 3; do [ -f /tmp/shutdown_in_progress ] && { say "Kindle shutting down: stopping Android"; sh "$DIR/embrith.sh" stop >/dev/null 2>&1; break; }; done ) </dev/null >/dev/null 2>&1 &
    echo $! > "$ST/shutdown.pid"
  else
    say "sleep: freezer did not freeze Android, Android keeps running"
  fi
  plog "enter: frozen=$([ -f "$ST/frozen" ] && echo 1 || echo 0) bat=$(cat "$BATT_SYS/capacity" 2>/dev/null)% up=$(cut -d' ' -f1 /proc/uptime)"
  screensaver_show
  sleep 1.5 2>/dev/null || sleep 2                        # the picture reaches the panel (GC16)
  date +%s > "$ST/asleep"
  batt_sample S
  rmdir "$ST/sleeplock" 2>/dev/null
  if [ -f "$ST/wake_pending" ]; then rm -f "$ST/wake_pending"; say "sleep: wake came during the picture, waking up again"; sleep_leave; fi
}

# outOfScreenSaver: KOReader order (device.lua outofScreenSaver, ui/screensaver.lua close): close the picture, then one
# full flashing redraw right away (UIManager:setDirty("all", "full") on the next tick). Here: thaw, then ctl/clear, which is
# kdispd's full flashing refresh of its frame buffer (repaint = 2).
sleep_leave() {
  if [ ! -f "$ST/asleep" ]; then [ -d "$ST/sleeplock" ] && touch "$ST/wake_pending"; return 0; fi
  sl_a=$(cat "$ST/asleep" 2>/dev/null)
  thaw_android
  rm -f "$ST/asleep" "$ST/frozen" "$ST/wake_pending" "$K_CTL/freeze"
  batt_sample W   # after the thaw: sampling never delays the wake-up
  date +%s > "$ST/woke"
  touch "$K_CTL/clear"; chmod 666 "$K_CTL/clear" 2>/dev/null
  # wake the watchdog now (it waits on this FIFO): it sees $ST/woke and publishes the status while the page is redrawn
  [ -p "$K_CTL/cmd" ] && echo woke > "$K_CTL/cmd"
  say "sleep: woke up"
  plog "leave: asleep $(( $(date +%s) - ${sl_a:-$(date +%s)} )) s bat=$(cat "$BATT_SYS/capacity" 2>/dev/null)% $(kernel_pm 3)"
}

# the watchdog saw a resume while asleep (no powerd unlock event): only facts for power.log, nothing is woken
wake_note() {          # $1 = seconds the watchdog did not run
  plog "gap $1 s while asleep, staying locked: powerd=$(timeout 5 lipc-get-prop com.lab126.powerd status 2>/dev/null | head -n 1 | cut -c1-60) freeze=$([ -f "$K_CTL/freeze" ] && echo 1 || echo 0) frozen=$([ -f "$ST/frozen" ] && echo 1 || echo 0) $(kernel_pm 4)"
}

# menu, idle timer: ask powerd, it does the rest (same as the power button)
sleep_request() {
  [ -f "$ST/asleep" ] && return 0
  if timeout 10 lipc-set-prop -i com.lab126.powerd powerButton 1 2>/dev/null; then say "sleep ($1): powerButton sent"
  else say "sleep ($1): powerButton failed, device stays awake"; fi
}

thaw_for_suspend() {   # powerd sent suspending: only the freezer state is changed, asleep and frozen are left to sleep_leave
  [ -f "$ST/frozen" ] || return 0
  [ -f "$FZ/freezer.state" ] && echo THAWED > "$FZ/freezer.state"
  return 0
}

power_event() {        # $1 = powerd event name, $2 = its argument (suspending: "disk" for hibernation)
  plog "event $1${2:+ $2} asleep=$([ -f "$ST/asleep" ] && echo 1 || echo 0)"
  case "$1" in
    goingToScreenSaver) sleep_enter ;;
    outOfScreenSaver)   sleep_leave ;;
    suspending)         thaw_for_suspend ;;
    resuming)           plog "resume: $(kernel_pm 3)" ;;
  esac
}

power_watch() {        # background, whole session: powerd events -> sleep_enter / sleep_leave
  command -v lipc-wait-event >/dev/null 2>&1 || { say "no lipc-wait-event: powerd events not handled"; return 0; }
  SUP=$$   # the watchdog that started this subshell: stop when it is gone (after a restart the container is alive again)
  while alive && [ -d "/proc/$SUP" ]; do
    t0=$(date +%s)
    # -m: one connection for all events (a restarted lipc-wait-event loses events that arrive in between)
    lipc-wait-event -m com.lab126.powerd goingToScreenSaver,outOfScreenSaver,suspending,resuming </dev/null 2>/dev/null | while read -r ev rest; do
      # the handler must not inherit this pipe as stdin: fbink without text reads stdin and prints it on the panel,
      # and it would eat the next powerd events (outOfScreenSaver never reaches this loop)
      say "powerd event: $ev $rest"; power_event "$ev" "$rest" </dev/null
    done
    if [ $(( $(date +%s) - t0 )) -lt 3 ]; then   # -m not supported or lipc failed: single-shot fallback
      timeout 60 lipc-wait-event com.lab126.powerd goingToScreenSaver,outOfScreenSaver,suspending,resuming </dev/null > "$ST/power.ev" 2>/dev/null
      ev=$(cut -d' ' -f1 "$ST/power.ev" 2>/dev/null); [ -n "$ev" ] && { say "powerd event (single-shot): $ev"; power_event "$ev"; }
      sleep 1
    fi
  done
}

# 电量采样 -> logs/batt.log, 每行 17 列:
#  1 时间 2 标记 3 charge_now(uAh) 4 current_avg(uA) 5 电量% 6 WIFI 7 FL 8 BT
#  9 CPU 忙(jiffies, 累计) 10 CPU 总(含空闲) 11-16 各组进程 CPU(累计): 安卓 kdispd kgest ssh 内核线程 Kindle其余
#  17 固定为 - (原为 /sys/power/wakeup_count; 读它时内核会一直等到没有进行中的唤醒事件, 休眠和唤醒途中会卡死)
# 标记: B 会话开始, A 亮屏(看门狗每 60 秒), S 进入休眠(锁屏图已画), W 唤醒(解冻后)
# S 和 W 不遍历 /proc (第 11-16 列为 -): 报告的休眠段只用 9 10 列, 遍历只会拖慢休眠和唤醒
# 电池节点: 第一个 type 为 Battery 且有 capacity 的 power_supply (各机型名称不同); 找不到时 BATT_SYS 为空, 采样直接跳过
BATT_SYS=
for p in /sys/class/power_supply/*; do
  [ "$(cat "$p/type" 2>/dev/null)" = Battery ] && [ -r "$p/capacity" ] && { BATT_SYS=$p; break; }
done
batt_cpu() {          # 各组进程 utime+stime 之和; 安卓 = 冻结组里的进程
  # 文件名从 stdin 读, 用 getline 逐个打开: 遍历中途退出的进程只是读不到 (getline < 0), 不会让 busybox awk 整个中止
  ls -d /proc/[0-9]* 2>/dev/null | awk -v fz="$FZ/tasks" '
    BEGIN { while ((getline p < fz) > 0) A[p] = 1 }
    {
      s = ""; if ((getline s < ($0 "/stat")) <= 0) next; close($0 "/stat")
      split(s, h, " "); pid = h[1]
      i = index(s, "("); j = index(s, ") "); c = substr(s, i + 1, j - i - 1); n = split(substr(s, j + 2), f, " ")
      if (n < 13) next
      if (pid in A) g = "and"; else if (c == "kdispd") g = "kd"; else if (c == "kgest") g = "kg"
      else if (c ~ /^(dropbear|dropbearmulti|sftp-server)$/) g = "ssh"; else if (f[2] == 2 || pid == 2) g = "ker"; else g = "kin"
      T[g] += f[12] + f[13]
    }
    END { printf "%d %d %d %d %d %d", T["and"], T["kd"], T["kg"], T["ssh"], T["ker"], T["kin"] }'
}
batt_sample() {
  [ -r "$BATT_SYS/charge_now" ] || return 0
  w=-; fl=-; bt=-
  [ -f "$K_CTL/status" ] && { w=$(sed -n 's/^WIFI=//p' "$K_CTL/status"); fl=$(sed -n 's/^FL=//p' "$K_CTL/status"); bt=$(sed -n 's/^BT=//p' "$K_CTL/status"); }
  cpu=$(awk '/^cpu /{ print $2 + $3 + $4 + $7 + $8, $2 + $3 + $4 + $5 + $6 + $7 + $8; exit }' /proc/stat)
  case "$1" in S|W) g="- - - - - -" ;; *) g=$(batt_cpu); [ -n "$g" ] || g="- - - - - -" ;; esac
  echo "$(date +%s) $1 $(cat "$BATT_SYS/charge_now") $(cat "$BATT_SYS/current_avg") $(cat "$BATT_SYS/capacity") ${w:--} ${fl:--} ${bt:--} $cpu $g -" >> "$LOGD/batt.log"
  if [ "$1" = B ] && [ "$(grep -c . "$LOGD/batt.log")" -gt 3000 ]; then
    tail -n 1500 "$LOGD/batt.log" > "$LOGD/batt.log.tmp" && mv "$LOGD/batt.log.tmp" "$LOGD/batt.log"
  fi
}

# KOReader autosuspend: every 4 min while the idle timer runs, reset powerd's own t1 timer so that powerd does not
# enter its screensaver before SLEEP_MIN. Charging or not, the same (no "stay awake while charging"). $1 = uptime seconds.
t1_reset() {
  [ -n "$T1_UP" ] && [ $(( $1 - T1_UP )) -lt 240 ] && return 0
  T1_UP=$1
  timeout 5 lipc-set-prop -i com.lab126.powerd touchScreenSaverTimeout 1 2>/dev/null
}

# powerd isCharging (KOReader PowerD:isCharging)
charging() {
  [ "$(timeout 5 lipc-get-prop -i com.lab126.powerd isCharging 2>/dev/null | tr -dc '0-9')" = 1 ]
}

# called by the watchdog every 20 s; $1 = uptime seconds. Shell builtins only, except when it acts.
# Activity = a touch (kgest touches ctl/touchup) or a wake-up ($ST/woke), seen as "newer than $ST/idle.mark".
# SLEEP_MIN comes from conf_cache (IDLE_MIN), read again only when ctl/conf changes.
idle_check() {
  [ -f "$ST/asleep" ] && return 0
  m=${IDLE_MIN:-15}
  # never (0): our timer does not act, but powerd's own screensaver timer must still be kept from firing, so t1 is reset as well
  [ "$m" -gt 0 ] || { t1_reset "$1"; return 0; }
  if [ -z "$IDLE0" ] || [ "$K_CTL/touchup" -nt "$ST/idle.mark" ] || [ "$ST/woke" -nt "$ST/idle.mark" ]; then IDLE0=$1; : > "$ST/idle.mark"; fi
  if [ $(( $1 - IDLE0 )) -ge $((m * 60)) ]; then
    IDLE0=$1; sleep_request "idle ${m} min"
  else
    t1_reset "$1"
  fi
}
