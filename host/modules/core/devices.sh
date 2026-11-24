# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Embrith contributors
# host/core/devices.sh - the device nodes Android gets: its own binder, null/zero/random, touch screen and virtual keys.
# Sourced by host/embrith.sh.

DEVDIR=/var/tmp/embrith-dev

# does any process hold /dev/$1 open?
dev_in_use() {
  for p in /proc/[0-9]*; do
    ls -l "$p/fd" 2>/dev/null | grep -q "/dev/$1\$" && return 0
    ls -l "$p/fd" 2>/dev/null | grep -q "/dev/$1 " && return 0
  done
  return 1
}

# ---- touch screen: which /dev/input/eventN, and the names Android's input device configuration (IDC) files are called by ----
TAB=$(printf '\t')

# One line per input device, tab separated: eventN, vendor, product, name ("-" when a field is missing).
# /proc/bus/input/devices has one block per device with a blank line after it; a block is written out when it ends.
input_table() {
  awk '
    function flush() {
      if (ev != "") printf "%s\t%s\t%s\t%s\n", ev, (ven == "" ? "-" : ven), (prod == "" ? "-" : prod), nm
      ev = ven = prod = nm = ""
    }
    /^I: / { for (k = 2; k <= NF; k++) { split($k, kv, "="); if (kv[1] == "Vendor") ven = kv[2]; if (kv[1] == "Product") prod = kv[2] } }
    /^N: / { nm = $0; sub(/^N: Name="/, "", nm); sub(/"$/, "", nm) }
    /^H: / { h = $0; sub(/^H: Handlers=/, "", h); n = split(h, hv, " "); for (k = 1; k <= n; k++) if (hv[k] ~ /^event[0-9]+$/) ev = hv[k] }
    NF == 0 { flush() }
    END { flush() }
  ' /proc/bus/input/devices
}

# Detect whether /proc/bus/input/devices (or a test fixture $1) uses 64-bit or 32-bit unsigned long bitmap words.
# Linux input_bits_to_string() prints space-separated hex longs without zero-padding ("0" for empty lower words).
input_dev_bits() {
  _f=${1:-/proc/bus/input/devices}
  if awk '/^B: /{ sub(/^B: [^=]*=/, ""); for (i = 1; i <= NF; i++) if (length($i) > 8) { f = 1; exit } } END { exit !f }' "$_f" 2>/dev/null; then
    echo 64
    return 0
  fi
  if [ "$_f" = /proc/bus/input/devices ]; then
    case "$(uname -m 2>/dev/null)" in *64*) echo 64; return 0 ;; esac
  fi
  echo 32
}

# Pick either the capacitive multi-touch touchscreen ("touch") or a separate pen digitizer ("stylus").
# Nibble positions stay invariant across unpadded lower words because each word is an unsigned long:
#   INPUT_PROP_DIRECT (bit 1): last nibble of rightmost PROP word matches [2367abefABEF]
#   ABS_MT_POSITION_X/Y (bits 53..54): nibble 13 of word 0 (64-bit) or nibble 5 of word 1 (32-bit) matches [67efEF]
#   BTN_TOOL_PEN..PENCIL (bits 320..323): nibble 0 of KEY word 5 (64-bit) or word 10 (32-bit) is non-zero
input_pick() {
  _mode=$1
  _skip=$2
  _file=${3:-/proc/bus/input/devices}
  _bits=$(input_dev_bits "$_file")
  awk -v mode="$_mode" -v skip="$_skip" -v bits="$_bits" '
    function flush() {
      if (ev != "" && nm !~ /^embrith-/) {
        path = "/dev/input/" ev
        if (mode == "touch") {
          if (direct && mt && !pen && p1 == "") p1 = path
          else if (mt && !pen && p2 == "")      p2 = path
          else if (direct && mt && p3 == "")    p3 = path
          else if (direct && abs && p4 == "")   p4 = path
        } else if (mode == "stylus" && path != skip) {
          if (direct && pen && abs && p1 == "")      p1 = path
          else if (pen && abs && p2 == "")           p2 = path
          else if (direct && abs && !mt && p3 == "") p3 = path
        }
      }
      ev = nm = ""; direct = abs = mt = pen = 0
    }
    /^N: / { nm = $0; sub(/^N: Name="/, "", nm); sub(/"$/, "", nm) }
    /^H: / { h = $0; sub(/^H: Handlers=/, "", h); n = split(h, hv, " "); for (k = 1; k <= n; k++) if (hv[k] ~ /^event[0-9]+$/) ev = hv[k] }
    /^B: PROP=/ {
      v = $0; sub(/^B: PROP=/, "", v); n = split(v, w, " ")
      if (n >= 1) { c = substr(w[n], length(w[n]), 1); direct = (c ~ /[2367abefABEF]/) }
    }
    /^B: ABS=/ {
      v = $0; sub(/^B: ABS=/, "", v); abs = (v ~ /[1-9a-fA-F]/)
      n = split(v, w, " ")
      if (n >= 1 && length(w[n]) >= 14) {
        mt = (substr(w[n], length(w[n]) - 13, 1) ~ /[67efEF]/)
      } else if (bits == 32 && n >= 2 && length(w[n - 1]) >= 6) {
        mt = (substr(w[n - 1], length(w[n - 1]) - 5, 1) ~ /[67efEF]/)
      }
    }
    /^B: KEY=/ {
      v = $0; sub(/^B: KEY=/, "", v); n = split(v, w, " ")
      kw = (bits == 64 && n >= 6) ? w[n - 5] : (bits == 32 && n >= 11) ? w[n - 10] : ""
      if (kw != "") pen = (substr(kw, length(kw), 1) ~ /[1-9a-fA-F]/)
    }
    NF == 0 { flush() }
    END {
      flush()
      if (p1 != "") print p1
      else if (p2 != "") print p2
      else if (p3 != "") print p3
      else if (p4 != "") print p4
    }
  ' "$_file"
}

