/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Embrith contributors */
/* ktrace: a tiny strace. Runs a command and prints the LAST syscalls it made before it exited or was killed.
 * usage: ktrace [-n lines] <command> [args...]       (default: the last 60 syscalls)
 * ARM EABI (the Kindle) and x86_64 (for testing on a PC) are supported.
 */
#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <signal.h>
#include <unistd.h>
#include <sys/ptrace.h>
#include <sys/wait.h>
#include <sys/user.h>

#if defined(__arm__)
typedef struct user_regs REGS;
#define SYSNO(r)  ((r).uregs[7])
#define ARG(r, i) ((r).uregs[i])
#define RET(r)    ((long)(r).uregs[0])
static const struct { int n; const char* name; } NAMES[] = {
 {1,"exit"},{2,"fork"},{3,"read"},{4,"write"},{5,"open"},{6,"close"},{11,"execve"},{12,"chdir"},{19,"lseek"},{20,"getpid"},
 {21,"mount"},{26,"ptrace"},{33,"access"},{37,"kill"},{41,"dup"},{42,"pipe"},{45,"brk"},{54,"ioctl"},{55,"fcntl"},{60,"umask"},
 {61,"chroot"},{63,"dup2"},{64,"getppid"},{78,"gettimeofday"},{85,"readlink"},{88,"reboot"},{91,"munmap"},{93,"ftruncate"},
 {114,"wait4"},{120,"clone"},{122,"uname"},{125,"mprotect"},{140,"_llseek"},{142,"_newselect"},{146,"writev"},{158,"sched_yield"},
 {162,"nanosleep"},{163,"mremap"},{168,"poll"},{172,"prctl"},{173,"rt_sigreturn"},{174,"rt_sigaction"},{175,"rt_sigprocmask"},
 {183,"getcwd"},{190,"vfork"},{191,"ugetrlimit"},{192,"mmap2"},{195,"stat64"},{196,"lstat64"},{197,"fstat64"},{199,"getuid32"},
 {200,"getgid32"},{201,"geteuid32"},{202,"getegid32"},{220,"madvise"},{221,"fcntl64"},{224,"gettid"},{240,"futex"},
 {248,"exit_group"},{256,"set_tid_address"},{263,"clock_gettime"},{268,"tgkill"},{281,"socket"},{283,"connect"},{322,"openat"},
 {327,"fstatat64"},{983045,"ARM_set_tls"},{983042,"ARM_breakpoint"},{983043,"ARM_cacheflush"},{-1,0}
};
#elif defined(__x86_64__)
typedef struct user_regs_struct REGS;
#define SYSNO(r)  ((r).orig_rax)
#define ARG(r, i) ((i)==0?(r).rdi:(i)==1?(r).rsi:(i)==2?(r).rdx:(i)==3?(r).r10:(i)==4?(r).r8:(r).r9)
#define RET(r)    ((long)(r).rax)
static const struct { int n; const char* name; } NAMES[] = {
 {0,"read"},{1,"write"},{2,"open"},{3,"close"},{9,"mmap"},{10,"mprotect"},{11,"munmap"},{12,"brk"},{21,"access"},{59,"execve"},
 {60,"exit"},{231,"exit_group"},{257,"openat"},{-1,0}
};
#else
#error "unsupported architecture"
#endif

static const char* sysname(long n) {
    static char buf[24];
    for (int i = 0; NAMES[i].n >= 0; i++) if (NAMES[i].n == n) return NAMES[i].name;
    snprintf(buf, sizeof buf, "sys_%ld", n);
    return buf;
}

static void read_str(pid_t pid, unsigned long addr, char* out, size_t max) {
    size_t i = 0;
    out[0] = 0;
    while (i + sizeof(long) < max) {
        errno = 0;
        long w = ptrace(PTRACE_PEEKDATA, pid, (void*)(addr + i), 0);
        if (errno) break;
        memcpy(out + i, &w, sizeof w);
        for (size_t k = 0; k < sizeof w; k++) if (!out[i + k]) return;
        i += sizeof w;
    }
    out[i] = 0;
}

#define RING 400
static char ring[RING][200];
static int rpos, rcount;
static void note(const char* fmt, ...) __attribute__((format(printf, 1, 2)));
#include <stdarg.h>
static void note(const char* fmt, ...) {
    va_list ap; va_start(ap, fmt);
    vsnprintf(ring[rpos], sizeof ring[0], fmt, ap);
    va_end(ap);
    rpos = (rpos + 1) % RING; if (rcount < RING) rcount++;
}

