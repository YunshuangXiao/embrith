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

# The touch screen is found by what the device reports, not by its name: a direct input device (INPUT_PROP_DIRECT, bit 1 of the
# lowest PROP word) that has absolute axes. Our own virtual devices (embrith-*) are skipped.
touch_node() {
  awk '
    function flush() {
      if (!done && ev != "" && direct && abs && nm !~ /^embrith-/) { print "/dev/input/" ev; done = 1 }
      ev = nm = ""; direct = abs = 0
    }
    /^N: / { nm = $0; sub(/^N: Name="/, "", nm); sub(/"$/, "", nm) }
    /^H: / { h = $0; sub(/^H: Handlers=/, "", h); n = split(h, hv, " "); for (k = 1; k <= n; k++) if (hv[k] ~ /^event[0-9]+$/) ev = hv[k] }
    /^B: PROP=/ { v = $0; sub(/^B: PROP=/, "", v); n = split(v, w, " "); c = substr(w[n], length(w[n])); direct = (c ~ /[2367abef]/) }
    /^B: ABS=/ { v = $0; sub(/^B: ABS=/, "", v); abs = (v ~ /[1-9a-f]/) }
    NF == 0 { flush() }
    END { flush() }
  ' /proc/bus/input/devices
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
  TN=$(vtouch_node); [ -n "$TN" ] && say "touch is grabbed by kgest, Android gets embrith-touch"
  [ -n "$TN" ] || TN=$(touch_node)
  if [ -n "$TN" ]; then
    cp -a "$TN" "$DEVDIR/input/event1" && say "touch screen: $TN" || say "WARNING: cannot copy the touch node $TN"
    export KD_TOUCH_NAMES="$(touch_idc_names "$TN")"
  else
    say "WARNING: no touch screen found in /proc/bus/input/devices (Android will not react to touches)"
  fi
  KN=$(keys_node)
  if [ -n "$KN" ]; then cp -a "$KN" "$DEVDIR/input/keys" && say "virtual keys: $KN"; fi
}
