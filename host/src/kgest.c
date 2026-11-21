/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors */
/* Referenced project: KOReader, AGPL-3.0, https://github.com/koreader/koreader (COPYING in that project). Files consulted:
 *   plugins/gestures.koplugin/main.lua   names and kinds of gestures (tap corner, multiswipe, two finger)
 *   frontend/device/kindle/device.lua    frameworkStarted registered through lipc (kgest --kaf)
 *   frontend/device/devicelistener.lua   calculateGestureDelta: the change of a level by a swipe, delta = ceil(max / 2 * x^2), x = the distance as a
 *                                        fraction of 0.8 of the screen side (re-implemented in swipe_delta below, no code copied)
 * Referenced project: VeraDroid, GPL-3.0-or-later, Copyright (C) 2026 the VeraDroid contributors. File consulted:
 *   tools/vdkaf/vdkaf.c                  the same stand-in for com.lab126.kaf (frameworkStarted answered 1 through liblipc)
 */
/* kgest: touch gestures for the Android session. Runs on the Kindle next to Android and reads the touch screen in
 * parallel (it never grabs the device, Android sees every touch as well).
 *
 * Every gesture runs an ACTION that the user chooses in the menu (embrith > Personalise > Gestures); the choice is stored in ctl/conf:
 *   G_TAP2        two fingers, short tap                      default none
 *   G_HOLD2       two fingers, hold 2 s                       default none
 *   G_SWIPE2_H    two fingers, swipe left/right               default none
 *   G_SWIPE2_VL   two fingers, swipe up/down, left half       default none
 *   G_SWIPE2_VR   two fingers, swipe up/down, right half      default none
 *   (fixed)       two fingers, touch two diagonal corners at once (top left + bottom right, or top right + bottom left): screenshot
 *   G_CORNER_TR / G_CORNER_BL / G_CORNER_BR   one finger, hold 2 s in a corner   default none
 *   More gestures, in the style of KOReader (all default none):
 *   G_TAP_TL / _TR / _BL / _BR     one finger, short tap in a corner        G_DTAP_TL / _TR / _BL / _BR   double tap in a corner
 *   G_EDGE_T_L                     one finger, swipe down from the top edge, left half                  default none
 *   G_EDGE_L_UP / _L_DOWN / _R_UP / _R_DOWN   one finger, swipe up or down along the left or right edge (starts within EDGE_SIDE_PX of it)
 *   light_adjust / warm_adjust on these and on G_SWIPE2_VL / _VR change the level while the fingers move (the amount follows swipe_delta)
 *   G_PINCH_IN / G_PINCH_OUT       two fingers, pinch together / spread apart
 *   G_SWIPE2_NE / _NW / _SE / _SW  two fingers, diagonal swipe
 *   FIXED (like iOS, not configurable, always on): one finger from the top edge, right half, swipe down = status panel;
 *                                                  one finger from the bottom edge, swipe up = left half home (HOMEPAGE key), right half back (BACK key)
 *   FIXED: one finger, hold 3 s in the top left corner = leave Android (works even when GESTURES=0)
 * Actions: none clear invert back home light_toggle light_adjust warm_adjust wifi_toggle statusbar menu screenshot lock page_next page_prev light_max light_min warm_max warm_min app_switch, and the ids of the action table of the launcher (Actions.java: refresh sleep open_settings light_set light_inc light_dec warm_set warm_inc warm_dec, the rest go to ctl/act for host/ui/ctl.sh) with their argument in <key>_ARG (lock = sleep: ctl/sleep; the power key and the magnetic cover are powerd events, not read here)
 * GESTURES=0 in ctl/conf switches all the configurable gestures off. The fixed ones stay on: the top edge swipe down in the right half (status panel),
 * the bottom edge swipe up (left half home, right half back), the two diagonal corners (screenshot) and the 3 s hold in the top left corner (leave Android).
 *
 * It also creates a virtual keyboard "embrith-keys" (BACK, HOME, MENU, volume keys) through /dev/uinput; the launcher script
 * hands that device to the container. Options:
 *   --touch /dev/input/eventN   the touch screen              --dir /var/tmp/kdisp   control directory (dir/ctl)
 *   --events FILE               test: read recorded input_event structs from a file (event time is the clock)
 *   --dry                       test: do not run lipc, do not use uinput, only print what would happen        */
#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <unistd.h>
#include <sys/time.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <linux/input.h>
#include <linux/uinput.h>
#include "panel.h"

/* Newer kernel headers (and 32-bit musl with 64-bit time_t) have no "time" member in struct input_event: use the accessor macros. */
#ifndef input_event_sec
#define input_event_sec time.tv_sec
#define input_event_usec time.tv_usec
#endif

static int g_panel_w, g_panel_h;   /* the panel size, set once by panel_detect() at the start of main */
#define CORNER_PX 280
#define EXIT_MS 3000
#define HOLD_MS 2000
#define TAP_MS 600
#define EDGE_SIDE_PX 30   /* a swipe along the left or right edge must start this close to it */
#define DTAP_MS 400       /* two taps in the same corner within this time are a double tap */
#define TAP1_MS 350       /* a one-finger tap is shorter than this */
#define PINCH_PX 150      /* the distance between two fingers changes by this much */
#ifndef KEY_APPSELECT
#define KEY_APPSELECT 0x244
#endif

struct slot { int on, x, y, x0, y0; };
static struct slot sl[16];
static int cur;
static int dry, from_file;
static const char* dir = "/var/tmp/kdisp";
static int uifd = -1;
static volatile sig_atomic_t g_stop;
static void on_sig(int s) { (void)s; g_stop = 1; }

static long long wall_ms(void) { struct timeval tv; gettimeofday(&tv, NULL); return (long long)tv.tv_sec * 1000 + tv.tv_usec / 1000; }

/* ------------------------------------------------------------------ side effects */
static void ctl_path(char* out, size_t n, const char* name) { snprintf(out, n, "%s/ctl/%s", dir, name); }

