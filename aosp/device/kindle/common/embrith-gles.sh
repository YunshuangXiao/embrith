#!/system/bin/sh
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Embrith contributors
# Started by init.kindle.rc when surfaceflinger is running. Waits until surfaceflinger has loaded libGLES_android, then sets
# embrith.sfready to 1: the rule in init.kindle.rc puts qemu.gles back to 2 from then on (newer WebView, embrith.gles 2).
i=0
while [ "$i" -lt 30 ]; do
  for d in /proc/[0-9]*; do
    [ "$(cat "$d/comm" 2>/dev/null)" = surfaceflinger ] || continue
    case "$(cat "$d/maps" 2>/dev/null)" in *libGLES_android*) i=99 ;; esac
  done
  [ "$i" -ge 99 ] && break
  sleep 1
  i=$((i + 1))
done
setprop embrith.sfready 1
