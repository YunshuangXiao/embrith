/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors */
/* Publishes grayscale, plus optional RGBA when the host advertises it. Header-only, no Android deps. */
#ifndef FRAME_SINK_H
#define FRAME_SINK_H
#include <fcntl.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>
#include "kdisp_proto.h"

class FrameSink {
public:
    FrameSink() : fd_(-1), map_(0), len_(0), hdr_(0), prev_(0), havePrev_(false),
                  accValid_(false), lastPub_(0), swapRB_(false) {
        ax_ = ay_ = aw_ = ah_ = 0;
    }
    ~FrameSink() { close_(); }

    void setSwapRB(bool v) { swapRB_ = v; }
    bool isOpen() const { return hdr_ != 0; }
    uint32_t width() const { return hdr_ ? hdr_->width : 0; }
    uint32_t height() const { return hdr_ ? hdr_->height : 0; }

    bool open(const char* path) {
        close_();
        int fd = ::open(path, O_RDWR | O_CLOEXEC);
        if (fd < 0) return false;
        struct stat st;
        if (fstat(fd, &st) < 0 || st.st_size < (off_t)KDISP_HDR_SIZE) { ::close(fd); return false; }
        void* m = mmap(0, (size_t)st.st_size, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
        if (m == MAP_FAILED) { ::close(fd); return false; }
        kdisp_hdr* h = (kdisp_hdr*)m;
        if (h->magic != KDISP_MAGIC || h->version != KDISP_VERSION || h->width == 0 || h->height == 0 ||
            h->stride < h->width ||
            ((h->flags & KDISP_FLAG_RGBA_PLANE) && (h->stride != h->width ||
             (uint64_t)h->data_offset + (uint64_t)h->width*h->height*5 > (uint64_t)st.st_size)) || (size_t)h->data_offset + (size_t)h->stride * h->height > (size_t)st.st_size) {
            munmap(m, (size_t)st.st_size); ::close(fd); return false;
        }
        prev_ = (uint8_t*)malloc((size_t)h->width * h->height * ((h->flags & KDISP_FLAG_RGBA_PLANE) ? 4 : 1));
        if (!prev_) { munmap(m, (size_t)st.st_size); ::close(fd); return false; }
        fd_ = fd; map_ = (uint8_t*)m; len_ = (size_t)st.st_size; hdr_ = h;
        havePrev_ = false; accValid_ = false;
        lastPub_ = __atomic_load_n(&hdr_->seq, __ATOMIC_ACQUIRE);
        return true;
    }

    /* rgba: top-left of the RGBA8888 frame; strideBytes: bytes per source row; w/h must match the header.
     * Returns true when something was published. */
    bool publish(const uint8_t* rgba, uint32_t strideBytes, uint32_t w, uint32_t h) {
        if (!hdr_ || w != hdr_->width || h != hdr_->height) return false;
        uint8_t* data = map_ + hdr_->data_offset;
        const uint32_t dstStride = hdr_->stride;
        const bool color = (hdr_->flags & KDISP_FLAG_RGBA_PLANE) && len_ >= hdr_->data_offset + (size_t)w*h*5;
        uint8_t* rgb = data + (size_t)w*h;
        uint8_t* row = (uint8_t*)malloc((size_t)w * (color ? 4 : 1));
        if (!row) return false;
        int minx = (int)w, maxx = -1, miny = (int)h, maxy = -1;
        bool wrote = false;
        uint32_t oddSeq = 0;
        const int ri = swapRB_ ? 2 : 0, bi = swapRB_ ? 0 : 2;

        for (uint32_t y = 0; y < h; y++) {
            const uint8_t* src = rgba + (size_t)y * strideBytes;
            for (uint32_t x = 0; x < w; x++) {
                const uint8_t* p = src + 4 * x;
                row[x] = (uint8_t)((p[ri] * 77u + p[1] * 150u + p[bi] * 29u) >> 8);
            }
            uint8_t* prev = prev_ + (size_t)y * w * (color ? 4 : 1);
            if (color) {
                uint8_t* cr = row;
                for (uint32_t x=0; x<w; ++x) { const uint8_t* p=src+4*x; cr[4*x]=p[ri]; cr[4*x+1]=p[1]; cr[4*x+2]=p[bi]; cr[4*x+3]=255; }
                if (havePrev_ && !memcmp(cr,prev,(size_t)w*4)) continue;
                if (!wrote) { wrote=true; oddSeq=lastPub_+1; __atomic_store_n(&hdr_->seq,oddSeq,__ATOMIC_RELEASE); }
                memcpy(rgb+(size_t)y*w*4,cr,(size_t)w*4);
                memcpy(prev,cr,(size_t)w*4);
                for(uint32_t x=0;x<w;++x) data[(size_t)y*dstStride+x]=(cr[4*x]*77u+cr[4*x+1]*150u+cr[4*x+2]*29u)>>8;
                minx=0; maxx=(int)w-1; if((int)y<miny)miny=y; maxy=y;
                continue;
            }
            uint32_t x0 = 0, x1 = w - 1;
            if (havePrev_) {
                if (memcmp(row, prev, w) == 0) continue;
                while (x0 < w && row[x0] == prev[x0]) x0++;
                while (x1 > x0 && row[x1] == prev[x1]) x1--;
            }
            if (!wrote) {
                wrote = true;
                oddSeq = lastPub_ + 1;
                __atomic_store_n(&hdr_->seq, oddSeq, __ATOMIC_RELEASE);
            }
            memcpy(data + (size_t)y * dstStride + x0, row + x0, x1 - x0 + 1);
            memcpy(prev + x0, row + x0, x1 - x0 + 1);
            if ((int)x0 < minx) minx = (int)x0;
            if ((int)x1 > maxx) maxx = (int)x1;
            if ((int)y < miny) miny = (int)y;
            if ((int)y > maxy) maxy = (int)y;
        }
        free(row);
        havePrev_ = true;
        if (!wrote) return false;

        uint32_t nx = (uint32_t)minx, ny = (uint32_t)miny;
        uint32_t nw = (uint32_t)(maxx - minx + 1), nh = (uint32_t)(maxy - miny + 1);
        uint32_t ack = __atomic_load_n(&hdr_->ack, __ATOMIC_ACQUIRE);
        if (accValid_ && ack != lastPub_) {          /* reader has not consumed the last frame: merge */
            uint32_t ux0 = ax_ < nx ? ax_ : nx, uy0 = ay_ < ny ? ay_ : ny;
            uint32_t ux1 = (ax_ + aw_) > (nx + nw) ? (ax_ + aw_) : (nx + nw);
            uint32_t uy1 = (ay_ + ah_) > (ny + nh) ? (ay_ + ah_) : (ny + nh);
            nx = ux0; ny = uy0; nw = ux1 - ux0; nh = uy1 - uy0;
        }
        ax_ = nx; ay_ = ny; aw_ = nw; ah_ = nh; accValid_ = true;
        hdr_->dirty_x = nx; hdr_->dirty_y = ny; hdr_->dirty_w = nw; hdr_->dirty_h = nh;
        hdr_->frame_counter++;
        lastPub_ = oddSeq + 1;
        __atomic_store_n(&hdr_->seq, lastPub_, __ATOMIC_RELEASE);
        return true;
    }

private:
    void close_() {
        if (map_) munmap(map_, len_);
        if (fd_ >= 0) ::close(fd_);
        free(prev_);
        fd_ = -1; map_ = 0; len_ = 0; hdr_ = 0; prev_ = 0;
    }
    FrameSink(const FrameSink&);
    FrameSink& operator=(const FrameSink&);

    int fd_;
    uint8_t* map_;
    size_t len_;
    kdisp_hdr* hdr_;
    uint8_t* prev_;
    bool havePrev_, accValid_;
    uint32_t lastPub_;
    uint32_t ax_, ay_, aw_, ah_;
    bool swapRB_;
};
#endif
