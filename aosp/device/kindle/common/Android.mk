LOCAL_PATH := $(call my-dir)
ifeq ($(BOARD_KINDLE_COMMON),true)
include $(call all-makefiles-under,$(LOCAL_PATH))
endif
