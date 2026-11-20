/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Source and changes
 *
 * This file is derived from system/core/libcutils/ashmem-dev.c of AOSP
 * (android-7.1.2_r36, Apache-2.0) and replaces it in the Embrith build
 * (aosp/05-apply.sh copies it over the original). It is modified by the
 * Embrith contributors, 2026.
 *
 * Kept from the original: the license header, the public API of
 * <cutils/ashmem.h> (function names and signatures), the order of the
 * functions, the description of ashmem_create_region, and the
 * TEMP_FAILURE_RETRY and errno-saving idioms.
 *
 * Changed: the Kindle PW6 kernel has no /dev/ashmem and no ION, but it has
 * memfd_create(). The device access of the original (__ashmem_open,
 * __ashmem_is_ashmem, the ioctl calls and the lock around them) is removed
 * and every region is an anonymous memfd instead.
 *
 * Differences from real ashmem:
 *  - the size is limited to INT32_MAX
 *  - set_prot_region is accepted but not enforced
 *  - pin and unpin are no-ops that always report "not purged" and "unpinned"
 *    (memory is never reclaimed)
 *
 * Applications that open /dev/ashmem themselves are handled by
 * aosp/device/kindle/common/kindleashmem/kindleashmem.c.
 */
#define LOG_TAG "ashmem"

#include <errno.h>
#include <fcntl.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/types.h>
#include <unistd.h>

#include <cutils/ashmem.h>
#include <log/log.h>

#ifndef __NR_memfd_create
#  if defined(__arm__)
#    define __NR_memfd_create 385
#  elif defined(__aarch64__)
#    define __NR_memfd_create 279
#  elif defined(__i386__)
#    define __NR_memfd_create 356
#  elif defined(__x86_64__)
#    define __NR_memfd_create 319
#  endif
#endif

/*
 * ashmem_create_region - creates a new ashmem region and returns the file
 * descriptor, or <0 on error
 *
 * `name' is an optional label to give the region (visible in /proc/pid/maps
 * as /memfd:ashmem/<name>)
 * `size' is the size of the region, in page-aligned bytes
 */
int ashmem_create_region(const char *name, size_t size)
{
    char buf[128];
    snprintf(buf, sizeof(buf), "ashmem/%s", (name && *name) ? name : "unnamed");

    int fd = (int) syscall(__NR_memfd_create, buf, 0u);
    if (fd < 0) {
        ALOGE("memfd_create(%s) failed: %s", buf, strerror(errno));
        return -1;
    }

    if (size > (size_t) INT32_MAX) {
        close(fd);
        errno = EINVAL;
        return -1;
    }

    if (TEMP_FAILURE_RETRY(ftruncate(fd, (off_t) size)) < 0) {
        int save_errno = errno;
        close(fd);
        errno = save_errno;
        return -1;
    }
    return fd;
}

int ashmem_set_prot_region(int fd __attribute__((unused)), int prot __attribute__((unused)))
{
    return 0;
}

int ashmem_pin_region(int fd __attribute__((unused)), size_t offset __attribute__((unused)),
                      size_t len __attribute__((unused)))
{
    return ASHMEM_NOT_PURGED;
}

int ashmem_unpin_region(int fd __attribute__((unused)), size_t offset __attribute__((unused)),
                        size_t len __attribute__((unused)))
{
    return ASHMEM_IS_UNPINNED;
}

int ashmem_get_size_region(int fd)
{
    struct stat st;
    if (TEMP_FAILURE_RETRY(fstat(fd, &st)) < 0) {
        return -1;
    }
    return (int) st.st_size;
}
