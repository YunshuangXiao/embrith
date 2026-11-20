LOCAL_PATH := $(call my-dir)

# Prebuilt Android System WebView (com.embrith.webview, built from the Chromium sources, armeabi-v7a, minSdk 24).
# Put the apk next to this file as webview.apk before building. Binary files are not kept in the repo.
ifeq ($(wildcard $(LOCAL_PATH)/webview.apk),)
$(error webview/webview.apk is missing)
endif
# Third-party license summary of the Chromium build (see readme). Not kept in the repo; AOSP puts it into the image NOTICE.
ifeq ($(wildcard $(LOCAL_PATH)/webview_licenses.notice),)
$(error webview/webview_licenses.notice is missing)
endif

include $(CLEAR_VARS)
LOCAL_MODULE := WebViewGoogle
LOCAL_MODULE_CLASS := APPS
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_SUFFIX := $(COMMON_ANDROID_PACKAGE_SUFFIX)
LOCAL_SRC_FILES := webview.apk
LOCAL_CERTIFICATE := PRESIGNED
LOCAL_DEX_PREOPT := false
LOCAL_NOTICE_FILE := $(LOCAL_PATH)/webview_licenses.notice
# Only what the WebView needs at run time. If the page engine fails to load, add the partition libraries back first.
LOCAL_PREBUILT_JNI_LIBS := \
    @lib/armeabi-v7a/libcrashpad_handler_trampoline.so \
    @lib/armeabi-v7a/libwebviewchromium.so
include $(BUILD_PREBUILT)