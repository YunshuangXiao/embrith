# Kindle PW6: the device specific part. Everything else is in device/kindle/common/kindle_common.mk.
$(call inherit-product, device/kindle/common/kindle_common.mk)

PRODUCT_NAME := kindle_pw6
PRODUCT_DEVICE := pw6
PRODUCT_BRAND := Kindle
PRODUCT_MODEL := Kindle PW6 Android
PRODUCT_MANUFACTURER := local

PRODUCT_AAPT_PREF_CONFIG := xhdpi

# HWC: the module is named hwcomposer.<TARGET_BOARD_PLATFORM> (device/kindle/common/hwcomposer/Android.mk).
PRODUCT_PACKAGES += hwcomposer.pw6

PRODUCT_PROPERTY_OVERRIDES += \
    ro.sf.lcd_density=300 \
    ro.config.low_ram=true \
    dalvik.vm.heapstartsize=8m \
    dalvik.vm.heapgrowthlimit=96m \
    dalvik.vm.heapsize=256m \
    dalvik.vm.heaptargetutilization=0.75 \
    dalvik.vm.heapminfree=512k \
    dalvik.vm.heapmaxfree=8m \
    persist.sys.timezone=Asia/Shanghai
