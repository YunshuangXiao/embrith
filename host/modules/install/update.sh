#!/bin/sh
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Embrith contributors
# host/modules/install/update.sh - install and update embrith itself. Run by documents/embrith-start.sh before "embrith.sh start":
#   update.sh            check $SRC/src and install it into $DEST when it is allowed; exit 0 = $DEST can be started, 1 = nothing to start
#   update.sh --restart-force  like --restart, but no check and no version compare for this one run (as developer mode)
#   update.sh --restart-net  like --restart, for the package that netupdate.sh downloaded ($SRC/net/src, the GitHub release): its own entry, not the local $SRC/src
#   update.sh --restart  "restart and update": same as a start, but no guard on kdispd, no screen output, no wait (supervise.sh)
#   update.sh --commit   Android has booted with the installed version (container.sh): delete the .old copies and the .pending marker
# $SRC (/mnt/us/embrith) is the user folder, $DEST (/mnt/us/system/embrith) the program folder. Both are used through /mnt/base-us: installing is
# mv only, a rename on the same ext4 (through the /mnt/us view it could become a copy of the 350 MB image).
# The package ($SRC/src, made by host/tools/make-package.sh): embrith.sh VERSION LICENSE bin/ modules/, and with embrith.raw also check.txt.
#   check.txt: first line "#embrith-pkg <version> <number of files>", then "<sha256>  <size>  <path>" per file, last line "#end".
# Normal mode: the package must be complete and its VERSION higher than the installed one.
#   With check.txt (a package with embrith.raw) every size and sha256 must be right. A package that was checked is remembered in $DEST/.src.mark
#   ("<fingerprint> ok|bad", the fingerprint is size and time of every file): an unchanged package is not hashed again.
#   Without check.txt (code only, no embrith.raw) only the required files and "sh -n" of every script are checked: a binary that was cut off is not noticed.
# Developer mode (DEV_MODE=1 in $SRC/settings/custom.conf): no check, no version compare, every item of the list that is in $SRC/src is installed.
# Rollback: a replaced item is kept as <name>.old. .installing = an install that was interrupted, .pending = installed, Android has not booted with it
#   yet. Both are rolled back by the next run; a package that did not boot goes to $SRC/src.failed. data/ and settings/ are never touched.

R=/mnt/base-us; [ -d "$R" ] || R=/mnt/us
SRC=$R/embrith
PKG=$SRC/src          # the package folder: $SRC/src (put there by hand), $SRC/net/src with --restart-net or an auto-downloaded release
DEST=$R/system/embrith
ITEMS="embrith.sh VERSION LICENSE embrith.raw bin modules check.txt"
FBINK=/mnt/us/libkh/bin/fbink
ROW=8; NOTED=; QUIET=

log() { mkdir -p "$SRC/logs" 2>/dev/null; echo "$(date '+%m-%d %H:%M:%S') $*" >> "$SRC/logs/install.log"; }
# $1 = Chinese (log), $2 = English (screen: no Chinese font is needed). every line shown on the screen makes leave() wait 5 s before Launching, so that the lines can be read
busy() { log "$1"; ROW=$((ROW + 2)); NOTED=1; [ -z "$QUIET" ] && [ -x "$FBINK" ] && "$FBINK" -q -y "$ROW" "$2" >/dev/null 2>&1; }
show() { busy "$1" "$2"; }
FAILED=
fail() { FAILED=1; show "$1" "$2"; }   # an update that did not work: leave() then makes a report (logs/report, it includes install.log)
report() { [ -f "$DEST/embrith.sh" ] && sh "$DEST/embrith.sh" report </dev/null >/dev/null 2>&1; }
emb() { if [ -n "$1" ]; then echo "Embrith $1"; else echo "Embrith"; fi; }   # the name with the version when there is one
leave() {
  [ -n "$FAILED" ] && { log "生成日志"; report; }
  [ -f "$DEST/embrith.sh" ] || show "没有已安装的 embrith, 也没有可用的安装包" "No installed Embrith and no usable package"
  [ -n "$NOTED" ] && [ -z "$QUIET" ] && sleep 5
  [ -f "$DEST/embrith.sh" ] && [ -z "$QUIET" ] && busy "正在启动" "$(emb "$(head -n 1 "$DEST/VERSION" 2>/dev/null | tr -d '\r\n ')"): Launching..."
  [ -f "$DEST/embrith.sh" ]; exit $?
}

commit() {
  for x in $ITEMS; do rm -rf "$DEST/$x.old"; done
  rm -f "$DEST/.pending" "$DEST/.src.mark"
}