/* the watchdog waits on the FIFO ctl/cmd (read -t 2): a line wakes it at once, else it looks at the request files only after up to 2 s */
static void wake_watchdog(void) {
    char cp[300]; ctl_path(cp, sizeof cp, "cmd");
    int cf = open(cp, O_WRONLY | O_NONBLOCK);        /* fails (ENXIO) when nobody reads it: then the 2 s scan finds the request */
    if (cf >= 0) { if (write(cf, "\n", 1) < 0) { } close(cf); }
}
static void touch_file(const char* name) {
    char p[300]; ctl_path(p, sizeof p, name);
    int fd = open(p, O_WRONLY | O_CREAT, 0666);
    if (fd >= 0) { fchmod(fd, 0666); close(fd); }
    wake_watchdog();
}
static int conf_int(const char* key, int def) {
    char p[300], line[160]; ctl_path(p, sizeof p, "conf");
    FILE* f = fopen(p, "r"); if (!f) return def;
    int v = def; size_t kl = strlen(key);
    while (fgets(line, sizeof line, f)) if (!strncmp(line, key, kl) && line[kl] == '=') v = atoi(line + kl + 1);
    fclose(f);
    return v;
}
static void toggle_invert(void) {
    char p[300], t[310], line[160]; ctl_path(p, sizeof p, "conf"); snprintf(t, sizeof t, "%s.k", p);
    FILE* in = fopen(p, "r"); FILE* out = fopen(t, "w");
    if (!out) { if (in) fclose(in); return; }
    int seen = 0;
    if (in) {
        while (fgets(line, sizeof line, in)) {
            if (!strncmp(line, "INVERT=", 7)) { fprintf(out, "INVERT=%d\n", atoi(line + 7) ? 0 : 1); seen = 1; }
            else fputs(line, out);
        }
        fclose(in);
    }
    if (!seen) fputs("INVERT=1\n", out);
    fclose(out); chmod(t, 0666); rename(t, p);
}
static int lipc_get(const char* svc, const char* prop) {   /* -1 when the property cannot be read */
    char cmd[200], buf[64]; snprintf(cmd, sizeof cmd, "lipc-get-prop %s %s 2>/dev/null", svc, prop);
    FILE* f = popen(cmd, "r"); if (!f) return -1;
    int v = -1; if (fgets(buf, sizeof buf, f)) v = atoi(buf);
    pclose(f); return v;
}
static void lipc_set(const char* svc, const char* prop, int val) {
    if (dry) return;
    char v[16]; snprintf(v, sizeof v, "%d", val);
    pid_t p = fork();
    if (p == 0) { int dn = open("/dev/null", O_WRONLY); if (dn >= 0) { dup2(dn, 1); dup2(dn, 2); }
                  execlp("lipc-set-prop", "lipc-set-prop", "-i", svc, prop, v, (char*)NULL); _exit(127); }
    else if (p > 0) waitpid(p, NULL, 0);
}
static void key_tap(int code) {
    if (dry || uifd < 0) return;
    struct input_event e; memset(&e, 0, sizeof e);
    e.type = EV_KEY; e.code = (unsigned short)code; e.value = 1; if (write(uifd, &e, sizeof e) < 0) return;
    e.type = EV_SYN; e.code = SYN_REPORT; e.value = 0; if (write(uifd, &e, sizeof e) < 0) return;
    usleep(40000);
    e.type = EV_KEY; e.code = (unsigned short)code; e.value = 0; if (write(uifd, &e, sizeof e) < 0) return;
    e.type = EV_SYN; e.code = SYN_REPORT; e.value = 0; if (write(uifd, &e, sizeof e) < 0) return;
}
/* a level was changed here, behind the launcher's back: ask the host to publish the status now (ctl/status_req, wake its FIFO ctl/cmd) so that an open panel or page shows it */
static void notify_status(void) {
    char p[300]; ctl_path(p, sizeof p, "status_req");
    int fd = open(p, O_WRONLY | O_CREAT | O_TRUNC, 0666);
    if (fd >= 0) { fchmod(fd, 0666); close(fd); }
    ctl_path(p, sizeof p, "cmd");
    fd = open(p, O_WRONLY | O_NONBLOCK);
    if (fd >= 0) { if (write(fd, "\n", 1) < 0) { /* nobody reads: the host scans ctl/ within 2 s */ } close(fd); }
}
static void adjust(int amber, int steps) {                  /* frontlight or warmth, 0..24 */
    const char* prop = "flIntensity";
    if (amber && lipc_get("com.lab126.powerd", "currentAmberLevel") >= 0) prop = "currentAmberLevel";
    int v = dry ? 10 : lipc_get("com.lab126.powerd", prop); if (v < 0) v = 10;
    v += steps; v = v < 0 ? 0 : v > 24 ? 24 : v;
    lipc_set("com.lab126.powerd", prop, v);
    printf("action: %s %+d -> %d\n", prop, steps, v);
    notify_status();
}
static void set_level(int amber, int v) {                  /* frontlight or warmth to a value, 0..24 */
    const char* prop = "flIntensity";
    if (amber && lipc_get("com.lab126.powerd", "currentAmberLevel") >= 0) prop = "currentAmberLevel";
    v = v < 0 ? 0 : v > 24 ? 24 : v;
    lipc_set("com.lab126.powerd", prop, v);
    printf("action: %s = %d\n", prop, v);
    notify_status();
}
static void light_toggle(void) {
    char p[300]; snprintf(p, sizeof p, "%s/flo", dir);
    int v = dry ? 0 : lipc_get("com.lab126.powerd", "flIntensity");
    if (v > 0) { FILE* f = fopen(p, "w"); if (f) { fprintf(f, "%d\n", v); fclose(f); } lipc_set("com.lab126.powerd", "flIntensity", 0); }
    else { int o = 10; FILE* f = fopen(p, "r"); if (f) { if (fscanf(f, "%d", &o) != 1) o = 10; fclose(f); } lipc_set("com.lab126.powerd", "flIntensity", o > 0 ? o : 10); }
    printf("action: frontlight toggle\n");
    notify_status();
}
static void wifi_toggle(void) {
    int c = dry ? 1 : lipc_get("com.lab126.cmd", "wirelessEnable");
    lipc_set("com.lab126.cmd", "wirelessEnable", c == 1 ? 0 : 1);
    printf("action: wifi toggle\n");
}


