LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := hwcomposer.$(TARGET_BOARD_PLATFORM)
LOCAL_MODULE_RELATIVE_PATH := hw
LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := hwcomposer.cpp
LOCAL_SHARED_LIBRARIES := liblog libcutils libhardware
LOCAL_C_INCLUDES += hardware/libhardware/modules/gralloc
LOCAL_CFLAGS := -Wall -Wno-unused-parameter
include $(BUILD_SHARED_LIBRARY)
