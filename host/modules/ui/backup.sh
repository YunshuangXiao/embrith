# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Embrith contributors
# host/modules/ui/backup.sh - export / import of settings (sourced by ctl.sh on ctl/backup_export and ctl/backup_import).
#   request: "ID cat,cat,..." ; categories: sys (custom.conf), disp (display.conf), widget (launcher Prefs incl. wallpaper paths), ssh (SSH_ keys, /mnt/us/.ssh),
#   lib (reading records of the library), usage (usage statistics). Answer: ctl/backup (id, state = run|done|err, msg).
#   The Prefs of the launcher live inside Android: the launcher writes backup/stage/prefs.tsv and usage.tsv before an export and
#   reads them after an import. Files go to <embrith folder>/backup/embrith-DATE.tar (visible over USB).
BK_DIR=$SRC/backup
BK_MEDIA=$BASE/data/media/0/Embrith
BK_SSHDIR=/mnt/us/.ssh   # keys and authorized_keys of ssh-toggle.sh

bk_status() {   # ID STATE MSG
  printf 'id=%s\nstate=%s\nmsg=%s\n' "$1" "$2" "$3" > "$K_CTL/backup.tmp" 2>/dev/null && chmod 666 "$K_CTL/backup.tmp" && mv "$K_CTL/backup.tmp" "$K_CTL/backup"
}
bk_has() { case ",$2," in *",$1,"*) return 0 ;; esac; return 1; }

bk_export() {   # ID CATS [PREFIX]
  id=$1; cats=$2; pre=${3:-embrith}; w=$ST/bk-out
  bk_status "$id" run ""
  rm -rf "$w"; mkdir -p "$w" "$BK_DIR" || { bk_status "$id" err "cannot create the backup folder"; return 1; }
  if bk_has sys "$cats"; then set_sync 2>/dev/null; cp "$SET_MEM" "$w/custom.conf" 2>/dev/null; fi
  if bk_has disp "$cats"; then cp "$SET/display.conf" "$w/display.conf" 2>/dev/null; fi
  if bk_has widget "$cats"; then cp "$BK_MEDIA/backup/stage/prefs.tsv" "$w/prefs.tsv" 2>/dev/null; fi
  if bk_has usage "$cats"; then cp "$BK_MEDIA/backup/stage/usage.tsv" "$w/usage.tsv" 2>/dev/null; fi
  if bk_has ssh "$cats"; then
    grep '^SSH_' "$SET/custom.conf" > "$w/ssh.conf" 2>/dev/null
    [ -d "$BK_SSHDIR" ] && { mkdir -p "$w/ssh"; cp -R "$BK_SSHDIR/." "$w/ssh/" 2>/dev/null; }
  fi
  if bk_has lib "$cats"; then
    mkdir -p "$w/library"
    for f in state recent.tsv folders.txt meta.tsv; do [ -e "$BK_MEDIA/library/$f" ] && cp -R "$BK_MEDIA/library/$f" "$w/library/"; done
  fi
  if bk_has usage "$cats"; then   # the full reading-time record (readlog.tsv, and time.tsv of older versions) belongs to the statistics
    mkdir -p "$w/stats"
    for f in readlog.tsv time.tsv; do [ -e "$BK_MEDIA/library/$f" ] && cp "$BK_MEDIA/library/$f" "$w/stats/"; done
  fi
  printf 'version=1\ncats=%s\ndate=%s\n' "$cats" "$(date +%Y%m%d-%H%M%S)" > "$w/manifest"
  out=$BK_DIR/$pre-$(date +%Y%m%d-%H%M%S).tar
  if tar -cf "$out" -C "$w" . 2>/dev/null; then
    [ "$pre" = embrith ] && bk_status "$id" done "$(basename "$out")"
  else
    rm -f "$out"; bk_status "$id" err "tar failed"; rm -rf "$w"; return 1
  fi
  rm -rf "$w"
}

bk_import() {   # ID CATS: the newest embrith-*.tar in the backup folder
  id=$1; want=$2; w=$ST/bk-in
  bk_status "$id" run ""
  src=$(ls -1t "$BK_DIR"/embrith-*.tar 2>/dev/null | head -n 1)
  [ -n "$src" ] || { bk_status "$id" err "no backup file in embrith/backup"; return 1; }
  rm -rf "$w"; mkdir -p "$w"
  tar -xf "$src" -C "$w" 2>/dev/null && [ -f "$w/manifest" ] || { bk_status "$id" err "not a backup file"; rm -rf "$w"; return 1; }
  have=$(sed -n 's/^cats=//p' "$w/manifest" | head -n 1)
  cats=; for c in $(echo "$want" | tr ',' ' '); do bk_has "$c" "$have" && cats="$cats,$c"; done
  cats=${cats#,}
  [ -n "$cats" ] || { bk_status "$id" err "the backup has none of the chosen types (it has: $have)"; rm -rf "$w"; return 1; }
  bk_export "$id" "$cats" pre-import >/dev/null 2>&1   # the current state is kept before it is replaced
  if bk_has sys "$cats" && [ -f "$w/custom.conf" ]; then
    while IFS='=' read -r k v; do
      case "$k" in ''|'#'*|SSH_*|APP_COUNT|DEV_MODE|*[!A-Z0-9_]*) continue ;; esac
      set_put "$k" "$v"; conf_put "$k" "$v"
    done < "$w/custom.conf"
  fi
  if bk_has disp "$cats" && [ -f "$w/display.conf" ]; then cp "$w/display.conf" "$SET/display.conf.import"; fi
  mkdir -p "$BK_MEDIA/backup/stage"
  for f in prefs.tsv usage.tsv; do
    c=widget; [ "$f" = usage.tsv ] && c=usage
    if bk_has "$c" "$cats" && [ -f "$w/$f" ]; then cp "$w/$f" "$BK_MEDIA/backup/stage/$f"; fi
  done
  mkdir -p "$BK_MEDIA/library"
  if bk_has lib "$cats" && [ -d "$w/library" ]; then cp -R "$w/library/." "$BK_MEDIA/library/"; fi
  if bk_has usage "$cats" && [ -d "$w/stats" ]; then cp -R "$w/stats/." "$BK_MEDIA/library/"; fi
  if bk_has ssh "$cats"; then
    if [ -f "$w/ssh.conf" ]; then   # SSH_ keys are carried over by set_flush untouched, so they are replaced in the file itself
      set_lock
      { grep -v '^SSH_' "$SET/custom.conf" 2>/dev/null; grep '^SSH_' "$w/ssh.conf"; } > "$SET/custom.conf.tmp" && mv "$SET/custom.conf.tmp" "$SET/custom.conf"
      set_unlock
    fi
    if [ -d "$w/ssh" ]; then mkdir -p "$BK_SSHDIR"; cp -R "$w/ssh/." "$BK_SSHDIR/"; fi
  fi
  chown -R 1023:1023 "$BK_MEDIA/backup" "$BK_MEDIA/library" 2>/dev/null; chmod -R a+rwX "$BK_MEDIA/backup" 2>/dev/null
  rm -rf "$w"
  bk_status "$id" done "$cats"
}
