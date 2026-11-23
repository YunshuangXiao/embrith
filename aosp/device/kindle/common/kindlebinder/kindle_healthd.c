/* SPDX-License-Identifier: Apache-2.0
 *
 * Embrith - Native Binder v7 batteryproperties daemon (kindle_healthd)
 *
 * Copyright (C) 2026 Embrith contributors
 *
 * Overview
 *   Static /sbin/healthd in the Android container cannot use LD_PRELOAD, so on
 *   host kernels with Binder protocol v7 it fails to talk to /dev/binder and
 *   system_server's BatteryService aborts waiting for "batteryproperties".
 *
 *   When container startup detects BINDER_VERSION == 7, enter.sh installs this
 *   native 32-bit Binder v7 implementation at /sbin/healthd. It speaks the
 *   32-bit Binder v7 ioctl wire format directly on /dev/binder, registers the
 *   "batteryproperties" service with servicemanager (including the 24-byte
 *   flat_binder_object slot width expected by AOSP's 64-bit Binder Parcel),
 *   answers IBatteryPropertiesRegistrar transactions, and broadcasts sysfs
 *   power_supply state from the Kindle Scribe (bd71827_bat, bd71827_ac,
 *   bq256xx-charger) to registered IBatteryPropertiesListener handles.
 *
 * Build modes
 *   - Default (AOSP static executable / POSIX libc): uses standard libc
 *     syscalls and int main(int argc, char **argv).
 *   - FREESTANDING_ARM: zero-libc ARM32 svc #0 build for standalone tooling.
 *   - UNIT_TEST: exposes pure parcel/sysfs helpers without main() or _start.
 */

#ifndef _GNU_SOURCE
#define _GNU_SOURCE
#endif

#include <stddef.h>
#include <stdint.h>

#ifdef FREESTANDING_ARM
#define SYS_READ_ARM 3L
#define SYS_WRITE_ARM 4L
#define SYS_OPEN_ARM 5L
#define SYS_CLOSE_ARM 6L
#define SYS_IOCTL_ARM 54L
#define SYS_NANOSLEEP_ARM 162L
#define SYS_POLL_ARM 168L
#define SYS_MMAP2_ARM 192L
#define SYS_EXIT_GROUP_ARM 248L

#define O_RDONLY 0
#define O_RDWR 2
#define O_CLOEXEC 0x80000
#define PROT_READ 1
#define MAP_PRIVATE 2
#define POLLIN 1

struct pollfd {
    int fd;
    short events;
    short revents;
};

struct timespec {
    long tv_sec;
    long tv_nsec;
};

static inline long sc1(long nr, long a0) {
    register long r7 __asm__("r7") = nr;
    register long r0 __asm__("r0") = a0;
    __asm__ volatile("svc #0" : "+r"(r0) : "r"(r7) : "memory");
    return r0;
}

static inline long sc2(long nr, long a0, long a1) {
    register long r7 __asm__("r7") = nr;
    register long r0 __asm__("r0") = a0;
    register long r1 __asm__("r1") = a1;
    __asm__ volatile("svc #0" : "+r"(r0) : "r"(r7), "r"(r1) : "memory");
    return r0;
}

static inline long sc3(long nr, long a0, long a1, long a2) {
    register long r7 __asm__("r7") = nr;
    register long r0 __asm__("r0") = a0;
    register long r1 __asm__("r1") = a1;
    register long r2 __asm__("r2") = a2;
    __asm__ volatile("svc #0" : "+r"(r0) : "r"(r7), "r"(r1), "r"(r2) : "memory");
    return r0;
}

static inline long sc6(long nr, long a0, long a1, long a2, long a3, long a4, long a5) {
    register long r7 __asm__("r7") = nr;
    register long r0 __asm__("r0") = a0;
    register long r1 __asm__("r1") = a1;
    register long r2 __asm__("r2") = a2;
    register long r3 __asm__("r3") = a3;
    register long r4 __asm__("r4") = a4;
    register long r5 __asm__("r5") = a5;
    __asm__ volatile("svc #0"
                     : "+r"(r0)
                     : "r"(r7), "r"(r1), "r"(r2), "r"(r3), "r"(r4), "r"(r5)
                     : "memory");
    return r0;
}

void *memcpy(void *dst, const void *src, size_t n) {
    uint8_t *d = (uint8_t *)dst;
    const uint8_t *s = (const uint8_t *)src;
    for (size_t i = 0; i < n; i++) {
        d[i] = s[i];
    }
    return dst;
}

void *memset(void *dst, int c, size_t n) {
    uint8_t *d = (uint8_t *)dst;
    uint8_t v = (uint8_t)c;
    for (size_t i = 0; i < n; i++) {
        d[i] = v;
    }
    return dst;
}

void __aeabi_memcpy(void *dst, const void *src, size_t n) { memcpy(dst, src, n); }
void __aeabi_memcpy4(void *dst, const void *src, size_t n) { memcpy(dst, src, n); }
void __aeabi_memcpy8(void *dst, const void *src, size_t n) { memcpy(dst, src, n); }
void __aeabi_memset(void *dst, size_t n, int c) { memset(dst, c, n); }
void __aeabi_memset4(void *dst, size_t n, int c) { memset(dst, c, n); }
void __aeabi_memset8(void *dst, size_t n, int c) { memset(dst, c, n); }
void __aeabi_memclr(void *dst, size_t n) { memset(dst, 0, n); }
void __aeabi_memclr4(void *dst, size_t n) { memset(dst, 0, n); }
void __aeabi_memclr8(void *dst, size_t n) { memset(dst, 0, n); }