static void conf_str(const char* key, const char* def, char* out, size_t n) {
    char p[300], line[160]; ctl_path(p, sizeof p, "conf");
    snprintf(out, n, "%s", def);
    FILE* f = fopen(p, "r"); if (!f) return;
    size_t kl = strlen(key);
    while (fgets(line, sizeof line, f)) if (!strncmp(line, key, kl) && line[kl] == '=') {
        char* v = line + kl + 1; size_t l = strlen(v);
        while (l > 0 && (v[l - 1] == '\n' || v[l - 1] == '\r' || v[l - 1] == ' ')) v[--l] = 0;
        if (l > 0) snprintf(out, n, "%s", v);
    }
    fclose(f);
}
static void write_hint(char c);
/* ask the Kindle side to do an action of the table: one line "id [argument]" in ctl/act (host/ui/ctl.sh act_run) */
static void act_append(const char* id, const char* arg) {
    char p[300]; ctl_path(p, sizeof p, "act");
    int fd = open(p, O_WRONLY | O_CREAT | O_APPEND, 0666);
    if (fd < 0) return;
    fchmod(fd, 0666);
    char line[96]; int n = snprintf(line, sizeof line, "%s%s%s\n", id, *arg ? " " : "", arg);
    if (n > 0) { if (n >= (int)sizeof line) n = (int)sizeof line - 1; if (write(fd, line, (size_t)n) < 0) printf("action: write ctl/act failed\n"); }
    close(fd);
    wake_watchdog();
}
static int step_of(const char* s) { int n = atoi(s); return n > 0 ? n : 1; }
/* run the action that the user assigned to a gesture. steps: for the *_adjust actions (+ = up). */
static void run_action(const char* key, const char* def, int steps, int right_half, const char* why) {
    char a[48]; conf_str(key, def, a, sizeof a);
    char argkey[64], arg[16]; snprintf(argkey, sizeof argkey, "%s_ARG", key); conf_str(argkey, "", arg, sizeof arg);
    printf("action: %s (%s -> %s)\n", a, why, key);
    if (!strcmp(a, "none")) return;
    else if (!strcmp(a, "clear")) touch_file("clear");
    else if (!strcmp(a, "invert")) toggle_invert();
    else if (!strcmp(a, "back")) key_tap(KEY_BACK);
    else if (!strcmp(a, "home")) key_tap(KEY_HOMEPAGE);
    else if (!strcmp(a, "light_toggle")) light_toggle();
    else if (!strcmp(a, "light_adjust")) adjust(0, steps);
    else if (!strcmp(a, "warm_adjust")) adjust(1, steps);
    else if (!strcmp(a, "wifi_toggle")) wifi_toggle();
    else if (!strcmp(a, "statusbar")) touch_file("statusbar");
    else if (!strcmp(a, "menu")) touch_file("menu");
    else if (!strcmp(a, "screenshot")) touch_file("screenshot");   /* kdispd saves the frame as PNG into /mnt/us/screenshots */
    else if (!strcmp(a, "page_next")) { write_hint('n'); key_tap(KEY_PAGEDOWN); }
    else if (!strcmp(a, "page_prev")) { write_hint('p'); key_tap(KEY_PAGEUP); }
    else if (!strcmp(a, "light_max")) set_level(0, 24);
    else if (!strcmp(a, "light_min")) set_level(0, 0);
    else if (!strcmp(a, "warm_max")) set_level(1, 24);
    else if (!strcmp(a, "warm_min")) set_level(1, 0);
    else if (!strcmp(a, "app_switch")) key_tap(KEY_APPSELECT);   /* recent apps */
    else if (!strcmp(a, "lock") || !strcmp(a, "sleep")) touch_file("sleep");          /* 休眠: embrith.sh asks powerd, like the power button */
    else if (!strcmp(a, "refresh")) touch_file("clear");
    else if (!strcmp(a, "open_settings")) touch_file("menu");
    else if (!strcmp(a, "light_set")) set_level(0, atoi(arg));
    else if (!strcmp(a, "warm_set")) set_level(1, atoi(arg));
    else if (!strcmp(a, "light_inc")) adjust(0, step_of(arg));
    else if (!strcmp(a, "light_dec")) adjust(0, -step_of(arg));
    else if (!strcmp(a, "warm_inc")) adjust(1, step_of(arg));
    else if (!strcmp(a, "warm_dec")) adjust(1, -step_of(arg));
    else act_append(a, arg);          /* the other actions of the table: wifi_on wifi_off bt_toggle restart_app reboot usb_eject check_update exit_app */
    (void)right_half;
}

/* ------------------------------------------------------------------ gesture state machine */
static int in_session, maxf, fired, nfing;
static long long t_start, t2;
static int cx0, cy0, lcx, lcy, maxtravel;
static int has2, diag2;
static int d0, dl;                              /* the distance between two fingers: when the second one came down, and now */
/* live steps: a swipe bound to light_adjust / warm_adjust changes the level while the fingers move (the amount: swipe_delta) */
#define LIVE_START_PX 60   /* the swipe becomes live (and gives the first level) after this distance */
static int live_on, live_mode, live_no, live_base, live_last;   /* live_mode: 1 one finger along an edge, 2 two fingers; live_no: bits of the checks that found another action (1 up, 2 down, 4 two fingers) */
static const char* live_prop;
static int last_corner = -1;                    /* corner of the last one-finger tap (0 TL, 1 TR, 2 BL, 3 BR), -1 none */
static long long last_tap_ms;
static int side_l, side_r;                      /* set at the start of a touch: an action is bound to a swipe along the left / right edge */
static int top_l;                               /* set at the start of a touch: an action is bound to G_EDGE_T_L (swipe down from the top edge, left half) */

static int isqrt(long v) { long r = 0; while ((r + 1) * (r + 1) <= v) r++; return (int)r; }
static int pair_dist(void) {
    int xs[2] = { 0, 0 }, ys[2] = { 0, 0 }, k = 0;
    for (int i = 0; i < 16 && k < 2; i++) if (sl[i].on) { xs[k] = sl[i].x; ys[k] = sl[i].y; k++; }
    if (k < 2) return 0;
    return isqrt((long)(xs[0] - xs[1]) * (xs[0] - xs[1]) + (long)(ys[0] - ys[1]) * (ys[0] - ys[1]));
}
/* a gesture slot has an action (not none)? */
static int bound(const char* key) { char a[48]; conf_str(key, "none", a, sizeof a); return strcmp(a, "none") != 0; }

/* The change of a level (0..24) by a vertical swipe of dy pixels (dy < 0 = up = +), after KOReader (devicelistener.lua, calculateGestureDelta):
 * x = the distance as a fraction of 0.8 of the screen height, at most 1; the change is ceil(12 * x^2), so at most half of the range: fine at
 * the start of a swipe, quick when it is long, and any swipe that counts changes the level by at least 1. */
