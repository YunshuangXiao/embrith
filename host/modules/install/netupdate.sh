#!/bin/sh
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Embrith contributors
# host/modules/install/netupdate.sh - update Embrith from the newest GitHub release. Its own channel: it never touches embrith/src (the manual
# package, see update.sh); the release is unpacked into embrith/net/src and installed by "update.sh --restart-net".
#   netupdate.sh check   ask GitHub for the latest release: ctl/netupd (state), ctl/netupd_log (the change log of the release)
#   netupdate.sh get     check, then download the release package into $SRC/net/src (state=ready when it is whole); the documents/ of the zip is kept in $SRC/net/documents
#   netupdate.sh auto    for the auto update switch: check at most once per 20 h, download a newer release, mark it (net/auto)
#                        so that the next normal start installs it
# ctl/netupd, one key=value per line: state=checking|latest|new|downloading|ready|error  cur=<installed>  new=<release>  msg=<error code>
#   error codes: nocurl nounzip nonet norelease badtag noasset badpkg
# The release needs an asset ending in .zip: the output of host/tools/make-package.sh (src/ and documents/; VERSION inside src/).
# Needs curl (https) and unzip on the Kindle. The zip is kept on disk while it is unpacked, then deleted.

REPO=yunshuangxiao/embrith
API=https://api.github.com/repos/$REPO/releases/latest
R=/mnt/base-us; [ -d "$R" ] || R=/mnt/us
SRC=$R/embrith
DEST=$R/system/embrith
NET=$SRC/net
K_CTL=/var/tmp/kdisp/ctl
ST=/var/tmp/embrith-state
HERE=$(cd "$(dirname "$0")" && pwd)

mkdir -p "$ST" "$SRC/logs" 2>/dev/null
log() { echo "$(date '+%m-%d %H:%M:%S') netupdate: $*" >> "$SRC/logs/install.log"; }

CUR=$(head -n 1 "$DEST/VERSION" 2>/dev/null | tr -d '\r\n ')
[ -n "$CUR" ] || CUR=$(head -n 1 "$HERE/../../VERSION" 2>/dev/null | tr -d '\r\n ')
NEW=; URL=

put() {            # $1 = state, $2 = error code
  [ -d "$K_CTL" ] || return 0
  { echo "state=$1"; echo "cur=$CUR"; echo "new=$NEW"; echo "msg=$2"; } > "$K_CTL/netupd.tmp" && chmod 666 "$K_CTL/netupd.tmp" && mv "$K_CTL/netupd.tmp" "$K_CTL/netupd"
}

valid_ver() { echo "$1" | grep -Eq '^[0-9]+(\.[0-9]+){2,3}$'; }
ver_gt() {          # $1 > $2 ? field by field as numbers
  i=1
  while [ $i -le 4 ]; do
    a=$(echo "$1" | cut -d. -f$i); b=$(echo "$2" | cut -d. -f$i)
    a=${a:-0}; b=${b:-0}
    [ "$a" -gt "$b" ] && return 0
    [ "$a" -lt "$b" ] && return 1
    i=$((i + 1))
  done
  return 1
}

# ask GitHub: sets NEW and URL, writes the change log. Returns 1 after put error.
fetch() {
  command -v curl >/dev/null 2>&1 || { log "curl not found"; put error nocurl; return 1; }
  command -v unzip >/dev/null 2>&1 || { log "unzip not found"; put error nounzip; return 1; }
  J=$ST/netupd.json; rm -f "$J"
  curl -fsSL --connect-timeout 15 -m 40 -H 'Accept: application/vnd.github+json' "$API" -o "$J" 2>/dev/null
  rc=$?
  if [ $rc -ne 0 ]; then
    log "release query failed, curl exit $rc"
    case $rc in 22) put error norelease ;; *) put error nonet ;; esac
    return 1
  fi
  tag=$(sed -n 's/^  "tag_name": *"\([^"]*\)".*/\1/p' "$J" | head -n 1)
  NEW=${tag#v}
  valid_ver "$NEW" || { log "bad tag: $tag"; NEW=; put error badtag; return 1; }
  URL=$(grep -o '"browser_download_url": *"[^"]*\.zip"' "$J" | head -n 1 | sed 's/^[^:]*: *"//; s/"$//')
  sed -n 's/^  "body": *"\(.*\)",*$/\1/p' "$J" | head -n 1 | sed 's/\\r//g; s/\\n/\n/g; s/\\t/  /g; s/\\"/"/g; s/\\\\/\\/g' > "$K_CTL/netupd_log.tmp"
  chmod 666 "$K_CTL/netupd_log.tmp" 2>/dev/null; mv "$K_CTL/netupd_log.tmp" "$K_CTL/netupd_log"
  echo "$URL" > "$ST/netupd.url"
  return 0
}

