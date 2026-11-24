/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors */
/* kutil: tiny helper for the Kindle (no mkswap/swapon applets there).
 *   kutil swap  /dev/zram0 <MB> [prio]   size the zram device, write a swap header, swapon
 *   kutil swapoff /dev/zram0              swapoff and reset the zram device
 *   kutil mkswap <file> <MB>              write a swap header into a regular file (for testing)
 *   kutil umount-lazy <path>              umount2(path, MNT_DETACH)
 *   kutil run-as <uid> <gid> <cmd> [args]  drop privileges, then exec (tests what a service start does)
 *   kutil tapd <event dev> [idle_s]       grab the touch screen, print "T x y" per finger lift and "I" every idle_s seconds (default 30)
 *   kutil panel                           print the panel size "W H" (read from the framebuffer)
 *   kutil binder-ver <dev>                print the Binder protocol version reported by <dev>
 */
#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <fcntl.h>
#include <unistd.h>
#include <stdint.h>
#include <sys/swap.h>
#include <sys/stat.h>
#include <sys/mount.h>
#include <grp.h>
#include <poll.h>
#include <signal.h>
#include <sys/ioctl.h>
#include <sys/prctl.h>
#include <linux/input.h>
#include "panel.h"

#define BINDER_VERSION _IOWR('b', 9, int32_t)

static int write_str(const char* path, const char* s) {
    int fd = open(path, O_WRONLY);
    if (fd < 0) return -1;
    ssize_t n = write(fd, s, strlen(s));
    close(fd);
    return n < 0 ? -1 : 0;
}

static int mkswap_fd(int fd, uint64_t bytes) {
    const uint32_t page = 4096;
    uint64_t pages = bytes / page;
    if (pages < 10) { fprintf(stderr, "too small\n"); return -1; }
    uint8_t* hdr = calloc(1, page);
    if (!hdr) return -1;
    uint32_t* w = (uint32_t*)(hdr + 1024);
    w[0] = 1;                       /* version */
    w[1] = (uint32_t)(pages - 1);   /* last_page */
    w[2] = 0;                       /* nr_badpages */
    memcpy(hdr + page - 10, "SWAPSPACE2", 10);
    if (lseek(fd, 0, SEEK_SET) < 0 || write(fd, hdr, page) != (ssize_t)page) { free(hdr); return -1; }
    free(hdr);
    fsync(fd);
    return 0;
}

static const char* base(const char* p) { const char* s = strrchr(p, '/'); return s ? s + 1 : p; }


/* tapd: exclusive touch reader for the Kindle-side pages. The Kindle UI gets no touches while it runs (EVIOCGRAB), so a tap on the
 * page never reaches the library underneath. The grab ends with the process. It dies with its parent (PDEATHSIG) and when its reader
 * is gone (SIGPIPE on the next line, at the latest after idle_s seconds). Only the first finger (slot 0) counts. */
static int cmd_tapd(const char* dev, int idle) {
    int fd = open(dev, O_RDONLY);
    if (fd < 0) { perror(dev); return 1; }
    if (ioctl(fd, EVIOCGRAB, 1) != 0) fprintf(stderr, "tapd: grab failed (%s), the Kindle still sees the touches\n", strerror(errno));
    prctl(PR_SET_PDEATHSIG, SIGTERM);
    setvbuf(stdout, NULL, _IOLBF, 0);
    int x = -1, y = -1, slot = 0;
    struct input_event ev;
    for (;;) {
        struct pollfd p = { fd, POLLIN, 0 };
        int r = poll(&p, 1, idle * 1000);
        if (r < 0) { if (errno == EINTR) continue; break; }
        if (r == 0) { if (printf("I\n") < 0 || fflush(stdout) != 0) break; continue; }
        ssize_t n = read(fd, &ev, sizeof ev);
        if (n != (ssize_t)sizeof ev) { if (n < 0 && errno == EINTR) continue; break; }
        int lift = 0;
        if (ev.type == EV_ABS) {
            if (ev.code == ABS_MT_SLOT) slot = ev.value;
            else if (slot == 0 && ev.code == ABS_MT_POSITION_X) x = ev.value;
            else if (slot == 0 && ev.code == ABS_MT_POSITION_Y) y = ev.value;
            else if (slot == 0 && ev.code == ABS_MT_TRACKING_ID && ev.value == -1) lift = 1;
        } else if (ev.type == EV_KEY && ev.code == BTN_TOUCH && ev.value == 0) lift = 1;
        if (lift) {
            if (x >= 0 && y >= 0) { if (printf("T %d %d\n", x, y) < 0 || fflush(stdout) != 0) break; }
            x = y = -1;
        }
    }
    ioctl(fd, EVIOCGRAB, 0);
    return 0;
}