static inline long sys_read(int fd, void *buf, size_t n) {
    return sc3(SYS_READ_ARM, fd, (long)buf, (long)n);
}
static inline long sys_write(int fd, const void *buf, size_t n) {
    return sc3(SYS_WRITE_ARM, fd, (long)buf, (long)n);
}
static inline int sys_open(const char *path, int flags) {
    return (int)sc3(SYS_OPEN_ARM, (long)path, flags, 0);
}
static inline int sys_close(int fd) {
    return (int)sc1(SYS_CLOSE_ARM, fd);
}
static inline int sys_ioctl(int fd, unsigned long req, void *arg) {
    return (int)sc3(SYS_IOCTL_ARM, fd, (long)req, (long)arg);
}
static inline int sys_nanosleep(const struct timespec *req) {
    return (int)sc2(SYS_NANOSLEEP_ARM, (long)req, 0);
}
static inline int sys_poll(struct pollfd *fds, unsigned long nfds, int timeout_ms) {
    return (int)sc3(SYS_POLL_ARM, (long)fds, (long)nfds, timeout_ms);
}
static inline void *sys_mmap_ro(size_t len, int fd) {
    return (void *)sc6(SYS_MMAP2_ARM, 0, (long)len, PROT_READ, MAP_PRIVATE, fd, 0);
}
#else
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <time.h>
#include <unistd.h>

static inline long sys_read(int fd, void *buf, size_t n) {
    return (long)read(fd, buf, n);
}
static inline long sys_write(int fd, const void *buf, size_t n) {
    return (long)write(fd, buf, n);
}
static inline int sys_open(const char *path, int flags) {
    return open(path, flags, 0);
}
static inline int sys_close(int fd) {
    return close(fd);
}
static inline int sys_ioctl(int fd, unsigned long req, void *arg) {
#ifdef __ANDROID__
    return ioctl(fd, (int)req, arg);
#else
    return ioctl(fd, req, arg);
#endif
}
static inline int sys_nanosleep(const struct timespec *req) {
    return nanosleep(req, NULL);
}
static inline int sys_poll(struct pollfd *fds, unsigned long nfds, int timeout_ms) {
    return poll(fds, (nfds_t)nfds, timeout_ms);
}
static inline void *sys_mmap_ro(size_t len, int fd) {
    return mmap(NULL, len, PROT_READ, MAP_PRIVATE, fd, 0);
}
#endif

#define BINDER_VERSION_REQ 0xc0046209U
#define BINDER_SET_MAX_THREADS 0x40046205U
#define BINDER_WRITE_READ_32 0xc0186201U

#define BINDER_TYPE_BINDER 0x73622a85U
#define BINDER_TYPE_HANDLE 0x73682a85U
#define TF_ONE_WAY 0x01U
#define TF_ACCEPT_FDS 0x10U

#define BC_TRANSACTION_32 0x40286300U
#define BC_REPLY_32 0x40286301U
#define BC_ACQUIRE_RESULT_CMD 0x40046302U
#define BC_FREE_BUFFER_32 0x40046303U
#define BC_INCREFS_CMD 0x40046304U
#define BC_ACQUIRE_CMD 0x40046305U
#define BC_RELEASE_CMD 0x40046306U
#define BC_DECREFS_CMD 0x40046307U
#define BC_INCREFS_DONE_32 0x40086308U
#define BC_ACQUIRE_DONE_32 0x40086309U
#define BC_ENTER_LOOPER_CMD 0x0000630cU
#define BC_REQUEST_DEATH_NOTIFICATION_32 0x4008630eU
#define BC_CLEAR_DEATH_NOTIFICATION_32 0x4008630fU
#define BC_DEAD_BINDER_DONE_32 0x40046310U

#define BR_ERROR_CMD 0x80047200U
#define BR_OK_CMD 0x00007201U
#define BR_TRANSACTION_32 0x80287202U
#define BR_REPLY_32 0x80287203U
#define BR_ACQUIRE_RESULT_CMD 0x80047204U
#define BR_DEAD_REPLY_CMD 0x00007205U
#define BR_TRANSACTION_COMPLETE_CMD 0x00007206U
#define BR_INCREFS_32 0x80087207U
#define BR_ACQUIRE_32 0x80087208U
#define BR_RELEASE_32 0x80087209U
#define BR_DECREFS_32 0x8008720aU
#define BR_ATTEMPT_ACQUIRE_32 0x800c720bU
#define BR_NOOP_CMD 0x0000720cU
#define BR_SPAWN_LOOPER_CMD 0x0000720dU
#define BR_FINISHED_CMD 0x0000720eU
#define BR_DEAD_BINDER_32 0x8004720fU
#define BR_CLEAR_DEATH_NOTIFICATION_DONE_32 0x80047210U
#define BR_FAILED_REPLY_CMD 0x00007211U

#define INTERFACE_TRANSACTION 0x5f4e5446U
#define DUMP_TRANSACTION 0x5f444d50U
#define PING_TRANSACTION 0x5f504e47U

#define LOCAL_BINDER_PTR 0x1001U
#define LOCAL_BINDER_COOKIE 0x1001U

#define BAT_STATUS_UNKNOWN 1
#define BAT_STATUS_CHARGING 2
#define BAT_STATUS_DISCHARGING 3
#define BAT_STATUS_NOT_CHARGING 4
#define BAT_STATUS_FULL 5

#define BAT_HEALTH_UNKNOWN 1
#define BAT_HEALTH_GOOD 2
#define BAT_HEALTH_OVERHEAT 3
#define BAT_HEALTH_DEAD 4
#define BAT_HEALTH_OVER_VOLTAGE 5
#define BAT_HEALTH_UNSPECIFIED_FAILURE 6
#define BAT_HEALTH_COLD 7

#define PROP_CURRENT_NOW 2
#define PROP_CURRENT_AVG 3
#define PROP_CAPACITY 4

#define MAX_LISTENERS 16

struct binder_write_read_32 {
    uint32_t write_size;
    uint32_t write_consumed;
    uint32_t write_buffer;
    uint32_t read_size;
    uint32_t read_consumed;
    uint32_t read_buffer;
};

struct binder_transaction_data_32 {
    union {
        uint32_t handle;
        uint32_t ptr;
    } target;
    uint32_t cookie;
    uint32_t code;
    uint32_t flags;
    int32_t sender_pid;
    uint32_t sender_euid;
    uint32_t data_size;
    uint32_t offsets_size;
    union {
        struct {
            uint32_t buffer;
            uint32_t offsets;
        } ptr;
        uint8_t buf[8];
    } data;
};