#define SWIPE_LEVELS 12
static int swipe_delta(int dy) {
    long long a = dy < 0 ? -dy : dy, full = (long long)g_panel_h * 8 / 10;
    if (full < 1) full = 1;
    if (a > full) a = full;
    int d = (int)((SWIPE_LEVELS * a * a + full * full - 1) / (full * full));
    return dy < 0 ? d : -d;
}
static int travel_of(const struct slot* s) { int dx = s->x - s->x0, dy = s->y - s->y0; return (int)(dx < 0 ? -dx : dx) + (int)(dy < 0 ? -dy : dy); }


static void hold_check(long long now) {
    if (!in_session || fired) return;
    if (maxf == 1 && nfing == 1 && maxtravel < 60 && now - t_start >= EXIT_MS) {      /* the way out: always available (Android shows the power menu; if it does not answer the watchdog exits) */
        const struct slot* s = NULL; for (int i = 0; i < 16; i++) if (sl[i].on) s = &sl[i];
        if (s && s->x0 < CORNER_PX && s->y0 < CORNER_PX) { fired = 1; printf("action: power menu\n"); touch_file("power_menu"); return; }
    }
    if (!conf_int("GESTURES", 1)) return;
    if (maxf >= 2 && nfing == 2 && has2 && maxtravel < 60 && now - t2 >= HOLD_MS) {
        fired = 1; run_action("G_HOLD2", "none", 0, 0, "two-finger hold"); return;
    }
    if (maxf == 1 && nfing == 1 && maxtravel < 60 && now - t_start >= HOLD_MS) {
        const struct slot* s = NULL; for (int i = 0; i < 16; i++) if (sl[i].on) s = &sl[i];
        if (!s) return;
        int l = s->x0 < CORNER_PX, r = s->x0 >= g_panel_w - CORNER_PX, t = s->y0 < CORNER_PX, b = s->y0 >= g_panel_h - CORNER_PX;
        if (!(l || r) || !(t || b)) return;
        if (t && l) return;                                 /* top left is the exit corner (3 s), not a 2 s action */
        fired = 1;
        if (t && r) run_action("G_CORNER_TR", "none", 0, 0, "corner top right");
        else if (b && l) run_action("G_CORNER_BL", "none", 0, 0, "corner bottom left");
        else if (b && r) run_action("G_CORNER_BR", "none", 0, 0, "corner bottom right");
    }
}

/* touch class for the display daemon (ctl/turn: "<wall ms> n|p|u|c"), written when a single finger is lifted:
 * n next page, p previous page, c tap in the middle third (a reader shows or hides its bars), u UI (not a page turn). */
static void write_hint(char c) {
    char p[300]; ctl_path(p, sizeof p, "turn");
    int fd = open(p, O_WRONLY | O_CREAT | O_TRUNC, 0666);
    if (fd < 0) return;
    fchmod(fd, 0666);
    char b[40]; int n = snprintf(b, sizeof b, "%lld %c\n", wall_ms(), c);
    if (write(fd, b, (size_t)n) < 0) { /* the hint is optional */ }
    close(fd);
}
/* a horizontal swipe (finger to the left = next page) or a tap (left third = previous, right third = next) is a page turn;
 * a tap in the middle third is c and every other touch (vertical swipe, short moves) is u */
static void turn_hint(int x0, int x1, int ady) {
    int dx = x1 - x0, adx = dx < 0 ? -dx : dx;
    char c = 'u';
    if (adx >= 100 && ady * 10 < adx * 6) c = dx < 0 ? 'n' : 'p';
    else if (adx + ady < 60) c = x0 < g_panel_w / 3 ? 'p' : x0 >= g_panel_w * 2 / 3 ? 'n' : 'c';
    write_hint(c);
}

static void release_eval(long long now) {
    if (fired) return;
    if (maxf < 2 || !has2) return;
    long long dur = now - t_start;
    if (diag2 && dur < TAP_MS && maxtravel < 60) {          /* fixed gestures: not configurable, and not switched off by GESTURES */
        printf("action: screenshot (%s, fixed)\n", diag2 == 1 ? "top left + bottom right" : "bottom left + top right");
        touch_file("screenshot"); return;
    }
    if (!conf_int("GESTURES", 1)) return;
    int dx = lcx - cx0, dy = lcy - cy0, adx = dx < 0 ? -dx : dx, ady = dy < 0 ? -dy : dy;
    int dd = dl - d0;
    if ((dd >= PINCH_PX || dd <= -PINCH_PX) && adx < 100 && ady < 100) run_action(dd > 0 ? "G_PINCH_OUT" : "G_PINCH_IN", "none", 0, 0, "two-finger pinch");
    else if (adx < 40 && ady < 40 && dur < TAP_MS) run_action("G_TAP2", "none", 0, 0, "two-finger tap");
    else if (adx >= 200 && ady * 10 < adx * 6) run_action("G_SWIPE2_H", "none", 0, 0, "two-finger swipe");
    else if (ady >= 200 && adx * 10 < ady * 6) {
        int right = cx0 >= g_panel_w / 2;
        run_action(right ? "G_SWIPE2_VR" : "G_SWIPE2_VL", "none", swipe_delta(dy), right, "two-finger vertical swipe");
    }
    else if (adx >= 150 && ady >= 150 && ady * 2 > adx && adx * 2 > ady)
        run_action(dy < 0 ? (dx > 0 ? "G_SWIPE2_NE" : "G_SWIPE2_NW") : (dx > 0 ? "G_SWIPE2_SE" : "G_SWIPE2_SW"), "none", 0, 0, "two-finger diagonal swipe");
}

/* single finger from the bottom edge: decided at release, needs the first and last position of that finger */
static int last_x, last_y, first_x, first_y;
#define EDGE_BOT_PX 30     /* the fixed swipe up (left half home, right half back) must start this close to the bottom edge, so that a swipe up inside an app does not count */
#define EDGE_TOP_PX 30     /* the same for the swipe down at the top edge (right half: fixed status bar, left half: G_EDGE_T_L) */

/* one finger: a swipe up or down along the left or right edge (first_x in the edge zone); runs the action when one is bound. */
static int side_swipe(int dyy) {
    if (!conf_int("GESTURES", 1) || first_x < 0) return 0;
    int side = first_x <= EDGE_SIDE_PX ? 1 : first_x >= g_panel_w - EDGE_SIDE_PX ? 2 : 0;
    int ady = dyy < 0 ? -dyy : dyy, adx = last_x - first_x; if (adx < 0) adx = -adx;
    if (!side || ady < 150 || adx >= 200) return 0;
    const char* key = side == 1 ? (dyy < 0 ? "G_EDGE_L_UP" : "G_EDGE_L_DOWN") : (dyy < 0 ? "G_EDGE_R_UP" : "G_EDGE_R_DOWN");
    if (!bound(key)) return 0;
    run_action(key, "none", swipe_delta(dyy), side == 2, "edge swipe");
    return 1;
}

