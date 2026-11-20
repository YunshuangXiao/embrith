#!/bin/bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Embrith contributors
# 把设备配置和补丁放进 ~/aosp. 在 WSL 宿主里运行, 可重复运行. 
set -e
SRC=${AOSP_DIR:-$HOME/aosp}
HERE=$(cd "$(dirname "$0")" && pwd)
[ -d "$SRC/build" ] || { echo "找不到 $SRC"; exit 1; }

# ---- Windows leaves "<file>:Zone.Identifier" files next to everything that was copied in with Explorer / downloaded in a browser.
# A colon in a file name breaks make ("target ... doesn't match the target pattern" / "multiple target patterns"): the build of
# KindleHome fails and the OLD system.img stays. Remove them from the sources and from the AOSP tree before anything is copied.
find "$HERE" -name '*:Zone.Identifier' -delete 2>/dev/null || true
find "$SRC/device/kindle" -name '*:Zone.Identifier' -delete 2>/dev/null || true

# ---- device tree: 整个替换, 编译树里不留其他测试版本的多余文件 (需要的标记文件如 use-core-mk 放进仓库的 device/kindle/common/) ----
# common/ is shared by all devices, every other folder (pw6, ...) is one device. kc/ is not a device tree (notes and a test).
mkdir -p "$SRC/device/kindle"
for D in $(cd "$HERE/device/kindle" && ls -d */ | tr -d / | grep -v '^kc$'); do
  rm -rf "$SRC/device/kindle/$D.new"
  cp -r "$HERE/device/kindle/$D" "$SRC/device/kindle/$D.new"
  rm -rf "$SRC/device/kindle/$D"
  mv "$SRC/device/kindle/$D.new" "$SRC/device/kindle/$D"
  find "$SRC/device/kindle/$D" -name '*:*' -print -delete 2>/dev/null | sed 's/^/removed (colon in file name): /' || true
done
# the marker may only exist after init.cpp has been patched (re-created below)
rm -f "$SRC/device/kindle/common/kindle-init-patched"

# ---- WebView: let the framework accept the newer WebView (com.embrith.webview) next to the stock one ----
# The stock config lists only com.android.webview, so any other provider is never valid. availableByDefault=false: it is used only
# when it is the chosen one (settings global webview_provider, set by host/modules/install/webview.sh).
# patches/webview-sig.txt (optional): base64 of the signing certificate of that apk, written as <signature>. Without it the
# framework accepts the package only on a debuggable build.
W="$SRC/frameworks/base/core/res/res/xml/config_webview_packages.xml"
rm -f "$W.orig"
python3 "$HERE/patches/webview-config.py" "$W" "$HERE/patches/webview-sig.txt"

# ---- libcutils ashmem -> memfd ----
F="$SRC/system/core/libcutils/ashmem-dev.c"
cp "$HERE/patches/ashmem-dev.c" "$F"

# ---- gralloc.default: never take the /dev/graphics/fb0 path ----
G="$SRC/hardware/libhardware/modules/gralloc/gralloc.cpp"
python3 "$HERE/patches/gralloc-nofb.py" "$G"

# ---- ethernet reports itself as Wi-Fi: apps that accept only TYPE_WIFI / TYPE_MOBILE treat eth0 as offline ----
E="$SRC/frameworks/opt/net/ethernet/java/com/android/server/ethernet/EthernetNetworkFactory.java"
[ -f "$E" ] || { echo "EthernetNetworkFactory.java not found: $E"; exit 1; }
python3 "$HERE/patches/ethernet-as-wifi.py" "$E"

# ---- init.rc: our rc first, no ueventd/console, healthd not critical, never reload an SELinux policy ----
python3 "$HERE/patches/init-rc.py" "$SRC/system/core/rootdir/init.rc"

# ---- init.cpp: never load/reload an SELinux policy into the Kindle kernel ----
python3 "$HERE/patches/init-no-selinux.py" "$SRC/system/core/init/init.cpp"

# ---- property_service.cpp: init treats "fsetxattr failed" (-2) as fatal; this kernel may not allow security xattrs on tmpfs ----
python3 "$HERE/patches/init-prop-tolerant.py" "$SRC/system/core/init/property_service.cpp"

