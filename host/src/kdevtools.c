/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors */
/* kdevtools - debug helper: forwards a TCP port on the Kindle to an abstract unix socket inside the container's network namespace.
 * Use: kdevtools <tcp_port> <container_init_pid> <socket_name>
 * Example: kdevtools 9222 $PID webview_devtools_remote_2037, then ssh -L 9222:127.0.0.1:9222 to the Kindle and open chrome://inspect.
 * Listens on 127.0.0.1 only. Not part of the normal install (host/bin is built by tools/build.sh, this file is built by hand). */
#define _GNU_SOURCE
#include <arpa/inet.h>
#include <fcntl.h>
#include <poll.h>
#include <sched.h>
#include <signal.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>

static void relay(int a, int b)
{
    char buf[8192];
    struct pollfd p[2] = { { a, POLLIN, 0 }, { b, POLLIN, 0 } };
    for (;;) {
        if (poll(p, 2, -1) < 0) return;
        for (int i = 0; i < 2; i++) {
            if (!(p[i].revents & (POLLIN | POLLHUP | POLLERR))) continue;
            ssize_t n = read(p[i].fd, buf, sizeof buf);
            if (n <= 0) return;
            int to = p[1 - i].fd;
            for (ssize_t off = 0; off < n;) {
                ssize_t w = write(to, buf + off, (size_t)(n - off));
                if (w <= 0) return;
                off += w;
            }
        }
    }
}

int main(int argc, char **argv)
{
    if (argc != 4) {
        fprintf(stderr, "usage: %s <tcp_port> <container_init_pid> <socket_name>\n", argv[0]);
        return 2;
    }
    char nsfile[64];
    snprintf(nsfile, sizeof nsfile, "/proc/%s/ns/net", argv[2]);
    size_t nl = strlen(argv[3]);
    if (nl == 0 || nl > 100) { fprintf(stderr, "bad socket name\n"); return 2; }

    int ls = socket(AF_INET, SOCK_STREAM, 0);
    int one = 1;
    setsockopt(ls, SOL_SOCKET, SO_REUSEADDR, &one, sizeof one);
    struct sockaddr_in in;
    memset(&in, 0, sizeof in);
    in.sin_family = AF_INET;
    in.sin_port = htons((unsigned short)atoi(argv[1]));
    in.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    if (bind(ls, (struct sockaddr *)&in, sizeof in) < 0 || listen(ls, 8) < 0) { perror("listen"); return 1; }
    signal(SIGCHLD, SIG_IGN);

    for (;;) {
        int c = accept(ls, NULL, NULL);
        if (c < 0) continue;
        if (fork() != 0) { close(c); continue; }
        close(ls);
        int ns = open(nsfile, O_RDONLY);
        if (ns < 0 || setns(ns, CLONE_NEWNET) < 0) { perror("setns"); _exit(1); }
        close(ns);
        int u = socket(AF_UNIX, SOCK_STREAM, 0);
        struct sockaddr_un a;
        memset(&a, 0, sizeof a);
        a.sun_family = AF_UNIX;
        memcpy(a.sun_path + 1, argv[3], nl);
        if (connect(u, (struct sockaddr *)&a, (socklen_t)(offsetof(struct sockaddr_un, sun_path) + 1 + nl)) < 0) { perror("connect"); _exit(1); }
        relay(c, u);
        _exit(0);
    }
}