/* the action of a swipe gesture is light_adjust (0) or warm_adjust (1); -1 for any other action */
static int adjust_kind(const char* key) {
    char a[48]; conf_str(key, "none", a, sizeof a);
    return !strcmp(a, "light_adjust") ? 0 : !strcmp(a, "warm_adjust") ? 1 : -1;
}
/* the swipe is live from now on: remember the level where it started; the steps are counted from there */
static void live_start(int amber, int mode) {
    live_prop = "flIntensity";
    if (amber && lipc_get("com.lab126.powerd", "currentAmberLevel") >= 0) live_prop = "currentAmberLevel";
    int v = dry ? 10 : lipc_get("com.lab126.powerd", live_prop); if (v < 0) v = 10;
    live_base = live_last = v; live_on = 1; live_mode = mode; fired = 1;   /* fired: the release does nothing more */
    printf("action: live %s from %d\n", live_prop, v);
}
/* steps: the change since the start (+ = brighter): the level follows the fingers, up and down, and stops at 0 and 24 */
static void live_apply(int steps) {
    int v = live_base + steps; v = v < 0 ? 0 : v > 24 ? 24 : v;
    if (v == live_last) return;
    live_last = v;
    lipc_set("com.lab126.powerd", live_prop, v);
    printf("action: %s %d (live)\n", live_prop, v);
    notify_status();
}
/* called after every touch report: finds a swipe with steps (two fingers up/down, left or right half, or one finger up/down along an edge)
 * and keeps changing the level while it goes on. Swipes with another action are left to the release check. */
static void live_update(void) {
    if (!in_session || nfing < 1) return;
    if (live_on) {
        if (live_mode == 2 && nfing >= 2) live_apply(swipe_delta(lcy - cy0));
        else if (live_mode == 1 && nfing == 1) live_apply(swipe_delta(last_y - first_y));
        return;
    }
    if (fired) return;
    if (nfing >= 2 && has2 && !diag2) {
        int dx = lcx - cx0, dy = lcy - cy0, adx = dx < 0 ? -dx : dx, ady = dy < 0 ? -dy : dy, dd = dl - d0;
        if (ady < LIVE_START_PX || adx * 10 >= ady * 6 || dd >= PINCH_PX || dd <= -PINCH_PX || (live_no & 4) || !conf_int("GESTURES", 1)) return;
        int k = adjust_kind(cx0 >= g_panel_w / 2 ? "G_SWIPE2_VR" : "G_SWIPE2_VL");
        if (k < 0) { live_no |= 4; return; }
        live_start(k, 2); live_apply(swipe_delta(dy));
    } else if (nfing == 1 && maxf == 1 && first_x >= 0 && first_y > EDGE_TOP_PX && first_y < g_panel_h - EDGE_BOT_PX) {
        int side = first_x <= EDGE_SIDE_PX ? 1 : first_x >= g_panel_w - EDGE_SIDE_PX ? 2 : 0;
        int dy = last_y - first_y, ady = dy < 0 ? -dy : dy, adx = last_x - first_x; if (adx < 0) adx = -adx;
        if (!side || !(side == 1 ? side_l : side_r) || ady < LIVE_START_PX || adx >= 200) return;
        int bit = dy < 0 ? 1 : 2;
        if (live_no & bit) return;
        const char* key = side == 1 ? (dy < 0 ? "G_EDGE_L_UP" : "G_EDGE_L_DOWN") : (dy < 0 ? "G_EDGE_R_UP" : "G_EDGE_R_DOWN");
        int k = adjust_kind(key);
        if (k < 0) { live_no |= bit; return; }
        live_start(k, 1); live_apply(swipe_delta(dy));
    }
}

/* one finger: a short tap in a corner, or a second one in the same corner (double tap). The tap still reaches Android. */
static void corner_tap(long long now) {
    static const char* const tk[4] = { "G_TAP_TL", "G_TAP_TR", "G_TAP_BL", "G_TAP_BR" };
    static const char* const dk[4] = { "G_DTAP_TL", "G_DTAP_TR", "G_DTAP_BL", "G_DTAP_BR" };
    if (!conf_int("GESTURES", 1) || first_x < 0 || first_y < 0) return;
    if (now - t_start >= TAP1_MS || maxtravel >= 40) { last_corner = -1; return; }
    int l = first_x < CORNER_PX, r = first_x >= g_panel_w - CORNER_PX, t = first_y < CORNER_PX, b = first_y >= g_panel_h - CORNER_PX;
    if (!(l || r) || !(t || b)) { last_corner = -1; return; }
    int c = (b ? 2 : 0) + (r ? 1 : 0);
    if (last_corner == c && now - last_tap_ms < DTAP_MS) {
        last_corner = -1;
        if (bound(dk[c])) run_action(dk[c], "none", 0, 0, "corner double tap");
        return;
    }
    last_corner = c; last_tap_ms = now;
    if (bound(tk[c])) run_action(tk[c], "none", 0, 0, "corner tap");
}