# ---- init.cpp: "Unable to set adequate mmap entropy value" must not be fatal (/proc/sys is read-only in the container) ----
python3 "$HERE/patches/init-mmap-rnd.py" "$SRC/system/core/init/init.cpp"

# ---- service.cpp / util.cpp: services must start without SELinux contexts ----
# Service::Start() asks the kernel for a context (getcon/getfilecon/security_compute_create) for every service that has
# no "seclabel"; without a loaded policy that fails and the service is never started. Services WITH a seclabel would
# die in setexeccon(), and sockets in setsockcreatecon(). The container has no SELinux: skip all of it.
python3 "$HERE/patches/init-service-no-selinux.py" "$SRC/system/core/init/service.cpp" "$SRC/system/core/init/util.cpp"

# ---- libselinux (Android flavour): restorecon / setfilecon must do nothing when SELinux is not active ----
# installd's create_app_data -> selinux_android_restorecon_pkgdir fails in the container ("Failed before getfilecon for
# /data/data/<pkg>: No data available"): no policy is loaded, so no app data dir can be created, SettingsProvider cannot open its
# database and system_server dies in InputMethodManagerService. With SELinux inactive these calls should simply succeed.
python3 "$HERE/patches/libselinux-no-selinux.py" "$SRC/external/libselinux/src/android.c" "$SRC/external/selinux/libselinux/src/android.c" "$SRC/external"

# ---- installd: restorecon_app_data_lazy must be a no-op when SELinux is not active ----
# It calls lgetfilecon() first, which fails with ENODATA on every directory (nothing carries a security.selinux label in this
# container): "Failed before getfilecon for /data/data/<pkg>: No data available" -> create_app_data returns -1 for every app ->
# no app data directories -> SettingsProvider cannot open its database -> system_server dies.
python3 "$HERE/patches/installd-no-selinux.py" "$SRC/frameworks/native/cmds/installd/commands.cpp"
echo kindle-init-patched > "$SRC/device/kindle/common/kindle-init-patched"


# ---- AlarmManagerService: the handler fallback crashes system_server on the first listener alarm ----
# When AlarmManagerService cannot open a kernel alarm device (/dev/alarm or timerfd with the *_ALARM clocks, neither works in this
# container) it falls back to "setLocked -> Handler message ALARM_EVENT". That old handler code calls alarm.operation.send() for every
# triggered alarm; alarms that were set with a listener (OnAlarmListener) have operation == null -> NullPointerException in the
# system server's main thread ("PendingIntent.send() on a null object reference", AlarmManagerService.java:2625), every ~6 s after boot.
# AlarmThread (the normal path) delivers through deliverAlarmsLocked(), which handles both kinds. Do the same here.
python3 "$HERE/patches/alarm-fallback.py" "$SRC/frameworks/base/services/core/java/com/android/server/AlarmManagerService.java"

# ---- libagl: no EGL_ANDROID_swap_rectangle ----
# With the extension SurfaceFlinger composes only the dirty rectangle into a double-buffered FB target and libagl copies the rest
# back from the previous buffer. Black areas (covers, search bar) stay in the frame until a full redraw (page turn). Without the
# extension DisplayDevice has no SWAP_RECTANGLE flag and every composition redraws the whole screen (hwcomposer publishes the whole
# target anyway). Needs libGLES_android rebuilt.
python3 "$HERE/patches/libagl-no-swaprect.py" "$SRC/frameworks/native/opengl/libagl/egl.cpp"

# ---- libgui: Surface::lock redraws the whole buffer (default on; switch in settings, advanced) ----
python3 "$HERE/patches/libgui-fulldirty.py" "$SRC/frameworks/native/libs/gui/Surface.cpp"

# ---- Activity.onBackPressed: the last back of a task goes to the home screen instead of finishing the app (needs framework.jar rebuilt) ----
python3 "$HERE/patches/back-to-home.py" "$SRC/frameworks/base/core/java/android/app/Activity.java"

for f in kindle-make.sh 08-package.sh 15-check-system.sh; do [ -f "$HERE/$f" ] && cp "$HERE/$f" "$SRC/$f" && chmod +x "$SRC/$f"; done
echo "已应用. 接着:  bash 03-container.sh run './kindle-make.sh'   (提示 aosp7-build 找不到镜像时, 先运行:  bash 03-container.sh build )"
