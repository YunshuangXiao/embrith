/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors *//* panel.h - the panel size, read from the hardware (/dev/fb0, FBIOGET_VSCREENINFO) at start-up.
 * No built-in default and no override. Returns 0 and fills w/h, or -1 when it cannot be read. */
#ifndef KD_PANEL_DETECT_H
#define KD_PANEL_DETECT_H
#include <fcntl.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <linux/fb.h>

static int panel_detect(unsigned* w, unsigned* h) {
    struct fb_var_screeninfo v;
    int fd = open("/dev/fb0", O_RDONLY);
    if (fd < 0) return -1;
    int r = ioctl(fd, FBIOGET_VSCREENINFO, &v);
    close(fd);
    if (r < 0 || v.xres == 0 || v.yres == 0) return -1;
    *w = v.xres; *h = v.yres;
    return 0;
}
#endif/* panel.h - the panel size, read from the hardware (/dev/fb0, FBIOGET_VSCREENINFO) at start-up.
 * No built-in default and no override. Returns 0 and fills w/h, or -1 when it cannot be read. */
#ifndef KD_PANEL_DETECT_H
#define KD_PANEL_DETECT_H
#include <fcntl.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <linux/fb.h>

static int panel_detect(unsigned* w, unsigned* h) {
    struct fb_var_screeninfo v;
    int fd = open("/dev/fb0", O_RDONLY);
    if (fd < 0) return -1;
    int r = ioctl(fd, FBIOGET_VSCREENINFO, &v);
    close(fd);
    if (r < 0 || v.xres == 0 || v.yres == 0) return -1;
    *w = v.xres; *h = v.yres;
    return 0;
}
#endif/* panel.h - the panel size, read from the hardware (/dev/fb0, FBIOGET_VSCREENINFO) at start-up.
 * No built-in default and no override. Returns 0 and fills w/h, or -1 when it cannot be read. */
#ifndef KD_PANEL_DETECT_H
#define KD_PANEL_DETECT_H
#include <fcntl.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <linux/fb.h>

static int panel_detect(unsigned* w, unsigned* h) {
    struct fb_var_screeninfo v;
    int fd = open("/dev/fb0", O_RDONLY);
    if (fd < 0) return -1;
    int r = ioctl(fd, FBIOGET_VSCREENINFO, &v);
    close(fd);
    if (r < 0 || v.xres == 0 || v.yres == 0) return -1;
    *w = v.xres; *h = v.yres;
    return 0;
}
#endif