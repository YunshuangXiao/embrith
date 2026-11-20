# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Embrith contributors
# host/modules/install/dexopt.sh - dex2oat (speed) for the apps the user installed. Sourced by host/embrith.sh.
#   DEX2OAT=1 (ctl/conf, switch "优化: dex2oat" in the launcher): every app installed from now on, by any route, is compiled
#   with speed at install time (pm.dexopt.install). Turning the switch on also compiles the apps that are not compiled yet,
#   while the launcher shows a full-screen progress page that ends only when the run ends or fails.
# ctl/dexopt (written here, read by the launcher): state=running|done|fail, total=N, done=N, cur=package, msg=text

DEXOPT_ST="$K_CTL/dexopt"

dexopt_state() {                      # STATE TOTAL DONE CURRENT MESSAGE
  printf 'state=%s\ntotal=%s\ndone=%s\ncur=%s\nmsg=%s\n' "$1" "$2" "$3" "$4" "$5" > "$DEXOPT_ST.tmp" \
    && chmod 666 "$DEXOPT_ST.tmp" && mv "$DEXOPT_ST.tmp" "$DEXOPT_ST"
}

# the install-time switch (also called after boot completed): on = speed, off = interpret-only (the image default)
dexopt_apply() {
  alive || return 0
  if [ "$(conf_val DEX2OAT)" = 1 ]; then
    ct_sh_t 15 'setprop pm.dexopt.install speed' >/dev/null 2>&1
    say "dexopt: install-time speed on"
  else
    ct_sh_t 15 'setprop pm.dexopt.install interpret-only' >/dev/null 2>&1
    say "dexopt: install-time speed off"
  fi
}

dexopt_busy() { ct_sh_t 10 'ps' 2>/dev/null | grep -q '[d]ex2oat'; }

dexopt_is_speed() {                   # PACKAGE: exit 0 when its odex is compiled with speed
  ct_sh_t 30 'dumpsys package dexopt' 2>/dev/null | tr -d '\r' | awk -v p="[$1]" '
    $1 == p { f = 1; next }
    /^  \[/ { f = 0 }
    f && /compilation_filter=speed,/ { ok = 1 }
    END { exit ok ? 0 : 1 }'
}

# put back everything dexopt_wait changed (GESTURES), release the lock, never leave state=running behind. It is also called
# after boot: a run that was killed hard (kill -9, the Kindle losing power) leaves $ST/dexopt.gestures behind.
dexopt_restore() {
  [ -f "$ST/dexopt.gestures" ] && { conf_put GESTURES "$(cat "$ST/dexopt.gestures" 2>/dev/null)"; rm -f "$ST/dexopt.gestures"; }
  [ "$(sed -n 's/^state=//p' "$DEXOPT_ST" 2>/dev/null)" = running ] && dexopt_state fail 0 0 "" "编译被中断"
  rmdir "$ST/dexopt.lock" 2>/dev/null
  return 0
}

dexopt_abort() { kill "$DEXOPT_RP" 2>/dev/null; dexopt_restore; exit 1; }

# the entry point (ctl/dexopt_start, runs in the background): dexopt_run runs in its own subshell and is waited for, so whatever
# happens inside it (error, early return, exit) comes back here; TERM/INT/HUP of this shell stop it. Either way the settings
# changed here are put back (dexopt_restore). One run at a time.
dexopt_wait() {
  alive || return 1
  mkdir "$ST/dexopt.lock" 2>/dev/null || return 0
  g=$(conf_val GESTURES); [ -n "$g" ] || g=1
  echo "$g" > "$ST/dexopt.gestures"
  trap 'dexopt_abort' INT TERM HUP
  conf_put GESTURES 0                                   # no gestures while the page is up (the fixed corner exit stays)
  ( dexopt_run ) &
  DEXOPT_RP=$!
  wait "$DEXOPT_RP"
  trap - INT TERM HUP
  dexopt_restore
}

dexopt_run() {
  alive || return 1
  dexopt_state running 0 0 "" "准备中"
  say "dexopt: start"
  dexopt_apply
  pk=$(ct_sh_t 30 'cmd package list packages -3' 2>/dev/null | tr -d '\r' | sed -n 's/^package://p')
  # nothing else should compete for memory: stop the apps (not the launcher) and every cached process
  for p in $pk; do ct_sh_t 10 "am force-stop $p" >/dev/null 2>&1; done
  ct_sh_t 10 'am kill-all' >/dev/null 2>&1
  total=$(echo $pk | wc -w); n=0; bad=0; last=""
  for p in $pk; do
    dexopt_state running "$total" "$n" "$p" ""
    if dexopt_is_speed "$p"; then n=$((n + 1)); continue; fi
    a=$(ls "$BASE"/data/app/"$p"-*/base.apk 2>/dev/null | head -n 1)
    if [ -n "$a" ]; then                                # speed odex is about twice the apk: keep room on /data
      need=$(( $(du -m "$a" | cut -f1) * 2 + 200 )); free=$(df -m -P "$BASE/data" | awk 'NR == 2 {print $4}')
      if [ "${free:-0}" -lt "$need" ]; then bad=$((bad + 1)); last="$p: 空间不足"; say "dexopt: $p skipped, low space"; n=$((n + 1)); continue; fi
    fi
    ( ct_sh_t 3600 "cmd package compile -m speed $p" >/dev/null 2>&1 ) </dev/null >/dev/null 2>&1 &
    cp=$!
    sleep 3
    # the cmd client may exit early, the compile itself runs in installd: wait for both, and keep ctl/dexopt fresh
    while kill -0 "$cp" 2>/dev/null || dexopt_busy; do dexopt_state running "$total" "$n" "$p" ""; sleep 4; done
    if dexopt_is_speed "$p"; then say "dexopt: $p speed"; else bad=$((bad + 1)); last="$p: 编译失败"; say "dexopt: $p failed"; fi
    n=$((n + 1))
  done
  if [ "$bad" -gt 0 ]; then dexopt_state fail "$total" "$n" "" "$bad 个应用没有编好 ($last)"
  else dexopt_state done "$total" "$n" "" "完成"; fi
  say "dexopt: end, failed $bad of $total"
}