struct flat_binder_object_32 {
    uint32_t type;
    uint32_t flags;
    union {
        uint32_t binder;
        int32_t handle;
    };
    uint32_t cookie;
};

struct binder_ptr_cookie_32 {
    uint32_t ptr;
    uint32_t cookie;
};

struct binder_handle_cookie_32 {
    uint32_t handle;
    uint32_t cookie;
};

struct binder_pri_ptr_cookie_32 {
    int32_t priority;
    uint32_t ptr;
    uint32_t cookie;
};

struct bat_props {
    int32_t ac;
    int32_t usb;
    int32_t wireless;
    int32_t max_curr;
    int32_t max_volt;
    int32_t status;
    int32_t health;
    int32_t present;
    int32_t level;
    int32_t voltage_mv;
    int32_t temp_tenths;
    int32_t charge_counter;
    int32_t current_ua;
    char tech[32];
};

static uint32_t udiv32(uint32_t n, uint32_t d, uint32_t *rem_out) {
    uint32_t q = 0;
    uint32_t r = 0;
    if (d == 0) {
        if (rem_out) *rem_out = 0;
        return 0;
    }
    for (int i = 31; i >= 0; i--) {
        r = (r << 1) | ((n >> i) & 1U);
        if (r >= d) {
            r -= d;
            q |= (1U << i);
        }
    }
    if (rem_out) *rem_out = r;
    return q;
}

static size_t cstrlen(const char *s) {
    size_t n = 0;
    while (s[n]) n++;
    return n;
}

static int cstreq(const char *a, const char *b) {
    size_t i = 0;
    while (a[i] && b[i]) {
        if (a[i] != b[i]) return 0;
        i++;
    }
    return a[i] == b[i];
}

static void join_path(char *dst, size_t cap, const char *dir, const char *sub) {
    size_t pos = 0;
    if (cap == 0) return;
    while (*dir && pos + 1 < cap) {
        dst[pos++] = *dir++;
    }
    if (pos > 0 && dst[pos - 1] != '/' && pos + 1 < cap) {
        dst[pos++] = '/';
    }
    while (*sub && pos + 1 < cap) {
        dst[pos++] = *sub++;
    }
    dst[pos] = '\0';
}

static int read_sysfs_str(const char *path, char *out, size_t cap) {
    int fd = sys_open(path, O_RDONLY | O_CLOEXEC);
    if (fd < 0) return -1;
    long n = sys_read(fd, out, cap - 1);
    sys_close(fd);
    if (n <= 0) return -1;
    size_t len = (size_t)n;
    while (len > 0 && (out[len - 1] == '\n' || out[len - 1] == '\r' ||
                       out[len - 1] == ' ' || out[len - 1] == '\t')) {
        len--;
    }
    out[len] = '\0';
    return (int)len;
}

static int parse_i32(const char *s, int32_t *out) {
    size_t i = 0;
    int neg = 0;
    uint32_t acc = 0;
    while (s[i] == ' ' || s[i] == '\t') i++;
    if (s[i] == '-') {
        neg = 1;
        i++;
    } else if (s[i] == '+') {
        i++;
    }
    if (s[i] < '0' || s[i] > '9') return -1;
    while (s[i] >= '0' && s[i] <= '9') {
        acc = acc * 10U + (uint32_t)(s[i] - '0');
        i++;
    }
    *out = neg ? -(int32_t)acc : (int32_t)acc;
    return 0;
}

static int read_sysfs_i32(const char *dir, const char *sub, int32_t *out) {
    char path[256];
    char buf[64];
    join_path(path, sizeof(path), dir, sub);
    if (read_sysfs_str(path, buf, sizeof(buf)) < 0) return -1;
    return parse_i32(buf, out);
}

static void init_bat_defaults(struct bat_props *out) {
    memset(out, 0, sizeof(*out));
    out->status = BAT_STATUS_DISCHARGING;
    out->health = BAT_HEALTH_GOOD;
    out->present = 1;
    out->level = 80;
    out->voltage_mv = 4000;
    out->temp_tenths = 250;
    memcpy(out->tech, "Li-ion", 7);
}

static void read_battery_props(const char *root_dir, struct bat_props *out) {
    char path[256];
    char buf[64];
    int32_t v;

    if (!root_dir || !root_dir[0]) root_dir = "/sys/class/power_supply";
    init_bat_defaults(out);

    if (read_sysfs_i32(root_dir, "bd71827_ac/online", &v) == 0) {
        out->ac = v ? 1 : 0;
    }
    if (read_sysfs_i32(root_dir, "bq256xx-charger/online", &v) == 0) {
        out->usb = v ? 1 : 0;
    }
    if (read_sysfs_i32(root_dir, "bd71827_bat/present", &v) == 0) {
        out->present = v ? 1 : 0;
    }
    if (read_sysfs_i32(root_dir, "bd71827_bat/voltage_now", &v) == 0 && v > 0) {
        if (v > 10000) {
            out->voltage_mv = (int32_t)udiv32((uint32_t)v, 1000U, NULL);
        } else {
            out->voltage_mv = v;
        }
    }
    if (read_sysfs_i32(root_dir, "bd71827_bat/current_now", &v) == 0) {
        out->current_ua = v;
    }
    if (read_sysfs_i32(root_dir, "bd71827_bat/temp", &v) == 0) {
        int32_t t = (v >= -40 && v <= 85) ? (v * 10) : v;
        if (t < -100) t = -100;
        if (t > 550) t = 550;
        out->temp_tenths = t;
    }
    if (read_sysfs_i32(root_dir, "bd71827_bat/capacity", &v) == 0) {
        if (v == 0) {
            out->level = (out->voltage_mv < 3400) ? 0 : 80;
        } else if (v > 100) {
            out->level = 100;
        } else if (v > 0) {
            out->level = v;
        }
    }

    join_path(path, sizeof(path), root_dir, "bd71827_bat/status");
    if (read_sysfs_str(path, buf, sizeof(buf)) > 0) {
        if (cstreq(buf, "Charging")) out->status = BAT_STATUS_CHARGING;
        else if (cstreq(buf, "Discharging")) out->status = BAT_STATUS_DISCHARGING;
        else if (cstreq(buf, "Not charging")) out->status = BAT_STATUS_NOT_CHARGING;
        else if (cstreq(buf, "Full")) out->status = BAT_STATUS_FULL;
    }

    join_path(path, sizeof(path), root_dir, "bd71827_bat/health");
    if (read_sysfs_str(path, buf, sizeof(buf)) > 0) {
        if (cstreq(buf, "Good")) out->health = BAT_HEALTH_GOOD;
        else if (cstreq(buf, "Overheat")) out->health = BAT_HEALTH_OVERHEAT;
        else if (cstreq(buf, "Dead")) out->health = BAT_HEALTH_DEAD;
        else if (cstreq(buf, "Over voltage")) out->health = BAT_HEALTH_OVER_VOLTAGE;
        else if (cstreq(buf, "Unspecified failure")) out->health = BAT_HEALTH_UNSPECIFIED_FAILURE;
        else if (cstreq(buf, "Cold")) out->health = BAT_HEALTH_COLD;
    }

    join_path(path, sizeof(path), root_dir, "bd71827_bat/technology");
    if (read_sysfs_str(path, buf, sizeof(buf)) > 0) {
        size_t len = cstrlen(buf);
        if (len >= sizeof(out->tech)) len = sizeof(out->tech) - 1;
        memcpy(out->tech, buf, len);
        out->tech[len] = '\0';
    }
}