# put the replaced items back. $1 = list file, $2 = src (interrupted install: the package stays usable) or failed (it did not boot: $SRC/src.failed)
undo() {
  [ -f "$1" ] || return 0
  if [ "$2" = failed ]; then rm -rf "$SRC/src.failed"; T=$SRC/src.failed; else T=$PKG; fi
  mkdir -p "$T"
  while read -r x; do
    [ -e "$DEST/$x.old" ] || continue
    if [ -e "$DEST/$x" ]; then rm -rf "$T/$x"; mv "$DEST/$x" "$T/$x"; fi
    mv "$DEST/$x.old" "$DEST/$x"
  done < "$1"
  rm -f "$1"
}

# the launcher scripts of a release (net/documents, from the documents/ of its zip) go to documents/embrith/ of the Kindle
docs_sync() {
  [ -d "$SRC/net/documents" ] || return 0
  if mkdir -p "$R/documents/embrith" && cp -rf "$SRC/net/documents/." "$R/documents/embrith/"; then log "已更新 documents/embrith"; else log "documents/embrith 复制失败"; fi
}

# move the items that are in $PKG into $DEST (the list goes to .installing first, then .pending)
import() {
  : > "$DEST/.installing" || return 1
  for x in $ITEMS; do
    [ -e "$PKG/$x" ] || continue
    echo "$x" >> "$DEST/.installing"
    rm -rf "$DEST/$x.old"
    if [ -e "$DEST/$x" ]; then mv "$DEST/$x" "$DEST/$x.old" || return 1; fi
    mv "$PKG/$x" "$DEST/$x" || return 1
  done
  mv "$DEST/.installing" "$DEST/.pending" || return 1
  rm -f "$DEST/.src.mark"
  rmdir "$PKG" 2>/dev/null
  [ "$PKG" = "$SRC/net/src" ] && { docs_sync; rm -rf "$SRC/net"; }
  return 0
}

valid_ver() { echo "$1" | grep -Eq '^[0-9]+(\.[0-9]+){2,3}$'; }
ver_gt() {          # $1 > $2 ? X.Y.Z or X.Y.Z.NN, compared field by field as numbers
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

fp() { (cd "$PKG" && find . -type f -exec ls -lni {} \; | sort | cksum | tr ' ' '-'); }

# a package without check.txt: only the items of the list, the required files, no embrith.raw, and every script is whole (sh -n)
light() {
  P=$PKG
  [ -f "$P/embrith.sh" ] && [ -f "$P/VERSION" ] && [ -d "$P/bin" ] && [ -d "$P/modules" ] || return 1
  [ -e "$P/embrith.raw" ] && return 1
  for f in $(ls -A "$P"); do case " $ITEMS " in *" $f "*) ;; *) return 1 ;; esac; done
  for f in "$P/embrith.sh" $(find "$P/modules" -name '*.sh'); do sh -n "$f" 2>/dev/null || return 1; done
  return 0
}

