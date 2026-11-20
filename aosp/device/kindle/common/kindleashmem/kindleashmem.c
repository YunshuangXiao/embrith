/* SPDX-License-Identifier: Apache-2.0
 *
 * Embrith - Kindle ashmem compatibility layer (libkindleashmem)
 *
 * Copyright (C) 2026 Embrith contributors
 *
 * Overview
 *   User-space stand-in for the missing /dev/ashmem driver, loaded with
 *   LD_PRELOAD into the app processes of the container. It provides a
 *   subset of the Linux ashmem ioctl interface on memfd-backed file
 *   descriptors and emulates the behavior where possible. It is not a
 *   complete replacement for the kernel ashmem driver.
 *
 *   Scope: only apps that open /dev/ashmem themselves and talk to it with
 *   the ashmem ioctls. System libraries use the libcutils memfd replacement
 *   (aosp/patches/ashmem-dev.c), which does not need this library.
 *
 * Rules
 *  1. open of exactly "/dev/ashmem" (any of the open calls, including the
 *     fortified bionic entry points __open_2 and __openat_2 that a two
 *     argument open() in code built with _FORTIFY_SOURCE compiles to)
 *     returns a new memfd named "kindle-ashmem-dev". Every other path goes
 *     to the kernel.
 *  2. ioctl requests whose type byte is 0x77 on an fd that carries that
 *     memfd name are answered here. Every other ioctl goes to the kernel.
 *  3. The size lives in the memfd itself, so dup()ed, inherited and passed
 *     fds keep it. The name and the protection mask do not fit in a memfd;
 *     they are kept in a small per-process table keyed by the inode of the
 *     memfd. dup() and fork() see them, an fd received from another process
 *     falls back to the default name and to the full mask.
 *  4. What a memfd cannot do is left out, not reported as an error: purging
 *     never happens (pin always says "not purged"), and the protection mask
 *     is accepted and read back but not enforced. This is the same policy
 *     as ashmem_set_prot_region in aosp/patches/ashmem-dev.c.
 *  5. Pointer arguments are copied with process_vm_readv/process_vm_writev
 *     on the own process, so a bad pointer gives EFAULT like it does in the
 *     kernel instead of a SIGSEGV inside this library. Where those calls
 *     are not available the pointer is used directly.
 *
 * Differences from the kernel driver (by design)
 *  - No purging: PIN says "not purged", GET_PIN_STATUS says "pinned",
 *    PURGE_ALL_CACHES returns 0 (the driver needs CAP_SYS_ADMIN).
 *  - SET_NAME and SET_SIZE are not refused after the first mmap, because a
 *    memfd cannot tell. The size can be set once (repeating the same value
 *    is accepted) and is limited to INT_MAX. PIN and UNPIN on a region that
 *    has not been mapped yet are not refused with EINVAL.
 *  - SET_PROT_MASK never fails and never changes what mmap allows: the
 *    value is only stored for GET_PROT_MASK. The driver refuses to add
 *    bits back and enforces the mask. The READ_IMPLIES_EXEC personality is
 *    not handled.
 *  - fstat() shows a regular file, and /proc/<pid>/maps shows the memfd name
 *    instead of dev/ashmem/<name>.
 *  - The name and exec bit are process-local (rule 3); the table holds 64
 *    regions and reuses its oldest slot when full.
 *
 * Provenance and references
 *   This revision replaces the earlier Embrith implementation of this
 *   library and has a different structure. It was written for this project
 *   with reference to the public sources below; this is a record of what
 *   was consulted, not a claim about other implementations.
 *
 *   - Interface: Linux UAPI header linux/ashmem.h
 *     (drivers/staging/android/uapi/ashmem.h, SPDX GPL-2.0 OR Apache-2.0):
 *     ioctl numbers, struct ashmem_pin, ASHMEM_NAME_LEN, ASHMEM_NAME_DEF,
 *     pin return values.
 *
 *   - Behavioral reference: Linux drivers/staging/android/ashmem.c
 *     (v5.4, GPL-2.0), consulted as a description of what the ioctls do:
 *     names are truncated to ASHMEM_NAME_LEN - 1 and default to
 *     ASHMEM_NAME_DEF, GET_NAME writes the name and its terminator only,
 *     the protection mask can only lose bits (EINVAL otherwise), pin ranges
 *     must be page aligned and inside the region with len 0 meaning "to
 *     the end", unknown commands give ENOTTY. No code from that file is
 *     used here.
 *
 *   - Kernel mechanisms: the man pages of memfd_create(2) and
 *     process_vm_readv(2) and process_vm_writev(2); bionic's fortified
 *     open entry points
 *     __open_2 and __openat_2 (the WeRead library libmoai-sqlite.so
 *     imports __open_2).
 *
 *   - Related implementation: RetyOS sources/ashmem/ashmem_memfd.c
 *     (SPDX Apache-2.0, a libcutils ashmem subset on sealed memfds),
 *     consulted for: recognizing a region by reading its memfd name from
 *     /proc/self/fd/N and checking pin ranges against the page aligned
 *     size. This file has its own
 *     structure: it hooks the /dev/ashmem ioctl interface, while that one
 *     implements the libcutils API.
 *
 * Licensing
 *   Referenced projects and third-party materials remain subject to their
 *   respective licenses and copyright notices. See the corresponding
 *   upstream sources for applicable terms.
 */
