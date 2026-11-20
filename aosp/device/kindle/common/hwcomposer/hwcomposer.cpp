/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors */
/*
 * HWC 1.1 for the Kindle PW6 Android container.
 * Every layer is composed by SurfaceFlinger (GLES, software). This module only takes the
 * framebuffer-target buffer, converts it to 8-bit gray and publishes the changed rectangle
 * into the shared file read by kdispd on the Kindle side. It also generates VSYNC.
 *
 * Properties (all optional):
 *   kindle.hwc.sink    shared file path        (default /dev/kdisp/frame)
 *   kindle.hwc.fps     vsync rate, 1..60       (default 30)
 *   kindle.hwc.swaprb  1 if the target is BGRA (default 0)
 */
#define LOG_TAG "hwc_kindle"
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <pthread.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>

#include <log/log.h>
#include <cutils/properties.h>
#include <hardware/hardware.h>
#include <hardware/hwcomposer.h>
#include <hardware/gralloc.h>
#include "gralloc_priv.h"
#include "frame_sink.h"

struct kindle_hwc {
    hwc_composer_device_1_t base;
    const hwc_procs_t* procs;
    pthread_t thread;
    pthread_mutex_t lock;
    pthread_cond_t cond;
    bool running;
    bool vsyncEnabled;
    int64_t periodNs;
    const gralloc_module_t* gralloc;
    FrameSink sink;
    int64_t lastOpenTryNs;
    char sinkPath[PROPERTY_VALUE_MAX];
    unsigned frames;
    bool loggedStride;
    uint32_t panelW, panelH;   /* from the property kindle.hwc.panel, set in hwc_open */
    int32_t dpiMilli;          /* from ro.sf.lcd_density, in 1/1000 dpi */
};

static int64_t now_ns() {
    struct timespec t;
    clock_gettime(CLOCK_MONOTONIC, &t);
    return (int64_t)t.tv_sec * 1000000000LL + t.tv_nsec;
}

static void* vsync_loop(void* arg) {
    kindle_hwc* ctx = (kindle_hwc*)arg;
    int64_t next = now_ns();
    bool was = false;
    for (;;) {
        pthread_mutex_lock(&ctx->lock);
        while (ctx->running && !ctx->vsyncEnabled) {
            was = false;
            pthread_cond_wait(&ctx->cond, &ctx->lock);
        }
        bool run = ctx->running;
        pthread_mutex_unlock(&ctx->lock);
        if (!run) break;
        if (!was) { next = now_ns(); was = true; }
        next += ctx->periodNs;
        struct timespec ts;
        ts.tv_sec = next / 1000000000LL;
        ts.tv_nsec = next % 1000000000LL;
        clock_nanosleep(CLOCK_MONOTONIC, TIMER_ABSTIME, &ts, NULL);
        pthread_mutex_lock(&ctx->lock);
        bool en = ctx->vsyncEnabled;
        pthread_mutex_unlock(&ctx->lock);
        if (en && ctx->procs && ctx->procs->vsync) ctx->procs->vsync(ctx->procs, 0, next);
    }
    return NULL;
}

static int hwc_prepare(hwc_composer_device_1_t* dev, size_t numDisplays, hwc_display_contents_1_t** displays) {
    if (!displays || numDisplays == 0 || !displays[0]) return 0;
    hwc_display_contents_1_t* list = displays[0];
    for (size_t i = 0; i < list->numHwLayers; i++) {
        hwc_layer_1_t* l = &list->hwLayers[i];
        if (l->compositionType == HWC_FRAMEBUFFER_TARGET) continue;
        l->compositionType = HWC_FRAMEBUFFER;
        l->hints = 0;
    }
    return 0;
}

static void wait_and_close_fence(int* fd, bool wait) {
    if (*fd < 0) return;
    if (wait) {
        struct pollfd p;
        p.fd = *fd; p.events = POLLIN; p.revents = 0;
        poll(&p, 1, 1000);
    }
    close(*fd);
    *fd = -1;
}

static uint32_t derive_stride(const private_handle_t* h, uint32_t w, uint32_t height) {
    if (h->size <= 4) return w;
    uint32_t pixels = (uint32_t)(h->size - 4) / 4;
    static const uint32_t aligns[] = {1, 2, 4, 8, 16, 32, 64, 128};
    for (size_t i = 0; i < sizeof(aligns) / sizeof(aligns[0]); i++) {
        uint32_t a = aligns[i];
        uint32_t hh = (height + a - 1) / a * a;
        if (hh && pixels % hh == 0) {
            uint32_t s = pixels / hh;
            if (s >= w && s - w < 256) return s;
        }
    }
    return w;
}