static int bat_props_equal(const struct bat_props *a, const struct bat_props *b) {
    return a->ac == b->ac && a->usb == b->usb && a->wireless == b->wireless &&
           a->status == b->status && a->health == b->health && a->present == b->present &&
           a->level == b->level && a->voltage_mv == b->voltage_mv &&
           a->temp_tenths == b->temp_tenths && a->current_ua == b->current_ua &&
           cstreq(a->tech, b->tech);
}

struct pbuf {
    uint8_t *data;
    size_t cap;
    size_t len;
};

static int pb_write_i32(struct pbuf *pb, int32_t v) {
    if (pb->len + 4 > pb->cap) return -1;
    memcpy(pb->data + pb->len, &v, 4);
    pb->len += 4;
    return 0;
}

static int pb_write_i64(struct pbuf *pb, int64_t v) {
    if (pb->len + 8 > pb->cap) return -1;
    memcpy(pb->data + pb->len, &v, 8);
    pb->len += 8;
    return 0;
}

static int pb_write_str16_ascii(struct pbuf *pb, const char *s) {
    size_t n = cstrlen(s);
    size_t padded = ((n + 1U) * 2U + 3U) & ~3U;
    if (pb_write_i32(pb, (int32_t)n) < 0) return -1;
    if (pb->len + padded > pb->cap) return -1;
    memset(pb->data + pb->len, 0, padded);
    for (size_t i = 0; i < n; i++) {
        pb->data[pb->len + i * 2U] = (uint8_t)s[i];
    }
    pb->len += padded;
    return 0;
}

static int pb_write_iface(struct pbuf *pb, const char *desc) {
    if (pb_write_i32(pb, 0) < 0) return -1;
    return pb_write_str16_ascii(pb, desc);
}

static int pb_skip_iface(const uint8_t *buf, size_t len, size_t *pos_out) {
    if (len < 8) return -1;
    int32_t slen;
    memcpy(&slen, buf + 4, 4);
    size_t pos = 8;
    if (slen >= 0) {
        uint64_t padded = (((uint64_t)(uint32_t)slen + 1ULL) * 2ULL + 3ULL) & ~3ULL;
        if (padded > len - pos) return -1;
        pos += (size_t)padded;
    }
    *pos_out = pos;
    return 0;
}

static int build_add_service_parcel(uint8_t *buf, size_t cap, uint32_t *len_out, uint32_t *off0_out) {
    struct pbuf pb = {buf, cap, 0};
    struct flat_binder_object_32 fbo;
    memset(&fbo, 0, sizeof(fbo));
    fbo.type = BINDER_TYPE_BINDER;
    fbo.flags = 0x17fU;
    fbo.binder = LOCAL_BINDER_PTR;
    fbo.cookie = LOCAL_BINDER_COOKIE;

    if (pb_write_iface(&pb, "android.os.IServiceManager") < 0) return -1;
    if (pb_write_str16_ascii(&pb, "batteryproperties") < 0) return -1;
    *off0_out = (uint32_t)pb.len;
    if (pb.len + sizeof(fbo) + 8U > pb.cap) return -1;
    memcpy(pb.data + pb.len, &fbo, sizeof(fbo));
    pb.len += sizeof(fbo);
    memset(pb.data + pb.len, 0, 8U);
    pb.len += 8U;
    if (pb_write_i32(&pb, 0) < 0) return -1;
    *len_out = (uint32_t)pb.len;
    return 0;
}

static int build_battery_changed_parcel(uint8_t *buf, size_t cap, uint32_t *len_out,
                                        const struct bat_props *p) {
    struct pbuf pb = {buf, cap, 0};
    if (pb_write_iface(&pb, "android.os.IBatteryPropertiesListener") < 0) return -1;
    if (pb_write_i32(&pb, 1) < 0) return -1;
    if (pb_write_i32(&pb, p->ac) < 0) return -1;
    if (pb_write_i32(&pb, p->usb) < 0) return -1;
    if (pb_write_i32(&pb, p->wireless) < 0) return -1;
    if (pb_write_i32(&pb, p->max_curr) < 0) return -1;
    if (pb_write_i32(&pb, p->max_volt) < 0) return -1;
    if (pb_write_i32(&pb, p->status) < 0) return -1;
    if (pb_write_i32(&pb, p->health) < 0) return -1;
    if (pb_write_i32(&pb, p->present) < 0) return -1;
    if (pb_write_i32(&pb, p->level) < 0) return -1;
    if (pb_write_i32(&pb, p->voltage_mv) < 0) return -1;
    if (pb_write_i32(&pb, p->temp_tenths) < 0) return -1;
    if (pb_write_i32(&pb, p->charge_counter) < 0) return -1;
    if (pb_write_str16_ascii(&pb, p->tech[0] ? p->tech : "Li-ion") < 0) return -1;
    *len_out = (uint32_t)pb.len;
    return 0;
}

