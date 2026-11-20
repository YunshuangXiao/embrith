/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors */
// kfake: pretends to be the Android HWC. Drives the same FrameSink code with synthetic frames.
// usage: kfake [seconds] [sink-file]
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>
#include "frame_sink.h"

static void fill(uint8_t* b, uint32_t W, uint32_t H, int x, int y, int w, int h, uint8_t g) {
    for (int j = y; j < y + h && j < (int)H; j++) {
        if (j < 0) continue;
        for (int i = x; i < x + w && i < (int)W; i++) {
            if (i < 0) continue;
            uint8_t* p = b + ((size_t)j * W + i) * 4;
            p[0] = p[1] = p[2] = g; p[3] = 255;
        }
    }
}

int main(int argc, char** argv) {
    int secs = argc > 1 ? atoi(argv[1]) : 20;
    const char* path = argc > 2 ? argv[2] : "/var/tmp/kdisp/frame";
    FrameSink s;
    int tries = 0;
    while (!s.open(path)) {
        if (++tries > 50) { fprintf(stderr, "kfake: cannot open %s\n", path); return 1; }
        usleep(100000);
    }
    uint32_t W = s.width(), H = s.height();
    uint8_t* buf = (uint8_t*)malloc((size_t)W * H * 4);
    if (!buf) return 1;
    int total = secs * 10, anim = total / 2, pub = 0;
    for (int f = 0; f < total; f++) {
        if (f < anim) {
            fill(buf, W, H, 0, 0, W, H, 255);
            int bw = W / 16;
            for (int i = 0; i < 16; i++) fill(buf, W, H, i * bw, 0, bw, 300, (uint8_t)(i * 17));
            fill(buf, W, H, 0, 320, (f * 20) % W, 40, 0);
            fill(buf, W, H, (f * 37) % (W - 120), 500 + (f * 53) % 800, 120, 120, 0);
        } else if (f == anim) {   /* page flip */
            fill(buf, W, H, 0, 0, W, H, 255);
            for (uint32_t y = 100; y < 1600; y += 40) fill(buf, W, H, 60, y, W - 120, 3, 0);
            fill(buf, W, H, 60, 100, 6, 1500, 0);
        }
        if (s.publish(buf, W * 4, W, H)) pub++;
        usleep(100000);
    }
    printf("kfake done: %d frames published\n", pub);
    free(buf);
    return 0;
}
