/* SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Embrith - Kindle 32-bit Binder v7 to v8 ABI compatibility shim (libkindlebinder)
 *
 * Copyright (C) 2026 Embrith contributors
 *
 * Overview
 *   Older Kindle kernels (e.g. Kindle Scribe 2022 on Linux 4.9.77-lab126) run
 *   the 32-bit Android Binder protocol v7 (CONFIG_ANDROID_BINDER_IPC without
 *   CONFIG_ANDROID_BINDER_IPC_32BIT=n), whereas Embrith's Android 7.1.2 userspace
 *   is compiled for Binder protocol v8 (64-bit binder_uintptr_t / binder_size_t
 *   wire structs on 32-bit ARM).
 *
 *   Loaded via LD_PRELOAD only when the host kernel reports BINDER_VERSION == 7,
 *   this library intercepts Binder ioctls on /dev/binder and translates:
 *     1. BINDER_VERSION: reports protocol version 8 to userspace when the kernel
 *        answers 7, avoiding an immediate abort in ProcessState::initWithDriver().
 *     2. BINDER_WRITE_READ: converts write buffers (BC_* v8 -> v7) and read
 *        buffers (BR_* v7 -> v8), including binder_transaction_data, fixed
 *        object/cookie structs, and inline flat_binder_object payloads + offsets.
 *     3. Kernel mmap parcel lifetime: allocates shadow userspace parcel buffers
 *        on BR_TRANSACTION_32 / BR_REPLY_32 that contain objects so expanded
 *        24-byte flat_binder_object_64 entries fit safely, and maps v8
 *        BC_FREE_BUFFER cookies back to the kernel's original 32-bit pointer.
 *
 * Build modes
 *   - Default (AOSP Bionic / POSIX headers): uses <dlfcn.h>, <sys/syscall.h>,
 *     RTLD_NEXT, and SYS_ioctl.
 *   - NO_SYSROOT: freestanding headerless ARM32 compile for standalone host
 *     packaging when no NDK sysroot is available.
 *   - UNIT_TEST: host-side unit test mode (excludes ioctl interception).
 *
 * Provenance and references
 *   Implemented from scratch for Embrith (no third-party source code copied).
 *   Wire struct layouts and ioctl numbers follow the Linux UAPI header
 *   linux/android/binder.h (Binder protocol v7 vs v8 / 32-bit vs 64-bit IPC).
 */

#ifndef _GNU_SOURCE
#define _GNU_SOURCE
#endif

#include <stdarg.h>
#include <stddef.h>
#include <stdint.h>

#if defined(UNIT_TEST)
#include <errno.h>
#include <stdlib.h>
#include <string.h>
static const void *test_unflatten_kptr(uint32_t p);
#elif defined(NO_SYSROOT)
void *malloc(size_t size);
void free(void *ptr);
void *memcpy(void *dst, const void *src, size_t n);
void *memset(void *s, int c, size_t n);
long syscall(long number, ...);
void *dlsym(void *handle, const char *symbol);
volatile int *__errno(void);
#define errno (*__errno())
#define RTLD_NEXT_HANDLE ((void *)(uintptr_t)0xfffffffeU)
#define BINDER_SYS_IOCTL 54L
#define test_unflatten_kptr(p) ((const void *)(uintptr_t)(p))
#else
#include <dlfcn.h>
#include <errno.h>
#include <stdlib.h>
#include <string.h>
#include <sys/syscall.h>
#include <unistd.h>
#define RTLD_NEXT_HANDLE RTLD_NEXT
#define BINDER_SYS_IOCTL SYS_ioctl
#define test_unflatten_kptr(p) ((const void *)(uintptr_t)(p))
#endif

#ifndef EINVAL
#define EINVAL 22
#endif
#ifndef ENOMEM
#define ENOMEM 12
#endif

#define BINDER_VERSION_REQ 0xc0046209U
#define BINDER_WRITE_READ_64 0xc0306201U
#define BINDER_WRITE_READ_32 0xc0186201U

#define BC_TRANSACTION_64 0x40406300U
#define BC_TRANSACTION_32 0x40286300U
#define BC_REPLY_64 0x40406301U
#define BC_REPLY_32 0x40286301U
#define BC_ACQUIRE_RESULT_CMD 0x40046302U
#define BC_FREE_BUFFER_64 0x40086303U
#define BC_FREE_BUFFER_32 0x40046303U
#define BC_INCREFS_CMD 0x40046304U
#define BC_ACQUIRE_CMD 0x40046305U
#define BC_RELEASE_CMD 0x40046306U
#define BC_DECREFS_CMD 0x40046307U
#define BC_INCREFS_DONE_64 0x40106308U
#define BC_INCREFS_DONE_32 0x40086308U
#define BC_ACQUIRE_DONE_64 0x40106309U
#define BC_ACQUIRE_DONE_32 0x40086309U
#define BC_ATTEMPT_ACQUIRE_CMD 0x4008630aU
#define BC_REGISTER_LOOPER_CMD 0x0000630bU
#define BC_ENTER_LOOPER_CMD 0x0000630cU
#define BC_EXIT_LOOPER_CMD 0x0000630dU
#define BC_REQUEST_DEATH_NOTIFICATION_64 0x400c630eU
#define BC_REQUEST_DEATH_NOTIFICATION_32 0x4008630eU
#define BC_CLEAR_DEATH_NOTIFICATION_64 0x400c630fU
#define BC_CLEAR_DEATH_NOTIFICATION_32 0x4008630fU
#define BC_DEAD_BINDER_DONE_64 0x40086310U
#define BC_DEAD_BINDER_DONE_32 0x40046310U