static int build_get_property_reply(uint8_t *buf, size_t cap, uint32_t *len_out,
                                    int32_t prop_id, const struct bat_props *p) {
    struct pbuf pb = {buf, cap, 0};
    int32_t ret = -22;
    int32_t has = 0;
    int64_t val = 0;
    if (prop_id == PROP_CAPACITY) {
        ret = 0;
        has = 1;
        val = (int64_t)p->level;
    } else if (prop_id == PROP_CURRENT_NOW || prop_id == PROP_CURRENT_AVG) {
        ret = 0;
        has = 1;
        val = (int64_t)p->current_ua;
    }
    if (pb_write_i32(&pb, 0) < 0) return -1;
    if (pb_write_i32(&pb, ret) < 0) return -1;
    if (pb_write_i32(&pb, has) < 0) return -1;
    if (has && pb_write_i64(&pb, val) < 0) return -1;
    *len_out = (uint32_t)pb.len;
    return 0;
}

static int fmt_append_str(char *buf, size_t cap, size_t *pos, const char *s) {
    while (*s) {
        if (*pos >= cap) return -1;
        buf[(*pos)++] = *s++;
    }
    return 0;
}

static int fmt_append_i32(char *buf, size_t cap, size_t *pos, int32_t v) {
    char tmp[16];
    size_t n = 0;
    uint32_t u;
    if (v < 0) {
        if (*pos >= cap) return -1;
        buf[(*pos)++] = '-';
        u = (uint32_t)(-(v + 1)) + 1U;
    } else {
        u = (uint32_t)v;
    }
    do {
        uint32_t rem = 0;
        u = udiv32(u, 10U, &rem);
        tmp[n++] = (char)('0' + rem);
    } while (u != 0 && n < sizeof(tmp));
    while (n > 0) {
        if (*pos >= cap) return -1;
        buf[(*pos)++] = tmp[--n];
    }
    return 0;
}

static size_t format_battery_dump(char *buf, size_t cap, const struct bat_props *p) {
    size_t pos = 0;
    fmt_append_str(buf, cap, &pos, "ac="); fmt_append_i32(buf, cap, &pos, p->ac);
    fmt_append_str(buf, cap, &pos, " usb="); fmt_append_i32(buf, cap, &pos, p->usb);
    fmt_append_str(buf, cap, &pos, " present="); fmt_append_i32(buf, cap, &pos, p->present);
    fmt_append_str(buf, cap, &pos, " level="); fmt_append_i32(buf, cap, &pos, p->level);
    fmt_append_str(buf, cap, &pos, " voltage="); fmt_append_i32(buf, cap, &pos, p->voltage_mv);
    fmt_append_str(buf, cap, &pos, " current="); fmt_append_i32(buf, cap, &pos, p->current_ua);
    fmt_append_str(buf, cap, &pos, " temp="); fmt_append_i32(buf, cap, &pos, p->temp_tenths);
    fmt_append_str(buf, cap, &pos, " status="); fmt_append_i32(buf, cap, &pos, p->status);
    fmt_append_str(buf, cap, &pos, " health="); fmt_append_i32(buf, cap, &pos, p->health);
    fmt_append_str(buf, cap, &pos, " tech="); fmt_append_str(buf, cap, &pos, p->tech);
    fmt_append_str(buf, cap, &pos, "\n");
    return pos;
}

#ifndef UNIT_TEST
struct wbuf {
    uint8_t data[512];
    size_t len;
};

static int wb_put(struct wbuf *wb, const void *p, size_t n) {
    if (wb->len + n > sizeof(wb->data)) return -1;
    memcpy(wb->data + wb->len, p, n);
    wb->len += n;
    return 0;
}

static int wb_cmd_u32(struct wbuf *wb, uint32_t cmd, uint32_t v) {
    return wb_put(wb, &cmd, 4) < 0 ? -1 : wb_put(wb, &v, 4);
}

static int wb_cmd_pc(struct wbuf *wb, uint32_t cmd, uint32_t a, uint32_t b) {
    struct binder_ptr_cookie_32 pc = {a, b};
    return wb_put(wb, &cmd, 4) < 0 ? -1 : wb_put(wb, &pc, sizeof(pc));
}

static int wb_cmd_hc(struct wbuf *wb, uint32_t cmd, uint32_t h, uint32_t c) {
    struct binder_handle_cookie_32 hc = {h, c};
    return wb_put(wb, &cmd, 4) < 0 ? -1 : wb_put(wb, &hc, sizeof(hc));
}

static int wb_cmd_tr(struct wbuf *wb, uint32_t cmd, uint32_t target, uint32_t code,
                     uint32_t flags, const void *data, uint32_t data_size,
                     const uint32_t *offsets, uint32_t offsets_size) {
    struct binder_transaction_data_32 tr;
    memset(&tr, 0, sizeof(tr));
    tr.target.handle = target;
    tr.code = code;
    tr.flags = flags;
    tr.data_size = data_size;
    tr.offsets_size = offsets_size;
    tr.data.ptr.buffer = (uint32_t)(uintptr_t)data;
    tr.data.ptr.offsets = (uint32_t)(uintptr_t)offsets;
    return wb_put(wb, &cmd, 4) < 0 ? -1 : wb_put(wb, &tr, sizeof(tr));
}

static int binder_flush(int fd, struct wbuf *wb) {
    struct binder_write_read_32 bwr;
    memset(&bwr, 0, sizeof(bwr));
    bwr.write_size = (uint32_t)wb->len;
    bwr.write_buffer = (uint32_t)(uintptr_t)wb->data;
    int rc = sys_ioctl(fd, BINDER_WRITE_READ_32, &bwr);
    wb->len = 0;
    return rc;
}