static void process_syn(long long now) {
    int n = 0, sx = 0, sy = 0, mt = 0;
    for (int i = 0; i < 16; i++) if (sl[i].on) { n++; sx += sl[i].x; sy += sl[i].y; int t = travel_of(&sl[i]); if (t > mt) mt = t; last_x = sl[i].x; last_y = sl[i].y; }
    nfing = n;
    if (n > 0 && !in_session) {
        in_session = 1; maxf = 0; fired = 0; t_start = now; has2 = 0; diag2 = 0; maxtravel = 0;
        first_x = first_y = -1; d0 = dl = 0; live_on = 0; live_no = 0;
        int g = conf_int("GESTURES", 1);
        side_l = g && (bound("G_EDGE_L_UP") || bound("G_EDGE_L_DOWN"));
        side_r = g && (bound("G_EDGE_R_UP") || bound("G_EDGE_R_DOWN"));
        top_l = g && bound("G_EDGE_T_L");
    }
    /* the first position of the finger may arrive in a later report than the tracking id: y0 is -1 until then, and -1 must not count as the top edge */
    if (in_session && first_y < 0) for (int i = 0; i < 16; i++) if (sl[i].on && sl[i].y0 >= 0) { first_x = sl[i].x0; first_y = sl[i].y0; }
    if (!in_session) return;
    if (mt > maxtravel) maxtravel = mt;
    if (n > maxf) maxf = n;
    if (n >= 2) {
        if (!has2) {
            has2 = 1; t2 = now; cx0 = sx / n; cy0 = sy / n; maxtravel = 0; d0 = pair_dist();
            int xs[2] = { 0, 0 }, ys[2] = { 0, 0 }, k = 0;
            for (int i = 0; i < 16 && k < 2; i++) if (sl[i].on) { xs[k] = sl[i].x; ys[k] = sl[i].y; k++; }
            if (k == 2) {   /* one finger in the top left and one in the bottom right corner, or top right and bottom left */
                int c0 = (xs[0] < CORNER_PX ? 1 : xs[0] >= g_panel_w - CORNER_PX ? 2 : 0) + (ys[0] < CORNER_PX ? 10 : ys[0] >= g_panel_h - CORNER_PX ? 20 : 0);
                int c1 = (xs[1] < CORNER_PX ? 1 : xs[1] >= g_panel_w - CORNER_PX ? 2 : 0) + (ys[1] < CORNER_PX ? 10 : ys[1] >= g_panel_h - CORNER_PX ? 20 : 0);
                diag2 = ((c0 == 11 && c1 == 22) || (c0 == 22 && c1 == 11)) ? 1 : ((c0 == 12 && c1 == 21) || (c0 == 21 && c1 == 12)) ? 2 : 0;   /* 1: top left + bottom right, 2: top right + bottom left */
                printf("two-finger down: (%d,%d) (%d,%d) diag %d\n", xs[0], ys[0], xs[1], ys[1], diag2); fflush(stdout);
            }
        }
        lcx = sx / n; lcy = sy / n; dl = pair_dist();
    }
    live_update();
    if (n == 0) {
        { char up[300]; ctl_path(up, sizeof up, "touchup"); int uf = open(up, O_WRONLY | O_CREAT | O_TRUNC, 0666); if (uf >= 0) { fchmod(uf, 0666); close(uf); } }
        if (maxf == 1 && !fired) {
            int dyy = last_y - first_y, dxx = last_x - first_x; if (dxx < 0) dxx = -dxx;
            if (first_y >= g_panel_h - EDGE_BOT_PX && dyy <= -150 && dxx < 200) { write_hint('u'); if (first_x < g_panel_w / 2) { printf("action: home (bottom edge swipe up, left half, fixed)\n"); key_tap(KEY_HOMEPAGE); } else { printf("action: back (bottom edge swipe up, right half, fixed)\n"); key_tap(KEY_BACK); } }
            else if (first_y >= 0 && first_y <= EDGE_TOP_PX && dyy >= 150 && dxx < 200 && (first_x >= g_panel_w / 2 || top_l)) {
                write_hint('u');
                if (first_x >= g_panel_w / 2) { printf("action: statusbar (top edge swipe down, right half, fixed)\n"); touch_file("statusbar"); }
                else run_action("G_EDGE_T_L", "none", 0, 0, "top edge swipe down, left half");
            }
            else if (side_swipe(dyy)) write_hint('u');
            else { corner_tap(now); turn_hint(first_x, last_x, dyy < 0 ? -dyy : dyy); }
        } else release_eval(now);
        in_session = 0; return;
    }
    hold_check(now);
}

static void handle(const struct input_event* e, long long now) {
    if (e->type == EV_ABS) {
        switch (e->code) {
        case ABS_MT_SLOT: cur = e->value < 0 ? 0 : e->value > 15 ? 15 : e->value; break;
        case ABS_MT_TRACKING_ID:
            if (e->value < 0) sl[cur].on = 0;
            else { sl[cur].on = 1; sl[cur].x0 = -1; sl[cur].y0 = -1; }
            break;
        case ABS_MT_POSITION_X: sl[cur].x = e->value; if (sl[cur].on && sl[cur].x0 < 0) sl[cur].x0 = e->value; break;
        case ABS_MT_POSITION_Y: sl[cur].y = e->value; if (sl[cur].on && sl[cur].y0 < 0) sl[cur].y0 = e->value; break;
        }
    } else if (e->type == EV_SYN && e->code == SYN_REPORT) process_syn(now);
}

/* Virtual copy of the touch screen (for --grab): same event types, keys, axes with their ranges, and input properties. */
static int vtouch_create(int src) {
    int fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK);
    if (fd < 0) fd = open("/dev/input/uinput", O_WRONLY | O_NONBLOCK);
    if (fd < 0) return -1;
    unsigned char evb[(EV_MAX + 8) / 8], keyb[(KEY_MAX + 8) / 8], absb[(ABS_MAX + 8) / 8], propb[(INPUT_PROP_MAX + 8) / 8];
    memset(evb, 0, sizeof evb); memset(keyb, 0, sizeof keyb); memset(absb, 0, sizeof absb); memset(propb, 0, sizeof propb);
    ioctl(src, EVIOCGBIT(0, sizeof evb), evb);
    ioctl(src, EVIOCGBIT(EV_KEY, sizeof keyb), keyb);
    ioctl(src, EVIOCGBIT(EV_ABS, sizeof absb), absb);
    ioctl(src, EVIOCGPROP(sizeof propb), propb);
    struct uinput_user_dev u; memset(&u, 0, sizeof u);
    snprintf(u.name, UINPUT_MAX_NAME_SIZE, "embrith-touch");
    u.id.bustype = BUS_VIRTUAL; u.id.vendor = 0x4b44; u.id.product = 0x0002; u.id.version = 1;
#define BIT(b, i) ((b)[(i) / 8] & (1 << ((i) % 8)))
    for (int t = 0; t <= EV_MAX; t++) if (BIT(evb, t)) ioctl(fd, UI_SET_EVBIT, t);
    for (int k = 0; k <= KEY_MAX; k++) if (BIT(keyb, k)) ioctl(fd, UI_SET_KEYBIT, k);
    for (int a = 0; a <= ABS_MAX; a++) if (BIT(absb, a)) {
        struct input_absinfo ai; memset(&ai, 0, sizeof ai);
        if (ioctl(src, EVIOCGABS(a), &ai) < 0) continue;
        ioctl(fd, UI_SET_ABSBIT, a);
        u.absmin[a] = ai.minimum; u.absmax[a] = ai.maximum; u.absfuzz[a] = ai.fuzz; u.absflat[a] = ai.flat;
    }
    for (int p = 0; p <= INPUT_PROP_MAX; p++) if (BIT(propb, p)) ioctl(fd, UI_SET_PROPBIT, p);