# Optional $1 overrides /proc/bus/input/devices for tests.
touch_node() {
  input_pick touch "" "$1"
}

# Find a separate pen/stylus digitizer node (excluding $1, the chosen raw touch node).
stylus_node() {
  input_pick stylus "$1" "$2"
}

touch_idc_names() {   # $1 = /dev/input/eventN -> "<name> Vendor_xxxx_Product_yyyy" (Android looks for <name>.idc, then the Vendor_ one)
  want=${1##*/}
  input_table | while IFS=$TAB read -r ev ven prod nm; do
    [ "$ev" = "$want" ] || continue
    name=$(printf '%s' "$nm" | tr -c 'A-Za-z0-9._-' '_')
    vp=
    if [ "$ven" != - ] && [ "$prod" != - ] && [ "${ven}_${prod}" != 0000_0000 ]; then vp=Vendor_${ven}_Product_${prod}; fi
    echo "$name $vp"
    break
  done
}

keys_node() {         # event node of the virtual keyboard that kgest creates
  input_table | while IFS=$TAB read -r ev ven prod nm; do
    [ "$nm" = embrith-keys ] && { echo "/dev/input/$ev"; break; }
  done
}

vtouch_node() {       # event node of the virtual touch screen kgest creates with --grab (the real one is grabbed)
  input_table | while IFS=$TAB read -r ev ven prod nm; do
    [ "$nm" = embrith-touch ] && { echo "/dev/input/$ev"; break; }
  done
}

# Android gets its OWN binder device: the Kindle already runs a service manager on /dev/binder
make_devices() {
  rm -rf "$DEVDIR"; mkdir -p "$DEVDIR/input"
  B=
  if [ "$BINDER_DEV" = auto ]; then CAND="hwbinder vndbinder"; else CAND="$BINDER_DEV"; fi
  for d in $CAND; do
    [ -e "/dev/$d" ] || continue
    if dev_in_use "$d"; then say "/dev/$d is in use by the Kindle, skipping"; else B=$d; break; fi
  done
  [ -n "$B" ] || die "no unused binder device (hwbinder/vndbinder) available"
  say "Android will use /dev/$B as its binder"
  cp -a "/dev/$B" "$DEVDIR/binder" || die "cannot copy /dev/$B"
  for n in null zero full tty random urandom; do cp -a "/dev/$n" "$DEVDIR/$n" || die "cannot copy /dev/$n"; done
  [ -c /dev/fuse ] && cp -a /dev/fuse "$DEVDIR/fuse"
  # event0 / event2 (power button, cover sensor) are NOT given to Android: it would go to sleep by itself on the power key (screen off,
  # single-finger touch dropped) and powerd would never be asked to suspend. The Kindle side handles those (power_watch).
  # the touch screen always appears inside the container as /dev/input/event1 (init.kindle.rc binds that one)
  TN_RAW=$(touch_node)
  TN=$(vtouch_node); [ -n "$TN" ] && say "touch is grabbed by kgest, Android gets embrith-touch"
  [ -n "$TN" ] || TN=$TN_RAW
  if [ -n "$TN" ]; then
    cp -a "$TN" "$DEVDIR/input/event1" && say "touch screen: $TN" || say "WARNING: cannot copy the touch node $TN"
    export KD_TOUCH_NAMES="$(touch_idc_names "$TN")"
  else
    say "WARNING: no touch screen found in /proc/bus/input/devices (Android will not react to touches)"
  fi
  SN=$(stylus_node "$TN_RAW")
  if [ -n "$SN" ] && [ "$SN" != "$TN" ] && [ "$SN" != "$TN_RAW" ]; then
    cp -a "$SN" "$DEVDIR/input/stylus" && say "stylus: $SN" || say "WARNING: cannot copy the stylus node $SN"
    export KD_STYLUS_NAMES="$(touch_idc_names "$SN")"
  else
    export KD_STYLUS_NAMES=""
  fi
  KN=$(keys_node)
  if [ -n "$KN" ]; then cp -a "$KN" "$DEVDIR/input/keys" && say "virtual keys: $KN"; fi
}
