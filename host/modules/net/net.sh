# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Embrith contributors
# host/net/net.sh - network for Android (slirp4netns).
# Sourced by host/embrith.sh.

# ---- network for Android: the Kindle's kernel has no veth, so slirp4netns (a static user-space NAT, official release,
# put it into bin/) creates an "eth0" in the container's network namespace and forwards everything through the Kindle's Wi-Fi.
net_start() {
  if [ ! -x "$RUNBIN/slirp4netns" ]; then
    say "no bin/slirp4netns: Android will have no network (put the armv7l static slirp4netns into embrith/bin/, see docs/ARCHITECTURE.md)"
    return 0
  fi
  [ -c /dev/net/tun ] || say "WARNING: /dev/net/tun is missing, slirp4netns will fail"
  "$RUNBIN/slirp4netns" --mtu=1500 "$(init_pid)" eth0 >> "$LOGD/net.log" 2>&1 &
  echo $! > "$ST/slirp.pid"
  say "network: slirp4netns started (log: $LOGD/net.log)"
  rm -f "$ST/netid"
  net_fix &
}

# netd 7.1 fails addInterfaceToNetwork (uidrange rule rejected by the kernel). Once netd has created the
# fwmark rule at pref 13000, add the missing table 1004 and rules by hand. netId is read from the rule, not hardcoded.
# the container's own PATH has no ip: run the Kindle's ip inside the container's network namespace only
nip() { timeout 5 nsenter -t "$(init_pid)" -n ip "$@"; }
net_fix() {
  n=0; id=
  while [ $n -lt 120 ]; do
    [ -f "$ST/init.pid" ] || return 0
    id=$(nip rule 2>/dev/null | sed -n 's/^13000:.*fwmark 0x1\([0-9a-f]*\)\/0x1ffff lookup 1004.*/\1/p' | head -n1)
    [ -n "$id" ] && break
    n=$((n+1)); sleep 2
  done
  [ -n "$id" ] || { say "network: no pref 13000 rule after 240s, netd rules not added"; return 0; }
  nip rule 2>/dev/null | grep -q '^20000:' && return 0
  nip route add 10.0.2.0/24 dev eth0 table 1004
  nip route add default via 10.0.2.2 dev eth0 table 1004
  nip rule add pref 14000 oif eth0 lookup 1004 2>/dev/null   # a busybox ip without oif fails here: not needed
  nip rule add pref 20000 fwmark 0x$id/0xffff lookup 1004
  nip rule add pref 22000 fwmark 0x0/0xffff lookup 1004
  say "network: netd rules added by hand (netId 0x$id)"
  printf '%d\n' "0x$id" > "$ST/netid"
  sleep 3; dns_apply boot
}

# DNS servers of Android (netd), as the user chose them: DNS= in ctl/conf, comma separated IPv4, empty = what DHCP gave (slirp, 10.0.2.3).
# DHCP can put the default back, so it is applied again once the network is up ("boot": nothing to do when the user chose nothing).
dns_apply() {
  [ -s "$ST/netid" ] || return 0
  d=$(sed -n 's/^DNS=//p' "$K_CTL/conf" 2>/dev/null | head -n1 | tr -dc '0-9.,')
  [ "$1" = boot ] && [ -z "$d" ] && return 0
  [ -n "$d" ] || d=10.0.2.3
  if ct_sh_t 20 "ndc resolver setnetdns $(cat "$ST/netid") '' $(printf '%s' "$d" | tr ',' ' ')" >/dev/null 2>&1; then say "network: dns $d"; else say "network: dns $d FAILED"; fi
}