int main(int argc, char** argv) {
    if (argc >= 2 && !strcmp(argv[1], "panel")) {
        unsigned w, h;
        if (panel_detect(&w, &h) != 0) { fprintf(stderr, "kutil: cannot determine the panel size\n"); return 1; }
        printf("%u %u\n", w, h);
        return 0;
    }
    if (argc >= 4 && !strcmp(argv[1], "mkswap")) {
        int fd = open(argv[2], O_RDWR | O_CREAT, 0600);
        if (fd < 0) { perror("open"); return 1; }
        uint64_t bytes = (uint64_t)atoll(argv[3]) << 20;
        if (ftruncate(fd, (off_t)bytes) < 0) { perror("ftruncate"); return 1; }
        return mkswap_fd(fd, bytes) ? 1 : 0;
    }
    if (argc >= 4 && !strcmp(argv[1], "swap")) {
        const char* dev = argv[2];
        uint64_t mb = (uint64_t)atoll(argv[3]);
        int prio = argc >= 5 ? atoi(argv[4]) : 100;
        char path[128];
        snprintf(path, sizeof path, "/sys/block/%s/reset", base(dev));
        write_str(path, "1");                       /* ignore failure: device may be fresh */
        snprintf(path, sizeof path, "/sys/block/%s/disksize", base(dev));
        char sz[32]; snprintf(sz, sizeof sz, "%llu", (unsigned long long)(mb << 20));
        if (write_str(path, sz) < 0) { perror("disksize"); return 1; }
        int fd = open(dev, O_RDWR);
        if (fd < 0) { perror("open zram"); return 1; }
        if (mkswap_fd(fd, mb << 20)) { perror("mkswap"); return 1; }
        close(fd);
        if (swapon(dev, SWAP_FLAG_PREFER | ((prio << SWAP_FLAG_PRIO_SHIFT) & SWAP_FLAG_PRIO_MASK)) < 0) {
            perror("swapon"); return 1;
        }
        printf("swap on %s: %llu MB\n", dev, (unsigned long long)mb);
        return 0;
    }
    if (argc >= 3 && !strcmp(argv[1], "swapoff")) {
        if (swapoff(argv[2]) < 0) perror("swapoff");
        char path[128]; snprintf(path, sizeof path, "/sys/block/%s/reset", base(argv[2]));
        write_str(path, "1");
        return 0;
    }
    if (argc >= 5 && !strcmp(argv[1], "run-as")) {
        if (setgroups(0, NULL) < 0) perror("setgroups");
        if (setgid((gid_t)atoi(argv[3])) < 0) { perror("setgid"); return 1; }
        if (setuid((uid_t)atoi(argv[2])) < 0) { perror("setuid"); return 1; }
        execv(argv[4], argv + 4);
        perror("execv");
        return 127;
    }
    if (argc >= 3 && !strcmp(argv[1], "tapd")) {
        int idle = argc >= 4 ? atoi(argv[3]) : 30;
        return cmd_tapd(argv[2], idle > 0 ? idle : 30);
    }
    if (argc >= 3 && !strcmp(argv[1], "umount-lazy")) {
        if (umount2(argv[2], MNT_DETACH) < 0) { perror("umount2"); return 1; }
        return 0;
    }
    if (argc >= 3 && !strcmp(argv[1], "binder-ver")) {
        int fd = open(argv[2], O_RDWR | O_CLOEXEC);
        if (fd < 0) { perror(argv[2]); return 1; }
        int32_t ver = 0;
        if (ioctl(fd, BINDER_VERSION, &ver) < 0) { perror("BINDER_VERSION"); close(fd); return 1; }
        close(fd);
        printf("%d\n", (int)ver);
        return 0;
    }
    fprintf(stderr, "usage: kutil swap <dev> <MB> [prio] | swapoff <dev> | mkswap <file> <MB> | umount-lazy <path> | binder-ver <dev> | tapd <event dev> [idle_s] | panel\n");
    return 2;
}