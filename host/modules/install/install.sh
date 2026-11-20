# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Embrith contributors
# host/install/install.sh - install an apk from the Kindle storage into the container.
# Sourced by host/embrith.sh.

do_install() {
  a="$1"; h=$(echo "$a" | sed "s#^/dev/kindle/#${KINDLE_USER:-/mnt/base-us}/#")
  res() { echo "$1" > "$K_CTL/install_result.tmp" && chmod 666 "$K_CTL/install_result.tmp" && mv "$K_CTL/install_result.tmp" "$K_CTL/install_result"; }
  res busy
  [ -f "$h" ] || { res "fail: file not found ($h)"; return; }
  mkdir -p "$BASE/data/local/tmp" && cp "$h" "$BASE/data/local/tmp/app.apk" && chmod 644 "$BASE/data/local/tmp/app.apk" || { res "fail: cannot copy the file"; return; }
  t=240; [ "$(conf_val DEX2OAT)" = 1 ] && t=1800     # with the switch on, the speed compile runs inside pm install
  out=$(ct_sh_t "$t" "pm install -r -d -g /data/local/tmp/app.apk" 2>&1)
  rm -f "$BASE/data/local/tmp/app.apk"
  case "$out" in *Success*) res ok; say "installed $h" ;; *) res "fail: $(echo "$out" | tail -n 1 | cut -c1-120)"; say "install of $h failed: $out" ;; esac
}

# embrith.sh install <apk>: the file must be on the Kindle; the same install path as the launcher uses
cmd_install() {
  alive || die "not running"; [ -f "$1" ] || die "no such file: $1"
  mkdir -p "$BASE/data/local/tmp" && cp "$1" "$BASE/data/local/tmp/app.apk" && chmod 644 "$BASE/data/local/tmp/app.apk" \
  && ct_sh "pm install -r -d -g /data/local/tmp/app.apk"
}
