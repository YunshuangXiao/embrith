LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := libkindleashmem
LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := kindleashmem.c
LOCAL_CFLAGS := -Wall -Werror -Wno-unused-parameter -U_FORTIFY_SOURCE
LOCAL_SANITIZE := never
LOCAL_CXX_STL := none
include $(BUILD_SHARED_LIBRARY)