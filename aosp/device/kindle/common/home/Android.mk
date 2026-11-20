# KindleHome: the small launcher ("Embrith") of the Kindle container. App grid, display settings, clear/invert/exit buttons.
LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_PACKAGE_NAME := KindleHome
LOCAL_MODULE_TAGS := optional
# priv-app: PACKAGE_USAGE_STATS (privileged) is granted without the user opening Usage access
LOCAL_PRIVILEGED_MODULE := true
# EmbrithIME is the only input method: the stock keyboards are dropped from the image, whoever pulls them in
LOCAL_OVERRIDES_PACKAGES := LatinIME PinyinIME OpenWnn
LOCAL_SRC_FILES := $(call all-java-files-under, src)
# assets/pinyin.tsv: the offline pinyin dictionary of EmbrithIME (made by tools/make-ime.py)
LOCAL_ASSET_DIR := $(LOCAL_PATH)/assets
LOCAL_SDK_VERSION := current
LOCAL_PROGUARD_ENABLED := disabled
include $(BUILD_PACKAGE)
