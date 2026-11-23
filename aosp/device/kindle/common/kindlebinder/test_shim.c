/* SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Host unit tests for libkindlebinder (Binder v7 <-> v8 translation & shadow buffer table).
 * Copyright (C) 2026 Embrith contributors
 */

#define UNIT_TEST 1
#include <assert.h>
#include <stdio.h>
#include "libkindlebinder.c"

static uint8_t g_test_arena[4096];
static const void *test_unflatten_kptr(uint32_t p) {
    return g_test_arena + (p - 0x40000000U);
}

static void test_write_translation(void) {
    uint8_t payload[64];
    memset(payload, 0xaa, sizeof(payload));
    struct flat_binder_object_64 fbo64 = {
        .type = 0x73622a85U,
        .flags = 0x17fU,
        .binder = 0x12345678ULL,
        .cookie = 0xdeadbeefULL,
    };
    memcpy(payload + 8, &fbo64, 24);
    uint64_t offs64[1] = {8};

    struct binder_transaction_data_64 tr64;
    memset(&tr64, 0, sizeof(tr64));
    tr64.target.handle = 0;
    tr64.cookie = 0x11223344ULL;
    tr64.code = 3;
    tr64.flags = 0x10;
    tr64.sender_pid = 42;
    tr64.sender_euid = 1000;
    tr64.data_size = 40;
    tr64.offsets_size = 8;
    tr64.data.ptr.buffer = (uint64_t)(uintptr_t)payload;
    tr64.data.ptr.offsets = (uint64_t)(uintptr_t)offs64;

    struct binder_handle_cookie_64 hc64 = {
        .handle = 7,
        .cookie = 0xcafebabeULL,
    };

    uint8_t u_buf[256];
    size_t u_pos = 0;
    uint32_t c;

    c = BC_ENTER_LOOPER_CMD;
    memcpy(u_buf + u_pos, &c, 4); u_pos += 4;
    c = BC_TRANSACTION_64;
    memcpy(u_buf + u_pos, &c, 4); u_pos += 4;
    memcpy(u_buf + u_pos, &tr64, 64); u_pos += 64;
    c = BC_REQUEST_DEATH_NOTIFICATION_64;
    memcpy(u_buf + u_pos, &c, 4); u_pos += 4;
    memcpy(u_buf + u_pos, &hc64, 12); u_pos += 12;
    c = BC_DEAD_BINDER_DONE_64;
    memcpy(u_buf + u_pos, &c, 4); u_pos += 4;
    uint64_t dead_c = 0x55667788ULL;
    memcpy(u_buf + u_pos, &dead_c, 8); u_pos += 8;

    uint8_t k_buf[256];
    size_t k_len = 0;
    struct write_ctx ctx;
    assert(translate_write_buf(u_buf, u_pos, k_buf, sizeof(k_buf), &k_len, &ctx) == 0);
    assert(k_len == 4 + 44 + 12 + 8);
    assert(ctx.num_cmds == 4);
    assert(ctx.num_tmp == 2);

    uint32_t kcmd;
    memcpy(&kcmd, k_buf, 4);
    assert(kcmd == BC_ENTER_LOOPER_CMD);
    memcpy(&kcmd, k_buf + 4, 4);
    assert(kcmd == BC_TRANSACTION_32);

    struct binder_transaction_data_32 tr32;
    memcpy(&tr32, k_buf + 8, 40);
    assert(tr32.code == 3);
    assert(tr32.data_size == 40);
    assert(tr32.offsets_size == 4);
    assert(tr32.data.ptr.buffer == (uint32_t)(uintptr_t)ctx.tmp_allocs[0]);
    assert(tr32.data.ptr.offsets == (uint32_t)(uintptr_t)ctx.tmp_allocs[1]);
    const uint32_t *koffs = (const uint32_t *)ctx.tmp_allocs[1];
    assert(koffs[0] == 8);
    struct flat_binder_object_32 fbo32;
    memcpy(&fbo32, (const uint8_t *)ctx.tmp_allocs[0] + 8, 16);
    assert(fbo32.type == 0x73622a85U);
    assert(fbo32.flags == 0x17fU);
    assert(fbo32.binder == 0x12345678U);
    assert(fbo32.cookie == 0xdeadbeefU);

    memcpy(&kcmd, k_buf + 48, 4);
    assert(kcmd == BC_REQUEST_DEATH_NOTIFICATION_32);
    struct binder_handle_cookie_32 hc32;
    memcpy(&hc32, k_buf + 52, 8);
    assert(hc32.handle == 7);
    assert(hc32.cookie == 0xcafebabeU);

    memcpy(&kcmd, k_buf + 60, 4);
    assert(kcmd == BC_DEAD_BINDER_DONE_32);
    uint32_t dead32;
    memcpy(&dead32, k_buf + 64, 4);
    assert(dead32 == 0x55667788U);

    assert(map_write_consumed(&ctx, 0, k_len, u_pos) == 0);
    assert(map_write_consumed(&ctx, 4, k_len, u_pos) == 4);
    assert(map_write_consumed(&ctx, 48, k_len, u_pos) == 72);
    assert(map_write_consumed(&ctx, k_len, k_len, u_pos) == u_pos);

    write_ctx_free_tmps(&ctx);
}