#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <pthread.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/types.h>
#include <sys/uio.h>
#include <unistd.h>
#include <linux/ashmem.h>

#ifndef __O_TMPFILE
#define __O_TMPFILE 020000000
#endif

#define DEV_PATH "/dev/ashmem"
#define REGION_NAME "kindle-ashmem-dev"

#define PROT_ALL (PROT_READ | PROT_WRITE | PROT_EXEC)
#define MAX_STATES 64

/* Syscall numbers for the user pointer copies. Without them the pointer is
 * used directly (and only checked against NULL). */
#if defined(SYS_process_vm_readv) && defined(SYS_process_vm_writev)
#  define NR_VM_READ  SYS_process_vm_readv
#  define NR_VM_WRITE SYS_process_vm_writev
#elif defined(__arm__)
#  define NR_VM_READ  376
#  define NR_VM_WRITE 377
#elif defined(__aarch64__)
#  define NR_VM_READ  270
#  define NR_VM_WRITE 271
#endif

typedef int (*op_fn)(int fd, unsigned long arg);

struct region_state {
    int used;
    dev_t dev;
    ino_t ino;
    unsigned int prot;
    char name[ASHMEM_NAME_LEN];
};

static struct region_state states[MAX_STATES];
static unsigned int next_victim;
static pthread_mutex_t states_lock = PTHREAD_MUTEX_INITIALIZER;

/* ---- helpers ---- */

static int fail(int err)
{
    errno = err;
    return -1;
}

static int is_region(int fd)
{
    char link[40], target[96];
    snprintf(link, sizeof(link), "/proc/self/fd/%d", fd);
    ssize_t n = readlink(link, target, sizeof(target) - 1);
    if (n < 0) return 0;
    target[n] = '\0';
    return strcmp(target, "/memfd:" REGION_NAME) == 0 ||
           strcmp(target, "/memfd:" REGION_NAME " (deleted)") == 0;
}

static off_t region_size(int fd)
{
    struct stat st;
    return fstat(fd, &st) ? (off_t)-1 : st.st_size;
}

/* Copy len bytes between a local buffer and a user pointer. Returns the
 * number of bytes copied (a read may stop early at the end of a mapping),
 * or -1 with errno set. */
static ssize_t user_copy(int to_user, void *local, void *user, size_t len)
{
    if (!user) return fail(EFAULT);
#ifdef NR_VM_READ
    struct iovec l = { local, len };
    struct iovec r = { user, len };
    ssize_t n = (ssize_t)syscall(to_user ? NR_VM_WRITE : NR_VM_READ,
                                 (long)getpid(), &l, 1UL, &r, 1UL, 0UL);
    if (n >= 0) return n;
    if (errno != ENOSYS && errno != EPERM) return -1;
#endif
    if (to_user) memcpy(user, local, len);
    else memcpy(local, user, len);
    return (ssize_t)len;
}

static int copy_exact(int to_user, void *local, void *user, size_t len)
{
    ssize_t n = user_copy(to_user, local, user, len);
    if (n < 0) return -1;
    return (size_t)n == len ? 0 : fail(EFAULT);
}

/* ---- per-process region table (name, protection mask) ---- */

static int region_key(int fd, dev_t *dev, ino_t *ino)
{
    struct stat st;
    if (fstat(fd, &st)) return -1;
    *dev = st.st_dev;
    *ino = st.st_ino;
    return 0;
}

/* The caller holds states_lock. When the table is full the oldest slot is
 * reused; its region then falls back to the defaults. */
