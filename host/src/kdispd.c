/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors */
/* Referenced project: KOReader, AGPL-3.0, https://github.com/koreader/koreader (COPYING in that project). Files consulted:
 *   base/ffi/framebuffer_mxcfb.lua     refresh_mtk(): the MTK update ioctl, waveform and flash handling
 *   base/ffi/mxcfb_kindle_h.lua        waveform mode constants (REAGL and others)
 *   frontend/ui/uimanager.lua          refresh handling (setDirty, _refresh): only the changed parts are refreshed
 * Interface source: MediaTek hwtcon_v2 driver, GPL-2.0-only, Copyright (c) MediaTek Inc.: swipe directions 0 down 1 up 2 left
 * 3 right and at most 60 steps (hal/hwtcon_pipeline_config.h). Only the interface is used, no driver code is copied.
 * The default of 12 steps is the stock Kindle page turn value.
 */
/* kdispd: Kindle-side display daemon.
 *
 * Android (HWC) publishes dirty rectangles of an 8-bit gray frame into a shared file. kdispd copies them into /dev/fb0
 * and asks fbink to refresh them. The refresh strategy is configurable (and can be changed while Android runs):
 *
 *   contrast  gamma 1.0 .. 2.5 applied to every pixel (text gets darker, light gray backgrounds get whiter)
 *   dither    8x8 ordered dither down to the panel's 16 gray levels (used for the slow, grayscale waveforms)
 *   local     waveform for small areas and for bursts of updates: du (about 150 ms, black/white), a2 (fastest,
 *             black/white only) or auto (grayscale waveform everywhere)
 *   invert    swap black and white
 *   settle    after a burst of fast updates, one grayscale refresh of the touched area removes the ghosting: 200 ms after the
 *             last frame, or 90 ms after the finger was lifted (ctl/touchup, written by kgest) if the screen is quiet
 *   flash-only (FLASH_ONLY=1, compatibility) every change is shown by one flashing full-screen refresh after a quiet moment.
 *             Default since 1.1.0.03 is FLASH_ONLY=0 (partial refreshes, like the Kindle); display.conf files written before
 *             (no CONF_VER) have their FLASH_ONLY reset to 0 once.
 *   flash     like the Kindle: every FLASH_EVERY-th page turn (default 6; 1 = every page; 0 = never) is followed by one
 *             full-screen flashing refresh. Nothing has to be done by hand.
 *
 * Control directory <dir>/ctl (the container sees it as /dev/kdisp/ctl, mode 0777):
 *   conf      KEY=VALUE lines, rewritten by the Android launcher; picked up within 300 ms
 *   clear     create this file: full-screen flashing refresh (removes ghosting), the file is deleted
 *   freeze    while this file exists nothing is drawn (Android is in the background); one repaint when it disappears
 *   exit      (handled by embrith.sh, not here)
 *
 * The panel size is read from /dev/fb0 at start-up (panel.h); nothing is configured by hand.  */
#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <errno.h>
#include <fcntl.h>
#include <math.h>
#include <signal.h>
#include <time.h>
#include <unistd.h>
#include <sys/wait.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <linux/fb.h>
#include "kdisp_proto.h"
#include "panel.h"
#include "display_backend.h"

static unsigned g_panel_w, g_panel_h;   /* the panel size, set once by panel_detect() at the start of main */

static volatile sig_atomic_t g_stop;
static void on_sig(int s) { (void)s; g_stop = 1; }
static const char* FBINK = "/mnt/us/libkh/bin/fbink";

static double now_ms(void) {
    struct timespec t; clock_gettime(CLOCK_MONOTONIC, &t);
    return t.tv_sec * 1000.0 + t.tv_nsec / 1e6;
}

/* ---------------------------------------------------------------- configuration */
enum { WFM_DU = 0, WFM_A2 = 1, WFM_AUTO = 2 };
struct conf {
    double   contrast;
    int      color;
    int      dither, local_wfm, invert, gestures;
    unsigned settle_ms, fast_ms;
    unsigned scroll_ms;               /* like fast_ms, for a big band declared UI by a swipe (ctl/turn u): frames closer than this are a scroll (black/white waveform, one merged refresh, gray clean-up when quiet) */
    unsigned flash_every, ui_pct;     /* every N page turns one full-screen flash (0 = never); a changed band up to ui_pct percent of the screen is a local (UI) update, a bigger one is a page update */
    int page_anim;                    /* 1 = the periodic full-screen page refresh is the driver's hardware swipe animation instead of a flash */
    unsigned anim_img_pct;            /* a page turn between a picture page and a text page (one has at least this percent of mid-gray pixels, the other has not) gets no swipe animation, 0 = always animate */
    unsigned anim_steps, anim_dir;    /* swipe steps (1..60) and direction (0 down, 1 up, 2 left, 3 right): the direction of a next page, a previous page uses the opposite one (dir ^ 1) */
    int flash_only;                   /* 1 = compatibility mode: every change is shown with ONE flashing full-screen GC16 refresh once it is quiet */
    int page_wfm;                     /* waveform for larger partial updates and the settle refresh: 0 = GC16 (default), 1 = GL16, 2 = REAGL (KOReader "partial" on MTK) */
    unsigned sleep_min;
    unsigned white_at;                /* gray levels (after the contrast table) from this value up are shown as pure white, but only while ctl/white says 1 (a chosen reading app is in front) */
    int nextra;                       /* keys kdispd does not use itself (gestures G_*, AMBER_*, ...): kept and saved unchanged */
    char extra[32][96];               /* not used here: embrith.sh reads it from ctl/conf (idle minutes before sleep, 0 = never) */
    int conf_ver;                     /* 2 since 1.1.0.03 (default FLASH_ONLY=0); 0 = file from before */
};
static void conf_defaults(struct conf* c) {
    c->color = 0; c->contrast = 1.3; c->dither = 1; c->local_wfm = WFM_DU; c->invert = 0; c->gestures = 1;
    c->settle_ms = 200; c->fast_ms = 250; c->scroll_ms = 900;
    c->flash_every = 6; c->ui_pct = 10; c->page_anim = 1; c->anim_steps = 12; c->anim_img_pct = 20; c->anim_dir = 2; c->flash_only = 0; c->conf_ver = 0; c->sleep_min = 10; c->page_wfm = 0; c->white_at = 224; c->nextra = 0;
}
static const char* wfm_name(int w) { return w == WFM_A2 ? "a2" : w == WFM_AUTO ? "auto" : "du"; }

static int conf_known(struct conf* c, const char* key, const char* val) {
    if (!strcmp(key, "COLOR")) c->color = atoi(val) != 0;
    else if (!strcmp(key, "CONTRAST")) { double g = atof(val); c->contrast = g < 1.0 ? 1.0 : g > 2.5 ? 2.5 : g; }
    else if (!strcmp(key, "DITHER")) c->dither = atoi(val) != 0;
    else if (!strcmp(key, "INVERT")) c->invert = atoi(val) != 0;
    else if (!strcmp(key, "GESTURES")) c->gestures = atoi(val) != 0;
    else if (!strcmp(key, "LOCAL_WFM")) c->local_wfm = !strcasecmp(val, "a2") ? WFM_A2 : !strcasecmp(val, "auto") ? WFM_AUTO : WFM_DU;
    else if (!strcmp(key, "SETTLE_MS")) { int v = atoi(val); c->settle_ms = v < 100 ? 100 : v > 5000 ? 5000 : (unsigned)v; }
    else if (!strcmp(key, "FLASH_EVERY")) { int v = atoi(val); c->flash_every = v < 0 ? 0 : v > 50 ? 50 : (unsigned)v; }
    else if (!strcmp(key, "FLASH_ONLY")) c->flash_only = atoi(val) != 0;
    else if (!strcmp(key, "PAGE_ANIM")) c->page_anim = atoi(val) != 0;
    else if (!strcmp(key, "ANIM_STEPS")) { int v = atoi(val); c->anim_steps = v < 1 ? 1 : v > 60 ? 60 : (unsigned)v; }
    else if (!strcmp(key, "ANIM_IMG_PCT")) { int v = atoi(val); c->anim_img_pct = v < 0 ? 0 : v > 100 ? 100 : (unsigned)v; }
    else if (!strcmp(key, "ANIM_DIR")) { int v = atoi(val); c->anim_dir = v < 0 ? 0 : v > 3 ? 3 : (unsigned)v; }
    else if (!strcmp(key, "UI_PCT")) { int v = atoi(val); c->ui_pct = v < 2 ? 2 : v > 50 ? 50 : (unsigned)v; }
    else if (!strcmp(key, "PAGE_PCT")) { /* retired, replaced by UI_PCT */ }
    else if (!strcmp(key, "CONF_VER")) c->conf_ver = atoi(val);
    else if (!strcmp(key, "PAGE_WFM")) c->page_wfm = !strcasecmp(val, "reagl") ? 2 : !strcasecmp(val, "gl16");
    else if (!strcmp(key, "SLEEP_MIN")) { int v = atoi(val); c->sleep_min = v < 0 ? 0 : v > 240 ? 240 : (unsigned)v; }
    else if (!strcmp(key, "WHITE_AT")) { int v = atoi(val); c->white_at = v < 128 ? 128 : v > 254 ? 254 : (unsigned)v; }
    else if (!strcmp(key, "SCROLL_MS")) { int v = atoi(val); c->scroll_ms = v < 50 ? 50 : v > 3000 ? 3000 : (unsigned)v; }
    else if (!strcmp(key, "FAST_MS")) { int v = atoi(val); c->fast_ms = v < 50 ? 50 : v > 2000 ? 2000 : (unsigned)v; }
    else return 0;
    return 1;
}
static void conf_apply(struct conf* c, const char* key, const char* val) {
    if (conf_known(c, key, val)) return;
    size_t kl = strlen(key);
    if (!kl || kl + strlen(val) + 2 > sizeof c->extra[0]) return;
    int k;
    for (k = 0; k < c->nextra; k++) if (!strncmp(c->extra[k], key, kl) && c->extra[k][kl] == '=') break;
    if (k == c->nextra) { if (c->nextra >= 32) return; c->nextra++; }
    snprintf(c->extra[k], sizeof c->extra[k], "%s=%s", key, val);
}
static void conf_load(struct conf* c, const char* path) {
    FILE* f = fopen(path, "r");
    if (!f) return;
    char line[160];
    while (fgets(line, sizeof line, f)) {
        char* eq = strchr(line, '='); if (!eq || line[0] == '#') continue;
        *eq = 0; char* v = eq + 1; size_t n = strlen(v);
        while (n && (v[n - 1] == '\n' || v[n - 1] == '\r' || v[n - 1] == ' ')) v[--n] = 0;
        conf_apply(c, line, v);
    }
    fclose(f);
}
static void conf_save(const struct conf* c, const char* path, mode_t mode) {
    char tmp[300]; snprintf(tmp, sizeof tmp, "%s.tmp", path);
    FILE* f = fopen(tmp, "w");
    if (!f) return;
    fprintf(f, "CONTRAST=%.1f\nDITHER=%d\nLOCAL_WFM=%s\nINVERT=%d\nGESTURES=%d\nSETTLE_MS=%u\nFAST_MS=%u\nSCROLL_MS=%u\nFLASH_EVERY=%u\nUI_PCT=%u\nFLASH_ONLY=%d\nPAGE_ANIM=%d\nANIM_STEPS=%u\nANIM_IMG_PCT=%u\nANIM_DIR=%u\nPAGE_WFM=%s\nSLEEP_MIN=%u\nWHITE_AT=%u\nCONF_VER=2\n",
            c->contrast, c->dither, wfm_name(c->local_wfm), c->invert, c->gestures, c->settle_ms, c->fast_ms, c->scroll_ms, c->flash_every, c->ui_pct, c->flash_only,
            c->page_anim, c->anim_steps, c->anim_img_pct, c->anim_dir,
            c->page_wfm == 2 ? "reagl" : c->page_wfm ? "gl16" : "gc16", c->sleep_min, c->white_at);
    fprintf(f, "COLOR=%d\n", c->color);
    for (int k = 0; k < c->nextra; k++) fprintf(f, "%s\n", c->extra[k]);
    fflush(f); fsync(fileno(f));          /* display.conf is on the Kindle's user storage: survive a power loss / hard reset */
    fclose(f);
    chmod(tmp, mode);
    rename(tmp, path);
}

