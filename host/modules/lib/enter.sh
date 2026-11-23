#!/bin/sh
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Embrith contributors
# Runs as PID 1 of a fresh pid/mount/ipc/uts namespace (started by embrith.sh via unshare).
# Builds the Android root, pivots into it and execs Android's init.
# Needs: KD_BASE (folder with data/), KD_SET (settings folder), KD_WALL (wallpapers folder), KD_IMG (embrith.raw), KD_ROOT_OFF and KD_ROOT_LEN (the root tar inside it), KD_LOOP (loop device already attached to the system part)
#
# Referenced project: VeraDroid, GPL-3.0-or-later, Copyright (C) 2026 the VeraDroid contributors. Files consulted:
#   host/pw6/boot/lib/memory.sh              Java heap limits for a ~1 GB device
#   host/pw6/boot/lib/slim.sh                services a reader does not use (config.disable_*), ro.zygote.disable_gl_preload
set -e
umask 022
R=/var/tmp/embrith/root
echo "[enter] building root in $R"
mkdir -p "$R"
mount -t tmpfs -o size=32m,mode=755 tmpfs "$R"
tail -c +$((KD_ROOT_OFF + 1)) "$KD_IMG" | head -c "$KD_ROOT_LEN" | tar -xf - -C "$R"
echo "ro.embrith.stage=${KD_STAGE:-init}" >> "$R/default.prop"
echo "persist.sys.usb.config=none" >> "$R/default.prop"   # there is no adbd binary: do not respawn it every 5 s
[ -n "$KD_PANEL" ] && echo "kindle.hwc.panel=$KD_PANEL" >> "$R/default.prop"   # panel size for the display HAL (WxH, read from the framebuffer)
[ -n "$KD_PPI" ] && echo "ro.sf.lcd_density=$KD_PPI" >> "$R/default.prop"   # pixel density from the model table (default.prop is read before build.prop)
# Java heap limits (same values as veradroid for a ~1 GB device). Without them apps get a ~16 MB heap and the launcher dies with OutOfMemoryError (screen is 1272x1696 at 300 dpi).
# They are set with "setprop" in post-fs-data: init loads /system/build.prop (96m/256m from the image) AFTER early-init, so a setprop in
# early-init is overwritten, while post-fs-data runs before zygote (class main) is started.
if ! grep -q "dalvik.vm.heapgrowthlimit" "$R/init.kindle.rc"; then
cat >> "$R/init.kindle.rc" <<'RC'

# appended by the launcher (enter.sh): java heap limits
on post-fs-data
    setprop dalvik.vm.heapstartsize 8m
    setprop dalvik.vm.heapgrowthlimit 256m
    setprop dalvik.vm.heapsize 512m
    setprop dalvik.vm.heaptargetutilization 0.75
    setprop dalvik.vm.heapminfree 512k
    setprop dalvik.vm.heapmaxfree 8m
RC
fi
# Services a reader never uses (SystemServer reads config.disable_*) and no EGL preload in zygote (all drawing is software), as veradroid slim does.
cat >> "$R/default.prop" <<'PROP'
ro.zygote.disable_gl_preload=true
config.disable_rtt=true
config.disable_mediaproj=true
config.disable_serial=true
config.disable_samplingprof=true
config.disable_otadexopt=true
PROP
# vold reads /fstab.<hardware> and crashed once at start when it did not exist; a file with one harmless entry is enough.
[ -e "$R/fstab.mt8512" ] || echo '/dev/null /embrith-unused ext4 ro wait' > "$R/fstab.mt8512"
# init.rc's "init_user0" runs "vdc --wait cryptfs init_user0" and blocks init until vold answers - but vold is only
# started AFTER post-fs-data (our "trigger embrith-vold"), so init waits forever and nothing else ever starts.
sed -i 's/^    init_user0$/    # (kindle) init_user0 (waits for vold, which starts later)/' "$R/init.rc"
mkdir -p "$R/host" "$R/system" "$R/data" "$R/dev" "$R/proc" "$R/sys" "$R/mnt" "$R/storage"

# bionic's arc4random refuses a /dev/urandom that is not a character device and kills the process with SIGKILL.
# Nothing creates the device nodes in this container (no ueventd), and init.rc does "copy /proc/cmdline /dev/urandom":
# init then CREATES /dev/urandom as a regular file. Put real character devices in place before that happens.
if ! grep -q "embrith-dev/urandom" "$R/init.kindle.rc"; then
cat >> "$R/init.kindle.rc" <<'RC'

# appended by the launcher (enter.sh)
on early-init
    write /dev/null 0
    write /dev/random 0
    write /dev/urandom 0
    mount none /host/var/tmp/embrith-dev/null /dev/null bind
    mount none /host/var/tmp/embrith-dev/random /dev/random bind
    mount none /host/var/tmp/embrith-dev/urandom /dev/urandom bind
    chmod 0666 /dev/null
    chmod 0666 /dev/random
    chmod 0666 /dev/urandom