static int binder_read_once(int fd, uint8_t *rbuf, size_t rcap, size_t *rconsumed) {
    struct binder_write_read_32 bwr;
    memset(&bwr, 0, sizeof(bwr));
    bwr.read_size = (uint32_t)rcap;
    bwr.read_buffer = (uint32_t)(uintptr_t)rbuf;
    int rc = sys_ioctl(fd, BINDER_WRITE_READ_32, &bwr);
    *rconsumed = (rc >= 0) ? (size_t)bwr.read_consumed : 0;
    return rc;
}

struct listener_tab {
    uint32_t handles[MAX_LISTENERS];
    uint8_t active[MAX_LISTENERS];
};

static int listener_add(struct listener_tab *t, uint32_t h) {
    int free_idx = -1;
    for (int i = 0; i < MAX_LISTENERS; i++) {
        if (t->active[i] && t->handles[i] == h) return 0;
        if (!t->active[i] && free_idx < 0) free_idx = i;
    }
    if (free_idx < 0) return -1;
    t->handles[free_idx] = h;
    t->active[free_idx] = 1;
    return 1;
}

static int listener_del(struct listener_tab *t, uint32_t h) {
    for (int i = 0; i < MAX_LISTENERS; i++) {
        if (t->active[i] && t->handles[i] == h) {
            t->active[i] = 0;
            t->handles[i] = 0;
            return 1;
        }
    }
    return 0;
}

static void send_battery_to_handle(int fd, uint32_t handle, const struct bat_props *p) {
    uint8_t pbuf[512];
    uint32_t plen = 0;
    if (build_battery_changed_parcel(pbuf, sizeof(pbuf), &plen, p) < 0) return;
    struct wbuf wb;
    wb.len = 0;
    if (wb_cmd_tr(&wb, BC_TRANSACTION_32, handle, 1U, TF_ONE_WAY, pbuf, plen, NULL, 0) < 0) return;
    binder_flush(fd, &wb);
}

static void broadcast_battery(int fd, const struct listener_tab *t, const struct bat_props *p) {
    for (int i = 0; i < MAX_LISTENERS; i++) {
        if (t->active[i]) {
            send_battery_to_handle(fd, t->handles[i], p);
        }
    }
}

static void handle_ptr_ref_cmds(int fd, uint32_t cmd, const uint8_t *payload) {
    struct binder_ptr_cookie_32 pc;
    memcpy(&pc, payload, sizeof(pc));
    if (cmd == BR_INCREFS_32 || cmd == BR_ACQUIRE_32) {
        struct wbuf wb;
        wb.len = 0;
        uint32_t reply_cmd = (cmd == BR_INCREFS_32) ? BC_INCREFS_DONE_32 : BC_ACQUIRE_DONE_32;
        wb_cmd_pc(&wb, reply_cmd, pc.ptr, pc.cookie);
        binder_flush(fd, &wb);
    }
}

static int register_service_retry(int fd) {
    uint8_t pbuf[256];
    uint32_t plen = 0;
    uint32_t off0 = 0;
    if (build_add_service_parcel(pbuf, sizeof(pbuf), &plen, &off0) < 0) return -1;

    for (;;) {
        struct wbuf wb;
        wb.len = 0;
        wb_cmd_tr(&wb, BC_TRANSACTION_32, 0U, 3U, TF_ACCEPT_FDS, pbuf, plen, &off0, 4U);
        if (binder_flush(fd, &wb) < 0) {
            struct timespec ts = {0, 100000000L};
            sys_nanosleep(&ts);
            continue;
        }

        int done = 0;
        int ok = 0;
        while (!done) {
            uint8_t rbuf[1024];
            size_t rlen = 0;
            if (binder_read_once(fd, rbuf, sizeof(rbuf), &rlen) < 0 || rlen == 0) {
                break;
            }
            size_t pos = 0;
            while (pos + 4 <= rlen) {
                uint32_t cmd;
                memcpy(&cmd, rbuf + pos, 4);
                pos += 4;
                switch (cmd) {
                case BR_NOOP_CMD:
                case BR_TRANSACTION_COMPLETE_CMD:
                case BR_SPAWN_LOOPER_CMD:
                case BR_FINISHED_CMD:
                case BR_OK_CMD:
                    break;
                case BR_DEAD_REPLY_CMD:
                case BR_FAILED_REPLY_CMD:
                    done = 1;
                    break;
                case BR_ERROR_CMD:
                case BR_ACQUIRE_RESULT_CMD:
                case BR_DEAD_BINDER_32:
                case BR_CLEAR_DEATH_NOTIFICATION_DONE_32:
                    if (pos + 4 > rlen) { pos = rlen; break; }
                    if (cmd == BR_ERROR_CMD) done = 1;
                    pos += 4;
                    break;
                case BR_INCREFS_32:
                case BR_ACQUIRE_32:
                case BR_RELEASE_32:
                case BR_DECREFS_32:
                    if (pos + 8 > rlen) { pos = rlen; break; }
                    handle_ptr_ref_cmds(fd, cmd, rbuf + pos);
                    pos += 8;
                    break;
                case BR_ATTEMPT_ACQUIRE_32:
                    if (pos + 12 > rlen) { pos = rlen; break; }
                    wb.len = 0;
                    wb_cmd_u32(&wb, BC_ACQUIRE_RESULT_CMD, 0);
                    binder_flush(fd, &wb);
                    pos += 12;
                    break;
                case BR_REPLY_32: {
                    if (pos + sizeof(struct binder_transaction_data_32) > rlen) {
                        pos = rlen;
                        done = 1;
                        break;
                    }
                    struct binder_transaction_data_32 tr;
                    memcpy(&tr, rbuf + pos, sizeof(tr));
                    pos += sizeof(tr);
                    int32_t reply_rc = 0;
                    if (tr.data_size >= 4 && tr.data.ptr.buffer != 0) {
                        memcpy(&reply_rc, (const void *)(uintptr_t)tr.data.ptr.buffer, 4);
                    }
                    wb.len = 0;
                    wb_cmd_u32(&wb, BC_FREE_BUFFER_32, tr.data.ptr.buffer);
                    binder_flush(fd, &wb);
                    if (reply_rc == 0) ok = 1;
                    done = 1;
                    break;
                }
                case BR_TRANSACTION_32: {
                    if (pos + sizeof(struct binder_transaction_data_32) > rlen) {
                        pos = rlen;
                        break;
                    }
                    struct binder_transaction_data_32 tr;
                    memcpy(&tr, rbuf + pos, sizeof(tr));
                    pos += sizeof(tr);
                    wb.len = 0;
                    wb_cmd_u32(&wb, BC_FREE_BUFFER_32, tr.data.ptr.buffer);
                    binder_flush(fd, &wb);
                    break;
                }
                default:
                    pos = rlen;
                    done = 1;
                    break;
                }
            }
        }
        if (ok) return 0;
        struct timespec ts = {0, 100000000L};
        sys_nanosleep(&ts);
    }
}