/* ---------------------------------------------------------------- pixel pipeline */
static uint8_t lut[256];
static int g_noanim;        /* 1 while ctl/noanim says that the app in front must not use the page turn animation */
static int g_white;         /* 1 while ctl/white says that an app chosen in Settings (white reading background) is in front */
static void build_lut(const struct conf* c) {
    for (int i = 0; i < 256; i++) {
        int v = (int)(pow(i / 255.0, c->contrast) * 255.0 + 0.5);
        if (v > 255) v = 255;
        if (g_white && v >= (int)c->white_at) v = 255;
        lut[i] = (uint8_t)(c->invert ? 255 - v : v);
    }
}
static const uint8_t bayer8[64] = {
     0, 32,  8, 40,  2, 34, 10, 42,   48, 16, 56, 24, 50, 18, 58, 26,
    12, 44,  4, 36, 14, 46,  6, 38,   60, 28, 52, 20, 62, 30, 54, 22,
     3, 35, 11, 43,  1, 33,  9, 41,   51, 19, 59, 27, 49, 17, 57, 25,
    15, 47,  7, 39, 13, 45,  5, 37,   63, 31, 55, 23, 61, 29, 53, 21 };

struct rect { unsigned x, y, w, h; };
static void rect_union(struct rect* a, const struct rect* b) {
    unsigned x0 = a->x < b->x ? a->x : b->x, y0 = a->y < b->y ? a->y : b->y;
    unsigned x1 = a->x + a->w > b->x + b->w ? a->x + a->w : b->x + b->w;
    unsigned y1 = a->y + a->h > b->y + b->h ? a->y + a->h : b->y + b->h;
    a->x = x0; a->y = y0; a->w = x1 - x0; a->h = y1 - y0;
}

/* source (shared frame) -> framebuffer through the contrast table and (optionally) the ordered dither */
static unsigned fb_bytes = 1;
static const uint8_t *rgb_plane;
static int color_enabled;
static void put_gray(uint8_t *row, unsigned x, uint8_t v) {
    if (fb_bytes == 1) row[x] = v;
    else { uint8_t *p = row + (size_t)x * 4; p[0] = p[1] = p[2] = v; p[3] = 255; }
}
static void copy_gray(uint8_t *dst, const uint8_t *src, unsigned count) {
    for (unsigned x = 0; x < count; ++x) put_gray(dst, x, src[x]);
}

static void blit(uint8_t* fb, unsigned fbstride, const uint8_t* src, const struct rect* r, int dither) {
    if (color_enabled && rgb_plane && fb_bytes == 4) {
        for (unsigned y=r->y;y<r->y+r->h;++y) memcpy(fb+(size_t)y*fbstride+(size_t)r->x*4,rgb_plane+((size_t)y*g_panel_w+r->x)*4,(size_t)r->w*4);
        return;
    }
    for (unsigned y = r->y; y < r->y + r->h; y++) {
        const uint8_t* s = src + (size_t)y * g_panel_w;
        uint8_t* d = fb + (size_t)y * fbstride;
        if (!dither) {
            for (unsigned x = r->x; x < r->x + r->w; x++) put_gray(d, x, lut[s[x]]);
        } else {
            const uint8_t* b = bayer8 + (y & 7) * 8;
            for (unsigned x = r->x; x < r->x + r->w; x++) {
                int v = lut[s[x]] + (int)(b[x & 7] * 17 / 64) - 8;
                v = v < 0 ? 0 : v > 255 ? 255 : v;
                v = (v + 8) / 17 * 17;
                put_gray(d, x, (uint8_t)(v > 255 ? 255 : v));
            }
        }
    }
}

/* share of mid-gray pixels (after the contrast table) in a rectangle, in 1/1000: text edges are a few per mille to a few percent,
 * photos and gray panels are most of the area. Decides the waveform and the dithering of a band. */
static unsigned gray_pm(const uint8_t* src, const struct rect* r) {
    unsigned long long g = 0;
    for (unsigned y = r->y; y < r->y + r->h; y++) {
        const uint8_t* s = src + (size_t)y * g_panel_w;
        for (unsigned x = r->x; x < r->x + r->w; x++) { uint8_t v = lut[s[x]]; if (v > 40 && v < 215) g++; }
    }
    return (unsigned)(g * 1000 / ((unsigned long long)r->w * r->h));
}
/* Share (1/1000) of the pixels in the rectangle that belong to a flat gray area: a pixel the panel does not show as pure black or white
 * (16 gray levels: 9..246) with the same value as its left neighbour. A gray background, button or slider counts, the soft edges of
 * text do not. A band with such an area goes out in the gray waveform at once instead of DU plus a clean-up. */
static unsigned flat_gray_pm(const uint8_t* src, const struct rect* r) {
    unsigned long long g = 0;
    for (unsigned y = r->y; y < r->y + r->h; y++) {
        const uint8_t* s = src + (size_t)y * g_panel_w;
        for (unsigned x = r->x + 1; x < r->x + r->w; x++) { uint8_t v = lut[s[x]]; if (v > 8 && v < 247 && s[x] == s[x - 1]) g++; }
    }
    return (unsigned)(g * 1000 / ((unsigned long long)r->w * r->h));
}

/* ---------------------------------------------------------------- dirty bands
 * Like KOReader's menus: only the parts that really changed are refreshed, not their bounding box. g_prev holds the source picture
 * as it was last sent to the panel; split_dirty compares the new frame with it inside the dirty rectangle and returns the changed
 * rows as horizontal bands (rows closer than BAND_GAP are one band), each only as wide as its changes, at most MAXBANDS. */
#define MAXBANDS 8
#define BAND_GAP 24
#define FLAT_GRAY_PM 20     /* a flat gray area (background, button) above this share (1/1000) of a band rules out the DU waveform too */
#define PIC_FAST_PM 200     /* a band of a fast burst with at least this share (1/1000) of mid-gray is a picture (cover, photo): not DU */
#define BG_GRAY_PM 100      /* unchanged gray inside a band (wallpaper, photo) above this share (1/1000) rules out the DU waveform */
static uint8_t* g_prev;
/* Overlay state (a reader's bars and panels): while it is open every update is local, whatever ctl/turn says. It starts when a bar or an
 * edge panel appears (or after a middle tap) and ends when the picture is back to what it was before (g_snap) or when a page turn is seen. */
#define EDGE_PX 12          /* a band this close to a screen edge touches it */
#define ESC_PCT 20          /* a band of at least this share of the screen, with nothing of the old page left, while an overlay is open is a page turn */
#define BAR_GAP 48          /* a band this close to one of the app's bars sits at that bar */
#define KEEP_PCT 8          /* the old page is still there when at least this share of the screen rows are unchanged rows with content */
#define OV_TOL_PM 3         /* the picture is "back" when at most this many per mille of the pixels differ (clock, battery) */
static uint8_t* g_snap;
static int same_picture(const uint8_t* a, const uint8_t* b, unsigned long tol) {
    unsigned long d = 0;
    for (unsigned y = 0; y < g_panel_h; y++) {
        const uint8_t* p = a + (size_t)y * g_panel_w; const uint8_t* q = b + (size_t)y * g_panel_w;
        if (!memcmp(p, q, g_panel_w)) continue;
        for (unsigned x = 0; x < g_panel_w; x++) if (p[x] != q[x] && ++d > tol) return 0;
    }
    return 1;
}
/* How many rows with content (not one flat color) the frame leaves untouched, counted in the picture on the panel (g_prev): the rows
 * outside every changed band. A panel that opens over a page leaves the rest of the page as it was, a page turn leaves next to nothing. */
static unsigned kept_rows(const struct rect* bands, int nb) {
    unsigned n = 0;
    if (!g_prev) return 0;
    for (unsigned y = 0; y < g_panel_h; y++) {
        int in = 0;
        for (int i = 0; i < nb && !in; i++) in = y >= bands[i].y && y < bands[i].y + bands[i].h;
        if (in) continue;
        const uint8_t* p = g_prev + (size_t)y * g_panel_w; uint8_t lo = p[0], hi = p[0];
        for (unsigned x = 1; x < g_panel_w && hi - lo < 64; x++) { if (p[x] < lo) lo = p[x]; else if (p[x] > hi) hi = p[x]; }
        if (hi - lo >= 64) n++;
    }
    return n;
}
/* 1 when the band sits at exactly one of the bars that were on screen when the overlay opened (touching or overlapping it, at most
 * BAR_GAP away): a panel that grows out of a bar. The body of a page lies between the bars and reaches both, so it is not one. */
static int at_one_bar(const struct rect* b, const struct rect* bars, int n) {
    int k = 0;
    for (int i = 0; i < n; i++) {
        const struct rect* q = &bars[i];
        unsigned dx = q->x >= b->x + b->w ? q->x - (b->x + b->w) : b->x >= q->x + q->w ? b->x - (q->x + q->w) : 0;
        unsigned dy = q->y >= b->y + b->h ? q->y - (b->y + b->h) : b->y >= q->y + q->h ? b->y - (q->y + q->h) : 0;
        if ((dx == 0 && dy <= BAR_GAP) || (dy == 0 && dx <= BAR_GAP)) k++;
    }
    return k == 1;
}
/* a band that touches one screen edge and spans (almost) the whole edge: a bar or a half-screen panel, never the page of a reader */
static int edge_panel(const struct rect* b) {
    int top = b->y <= EDGE_PX, bot = b->y + b->h + EDGE_PX >= g_panel_h, lef = b->x <= EDGE_PX, rig = b->x + b->w + EDGE_PX >= g_panel_w;
    if (b->w * 100ul >= g_panel_w * 95ul && b->h * 100ul < g_panel_h * 90ul && (top || bot)) return 1;
    if (b->h * 100ul >= g_panel_h * 95ul && b->w * 100ul < g_panel_w * 90ul && (lef || rig)) return 1;
    return 0;
}
static void track(const uint8_t* src, const struct rect* r) {
    if (!g_prev) return;
    for (unsigned y = r->y; y < r->y + r->h; y++) memcpy(g_prev + (size_t)y * g_panel_w + r->x, src + (size_t)y * g_panel_w + r->x, r->w);
}
/* like blit without dither, but only the pixels that differ from what the panel shows (g_prev); the rest of fb stays as it was,
 * so a DU refresh of a sparse band (outline of a pressed row) drives only the outline and leaves the dithered wallpaper alone */