RC
fi

# netd's logwrapper needs a pty: /dev/ptmx is normally created by ueventd.
if ! grep -q "pts/ptmx" "$R/init.kindle.rc"; then
cat >> "$R/init.kindle.rc" <<'RC'

# appended by the launcher (enter.sh): ptys
on early-init
    symlink pts/ptmx /dev/ptmx
    chmod 0666 /dev/pts/ptmx
RC
fi

if ! grep -q "user_de/0" "$R/init.kindle.rc"; then
cat >> "$R/init.kindle.rc" <<'RC'

# appended by the launcher (enter.sh): user 0's storage directories. init.rc used to get them from "init_user0"
# (vold creates them), which we have to skip: it waits for vold, which only starts later. Without /data/user_de/0
# installd cannot create any app data directory and system_server dies in SettingsProvider.
on post-fs-data
    mkdir /data/user_de 0711 system system
    mkdir /data/user_de/0 0771 system system
    mkdir /data/system_de 0770 system system
    mkdir /data/system_de/0 0770 system system
    mkdir /data/system_ce 0770 system system
    mkdir /data/system_ce/0 0770 system system
    mkdir /data/misc_de 01771 system misc
    mkdir /data/misc_de/0 01771 system misc
    mkdir /data/misc_ce 01771 system misc
    mkdir /data/misc_ce/0 01771 system misc
    mkdir /data/media 0770 media_rw media_rw
    mkdir /data/media/0 0770 media_rw media_rw
    mkdir /data/misc/profiles 0771 system system
    mkdir /data/misc/profiles/cur 0771 system system
    mkdir /data/misc/profiles/cur/0 0771 system system
    mkdir /data/misc/profiles/ref 0771 system system
RC
fi

if ! grep -q "embrith-dev/fuse" "$R/init.kindle.rc"; then
cat >> "$R/init.kindle.rc" <<'RC'

# appended by the launcher (enter.sh): /dev/fuse for the sdcard daemon (emulated storage)
on early-init
    write /dev/fuse 0
    mount none /host/var/tmp/embrith-dev/fuse /dev/fuse bind
    chmod 0666 /dev/fuse
RC
fi

# wallpaper / lock screen pictures: <user folder>/wallpapers (KD_WALL) appears as /dev/wallpapers
if [ -d "$KD_WALL" ] && ! grep -q "dev/wallpapers" "$R/init.kindle.rc"; then
cat >> "$R/init.kindle.rc" <<RC

# appended by the launcher (enter.sh): wallpapers
on early-init
    mkdir /dev/wallpapers 0755 root root
    mount none /host$KD_WALL /dev/wallpapers bind
RC
fi

# the Kindle's user storage (documents, apk files you copied over USB) as /dev/kindle, read-only: the launcher's file picker uses it
# /mnt/us is the Kindle user storage (fsp 777); KD_BASE is on /mnt/base-us (ext4, embrith 700 root): do not use dirname
KD_USER=/mnt/us
if [ -d "$KD_USER" ] && ! grep -q "dev/kindle" "$R/init.kindle.rc"; then
cat >> "$R/init.kindle.rc" <<RC

# appended by the launcher (enter.sh): the Kindle's user storage
on early-init
    mkdir /dev/kindle 0755 root root
    mount none /host$KD_USER /dev/kindle bind
    mount none /dev/kindle /dev/kindle remount bind ro
RC
fi

mount -t ext4 -o ro "$KD_LOOP" "$R/system"
if [ "$KD_BINDER_VER" = 7 ]; then
  BINDER_SHIM=
  if [ -f /var/tmp/embrith-bin/libkindlebinder.so ]; then
    cp /var/tmp/embrith-bin/libkindlebinder.so "$R/sbin/libkindlebinder.so"
    chmod 755 "$R/sbin/libkindlebinder.so"
    BINDER_SHIM=/sbin/libkindlebinder.so
  elif [ -f "$R/system/lib/libkindlebinder.so" ]; then
    BINDER_SHIM=/system/lib/libkindlebinder.so
  fi
  if [ -n "$BINDER_SHIM" ]; then
    sed -i "s|export LD_PRELOAD /system/lib/libkindleashmem.so|export LD_PRELOAD $BINDER_SHIM:/system/lib/libkindleashmem.so|" "$R/init.kindle.rc"
    # surfaceflinger carries security.capability file xattrs on system.img, which make Bionic skip LD_PRELOAD
    if [ -f "$R/system/bin/surfaceflinger" ]; then
      cp "$R/system/bin/surfaceflinger" "$R/sf.bin" && chmod 755 "$R/sf.bin" && mount --bind "$R/sf.bin" "$R/system/bin/surfaceflinger"
    fi
  else
    echo "[enter] WARNING: binder v7 detected but libkindlebinder.so not found"
  fi
  if [ -x /var/tmp/embrith-bin/kindle_healthd ]; then
    cp /var/tmp/embrith-bin/kindle_healthd "$R/sbin/healthd"
    chmod 755 "$R/sbin/healthd"
  elif [ -x "$R/system/bin/kindle_healthd" ]; then
    cp "$R/system/bin/kindle_healthd" "$R/sbin/healthd"
    chmod 755 "$R/sbin/healthd"
  else
    awk '{print} /^service healthd \/sbin\/healthd$/ {print "    disabled"}' "$R/init.rc" > "$R/init.rc.tmp" && mv "$R/init.rc.tmp" "$R/init.rc"
  fi
  chmod 644 "$R/init.rc" "$R/init.kindle.rc"