static struct region_state *state_get(dev_t dev, ino_t ino, int create)
{
    struct region_state *free_slot = NULL;
    for (int i = 0; i < MAX_STATES; i++) {
        if (states[i].used) {
            if (states[i].dev == dev && states[i].ino == ino) return &states[i];
        } else if (!free_slot) {
            free_slot = &states[i];
        }
    }
    if (!create) return NULL;
    if (!free_slot) free_slot = &states[next_victim++ % MAX_STATES];
    memset(free_slot, 0, sizeof(*free_slot));
    free_slot->used = 1;
    free_slot->dev = dev;
    free_slot->ino = ino;
    free_slot->prot = PROT_ALL;
    return free_slot;
}

/* A new memfd must not inherit what an earlier region with the same inode
 * number left behind. */
static void state_reset(int fd)
{
    dev_t dev;
    ino_t ino;
    if (region_key(fd, &dev, &ino)) return;
    pthread_mutex_lock(&states_lock);
    struct region_state *st = state_get(dev, ino, 0);
    if (st) st->used = 0;
    pthread_mutex_unlock(&states_lock);
}

/* ---- the ioctl operations ---- */

/* arg is the user pointer of a char[ASHMEM_NAME_LEN] buffer. At most
 * ASHMEM_NAME_LEN - 1 characters are kept. */
static int op_set_name(int fd, unsigned long arg)
{
    char buf[ASHMEM_NAME_LEN];
    dev_t dev;
    ino_t ino;

    memset(buf, 0, sizeof(buf));
    ssize_t n = user_copy(0, buf, (void *)arg, ASHMEM_NAME_LEN - 1);
    if (n < 0) return -1;
    /* A short read that stopped before the terminator is a bad pointer. */
    if (n == 0 || (!memchr(buf, '\0', (size_t)n) && n < ASHMEM_NAME_LEN - 1))
        return fail(EFAULT);
    buf[n] = '\0';

    if (region_key(fd, &dev, &ino)) return -1;
    pthread_mutex_lock(&states_lock);
    struct region_state *st = state_get(dev, ino, 1);
    memcpy(st->name, buf, sizeof(st->name));
    pthread_mutex_unlock(&states_lock);
    return 0;
}

/* Writes the name and its terminator only, like the driver does. */
static int op_get_name(int fd, unsigned long arg)
{
    char buf[ASHMEM_NAME_LEN];
    dev_t dev;
    ino_t ino;

    if (!arg) return fail(EFAULT);
    if (region_key(fd, &dev, &ino)) return -1;

    buf[0] = '\0';
    pthread_mutex_lock(&states_lock);
    struct region_state *st = state_get(dev, ino, 0);
    if (st) memcpy(buf, st->name, sizeof(buf));
    pthread_mutex_unlock(&states_lock);

    if (!buf[0]) {
        memcpy(buf, ASHMEM_NAME_DEF, sizeof(ASHMEM_NAME_DEF));
    }
    buf[ASHMEM_NAME_LEN - 1] = '\0';
    return copy_exact(1, buf, (void *)arg, strlen(buf) + 1);
}

/* arg is the size by value. The driver lets the size change until the first
 * mmap; a memfd cannot tell, so the size is set once and the same value may
 * be set again. */
static int op_set_size(int fd, unsigned long arg)
{
    off_t cur = region_size(fd);
    if (cur < 0) return -1;
    if (arg > INT_MAX) return fail(EINVAL);
    if (cur == (off_t)arg) return 0;
    if (cur != 0) return fail(EINVAL);
    return ftruncate(fd, (off_t)arg);
}

static int op_get_size(int fd, unsigned long arg)
{
    off_t cur = region_size(fd);
    return cur < 0 ? -1 : (int)cur;
}

/* arg is the new mask by value. It is never refused and never enforced, like
 * ashmem_set_prot_region in aosp/patches/ashmem-dev.c; the value is only kept
 * so that GET_PROT_MASK reads it back. */
static int op_set_prot(int fd, unsigned long arg)
{
    dev_t dev;
    ino_t ino;
    if (region_key(fd, &dev, &ino)) return -1;
    pthread_mutex_lock(&states_lock);
    state_get(dev, ino, 1)->prot = (unsigned int)(arg & PROT_ALL);
    pthread_mutex_unlock(&states_lock);
    return 0;
}

static int op_get_prot(int fd, unsigned long arg)
{
    dev_t dev;
    ino_t ino;
    unsigned int prot = PROT_ALL;
    if (region_key(fd, &dev, &ino)) return -1;
    pthread_mutex_lock(&states_lock);
    struct region_state *st = state_get(dev, ino, 0);
    if (st) prot = st->prot;
    pthread_mutex_unlock(&states_lock);
    return (int)prot;
}