#define BR_ERROR_CMD 0x80047200U
#define BR_OK_CMD 0x00007201U
#define BR_TRANSACTION_32 0x80287202U
#define BR_TRANSACTION_64 0x80407202U
#define BR_REPLY_32 0x80287203U
#define BR_REPLY_64 0x80407203U
#define BR_ACQUIRE_RESULT_CMD 0x80047204U
#define BR_DEAD_REPLY_CMD 0x00007205U
#define BR_TRANSACTION_COMPLETE_CMD 0x00007206U
#define BR_INCREFS_32 0x80087207U
#define BR_INCREFS_64 0x80107207U
#define BR_ACQUIRE_32 0x80087208U
#define BR_ACQUIRE_64 0x80107208U
#define BR_RELEASE_32 0x80087209U
#define BR_RELEASE_64 0x80107209U
#define BR_DECREFS_32 0x8008720aU
#define BR_DECREFS_64 0x8010720aU
#define BR_ATTEMPT_ACQUIRE_32 0x800c720bU
#define BR_ATTEMPT_ACQUIRE_64 0x8018720bU
#define BR_NOOP_CMD 0x0000720cU
#define BR_SPAWN_LOOPER_CMD 0x0000720dU
#define BR_FINISHED_CMD 0x0000720eU
#define BR_DEAD_BINDER_32 0x8004720fU
#define BR_DEAD_BINDER_64 0x8008720fU
#define BR_CLEAR_DEATH_NOTIFICATION_DONE_32 0x80047210U
#define BR_CLEAR_DEATH_NOTIFICATION_DONE_64 0x80087210U
#define BR_FAILED_REPLY_CMD 0x00007211U

struct binder_write_read_64 {
    uint64_t write_size;
    uint64_t write_consumed;
    uint64_t write_buffer;
    uint64_t read_size;
    uint64_t read_consumed;
    uint64_t read_buffer;
};

struct binder_write_read_32 {
    uint32_t write_size;
    uint32_t write_consumed;
    uint32_t write_buffer;
    uint32_t read_size;
    uint32_t read_consumed;
    uint32_t read_buffer;
};

