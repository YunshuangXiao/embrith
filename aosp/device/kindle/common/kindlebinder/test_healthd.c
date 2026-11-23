/* SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Host unit tests for kindle_healthd (parcel layout, battery property sysfs parsing, dump format).
 * Copyright (C) 2026 Embrith contributors
 */

#ifndef UNIT_TEST
#define UNIT_TEST 1
#endif

#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

#include "kindle_healthd.c"

static int32_t read_i32_le(const uint8_t *p) {
    int32_t v;
    memcpy(&v, p, 4);
    return v;
}

static int64_t read_i64_le(const uint8_t *p) {
    int64_t v;
    memcpy(&v, p, 8);
    return v;
}

static size_t read_str16_le(const uint8_t *p, char *ascii_out, size_t cap) {
    int32_t n = read_i32_le(p);
    assert(n >= 0);
    size_t un = (size_t)n;
    assert(un + 1 <= cap);
    for (size_t i = 0; i < un; i++) {
        uint16_t ch;
        memcpy(&ch, p + 4 + i * 2, 2);
        ascii_out[i] = (char)(ch & 0xffU);
    }
    ascii_out[un] = '\0';
    uint16_t nul;
    memcpy(&nul, p + 4 + un * 2, 2);
    assert(nul == 0);
    return 4U + (((un + 1U) * 2U + 3U) & ~3U);
}

static void write_file_txt(const char *dir, const char *sub, const char *txt) {
    char path[512];
    snprintf(path, sizeof(path), "%s/%s", dir, sub);
    FILE *f = fopen(path, "w");
    assert(f != NULL);
    fputs(txt, f);
    fclose(f);
}

static void unlink_file_rel(const char *dir, const char *sub) {
    char path[512];
    snprintf(path, sizeof(path), "%s/%s", dir, sub);
    unlink(path);
}

static void rmdir_rel(const char *dir, const char *sub) {
    char path[512];
    snprintf(path, sizeof(path), "%s/%s", dir, sub);
    rmdir(path);
}

static void test_parcels(void) {
    uint8_t buf[512];
    uint32_t len = 0;
    uint32_t off0 = 0;
    char sbuf[128];

    assert(build_add_service_parcel(buf, sizeof(buf), &len, &off0) == 0);
    size_t pos = 0;
    assert(read_i32_le(buf + pos) == 0);
    pos += 4;
    pos += read_str16_le(buf + pos, sbuf, sizeof(sbuf));
    assert(strcmp(sbuf, "android.os.IServiceManager") == 0);
    pos += read_str16_le(buf + pos, sbuf, sizeof(sbuf));
    assert(strcmp(sbuf, "batteryproperties") == 0);
    assert(pos == off0);

    struct flat_binder_object_32 fbo;
    memcpy(&fbo, buf + pos, sizeof(fbo));
    assert(fbo.type == BINDER_TYPE_BINDER);
    assert(fbo.flags == 0x17fU);
    assert(fbo.binder == LOCAL_BINDER_PTR);
    assert(fbo.cookie == LOCAL_BINDER_COOKIE);
    pos += sizeof(fbo);
    uint64_t pad64 = 1;
    memcpy(&pad64, buf + pos, 8);
    assert(pad64 == 0);
    pos += 8;
    assert(read_i32_le(buf + pos) == 0);
    pos += 4;
    assert(pos == len);
    assert(len - off0 == 28);

    size_t skip = 0;
    assert(pb_skip_iface(buf, len, &skip) == 0);
    assert(skip == 4 + 4 + (((26U + 1U) * 2U + 3U) & ~3U));

    struct bat_props bp;
    init_bat_defaults(&bp);
    bp.ac = 1;
    bp.usb = 0;
    bp.wireless = 0;
    bp.status = BAT_STATUS_CHARGING;
    bp.health = BAT_HEALTH_GOOD;
    bp.present = 1;
    bp.level = 86;
    bp.voltage_mv = 4172;
    bp.temp_tenths = 210;
    bp.current_ua = -153000;
    strcpy(bp.tech, "Li-ion");

    assert(build_battery_changed_parcel(buf, sizeof(buf), &len, &bp) == 0);
    pos = 0;
    assert(read_i32_le(buf + pos) == 0);
    pos += 4;
    pos += read_str16_le(buf + pos, sbuf, sizeof(sbuf));
    assert(strcmp(sbuf, "android.os.IBatteryPropertiesListener") == 0);
    assert(read_i32_le(buf + pos) == 1); pos += 4;
    assert(read_i32_le(buf + pos) == 1); pos += 4;
    assert(read_i32_le(buf + pos) == 0); pos += 4;
    assert(read_i32_le(buf + pos) == 0); pos += 4;
    assert(read_i32_le(buf + pos) == 0); pos += 4;
    assert(read_i32_le(buf + pos) == 0); pos += 4;
    assert(read_i32_le(buf + pos) == BAT_STATUS_CHARGING); pos += 4;
    assert(read_i32_le(buf + pos) == BAT_HEALTH_GOOD); pos += 4;
    assert(read_i32_le(buf + pos) == 1); pos += 4;
    assert(read_i32_le(buf + pos) == 86); pos += 4;
    assert(read_i32_le(buf + pos) == 4172); pos += 4;
    assert(read_i32_le(buf + pos) == 210); pos += 4;
    assert(read_i32_le(buf + pos) == 0); pos += 4;
    pos += read_str16_le(buf + pos, sbuf, sizeof(sbuf));
    assert(strcmp(sbuf, "Li-ion") == 0);
    assert(pos == len);

    assert(build_get_property_reply(buf, sizeof(buf), &len, PROP_CAPACITY, &bp) == 0);
    assert(len == 20);
    assert(read_i32_le(buf + 0) == 0);
    assert(read_i32_le(buf + 4) == 0);
    assert(read_i32_le(buf + 8) == 1);
    assert(read_i64_le(buf + 12) == 86);

    assert(build_get_property_reply(buf, sizeof(buf), &len, PROP_CURRENT_NOW, &bp) == 0);
    assert(len == 20);
    assert(read_i64_le(buf + 12) == -153000);

    assert(build_get_property_reply(buf, sizeof(buf), &len, 99, &bp) == 0);
    assert(len == 12);
    assert(read_i32_le(buf + 4) == -22);
    assert(read_i32_le(buf + 8) == 0);
}

