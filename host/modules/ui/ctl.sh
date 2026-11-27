# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Embrith contributors
# host/ui/ctl.sh - requests from the Android launcher (files in ctl/), the status it shows, and the warmth schedule.
# Sourced by host/embrith.sh.

K_CTL=/var/tmp/kdisp/ctl

radio_wifi() { c=$(timeout 5 lipc-get-prop com.lab126.cmd wirelessEnable 2>/dev/null | tr -d '\r\n'); [ -n "$c" ] || c=1; timeout 15 lipc-set-prop -i com.lab126.cmd wirelessEnable $((1 - c)) 2>/dev/null; }

radio_bt()   {
  b=$(timeout 5 lipc-get-prop com.lab126.btfd BTstate 2>/dev/null | tr -d '\r\n')
  if [ "${b:-0}" != 0 ]; then timeout 15 lipc-set-prop -i com.lab126.btfd BTflightMode 1 2>/dev/null
  else timeout 15 lipc-set-prop -i com.lab126.btfd BTflightMode 0 2>/dev/null; timeout 15 lipc-set-prop -s com.lab126.btfd BTenable 1:2 2>/dev/null; fi
}

# Lists for the Android pop-ups (NetPickActivity). One line per entry, TAB separated, written atomically (empty file = nothing found).
#   wifi_list: connected(0/1) known(0/1) secured(0/1) enterprise(0/1) signal(0-5) essid      (only essid/flags/signal; profileData and its psk are never read)
#   bt_list:   connected(0/1) mac name                                                       (paired devices)
list_wifi() {
  cur=$(echo '{}' | timeout 5 lipc-hash-prop com.lab126.wifid currentEssid 2>/dev/null | sed -n 's/^[[:space:]]*essid = "\(.*\)"[[:space:]]*$/\1/p' | head -n1)
  o="$K_CTL/wifi_list.tmp"
  echo '{}' | timeout 10 lipc-hash-prop com.lab126.wifid scanList 2>/dev/null | awk -v cur="$cur" '
    function flush() { if (n && e != "" && !(e in seen)) { seen[e] = 1; printf "%d\t%d\t%d\t%d\t%d\t%s\n", (e == cur), k, s, w, g, e } }
    /^Hash Index/ { flush(); n = 1; e = ""; k = s = w = g = 0; next }
    /^[ \t]*essid = / { sub(/^[^"]*"/, ""); sub(/"[ \t]*$/, ""); e = $0 }
    /^[ \t]*secured = "yes"/ { s = 1 }
    /^[ \t]*known = "yes"/ { k = 1 }
    /^[ \t]*enterprise = "yes"/ { w = 1 }
    /^[ \t]*signal = / { g = $0; gsub(/[^0-9]/, "", g) }
    END { flush() }' > "$o" && chmod 666 "$o" && mv "$o" "$K_CTL/wifi_list"
}

list_bt() {
  o="$K_CTL/bt_list.tmp"
  echo '{}' | timeout 10 lipc-hash-prop com.lab126.btfd ListPaired 2>/dev/null | awk '
    function flush() { if (m != "") printf "%d\t%s\t%s\n", c, m, n }
    /^Hash Index/ { flush(); m = ""; n = ""; c = 0; next }
    /^[ \t]*bd_name = / { sub(/^[^"]*"/, ""); sub(/"[ \t]*$/, ""); n = $0 }
    /^[ \t]*bd_address = / { sub(/^[^"]*"/, ""); sub(/"[ \t]*$/, ""); m = $0 }
    /^[ \t]*is_connected = / { c = ($0 ~ /\(1\)/) ? 1 : 0 }
    END { flush() }' > "$o" && chmod 666 "$o" && mv "$o" "$K_CTL/bt_list"
}

# Adds a new Wi-Fi profile with a password and connects to it. Only when WIFI_PSK_INPUT=1 in ctl/conf (the switch in settings, network).
# ctl/wifi_add: line 1 = essid, line 2 = password. It is read once and removed at once. The password is never logged, never put on a
# command line (it goes through stdin) and never kept in a variable longer than this function runs.
# Input format of createProfile (verified on the device): { essid = "x", secured = "yes", psk = "y", store_nw_user_pref = (0) }.
# Quote and backslash inside essid or password are refused: how the parser escapes them is not verified.
wifi_add() {
  wa_f="$K_CTL/wifi_add"
  wa_e=$(sed -n 1p "$wa_f" 2>/dev/null | tr -d '\000-\037')
  wa_p=$(sed -n 2p "$wa_f" 2>/dev/null | tr -d '\000-\037')
  rm -f "$wa_f"
  if [ "$(sed -n 's/^WIFI_PSK_INPUT=//p' "$K_CTL/conf" 2>/dev/null | head -n1)" != 1 ]; then say "wifi add refused: switch is off"; wa_p=; return; fi
  if [ -z "$wa_e" ] || [ ${#wa_p} -lt 8 ] || [ ${#wa_p} -gt 63 ]; then say "wifi add refused: bad essid or password length"; wa_p=; return; fi
  case "$wa_e$wa_p" in *\"*|*\\*) say "wifi add refused: quote or backslash"; wa_p=; return ;; esac
  say "wifi add: $wa_e"
  printf '{ essid = "%s", secured = "yes", psk = "%s", store_nw_user_pref = (0) }' "$wa_e" "$wa_p" | timeout 20 lipc-hash-prop com.lab126.wifid createProfile >/dev/null 2>&1 || say "wifi add: createProfile failed"
  wa_p=
  ( timeout 60 lipc-set-prop -s com.lab126.cmd ensureConnection "wifi:$wa_e" ) </dev/null >/dev/null 2>&1 &
}

# The actions of the action table (launcher: Actions.java). ctl/act holds one request per line, "id [argument]". The id is the same as in the table; an action
# that already has its own request file only creates that file (it is handled below in the same pass), the others are done here.
act_touch() { : > "$K_CTL/$1"; chmod 666 "$K_CTL/$1" 2>/dev/null; }
act_clamp() { v=$(printf '%s' "$1" | tr -dc '0-9-'); [ -n "$v" ] || v=0; [ "$v" -lt 0 ] && v=0; [ "$v" -gt 24 ] && v=24; echo "$v"; }   # 0..24: the range of powerd
act_light() { timeout 10 lipc-set-prop -i com.lab126.powerd flIntensity "$(act_clamp "$1")" 2>/dev/null; }
act_step() { n=$(printf '%s' "$1" | tr -dc '0-9'); [ -n "$n" ] || n=1; echo "$n"; }
act_run() {   # $1 = id, $2 = argument
  case "$1" in
    wifi_on)  timeout 15 lipc-set-prop -i com.lab126.cmd wirelessEnable 1 2>/dev/null ;;
    wifi_off) timeout 15 lipc-set-prop -i com.lab126.cmd wirelessEnable 0 2>/dev/null ;;
    wifi_toggle) radio_wifi; QDUE="$((${el:-0} + 3)) $((${el:-0} + 10))" ;;
    bt_toggle)   radio_bt; QDUE="$((${el:-0} + 3)) $((${el:-0} + 10))" ;;
    light_set) act_light "$2" ;;
    light_inc|light_dec)
      cur=$(timeout 5 lipc-get-prop com.lab126.powerd flIntensity 2>/dev/null | tr -dc '0-9'); [ -n "$cur" ] || cur=0
      if [ "$1" = light_inc ]; then act_light "$((cur + $(act_step "$2")))"; else act_light "$((cur - $(act_step "$2")))"; fi ;;
    light_toggle)
      cur=$(timeout 5 lipc-get-prop com.lab126.powerd flIntensity 2>/dev/null | tr -dc '0-9'); [ -n "$cur" ] || cur=0
      if [ "$cur" -gt 0 ]; then echo "$cur" > "$K_CTL/flo" 2>/dev/null; act_light 0
      else o=$(cat "$K_CTL/flo" 2>/dev/null | tr -dc '0-9'); [ -n "$o" ] && [ "$o" -gt 0 ] || o=10; act_light "$o"; fi ;;
    warm_set|warm_inc|warm_dec)
      cur=$(timeout 5 lipc-get-prop com.lab126.powerd currentAmberLevel 2>/dev/null | tr -dc '0-9'); [ -n "$cur" ] || cur=0
      case "$1" in warm_set) w=$2 ;; warm_inc) w=$((cur + $(act_step "$2"))) ;; *) w=$((cur - $(act_step "$2"))) ;; esac
      w=$(act_clamp "$w"); conf_put WARM_DEFAULT "$w"; amber_set "$w" ;;
    sleep) sleep_request "action" ;;
    refresh) act_touch clear ;;
    screenshot) act_touch screenshot ;;
    restart_app) act_touch restart ;;
    reboot) act_touch kindle_reboot ;;
    usb_eject) act_touch usb_eject ;;
    check_update) act_touch netupd_check ;;
    exit_app) act_touch exit ;;
    *) say "act: unknown action $1" ;;
  esac
  STAT_LAST=0
}