static void handle_transaction(int fd, const struct binder_transaction_data_32 *tr,
                               struct listener_tab *ltab, struct bat_props *cur_props) {
    struct wbuf wb;
    wb.len = 0;
    const uint8_t *data = (const uint8_t *)(uintptr_t)tr->data.ptr.buffer;
    const uint32_t *offs = (const uint32_t *)(uintptr_t)tr->data.ptr.offsets;
    uint8_t rep[256];
    uint32_t rep_len = 0;
    uint32_t notify_handle = 0;
    int do_notify = 0;

    if (tr->code == 1U) {
        if (tr->offsets_size >= 4U && offs != NULL && data != NULL) {
            uint32_t off0 = offs[0];
            if (off0 + sizeof(struct flat_binder_object_32) <= tr->data_size) {
                struct flat_binder_object_32 fbo;
                memcpy(&fbo, data + off0, sizeof(fbo));
                if (fbo.type == BINDER_TYPE_HANDLE) {
                    int added = listener_add(ltab, (uint32_t)fbo.handle);
                    if (added > 0) {
                        wb_cmd_u32(&wb, BC_INCREFS_CMD, (uint32_t)fbo.handle);
                        wb_cmd_u32(&wb, BC_ACQUIRE_CMD, (uint32_t)fbo.handle);
                        wb_cmd_hc(&wb, BC_REQUEST_DEATH_NOTIFICATION_32,
                                  (uint32_t)fbo.handle, (uint32_t)fbo.handle);
                    }
                    if (added >= 0) {
                        notify_handle = (uint32_t)fbo.handle;
                        do_notify = 1;
                    }
                }
            }
        }
        int32_t zero = 0;
        memcpy(rep, &zero, 4);
        rep_len = 4;
        if (!(tr->flags & TF_ONE_WAY)) {
            wb_cmd_tr(&wb, BC_REPLY_32, 0U, 0U, 0U, rep, rep_len, NULL, 0U);
        }
        wb_cmd_u32(&wb, BC_FREE_BUFFER_32, tr->data.ptr.buffer);
        binder_flush(fd, &wb);
        if (do_notify) {
            read_battery_props("/sys/class/power_supply", cur_props);
            send_battery_to_handle(fd, notify_handle, cur_props);
        }
        return;
    }

    if (tr->code == 2U) {
        if (tr->offsets_size >= 4U && offs != NULL && data != NULL) {
            uint32_t off0 = offs[0];
            if (off0 + sizeof(struct flat_binder_object_32) <= tr->data_size) {
                struct flat_binder_object_32 fbo;
                memcpy(&fbo, data + off0, sizeof(fbo));
                if (fbo.type == BINDER_TYPE_HANDLE &&
                    listener_del(ltab, (uint32_t)fbo.handle) > 0) {
                    wb_cmd_hc(&wb, BC_CLEAR_DEATH_NOTIFICATION_32,
                              (uint32_t)fbo.handle, (uint32_t)fbo.handle);
                    wb_cmd_u32(&wb, BC_RELEASE_CMD, (uint32_t)fbo.handle);
                    wb_cmd_u32(&wb, BC_DECREFS_CMD, (uint32_t)fbo.handle);
                }
            }
        }
        int32_t zero = 0;
        memcpy(rep, &zero, 4);
        rep_len = 4;
        if (!(tr->flags & TF_ONE_WAY)) {
            wb_cmd_tr(&wb, BC_REPLY_32, 0U, 0U, 0U, rep, rep_len, NULL, 0U);
        }
        wb_cmd_u32(&wb, BC_FREE_BUFFER_32, tr->data.ptr.buffer);
        binder_flush(fd, &wb);
        return;
    }

    if (tr->code == 3U) {
        int32_t prop_id = -1;
        size_t pos = 0;
        if (data != NULL && pb_skip_iface(data, tr->data_size, &pos) == 0 &&
            pos + 4 <= tr->data_size) {
            memcpy(&prop_id, data + pos, 4);
        }
        read_battery_props("/sys/class/power_supply", cur_props);
        build_get_property_reply(rep, sizeof(rep), &rep_len, prop_id, cur_props);
        if (!(tr->flags & TF_ONE_WAY)) {
            wb_cmd_tr(&wb, BC_REPLY_32, 0U, 0U, 0U, rep, rep_len, NULL, 0U);
        }
        wb_cmd_u32(&wb, BC_FREE_BUFFER_32, tr->data.ptr.buffer);
        binder_flush(fd, &wb);
        return;
    }

    if (tr->code == INTERFACE_TRANSACTION) {
        struct pbuf pb = {rep, sizeof(rep), 0};
        pb_write_str16_ascii(&pb, "android.os.IBatteryPropertiesRegistrar");
        rep_len = (uint32_t)pb.len;
        if (!(tr->flags & TF_ONE_WAY)) {
            wb_cmd_tr(&wb, BC_REPLY_32, 0U, 0U, 0U, rep, rep_len, NULL, 0U);
        }
        wb_cmd_u32(&wb, BC_FREE_BUFFER_32, tr->data.ptr.buffer);
        binder_flush(fd, &wb);
        return;
    }

    if (!(tr->flags & TF_ONE_WAY)) {
        wb_cmd_tr(&wb, BC_REPLY_32, 0U, 0U, 0U, NULL, 0U, NULL, 0U);
    }
    wb_cmd_u32(&wb, BC_FREE_BUFFER_32, tr->data.ptr.buffer);
    binder_flush(fd, &wb);
}

