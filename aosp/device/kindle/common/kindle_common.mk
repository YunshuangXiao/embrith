# Common part of all Kindle products (device/kindle/<device>/kindle_<device>.mk inherits it): framework + runtime + our own home screen (KindleHome). No stock apps.
#
# Default (slim): base is core_minimal.mk. None of Calendar, Contacts, Email, DeskClock, Calculator, Phone/Telecom/MMS,
# Settings (and its CryptKeeper process), Launcher2, QuickSearchBox, Browser, PrintSpooler, ... is built.
# Fallback if the slim image does not boot: create an empty file  device/kindle/common/use-core-mk  and build again
# (the old base core.mk with all the stock apps; KindleHome is still added).
ifneq ($(wildcard device/kindle/common/use-core-mk),)
PRODUCT_PACKAGES += KindleHome
$(call inherit-product, $(SRC_TARGET_DIR)/product/core.mk)
else
$(call inherit-product, $(SRC_TARGET_DIR)/product/core_minimal.mk)

# What the framework and an app like WeRead really need (several are in core_minimal already: listing them twice is harmless).
# Apps/providers: settings database, adb shell, "copy the APK" service for pm install, storage provider, key store UI backend,
# keyboard layouts, media/download providers (DownloadManager, MediaStore), package installer, certificate and proxy helpers.
PRODUCT_PACKAGES += \
    SettingsProvider Shell DefaultContainerService ExternalStorageProvider KeyChain InputDevices \
    MediaProvider DownloadProvider PackageInstaller CertInstaller ProxyHandler PacProcessor

# PackageManagerService REFUSES to start without these: an intent filter verifier (StatementService: "There must be at least one
# intent filter verifier") and the two system shared libraries android.ext.services / android.ext.shared.
PRODUCT_PACKAGES += StatementService ExtServices ExtShared FusedLocation BackupRestoreConfirmation SharedStorageBackup

# Command line tools: pm (install apk, list packages), settings, ime, input, svc, wm. The slim base has am and dumpsys but not pm.
PRODUCT_PACKAGES += pm settings ime input svc wm

# Native daemons init starts. Without audioserver/mediaserver system_server waits for "media.audio_flinger" forever.
PRODUCT_PACKAGES += \
    logd debuggerd servicemanager surfaceflinger bootanimation vold vdc netd ndc installd keystore gatekeeperd \
    audioserver mediaserver healthd lmkd sdcard toolbox toybox iptables

# Our own home screen replaces Launcher2 (and Settings): app grid, display settings, clear/invert/exit.

# RenderScript JNI and CPU driver. Apps (WeRead) call RenderScript.create(); without librs_jni the app dies with UnsatisfiedLinkError.
PRODUCT_PACKAGES += librs_jni libRSDriver
PRODUCT_PACKAGES += KindleHome

# WebView (WeRead shows some pages in one)
$(call inherit-product-if-exists, frameworks/webview/chromium/chromium.mk)
# Newer WebView next to the stock one (webview/Android.mk). Both stay in the image; WEBVIEW in ctl/conf decides which one is enabled.
PRODUCT_PACKAGES += WebViewGoogle
# SwiftShader (ES 2/3 in software) for the newer WebView: swiftshader/Android.mk, used by processes that start while qemu.gles is 2 (set by webview_apply).
PRODUCT_PACKAGES += libEGL_swiftshader libGLESv1_CM_swiftshader libGLESv2_swiftshader

# Input method: EmbrithIME (inside KindleHome) is the only one; no stock keyboard is built into the image
endif

# The framework needs these while zygote preloads (TextView.preloadFontCache -> Skia font manager, Hyphenator.init, input layouts).
# Without them zygote crashes in SkFontMgr_New_Android and logs "/system/usr/hyphen-data/hyph-*.hyb: ENOENT".
# inherit-product-if-exists: a makefile that does not exist in this source tree is simply skipped.
$(call inherit-product-if-exists, frameworks/base/data/fonts/fonts.mk)
$(call inherit-product-if-exists, external/roboto-fonts/fonts.mk)
$(call inherit-product-if-exists, external/noto-fonts/fonts.mk)
$(call inherit-product-if-exists, external/google-fonts/dancing-script/fonts.mk)
$(call inherit-product-if-exists, external/google-fonts/carrois-gothic-sc/fonts.mk)
$(call inherit-product-if-exists, external/google-fonts/coming-soon/fonts.mk)
$(call inherit-product-if-exists, external/google-fonts/cutive-mono/fonts.mk)
$(call inherit-product-if-exists, external/hyphenation-patterns/patterns.mk)
$(call inherit-product-if-exists, frameworks/base/data/keyboards/keyboards.mk)

PRODUCT_LOCALES := zh_CN en_US
PRODUCT_AAPT_CONFIG := normal

# Software GL (ES 1.x) for SurfaceFlinger, software gralloc. See the graphics notes.
PRODUCT_PACKAGES += libGLES_android gralloc.default

# /dev/ashmem emulation on memfd for apps (LD_PRELOAD in init.kindle.rc).
PRODUCT_PACKAGES += libkindleashmem

# ro.kernel.qemu=1 makes HWUI report "unavailable" (apps then render in software, which is
# what we need: no ES 2.0). EGL falls back to libGLES_android (ES 1.1) for SurfaceFlinger.
PRODUCT_PROPERTY_OVERRIDES += \
    ro.kernel.qemu=1 \
    debug.sf.nobootanimation=1

ifneq ($(wildcard frameworks/native/data/etc/handheld_core_hardware.xml),)
PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/handheld_core_hardware.xml:system/etc/permissions/handheld_core_hardware.xml
endif
ifneq ($(wildcard frameworks/native/data/etc/android.hardware.ethernet.xml),)
PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.ethernet.xml:system/etc/permissions/android.hardware.ethernet.xml
endif
ifneq ($(wildcard frameworks/native/data/etc/android.hardware.touchscreen.multitouch.xml),)
PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.touchscreen.multitouch.xml:system/etc/permissions/android.hardware.touchscreen.multitouch.xml
endif

# Container glue: device rc + a marker the launcher checks (proves init was built without SELinux loading)
PRODUCT_COPY_FILES += \
    device/kindle/common/init.kindle.rc:root/init.kindle.rc \
    device/kindle/common/embrith-gles.sh:system/bin/embrith-gles.sh
# The marker is created by 05-apply.sh only after init.cpp was patched successfully.
ifneq ($(wildcard device/kindle/common/kindle-init-patched),)
PRODUCT_COPY_FILES += \
    device/kindle/common/kindle-init-patched:root/kindle-init-patched
endif
