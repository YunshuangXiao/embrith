LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := libkindlebinder
LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := libkindlebinder.c
LOCAL_SHARED_LIBRARIES := libdl
LOCAL_CFLAGS := -Wall -Werror -Wno-unused-parameter -U_FORTIFY_SOURCE
LOCAL_SANITIZE := never
LOCAL_CXX_STL := none
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := kindle_healthd
LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := kindle_healthd.c
LOCAL_FORCE_STATIC_EXECUTABLE := true
LOCAL_STATIC_LIBRARIES := libc
LOCAL_CFLAGS := -Wall -Werror -Wno-unused-parameter
LOCAL_SANITIZE := never
LOCAL_CXX_STL := none
include $(BUILD_EXECUTABLE)