int main(int argc, char** argv) {
    int want = 60, a = 1;
    if (argc > 2 && !strcmp(argv[1], "-n")) { want = atoi(argv[2]); a = 3; }
    if (a >= argc) { fprintf(stderr, "usage: ktrace [-n lines] cmd [args...]\n"); return 2; }
    if (want > RING) want = RING;

    pid_t pid = fork();
    if (pid < 0) { perror("fork"); return 1; }
    if (pid == 0) {
        ptrace(PTRACE_TRACEME, 0, 0, 0);
        raise(SIGSTOP);
        execv(argv[a], argv + a);
        fprintf(stderr, "ktrace: execv %s: %s\n", argv[a], strerror(errno));
        _exit(127);
    }
    int st;
    waitpid(pid, &st, 0);
    ptrace(PTRACE_SETOPTIONS, pid, 0, PTRACE_O_TRACESYSGOOD | PTRACE_O_EXITKILL);

    int in_syscall = 0; long lastno = -1; char path[96] = "";
    unsigned long args[3] = {0, 0, 0};
    int final_status = 0, ended = 0;
    for (;;) {
        ptrace(PTRACE_SYSCALL, pid, 0, 0);
        if (waitpid(pid, &st, 0) < 0) break;
        if (WIFEXITED(st))   { note("== process exited with status %d", WEXITSTATUS(st)); final_status = WEXITSTATUS(st); ended = 1; break; }
        if (WIFSIGNALED(st)) { note("== process KILLED by signal %d%s", WTERMSIG(st), WCOREDUMP(st) ? " (core)" : ""); final_status = 128 + WTERMSIG(st); ended = 1; break; }
        if (!WIFSTOPPED(st)) continue;
        int sig = WSTOPSIG(st);
        if (sig == (SIGTRAP | 0x80)) {
            REGS r;
#if defined(__arm__)
            if (ptrace(PTRACE_GETREGS, pid, 0, &r) < 0) continue;
#else
            if (ptrace(PTRACE_GETREGS, pid, 0, &r) < 0) continue;
#endif
            if (!in_syscall) {
                in_syscall = 1; lastno = (long)SYSNO(r);
                for (int i = 0; i < 3; i++) args[i] = (unsigned long)ARG(r, i);
                path[0] = 0;
                const char* nm = sysname(lastno);
                if (!strcmp(nm, "open") || !strcmp(nm, "access") || !strcmp(nm, "execve") || !strcmp(nm, "stat64") ||
                    !strcmp(nm, "readlink") || !strcmp(nm, "chdir") || !strcmp(nm, "mount")) read_str(pid, args[0], path, sizeof path);
                else if (!strcmp(nm, "openat") || !strcmp(nm, "fstatat64")) read_str(pid, args[1], path, sizeof path);
                note("-> %s(%#lx, %#lx, %#lx)%s%s%s", nm, args[0], args[1], args[2], path[0] ? "  \"" : "", path, path[0] ? "\"" : "");
            } else {
                in_syscall = 0;
                long rv = RET(r);
                note("   %s = %ld%s", sysname(lastno), rv, rv < 0 && rv > -4096 ? " (error)" : "");
            }
            continue;
        }
        /* a signal arrives: record it and pass it on */
        note("** signal %d delivered", sig);
        ptrace(PTRACE_SYSCALL, pid, 0, (void*)(long)(sig == SIGSTOP || sig == SIGTRAP ? 0 : sig));
        if (waitpid(pid, &st, 0) < 0) break;
        if (WIFEXITED(st))   { note("== process exited with status %d", WEXITSTATUS(st)); final_status = WEXITSTATUS(st); ended = 1; break; }
        if (WIFSIGNALED(st)) { note("== process KILLED by signal %d%s", WTERMSIG(st), WCOREDUMP(st) ? " (core)" : ""); final_status = 128 + WTERMSIG(st); ended = 1; break; }
        /* resume loop: the stop we just got is handled in the next iteration by re-issuing PTRACE_SYSCALL */
        if (WIFSTOPPED(st) && WSTOPSIG(st) == (SIGTRAP | 0x80)) { in_syscall = !in_syscall; }
    }
    (void)ended;
    int start = rcount > want ? rcount - want : 0;
    printf("== last %d of %d events\n", rcount - start, rcount);
    for (int i = start; i < rcount; i++) {
        int idx = (rpos - rcount + i + RING * 2) % RING;
        puts(ring[idx]);
    }
    return final_status;
}