#undef BIT
    if (write(fd, &u, sizeof u) < 0 || ioctl(fd, UI_DEV_CREATE) < 0) { close(fd); return -1; }
    return fd;
}

/* Forwarding to the virtual touch screen (like KOReader: a contact that turns out to be one of two is not a single-finger gesture).
 * The first finger is held back for VT_HOLD_MS. A second finger within that time (two-finger gesture) drops the whole touch,
 * so Android never sees either finger. Without a second finger the held events are sent when the finger lifts, moves
 * VT_MOVE_PX or the time is up; after that everything is forwarded as it comes (a late second finger = pinch, Android's).
 * GESTURES=0 keeps two-finger input for Android, except for the diagonal screenshot. */
#define VT_HOLD_MS 50
#define VT_MOVE_PX 40
#define VT_EDGE_MS 400     /* a touch that starts in the top or bottom edge zone is held back longer: it may be a fixed edge swipe, which Android must not see */
enum { VT_IDLE, VT_HOLD, VT_PASS, VT_MUTE };
static int vt_state, vt_slot, vt_src, vt_slot0, vt_nb;
static struct input_event vt_buf[512];

static void vt_flush(int fd) {
    if (vt_nb <= 0) { vt_nb = 0; return; }
    if (vt_slot != vt_slot0) { struct input_event e; memset(&e, 0, sizeof e); e.type = EV_ABS; e.code = ABS_MT_SLOT; e.value = vt_slot0; if (write(fd, &e, sizeof e) < 0) { /* lost one event */ } }
    for (int i = 0; i < vt_nb; i++) if (write(fd, &vt_buf[i], sizeof vt_buf[i]) < 0) { /* lost one event: nothing better to do */ }
    vt_slot = vt_src; vt_nb = 0;
}

/* 1: the touch started in the top edge zone (swipe down: right half = status bar, left half = G_EDGE_T_L when bound), -1: in the bottom edge zone (swipe up = back), 2 / 3: in the left / right edge zone while an action is bound to it, 0: elsewhere or not known yet.
 * Same zones and distances as the release check in process_syn. */
static int edge_zone(void) {
    if (first_y < 0) return 0;
    if (first_y >= g_panel_h - EDGE_BOT_PX) return -1;
    if (first_y <= EDGE_TOP_PX && (first_x >= g_panel_w / 2 || top_l)) return 1;
    if (side_l && first_x <= EDGE_SIDE_PX) return 2;
    if (side_r && first_x >= g_panel_w - EDGE_SIDE_PX) return 3;
    return 0;
}
/* how far the finger has moved in the direction of the edge swipe (negative: the other way) */
static int edge_progress(int z) { int dy = last_y - first_y; return z >= 2 ? (dy < 0 ? -dy : dy) : z > 0 ? dy : -dy; }
static int edge_swipe(int z) { int dx = last_x - first_x; if (dx < 0) dx = -dx; return (edge_progress(z) >= 150 && dx < 200) || (live_on && live_mode == 1 && z >= 2); }

static void vt_push(int fd, const struct input_event* e) {
    if (vt_nb == 0) vt_slot0 = vt_src;
    if (e->type == EV_ABS && e->code == ABS_MT_SLOT) vt_src = e->value < 0 ? 0 : e->value > 15 ? 15 : e->value;
    if (vt_nb < 512) vt_buf[vt_nb++] = *e;
    if (!(e->type == EV_SYN && e->code == SYN_REPORT)) { if (vt_nb >= 512) { vt_flush(fd); vt_state = VT_PASS; } return; }
    if (vt_state == VT_MUTE) { vt_nb = 0; if (nfing == 0) vt_state = VT_IDLE; return; }
    if (vt_state == VT_PASS) { vt_flush(fd); if (nfing == 0) vt_state = VT_IDLE; return; }
    if (nfing == 0) {                                                       /* lifted inside the hold-back time: a tap, or a fixed edge swipe that Android must not see */
        int z = edge_zone();
        if (z && maxf == 1 && edge_swipe(z)) vt_nb = 0; else vt_flush(fd);
        vt_state = VT_IDLE; return;
    }
    if (nfing >= 2 && (diag2 || conf_int("GESTURES", 1))) { vt_nb = 0; vt_state = VT_MUTE; return; }
    vt_state = VT_HOLD;
    if (nfing == 1 && edge_zone()) {            /* started at the top or bottom edge: held until it is clear that it is not a fixed edge swipe */
        int z = edge_zone(), dx = last_x - first_x; if (dx < 0) dx = -dx;
        if (edge_swipe(z)) { vt_nb = 0; vt_state = VT_MUTE; return; }
        if (dx >= 200 || edge_progress(z) <= -VT_MOVE_PX || wall_ms() - t_start >= VT_EDGE_MS) { vt_flush(fd); vt_state = VT_PASS; }
        return;
    }
    if (nfing >= 2 || maxtravel >= VT_MOVE_PX || wall_ms() - t_start >= VT_HOLD_MS) { vt_flush(fd); vt_state = VT_PASS; }
}

/* the hold-back time is up without a new event */
static void vt_tick(int fd) {
    if (vt_state == VT_HOLD && vt_nb > 0 && vt_buf[vt_nb - 1].type == EV_SYN && wall_ms() - t_start >= (nfing == 1 && edge_zone() ? VT_EDGE_MS : VT_HOLD_MS)) { vt_flush(fd); vt_state = VT_PASS; }
}

static int uinput_create(void) {
    int fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK);
    if (fd < 0) fd = open("/dev/input/uinput", O_WRONLY | O_NONBLOCK);
    if (fd < 0) return -1;
    ioctl(fd, UI_SET_EVBIT, EV_KEY);
    static const int keys[] = { KEY_BACK, KEY_HOME, KEY_HOMEPAGE, KEY_MENU, KEY_VOLUMEUP, KEY_VOLUMEDOWN, KEY_PAGEUP, KEY_PAGEDOWN, KEY_APPSELECT };    for (unsigned i = 0; i < sizeof keys / sizeof keys[0]; i++) ioctl(fd, UI_SET_KEYBIT, keys[i]);
    struct uinput_user_dev u; memset(&u, 0, sizeof u);
    snprintf(u.name, UINPUT_MAX_NAME_SIZE, "embrith-keys");
    u.id.bustype = BUS_VIRTUAL; u.id.vendor = 0x4b44; u.id.product = 0x0001; u.id.version = 1;
    if (write(fd, &u, sizeof u) < 0 || ioctl(fd, UI_DEV_CREATE) < 0) { close(fd); return -1; }
    return fd;
}