static void blit_changed(uint8_t* fb, unsigned fbstride, const uint8_t* src, const struct rect* r) {
    for (unsigned y = r->y; y < r->y + r->h; y++) {
        const uint8_t* s = src + (size_t)y * g_panel_w;
        const uint8_t* p = g_prev ? g_prev + (size_t)y * g_panel_w : NULL;
        uint8_t* d = fb + (size_t)y * fbstride;
        for (unsigned x = r->x; x < r->x + r->w; x++) {
            if (p && s[x] == p[x]) continue;
            put_gray(d, x, lut[s[x]]);
        }
    }
}
static int split_dirty(const uint8_t* src, const struct rect* r, struct rect* out) {
    if (!g_prev) { out[0] = *r; return 1; }
    struct rect tmp[64]; int n = 0, in = 0; unsigned by0 = 0, blast = 0, bx0 = 0, bx1 = 0;
    for (unsigned y = r->y; y < r->y + r->h; y++) {
        const uint8_t* s = src + (size_t)y * g_panel_w; const uint8_t* p = g_prev + (size_t)y * g_panel_w;
        if (!memcmp(s + r->x, p + r->x, r->w)) continue;
        unsigned a = r->x, b = r->x + r->w - 1;
        while (s[a] == p[a]) a++;
        while (s[b] == p[b]) b--;
        if (in && y - blast <= BAND_GAP) { if (a < bx0) bx0 = a; if (b > bx1) bx1 = b; blast = y; continue; }
        if (in) {
            struct rect q = { bx0, by0, bx1 - bx0 + 1, blast - by0 + 1 };
            if (n < 64) tmp[n++] = q; else rect_union(&tmp[63], &q);
        }
        in = 1; by0 = blast = y; bx0 = a; bx1 = b;
    }
    if (in) { struct rect q = { bx0, by0, bx1 - bx0 + 1, blast - by0 + 1 }; if (n < 64) tmp[n++] = q; else rect_union(&tmp[63], &q); }
    while (n > MAXBANDS) {                       /* merge the two neighbouring bands with the smallest gap */
        int best = 0; unsigned bg = ~0u;
        for (int i = 0; i + 1 < n; i++) { unsigned g = tmp[i + 1].y - (tmp[i].y + tmp[i].h); if (g < bg) { bg = g; best = i; } }
        rect_union(&tmp[best], &tmp[best + 1]);
        for (int i = best + 1; i + 1 < n; i++) tmp[i] = tmp[i + 1];
        n--;
    }
    for (int i = 0; i < n; i++) {                /* a few pixels of margin (antialiasing at the edges) */
        struct rect q = tmp[i];
        unsigned x0 = q.x > 4 ? q.x - 4 : 0, y0 = q.y > 4 ? q.y - 4 : 0;
        unsigned x1 = q.x + q.w + 4 > g_panel_w ? g_panel_w : q.x + q.w + 4, y1 = q.y + q.h + 4 > g_panel_h ? g_panel_h : q.y + q.h + 4;
        out[i].x = x0; out[i].y = y0; out[i].w = x1 - x0; out[i].h = y1 - y0;
    }
    return n;
}

/* how many pixels of a band really differ from what the panel shows (g_prev), and how many of those end up mid-gray: a band can be
 * large (the outline of a pressed row is as wide as the row) and still change very little. Call before track(). */
static void count_changed(const uint8_t* src, const struct rect* r, unsigned long* chg, unsigned long* gchg) {
    unsigned long c = 0, g = 0;
    for (unsigned y = r->y; y < r->y + r->h; y++) {
        const uint8_t* s = src + (size_t)y * g_panel_w; const uint8_t* p = g_prev ? g_prev + (size_t)y * g_panel_w : NULL;
        for (unsigned x = r->x; x < r->x + r->w; x++) {
            if (p && s[x] == p[x]) continue;
            c++;
            uint8_t v = lut[s[x]]; if (v > 40 && v < 215) g++;
        }
    }
    *chg = c; *gchg = g;
}

/* the share of mid-gray pixels (percent) in a rect of a picture, from every 4th pixel of every 4th row: a text page has a few (the edges of
 * the letters), a picture page has many. Call before track(), g_prev is the picture the panel shows now. */
static int mid_pct(const uint8_t* src, const struct rect* r) {
    if (!src) return 0;
    unsigned long n = 0, g = 0;
    for (unsigned y = r->y; y < r->y + r->h; y += 4) {
        const uint8_t* s = src + (size_t)y * g_panel_w;
        for (unsigned x = r->x; x < r->x + r->w; x += 4) { n++; uint8_t v = lut[s[x]]; if (v > 40 && v < 215) g++; }
    }
    return n ? (int)(g * 100 / n) : 0;
}

/* ---------------------------------------------------------------- refresh */
/* fbink behaves differently between firmware versions (after the 5.20.1 update most invocations with -W / a region / -q died with
 * SIGSEGV while a plain "fbink -s" still worked). So there is a list of ways to ask for a refresh, from the most capable to the most
 * conservative. The first one that exits without crashing is kept; when a call dies, the next variant is used and the screen is redrawn. */
#define NVARIANT 5
static int g_variant = 0;          /* the variant used for new refreshes */
static int g_variant_ok = -1;      /* a variant that was seen to exit cleanly */
#define MAXKIDS 32
static struct { pid_t pid; int variant; } g_kids[MAXKIDS];
static void remember_kid(pid_t p, int v) {
    for (int i = 0; i < MAXKIDS; i++) if (g_kids[i].pid == 0) { g_kids[i].pid = p; g_kids[i].variant = v; return; }
}
static const char* variant_desc(int v) {
    static const char* d[NVARIANT] = { "-q [-f] -s <region> -W <mode>", "-q [-f] -s<region> -W <mode>", "[-f] -s <region>", "[-f] -s", "-s" };
    return d[v < 0 ? 0 : v >= NVARIANT ? NVARIANT - 1 : v];
}
static void spawn_fbink(const struct rect* r, const char* mode, int flash) {
    char region[96], attached[100];
    snprintf(region, sizeof region, "top=%u,left=%u,width=%u,height=%u", r->y, r->x, r->w, r->h);
    snprintf(attached, sizeof attached, "-s%s", region);
    int v = g_variant;
    pid_t p = fork();
    if (p == 0) {
        int dn = open("/dev/null", O_WRONLY);
        if (dn >= 0) { dup2(dn, 1); dup2(dn, 2); }
        const char* a[12]; int n = 0;
        a[n++] = "fbink";
        if (v <= 1) a[n++] = "-q";
        if (flash && v <= 3) a[n++] = "-f";
        if (v == 0)      { a[n++] = "-s"; a[n++] = region; a[n++] = "-W"; a[n++] = mode; }
        else if (v == 1) { a[n++] = attached; a[n++] = "-W"; a[n++] = mode; }
        else if (v == 2) { a[n++] = "-s"; a[n++] = region; }
        else             { a[n++] = "-s"; }
        a[n] = NULL;
        execv(FBINK, (char* const*)a);
        _exit(127);
    }
    if (p > 0) remember_kid(p, v);
}
/* ---- direct update through the MTK driver ioctl, the same call KOReader makes on the PW6 (base/ffi/framebuffer_mxcfb.lua, refresh_mtk).
 * It allows what fbink does not: REAGL, the DU/GC16 histogram split done by the driver, and a full-mode update without a visible flash.
 * When the ioctl fails, refresh_region falls back to fbink. */
struct mtk_rect { uint32_t top, left, width, height; };
struct mtk_alt { uint32_t phys_addr, width, height; struct mtk_rect alt_update_region; };
struct mtk_update {
    struct mtk_rect update_region;
    uint32_t waveform_mode, update_mode, update_marker;
    int32_t temp;
    uint32_t flags;
    int32_t dither_mode, quant_bit;
    struct mtk_alt alt_buffer_data;
    uint32_t swipe_direction, swipe_steps;
    uint32_t hist_bw_waveform_mode, hist_gray_waveform_mode;
    uint32_t ts_pxp, ts_epdc;
};
_Static_assert(sizeof(struct mtk_update) == 96, "mtk_update must match the ioctl size (96)");
struct mtk_marker { uint32_t update_marker, collision_test; };
#define MTK_SEND_UPDATE      0x4060462eU
#define MTK_WAIT_COMPLETE    0xc008462fU
#define MTK_WAIT_SUBMISSION  0xc0044637U
#define MTK_SET_UPDATE_FLAGS 0x4004463bU
#define MTK_FAST_MODE_ON     0x80000001U      /* keeps the driver from silently promoting updates to REAGL (KOReader does this on the PW6) */
#define MTK_FAST_MODE_OFF    0x80000000U
#define MTK_WFM_DU 1u
#define MTK_WFM_GC16 2u
#define MTK_WFM_GL16 3u
#define MTK_WFM_REAGL 4u
#define MTK_WFM_A2 6u
static int g_fbfd = -1;
static int g_direct = -1;          /* -1 not tried yet, 1 works, 0 failed or disabled (fbink is used) */
static uint32_t g_marker;
static unsigned mtk_wfm(const char* m) {
    return !strcmp(m, "DU") ? MTK_WFM_DU : !strcmp(m, "A2") ? MTK_WFM_A2 : !strcmp(m, "GL16") ? MTK_WFM_GL16 : !strcmp(m, "REAGL") ? MTK_WFM_REAGL : MTK_WFM_GC16;
}
/* 1 = sent (or skipped on purpose), 0 = the ioctl failed */
static int mtk_update_region(const struct rect* r, const char* mode, int flash) {
    unsigned x = r->x, y = r->y, w = r->w, h = r->h;
    if (x >= g_panel_w || y >= g_panel_h) return 1;
    if (x + w > g_panel_w) w = g_panel_w - x;
    if (y + h > g_panel_h) h = g_panel_h - y;
    if (w <= 1 || h <= 1) return 1;                 /* tiny regions may upset the driver (KOReader skips them too) */
    unsigned wfm = fb_bytes == 4 ? 257u : mtk_wfm(mode);
    // /* like KOReader: a flashing update waits until the previous one was submitted; GC16, REAGL and a full-screen flash wait until it is done */
    // if (g_marker && flash) { uint32_t m = g_marker; ioctl(g_fbfd, MTK_WAIT_SUBMISSION, &m); }
    // if (g_marker && (wfm == MTK_WFM_REAGL || wfm == MTK_WFM_GC16 || (flash && w == g_panel_w && h == g_panel_h))) {
    //     struct mtk_marker mk = { g_marker, 0 };
    //     ioctl(g_fbfd, MTK_WAIT_COMPLETE, &mk);
    // }
    struct mtk_update u; memset(&u, 0, sizeof u);
    u.update_region.top = y; u.update_region.left = x; u.update_region.width = w; u.update_region.height = h;
    u.waveform_mode = wfm;
    u.update_mode = (flash || (fb_bytes == 4 && w == g_panel_w && h == g_panel_h) || wfm == MTK_WFM_REAGL) ? 1 : 0;     /* REAGL is always a full-mode update: it drives every pixel and cleans the ghosts, without a visible flash */
    if (++g_marker == 0) g_marker = 1;
    u.update_marker = g_marker;
    u.temp = 4096;                                  /* TEMP_USE_AMBIENT */
    if (wfm == MTK_WFM_REAGL) { u.hist_bw_waveform_mode = MTK_WFM_REAGL; u.hist_gray_waveform_mode = MTK_WFM_REAGL; }
    else { u.hist_bw_waveform_mode = MTK_WFM_DU; u.hist_gray_waveform_mode = MTK_WFM_GC16; }
    if (wfm == MTK_WFM_DU) u.flags = 2;             /* EPDC_FLAG_FORCE_MONOCHROME */
    return ioctl(g_fbfd, MTK_SEND_UPDATE, &u) == 0;
}
/* The driver's hardware swipe animation (what the Kindle UI uses for page turns): EPDC_FLAG_ENABLE_SWIPE plus direction and steps, same call as
 * RetyOS makes (sources/stage5/hwtcon_frame_core.cpp, EnableSwipeAnimation). The driver picks the REAGL waveform for it itself and drops the
 * flag when the waveform file has no swipe support. 1 = sent, 0 = not possible (the caller flashes instead). */