ctl_requests() {
  if [ -f "$K_CTL/act" ]; then mv "$K_CTL/act" "$K_CTL/act.run" 2>/dev/null
    while read -r act_id act_arg; do [ -n "$act_id" ] && act_run "$act_id" "$act_arg"; done < "$K_CTL/act.run"
    rm -f "$K_CTL/act.run"; fi
  if [ -f "$K_CTL/status_req" ]; then q=; read -r q < "$K_CTL/status_req" 2>/dev/null; rm -f "$K_CTL/status_req"   # "quick" = skip the SSH queries
    case "$q" in quick) publish_status quick ;; *) publish_status ;; esac; STAT_LAST=${el:-1}; fi   # the launcher shows status (panel, settings): fresh values, SSH included
  [ -f "$K_CTL/wifi" ] && { rm -f "$K_CTL/wifi"; radio_wifi; STAT_LAST=0; QDUE="$((${el:-0} + 3)) $((${el:-0} + 10))"; }
  [ -f "$K_CTL/bt" ]   && { rm -f "$K_CTL/bt"; radio_bt; STAT_LAST=0; QDUE="$((${el:-0} + 3)) $((${el:-0} + 10))"; }
  if [ -f "$K_CTL/net_list" ]; then k=$(cat "$K_CTL/net_list" 2>/dev/null | tr -dc 'a-z'); rm -f "$K_CTL/net_list"
    case "$k" in wifi) list_wifi ;; bt) list_bt ;; esac; fi
  if [ -f "$K_CTL/wifi_join" ]; then v=$(head -n 1 "$K_CTL/wifi_join" 2>/dev/null | tr -d '\r'); rm -f "$K_CTL/wifi_join"
    [ -n "$v" ] && { say "wifi join: $v"; ( timeout 60 lipc-set-prop -s com.lab126.wifid cmConnect "$v" ) </dev/null >/dev/null 2>&1 & }; STAT_LAST=0; QDUE="$((${el:-0} + 3)) $((${el:-0} + 10))"; fi
  [ -f "$K_CTL/wifi_add" ] && { wifi_add; STAT_LAST=0; QDUE="$((${el:-0} + 3)) $((${el:-0} + 10))"; }
  for r in bt_connect:Connect bt_disconnect:Disconnect; do
    f=${r%%:*}; [ -f "$K_CTL/$f" ] || continue
    v=$(head -n 1 "$K_CTL/$f" 2>/dev/null | tr -dc '0-9A-Fa-f:'); rm -f "$K_CTL/$f"
    [ -n "$v" ] && { say "bt ${r#*:}: $v"; ( timeout 60 lipc-set-prop -s com.lab126.btfd "${r#*:}" "$v" ) </dev/null >/dev/null 2>&1 & }; STAT_LAST=0; QDUE="$((${el:-0} + 3)) $((${el:-0} + 10))"
  done
  if [ -f "$K_CTL/light_set" ]; then v=$(cat "$K_CTL/light_set" 2>/dev/null | tr -dc '0-9'); rm -f "$K_CTL/light_set"
    [ -n "$v" ] && timeout 10 lipc-set-prop -i com.lab126.powerd flIntensity "$v" 2>/dev/null; STAT_LAST=0; fi
  if [ -f "$K_CTL/warm_set" ]; then v=$(cat "$K_CTL/warm_set" 2>/dev/null | tr -dc '0-9'); rm -f "$K_CTL/warm_set"
    [ -n "$v" ] && { conf_put WARM_DEFAULT "$v"; amber_set "$v"; }; STAT_LAST=0; fi
  if [ -f "$K_CTL/install" ]; then p=$(cat "$K_CTL/install" 2>/dev/null | tr -d '\r\n'); rm -f "$K_CTL/install"; ( do_install "$p" ) >/dev/null 2>&1 & fi
  [ -f "$K_CTL/dexopt_apply" ] && { rm -f "$K_CTL/dexopt_apply"; ( dexopt_apply ) </dev/null >/dev/null 2>&1 & }
  [ -f "$K_CTL/dexopt_start" ] && { rm -f "$K_CTL/dexopt_start"; ( dexopt_wait ) </dev/null >/dev/null 2>&1 & }
  [ -f "$K_CTL/webview_apply" ] && { rm -f "$K_CTL/webview_apply"; ( webview_apply ) </dev/null >/dev/null 2>&1 & }
  [ -f "$K_CTL/fulldirty_apply" ] && { rm -f "$K_CTL/fulldirty_apply"; ( fulldirty_apply ) </dev/null >/dev/null 2>&1 & }
  if [ -f "$K_CTL/backup_export" ]; then v=$(head -n 1 "$K_CTL/backup_export" 2>/dev/null | tr -dc 'a-z0-9, '); rm -f "$K_CTL/backup_export"; ( . "$MOD/ui/backup.sh"; bk_export $v ) </dev/null >/dev/null 2>&1 & fi
  if [ -f "$K_CTL/backup_import" ]; then v=$(head -n 1 "$K_CTL/backup_import" 2>/dev/null | tr -dc 'a-z0-9, '); rm -f "$K_CTL/backup_import"; ( . "$MOD/ui/backup.sh"; bk_import $v ) </dev/null >/dev/null 2>&1 & fi
  [ -f "$K_CTL/dns_apply" ] && { rm -f "$K_CTL/dns_apply"; ( dns_apply ) </dev/null >/dev/null 2>&1 & }
  [ -f "$K_CTL/ssh" ] && { rm -f "$K_CTL/ssh"; ssh_toggle; STAT_LAST=0; SDUE="$((${el:-0} + 3)) $((${el:-0} + 10))"; }
  [ -f "$K_CTL/ssh_clean" ] && { rm -f "$K_CTL/ssh_clean"; ssh_clean; }
  [ -f "$K_CTL/report_gen" ] && { rm -f "$K_CTL/report_gen"; ( report_gen; report_list ) </dev/null >/dev/null 2>&1 & }
  [ -f "$K_CTL/report_ls" ] && { rm -f "$K_CTL/report_ls"; report_list; }
  if [ -f "$K_CTL/report_del" ]; then v=$(head -n 1 "$K_CTL/report_del" 2>/dev/null | tr -d '\r\n'); rm -f "$K_CTL/report_del"; report_del "$v"; report_list; fi
  if [ -f "$K_CTL/report_view" ]; then v=$(head -n 1 "$K_CTL/report_view" 2>/dev/null | tr -d '\r\n'); rm -f "$K_CTL/report_view"; report_view "$v"; fi
  if [ -f "$K_CTL/ssh_src" ]; then v=$(head -n 1 "$K_CTL/ssh_src" 2>/dev/null | tr -dc 'a-z'); rm -f "$K_CTL/ssh_src"; ssh_toggle_src "$v"; STAT_LAST=0; SDUE="$((${el:-0} + 3)) $((${el:-0} + 10))"; fi
  if [ -f "$K_CTL/ssh_port" ]; then v=$(head -n 1 "$K_CTL/ssh_port" 2>/dev/null | tr -dc 'a-z0-9 '); rm -f "$K_CTL/ssh_port"; ssh_port_set $v; STAT_LAST=0; SDUE="$((${el:-0} + 3)) $((${el:-0} + 10))"; fi
  if [ -f "$K_CTL/ssh_auth" ]; then v=$(head -n 1 "$K_CTL/ssh_auth" 2>/dev/null | tr -d '\r'); rm -f "$K_CTL/ssh_auth"; ssh_auth $v; STAT_LAST=0; SDUE="$((${el:-0} + 3)) $((${el:-0} + 10))"; fi
  if [ -f "$K_CTL/ssh_auth_k" ]; then v=$(head -n 1 "$K_CTL/ssh_auth_k" 2>/dev/null | tr -dc 'a-z'); rm -f "$K_CTL/ssh_auth_k"; ssh_auth_k "$v"; STAT_LAST=0; SDUE="$((${el:-0} + 3)) $((${el:-0} + 10))"; fi
  if [ -f "$K_CTL/restart" ]; then rm -f "$K_CTL/restart"; say "restart requested from Android"; progress 0; : > "$K_CTL/restarting"; chmod 666 "$K_CTL/restarting" 2>/dev/null; RESTART_REQ=1; touch "$STOPF"; fi
  if [ -f "$K_CTL/restart_update" ]; then rm -f "$K_CTL/restart_update"; say "restart and update requested from Android"; progress 0; : > "$K_CTL/restarting"; chmod 666 "$K_CTL/restarting" 2>/dev/null; RESTART_REQ=2; touch "$STOPF"; fi
  if [ -f "$K_CTL/restart_update_force" ]; then rm -f "$K_CTL/restart_update_force"; say "forced restart and update requested from Android"; progress 0; : > "$K_CTL/restarting"; chmod 666 "$K_CTL/restarting" 2>/dev/null; RESTART_REQ=3; touch "$STOPF"; fi
  if [ -f "$K_CTL/restart_update_net" ]; then rm -f "$K_CTL/restart_update_net"; say "restart and update (GitHub release) requested from Android"; progress 0; : > "$K_CTL/restarting"; chmod 666 "$K_CTL/restarting" 2>/dev/null; RESTART_REQ=4; touch "$STOPF"; fi
  [ -f "$K_CTL/netupd_check" ] && { rm -f "$K_CTL/netupd_check"; ( sh "$MOD/install/netupdate.sh" check ) </dev/null >/dev/null 2>&1 & }
  [ -f "$K_CTL/netupd_get" ] && { rm -f "$K_CTL/netupd_get"; ( sh "$MOD/install/netupdate.sh" get ) </dev/null >/dev/null 2>&1 & }
  [ -f "$K_CTL/sleep" ] && { rm -f "$K_CTL/sleep"; sleep_request "menu"; }
  [ -f "$K_CTL/usb_eject" ] && { rm -f "$K_CTL/usb_eject"; usb_eject; }
  for r in fl_auto:FL_AUTO_PROP; do
    f=${r%%:*}; [ -f "$K_CTL/$f" ] || continue
    v=$(cat "$K_CTL/$f" 2>/dev/null | tr -dc '01'); rm -f "$K_CTL/$f"
    . "$ST/lipcprops" 2>/dev/null; eval "pp=\$${r#*:}"
    if [ -n "$pp" ] && [ -n "$v" ]; then timeout 10 lipc-set-prop -i com.lab126.powerd "$pp" "$v" 2>/dev/null || say "lipc-set-prop $pp $v failed"; fi
    STAT_LAST=0
  done
  if [ -f "$K_CTL/display_size" ]; then v=$(cat "$K_CTL/display_size" 2>/dev/null | tr -dc '0-9'); rm -f "$K_CTL/display_size"
    [ -n "$v" ] && { say "display size: wm density $v"; ( ct_sh_t 30 "wm density $v" ) </dev/null >/dev/null 2>&1 & }; fi
  if [ -f "$K_CTL/font_scale" ]; then v=$(cat "$K_CTL/font_scale" 2>/dev/null | tr -dc '0-9.'); rm -f "$K_CTL/font_scale"
    [ -n "$v" ] && { say "font scale: $v"; ( ct_sh_t 30 "settings put system font_scale $v" ) </dev/null >/dev/null 2>&1 & }; fi
}

