LOCAL_PATH := $(call my-dir)
ifeq ($(TARGET_DEVICE),pw6)
include $(call all-makefiles-under,$(LOCAL_PATH))
endif