#define MTK_FLAG_ENABLE_SWIPE 0x10000u
#define MTK_WFM_P2SW 13u
static int mtk_swipe_region(const struct rect* r, unsigned steps, unsigned dir) {
    if (g_direct == 0 || g_fbfd < 0 || r->w <= 1 || r->h <= 1) return 0;
    struct mtk_update u; memset(&u, 0, sizeof u);
    u.update_region.top = r->y; u.update_region.left = r->x; u.update_region.width = r->w; u.update_region.height = r->h;
    if (!steps || steps > 60 || dir > 3) return 0;
    u.waveform_mode = MTK_WFM_P2SW; u.update_mode = 0;
    if (++g_marker == 0) g_marker = 1;
    u.update_marker = g_marker;
    u.temp = 4096;
    u.flags = MTK_FLAG_ENABLE_SWIPE;
    u.swipe_direction = dir; u.swipe_steps = steps;
    u.hist_bw_waveform_mode = MTK_WFM_DU; u.hist_gray_waveform_mode = MTK_WFM_GC16;
    return ioctl(g_fbfd, MTK_SEND_UPDATE, &u) == 0;
}
static void refresh_region(const struct rect* r, const char* mode, int flash) {
    if (g_direct != 0) {
        if (mtk_update_region(r, mode, flash)) {
            if (g_direct < 0) { g_direct = 1; printf("refresh: direct MTK ioctl works\n"); fflush(stdout); }
            return;
        }
        if (g_direct < 0) { g_direct = 0; printf("refresh: direct MTK ioctl failed (%s), using fbink\n", strerror(errno)); fflush(stdout); }
    }
    spawn_fbink(r, mode, flash);
}

/* Height in pixels of the soft keyboard while it is on screen (ctl/kbd, written by the input method; the keyboard sits at the bottom
 * edge), 0 when it is not shown. */
static unsigned kbd_height(const char* path) {
    FILE* f = fopen(path, "r");
    if (!f) return 0;
    int h = 0;
    if (fscanf(f, "%d", &h) != 1) h = 0;
    fclose(f);
    return h < 0 ? 0 : h > (int)g_panel_h ? g_panel_h : (unsigned)h;
}

/* Last touch class as kgest or the system UI declared it (ctl/turn: "<wall ms> n|p|u|c|N|P"): 'n' next page, 'p' previous page (a guess from the touch), 'N' 'P' the same declared by the system UI (a page turn whatever the picture looks like), 'u' UI (not a page turn), 'c' tap in the middle third (a reader shows or hides its bars), 0 when there is no fresh hint. */

static char turn_hint(const char* path) {
    FILE* f = fopen(path, "r");
    if (!f) return 0;
    long long ms = 0; char c = 0;
    int ok = fscanf(f, "%lld %c", &ms, &c) == 2;
    fclose(f);
    struct timespec ts; clock_gettime(CLOCK_REALTIME, &ts);
    long long now = (long long)ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
    return ok && now - ms >= -200 && now - ms <= 1500 && (c == 'n' || c == 'p' || c == 'u' || c == 'c' || c == 'N' || c == 'P') ? c : 0;
}

/* Refresh declarations (ctl/refresh, one per line: "<wall ms> <mode> <x> <y> <w> <h> <d>"), like a KOReader widget calling
 * setDirty(widget, mode, region, dither). mode: a2 fast ui partial flashui flashpartial full (priority in this order, as in KOReader);
 * w = 0 means the whole screen; d = 1 asks for dithering (image content). Only lines younger than 1500 ms count. */
enum { RM_A2 = 0, RM_FAST, RM_UI, RM_PARTIAL, RM_FLASHUI, RM_FLASHPARTIAL, RM_FULL };
struct decl { int mode, dither; struct rect r; };
#define MAXDECL 16
static int decl_mode(const char* s) {
    static const char* const n[] = { "a2", "fast", "ui", "partial", "flashui", "flashpartial", "full" };
    for (int i = 0; i < 7; i++) if (!strcmp(s, n[i])) return i;
    return -1;
}
static int read_decls(const char* path, struct decl* out) {
    FILE* f = fopen(path, "r");
    if (!f) return 0;
    struct timespec ts; clock_gettime(CLOCK_REALTIME, &ts);
    long long now = (long long)ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
    int n = 0; char line[96];
    while (n < MAXDECL && fgets(line, sizeof line, f)) {
        long long ms; char m[16]; unsigned x, y, w, h; int d = 0;
        if (sscanf(line, "%lld %15s %u %u %u %u %d", &ms, m, &x, &y, &w, &h, &d) < 6) continue;
        int k = decl_mode(m);
        if (k < 0 || now - ms < -200 || now - ms > 1500) continue;
        if (w == 0) { x = 0; y = 0; w = g_panel_w; h = g_panel_h; }
        out[n].mode = k; out[n].dither = d != 0; out[n].r.x = x; out[n].r.y = y; out[n].r.w = w; out[n].r.h = h; n++;
    }
    fclose(f);
    return n;
}
/* the declared mode for a band: the highest mode among the declarations that overlap it (dither: any of them), -1 when none does */
static int decl_for(const struct decl* d, int nd, const struct rect* b, int* dither) {
    int m = -1;
    for (int i = 0; i < nd; i++) {
        if (d[i].r.x >= b->x + b->w || b->x >= d[i].r.x + d[i].r.w || d[i].r.y >= b->y + b->h || b->y >= d[i].r.y + d[i].r.h) continue;
        if (d[i].mode > m) m = d[i].mode;
        if (d[i].dither) *dither = 1;
    }
    return m;
}

/* Called regularly: collect finished fbink processes. Returns 1 when the variant changed (the caller redraws the whole screen). */
static int reap_fbink(void) {
    int changed = 0, st; pid_t p;
    while ((p = waitpid(-1, &st, WNOHANG)) > 0) {
        int v = -1;
        for (int i = 0; i < MAXKIDS; i++) if (g_kids[i].pid == p) { v = g_kids[i].variant; g_kids[i].pid = 0; }
        if (v < 0) continue;
        int bad = WIFSIGNALED(st) || (WIFEXITED(st) && WEXITSTATUS(st) != 0);
        if (!bad) { if (g_variant_ok < 0) { g_variant_ok = v; printf("fbink variant %d works: fbink %s\n", v, variant_desc(v)); fflush(stdout); } continue; }
        printf("fbink variant %d failed (%s %d): fbink %s\n", v, WIFSIGNALED(st) ? "signal" : "exit", WIFSIGNALED(st) ? WTERMSIG(st) : WEXITSTATUS(st), variant_desc(v));
        fflush(stdout);
        if (g_variant_ok < 0 && v == g_variant && g_variant < NVARIANT - 1) { g_variant++; changed = 1; printf("trying variant %d: fbink %s\n", g_variant, variant_desc(g_variant)); fflush(stdout); }
    }
    return changed;
}

/* ---- screenshot: the last frame Android drew, as an 8-bit gray PNG. Stored (uncompressed) deflate blocks, so no zlib is needed. ---- */
static uint32_t crc_tab[256];
static uint32_t crc_run(uint32_t c, const uint8_t* p, size_t n) {
    if (!crc_tab[1]) for (uint32_t i = 0; i < 256; i++) { uint32_t v = i; for (int k = 0; k < 8; k++) v = (v & 1) ? 0xedb88320u ^ (v >> 1) : v >> 1; crc_tab[i] = v; }
    while (n--) c = crc_tab[(c ^ *p++) & 0xff] ^ (c >> 8);
    return c;
}
static void put32(uint8_t* b, uint32_t v) { b[0] = v >> 24; b[1] = v >> 16; b[2] = v >> 8; b[3] = v; }
static int png_chunk(FILE* f, const char* type, const uint8_t* d, uint32_t n) {
    uint8_t b[4]; put32(b, n);
    uint32_t c = crc_run(0xffffffffu, (const uint8_t*)type, 4);
    if (n) c = crc_run(c, d, n);
    if (fwrite(b, 1, 4, f) != 4 || fwrite(type, 1, 4, f) != 4 || (n && fwrite(d, 1, n, f) != n)) return -1;
    put32(b, c ^ 0xffffffffu);
    return fwrite(b, 1, 4, f) == 4 ? 0 : -1;
}
static int save_png(const char* path, const uint8_t* g, unsigned w, unsigned h) {
    size_t rawn = (size_t)(w + 1) * h, nblk = (rawn + 65534) / 65535, zn = 2 + nblk * 5 + rawn + 4;
    uint8_t* raw = malloc(rawn); uint8_t* z = malloc(zn);
    if (!raw || !z) { free(raw); free(z); return -1; }
    for (unsigned y = 0; y < h; y++) { raw[(size_t)y * (w + 1)] = 0; memcpy(raw + (size_t)y * (w + 1) + 1, g + (size_t)y * w, w); }
    size_t o = 0; z[o++] = 0x78; z[o++] = 0x01;
    for (size_t i = 0; i < rawn; i += 65535) {
        size_t n = rawn - i < 65535 ? rawn - i : 65535;
        z[o++] = (i + n >= rawn) ? 1 : 0; z[o++] = n & 255; z[o++] = (n >> 8) & 255; z[o++] = (~n) & 255; z[o++] = ((~n) >> 8) & 255;
        memcpy(z + o, raw + i, n); o += n;
    }
    uint32_t a = 1, s2 = 0;
    for (size_t i = 0; i < rawn; i++) { a = (a + raw[i]) % 65521; s2 = (s2 + a) % 65521; }
    put32(z + o, (s2 << 16) | a); o += 4;
    uint8_t ih[13] = { 0 }; put32(ih, w); put32(ih + 4, h); ih[8] = 8;
    char tmp[420]; snprintf(tmp, sizeof tmp, "%s.tmp", path);
    FILE* f = fopen(tmp, "wb"); int rc = -1;
    if (f) {
        static const uint8_t sig[8] = { 0x89, 'P', 'N', 'G', 13, 10, 26, 10 };
        if (fwrite(sig, 1, 8, f) == 8 && png_chunk(f, "IHDR", ih, 13) == 0 && png_chunk(f, "IDAT", z, (uint32_t)o) == 0 && png_chunk(f, "IEND", NULL, 0) == 0) rc = 0;
        if (fclose(f) != 0) rc = -1;
    }
    if (rc == 0) rc = rename(tmp, path); else unlink(tmp);
    free(raw); free(z);
    return rc;
}