# warmth: WARM_DEFAULT = the warmth at any time (status panel slider, warmth page): a change applies at once, always.
# AMBER_LEVEL = the warmth of the window AMBER_ON..AMBER_OFF (AMBER_SCHED=1, HHMM, may cross midnight). Entering the window
# switches to AMBER_LEVEL, leaving it switches back to WARM_DEFAULT. Inside the window both sliders apply at once (the last
# one moved wins); outside it only WARM_DEFAULT does, AMBER_LEVEL waits for the window.
# Nothing is written to powerd when the value is already applied ($ST/amber_applied).
amber_inwin() {          # true inside the window
  [ "$(conf_val AMBER_SCHED)" = 1 ] || return 1
  on=$(conf_val AMBER_ON); off=$(conf_val AMBER_OFF); [ -n "$on" ] || on=2100; [ -n "$off" ] || off=0700
  now=$(date +%H%M)
  if [ "$on" -le "$off" ]; then [ "$now" -ge "$on" ] && [ "$now" -lt "$off" ]
  else [ "$now" -ge "$on" ] || [ "$now" -lt "$off" ]; fi
}
amber_want() {           # the value for the current side of the window
  d=$(conf_val WARM_DEFAULT)
  if [ -z "$d" ]; then                                   # first run: keep what powerd has now
    d=$(timeout 5 lipc-get-prop com.lab126.powerd currentAmberLevel 2>/dev/null | tr -dc '0-9')
    [ -n "$d" ] && conf_put WARM_DEFAULT "$d"
  fi
  if amber_inwin; then lv=$(conf_val AMBER_LEVEL); echo "${lv:-$d}"; else echo "$d"; fi
}
amber_set() {            # $1 = warmth: write it to powerd unless it is already there
  [ -n "$1" ] || return 0
  [ "$1" = "$(cat "$ST/amber_applied" 2>/dev/null)" ] && return 0
  if timeout 10 lipc-set-prop -i com.lab126.powerd currentAmberLevel "$1" 2>/dev/null; then
    echo "$1" > "$ST/amber_applied"; say "warmth: $1"; STAT_LAST=0
  fi
}
amber_apply() {          # window boundary: switch only when the side changed (timer, wake-up); "force" = always (start, window settings)
  if amber_inwin; then w=1; else w=0; fi
  [ "$w" = "$AMB_W" ] && [ "$1" != force ] && return 0
  AMB_W=$w; amber_set "$(amber_want)"
}
amber_conf() {           # after a change of ctl/conf (and once at start): which warmth setting changed
  d=$(conf_val WARM_DEFAULT); l=$(conf_val AMBER_LEVEL); s="$(conf_val AMBER_SCHED) $(conf_val AMBER_ON) $(conf_val AMBER_OFF)"
  if [ "$s" != "$AMB_S" ]; then AMB_S=$s; amber_apply force; amber_arm; fi
  if [ "$d" != "$AMB_D" ]; then AMB_D=$d; [ -n "$AMB_INIT" ] && amber_set "$d"; fi
  if [ "$l" != "$AMB_L" ]; then AMB_L=$l; [ -n "$AMB_INIT" ] && [ "$AMB_W" = 1 ] && amber_set "$l"; fi
  AMB_INIT=1
}