fi

# Touch screen: Android only treats the device as a touch SCREEN (not a touch pad) when an input device configuration
# says so. /system is read-only, so the idc directory is copied, extended and bind-mounted over the original.
if [ -d "$R/system/usr/idc" ] && [ -n "$KD_TOUCH_NAMES" ]; then
  mkdir -p "$R/idc.tmp"
  cp "$R"/system/usr/idc/* "$R/idc.tmp/" 2>/dev/null || true
  for n in $KD_TOUCH_NAMES; do
    printf 'touch.deviceType = touchScreen\ntouch.orientationAware = 1\ndevice.internal = 1\n' > "$R/idc.tmp/$n.idc"
  done
  chmod 755 "$R/idc.tmp"; chmod 644 "$R"/idc.tmp/*
  mount --bind "$R/idc.tmp" "$R/system/usr/idc" && echo "[enter] touch idc: $KD_TOUCH_NAMES"
fi

# System font chosen in Settings > Personalize (FONT_FILE in custom.conf): a ttf/otf in the Kindle fonts folder, the one the launcher
# lists as /dev/kindle/fonts (= /mnt/us/fonts, read through /mnt/base-us, the real ext4 behind it). The file is bind-mounted over
# the sans-serif files named in fonts.xml (Roboto*), so every glyph it has is used first. Zygote preloads fonts: needs a restart of Android.
FONT_F=$(sed -n 's/^FONT_FILE=//p' "$KD_SET/custom.conf" 2>/dev/null | head -n 1 | tr -d '\r')
case "$FONT_F" in ''|*/*) FONT_F= ;; esac
FONT_P=
for d in /mnt/base-us/fonts /mnt/us/fonts; do [ -n "$FONT_F" ] && [ -f "$d/$FONT_F" ] && { FONT_P=$d/$FONT_F; break; }; done
if [ -n "$FONT_P" ] && [ -f "$R/system/etc/fonts.xml" ]; then
  sed -n '/<family name="sans-serif">/,/<\/family>/p' "$R/system/etc/fonts.xml" | sed -n -E 's/.*>([^<>]*\.(ttf|otf))<.*/\1/p' | while read -r fn; do
    if [ -f "$R/system/fonts/$fn" ]; then mount --bind "$FONT_P" "$R/system/fonts/$fn"; fi
  done
  echo "[enter] system font: $FONT_P"
elif [ -n "$FONT_F" ]; then
  echo "[enter] system font $FONT_F not found in /mnt/base-us/fonts or /mnt/us/fonts: default font"
fi
# The virtual keyboard created by kgest (BACK / HOME / volume keys) appears as one more input device.
if [ -e /var/tmp/embrith-dev/input/keys ] && ! grep -q "embrith-dev/input/keys" "$R/init.kindle.rc"; then
cat >> "$R/init.kindle.rc" <<'RC'

# appended by the launcher (enter.sh): virtual keyboard "embrith-keys"
on early-init
    write /dev/input/event10 0
    mount none /host/var/tmp/embrith-dev/input/keys /dev/input/event10 bind
    chown root input /dev/input/event10
    chmod 0660 /dev/input/event10
RC
fi
mkdir -p "$KD_BASE/data"
mount -o bind "$KD_BASE/data" "$R/data"
if [ -f "$KD_BASE/media.img" ] && grep -q " $KD_BASE/data/media ext4" /proc/mounts; then
  mkdir -p "$R/data/media"
  mount -o bind "$KD_BASE/data/media" "$R/data/media"
  echo "[enter] media on /data/media ($KD_BASE/media.img)"
fi

# Kindle folders chosen in Settings (KD_SHARES, checked and recorded by container.sh): read-only bind mounts that keep the folder tree.
# KD_USER is set above. A failed folder is skipped (a half-done read-write bind is removed again).
if [ -n "$KD_SHARES" ]; then
  oi=$IFS; IFS=:; set -f
  for p in $KD_SHARES; do
    t="$R/data/media/0/Kindle/$p"
    if mount -o bind "$KD_USER/$p" "$t" && mount -o remount,ro,bind "$t"; then
      echo "[enter] shared (ro): $p"
    else
      umount -l "$t" 2>/dev/null
      echo "[enter] WARNING: shared folder failed: $p"
    fi
  done
  IFS=$oi; set +f
fi

cd "$R"
pivot_root . host
cd /
echo "[enter] exec /init"
exec /init