/* ---- start-up splash: a picture (the name) and a progress bar, drawn straight into the Kindle framebuffer while Android boots.
 * embrith.sh writes 0..100 into ctl/progress; 100 (or a time-out) ends the splash and the Android screen takes over. ---- */
#define BAR_H 12                /* the bar area: a thin track (2 px) and, over the filled part, a thick bar (6 px) with round ends, like the brightness bar */
#define BAR_Y (g_panel_h - 230)   /* near the bottom, 230 px above the edge (make-assets.py keeps the name clear of it) */
static int bar_x0, bar_w;   /* same width as the visible part of the picture (splash_bounds); the defaults are set in main */
/* the visible width of the logo: the columns that have dark pixels above the bar. Without a picture the default stays. */
static void splash_bounds(const uint8_t* img) {
    int minx = g_panel_w, maxx = -1;
    for (int y = 0; y < BAR_Y - 8; y++) {
        const uint8_t* row = img + (size_t)y * g_panel_w;
        for (int x = 0; x < g_panel_w; x++) if (row[x] < 0xe0) { if (x < minx) minx = x; if (x > maxx) maxx = x; }
    }
    if (maxx - minx >= 100) { bar_x0 = minx; bar_w = maxx - minx + 1; }
}
static void splash_bar(uint8_t* fb, unsigned stride, int pct) {
    if (pct < 0) pct = 0;
    if (pct > 100) pct = 100;
    const int r = 3, mid = BAR_H / 2;                 /* the thick bar is 2 * r = 6 px high, centred between rows mid - 1 and mid */
    int fill = bar_w * pct / 100;
    for (int y = 0; y < BAR_H; y++) {
        uint8_t* row = fb + (size_t)(BAR_Y + y) * stride + (size_t)bar_x0 * fb_bytes;
        int cy2 = 2 * y + 1 - 2 * mid;                /* doubled distance from the centre line, so that the circle is exact */
        for (int x = 0; x < bar_w; x++) {
            int black = 0;
            if (x >= fill && (y == mid - 1 || y == mid)) black = 1;           /* not filled yet: a thin line */
            else if (x < fill && cy2 >= -2 * r + 1 && cy2 <= 2 * r - 1) {      /* filled: a thick bar with round ends */
                int ex = x < r ? x - r : (x >= fill - r ? x - (fill - r) : 0);
                int cx2 = 2 * ex + 1;
                black = (x >= r && x < fill - r) || (cx2 * cx2 + cy2 * cy2 <= 4 * r * r);
            }
            put_gray(row, x, black ? 0x00 : 0xff);
        }
    }
}
static uint8_t* load_raw(const char* path) {        /* a full-screen 8-bit gray picture of exactly the panel size, else it is ignored */
    if (!path) return NULL;
    struct stat st; if (stat(path, &st) != 0 || (unsigned long long)st.st_size != (unsigned long long)g_panel_w * g_panel_h) return NULL;
    FILE* f = fopen(path, "rb"); if (!f) return NULL;
    uint8_t* b = malloc((size_t)g_panel_w * g_panel_h);
    if (b && fread(b, 1, (size_t)g_panel_w * g_panel_h, f) != (size_t)g_panel_w * g_panel_h) { free(b); b = NULL; }
    fclose(f); return b;
}
static int read_int_file(const char* path, int def) {
    FILE* f = fopen(path, "r"); if (!f) return def;
    int v = def; if (fscanf(f, "%d", &v) != 1) v = def;
    fclose(f); return v;
}

static const char* local_mode(const struct conf* c) { return c->local_wfm == WFM_A2 ? "A2" : "DU"; }
/* Larger partial updates and the clean-up after fast ones (KOReader: "ui"/"partial" = GC16 without flash). GL16 made gray content
 * look washed out between the full flashes, so GC16 is the default. */
static const char* page_mode(const struct conf* c) { return c->page_wfm == 2 ? "REAGL" : c->page_wfm ? "GL16" : "GC16"; }

static long long mtime_of(const char* path) {
    struct stat st;
    return stat(path, &st) == 0 ? (long long)st.st_mtim.tv_sec * 1000 + st.st_mtim.tv_nsec / 1000000 : 0;
}
/* 1 when the file was touched less than ms ago (wall clock) */
static int fresh_file(const char* path, long long ms) {
    long long m = mtime_of(path);
    struct timespec ts; clock_gettime(CLOCK_REALTIME, &ts);
    long long now = (long long)ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
    return m && now - m >= -200 && now - m <= ms;
}
/* ctl/conf changes: KindleHome replaces the file by a rename, so a new inode (or size) also counts, not only a new mtime */
static long long conf_stamp(const char* path) {
    struct stat st;
    if (stat(path, &st) != 0) return 0;
    return ((long long)st.st_mtim.tv_sec * 1000 + st.st_mtim.tv_nsec / 1000000) ^ ((long long)st.st_ino << 40) ^ ((long long)st.st_size << 20);
}