static void test_sysfs(void) {
    char tmpl[] = "/tmp/khealth_XXXXXX";
    char *root = mkdtemp(tmpl);
    assert(root != NULL);

    char sub[512];
    snprintf(sub, sizeof(sub), "%s/bd71827_ac", root);
    assert(mkdir(sub, 0755) == 0);
    snprintf(sub, sizeof(sub), "%s/bq256xx-charger", root);
    assert(mkdir(sub, 0755) == 0);
    snprintf(sub, sizeof(sub), "%s/bd71827_bat", root);
    assert(mkdir(sub, 0755) == 0);

    write_file_txt(root, "bd71827_ac/online", "0\n");
    write_file_txt(root, "bq256xx-charger/online", "1\n");
    write_file_txt(root, "bd71827_bat/present", "1\n");
    write_file_txt(root, "bd71827_bat/capacity", "86\n");
    write_file_txt(root, "bd71827_bat/voltage_now", "4172000\n");
    write_file_txt(root, "bd71827_bat/current_now", "-153000\n");
    write_file_txt(root, "bd71827_bat/temp", "21\n");
    write_file_txt(root, "bd71827_bat/status", "Discharging\n");
    write_file_txt(root, "bd71827_bat/health", "Good\n");
    write_file_txt(root, "bd71827_bat/technology", "Li-ion\n");

    struct bat_props bp;
    read_battery_props(root, &bp);
    assert(bp.ac == 0);
    assert(bp.usb == 1);
    assert(bp.present == 1);
    assert(bp.level == 86);
    assert(bp.voltage_mv == 4172);
    assert(bp.current_ua == -153000);
    assert(bp.temp_tenths == 210);
    assert(bp.status == BAT_STATUS_DISCHARGING);
    assert(bp.health == BAT_HEALTH_GOOD);
    assert(strcmp(bp.tech, "Li-ion") == 0);

    write_file_txt(root, "bd71827_bat/capacity", "0\n");
    read_battery_props(root, &bp);
    assert(bp.level == 80);

    write_file_txt(root, "bd71827_bat/voltage_now", "3300000\n");
    read_battery_props(root, &bp);
    assert(bp.voltage_mv == 3300);
    assert(bp.level == 0);

    read_battery_props("/nonexistent/path/for/test", &bp);
    assert(bp.level == 80);
    assert(bp.present == 1);
    assert(bp.status == BAT_STATUS_DISCHARGING);
    assert(bp.health == BAT_HEALTH_GOOD);
    assert(strcmp(bp.tech, "Li-ion") == 0);

    struct bat_props bp2 = bp;
    assert(bat_props_equal(&bp, &bp2) == 1);
    bp2.level = 79;
    assert(bat_props_equal(&bp, &bp2) == 0);

    char dump[256];
    size_t dn = format_battery_dump(dump, sizeof(dump), &bp);
    assert(dn > 0 && dn < sizeof(dump));
    dump[dn] = '\0';
    assert(strstr(dump, "level=80") != NULL);
    assert(strstr(dump, "tech=Li-ion") != NULL);

    unlink_file_rel(root, "bd71827_ac/online");
    unlink_file_rel(root, "bq256xx-charger/online");
    unlink_file_rel(root, "bd71827_bat/present");
    unlink_file_rel(root, "bd71827_bat/capacity");
    unlink_file_rel(root, "bd71827_bat/voltage_now");
    unlink_file_rel(root, "bd71827_bat/current_now");
    unlink_file_rel(root, "bd71827_bat/temp");
    unlink_file_rel(root, "bd71827_bat/status");
    unlink_file_rel(root, "bd71827_bat/health");
    unlink_file_rel(root, "bd71827_bat/technology");
    rmdir_rel(root, "bd71827_ac");
    rmdir_rel(root, "bq256xx-charger");
    rmdir_rel(root, "bd71827_bat");
    rmdir(root);
}

int main(void) {
    test_parcels();
    test_sysfs();
    printf("ALL_HEALTHD_TESTS_OK\n");
    return 0;
}
