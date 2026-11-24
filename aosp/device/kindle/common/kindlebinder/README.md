# Binder v7 and Scribe Runtime Compatibility

Adds runtime-detected compatibility for Kindle devices running a 32-bit Binder v7 kernel (such as Kindle Scribe 2022 on `4.9.77-lab126`), VFAT `/mnt/base-us` storage, and kernels built without `CONFIG_MAGIC_SYSRQ`.

## What This Adds

- **Runtime Binder protocol detection (`kutil binder-ver`, `container.sh`, `enter.sh`, `common.sh`)**:
  - At container startup, `kutil binder-ver "$DEVDIR/binder"` queries `BINDER_VERSION` and exports `KD_BINDER_VER`.
  - When `KD_BINDER_VER == 7`:
    - `enter.sh` enables `libkindlebinder.so` ahead of `libkindleashmem.so` in `LD_PRELOAD`, bind-mounts an xattr-free copy of `surfaceflinger` so Bionic honors `LD_PRELOAD`, and installs `kindle_healthd` at `/sbin/healthd`.
    - `common.sh` (`ct_preload`) adds `LD_PRELOAD` for host-invoked container helpers (`in_ct`, `ct_sh_t`, `in_ct_long`, `in_ct_t`).
  - When `KD_BINDER_VER != 7` (e.g. PW6 / Colorsoft with Binder v8), none of the v7 shims or `LD_PRELOAD` overrides are activated.
- **VFAT `/mnt/base-us` ext4 loopback `/data` (`default.conf`, `container.sh`, `stop.sh`, `enter.sh`)**:
  - When `/mnt/base-us` is not mounted as `ext4`, `container.sh` creates and loop-mounts `data.img` (`DATA_IMG_MB=1024` by default) at `$BASE/data`, and optionally mounts `media.img` at `$BASE/data/media` if present (avoiding FAT32's 4 GiB − 1 byte file-size cap when larger internal storage is needed). `stop.sh` unmounts and detaches both cleanly.
- **Missing `/proc/sys/kernel/sysrq` guard (`kindle_ui.sh`)**:
  - `protect_host()` and `unprotect_host()` check `[ -e /proc/sys/kernel/sysrq ]` so kernels built without `CONFIG_MAGIC_SYSRQ` do not fail container startup.
- **`hide_host()` race fix (`container.sh`)**:
  - Waits for `/proc/$(init_pid)/root/host/var/tmp/embrith-bin/kutil` before calling `nsenter -t "$(init_pid)" -m -r -- /host/var/tmp/embrith-bin/kutil umount-lazy /host`.
- **Multi-touch prioritization & separate stylus pass-through (`devices.sh`, `container.sh`, `enter.sh`)**:
  - `touch_node()` prioritizes capacitive multi-touch panels (`ABS_MT_POSITION_X`/`Y` without `BTN_TOOL_PEN`) over stylus digitizers, while `stylus_node()` passes any separate pen digitizer (e.g. Kindle Scribe Wacom EMR) through as `/dev/input/event11` with matching `.idc` config so finger touch and pen work together.

## Building & Testing

### Host unit tests
```sh
clang -Wall -Wextra -Werror -O2 aosp/device/kindle/common/kindlebinder/test_shim.c -o /tmp/test_shim && /tmp/test_shim
clang -Wall -Wextra -Werror -O2 -DUNIT_TEST aosp/device/kindle/common/kindlebinder/test_healthd.c -o /tmp/test_healthd && /tmp/test_healthd
```

### AOSP build
`aosp/device/kindle/common/kindle_common.mk` includes `libkindlebinder` and `kindle_healthd` in `PRODUCT_PACKAGES`, so `08-package.sh` bundles `/system/lib/libkindlebinder.so` and `/system/bin/kindle_healthd` into `embrith.raw`.

### Standalone host packaging (optional)
`enter.sh` prefers `/var/tmp/embrith-bin/{libkindlebinder.so,kindle_healthd}` if placed in `host/bin/`. Both files also support headerless/freestanding ARMv7 compilation (`-DNO_SYSROOT` for `libkindlebinder.c` and `-DFREESTANDING_ARM` for `kindle_healthd.c`).

## Hardware Validation

- **Verified on**: Kindle Scribe 2022 (`armv7l`, firmware `5.20.2`, kernel `4.9.77-lab126`, Binder protocol v7, VFAT `/mnt/base-us`, no `/proc/sys/kernel/sysrq`). Full container boot (`sys.boot_completed=1`), `servicemanager` / `surfaceflinger` / `batteryproperties` / SystemUI / Launcher startup, touch input, and clean stop verified on device.
- **Not yet run in this workspace**: Physical hardware regression on PW6 / Colorsoft and full clean AOSP image rebuild via `08-package.sh`.