struct binder_transaction_data_64 {
    union {
        uint32_t handle;
        uint64_t ptr;
    } target;
    uint64_t cookie;
    uint32_t code;
    uint32_t flags;
    int32_t sender_pid;
    uint32_t sender_euid;
    uint64_t data_size;
    uint64_t offsets_size;
    union {
        struct {
            uint64_t buffer;
            uint64_t offsets;
        } ptr;
        uint8_t buf[8];
    } data;
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

struct flat_binder_object_64 {
    uint32_t type;
    uint32_t flags;
    union {
        uint64_t binder;
        int32_t handle;
    };
    uint64_t cookie;
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

struct binder_ptr_cookie_64 {
    uint64_t ptr;
    uint64_t cookie;
};

struct binder_ptr_cookie_32 {
    uint32_t ptr;
    uint32_t cookie;
};

struct binder_handle_cookie_64 {
    uint32_t handle;
    uint64_t cookie;
} __attribute__((packed));

struct binder_handle_cookie_32 {
    uint32_t handle;
    uint32_t cookie;
};

struct binder_pri_ptr_cookie_64 {
    int32_t priority;
    uint32_t pad;
    uint64_t ptr;
    uint64_t cookie;
};

struct binder_pri_ptr_cookie_32 {
    int32_t priority;
    uint32_t ptr;
    uint32_t cookie;
};

_Static_assert(sizeof(struct binder_write_read_64) == 48, "bwr64");
_Static_assert(sizeof(struct binder_write_read_32) == 24, "bwr32");
_Static_assert(sizeof(struct binder_transaction_data_64) == 64, "tr64");
_Static_assert(sizeof(struct binder_transaction_data_32) == 40, "tr32");
_Static_assert(sizeof(struct flat_binder_object_64) == 24, "fbo64");
_Static_assert(sizeof(struct flat_binder_object_32) == 16, "fbo32");
_Static_assert(sizeof(struct binder_ptr_cookie_64) == 16, "pc64");
_Static_assert(sizeof(struct binder_ptr_cookie_32) == 8, "pc32");
_Static_assert(sizeof(struct binder_handle_cookie_64) == 12, "hc64");
_Static_assert(sizeof(struct binder_handle_cookie_32) == 8, "hc32");
_Static_assert(sizeof(struct binder_pri_ptr_cookie_64) == 24, "ppc64");
_Static_assert(sizeof(struct binder_pri_ptr_cookie_32) == 12, "ppc32");

#define SHADOW_MAX 4096
#define MAX_TMP_ALLOCS 128
#define MAX_CMD_MAP 512
#define MAX_FREE_DEFER 512

struct shadow_entry {
    uintptr_t u_ptr;
    uint32_t k_ptr;
    uint8_t state;
};

static struct shadow_entry g_shadows[SHADOW_MAX];
static size_t g_shadow_active = 0;
static size_t g_shadow_tombs = 0;
static volatile int g_shadow_lock = 0;

static inline void lock_acquire(volatile int *lk) {
    while (__atomic_test_and_set(lk, __ATOMIC_ACQUIRE)) {
    }
}

static inline void lock_release(volatile int *lk) {
    __atomic_clear(lk, __ATOMIC_RELEASE);
}

static inline size_t shadow_hash(uintptr_t u_ptr) {
    return (((size_t)u_ptr >> 4) * 2654435761U) & (SHADOW_MAX - 1);
}

static int shadow_insert(uintptr_t u_ptr, uint32_t k_ptr) {
    size_t idx = shadow_hash(u_ptr);
    size_t first_tomb = (size_t)-1;
    lock_acquire(&g_shadow_lock);
    for (size_t n = 0; n < SHADOW_MAX; n++) {
        size_t i = (idx + n) & (SHADOW_MAX - 1);
        uint8_t st = g_shadows[i].state;
        if (st == 0) {
            size_t dst = (first_tomb != (size_t)-1) ? first_tomb : i;
            if (g_shadows[dst].state == 2) {
                g_shadow_tombs--;
            }
            g_shadows[dst].u_ptr = u_ptr;
            g_shadows[dst].k_ptr = k_ptr;
            g_shadows[dst].state = 1;
            g_shadow_active++;
            lock_release(&g_shadow_lock);
            return 0;
        }
        if (st == 2 && first_tomb == (size_t)-1) {
            first_tomb = i;
        } else if (st == 1 && g_shadows[i].u_ptr == u_ptr) {
            g_shadows[i].k_ptr = k_ptr;
            lock_release(&g_shadow_lock);
            return 0;
        }
    }
    if (first_tomb != (size_t)-1) {
        g_shadow_tombs--;
        g_shadows[first_tomb].u_ptr = u_ptr;
        g_shadows[first_tomb].k_ptr = k_ptr;
        g_shadows[first_tomb].state = 1;
        g_shadow_active++;
        lock_release(&g_shadow_lock);
        return 0;
    }
    lock_release(&g_shadow_lock);
    return -1;
}

static int shadow_lookup(uintptr_t u_ptr, uint32_t *k_ptr_out) {
    size_t idx = shadow_hash(u_ptr);
    lock_acquire(&g_shadow_lock);
    if (g_shadow_active == 0) {
        lock_release(&g_shadow_lock);
        return 0;
    }
    for (size_t n = 0; n < SHADOW_MAX; n++) {
        size_t i = (idx + n) & (SHADOW_MAX - 1);
        uint8_t st = g_shadows[i].state;
        if (st == 0) {
            break;
        }
        if (st == 1 && g_shadows[i].u_ptr == u_ptr) {
            *k_ptr_out = g_shadows[i].k_ptr;
            lock_release(&g_shadow_lock);
            return 1;
        }
    }
    lock_release(&g_shadow_lock);
    return 0;
}

static int shadow_remove(uintptr_t u_ptr) {
    size_t idx = shadow_hash(u_ptr);
    lock_acquire(&g_shadow_lock);
    for (size_t n = 0; n < SHADOW_MAX; n++) {
        size_t i = (idx + n) & (SHADOW_MAX - 1);
        uint8_t st = g_shadows[i].state;
        if (st == 0) {
            break;
        }
        if (st == 1 && g_shadows[i].u_ptr == u_ptr) {
            g_shadows[i].state = 2;
            g_shadows[i].u_ptr = 0;
            g_shadows[i].k_ptr = 0;
            g_shadow_active--;
            g_shadow_tombs++;
            if (g_shadow_active == 0 && g_shadow_tombs > (SHADOW_MAX / 4)) {
                memset(g_shadows, 0, sizeof(g_shadows));
                g_shadow_tombs = 0;
            }
            lock_release(&g_shadow_lock);
            return 1;
        }
    }
    lock_release(&g_shadow_lock);
    return 0;
}

struct write_ctx {
    void *tmp_allocs[MAX_TMP_ALLOCS];
    size_t num_tmp;
    struct {
        size_t u_off;
        size_t k_off;
    } cmd_map[MAX_CMD_MAP];
    size_t num_cmds;
    struct {
        uintptr_t u_ptr;
        size_t k_end;
    } free_defer[MAX_FREE_DEFER];
    size_t num_free;
};

static void write_ctx_free_tmps(struct write_ctx *ctx) {
    for (size_t i = 0; i < ctx->num_tmp; i++) {
        free(ctx->tmp_allocs[i]);
    }
    ctx->num_tmp = 0;
}

static int write_ctx_add_tmp(struct write_ctx *ctx, void *p) {
    if (ctx->num_tmp >= MAX_TMP_ALLOCS) {
        return -1;
    }
    ctx->tmp_allocs[ctx->num_tmp++] = p;
    return 0;
}

static int translate_write_buf(const uint8_t *u_buf, size_t u_len,
                               uint8_t *k_buf, size_t k_cap, size_t *k_len_out,
                               struct write_ctx *ctx) {
    size_t u_pos = 0;
    size_t k_pos = 0;
    ctx->num_tmp = 0;
    ctx->num_cmds = 0;
    ctx->num_free = 0;