#ifdef KGEST_LIPC
/* frameworkStarted on com.lab126.kaf (KOReader device.lua frameworkStopped): powerd asks the framework before it handles the
 * power key; with lab126_gui stopped nothing answers, so this process answers "started" while embrith runs (--kaf).
 * The property lives as long as this process's lipc connection: it goes away when kgest stops. */
#include <openlipc.h>
static LIPC* kaf_lipc;
static LIPCcode kaf_started(LIPC* lipc, const char* property, void* value, void* data) {
    (void)lipc; (void)property; (void)data;
    *(int*)value = 1;
    return LIPC_OK;
}
static void kaf_register(void) {
    LIPCcode code = LIPC_OK;
    kaf_lipc = LipcOpenEx("com.lab126.kaf", &code);
    if (!kaf_lipc) { printf("kgest: WARNING no lipc on com.lab126.kaf (code %d), frameworkStarted not set\n", (int)code); return; }
    if (LipcRegisterIntProperty(kaf_lipc, "frameworkStarted", kaf_started, NULL, NULL) != LIPC_OK) {
        printf("kgest: WARNING frameworkStarted not registered\n");
        LipcClose(kaf_lipc); kaf_lipc = NULL; return;
    }
    printf("kgest: frameworkStarted=1 on com.lab126.kaf (framework stopped)\n");
}
static void kaf_unregister(void) {
    if (!kaf_lipc) return;
    LipcUnregisterProperty(kaf_lipc, "frameworkStarted", NULL);
    LipcClose(kaf_lipc); kaf_lipc = NULL;
}
#endif
static int kaf;   /* --kaf: answer frameworkStarted (embrith.sh start --framework_stop) */

int main(int argc, char** argv) {
    const char* touch = NULL; const char* events = NULL; int grab = 0, vtfd = -1;
    for (int i = 1; i < argc; i++) {
        if (!strcmp(argv[i], "--touch") && i + 1 < argc) touch = argv[++i];
        else if (!strcmp(argv[i], "--dir") && i + 1 < argc) dir = argv[++i];
        else if (!strcmp(argv[i], "--events") && i + 1 < argc) { events = argv[++i]; from_file = 1; }
        else if (!strcmp(argv[i], "--dry")) dry = 1;
        else if (!strcmp(argv[i], "--grab")) grab = 1;
        else if (!strcmp(argv[i], "--kaf")) kaf = 1;
    }
    setvbuf(stdout, NULL, _IOLBF, 0);
    { unsigned pw, ph; if (panel_detect(&pw, &ph) != 0) { fprintf(stderr, "kgest: cannot determine the panel size (cannot read /dev/fb0)\n"); return 1; } g_panel_w = (int)pw; g_panel_h = (int)ph; }
    struct sigaction sa; memset(&sa, 0, sizeof sa); sa.sa_handler = on_sig;
    sigaction(SIGTERM, &sa, NULL); sigaction(SIGINT, &sa, NULL);
    signal(SIGCHLD, SIG_DFL);
    int fd = open(events ? events : touch ? touch : "/dev/input/event1", O_RDONLY | (from_file ? 0 : O_NONBLOCK));
    if (fd < 0) { perror("open touch"); return 1; }
    if (!dry && grab && !from_file) {          /* the virtual touch screen must exist before keys.ready (the launcher copies the nodes then) */
        vtfd = vtouch_create(fd);
        if (vtfd >= 0 && ioctl(fd, EVIOCGRAB, 1) == 0) printf("kgest: touch screen grabbed, Android gets embrith-touch\n");
        else {
            printf("kgest: WARNING grab failed (%s), the touch screen stays shared with the Kindle\n", vtfd < 0 ? "no uinput" : strerror(errno));
            if (vtfd >= 0) { ioctl(vtfd, UI_DEV_DESTROY); close(vtfd); vtfd = -1; }
        }
    }
    if (!dry) {
        uifd = uinput_create();
        printf(uifd >= 0 ? "kgest: virtual keyboard embrith-keys created\n" : "kgest: WARNING no /dev/uinput, BACK/HOME gestures will do nothing\n");
        char p[300]; snprintf(p, sizeof p, "%s/keys.ready", dir);
        if (uifd >= 0) { int f = open(p, O_WRONLY | O_CREAT | O_TRUNC, 0666); if (f >= 0) close(f); }
    }
    if (kaf && !dry) {
#ifdef KGEST_LIPC
        kaf_register();
#else
        printf("kgest: built without KGEST_LIPC, frameworkStarted is not set (the power key may be ignored with the framework stopped)\n");
#endif
    }
    printf("kgest up: %s, gestures %d\n", events ? events : touch ? touch : "/dev/input/event1", conf_int("GESTURES", 1));

    struct input_event ev; long long last_t = 0;
    while (!g_stop) {
        if (from_file) {
            ssize_t n = read(fd, &ev, sizeof ev);
            if (n != (ssize_t)sizeof ev) break;
            last_t = (long long)ev.input_event_sec * 1000 + ev.input_event_usec / 1000;
            handle(&ev, last_t);
        } else {
            struct pollfd pf[1]; pf[0].fd = fd; pf[0].events = POLLIN; pf[0].revents = 0;
            int r = poll(pf, 1, vt_state == VT_HOLD ? 20 : 200);
            if (r > 0 && (pf[0].revents & POLLIN)) {
                ssize_t n;
                while ((n = read(fd, &ev, sizeof ev)) == (ssize_t)sizeof ev) {
                    handle(&ev, wall_ms());
                    if (vtfd >= 0) vt_push(vtfd, &ev);
                }
                if (vtfd >= 0) vt_tick(vtfd);
                if (n < 0 && errno != EAGAIN && errno != EINTR) break;
            } else if (r == 0) { hold_check(wall_ms()); if (vtfd >= 0) vt_tick(vtfd); }
        }
    }
    if (uifd >= 0) { ioctl(uifd, UI_DEV_DESTROY); close(uifd); }
    if (vtfd >= 0) { ioctl(fd, EVIOCGRAB, 0); ioctl(vtfd, UI_DEV_DESTROY); close(vtfd); }
#ifdef KGEST_LIPC
    kaf_unregister();
#endif
    printf("kgest stopping\n");
    return 0;
}
