/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors */
/* Shared frame buffer between the Android container (HWC) and the Kindle-side daemon (kdispd).
 * One file on tmpfs: [ header (4096 bytes) | width*height 8-bit gray pixels, row stride = hdr.stride ].
 * If KDISP_FLAG_RGBA_PLANE is set, append width*height*4 RGBA8888 bytes after the tightly packed gray plane.
 * Single writer (HWC / kfake), single reader (kdispd). Seqlock: seq is odd while the writer is busy.
 * The reader sets ack = seq after copying; the writer uses it to know whether to merge dirty rects. */
#ifndef KDISP_PROTO_H
#define KDISP_PROTO_H
#include <stdint.h>

#define KDISP_MAGIC     0x5053444bu   /* "KDSP" */
#define KDISP_VERSION   1u
#define KDISP_HDR_SIZE  4096u
#define KDISP_FLAG_RGBA_PLANE 1u /* trailing width*height RGBA8888 plane; seq protects both planes */

struct kdisp_hdr {
    uint32_t magic;
    uint32_t version;
    uint32_t width;
    uint32_t height;
    uint32_t stride;        /* bytes per row of the pixel area */
    uint32_t data_offset;   /* KDISP_HDR_SIZE */
    uint32_t seq;           /* writer -> reader */
    uint32_t ack;           /* reader -> writer */
    uint32_t dirty_x, dirty_y, dirty_w, dirty_h;
    uint32_t frame_counter;
    uint32_t flags;
};
#endif