static int hwc_set(hwc_composer_device_1_t* dev, size_t numDisplays, hwc_display_contents_1_t** displays) {
    kindle_hwc* ctx = (kindle_hwc*)dev;
    if (!displays || numDisplays == 0 || !displays[0]) return 0;
    hwc_display_contents_1_t* list = displays[0];

    hwc_layer_1_t* target = NULL;
    for (size_t i = 0; i < list->numHwLayers; i++) {
        hwc_layer_1_t* l = &list->hwLayers[i];
        bool isTarget = (l->compositionType == HWC_FRAMEBUFFER_TARGET);
        wait_and_close_fence(&l->acquireFenceFd, isTarget);
        l->releaseFenceFd = -1;
        if (isTarget) target = l;
    }
    list->retireFenceFd = -1;
    if (!target || !target->handle || !ctx->gralloc) return 0;

    if (!ctx->sink.isOpen()) {                    /* the host daemon may start later: retry once a second */
        int64_t t = now_ns();
        if (t - ctx->lastOpenTryNs < 1000000000LL) return 0;
        ctx->lastOpenTryNs = t;
        if (!ctx->sink.open(ctx->sinkPath)) return 0;
        ALOGI("sink %s opened (%ux%u)", ctx->sinkPath, ctx->sink.width(), ctx->sink.height());
    }

    const private_handle_t* ph = (const private_handle_t*)target->handle;
    uint32_t w = ctx->sink.width(), h = ctx->sink.height();
    void* vaddr = NULL;
    if (ctx->gralloc->lock(ctx->gralloc, target->handle, GRALLOC_USAGE_SW_READ_OFTEN, 0, 0, (int)w, (int)h, &vaddr) != 0 || !vaddr) {
        ALOGE("gralloc lock failed");
        return 0;
    }
    uint32_t stridePx = derive_stride(ph, w, h);
    if (!ctx->loggedStride) {
        ctx->loggedStride = true;
        ALOGI("target buffer: size=%d stride=%u px, panel %ux%u", ph->size, stridePx, w, h);
    }
    if (ctx->sink.publish((const uint8_t*)vaddr, stridePx * 4, w, h)) ctx->frames++;
    ctx->gralloc->unlock(ctx->gralloc, target->handle);
    return 0;
}

static int hwc_eventControl(hwc_composer_device_1_t* dev, int disp, int event, int enabled) {
    kindle_hwc* ctx = (kindle_hwc*)dev;
    if (disp != HWC_DISPLAY_PRIMARY) return -EINVAL;
    if (event == HWC_EVENT_VSYNC) {
        pthread_mutex_lock(&ctx->lock);
        ctx->vsyncEnabled = enabled != 0;
        pthread_cond_signal(&ctx->cond);
        pthread_mutex_unlock(&ctx->lock);
        return 0;
    }
    return -EINVAL;
}

static int hwc_blank(hwc_composer_device_1_t* dev, int disp, int blank) {
    return disp == HWC_DISPLAY_PRIMARY ? 0 : -EINVAL;
}

static int hwc_query(hwc_composer_device_1_t* dev, int what, int* value) {
    kindle_hwc* ctx = (kindle_hwc*)dev;
    switch (what) {
        case HWC_BACKGROUND_LAYER_SUPPORTED: *value = 0; return 0;
        case HWC_VSYNC_PERIOD: *value = (int)ctx->periodNs; return 0;
        case HWC_DISPLAY_TYPES_SUPPORTED: *value = HWC_DISPLAY_PRIMARY_BIT; return 0;
    }
    return -EINVAL;
}

static void hwc_registerProcs(hwc_composer_device_1_t* dev, hwc_procs_t const* procs) {
    ((kindle_hwc*)dev)->procs = procs;
}

static void hwc_dump(hwc_composer_device_1_t* dev, char* buff, int buff_len) {
    kindle_hwc* ctx = (kindle_hwc*)dev;
    snprintf(buff, buff_len, "kindle hwc: frames=%u sink=%s open=%d\n", ctx->frames, ctx->sinkPath, (int)ctx->sink.isOpen());
}

static int hwc_getDisplayConfigs(hwc_composer_device_1_t* dev, int disp, uint32_t* configs, size_t* numConfigs) {
    if (disp != HWC_DISPLAY_PRIMARY) return -EINVAL;
    if (*numConfigs > 0) { configs[0] = 0; *numConfigs = 1; }
    return 0;
}