int main(int argc, char** argv) {
    const char* dir = "/var/tmp/kdisp";
    const char* persist = NULL;
    const char* shots_dir = NULL;
    const char* splash_file = NULL; const char* exit_file = NULL; const char* restart_file = NULL;
    struct conf cf; conf_defaults(&cf);
    unsigned min_gap_ms = 100;
    int a_settle = -1;
    for (int i = 1; i < argc; i++) {
        if (!strcmp(argv[i], "--dir") && i + 1 < argc) dir = argv[++i];
        else if (!strcmp(argv[i], "--fbink") && i + 1 < argc) FBINK = argv[++i];
        else if (!strcmp(argv[i], "--conf") && i + 1 < argc) persist = argv[++i];
        else if (!strcmp(argv[i], "--shots") && i + 1 < argc) shots_dir = argv[++i];
        else if (!strcmp(argv[i], "--splash") && i + 1 < argc) splash_file = argv[++i];
        else if (!strcmp(argv[i], "--exitpage") && i + 1 < argc) exit_file = argv[++i];
        else if (!strcmp(argv[i], "--restartpage") && i + 1 < argc) restart_file = argv[++i];
        else if (!strcmp(argv[i], "--settle-ms") && i + 1 < argc) a_settle = atoi(argv[++i]);
        else if (!strcmp(argv[i], "--min-gap-ms") && i + 1 < argc) min_gap_ms = (unsigned)atoi(argv[++i]);
    }
    if (persist) {
        conf_load(&cf, persist);
        if (cf.conf_ver < 2) {                 /* display.conf from before 1.1.0.03: FLASH_ONLY=1 was the old default, not a choice */
            cf.flash_only = 0; cf.conf_ver = 2;
            conf_save(&cf, persist, 0644);
            printf("display.conf: old version, FLASH_ONLY reset to 0 (partial refresh)\n");
        }
    }
    if (a_settle > 0) cf.settle_ms = (unsigned)a_settle;
    build_lut(&cf);
    setvbuf(stdout, NULL, _IOLBF, 0);
    struct sigaction sa; memset(&sa, 0, sizeof sa); sa.sa_handler = on_sig;
    sigaction(SIGTERM, &sa, NULL); sigaction(SIGINT, &sa, NULL);
    /* children (fbink) are collected by reap_fbink(): their exit status tells whether this firmware's fbink crashed */

    /* Select the backend on EVERY start; never persist a guessed device model. */
    struct fb_var_screeninfo detected_v; struct fb_fix_screeninfo detected_f;
    int detect_fd = open("/dev/fb0", O_RDONLY);
    if (detect_fd < 0 || ioctl(detect_fd, FBIOGET_VSCREENINFO, &detected_v) < 0 ||
        ioctl(detect_fd, FBIOGET_FSCREENINFO, &detected_f) < 0) {
        if (detect_fd >= 0) close(detect_fd);
        fprintf(stderr, "cannot detect framebuffer backend\n"); return 1;
    }
    close(detect_fd);
    int color_supported = display_supports_color(&detected_v, &detected_f);
    if (detected_v.bits_per_pixel != 8 && !color_supported) {
        fprintf(stderr, "unsupported framebuffer backend: %u bpp\n", detected_v.bits_per_pixel); return 1;
    }
    if (!color_supported) cf.color = 0;
    printf("display backend: %s\n", color_supported ? "kc-rgba8888" : "gray8");

    /* --- directories and the shared frame file --- */
    char path[256], ctl[256], ctlconf[300], ctlclear[300], ctlfreeze[300], ctlup[300], ctldump[300], ctlshot[300], dumpout[300];
    mkdir(dir, 0777); chmod(dir, 0777);
    snprintf(ctl, sizeof ctl, "%s/ctl", dir);
    mkdir(ctl, 0777); chmod(ctl, 0777);
    snprintf(ctlconf, sizeof ctlconf, "%s/conf", ctl);
    snprintf(ctlclear, sizeof ctlclear, "%s/clear", ctl);
    snprintf(ctlfreeze, sizeof ctlfreeze, "%s/freeze", ctl);
    snprintf(ctlup, sizeof ctlup, "%s/touchup", ctl);
    snprintf(ctldump, sizeof ctldump, "%s/dump", ctl);
    snprintf(ctlshot, sizeof ctlshot, "%s/screenshot", ctl);
    snprintf(dumpout, sizeof dumpout, "%s/screen.pgm", ctl);
    unlink(ctlclear); unlink(ctlfreeze);
    { char cap[300]; snprintf(cap,sizeof cap,"%s/color_supported",ctl);
      FILE *f=fopen(cap,"w"); if(f) { fprintf(f,"%d\n",color_supported); fclose(f); chmod(cap,0644); } }
    conf_save(&cf, ctlconf, 0666);
    long long conf_mt = conf_stamp(ctlconf);

    if (panel_detect(&g_panel_w, &g_panel_h) != 0) { fprintf(stderr, "kdispd: cannot determine the panel size (cannot read /dev/fb0)\n"); return 1; }
    printf("kdispd: panel %ux%u\n", g_panel_w, g_panel_h);
    bar_w = g_panel_w < 720 ? (int)g_panel_w : 720; bar_x0 = ((int)g_panel_w - bar_w) / 2;
    snprintf(path, sizeof path, "%s/frame", dir);
    int sfd = open(path, O_RDWR | O_CREAT | O_TRUNC, 0666);
    if (sfd < 0) { perror("open sink"); return 1; }
    fchmod(sfd, 0666);
    size_t slen = KDISP_HDR_SIZE + (size_t)g_panel_w * g_panel_h * (color_supported ? 5 : 1);
    if (ftruncate(sfd, (off_t)slen) < 0) { perror("ftruncate"); return 1; }
    uint8_t* smap = mmap(NULL, slen, PROT_READ | PROT_WRITE, MAP_SHARED, sfd, 0);
    if (smap == MAP_FAILED) { perror("mmap sink"); return 1; }
    memset(smap + KDISP_HDR_SIZE, 0xff, slen - KDISP_HDR_SIZE);
    struct kdisp_hdr* hdr = (struct kdisp_hdr*)smap;
    memset(hdr, 0, sizeof *hdr);
    hdr->version = KDISP_VERSION; hdr->width = g_panel_w; hdr->height = g_panel_h;
    hdr->flags = color_supported ? KDISP_FLAG_RGBA_PLANE : 0;
    rgb_plane = color_supported ? smap + KDISP_HDR_SIZE + (size_t)g_panel_w*g_panel_h : NULL;
    hdr->stride = g_panel_w; hdr->data_offset = KDISP_HDR_SIZE;
    __atomic_store_n(&hdr->magic, KDISP_MAGIC, __ATOMIC_RELEASE);
    const uint8_t* sdata = smap + KDISP_HDR_SIZE;

    /* --- framebuffer --- */
    uint8_t *fbbase = NULL, *fb; size_t fblen; unsigned fbstride; int fbfd;
    {
        struct fb_var_screeninfo v; struct fb_fix_screeninfo f;
        fbfd = open("/dev/fb0", O_RDWR);
        if (fbfd < 0 || ioctl(fbfd, FBIOGET_VSCREENINFO, &v) < 0 || ioctl(fbfd, FBIOGET_FSCREENINFO, &f) < 0) {
            perror("fb0"); return 1;
        }
        if ((v.bits_per_pixel != 8 && v.bits_per_pixel != 32) || v.xres != g_panel_w || v.yres != g_panel_h) {
            fprintf(stderr, "unexpected fb: %ux%u %ubpp\n", v.xres, v.yres, v.bits_per_pixel); return 1;
        }
        fb_bytes = v.bits_per_pixel / 8;
        if (fb_bytes == 4 && (v.red.offset != 0 || v.green.offset != 8 || v.blue.offset != 16 || v.red.length != 8 || v.green.length != 8 || v.blue.length != 8)) {
            fprintf(stderr, "unsupported 32bpp channel layout\n"); return 1;
        }
        if ((uint64_t)(v.xoffset + v.xres) * fb_bytes > f.line_length ||
            (uint64_t)(v.yoffset + v.yres) * f.line_length > f.smem_len) {
            fprintf(stderr, "invalid framebuffer bounds\n"); return 1;
        }
        fblen = f.smem_len;
        fbbase = mmap(NULL, fblen, PROT_READ | PROT_WRITE, MAP_SHARED, fbfd, 0);
        fbstride = f.line_length;
        fb = fbbase + (size_t)v.yoffset * fbstride + (size_t)v.xoffset * fb_bytes;
    }
    if (fbbase == MAP_FAILED || !fbbase) { perror("mmap fb"); return 1; }
    {
        g_fbfd = fbfd;
        uint32_t fl = MTK_FAST_MODE_ON;
        if (ioctl(fbfd, MTK_SET_UPDATE_FLAGS, &fl) != 0) { printf("refresh: fast mode flag not set (%s)\n", strerror(errno)); fflush(stdout); }
    }
    uint8_t* backup = malloc((size_t)fbstride * g_panel_h);
    if (!backup) return 1;
    for (unsigned y = 0; y < g_panel_h; ++y) memcpy(backup + (size_t)y * fbstride, fb + (size_t)y * fbstride, (size_t)g_panel_w * fb_bytes);
    printf("kdispd up: sink %s, fb stride %u, fbink %s, contrast %.1f dither %d local %s invert %d\n",
           path, fbstride, FBINK, cf.contrast, cf.dither, wfm_name(cf.local_wfm), cf.invert);

    const struct rect full = { 0, 0, g_panel_w, g_panel_h };
    uint32_t acked = 0;
    double last_spawn = 0, last_refresh = 0, last_ctl = 0, last_upcheck = 0, last_up = -10000;
    long long up_mt = mtime_of(ctlup);
    struct rect pend = {0, 0, 0, 0}; int settle_needed = 0;
    /* The Kindle UI is paused with its page still in the framebuffer, and the source buffer starts out white. Android's first frame
     * (a white home screen) is then identical to it, so only the dark parts (text, borders) would ever be written and the Kindle's
     * library page would stay visible around them. Start with one flashing full-screen refresh so the panel matches the buffer. */
    int frozen = 0, repaint = 2;
    uint8_t* splash = NULL; int splash_active = 0, splash_pct = -1; double splash_t0 = 0, last_prog = 0;
    char ctlturn[300]; snprintf(ctlturn, sizeof ctlturn, "%s/turn", ctl);
    char ctlnoswipe[300]; snprintf(ctlnoswipe, sizeof ctlnoswipe, "%s/noswipe", ctl);
    char ctlkbd[300]; snprintf(ctlkbd, sizeof ctlkbd, "%s/kbd", ctl);
    unlink(ctlkbd);
    char ctlrefresh[300]; snprintf(ctlrefresh, sizeof ctlrefresh, "%s/refresh", ctl);
    unlink(ctlrefresh);
    char ctlwhite[300]; snprintf(ctlwhite, sizeof ctlwhite, "%s/white", ctl);
    unlink(ctlwhite);
    char ctlnoanim[300]; snprintf(ctlnoanim, sizeof ctlnoanim, "%s/noanim", ctl);
    unlink(ctlnoanim);
    char ctlprog[300]; snprintf(ctlprog, sizeof ctlprog, "%s/progress", ctl);
    unlink(ctlprog);
    /* "returning to Kindle" page: embrith.sh creates ctl/exiting as the first step of leaving Android. The page is drawn at once and stays
     * on the panel (the Kindle UI repaints it when it is back); the old Kindle page is NOT restored over it. */
    uint8_t* exitpage = load_raw(exit_file); int exiting = 0; double last_exitcheck = 0;
    char ctlexiting[300]; snprintf(ctlexiting, sizeof ctlexiting, "%s/exiting", ctl);
    unlink(ctlexiting);
    /* "restarting" page: ctl/restarting is created when a restart is requested from Android. Same handling as the exit page. */
    uint8_t* restartpage = load_raw(restart_file);
    char ctlrestarting[300]; snprintf(ctlrestarting, sizeof ctlrestarting, "%s/restarting", ctl);
    unlink(ctlrestarting);
    splash = load_raw(splash_file);
    if (splash) {
        for (unsigned y = 0; y < g_panel_h; y++) copy_gray(fb + (size_t)y * fbstride, splash + (size_t)y * g_panel_w, g_panel_w);
        splash_bounds(splash);
        splash_bar(fb, fbstride, 0);
        refresh_region(&full, "GC16", 1);
        splash_active = 1; splash_pct = 0; splash_t0 = now_ms(); last_prog = splash_t0; repaint = 0;
        printf("splash up: %s\n", splash_file); fflush(stdout);
    }
    unsigned n_fast = 0, n_slow = 0, n_settle = 0, n_local = 0, n_clear = 0, n_flash = 0, retries = 0;
    unsigned pages = 0; double last_page = -10000; int fo_pending = 0; double last_flash = -10000;
    int ov_open = 0; struct rect ov_bars[MAXBANDS + 1]; int ov_nbars = 0;
    unsigned kbd_h = 0; double kbd_t = -10000;
    struct rect torn_r = {0, 0, 0, 0}; int torn_have = 0;
    g_prev = calloc((size_t)g_panel_w * g_panel_h, 1);

    color_enabled = cf.color;
    while (!g_stop) {
        double t = now_ms();
        if (reap_fbink() && !repaint) repaint = 2;          /* a refresh was lost: the next variant starts with a full flashing redraw */
        if ((exitpage || restartpage) && !exiting && t - last_exitcheck >= 100) {
            last_exitcheck = t;
            const uint8_t* pg = NULL;
            if (exitpage && access(ctlexiting, F_OK) == 0) pg = exitpage;
            else if (restartpage && access(ctlrestarting, F_OK) == 0) pg = restartpage;
            if (pg) {
                exiting = 1; splash_active = 0;
                for (unsigned y = 0; y < g_panel_h; y++) copy_gray(fb + (size_t)y * fbstride, pg + (size_t)y * g_panel_w, g_panel_w);
                refresh_region(&full, "GC16", 1);
                printf("exit page shown\n"); fflush(stdout);
            }
        }
        if (exiting) { usleep(30000); continue; }                 /* nothing from Android is drawn any more */

        /* ---- control directory, polled every 300 ms ---- */
        if (t - last_ctl >= 300) {
            last_ctl = t;
            long long m = conf_stamp(ctlconf);
            if (m && m != conf_mt) {
                struct conf n = cf; conf_load(&n, ctlconf);
                if (!color_supported) n.color = 0;
                int look = n.color != cf.color || n.contrast != cf.contrast || n.invert != cf.invert || n.dither != cf.dither || (g_white && n.white_at != cf.white_at);
                cf = n; color_enabled = cf.color; conf_mt = m; build_lut(&cf);
                if (persist) conf_save(&cf, persist, 0644);
                if (look) repaint = 1;               /* the screen has to be redrawn with the new table */
                printf("conf: contrast %.1f dither %d local %s invert %d gestures %d flash-every %u flash-only %d\n",
                       cf.contrast, cf.dither, wfm_name(cf.local_wfm), cf.invert, cf.gestures, cf.flash_every, cf.flash_only);
            }
            {   /* ctl/white: written by the launcher when a chosen app comes to the front (1) or goes away (0) */
                int w = 0; FILE* wf = fopen(ctlwhite, "r");
                if (wf) { int ch = fgetc(wf); w = ch == '1'; fclose(wf); }
                if (w != g_white) { g_white = w; build_lut(&cf); repaint = 1; printf("white background: %s (from %u)\n", w ? "on" : "off", cf.white_at); }
            }
            {   /* ctl/noanim: written by the launcher, 1 while an app that has the page turn animation switched off is in front */
                int na = 0; FILE* nf = fopen(ctlnoanim, "r");
                if (nf) { int ch = fgetc(nf); na = ch == '1'; fclose(nf); }
                if (na != g_noanim) { g_noanim = na; printf("page animation: %s\n", na ? "off for this app" : "on"); }
            }
            if (access(ctlclear, F_OK) == 0) { unlink(ctlclear); repaint = 2; }
            if (access(ctldump, F_OK) == 0) {                 /* "embrith.sh shot": save what Android has drawn so far */
                unlink(ctldump);
                FILE* df = fopen(dumpout, "wb");
                if (df) {
                    fprintf(df, "P5\n%d %d\n255\n", g_panel_w, g_panel_h);
                    fwrite(sdata, 1, (size_t)g_panel_w * g_panel_h, df);
                    fclose(df); chmod(dumpout, 0644);
                    printf("dump: %s (frame counter %u)\n", dumpout, hdr->frame_counter);
                }
            }
            if (access(ctlshot, F_OK) == 0) {                 /* ctl/screenshot: save Android's last frame as a PNG into the --shots directory */
                unlink(ctlshot);
                if (shots_dir) {
                    char out[400], ts[32]; time_t tt = time(NULL); struct tm tmv; localtime_r(&tt, &tmv);
                    strftime(ts, sizeof ts, "%Y%m%d_%H%M%S", &tmv);
                    mkdir(shots_dir, 0777);
                    snprintf(out, sizeof out, "%s/screenshot_%s.png", shots_dir, ts);
                    printf("screenshot: %s %s\n", out, save_png(out, sdata, g_panel_w, g_panel_h) == 0 ? "saved" : "FAILED");
                    repaint = 2;                          /* full flashing redraw, so that the user sees that a screenshot was taken */
                }
            }
            int fz = access(ctlfreeze, F_OK) == 0;
            if (fz != frozen) { frozen = fz; if (!fz && !repaint) repaint = 1; printf("freeze %d\n", fz); }
        }

        /* finger lifted (kgest touches ctl/touchup): the picture is about to be final, do not wait for the long idle time */
        if (t - last_upcheck >= 25) {
            last_upcheck = t;
            long long m = mtime_of(ctlup);
            if (m && m != up_mt) { up_mt = m; last_up = t; }
        }

        uint32_t seq = __atomic_load_n(&hdr->seq, __ATOMIC_ACQUIRE);

        if (splash_active) {                       /* Android's frames are acknowledged but not shown until the splash is over */
            if (t - last_prog >= 250) {
                last_prog = t;
                int pct = read_int_file(ctlprog, -1);
                if (pct >= 100 || t - splash_t0 > 240000) { splash_active = 0; repaint = 2; printf("splash done\n"); fflush(stdout); }
                else if (pct >= 0 && pct != splash_pct) {
                    splash_pct = pct; splash_bar(fb, fbstride, pct);
                    struct rect br = { bar_x0 - 4, BAR_Y - 4, bar_w + 8, BAR_H + 8 };
                    refresh_region(&br, "DU", 0);
                }
            }
            if (splash_active) {
                if (!(seq & 1)) { acked = seq; __atomic_store_n(&hdr->ack, acked, __ATOMIC_RELEASE); }
                usleep(20000); continue;
            }
        }

        /* ---- redraw everything: after a table change (grayscale refresh) or on request (flashing, removes ghosting) ---- */
        if (repaint && !frozen && !(seq & 1)) {
            blit(fb, fbstride, sdata, &full, cf.dither); track(sdata, &full);
            refresh_region(&full, "GC16", repaint == 2);
            if (repaint == 2) n_clear++;
            repaint = 0; settle_needed = 0; last_refresh = last_spawn = now_ms(); ov_open = 0;
            acked = seq; __atomic_store_n(&hdr->ack, acked, __ATOMIC_RELEASE);
            continue;
        }

        if (!(seq & 1) && seq != acked && t - last_spawn >= min_gap_ms) {
            struct rect r = { hdr->dirty_x, hdr->dirty_y, hdr->dirty_w, hdr->dirty_h };
            if (r.x >= g_panel_w || r.y >= g_panel_h || r.w == 0 || r.h == 0) { acked = seq; __atomic_store_n(&hdr->ack, acked, __ATOMIC_RELEASE); continue; }
            if (r.x + r.w > g_panel_w) r.w = g_panel_w - r.x;
            if (r.y + r.h > g_panel_h) r.h = g_panel_h - r.y;
            if (frozen) {                           /* background: draw nothing, one repaint when it is back */
                acked = seq; __atomic_store_n(&hdr->ack, acked, __ATOMIC_RELEASE);
                continue;
            }
            if (color_enabled && fb_bytes == 4) {
                blit(fb,fbstride,sdata,&r,0);
                if (__atomic_load_n(&hdr->seq,__ATOMIC_ACQUIRE)!=seq) { repaint=1; retries++; continue; }
                char hint = turn_hint(ctlturn);
                int animate = cf.page_anim && !g_noanim && !fresh_file(ctlnoswipe,1500) &&
                    (hint == 'n' || hint == 'p' || hint == 'N' || hint == 'P') &&
                    (uint64_t)r.w*r.h*100 >= (uint64_t)g_panel_w*g_panel_h*cf.ui_pct && t-last_page >= 800;
                int animated = 0;
                if (animate) {
                    unsigned direction = (hint == 'p' || hint == 'P') ? (cf.anim_dir ^ 1u) : cf.anim_dir;
                    animated = mtk_swipe_region(&r,cf.anim_steps,direction);
                    printf("KC swipe: sent=%d dir=%u steps=%u\n",animated,direction,cf.anim_steps);
                    unlink(ctlturn); last_page=t;
                }
                if (!animated) refresh_region(&r,"GC16",0);
                track(sdata,&r);
                acked=seq; __atomic_store_n(&hdr->ack,acked,__ATOMIC_RELEASE);
                last_spawn=last_refresh=t; settle_needed=0; continue;
            }
            if (cf.flash_only) {          /* compatibility mode: no partial refreshes at all, see the settle branch below */
                blit(fb, fbstride, sdata, &r, cf.dither); track(sdata, &r);
                if (__atomic_load_n(&hdr->seq, __ATOMIC_ACQUIRE) != seq) { retries++; continue; }
                acked = seq; __atomic_store_n(&hdr->ack, acked, __ATOMIC_RELEASE);
                fo_pending = 1; last_refresh = t;
                continue;
            }
            /* only the bands that really changed (plus what a torn copy may have left behind) */
            struct rect bands[MAXBANDS + 3];              /* one spare for a torn rectangle, two for bands split at the top of the keyboard */
            int nb = split_dirty(sdata, &r, bands);
            if (torn_have) { if (nb <= MAXBANDS) bands[nb++] = torn_r; else rect_union(&bands[nb - 1], &torn_r); torn_have = 0; }
            if (nb == 0) { acked = seq; __atomic_store_n(&hdr->ack, acked, __ATOMIC_RELEASE); continue; }
            r = bands[0];
            for (int i = 1; i < nb; i++) rect_union(&r, &bands[i]);
            /* Refresh classes, decided by the largest changed band and not by the bounding box (a top bar plus a bottom bar span the
             * whole screen but are two small updates):
             *   local: every band is at most ui_pct of the screen (bars, buttons, hints): DU / A2 plus the settle refresh
             *   ui:    a big band that was declared as UI (ctl/turn "u" or "c", ctl/noswipe) or that belongs to an open overlay: gray waveform, no animation, no page count
             *   page:  a big band otherwise: page waveform; the hardware swipe only when ctl/turn says n or p
             * Overlay: from the moment a bar or an edge panel shows up (or after a middle tap) until the picture is back to what it was
             * before, everything is ui or local. A page turn seen meanwhile ends it: one the system UI declared (N, P), or a touch
             * guess (n, p) with a big band that leaves nothing of the old page and does not sit at one of the app's bars (a panel that
             * opens leaves the rest of the page, or grows out of a bar). */
            unsigned long long maxband = 0, maxflat = 0, screen = (unsigned long long)g_panel_w * g_panel_h;
            int edge_any = 0, flat_big = 0, iflat = -1;
            for (int i = 0; i < nb; i++) {
                unsigned long long a = (unsigned long long)bands[i].w * bands[i].h;
                if (a > maxband) maxband = a;
                if (edge_panel(&bands[i])) edge_any = 1;
                else { if (a > maxflat) { maxflat = a; iflat = i; } if (a * 100 >= (unsigned long long)cf.ui_pct * screen) flat_big = 1; }
            }
            int big = maxband * 100 >= (unsigned long long)cf.ui_pct * screen;
            char th = turn_hint(ctlturn);
            int forced = th == 'N' || th == 'P';
            if (forced) th = th == 'N' ? 'n' : 'p';
            /* The soft keyboard declares itself (ctl/kbd): what changes inside it is a local update whatever its size, and a touch on a
             * key is never a page turn. Its last frame (the keyboard going away) can come after the file is gone: keep it for a second. */
            { unsigned h = kbd_height(ctlkbd); if (h) { kbd_h = h; kbd_t = t; } else if (t - kbd_t > 1000) kbd_h = 0; }
            int kb_on = kbd_h > 0 && !forced;
            unsigned kb_y = kb_on ? g_panel_h - kbd_h : g_panel_h;
            struct decl decls[MAXDECL]; int nd = read_decls(ctlrefresh, decls);
            int ov_was = ov_open, ov_enter = 0, ov_esc = 0;
            if (ov_was) {
                if (forced) { ov_open = 0; ov_esc = 1; printf("overlay: closed by a declared page turn\n"); }
                else if (same_picture(sdata, g_snap, (unsigned long)g_panel_w * g_panel_h * OV_TOL_PM / 1000)) { ov_open = 0; printf("overlay: closed\n"); }
                else if (!kb_on && (th == 'n' || th == 'p') && maxflat * 100 >= (unsigned long long)ESC_PCT * screen) {
                    unsigned kept = kept_rows(bands, nb);
                    int bar = iflat >= 0 && at_one_bar(&bands[iflat], ov_bars, ov_nbars);
                    if (kept * 100 < g_panel_h * KEEP_PCT && !bar) { ov_open = 0; ov_esc = 1; }
                    printf("overlay: %s hint=%c kept=%u bar=%d\n", ov_esc ? "escape" : "stay", th, kept, bar);
                }
            } else if (0 && !forced && !kb_on && (th == 'c' || (edge_any && !flat_big))) {   /* test: overlay never opens */
                if (!g_snap) g_snap = malloc((size_t)g_panel_w * g_panel_h);
                if (g_snap && g_prev) { memcpy(g_snap, g_prev, (size_t)g_panel_w * g_panel_h); ov_open = 1; ov_enter = 1; ov_nbars = 0;
                    for (int i = 0; i < nb; i++) if (bands[i].w * 2 >= g_panel_w || bands[i].h * 2 >= g_panel_h) ov_bars[ov_nbars++] = bands[i];   /* the bars (or the panel) this overlay starts with */
                    printf("overlay: open hint=%c edge=%d\n", th ? th : '-', edge_any); }
            }
            int in_ov = (ov_was && !ov_esc) || ov_enter;
            int declared_ui = th == 'u' || th == 'c' || in_ov || kb_on || fresh_file(ctlnoswipe, 1500) || (nd > 0 && !(th == 'n' || th == 'p'));
            int page = big && !declared_ui && (th || (t - last_refresh) >= cf.fast_ms);   /* no hint and refreshing fast = scrolling: not a page */
            if (big) printf("class: %s maxband=%llu%% nb=%d hint=%c overlay=%d kbd=%u\n", page ? "page" : declared_ui ? "ui" : "scroll", maxband * 100 / screen, nb, th ? th : '-', in_ov, kb_on ? kbd_h : 0);
            if (page) {
                int kind_change = 0;   /* picture page <-> text page: the swipe animation is skipped, the new page is refreshed in place */
                if (cf.anim_img_pct > 0 && (th == 'n' || th == 'p') && g_prev) {
                    int before = mid_pct(g_prev, &r), after = mid_pct(sdata, &r);
                    kind_change = (before >= (int)cf.anim_img_pct) != (after >= (int)cf.anim_img_pct);
                    printf("kind: mid=%d%%->%d%% change=%d\n", before, after, kind_change);
                }
                int turned = (th == 'n' || th == 'p') && cf.page_anim && !g_noanim && !kind_change;
                int pg = t - last_page >= 800;       /* a page turn can arrive as several frames: count at most one per 800 ms */
                int flash = (!cf.page_anim || g_noanim) && cf.flash_every > 0 && pg && pages + 1 >= cf.flash_every;
                const struct rect* pr = flash ? &full : &r;
                blit(fb, fbstride, sdata, pr, cf.dither); track(sdata, pr);
                if (__atomic_load_n(&hdr->seq, __ATOMIC_ACQUIRE) != seq) { retries++; torn_r = r; torn_have = 1; continue; }
                acked = seq; __atomic_store_n(&hdr->ack, acked, __ATOMIC_RELEASE);
                if (pg) { last_page = t; pages = flash ? 0 : pages + 1; }
                last_spawn = last_refresh = t; settle_needed = 0;
                if (turned) {
                    unsigned sdir = th == 'p' ? (cf.anim_dir ^ 1u) : cf.anim_dir;
                    unlink(ctlturn);                 /* one swipe per gesture */
                    if (mtk_swipe_region(&r, cf.anim_steps, sdir)) { printf("swipe: %ux%u+%u+%u dir=%u\n", r.w, r.h, r.x, r.y, sdir); continue; }
                }
                if (flash) { refresh_region(&full, "GC16", 1); n_flash++; }
                else { refresh_region(&r, page_mode(&cf), 0); n_slow++; }
                continue;
            }
            int fast = (t - last_refresh) < ((th == 'u' && big && !kb_on) ? cf.scroll_ms : cf.fast_ms);   /* a swipe in a UI screen (settings): every frame is a scroll step, not a new gray page */
            /* scrolling, or a big change of a UI screen (not an overlay): one refresh for the whole changed area instead of one update per band.
             * Several gray updates queue up in the driver and take as many times as long as one. */
            if ((fast || (declared_ui && !in_ov && big)) && nb > 1 && !kb_on) { bands[0] = r; nb = 1; }
            for (int i = 0, n0 = nb; kb_on && i < n0 && nb < MAXBANDS + 3; i++)     /* a band across the top of the keyboard: one band above it, one inside */
                if (bands[i].y + 8 < kb_y && bands[i].y + bands[i].h > kb_y + 8) {
                    bands[nb] = bands[i]; bands[nb].y = kb_y; bands[nb].h = bands[i].y + bands[i].h - kb_y;
                    bands[i].h = kb_y - bands[i].y; nb++;
                }
            int grayb[MAXBANDS + 3], nosettle[MAXBANDS + 3], bflash[MAXBANDS + 3];
            const char* bmode[MAXBANDS + 3];
            for (int i = 0; i < nb; i++) {
                unsigned long area = (unsigned long)bands[i].w * bands[i].h, chg, gchg;
                count_changed(sdata, &bands[i], &chg, &gchg);
                /* only a band up to ui_pct of the screen can be local; a big band here was declared UI or is scrolling */
                int sparse = chg * 5 <= area;
                /* a picture (book covers loading one after the other) cannot go out in DU even inside a burst: it would show in black and white
                 * until the settle refresh redraws it in gray. Keys of the soft keyboard stay DU. */
                int fst = fast;
                if (fst && !kb_on && gray_pm(sdata, &bands[i]) >= PIC_FAST_PM) fst = 0;
                int local = !fst && cf.local_wfm != WFM_AUTO && area * 100 <= (unsigned long)cf.ui_pct * g_panel_w * g_panel_h;
                unsigned gpm = fst ? 0 : gray_pm(sdata, &bands[i]);
                unsigned gpc = fst ? 0 : (unsigned)(gchg * 1000 / area);   /* gray share of the changed pixels only */
                /* gray in the band that DU would force to black or white: unchanged mid-gray, or any flat gray area (a light or dark
                 * gray background is outside the mid-gray range of gray_pm and showed up as a black or white rectangle) */
                int bgray = (gpm > gpc && gpm - gpc >= BG_GRAY_PM) || (!fst && flat_gray_pm(sdata, &bands[i]) >= FLAT_GRAY_PM);
                /* small band, not fast: with gray (text edges) it goes out once in the gray waveform, without gray DU is lossless and
                 * needs no clean-up. A band with gray in what changed (also a sparse one: text edges are gray) goes out in the gray waveform,
                 * DU does not drive pixels that turn gray. Fast bands keep DU plus the clean-up when it is quiet. */
                int kb = kb_on && bands[i].y + 8 >= kb_y;        /* inside the keyboard: always the black/white waveform, grays are cleaned up when it is quiet */
                grayb[i] = fst || kb ? 0 : (!local || bgray || gpc >= 5);
                int only_changed = (local || (kb && !fst)) && !bgray && (gpc < 5 || sparse);
                nosettle[i] = only_changed && gpc < 5;
                bmode[i] = grayb[i] ? page_mode(&cf) : local_mode(&cf); bflash[i] = 0;
                int ddith = 0, dm = nd ? decl_for(decls, nd, &bands[i], &ddith) : -1;
                if (dm >= 0) {      /* declared by the app: the pixel statistics above no longer decide this band */
                    static const char* const dwfm[] = { "A2", "DU", "GL16", "GC16", "GL16", "GC16", "GC16" };
                    bmode[i] = dwfm[dm]; bflash[i] = dm >= RM_FLASHUI;
                    grayb[i] = dm >= RM_UI; nosettle[i] = dm == RM_A2;
                    if (dm >= RM_UI) only_changed = 0;
                    printf("refresh decl: mode=%d band=%ux%u+%u+%u dither=%d\n", dm, bands[i].w, bands[i].h, bands[i].x, bands[i].y, ddith);
                }
                /* dithering only for picture-like content, it makes text edges noisy */
                if (only_changed) blit_changed(fb, fbstride, sdata, &bands[i]);
                else blit(fb, fbstride, sdata, &bands[i], grayb[i] && cf.dither && (gpm >= 500 || ddith));                track(sdata, &bands[i]);
            }
            if (__atomic_load_n(&hdr->seq, __ATOMIC_ACQUIRE) != seq) { retries++; torn_r = r; torn_have = 1; continue; }  /* torn: copy again */
            acked = seq;
            __atomic_store_n(&hdr->ack, acked, __ATOMIC_RELEASE);

            for (int i = 0; i < nb; i++) {
                refresh_region(&bands[i], bmode[i], bflash[i]);
                if (!grayb[i]) {                    /* black/white waveform leaves ghosts: clean up when it is quiet */
                    if (fast) n_fast++; else n_local++;
                    if (nosettle[i]) continue;
                    if (settle_needed) rect_union(&pend, &bands[i]); else { pend = bands[i]; settle_needed = 1; }
                } else n_slow++;
            }
            last_spawn = t; last_refresh = t;
        } else if (fo_pending && !frozen && seq == acked && t - last_flash > 900 &&
                   (t - last_refresh > cf.settle_ms || (t - last_up < 1500 && t - last_up > 40 && t - last_refresh > 90))) {
            blit(fb, fbstride, sdata, &full, cf.dither); track(sdata, &full);
            refresh_region(&full, "GC16", 1);
            fo_pending = 0; n_flash++; last_flash = t; last_refresh = t; last_spawn = t;
        } else if (settle_needed && !frozen && seq == acked &&
                   (t - last_refresh > cf.settle_ms || (t - last_up < 1500 && t - last_up > 40 && t - last_refresh > 90))) {
            blit(fb, fbstride, sdata, &pend, cf.dither && gray_pm(sdata, &pend) >= 500);   /* DU / A2 dropped the grays: redraw them in full gray */
            refresh_region(&pend, page_mode(&cf), 0);
            settle_needed = 0; n_settle++; last_refresh = t; last_spawn = t;
        }
        usleep(8000);
    }

    {   /* a change made in the last moments (e.g. a gesture chosen right before "exit") must still reach display.conf */
        long long m = conf_stamp(ctlconf);
        if (persist && m && m != conf_mt) { struct conf n = cf; conf_load(&n, ctlconf); conf_save(&n, persist, 0644); printf("conf: saved on stop\n"); }
    }
    printf("kdispd stopping: fast=%u slow=%u settle=%u local=%u clear=%u flash=%u torn-retries=%u frames=%u\n",
           n_fast, n_slow, n_settle, n_local, n_clear, n_flash, retries, hdr->frame_counter);
    if (exiting) usleep(600000);                              /* the exit page is already on its way to the panel */
    else {
        for (unsigned y = 0; y < g_panel_h; ++y) memcpy(fb + (size_t)y * fbstride, backup + (size_t)y * fbstride, (size_t)g_panel_w * fb_bytes);
        refresh_region(&full, "GC16", 1);
        usleep(400000);
    }
    if (g_fbfd >= 0) { uint32_t fl = MTK_FAST_MODE_OFF; ioctl(g_fbfd, MTK_SET_UPDATE_FLAGS, &fl); }   /* give the driver back to the Kindle as KOReader does */
    munmap(fbbase, fblen); munmap(smap, slen);
    unlink(path);
    return 0;
}
