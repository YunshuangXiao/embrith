/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors */
/* kdetach: re-run a command fully detached from whoever started it.
 * The library launcher starts scripts through a setuid helper, so their REAL uid stays the Kindle's "framework" user,
 * and pausing/stopping Kindle services can signal (and kill) them. This helper makes the process:
 *   - a real root (setresuid/setresgid 0), in its own session, not a process-group leader
 *   - with a clean environment and no inherited descriptors above 2, stdin from /dev/null
 *   - with oom_score_adj reset to 0
 * usage: kdetach <command> [args...]    (the parent returns immediately)
 */
#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <unistd.h>

int main(int argc, char** argv) {
    if (argc < 2) { fprintf(stderr, "usage: kdetach <command> [args...]\n"); return 2; }
    pid_t p = fork();
    if (p < 0) { perror("fork"); return 1; }
    if (p > 0) return 0;                       /* the caller gets control back at once */

    setsid();
    if (setresgid(0, 0, 0) < 0) perror("setresgid");
    if (setresuid(0, 0, 0) < 0) perror("setresuid");

    int fd = open("/proc/self/oom_score_adj", O_WRONLY);
    if (fd >= 0) { if (write(fd, "0", 1) < 0) { /* ignore */ } close(fd); }

    int dn = open("/dev/null", O_RDONLY);
    if (dn >= 0) { dup2(dn, 0); if (dn > 2) close(dn); }
    for (int i = 3; i < 256; i++) close(i);

    clearenv();
    setenv("PATH", "/usr/sbin:/usr/bin:/sbin:/bin", 1);
    setenv("HOME", "/", 1);
    execvp(argv[1], argv + 1);
    perror("execvp");
    _exit(127);
}
