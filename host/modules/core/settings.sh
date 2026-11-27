# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Embrith contributors
# host/modules/core/settings.sh - the one settings table of embrith, after KOReader's G_reader_settings:
#  - one table, read once (set_load), changed in memory only (set_put marks it dirty);
#  - written as a whole file to $SET/custom.conf only at events (set_flush: stop, before sleep, start);
#    the old file is kept as .bak, the new one is renamed into place (atomic);
#  - every read/write of the table is under one lock (mkdir lock, shell has no flock), because embrith runs several
#    processes (start, the watchdog, its power-event subshell, stop) that touch the same table.
# Android, kdispd and the watchdog change values in ctl/conf; set_sync moves those changes into the table.
# Unknown keys are kept.
# Keys starting with SSH_ belong to lib/ssh-toggle.sh (it also runs from the library booklet while embrith is stopped): they are never loaded
# into the table (so they never reach ctl/conf, SSH_HASH included), and set_flush carries them over from the file untouched.
#
# Referenced project: KOReader, AGPL-3.0, https://github.com/koreader/koreader (COPYING in that project). Files consulted:
#   reader.lua, frontend/luasettings.lua     G_reader_settings: one table, read once, written as a whole
#   frontend/ui/uimanager.lua                SaveState (write at events)
SET_FILE=$SET/custom.conf
SET_DEF=$SET/embrith.default   # the default file name, taken over while custom.conf has no settings yet
SET_MEM=$ST/settings.mem          # the in-memory table of this embrith session (in /var/tmp, gone at reboot)
SET_DIRTY=$ST/settings.dirty
SET_LOCK=$ST/settings.lock

set_lock() {                      # mkdir is atomic; a lock older than 10 s belongs to a dead process
  n=0
  while ! mkdir "$SET_LOCK" 2>/dev/null; do
    [ $n -ge 100 ] && { rm -rf "$SET_LOCK"; continue; }
    sleep 0.1 2>/dev/null || sleep 1; n=$((n + 1))
  done
}
set_unlock() { rmdir "$SET_LOCK" 2>/dev/null; }

set_load() {                      # start: file -> memory (an empty or missing file is fine)
  set_lock
  mkdir -p "$(dirname "$SET_FILE")" 2>/dev/null
  if [ -f "$SET_DEF" ] && ! grep -qv '^SSH_' "$SET_FILE" 2>/dev/null; then   # no settings in custom.conf yet (it may hold SSH_ keys only): take the old file
    { cat "$SET_DEF"; cat "$SET_FILE" 2>/dev/null; } > "$SET_FILE.tmp" && mv "$SET_FILE.tmp" "$SET_FILE"
  fi
  if [ -f "$SET_FILE" ]; then grep -v '^SSH_' "$SET_FILE" > "$SET_MEM"; else : > "$SET_MEM"; fi
  rm -f "$SET_DIRTY"
  set_unlock
}
set_get() {                       # KEY DEFAULT
  set_lock; v=$(sed -n "s/^$1=//p" "$SET_MEM" 2>/dev/null | head -n1); set_unlock
  printf '%s' "${v:-$2}"
}
set_put() {                       # KEY VALUE (memory only, marks the table dirty)
  set_lock
  cur=$(sed -n "s/^$1=//p" "$SET_MEM" 2>/dev/null | head -n1)
  if [ "$cur" != "$2" ]; then
    grep -v "^$1=" "$SET_MEM" > "$SET_MEM.tmp" 2>/dev/null; printf '%s=%s\n' "$1" "$2" >> "$SET_MEM.tmp"
    mv "$SET_MEM.tmp" "$SET_MEM"; : > "$SET_DIRTY"
  fi
  set_unlock
}
set_flush() {                     # memory -> $SET/custom.conf, only when dirty
  set_lock
  if [ -f "$SET_DIRTY" ]; then   # the group ends with true: grep exits 1 when there is no SSH_ line, which skipped the mv and left only custom.conf.tmp
    [ -f "$SET_FILE" ] && cp "$SET_FILE" "$SET_FILE.bak" 2>/dev/null
    { cat "$SET_MEM"; grep '^SSH_' "$SET_FILE" 2>/dev/null; true; } > "$SET_FILE.tmp" && mv "$SET_FILE.tmp" "$SET_FILE" && rm -f "$SET_DIRTY"
  fi
  set_unlock
}
set_sync() {                      # ctl/conf (written by Android, kdispd, the watchdog) <-> memory
  [ -f "$K_CTL/conf" ] || return 0
  while IFS='=' read -r k v; do    # changes in ctl/conf -> memory
    case "$k" in ''|\#*) continue ;; esac
    set_put "$k" "$v"
  done < "$K_CTL/conf"
  set_lock
  while IFS='=' read -r k v; do    # memory keys missing from ctl/conf -> ctl/conf
    case "$k" in ''|\#*) continue ;; esac
    grep -q "^$k=" "$K_CTL/conf" 2>/dev/null || printf '%s=%s\n' "$k" "$v" >> "$K_CTL/conf"
  done < "$SET_MEM"
  set_unlock
}
app_count_refresh() {             # APP_COUNT: catches installs and uninstalls made outside the launcher (only "zero or not" is corrected)
  n=$(ls -d "$BASE"/data/app/*-[0-9]* 2>/dev/null | wc -l); n=$((n + 0))
  cur=$(set_get APP_COUNT -1); cur=$((cur + 0))
  if [ "$n" -eq 0 ]; then
    if [ "$cur" -ne 0 ]; then set_put APP_COUNT 0; fi
  elif [ "$cur" -le 0 ]; then
    set_put APP_COUNT "$n"
  fi
}
set_restore_ctl() {               # start, after kdispd wrote ctl/conf: the table wins for every key it has
  set_lock
  while IFS='=' read -r k v; do
    case "$k" in ''|\#*) continue ;; esac
    grep -v "^$k=" "$K_CTL/conf" > "$K_CTL/conf.tmp" 2>/dev/null; printf '%s=%s\n' "$k" "$v" >> "$K_CTL/conf.tmp"
    mv "$K_CTL/conf.tmp" "$K_CTL/conf"
  done < "$SET_MEM"
  set_unlock
}
