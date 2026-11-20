# Common part of the Kindle container boards: 32-bit ARMv7, runs on the Kindle's own 5.15 kernel.
# Only a system image (+ ramdisk root) is built; there is no kernel/bootloader/recovery.
BOARD_KINDLE_COMMON := true

TARGET_NO_BOOTLOADER := true
TARGET_NO_KERNEL := true
TARGET_NO_RECOVERY := true
TARGET_NO_RADIOIMAGE := true

TARGET_ARCH := arm
TARGET_ARCH_VARIANT := armv7-a-neon
TARGET_CPU_ABI := armeabi-v7a
TARGET_CPU_ABI2 := armeabi
TARGET_CPU_VARIANT := generic
TARGET_CPU_SMP := true

# Kernel 5.15 binder only speaks the 64-bit protocol (binder_size_t = u64).
TARGET_USES_64_BIT_BINDER := true

TARGET_USERIMAGES_USE_EXT4 := true
BOARD_FLASH_BLOCK_SIZE := 4096

BOARD_HAVE_BLUETOOTH := false