amber_sched() { amber_apply; }

# No polling for the warmth window: one background sleep until the next AMBER_ON or AMBER_OFF, which then writes
# $ST/amber_tick for the watchdog (apply, arm again). Armed again at start, on every change of ctl/conf and after a
# wake-up (sleep does not count while the device is suspended, so the timer is late after a suspend).
amber_arm() {
  [ -f "$ST/amber.pid" ] && { kill "$(cat "$ST/amber.pid")" 2>/dev/null; rm -f "$ST/amber.pid"; }
  [ "$(conf_val AMBER_SCHED)" = 1 ] || return 0
  on=$(conf_val AMBER_ON); off=$(conf_val AMBER_OFF); [ -n "$on" ] || on=2100; [ -n "$off" ] || off=0700
  set -- $(date '+%H %M %S'); now=$(( ${1#0} * 3600 + ${2#0} * 60 + ${3#0} ))
  h=${on%??}; m=${on#??}; t1=$(( ${h#0} * 3600 + ${m#0} * 60 ))
  h=${off%??}; m=${off#??}; t2=$(( ${h#0} * 3600 + ${m#0} * 60 ))
  d1=$(( (t1 - now + 86400) % 86400 )); [ "$d1" -gt 0 ] || d1=86400
  d2=$(( (t2 - now + 86400) % 86400 )); [ "$d2" -gt 0 ] || d2=86400
  d=$d1; [ "$d2" -lt "$d" ] && d=$d2
  ( sleep $((d + 1)); : > "$ST/amber_tick" ) </dev/null >/dev/null 2>&1 &
  echo $! > "$ST/amber.pid"
}

publish_status() {
  [ -d "$K_CTL" ] || return 0
  w=$(timeout 5 lipc-get-prop com.lab126.cmd wirelessEnable 2>/dev/null | tr -d '\r\n'); [ -n "$w" ] || w=1
  b=$(timeout 5 lipc-get-prop com.lab126.btfd BTstate 2>/dev/null | tr -d '\r\n'); [ "${b:-0}" != 0 ] && b=1 || b=0
  bt=$(timeout 5 lipc-get-prop com.lab126.powerd battLevel 2>/dev/null | tr -d '\r\n'); [ -n "$bt" ] || bt=-1
  fl=$(timeout 5 lipc-get-prop com.lab126.powerd flIntensity 2>/dev/null | tr -d '\r\n'); [ -n "$fl" ] || fl=-1
  am=$(timeout 5 lipc-get-prop com.lab126.powerd currentAmberLevel 2>/dev/null | tr -d '\r\n'); [ -n "$am" ] || am=-1
  ch=$(timeout 5 lipc-get-prop -i com.lab126.powerd isCharging 2>/dev/null | tr -dc '0-9'); [ "$ch" = 1 ] || ch=0
  ip=$(wifi_ip)
  # SSH (4 runs of ssh-toggle.sh): not on the quick path of the watchdog ($1 = quick), only on status_req and after an SSH change
  if [ "$1" != quick ] || [ -z "$SSH_AT" ]; then
  SSH_AT=1
  ss=$(ssh_state)
  set -- $(timeout 10 sh "$MOD/lib/ssh-toggle.sh" authinfo </dev/null 2>/dev/null); sa=${1:-password}; sp=${2:-0}
  set -- $(timeout 10 sh "$MOD/lib/ssh-toggle.sh" authinfo koreader </dev/null 2>/dev/null); sak=${1:-password}
  # 各来源的 SSH 状态(1 开 0 关 -1 没有安装)和端口
  L=$(timeout 10 sh "$MOD/lib/ssh-toggle.sh" list </dev/null 2>/dev/null)
  se=-1; sk=-1; pe=2222; pk=2222
  while read -r xn xr xu xp xm xh; do
    v=-1; [ "$xr" = 1 ] && v=$xu
    case "$xn" in embrith) se=$v; pe=${xp:-2222} ;; koreader) sk=$v; pk=${xp:-2222} ;; esac
  done <<EOF
$L
EOF
  fi
  . "$ST/lipcprops" 2>/dev/null
  fa=-1; [ -n "$FL_AUTO_PROP" ] && { v=$(timeout 5 lipc-get-prop com.lab126.powerd "$FL_AUTO_PROP" 2>/dev/null | tr -dc '0-9'); [ -n "$v" ] && { [ "$v" != 0 ] && fa=1 || fa=0; }; }
  ev=$(head -n 1 "$DIR/VERSION" 2>/dev/null | tr -d '\r\n ')   # the Embrith version this run was started from (settings, General)
  printf 'WIFI=%s\nBT=%s\nBATT=%s\nFL=%s\nAMBER=%s\nSSH=%s\nIP=%s\nSSH_AUTH=%s\nSSH_PW=%s\nFL_AUTO=%s\nSSH_E=%s\nSSH_K=%s\nPORT_E=%s\nPORT_K=%s\nSSH_AUTH_K=%s\nCHG=%s\nVER=%s\n' "$w" "$b" "$bt" "$fl" "$am" "$ss" "$ip" "$sa" "$sp" "$fa" "$se" "$sk" "$pe" "$pk" "$sak" "$ch" "$ev" > "$K_CTL/status.tmp" && chmod 666 "$K_CTL/status.tmp" && mv "$K_CTL/status.tmp" "$K_CTL/status"
}

wifi_ip() { ifconfig wlan0 2>/dev/null | sed -n 's/.*inet addr:\([0-9.]*\).*/\1/p' | head -n1; }
