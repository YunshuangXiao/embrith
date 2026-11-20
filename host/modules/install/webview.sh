# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Embrith contributors
# host/modules/install/webview.sh - which WebView the container uses. Sourced by host/embrith.sh.
#   WEBVIEW=1 (ctl/conf, switch "新版 WebView" in the launcher): the newer WebView (com.embrith.webview) is the only enabled
#   provider. 0 or unset: the one built from AOSP (com.android.webview, Chromium 52). The package state is kept in /data and is
#   applied again after every boot. A provider that is not installed is never switched to, so one provider always stays enabled.
#   qemu.gles follows the choice (see webview_apply).
# Only the two WebView packages are touched (never other packages: see the note in container.sh about pm disable-user).

WEBVIEW_OLD=com.android.webview
WEBVIEW_NEW=com.embrith.webview

webview_apply() {
  alive || return 0
  if [ "$(conf_val WEBVIEW)" = 0 ]; then on=$WEBVIEW_OLD; off=$WEBVIEW_NEW; else on=$WEBVIEW_NEW; off=$WEBVIEW_OLD; fi
  if ! ct_sh_t 15 "pm path $on" 2>/dev/null | grep -q '^package:'; then
    say "webview: $on is not installed, nothing changed"; return 0
  fi
  # Stock AOSP EGL loader (ro.kernel.qemu=1): qemu.gles 0 = libGLES_android (ES 1), 2 = scan /system/lib/egl (SwiftShader, ES 2).
  # Only processes that start after this call see the new value (surfaceflinger loaded its EGL at boot): restart the apps.
  if [ "$on" = "$WEBVIEW_NEW" ]; then g=2; else g=0; fi
  # The command line file of the WebView has to match the choice as well (it is also written at container start). Subshell: it sets D and F.
  ( prep_webview )
  # embrith.gles keeps the wanted value for init.kindle.rc, which resets qemu.gles to 0 when surfaceflinger restarts.
  ct_sh_t 15 "setprop embrith.gles $g; setprop qemu.gles $g" >/dev/null 2>&1
  # 7.1 only accepts a provider that is not available by default when it is the chosen one (global webview_provider): set it first
  ct_sh_t 15 "settings put global webview_provider $on" >/dev/null 2>&1
  dis=$(ct_sh_t 15 'pm list packages -d' 2>/dev/null | tr -d '\r')
  case "$dis" in *"package:$on"*) ct_sh_t 15 "pm enable $on" >/dev/null 2>&1 ;; esac
  if ct_sh_t 15 "pm path $off" 2>/dev/null | grep -q '^package:'; then
    case "$dis" in *"package:$off"*) ;; *) ct_sh_t 15 "pm disable $off" >/dev/null 2>&1 ;; esac
  fi
  say "webview: $on"
}

# FULLDIRTY (ctl/conf, switch "整屏重绘" in settings, advanced): libgui reads debug.embrith.fulldirty once per app process.
# On unless the value is 0. Applied again after every boot; running apps keep the old value until they are restarted.
fulldirty_apply() {
  alive || return 0
  if [ "$(conf_val FULLDIRTY)" = 0 ]; then v=0; else v=1; fi
  ct_sh_t 15 "setprop debug.embrith.fulldirty $v" >/dev/null 2>&1
  say "fulldirty: $v"
}