/* arg is the user pointer of a struct ashmem_pin. */
static int op_pin_range(int fd, unsigned long arg)
{
    struct ashmem_pin r;
    if (copy_exact(0, &r, (void *)arg, sizeof(r))) return -1;

    off_t size = region_size(fd);
    if (size < 0) return -1;
    unsigned long page = (unsigned long)sysconf(_SC_PAGESIZE);
    unsigned long end = ((unsigned long)size + page - 1) / page * page;
    if (r.offset % page || r.len % page) return fail(EINVAL);
    if (r.offset > end || r.len > end - r.offset) return fail(EINVAL);
    return ASHMEM_NOT_PURGED;
}

static int op_pin_status(int fd, unsigned long arg)
{
    return ASHMEM_IS_PINNED;
}

static int op_purge_all(int fd, unsigned long arg)
{
    return 0;
}

static const struct {
    unsigned int cmd;
    op_fn run;
} ops[] = {
    { ASHMEM_SET_NAME,        op_set_name },
    { ASHMEM_GET_NAME,        op_get_name },
    { ASHMEM_SET_SIZE,        op_set_size },
    { ASHMEM_GET_SIZE,        op_get_size },
    { ASHMEM_SET_PROT_MASK,   op_set_prot },
    { ASHMEM_GET_PROT_MASK,   op_get_prot },
    { ASHMEM_PIN,             op_pin_range },
    { ASHMEM_UNPIN,           op_pin_range },
    { ASHMEM_GET_PIN_STATUS,  op_pin_status },
    { ASHMEM_PURGE_ALL_CACHES, op_purge_all },
};

/* ---- the calls that are intercepted ---- */

static int route_ioctl(int fd, unsigned int cmd, unsigned long arg)
{
    if (_IOC_TYPE(cmd) == __ASHMEMIOC && is_region(fd)) {
        for (size_t i = 0; i < sizeof(ops) / sizeof(ops[0]); i++)
            if (ops[i].cmd == cmd) return ops[i].run(fd, arg);
        return fail(ENOTTY);
    }
    return (int)syscall(SYS_ioctl, fd, (unsigned long)cmd, arg);
}

/* The third argument is a pointer or a plain number depending on cmd, and
 * some commands have none. It is always fetched as one machine word; that is
 * what the calling convention passes, and the commands without an argument
 * ignore it. */
int ioctl(int fd, int cmd, ...)
{
    va_list ap;
    va_start(ap, cmd);
    unsigned long arg = va_arg(ap, unsigned long);
    va_end(ap);
    return route_ioctl(fd, (unsigned int)cmd, arg);
}

static int route_open(int dirfd, const char *path, int flags, unsigned int mode)
{
    if (path && !strcmp(path, DEV_PATH)) {
        /* memfd_create flags: 1 close-on-exec. */
        unsigned int mf = (flags & O_CLOEXEC) ? 1u : 0u;
        int fd;
#ifdef SYS_memfd_create
        fd = (int)syscall(SYS_memfd_create, REGION_NAME, mf);
#else
        fd = (int)syscall(385, REGION_NAME, mf); /* ARM EABI */
#endif
        if (fd >= 0) state_reset(fd);
        return fd;
    }
    return (int)syscall(SYS_openat, dirfd, path, flags, mode);
}

/* The optional mode argument exists when the kernel would look at it. */
#define MODE_FROM_ARGS(flags, last)                                  \
    unsigned int mode = 0;                                           \
    do {                                                             \
        va_list ap_;                                                 \
        va_start(ap_, last);                                         \
        if ((flags) & (O_CREAT | __O_TMPFILE))                       \
            mode = va_arg(ap_, unsigned int);                        \
        va_end(ap_);                                                 \
    } while (0)

int open(const char *path, int flags, ...)
{
    MODE_FROM_ARGS(flags, flags);
    return route_open(AT_FDCWD, path, flags, mode);
}

int open64(const char *path, int flags, ...)
{
    MODE_FROM_ARGS(flags, flags);
    return route_open(AT_FDCWD, path, flags | O_LARGEFILE, mode);
}

int openat(int dirfd, const char *path, int flags, ...)
{
    MODE_FROM_ARGS(flags, flags);
    return route_open(dirfd, path, flags, mode);
}

int openat64(int dirfd, const char *path, int flags, ...)
{
    MODE_FROM_ARGS(flags, flags);
    return route_open(dirfd, path, flags | O_LARGEFILE, mode);
}

/* bionic fcntl.h: open(path, flags) and openat(fd, path, flags) without a
 * mode argument call these two exported functions in fortified code. */
int __open_2(const char *path, int flags)
{
    return route_open(AT_FDCWD, path, flags, 0);
}

int __openat_2(int dirfd, const char *path, int flags)
{
    return route_open(dirfd, path, flags, 0);
}