# the state after a successful query
settle() {
  if [ -n "$CUR" ] && ! ver_gt "$NEW" "$CUR"; then put latest; return; fi
  if [ -f "$NET/src/VERSION" ] && [ ! -f "$ST/netupd.busy" ] && [ "$(head -n 1 "$NET/src/VERSION" | tr -d '\r\n ')" = "$NEW" ]; then put ready; return; fi
  put new
}

do_check() {
  put checking
  fetch || return 1
  settle
}

do_get() {          # $1 = auto
  put checking
  fetch || return 1
  if [ -n "$CUR" ] && ! ver_gt "$NEW" "$CUR"; then put latest; return 0; fi
  [ -n "$URL" ] || { log "no .zip asset"; put error noasset; return 1; }
  put downloading
  : > "$ST/netupd.busy"
  rm -rf "$NET"; mkdir -p "$NET/tmp" || { rm -f "$ST/netupd.busy"; put error badpkg; return 1; }
  curl -fsSL --connect-timeout 20 --retry 2 "$URL" -o "$NET/pkg.zip" 2>/dev/null
  rc=$?
  if [ $rc -ne 0 ]; then
    log "download failed, curl exit $rc"; rm -rf "$NET"; rm -f "$ST/netupd.busy"; put error nonet; return 1
  fi
  unzip -q -o "$NET/pkg.zip" -d "$NET/tmp" >/dev/null 2>&1
  rc=$?; rm -f "$NET/pkg.zip"
  if [ $rc -ne 0 ]; then
    log "unzip failed, exit $rc"; rm -rf "$NET"; rm -f "$ST/netupd.busy"; put error badpkg; return 1
  fi
  # the package folder is src/ (also when the zip has one more folder around it)
  P=
  for d in "$NET/tmp/src" "$NET"/tmp/*/src "$NET/tmp"; do [ -f "$d/VERSION" ] && { P=$d; break; }; done
  if [ -n "$P" ]; then
    D=; case "$P" in */src) D=${P%/src}/documents ;; esac
    mv "$P" "$NET/src"; [ -n "$D" ] && [ -d "$D" ] && mv "$D" "$NET/documents"; rm -rf "$NET/tmp"
  else log "package has no VERSION"; rm -rf "$NET"; rm -f "$ST/netupd.busy"; put error badpkg; return 1; fi
  if [ "$(head -n 1 "$NET/src/VERSION" | tr -d '\r\n ')" != "$NEW" ]; then
    log "package version differs from the tag $NEW"; rm -rf "$NET"; rm -f "$ST/netupd.busy"; put error badpkg; return 1
  fi
  [ "$1" = auto ] && : > "$NET/auto"
  rm -f "$ST/netupd.busy"
  log "downloaded $NEW"
  put ready
}

[ -d "$K_CTL" ] || exit 0
if [ -f "$ST/netupd.busy" ] && [ -n "$(find "$ST/netupd.busy" -mmin -30 2>/dev/null)" ]; then exit 0; fi    # a download is running
case "$1" in
  check) do_check ;;
  get) do_get ;;
  auto)
    [ -n "$(find "$SRC/net.last" -mmin -1200 2>/dev/null)" ] && exit 0
    : > "$SRC/net.last" 2>/dev/null
    do_get auto ;;
esac
exit 0