    while (u_pos < u_len) {
        if (u_len - u_pos < 4 || k_cap - k_pos < 4) {
            write_ctx_free_tmps(ctx);
            return -1;
        }
        if (ctx->num_cmds < MAX_CMD_MAP) {
            ctx->cmd_map[ctx->num_cmds].u_off = u_pos;
            ctx->cmd_map[ctx->num_cmds].k_off = k_pos;
            ctx->num_cmds++;
        }

        uint32_t cmd;
        memcpy(&cmd, u_buf + u_pos, 4);
        u_pos += 4;

        switch (cmd) {
        case BC_TRANSACTION_64:
        case BC_REPLY_64: {
            if (u_len - u_pos < 64 || k_cap - k_pos < 44) {
                write_ctx_free_tmps(ctx);
                return -1;
            }
            struct binder_transaction_data_64 tr64;
            struct binder_transaction_data_32 tr32;
            memcpy(&tr64, u_buf + u_pos, 64);
            u_pos += 64;

            if ((tr64.offsets_size % 8u) != 0) {
                write_ctx_free_tmps(ctx);
                return -1;
            }

            memset(&tr32, 0, sizeof(tr32));
            tr32.target.ptr = (uint32_t)tr64.target.ptr;
            tr32.cookie = (uint32_t)tr64.cookie;
            tr32.code = tr64.code;
            tr32.flags = tr64.flags;
            tr32.sender_pid = tr64.sender_pid;
            tr32.sender_euid = tr64.sender_euid;
            tr32.data_size = (uint32_t)tr64.data_size;

            if (tr64.offsets_size > 0) {
                size_t n = (size_t)(tr64.offsets_size / 8u);
                size_t dsz = (size_t)tr64.data_size;
                uint8_t *tmp_data = NULL;
                uint32_t *tmp_offs = NULL;

                if (dsz > 0) {
                    tmp_data = (uint8_t *)malloc(dsz);
                    if (!tmp_data || write_ctx_add_tmp(ctx, tmp_data) != 0) {
                        free(tmp_data);
                        write_ctx_free_tmps(ctx);
                        return -1;
                    }
                    memcpy(tmp_data, (const void *)(uintptr_t)tr64.data.ptr.buffer, dsz);
                }

                if (n > (size_t)-1 / 4u) {
                    write_ctx_free_tmps(ctx);
                    return -1;
                }
                tmp_offs = (uint32_t *)malloc(n * 4u);
                if (!tmp_offs || write_ctx_add_tmp(ctx, tmp_offs) != 0) {
                    free(tmp_offs);
                    write_ctx_free_tmps(ctx);
                    return -1;
                }

                const uint8_t *u_offs = (const uint8_t *)(uintptr_t)tr64.data.ptr.offsets;
                for (size_t i = 0; i < n; i++) {
                    uint64_t off64;
                    memcpy(&off64, u_offs + i * 8u, 8);
                    uint32_t off32 = (uint32_t)off64;
                    tmp_offs[i] = off32;
                    if (tmp_data && off64 <= dsz && dsz - (size_t)off64 >= 24u) {
                        struct flat_binder_object_64 f64;
                        struct flat_binder_object_32 f32;
                        memcpy(&f64, tmp_data + off32, 24);
                        f32.type = f64.type;
                        f32.flags = f64.flags;
                        f32.binder = (uint32_t)f64.binder;
                        f32.cookie = (uint32_t)f64.cookie;
                        memcpy(tmp_data + off32, &f32, 16);
                        memset(tmp_data + off32 + 16, 0, 8);
                    }
                }

                tr32.offsets_size = (uint32_t)(n * 4u);
                tr32.data.ptr.buffer = (uint32_t)(uintptr_t)tmp_data;
                tr32.data.ptr.offsets = (uint32_t)(uintptr_t)tmp_offs;
            } else {
                tr32.offsets_size = 0;
                tr32.data.ptr.buffer = (uint32_t)tr64.data.ptr.buffer;
                tr32.data.ptr.offsets = (uint32_t)tr64.data.ptr.offsets;
            }

            uint32_t kcmd = (cmd == BC_TRANSACTION_64) ? BC_TRANSACTION_32 : BC_REPLY_32;
            memcpy(k_buf + k_pos, &kcmd, 4);
            memcpy(k_buf + k_pos + 4, &tr32, 40);
            k_pos += 44;
            break;
        }
        case BC_FREE_BUFFER_64: {
            if (u_len - u_pos < 8 || k_cap - k_pos < 8) {
                write_ctx_free_tmps(ctx);
                return -1;
            }
            uint64_t u_ptr64;
            memcpy(&u_ptr64, u_buf + u_pos, 8);
            u_pos += 8;

            uint32_t k_ptr32 = (uint32_t)u_ptr64;
            uint32_t found_k = 0;
            if (shadow_lookup((uintptr_t)u_ptr64, &found_k)) {
                k_ptr32 = found_k;
                if (ctx->num_free < MAX_FREE_DEFER) {
                    ctx->free_defer[ctx->num_free].u_ptr = (uintptr_t)u_ptr64;
                    ctx->free_defer[ctx->num_free].k_end = k_pos + 8;
                    ctx->num_free++;
                }
            }

            uint32_t kcmd = BC_FREE_BUFFER_32;
            memcpy(k_buf + k_pos, &kcmd, 4);
            memcpy(k_buf + k_pos + 4, &k_ptr32, 4);
            k_pos += 8;
            break;
        }
        case BC_INCREFS_DONE_64:
        case BC_ACQUIRE_DONE_64: {
            if (u_len - u_pos < 16 || k_cap - k_pos < 12) {
                write_ctx_free_tmps(ctx);
                return -1;
            }
            struct binder_ptr_cookie_64 pc64;
            struct binder_ptr_cookie_32 pc32;
            memcpy(&pc64, u_buf + u_pos, 16);
            u_pos += 16;
            pc32.ptr = (uint32_t)pc64.ptr;
            pc32.cookie = (uint32_t)pc64.cookie;
            uint32_t kcmd = (cmd == BC_INCREFS_DONE_64) ? BC_INCREFS_DONE_32 : BC_ACQUIRE_DONE_32;
            memcpy(k_buf + k_pos, &kcmd, 4);
            memcpy(k_buf + k_pos + 4, &pc32, 8);
            k_pos += 12;
            break;
        }
        case BC_REQUEST_DEATH_NOTIFICATION_64:
        case BC_CLEAR_DEATH_NOTIFICATION_64: {
            if (u_len - u_pos < 12 || k_cap - k_pos < 12) {
                write_ctx_free_tmps(ctx);
                return -1;
            }
            struct binder_handle_cookie_64 hc64;
            struct binder_handle_cookie_32 hc32;
            memcpy(&hc64, u_buf + u_pos, 12);
            u_pos += 12;
            hc32.handle = hc64.handle;
            hc32.cookie = (uint32_t)hc64.cookie;
            uint32_t kcmd = (cmd == BC_REQUEST_DEATH_NOTIFICATION_64)
                                ? BC_REQUEST_DEATH_NOTIFICATION_32
                                : BC_CLEAR_DEATH_NOTIFICATION_32;
            memcpy(k_buf + k_pos, &kcmd, 4);
            memcpy(k_buf + k_pos + 4, &hc32, 8);
            k_pos += 12;
            break;
        }
        case BC_DEAD_BINDER_DONE_64: {
            if (u_len - u_pos < 8 || k_cap - k_pos < 8) {
                write_ctx_free_tmps(ctx);
                return -1;
            }
            uint64_t cookie64;
            memcpy(&cookie64, u_buf + u_pos, 8);
            u_pos += 8;
            uint32_t cookie32 = (uint32_t)cookie64;
            uint32_t kcmd = BC_DEAD_BINDER_DONE_32;
            memcpy(k_buf + k_pos, &kcmd, 4);
            memcpy(k_buf + k_pos + 4, &cookie32, 4);
            k_pos += 8;
            break;
        }
        case BC_ACQUIRE_RESULT_CMD:
        case BC_INCREFS_CMD:
        case BC_ACQUIRE_CMD:
        case BC_RELEASE_CMD:
        case BC_DECREFS_CMD: {
            if (u_len - u_pos < 4 || k_cap - k_pos < 8) {
                write_ctx_free_tmps(ctx);
                return -1;
            }
            memcpy(k_buf + k_pos, &cmd, 4);
            memcpy(k_buf + k_pos + 4, u_buf + u_pos, 4);
            u_pos += 4;
            k_pos += 8;
            break;
        }
        case BC_ATTEMPT_ACQUIRE_CMD: {
            if (u_len - u_pos < 8 || k_cap - k_pos < 12) {
                write_ctx_free_tmps(ctx);
                return -1;
            }
            memcpy(k_buf + k_pos, &cmd, 4);
            memcpy(k_buf + k_pos + 4, u_buf + u_pos, 8);
            u_pos += 8;
            k_pos += 12;
            break;
        }
        case BC_REGISTER_LOOPER_CMD:
        case BC_ENTER_LOOPER_CMD:
        case BC_EXIT_LOOPER_CMD: {
            memcpy(k_buf + k_pos, &cmd, 4);
            k_pos += 4;
            break;
        }
        default:
            write_ctx_free_tmps(ctx);
            return -1;
        }
    }

