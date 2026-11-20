LOCAL_PATH := $(call my-dir)

# SwiftShader EGL / GLES libraries (armeabi-v7a), built by aosp/10-swiftshader.sh into lib/. Binary files are not kept in the repo.
# They go to /system/lib/egl; the stock EGL loader scans that directory when qemu.gles is 2 (set by host/modules/install/webview.sh).
ifeq ($(wildcard $(LOCAL_PATH)/lib/libEGL_swiftshader.so),)
$(error swiftshader/lib/libEGL_swiftshader.so is missing: run aosp/10-swiftshader.sh)
endif

define kindle_swiftshader_lib
include $(CLEAR_VARS)
LOCAL_MODULE := $(1)
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
LOCAL_MODULE_SUFFIX := .so
LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := lib/$(1).so
LOCAL_MODULE_PATH := $$(TARGET_OUT_SHARED_LIBRARIES)/egl
LOCAL_MULTILIB := 32
include $$(BUILD_PREBUILT)
endef

$(eval $(call kindle_swiftshader_lib,libEGL_swiftshader))
$(eval $(call kindle_swiftshader_lib,libGLESv1_CM_swiftshader))
$(eval $(call kindle_swiftshader_lib,libGLESv2_swiftshader))