static void test_read_and_shadow_free(void) {
    uint8_t *k_payload = g_test_arena + 0x100;
    uint32_t *koffs = (uint32_t *)(g_test_arena + 0x200);
    memset(k_payload, 0x5a, 40);
    struct flat_binder_object_32 fbo32 = {
        .type = 0x73682a85U,
        .flags = 0x17fU,
        .handle = 5,
        .cookie = 0,
    };
    memcpy(k_payload + 4, &fbo32, 16);
    memset(k_payload + 20, 0, 8);
    koffs[0] = 4;

    struct binder_transaction_data_32 tr32;
    memset(&tr32, 0, sizeof(tr32));
    tr32.target.ptr = 0x22334455U;
    tr32.cookie = 0x66778899U;
    tr32.code = 1;
    tr32.data_size = 36;
    tr32.offsets_size = 4;
    tr32.data.ptr.buffer = 0x40000100U;
    tr32.data.ptr.offsets = 0x40000200U;

    uint8_t k_rbuf[256];
    size_t k_rlen = 0;
    uint32_t cmd;

    cmd = BR_NOOP_CMD;
    memcpy(k_rbuf + k_rlen, &cmd, 4); k_rlen += 4;
    cmd = BR_TRANSACTION_32;
    memcpy(k_rbuf + k_rlen, &cmd, 4); k_rlen += 4;
    memcpy(k_rbuf + k_rlen, &tr32, 40); k_rlen += 40;
    cmd = BR_INCREFS_32;
    memcpy(k_rbuf + k_rlen, &cmd, 4); k_rlen += 4;
    struct binder_ptr_cookie_32 pc32 = { .ptr = 0x11111111U, .cookie = 0x22222222U };
    memcpy(k_rbuf + k_rlen, &pc32, 8); k_rlen += 8;
    cmd = BR_ATTEMPT_ACQUIRE_32;
    memcpy(k_rbuf + k_rlen, &cmd, 4); k_rlen += 4;
    struct binder_pri_ptr_cookie_32 ppc32 = { .priority = -4, .ptr = 0x33333333U, .cookie = 0x44444444U };
    memcpy(k_rbuf + k_rlen, &ppc32, 12); k_rlen += 12;
    cmd = BR_DEAD_BINDER_32;
    memcpy(k_rbuf + k_rlen, &cmd, 4); k_rlen += 4;
    uint32_t dc32 = 0x99887766U;
    memcpy(k_rbuf + k_rlen, &dc32, 4); k_rlen += 4;

    uint8_t u_rbuf[512];
    size_t u_rlen = 0;
    assert(translate_read_buf(k_rbuf, k_rlen, u_rbuf, sizeof(u_rbuf), &u_rlen) == 0);
    assert(u_rlen == 4 + 68 + 20 + 28 + 12);

    uint32_t ucmd;
    memcpy(&ucmd, u_rbuf, 4); assert(ucmd == BR_NOOP_CMD);
    memcpy(&ucmd, u_rbuf + 4, 4); assert(ucmd == BR_TRANSACTION_64);
    struct binder_transaction_data_64 tr64;
    memcpy(&tr64, u_rbuf + 8, 64);
    assert(tr64.target.ptr == 0x22334455ULL);
    assert(tr64.cookie == 0x66778899ULL);
    assert(tr64.offsets_size == 8);
    assert(tr64.data.ptr.buffer != (uint64_t)(uintptr_t)k_payload);

    const uint64_t *uoffs = (const uint64_t *)(uintptr_t)tr64.data.ptr.offsets;
    assert(uoffs[0] == 4);
    struct flat_binder_object_64 fbo64;
    memcpy(&fbo64, (const uint8_t *)(uintptr_t)tr64.data.ptr.buffer + 4, 24);
    assert(fbo64.type == 0x73682a85U);
    assert(fbo64.handle == 5);
    assert(fbo64.cookie == 0);

    memcpy(&ucmd, u_rbuf + 72, 4); assert(ucmd == BR_INCREFS_64);
    struct binder_ptr_cookie_64 pc64;
    memcpy(&pc64, u_rbuf + 76, 16);
    assert(pc64.ptr == 0x11111111ULL && pc64.cookie == 0x22222222ULL);

    memcpy(&ucmd, u_rbuf + 92, 4); assert(ucmd == BR_ATTEMPT_ACQUIRE_64);
    struct binder_pri_ptr_cookie_64 ppc64;
    memcpy(&ppc64, u_rbuf + 96, 24);
    assert(ppc64.priority == -4 && ppc64.ptr == 0x33333333ULL && ppc64.cookie == 0x44444444ULL);

    memcpy(&ucmd, u_rbuf + 120, 4); assert(ucmd == BR_DEAD_BINDER_64);
    uint64_t dc64;
    memcpy(&dc64, u_rbuf + 124, 8);
    assert(dc64 == 0x99887766ULL);

    /* Now free shadow buffer via BC_FREE_BUFFER_64 */
    uint8_t free_u[12], free_k[12];
    size_t free_klen = 0;
    struct write_ctx wctx;
    uint32_t fcmd = BC_FREE_BUFFER_64;
    memcpy(free_u, &fcmd, 4);
    memcpy(free_u + 4, &tr64.data.ptr.buffer, 8);
    assert(translate_write_buf(free_u, 12, free_k, sizeof(free_k), &free_klen, &wctx) == 0);
    assert(free_klen == 8);
    assert(wctx.num_free == 1);
    uint32_t fkcmd, fkptr;
    memcpy(&fkcmd, free_k, 4);
    memcpy(&fkptr, free_k + 4, 4);
    assert(fkcmd == BC_FREE_BUFFER_32);
    assert(fkptr == 0x40000100U);

    assert(shadow_remove(wctx.free_defer[0].u_ptr) == 1);
    free((void *)wctx.free_defer[0].u_ptr);
    assert(g_shadow_active == 0);
}