int kmain(int argc, char **argv) {
    struct bat_props cur_props;
    read_battery_props("/sys/class/power_supply", &cur_props);

    if (argc >= 2 && argv && argv[1] && cstreq(argv[1], "--dump")) {
        char out[256];
        size_t n = format_battery_dump(out, sizeof(out), &cur_props);
        sys_write(1, out, n);
        return 0;
    }

    int fd = sys_open("/dev/binder", O_RDWR | O_CLOEXEC);
    if (fd < 0) return 1;

    int32_t ver = 0;
    if (sys_ioctl(fd, BINDER_VERSION_REQ, &ver) < 0 || ver != 7) {
        sys_close(fd);
        return 2;
    }

    uint32_t max_threads = 0;
    sys_ioctl(fd, BINDER_SET_MAX_THREADS, &max_threads);

    void *mapped = sys_mmap_ro(128U * 1024U, fd);
    if (mapped == (void *)-1 || (uintptr_t)mapped >= (uintptr_t)-4095L) {
        sys_close(fd);
        return 3;
    }

    if (register_service_retry(fd) < 0) {
        sys_close(fd);
        return 4;
    }

    struct wbuf wb;
    wb.len = 0;
    uint32_t cmd_enter = BC_ENTER_LOOPER_CMD;
    wb_put(&wb, &cmd_enter, 4);
    if (binder_flush(fd, &wb) < 0) {
        sys_close(fd);
        return 5;
    }

    struct listener_tab ltab;
    memset(&ltab, 0, sizeof(ltab));
    int idle_ticks = 0;

    for (;;) {
        struct pollfd pfd;
        pfd.fd = fd;
        pfd.events = POLLIN;
        pfd.revents = 0;
        int pr = sys_poll(&pfd, 1, 30000);
        if (pr > 0 && (pfd.revents & POLLIN)) {
            uint8_t rbuf[4096];
            size_t rlen = 0;
            if (binder_read_once(fd, rbuf, sizeof(rbuf), &rlen) < 0) {
                continue;
            }
            size_t pos = 0;
            while (pos + 4 <= rlen) {
                uint32_t cmd;
                memcpy(&cmd, rbuf + pos, 4);
                pos += 4;
                switch (cmd) {
                case BR_NOOP_CMD:
                case BR_TRANSACTION_COMPLETE_CMD:
                case BR_DEAD_REPLY_CMD:
                case BR_FAILED_REPLY_CMD:
                case BR_SPAWN_LOOPER_CMD:
                case BR_FINISHED_CMD:
                case BR_OK_CMD:
                    break;
                case BR_ERROR_CMD:
                case BR_ACQUIRE_RESULT_CMD:
                case BR_CLEAR_DEATH_NOTIFICATION_DONE_32:
                    if (pos + 4 > rlen) { pos = rlen; break; }
                    pos += 4;
                    break;
                case BR_DEAD_BINDER_32: {
                    if (pos + 4 > rlen) { pos = rlen; break; }
                    uint32_t cookie;
                    memcpy(&cookie, rbuf + pos, 4);
                    pos += 4;
                    wb.len = 0;
                    if (listener_del(&ltab, cookie) > 0) {
                        wb_cmd_u32(&wb, BC_RELEASE_CMD, cookie);
                        wb_cmd_u32(&wb, BC_DECREFS_CMD, cookie);
                    }
                    wb_cmd_u32(&wb, BC_DEAD_BINDER_DONE_32, cookie);
                    binder_flush(fd, &wb);
                    break;
                }
                case BR_INCREFS_32:
                case BR_ACQUIRE_32:
                case BR_RELEASE_32:
                case BR_DECREFS_32:
                    if (pos + 8 > rlen) { pos = rlen; break; }
                    handle_ptr_ref_cmds(fd, cmd, rbuf + pos);
                    pos += 8;
                    break;
                case BR_ATTEMPT_ACQUIRE_32:
                    if (pos + 12 > rlen) { pos = rlen; break; }
                    wb.len = 0;
                    wb_cmd_u32(&wb, BC_ACQUIRE_RESULT_CMD, 0);
                    binder_flush(fd, &wb);
                    pos += 12;
                    break;
                case BR_TRANSACTION_32: {
                    if (pos + sizeof(struct binder_transaction_data_32) > rlen) {
                        pos = rlen;
                        break;
                    }
                    struct binder_transaction_data_32 tr;
                    memcpy(&tr, rbuf + pos, sizeof(tr));
                    pos += sizeof(tr);
                    handle_transaction(fd, &tr, &ltab, &cur_props);
                    break;
                }
                case BR_REPLY_32: {
                    if (pos + sizeof(struct binder_transaction_data_32) > rlen) {
                        pos = rlen;
                        break;
                    }
                    struct binder_transaction_data_32 tr;
                    memcpy(&tr, rbuf + pos, sizeof(tr));
                    pos += sizeof(tr);
                    wb.len = 0;
                    wb_cmd_u32(&wb, BC_FREE_BUFFER_32, tr.data.ptr.buffer);
                    binder_flush(fd, &wb);
                    break;
                }
                default:
                    pos = rlen;
                    break;
                }
            }
        } else if (pr == 0) {
            struct bat_props next_props;
            read_battery_props("/sys/class/power_supply", &next_props);
            idle_ticks++;
            if (!bat_props_equal(&cur_props, &next_props) || idle_ticks >= 2) {
                cur_props = next_props;
                idle_ticks = 0;
                broadcast_battery(fd, &ltab, &cur_props);
            }
        }
    }
    return 0;
}

#ifdef FREESTANDING_ARM
__attribute__((naked)) void _start(void) {
    __asm__ volatile(
        "ldr r0, [sp]\n"
        "add r1, sp, #4\n"
        "bic sp, sp, #7\n"
        "bl kmain\n"
        "mov r7, #248\n"
        "svc #0\n"
        "b .\n");
}
#else
int main(int argc, char **argv) {
    return kmain(argc, argv);
}
#endif
#endif