# the package is complete and right: check.txt is whole, lists exactly the files that are there, and every size and sha256 is right
verify() {
  P=$PKG; C=$P/check.txt
  [ -f "$C" ] && [ -f "$P/embrith.sh" ] && [ -f "$P/VERSION" ] && [ -d "$P/bin" ] && [ -d "$P/modules" ] || return 1
  head -n 1 "$C" | grep -q '^#embrith-pkg ' || return 1
  [ "$(tail -n 1 "$C")" = '#end' ] || return 1
  [ "$(head -n 1 "$C" | cut -d' ' -f2)" = "$(head -n 1 "$P/VERSION" | tr -d '\r\n ')" ] || return 1
  n=$(grep -vc '^#' "$C")
  [ "$(head -n 1 "$C" | cut -d' ' -f3)" = "$n" ] || return 1
  [ "$(find "$P" -type f ! -name check.txt | wc -l | tr -d ' ')" = "$n" ] || return 1
  grep -v '^#' "$C" | while read -r h s p; do
    case "$p" in *..*) exit 1 ;; esac
    case "$p" in embrith.sh|VERSION|LICENSE|embrith.raw|bin/*|modules/*) ;; *) exit 1 ;; esac
    [ -f "$P/$p" ] || exit 1
    [ "$(wc -c < "$P/$p" | tr -d ' ')" = "$s" ] || exit 1
    [ "$(sha256sum "$P/$p" | cut -d' ' -f1)" = "$h" ] || exit 1
  done
}

[ "$1" = --commit ] && { commit; exit 0; }
# --restart: called by supervise.sh between the stop and the start of "restart and update": the container is down but kdispd stays up (it shows the restart page), so the guard is skipped and nothing is drawn or waited for
case "$1" in --restart|--restart-force|--restart-net) QUIET=1 ;; *) pidof kdispd >/dev/null 2>&1 && exit 0 ;; esac      # Android is running (or starting): never touch the files under it

mkdir -p "$SRC/logs" "$SRC/settings"
[ "$1" = --restart-net ] && PKG=$SRC/net/src
# a release that the auto update downloaded (net/auto) is installed by the next normal start, when nothing was put into src by hand
[ -z "$1" ] && [ -f "$SRC/net/auto" ] && [ -d "$SRC/net/src" ] && { [ ! -d "$SRC/src" ] || [ -z "$(ls -A "$SRC/src" 2>/dev/null)" ]; } && PKG=$SRC/net/src
DEV=$(sed -n 's/^DEV_MODE=//p' "$SRC/settings/custom.conf" 2>/dev/null | head -n1 | tr -dc '01')
[ "$1" = --restart-force ] && { DEV=1; log "强制更新: 不校验, 不比较版本号"; }

# 1. what an earlier run left behind
if [ -f "$DEST/.installing" ]; then
  undo "$DEST/.installing" src
  fail "上次安装被中断, 已还原" "The last install was interrupted, restored"
fi
if [ -f "$DEST/.pending" ]; then
  undo "$DEST/.pending" failed; rm -f "$DEST/.src.mark"
  fail "上次更新没有启动成功, 已回退, 新文件放在 $SRC/src.failed" "The last update did not boot, rolled back"
fi

# 2. is there a package
if [ ! -d "$PKG" ] || [ -z "$(ls -A "$PKG" 2>/dev/null)" ]; then
  rm -f "$DEST/.src.mark"
  [ "$DEV" = 1 ] && [ "$1" != --restart-force ] && log "开发模式: 开"
  leave
fi

# 3. developer mode: no check, no version compare
if [ "$DEV" = 1 ]; then
  log "开发模式: 不校验, 不比版本, 安装 $PKG 里清单内的文件"
  if [ -d "$DEST" ] || { mkdir -p "$DEST" && chmod 700 "$DEST"; }; then
    busy "开发模式: 正在安装" "$(emb "$(head -n 1 "$PKG/VERSION" 2>/dev/null | tr -d '\r\n ')"): Installing..."
    if import; then show "开发模式: 已安装 src 里的文件(未校验)" "Developer mode: installed the package (not checked)"
    else undo "$DEST/.installing" src; fail "开发模式: 安装失败, 已还原" "Developer mode: install failed, restored"; fi
  fi
  [ -d "$PKG" ] && show "src 里有不在清单内的文件, 已忽略: $(ls -A "$PKG" | tr '\n' ' ')" "Files in src that are not on the install list were ignored"
  leave
fi

# 4. normal mode: version
NEW=$(head -n 1 "$PKG/VERSION" 2>/dev/null | tr -d '\r\n ')
OLD=$(head -n 1 "$DEST/VERSION" 2>/dev/null | tr -d '\r\n ')
valid_ver "$NEW" || { fail "安装包不完整: VERSION 缺失或格式不对, 已跳过" "Package incomplete (VERSION), skipped"; leave; }
if [ -f "$DEST/embrith.sh" ] && valid_ver "$OLD" && ! ver_gt "$NEW" "$OLD"; then
  show "安装包版本 $NEW 不高于已安装的 $OLD, 已跳过(src 里的文件保留)" "Package $NEW is not newer than installed $OLD, skipped"
  leave
fi

# 5. normal mode: check
if [ ! -f "$PKG/check.txt" ]; then
  busy "正在校验文件" "$(emb "$NEW"): Verifying files..."
  light || { fail "安装包不完整(没有 check.txt 的包只能是不含 embrith.raw 的代码包), 已跳过" "Package incomplete, skipped"; leave; }
else
  # with check.txt: once per package (see .src.mark)
  F=$(fp); MF=; MS=
  [ -f "$DEST/.src.mark" ] && read -r MF MS < "$DEST/.src.mark"
  if [ "$MF" = "$F" ] && [ "$MS" = ok ]; then :
  elif [ "$MF" = "$F" ] && [ "$MS" = bad ]; then
    fail "安装包校验失败(上次已记录), 已跳过, 请重新上传 src" "Package check failed (recorded), skipped. Upload it again"
    leave
  else
    busy "正在校验文件" "$(emb "$NEW"): Verifying files..."
    if verify; then r=ok; else r=bad; fi
    [ -d "$DEST" ] && echo "$F $r" > "$DEST/.src.mark"
    [ "$r" = ok ] || { fail "安装包校验失败, 已跳过(是否没传完?)" "Package check failed, skipped"; leave; }
  fi
fi

# 6. install
if [ -d "$DEST" ] || { mkdir -p "$DEST" && chmod 700 "$DEST"; }; then
  busy "正在安装 $NEW" "$(emb "$NEW"): Installing..."
  if import; then log "已安装 $NEW"
  else undo "$DEST/.installing" src; fail "安装失败, 已还原" "Install failed, restored"; fi
fi
leave