static void test_read_caps_and_shadows(void) {
    assert(calc_k_read_size(0) == 0);
    assert(calc_k_read_size(128) == 72);
    assert(calc_k_read_size(256) == 144);
    assert(calc_k_read_size(68) == 44);
    assert((calc_k_read_size(128) * 7) / 4 <= 128);
    assert((calc_k_read_size(256) * 7) / 4 <= 256);

    for (int i = 0; i < 2000; i++) {
        uintptr_t u = (uintptr_t)(0x10000000U + i * 32U);
        uint32_t k = (uint32_t)(0x40000000U + i * 16U);
        assert(shadow_insert(u, k) == 0);
    }
    for (int i = 0; i < 2000; i++) {
        uintptr_t u = (uintptr_t)(0x10000000U + i * 32U);
        uint32_t k = 0;
        assert(shadow_lookup(u, &k) == 1);
        assert(k == (uint32_t)(0x40000000U + i * 16U));
        assert(shadow_remove(u) == 1);
        assert(shadow_lookup(u, &k) == 0);
    }
    assert(g_shadow_active == 0);
}

int main(void) {
    test_write_translation();
    test_read_and_shadow_free();
    test_read_caps_and_shadows();
    printf("ALL_SHIM_TESTS_OK\n");
    return 0;
}