static int hwc_getDisplayAttributes(hwc_composer_device_1_t* dev, int disp, uint32_t config,
                                    const uint32_t* attributes, int32_t* values) {
    kindle_hwc* ctx = (kindle_hwc*)dev;
    if (disp != HWC_DISPLAY_PRIMARY || config != 0) return -EINVAL;
    for (int i = 0; attributes[i] != HWC_DISPLAY_NO_ATTRIBUTE; i++) {
        switch (attributes[i]) {
            case HWC_DISPLAY_VSYNC_PERIOD: values[i] = (int32_t)ctx->periodNs; break;
            case HWC_DISPLAY_WIDTH: values[i] = (int32_t)ctx->panelW; break;
            case HWC_DISPLAY_HEIGHT: values[i] = (int32_t)ctx->panelH; break;
            case HWC_DISPLAY_DPI_X:
            case HWC_DISPLAY_DPI_Y: values[i] = ctx->dpiMilli; break;
            default: values[i] = 0; break;
        }
    }
    return 0;
}

static int hwc_close(hw_device_t* dev) {
    kindle_hwc* ctx = (kindle_hwc*)dev;
    pthread_mutex_lock(&ctx->lock);
    ctx->running = false;
    pthread_cond_signal(&ctx->cond);
    pthread_mutex_unlock(&ctx->lock);
    pthread_join(ctx->thread, NULL);
    delete ctx;
    return 0;
}

static int hwc_open(const hw_module_t* module, const char* name, hw_device_t** device) {
    if (strcmp(name, HWC_HARDWARE_COMPOSER) != 0) return -EINVAL;

    kindle_hwc* ctx = new kindle_hwc();
    memset(&ctx->base, 0, sizeof(ctx->base));
    ctx->procs = NULL;
    ctx->running = true;
    ctx->vsyncEnabled = false;
    ctx->lastOpenTryNs = 0;
    ctx->frames = 0;
    ctx->loggedStride = false;
    pthread_mutex_init(&ctx->lock, NULL);
    pthread_cond_init(&ctx->cond, NULL);

    char v[PROPERTY_VALUE_MAX];
    property_get("kindle.hwc.sink", ctx->sinkPath, "/dev/kdisp/frame");
    property_get("kindle.hwc.fps", v, "30");
    int fps = atoi(v);
    if (fps < 1) fps = 1;
    if (fps > 60) fps = 60;
    ctx->periodNs = 1000000000LL / fps;
    property_get("kindle.hwc.swaprb", v, "0");
    ctx->sink.setSwapRB(atoi(v) != 0);

    /* panel size and pixel density come from the hardware, through properties the host launcher writes into default.prop */
    unsigned pw = 0, ph = 0;
    property_get("kindle.hwc.panel", v, "");
    if (sscanf(v, "%ux%u", &pw, &ph) != 2 || pw == 0 || ph == 0) {
        ALOGE("property kindle.hwc.panel is not set, the panel size is unknown");
        delete ctx;
        return -ENODEV;
    }
    ctx->panelW = pw;
    ctx->panelH = ph;
    property_get("ro.sf.lcd_density", v, "0");
    ctx->dpiMilli = atoi(v) * 1000;

    if (hw_get_module(GRALLOC_HARDWARE_MODULE_ID, (const hw_module_t**)&ctx->gralloc) != 0) {
        ALOGE("gralloc module not found");
        ctx->gralloc = NULL;
    }

    ctx->base.common.tag = HARDWARE_DEVICE_TAG;
    ctx->base.common.version = HWC_DEVICE_API_VERSION_1_1;
    ctx->base.common.module = const_cast<hw_module_t*>(module);
    ctx->base.common.close = hwc_close;
    ctx->base.prepare = hwc_prepare;
    ctx->base.set = hwc_set;
    ctx->base.eventControl = hwc_eventControl;
    ctx->base.blank = hwc_blank;
    ctx->base.query = hwc_query;
    ctx->base.registerProcs = hwc_registerProcs;
    ctx->base.dump = hwc_dump;
    ctx->base.getDisplayConfigs = hwc_getDisplayConfigs;
    ctx->base.getDisplayAttributes = hwc_getDisplayAttributes;

    pthread_create(&ctx->thread, NULL, vsync_loop, ctx);
    *device = &ctx->base.common;
    ALOGI("kindle hwc up: %ux%u, %d fps vsync, sink %s", ctx->panelW, ctx->panelH, fps, ctx->sinkPath);
    return 0;
}

static struct hw_module_methods_t hwc_module_methods = { hwc_open };

hwc_module_t HAL_MODULE_INFO_SYM = {
    common: {
        tag: HARDWARE_MODULE_TAG,
        version_major: 1,
        version_minor: 0,
        id: HWC_HARDWARE_MODULE_ID,
        name: "Kindle PW6 HWC",
        author: "local",
        methods: &hwc_module_methods,
        dso: 0,
        reserved: {0},
    }
};