    *k_len_out = k_pos;
    return 0;
}

static size_t map_write_consumed(const struct write_ctx *ctx, size_t k_consumed,
                                 size_t k_total, size_t u_total) {
    if (k_consumed >= k_total) {
        return u_total;
    }
    for (size_t i = ctx->num_cmds; i > 0; i--) {
        if (k_consumed >= ctx->cmd_map[i - 1].k_off) {
            return ctx->cmd_map[i - 1].u_off;
        }
    }
    return 0;
}

static size_t calc_k_read_size(size_t avail_u) {
    if (avail_u == 0) {
        return 0;
    }
    size_t k = (avail_u * 4u) / 7u;
    k &= ~(size_t)3u;
    if (avail_u >= 68u && k < 44u) {
        k = 44u;
    }
    return k;
}

static int translate_read_buf(const uint8_t *k_buf, size_t k_len,
                              uint8_t *u_buf, size_t u_cap, size_t *u_len_out) {
    size_t k_pos = 0;
    size_t u_pos = 0;

    while (k_pos < k_len) {
        if (k_len - k_pos < 4) {
            return -1;
        }
        uint32_t cmd;
        memcpy(&cmd, k_buf + k_pos, 4);
        k_pos += 4;

        switch (cmd) {
        case BR_TRANSACTION_32:
        case BR_REPLY_32: {
            if (k_len - k_pos < 40 || u_cap - u_pos < 68) {
                return -1;
            }
            struct binder_transaction_data_32 tr32;
            struct binder_transaction_data_64 tr64;
            memcpy(&tr32, k_buf + k_pos, 40);
            k_pos += 40;

            memset(&tr64, 0, sizeof(tr64));
            tr64.target.ptr = (uint64_t)tr32.target.ptr;
            tr64.cookie = (uint64_t)tr32.cookie;
            tr64.code = tr32.code;
            tr64.flags = tr32.flags;
            tr64.sender_pid = tr32.sender_pid;
            tr64.sender_euid = tr32.sender_euid;
            tr64.data_size = (uint64_t)tr32.data_size;

            if (tr32.offsets_size > 0 && (tr32.offsets_size % 4u) == 0) {
                size_t n = (size_t)(tr32.offsets_size / 4u);
                size_t dsz = (size_t)tr32.data_size;
                size_t d_aligned = (dsz + 7u) & ~(size_t)7u;
                if (d_aligned < dsz || n > ((size_t)-1 - d_aligned) / 8u) {
                    return -1;
                }
                size_t total = d_aligned + n * 8u;
                uint8_t *shadow = (uint8_t *)malloc(total ? total : 8u);
                if (!shadow) {
                    return -1;
                }
                if (dsz > 0) {
                    memcpy(shadow, test_unflatten_kptr(tr32.data.ptr.buffer), dsz);
                }

                const uint8_t *k_offs = (const uint8_t *)test_unflatten_kptr(tr32.data.ptr.offsets);
                uint8_t *u_offs = shadow + d_aligned;
                for (size_t i = 0; i < n; i++) {
                    uint32_t off32;
                    memcpy(&off32, k_offs + i * 4u, 4);
                    uint64_t off64 = (uint64_t)off32;
                    memcpy(u_offs + i * 8u, &off64, 8);
                    if (off32 <= dsz && dsz - off32 >= 24u) {
                        struct flat_binder_object_32 f32;
                        struct flat_binder_object_64 f64;
                        memcpy(&f32, shadow + off32, 16);
                        f64.type = f32.type;
                        f64.flags = f32.flags;
                        f64.binder = (uint64_t)f32.binder;
                        f64.cookie = (uint64_t)f32.cookie;
                        memcpy(shadow + off32, &f64, 24);
                    }
                }

                if (shadow_insert((uintptr_t)shadow, tr32.data.ptr.buffer) != 0) {
                    free(shadow);
                    return -1;
                }
                tr64.offsets_size = (uint64_t)(n * 8u);
                tr64.data.ptr.buffer = (uint64_t)(uintptr_t)shadow;
                tr64.data.ptr.offsets = (uint64_t)(uintptr_t)u_offs;
            } else {
                tr64.offsets_size = 0;
                tr64.data.ptr.buffer = (uint64_t)tr32.data.ptr.buffer;
                tr64.data.ptr.offsets = (uint64_t)tr32.data.ptr.offsets;
            }

            uint32_t ucmd = (cmd == BR_TRANSACTION_32) ? BR_TRANSACTION_64 : BR_REPLY_64;
            memcpy(u_buf + u_pos, &ucmd, 4);
            memcpy(u_buf + u_pos + 4, &tr64, 64);
            u_pos += 68;
            break;
        }
        case BR_INCREFS_32:
        case BR_ACQUIRE_32:
        case BR_RELEASE_32:
        case BR_DECREFS_32: {
            if (k_len - k_pos < 8 || u_cap - u_pos < 20) {
                return -1;
            }
            struct binder_ptr_cookie_32 pc32;
            struct binder_ptr_cookie_64 pc64;
            memcpy(&pc32, k_buf + k_pos, 8);
            k_pos += 8;
            pc64.ptr = (uint64_t)pc32.ptr;
            pc64.cookie = (uint64_t)pc32.cookie;
            uint32_t ucmd = (cmd & ~0x00ff0000U) | 0x00100000U;
            memcpy(u_buf + u_pos, &ucmd, 4);
            memcpy(u_buf + u_pos + 4, &pc64, 16);
            u_pos += 20;
            break;
        }
        case BR_ATTEMPT_ACQUIRE_32: {
            if (k_len - k_pos < 12 || u_cap - u_pos < 28) {
                return -1;
            }
            struct binder_pri_ptr_cookie_32 ppc32;
            struct binder_pri_ptr_cookie_64 ppc64;
            memcpy(&ppc32, k_buf + k_pos, 12);
            k_pos += 12;
            ppc64.priority = ppc32.priority;
            ppc64.pad = 0;
            ppc64.ptr = (uint64_t)ppc32.ptr;
            ppc64.cookie = (uint64_t)ppc32.cookie;
            uint32_t ucmd = BR_ATTEMPT_ACQUIRE_64;
            memcpy(u_buf + u_pos, &ucmd, 4);
            memcpy(u_buf + u_pos + 4, &ppc64, 24);
            u_pos += 28;
            break;
        }
        case BR_DEAD_BINDER_32:
        case BR_CLEAR_DEATH_NOTIFICATION_DONE_32: {
            if (k_len - k_pos < 4 || u_cap - u_pos < 12) {
                return -1;
            }
            uint32_t cookie32;
            memcpy(&cookie32, k_buf + k_pos, 4);
            k_pos += 4;
            uint64_t cookie64 = (uint64_t)cookie32;
            uint32_t ucmd = (cmd == BR_DEAD_BINDER_32)
                                ? BR_DEAD_BINDER_64
                                : BR_CLEAR_DEATH_NOTIFICATION_DONE_64;
            memcpy(u_buf + u_pos, &ucmd, 4);
            memcpy(u_buf + u_pos + 4, &cookie64, 8);
            u_pos += 12;
            break;
        }
        case BR_ERROR_CMD:
        case BR_ACQUIRE_RESULT_CMD: {
            if (k_len - k_pos < 4 || u_cap - u_pos < 8) {
                return -1;
            }
            memcpy(u_buf + u_pos, &cmd, 4);
            memcpy(u_buf + u_pos + 4, k_buf + k_pos, 4);
            k_pos += 4;
            u_pos += 8;
            break;
        }
        case BR_OK_CMD:
        case BR_DEAD_REPLY_CMD:
        case BR_TRANSACTION_COMPLETE_CMD:
        case BR_NOOP_CMD:
        case BR_SPAWN_LOOPER_CMD:
        case BR_FINISHED_CMD:
        case BR_FAILED_REPLY_CMD: {
            if (u_cap - u_pos < 4) {
                return -1;
            }
            memcpy(u_buf + u_pos, &cmd, 4);
            u_pos += 4;
            break;
        }
        default:
            return -1;
        }
    }

    *u_len_out = u_pos;
    return 0;
}

#ifndef UNIT_TEST
static volatile int g_binder_v7_active = 0;
typedef int (*ioctl_fn_t)(int, int, ...);
static ioctl_fn_t g_next_ioctl = NULL;

static int raw_ioctl(int fd, unsigned long req, void *arg) {
    return (int)syscall(BINDER_SYS_IOCTL, fd, req, arg);
}

static int chain_ioctl(int fd, int req, void *arg) {
    ioctl_fn_t fn = __atomic_load_n(&g_next_ioctl, __ATOMIC_ACQUIRE);
    if (!fn) {
        extern int ioctl(int, int, ...);
        void *sym = dlsym(RTLD_NEXT_HANDLE, "ioctl");
        if (sym && sym != (void *)ioctl) {
            fn = (ioctl_fn_t)sym;
            __atomic_store_n(&g_next_ioctl, fn, __ATOMIC_RELEASE);
        }
    }
    if (fn) {
        return fn(fd, req, arg);
    }
    return raw_ioctl(fd, (unsigned long)(unsigned int)req, arg);
}

static int handle_bwr64(int fd, struct binder_write_read_64 *u_bwr) {
    if (!u_bwr) {
        errno = EINVAL;
        return -1;
    }

    struct write_ctx wctx;
    wctx.num_tmp = 0;
    wctx.num_cmds = 0;
    wctx.num_free = 0;

    uint8_t k_wstack[512];
    uint8_t *k_wbuf = NULL;
    size_t k_wlen = 0;
    size_t u_wstart = (size_t)u_bwr->write_consumed;
    size_t u_wtot = (size_t)u_bwr->write_size;

    if (u_wstart < u_wtot) {
        size_t u_wrem = u_wtot - u_wstart;
        if (u_wrem <= sizeof(k_wstack)) {
            k_wbuf = k_wstack;
        } else {
            k_wbuf = (uint8_t *)malloc(u_wrem);
            if (!k_wbuf) {
                errno = ENOMEM;
                return -1;
            }
        }
        if (translate_write_buf((const uint8_t *)(uintptr_t)u_bwr->write_buffer + u_wstart,
                                u_wrem, k_wbuf, u_wrem, &k_wlen, &wctx) != 0) {
            if (k_wbuf != k_wstack) {
                free(k_wbuf);
            }
            errno = EINVAL;
            return -1;
        }
    }

    uint8_t k_rstack[512];
    uint8_t *k_rbuf = NULL;
    size_t u_rstart = (size_t)u_bwr->read_consumed;
    size_t u_rtot = (size_t)u_bwr->read_size;
    size_t u_ravail = (u_rstart < u_rtot) ? (u_rtot - u_rstart) : 0;
    size_t k_rcap = calc_k_read_size(u_ravail);

    if (k_rcap > 0) {
        if (k_rcap <= sizeof(k_rstack)) {
            k_rbuf = k_rstack;
        } else {
            k_rbuf = (uint8_t *)malloc(k_rcap);
            if (!k_rbuf) {
                write_ctx_free_tmps(&wctx);
                if (k_wbuf != k_wstack) {
                    free(k_wbuf);
                }
                errno = ENOMEM;
                return -1;
            }
        }
    }

    struct binder_write_read_32 k_bwr;
    k_bwr.write_size = (uint32_t)k_wlen;
    k_bwr.write_consumed = 0;
    k_bwr.write_buffer = (uint32_t)(uintptr_t)k_wbuf;
    k_bwr.read_size = (uint32_t)k_rcap;
    k_bwr.read_consumed = 0;
    k_bwr.read_buffer = (uint32_t)(uintptr_t)k_rbuf;

    int ret = raw_ioctl(fd, BINDER_WRITE_READ_32, &k_bwr);
    int saved_errno = errno;

    write_ctx_free_tmps(&wctx);
    if (k_wbuf && k_wbuf != k_wstack) {
        free(k_wbuf);
    }

    for (size_t i = 0; i < wctx.num_free; i++) {
        if (wctx.free_defer[i].k_end <= k_bwr.write_consumed) {
            shadow_remove(wctx.free_defer[i].u_ptr);
            free((void *)wctx.free_defer[i].u_ptr);
        }
    }

    if (u_wstart < u_wtot && k_bwr.write_consumed > 0) {
        size_t u_consumed_delta = map_write_consumed(&wctx, k_bwr.write_consumed,
                                                     k_wlen, u_wtot - u_wstart);
        u_bwr->write_consumed = (uint64_t)(u_wstart + u_consumed_delta);
    }

    if (k_rbuf && k_bwr.read_consumed > 0) {
        size_t u_written = 0;
        if (translate_read_buf(k_rbuf, k_bwr.read_consumed,
                               (uint8_t *)(uintptr_t)u_bwr->read_buffer + u_rstart,
                               u_ravail, &u_written) != 0) {
            if (k_rbuf != k_rstack) {
                free(k_rbuf);
            }
            errno = EINVAL;
            return -1;
        }
        u_bwr->read_consumed = (uint64_t)(u_rstart + u_written);
    }

    if (k_rbuf && k_rbuf != k_rstack) {
        free(k_rbuf);
    }

    errno = saved_errno;
    return ret;
}

__attribute__((visibility("default")))
int ioctl(int fd, int request, ...) {
    va_list ap;
    va_start(ap, request);
    void *arg = va_arg(ap, void *);
    va_end(ap);

    unsigned int ureq = (unsigned int)request;
    if (ureq == BINDER_VERSION_REQ) {
        int ret = raw_ioctl(fd, ureq, arg);
        if (ret == 0 && arg) {
            int32_t ver = 0;
            memcpy(&ver, arg, sizeof(ver));
            if (ver == 7) {
                __atomic_store_n(&g_binder_v7_active, 1, __ATOMIC_RELEASE);
                int32_t v8 = 8;
                memcpy(arg, &v8, sizeof(v8));
            } else if (ver == 8) {
                __atomic_store_n(&g_binder_v7_active, -1, __ATOMIC_RELEASE);
            }
        }
        return ret;
    }

    if (ureq == BINDER_WRITE_READ_64) {
        int active = __atomic_load_n(&g_binder_v7_active, __ATOMIC_ACQUIRE);
        if (active == 0) {
            int32_t ver = 0;
            if (raw_ioctl(fd, BINDER_VERSION_REQ, &ver) == 0) {
                active = (ver == 7) ? 1 : -1;
                __atomic_store_n(&g_binder_v7_active, active, __ATOMIC_RELEASE);
            }
        }
        if (active == 1) {
            return handle_bwr64(fd, (struct binder_write_read_64 *)arg);
        }
        return raw_ioctl(fd, ureq, arg);
    }

    if ((ureq & 0xff00U) == 0x6200U) {
        return raw_ioctl(fd, ureq, arg);
    }

    return chain_ioctl(fd, request, arg);
}
#endif
