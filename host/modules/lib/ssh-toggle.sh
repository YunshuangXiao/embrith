#!/bin/sh
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Embrith contributors
# embrith SSH 开关 v1.1.0
# 用法: ssh-toggle.sh [on|off|toggle|status|authinfo]
#       ssh-toggle.sh auth [embrith|koreader] key|none|password [\$k\$盐\$sha256hex|=]   (缺省来源 embrith; = 保留已有自定义密码; koreader 不支持自定义密码)
#       按来源单独控制(SSH 页面用, 来源: embrith|koreader, 各有自己的 pid 文件和端口):
#       ssh-toggle.sh on|off|status <来源>   ssh-toggle.sh port <来源> [端口]   ssh-toggle.sh list
# 登录方式($AUTHCONF): key=只用密钥(-s)  password=密码(有 HASH 用自定义, 否则 Kindle root 密码)  none=免密(embrith -B, koreader -n); 每个来源各存各的(MODE= / MODE_koreader=)
# 运行状态: 扫描所有 dropbear / sshd 进程, 有存活进程即视为运行中
# 查找顺序: embrith 内置(bin/dropbearmulti) -> KOReader dropbear -> Kindle 自带
# 内置版: /mnt/us 是 FAT 无执行位, 权限无效, 运行文件复制到 $RUN(tmpfs)再执行
# 主机密钥: 所有 dropbear 来源共用 $SHARED_HOSTKEY, 指纹不随来源变化
PATH=/sbin:/bin:/usr/sbin:/usr/bin:/usr/local/bin
export PATH

PORT=2222

# KOReader
KOREADER_DIR="/mnt/us/koreader"
KOREADER_DROPBEAR="$KOREADER_DIR/dropbear"
KOREADER_PIDFILE="/tmp/dropbear_koreader.pid"

# Embrith
KBASE="/mnt/us/embrith"                                  # user folder: settings, logs, ssh
KDEST=$(cd "$(dirname "$0")/../.." && pwd)               # program folder (this script is KDEST/modules/lib/): bin
KSSH="$KBASE/ssh"
EMBRITH_DROPBEAR="$KSSH/dropbear"
EMBRITH_MULTI="$KDEST/bin/dropbearmulti"
EMBRITH_PIDFILE="/var/run/embrith-dropbear.pid"
RUN="/var/tmp/embrith-ssh"

# Shared SSH directory
SSH_DIR="/mnt/us/.ssh"
SHARED_HOSTKEY="$SSH_DIR/dropbear_ecdsa_host_key"

# Kindle fallback
KINDLE_PIDFILE="/tmp/dropbear_toggle.pid"

LOGFILE="$KBASE/logs/ssh.log"
# SSH keys (SSH_MODE SSH_HASH SSH_MODE_koreader SSH_PORT_embrith SSH_PORT_koreader) live in the settings file; only keys starting with SSH_ are touched here
AUTHCONF="$KBASE/settings/custom.conf"
OLD_AUTHCONF="$KSSH/auth.conf"

SRC=""; BIN=""; KEYGEN=""
MODE=""; HASH=""
PIDS=""; MASTERS=""

mkdir -p "$KBASE/logs" 2>/dev/null
log() { echo "$(date '+%m-%d %H:%M:%S') $*" >> "$LOGFILE" 2>/dev/null; }

# 程序名(/proc/PID/comm)是否为 dropbear / dropbearmulti / sshd(sshd 会话进程 "sshd: user@pts/0" 的 comm 也是 sshd).
# 只用 shell 内建命令: scan() 对 /proc 下每个进程都调用一次, 以前每次都起 tr 和 head, 一次扫描就要几百个进程.
is_ssh_pid() {
    a=
    read -r a 2>/dev/null < "/proc/$1/comm" || return 1
    case "$a" in
        dropbear|dropbearmulti|sshd) return 0 ;;
    esac
    return 1
}

# 父进程号(/proc/PID/stat 中 ")" 之后第 2 个字段)
ppid_of() { l=; read -r l 2>/dev/null < "/proc/$1/stat"; l=${l##*) }; set -- $l; echo "$2"; }

# 扫描全部 SSH 进程: PIDS=全部, MASTERS=父进程不是 SSH 的监听进程(会话子进程不计)
scan() {
    PIDS=""; MASTERS=""
    for d in /proc/[0-9]*; do
        p=${d#/proc/}
        is_ssh_pid "$p" || continue
        PIDS="$PIDS $p"
        pp=$(ppid_of "$p")
        is_ssh_pid "$pp" || MASTERS="$MASTERS $p"
    done
    PIDS=${PIDS# }; MASTERS=${MASTERS# }
    [ -n "$PIDS" ]
}

# 选出可用来源, 结果写入 SRC / BIN / KEYGEN
pick() {
    if [ -f "$EMBRITH_MULTI" ] || [ -f "$EMBRITH_DROPBEAR" ]; then
        SRC=embrith; BIN="$RUN/dropbear"; KEYGEN="$RUN/dropbearkey"; return 0
    fi
    if [ -x "$KOREADER_DROPBEAR" ]; then
        SRC=koreader; BIN="$KOREADER_DROPBEAR"; KEYGEN=""
        [ -x "$KOREADER_DIR/dropbearkey" ] && KEYGEN="$KOREADER_DIR/dropbearkey"
        return 0
    fi
    for c in /mnt/us/usbnet/bin/dropbear /usr/sbin/dropbear /usr/bin/dropbear; do
        if [ -x "$c" ]; then
            SRC=kindle; BIN=$c; KEYGEN=""
            [ -x "${c%/*}/dropbearkey" ] && KEYGEN="${c%/*}/dropbearkey"
            return 0
        fi
    done
    for c in /usr/sbin/sshd /usr/bin/sshd; do
        if [ -x "$c" ]; then SRC=kindle-sshd; BIN=$c; KEYGEN=""; return 0; fi
    done
    SRC=""; BIN=""; KEYGEN=""
    return 1
}

open_firewall() {
    command -v iptables >/dev/null 2>&1 || return 0
    iptables -C INPUT -p tcp --dport "$PORT" -m conntrack --ctstate NEW,ESTABLISHED -j ACCEPT 2>/dev/null ||
        iptables -A INPUT -p tcp --dport "$PORT" -m conntrack --ctstate NEW,ESTABLISHED -j ACCEPT
    iptables -C OUTPUT -p tcp --sport "$PORT" -m conntrack --ctstate ESTABLISHED -j ACCEPT 2>/dev/null ||
        iptables -A OUTPUT -p tcp --sport "$PORT" -m conntrack --ctstate ESTABLISHED -j ACCEPT
}

close_firewall() {
    command -v iptables >/dev/null 2>&1 || return 0
    while iptables -D INPUT -p tcp --dport "$PORT" -m conntrack --ctstate NEW,ESTABLISHED -j ACCEPT 2>/dev/null; do :; done
    while iptables -D OUTPUT -p tcp --sport "$PORT" -m conntrack --ctstate ESTABLISHED -j ACCEPT 2>/dev/null; do :; done
}

# 内置版: 程序复制到 tmpfs 并建链接; 优先 bin/dropbearmulti, 其次 ssh/dropbear
prep_embrith() {
    mkdir -p "$RUN" && chmod 700 "$RUN" || return 1
    s="$EMBRITH_MULTI"; [ -f "$s" ] || s="$EMBRITH_DROPBEAR"
    cmp -s "$s" "$RUN/dropbearmulti" || cp "$s" "$RUN/dropbearmulti" || return 1
    chmod 755 "$RUN/dropbearmulti"
    for n in dropbear dropbearkey scp; do ln -sf dropbearmulti "$RUN/$n"; done
    # SFTP 子系统: dropbear 执行 SFTPSERVER_PATH(= $RUN/sftp-server); FAT 上不能执行, 复制到 tmpfs
    if [ -f "$KDEST/bin/sftp-server" ]; then
        cmp -s "$KDEST/bin/sftp-server" "$RUN/sftp-server" || cp "$KDEST/bin/sftp-server" "$RUN/sftp-server" || return 1
        chmod 755 "$RUN/sftp-server"
    fi
}

# 共享主机密钥: 不存在时用当前来源的 dropbearkey 生成; 运行时用 tmpfs 副本(600)
prep_hostkey() {
    mkdir -p "$SSH_DIR" || return 1
    if [ ! -s "$SHARED_HOSTKEY" ]; then
        [ -n "$KEYGEN" ] && [ -x "$KEYGEN" ] || { log "无 dropbearkey, 无法生成 $SHARED_HOSTKEY"; return 1; }
        "$KEYGEN" -t ecdsa -f "$SHARED_HOSTKEY" >/dev/null 2>>"$LOGFILE" || return 1
    fi
    mkdir -p "$RUN" && chmod 700 "$RUN" || return 1
    cp "$SHARED_HOSTKEY" "$RUN/hostkey" && chmod 600 "$RUN/hostkey"
}

# authorized_keys: $SSH_DIR 优先, 其次 $KSSH; 复制到 tmpfs 并设 600(FAT 上权限无效, dropbear 会拒绝)
prep_authkeys() {
    rm -f "$RUN/authorized_keys"
    for f in "$SSH_DIR/authorized_keys" "$KSSH/authorized_keys"; do
        if [ -s "$f" ]; then
            cp "$f" "$RUN/authorized_keys" && chmod 600 "$RUN/authorized_keys"
            return 0
        fi
    done
    log "无 authorized_keys, 只能用密码登录"
}

# 读登录方式; 缺省 password(不设自定义密码 = Kindle root 密码, 与旧版行为一致)
# 每个来源有自己的登录方式: embrith 用 MODE= / HASH=, koreader 用 MODE_koreader=(它的 dropbear 没有自定义密码, HASH 恒为空)
load_auth() {
    if [ "$1" = koreader ]; then
        MODE=$(sed -n 's/^SSH_MODE_koreader=//p' "$AUTHCONF" 2>/dev/null | head -n 1 | tr -d '\r')
        HASH=""
    else
        MODE=$(sed -n 's/^SSH_MODE=//p' "$AUTHCONF" 2>/dev/null | head -n 1 | tr -d '\r')
        HASH=$(sed -n 's/^SSH_HASH=//p' "$AUTHCONF" 2>/dev/null | head -n 1 | tr -d '\r')
        valid_hash "$HASH" || HASH=""
    fi
    case "$MODE" in key|password|none) ;; *) MODE=password ;; esac
}

# set or add one KEY=VALUE line of the settings file (atomic: tmp + rename), every other line is kept
conf_put() {
    mkdir -p "${AUTHCONF%/*}" || return 1
    { grep -v "^$1=" "$AUTHCONF" 2>/dev/null; echo "$1=$2"; } > "$AUTHCONF.tmp" && mv "$AUTHCONF.tmp" "$AUTHCONF"
}

# first run after the move: take the keys of the old ssh/auth.conf (MODE HASH MODE_koreader PORT_*) into the settings file; the old file stays
migrate_conf() {
    [ -f "$OLD_AUTHCONF" ] || return 0
    grep -q '^SSH_' "$AUTHCONF" 2>/dev/null && return 0
    grep -E '^(MODE|HASH|MODE_koreader|PORT_embrith|PORT_koreader)=' "$OLD_AUTHCONF" 2>/dev/null | while IFS='=' read -r k v; do
        conf_put "SSH_$k" "$v"
    done
    log "migrate $OLD_AUTHCONF -> $AUTHCONF"
}

valid_hash() { echo "$1" | grep -qE '^\$k\$[0-9a-f]{8,32}\$[0-9a-f]{64}$'; }

# 内置版的密码文件(dropbear 补丁读取): password+HASH 写哈希, none 写空文件, 其他删除
prep_passwd() {
    rm -f "$RUN/passwd"
    case "$MODE" in
        password) [ -n "$HASH" ] && { ( umask 077; echo "$HASH" > "$RUN/passwd" ); } ;;
        none)     ( umask 077; : > "$RUN/passwd" ) ;;
    esac
}

# 由 embrith.sh 处理 ctl/ssh_auth 时调用; 保存后若 SSH 在运行则重启使之生效
# 用法: set_auth <方式> [哈希|=] [来源, 缺省 embrith]. 哈希只对 embrith 有效: 空 = 改用 Kindle root 密码, "=" = 保留已有的自定义密码.
set_auth() {
    m=$1; h=$2; as=${3:-embrith}
    case "$m" in key|password|none) ;; *) echo "[ERROR] 未知登录方式: $m"; return 1 ;; esac
    valid_src "$as" || { echo "[ERROR] 未知来源: $as"; return 1; }
    load_auth embrith; EM=$MODE; EH=$HASH
    load_auth koreader; KM=$MODE
    if [ "$as" = koreader ]; then
        case "$h" in ''|=) ;; *) echo "[ERROR] KOReader 的 dropbear 不支持自定义密码"; return 1 ;; esac
        KM=$m
    else
        EM=$m
        if [ "$m" = password ]; then
            if [ "$h" = "=" ]; then :
            elif [ -n "$h" ]; then
                valid_hash "$h" || { echo "[ERROR] 密码格式不对"; return 1; }
                EH=$h
            else
                EH=""     # 不带哈希 = 改用 Kindle root 密码
            fi
        fi
    fi
    conf_put SSH_MODE "$EM" && conf_put SSH_HASH "$EH" && conf_put SSH_MODE_koreader "$KM" || return 1
    log "auth src=$as mode=$m custom_pw=$([ "$as" = embrith ] && [ -n "$EH" ] && echo 1 || echo 0)"
    echo "SSH_AUTH=$m SRC=$as"
    # only restart this source when it is running; never touch the other sources, never start one that is off
    if src_running "$as"; then
        stop_src "$as" >/dev/null; start_src "$as" >/dev/null
    fi
}

show_authinfo() {
    load_auth "$1"
    echo "$MODE $([ -n "$HASH" ] && echo 1 || echo 0)"
}

start_ssh() {
    if scan; then
        echo "SSH=1 PID=$MASTERS"
        echo "[INFO] SSH 已在运行(PID $MASTERS), 未重复启动"
        return 0
    fi
    if ! pick; then
        echo "SSH=-1"
        echo "[INFO] 未找到 dropbear / sshd(embrith, KOReader, Kindle 均无), 未启动"
        return 1
    fi

    valid_src "$SRC" && PORT=$(get_port "$SRC")
    case "$SRC" in
        kindle-sshd)
            log "系统 sshd 不支持 embrith 登录方式设置, 按其自身配置运行"
            "$BIN" </dev/null >/dev/null 2>>"$LOGFILE" ;;
        *)
            if [ "$SRC" = embrith ] && ! prep_embrith 2>>"$LOGFILE"; then
                echo "SSH=0 SRC=$SRC"
                echo "[ERROR] 内置 dropbear 准备失败, 见 $LOGFILE"
                return 1
            fi
            if ! prep_hostkey; then
                echo "SSH=0 SRC=$SRC"
                echo "[ERROR] 主机密钥不可用(来源 $SRC), 见 $LOGFILE"
                return 1
            fi
            case "$SRC" in
                embrith)   PF=$EMBRITH_PIDFILE ;;
                koreader) PF=$KOREADER_PIDFILE ;;
                *)        PF=$KINDLE_PIDFILE ;;
            esac
            load_auth "$SRC"
            OPT=""
            case "$MODE" in
                key)  OPT="-s" ;;
                none) OPT="-B" ;;
            esac
            if [ "$SRC" = embrith ]; then
                prep_authkeys; prep_passwd; OPT="$OPT -D $RUN"
            elif [ "$SRC" = koreader ]; then
                [ "$MODE" = none ] && OPT="-n"      # KOReader's patched dropbear: -n = no password check
            else
                [ "$MODE" = none ] && log "来源 $SRC 不支持免密, -B 只对空密码账户有效"
            fi
            open_firewall
            # shellcheck disable=SC2086
            # 后台进程不能继承调用者的 stdout/stdin, 否则 embrith.sh 的管道永远等不到 EOF, 看门狗卡死
            "$BIN" -E -p "$PORT" -P "$PF" -r "$RUN/hostkey" $OPT </dev/null >/dev/null 2>>"$LOGFILE" ;;
    esac

    sleep 1
    if scan; then
        echo "SSH=1 SRC=$SRC PID=$MASTERS"
        echo "[ON] SSH 已启动, 端口 $PORT, 来源 $SRC"
        log "start ok src=$SRC pid=$MASTERS"
    else
        echo "SSH=0 SRC=$SRC"
        echo "[ERROR] SSH 启动失败, 见 $LOGFILE"
        log "start fail src=$SRC"
        return 1
    fi
}

# 只停监听进程(已建立的会话保留, 避免切断当前 ssh 连接)
stop_ssh() {
    if ! scan; then
        close_firewall
        rm -f "$EMBRITH_PIDFILE" "$KOREADER_PIDFILE" "$KINDLE_PIDFILE"
        echo "SSH=0"
        echo "[OFF] SSH 未在运行"
        return 0
    fi
    if [ -z "$MASTERS" ]; then
        echo "SSH=1 PID=$PIDS"
        echo "[INFO] 只剩会话进程(PID $PIDS), 无监听进程可停"
        return 0
    fi
    # the global stop closes the firewall of every running source (each has its own port)
    for s in embrith koreader; do
        src_running "$s" && { PORT=$(eff_port "$s"); close_firewall; }
    done
    kill $MASTERS 2>/dev/null
    COUNT=0
    while [ "$COUNT" -lt 5 ]; do
        alive=""
        for p in $MASTERS; do kill -0 "$p" 2>/dev/null && alive="$alive $p"; done
        [ -z "$alive" ] && break
        sleep 1
        COUNT=$((COUNT + 1))
    done
    [ -n "$alive" ] && kill -9 $alive 2>/dev/null
    rm -f "$EMBRITH_PIDFILE" "$KOREADER_PIDFILE" "$KINDLE_PIDFILE" "$RUN/passwd"
    close_firewall
    log "stop pid=$MASTERS"
    echo "SSH=0"
    echo "[OFF] SSH 已停止(PID $MASTERS)"
}

show_status() {
    if scan; then
        echo "SSH=1 PID=$MASTERS"
    elif pick; then
        echo "SSH=0 SRC=$SRC"
    else
        echo "SSH=-1"
    fi
}

# ---- 按来源单独控制(lib/ssh-page.sh 用): 每个来源有自己的 pid 文件和端口(设置文件 custom.conf 里的 SSH_PORT_<来源>=) ----
DEFAULT_PORT=2222

valid_src() { case "$1" in embrith|koreader) return 0 ;; esac; return 1; }

src_pidfile() { case "$1" in embrith) echo "$EMBRITH_PIDFILE" ;; koreader) echo "$KOREADER_PIDFILE" ;; esac; }

src_ready() {
    case "$1" in
        embrith)  [ -f "$EMBRITH_MULTI" ] || [ -f "$EMBRITH_DROPBEAR" ] ;;
        koreader) [ -f "$KOREADER_DROPBEAR" ] ;;     # /mnt/us is FAT: the file may have no exec bit, start_src runs a copy then
        *)        return 1 ;;
    esac
}

src_running() {
    valid_src "$1" || return 1
    pf=$(src_pidfile "$1"); [ -s "$pf" ] || return 1
    p=$(head -n 1 "$pf" 2>/dev/null | tr -dc '0-9'); [ -n "$p" ] || return 1
    kill -0 "$p" 2>/dev/null && is_ssh_pid "$p"
}

# actual listening port of a running source (from its -p argument), else the configured one
eff_port() {
    if src_running "$1"; then
        p=$(head -n 1 "$(src_pidfile "$1")" 2>/dev/null | tr -dc '0-9')
        v=$(tr '\0' '\n' < "/proc/$p/cmdline" 2>/dev/null | sed -n '/^-p$/{n;p;q;}' | tr -dc '0-9')
        [ -n "$v" ] && { echo "$v"; return; }
    fi
    get_port "$1"
}

get_port() {
    valid_src "$1" || { echo "$DEFAULT_PORT"; return 1; }
    v=$(sed -n "s/^SSH_PORT_$1=//p" "$AUTHCONF" 2>/dev/null | head -n 1 | tr -dc '0-9')
    [ -n "$v" ] && [ "$v" -ge 1 ] && [ "$v" -le 65535 ] || v=$DEFAULT_PORT
    echo "$v"
}

set_port() {
    ws=$1; n=$(echo "$2" | tr -dc '0-9')
    valid_src "$ws" || { echo "[ERROR] 未知来源: $ws"; return 1; }
    { [ -n "$n" ] && [ "$n" -ge 1 ] && [ "$n" -le 65535 ]; } || { echo "[ERROR] 端口不对: $2"; return 1; }
    for o in embrith koreader; do
        [ "$o" = "$ws" ] && continue
        [ "$(eff_port "$o")" = "$n" ] && { echo "[ERROR] 端口 $n 已被 $o 使用"; return 1; }
    done
    conf_put "SSH_PORT_$ws" "$n" || return 1
    log "port $ws=$n"
    echo "PORT=$n SRC=$ws"
    if src_running "$ws"; then stop_src "$ws" >/dev/null; start_src "$ws"; fi
}

start_src() {
    ws=$1
    valid_src "$ws" && src_ready "$ws" || { echo "SSH=-1 SRC=$ws"; echo "[INFO] 来源 $ws 不可用"; return 1; }
    if src_running "$ws"; then echo "SSH=1 SRC=$ws PORT=$(get_port "$ws")"; return 0; fi
    PORT=$(get_port "$ws")
    for o in embrith koreader; do
        [ "$o" = "$ws" ] && continue
        if src_running "$o" && [ "$(eff_port "$o")" = "$PORT" ]; then
            echo "SSH=0 SRC=$ws"; echo "[ERROR] 端口 $PORT 已被 $o 使用, 请先改端口"; return 1
        fi
    done
    case "$ws" in
        embrith)
            SRC=embrith; BIN="$RUN/dropbear"; KEYGEN="$RUN/dropbearkey"; PF=$EMBRITH_PIDFILE
            prep_embrith 2>>"$LOGFILE" || { echo "SSH=0 SRC=$ws"; echo "[ERROR] 内置 dropbear 准备失败, 见 $LOGFILE"; return 1; } ;;
        koreader)
            SRC=koreader; BIN=$KOREADER_DROPBEAR; PF=$KOREADER_PIDFILE; KEYGEN=""
            if [ ! -x "$BIN" ]; then      # FAT without exec bit: run a copy from tmpfs
                mkdir -p "$RUN" && chmod 700 "$RUN" || return 1
                { cmp -s "$BIN" "$RUN/dropbear_ko" || cp "$BIN" "$RUN/dropbear_ko"; } && chmod 755 "$RUN/dropbear_ko" || return 1
                BIN=$RUN/dropbear_ko
            fi
            if [ -f "$KOREADER_DIR/dropbearkey" ]; then
                KEYGEN="$KOREADER_DIR/dropbearkey"
                if [ ! -x "$KEYGEN" ]; then
                    cp "$KEYGEN" "$RUN/dropbearkey_ko" && chmod 755 "$RUN/dropbearkey_ko" && KEYGEN=$RUN/dropbearkey_ko
                fi
            fi ;;
    esac
    prep_hostkey || { echo "SSH=0 SRC=$ws"; echo "[ERROR] 主机密钥不可用, 见 $LOGFILE"; return 1; }
    load_auth "$ws"
    OPT=""
    case "$MODE" in
        key)  OPT="-s" ;;
        none) OPT="-B" ;;
    esac
    if [ "$ws" = embrith ]; then
        prep_authkeys; prep_passwd; OPT="$OPT -D $RUN"
    fi
    # KOReader's dropbear is patched: -n = no password check, public keys from settings/SSH/authorized_keys relative to its directory
    [ "$ws" = koreader ] && [ "$MODE" = none ] && OPT="-n"
    open_firewall
    # shellcheck disable=SC2086
    if [ "$ws" = koreader ]; then
        ( cd "$KOREADER_DIR" && "$BIN" -E -p "$PORT" -P "$PF" -r "$RUN/hostkey" $OPT </dev/null >/dev/null 2>>"$LOGFILE" )
    else
        "$BIN" -E -p "$PORT" -P "$PF" -r "$RUN/hostkey" $OPT </dev/null >/dev/null 2>>"$LOGFILE"
    fi
    sleep 1
    if src_running "$ws"; then
        echo "SSH=1 SRC=$ws PORT=$PORT"
        log "start ok src=$ws port=$PORT"
    else
        echo "SSH=0 SRC=$ws"
        echo "[ERROR] SSH 启动失败, 见 $LOGFILE"
        log "start fail src=$ws port=$PORT"
        return 1
    fi
}

# 只停这个来源的监听进程(已建立的会话保留)
stop_src() {
    ws=$1
    valid_src "$ws" || { echo "SSH=-1 SRC=$ws"; return 1; }
    PORT=$(eff_port "$ws")
    if src_running "$ws"; then
        pf=$(src_pidfile "$ws"); p=$(head -n 1 "$pf" | tr -dc '0-9')
        kill "$p" 2>/dev/null
        n=0
        while [ "$n" -lt 5 ] && kill -0 "$p" 2>/dev/null; do sleep 1; n=$((n + 1)); done
        kill -0 "$p" 2>/dev/null && kill -9 "$p" 2>/dev/null
        log "stop src=$ws pid=$p"
    fi
    rm -f "$(src_pidfile "$ws")"
    [ "$ws" = embrith ] && rm -f "$RUN/passwd"
    close_firewall
    echo "SSH=0 SRC=$ws"
}

show_src() {
    valid_src "$1" || { echo "SSH=-1 SRC=$1"; return 1; }
    if src_running "$1"; then echo "SSH=1 SRC=$1 PORT=$(eff_port "$1")"
    elif src_ready "$1"; then echo "SSH=0 SRC=$1 PORT=$(get_port "$1")"
    else echo "SSH=-1 SRC=$1"; fi
}

# 每个来源一行(制表符分隔): 名字 可用 运行中 端口
list_src() {
    for s in embrith koreader; do
        r=0; src_ready "$s" && r=1
        u=0; src_running "$s" && u=1
        load_auth "$s"
        printf '%s\t%s\t%s\t%s\t%s\t%s\n' "$s" "$r" "$u" "$(eff_port "$s")" "$MODE" "$([ -n "$HASH" ] && echo 1 || echo 0)"
    done
}

# 进程累计 CPU 时间 "utime stime"(/proc/PID/stat 中 ")" 之后第 12, 13 个字段)
cpu_of() { sed 's/.*) //' "/proc/$1/stat" 2>/dev/null | cut -d' ' -f12,13 | tr ' ' +; }

# 清理不活动的 SSH 会话, 保留当前调用链上的会话:
#   终端会话: 其 pty 设备 $CLEAN_IDLE_MIN 分钟内无读写(find -mmin)
#   sftp 会话: 3 秒内 sftp-server 的 CPU 时间没有变化(sshfs 带 reconnect 时会自动重连)
# 休眠后客户端已断开的会话服务端不知情, 靠它回收
clean_sessions() {
    CLEAN_IDLE_MIN=30
    keep=" "; p=$$
    while [ -n "$p" ] && [ "$p" -gt 1 ]; do keep="$keep$p "; p=$(ppid_of "$p"); done
    n=0; SF=""
    for d in /proc/[0-9]*; do
        c=${d#/proc/}
        is_ssh_pid "$c" && continue
        pp=$(ppid_of "$c")
        is_ssh_pid "$pp" || continue
        case "$keep" in *" $pp "*) continue ;; esac
        a=$(tr '\0' '\n' < "$d/cmdline" 2>/dev/null | head -n 1)
        if [ "${a##*/}" = sftp-server ]; then
            SF="$SF $c:$pp:$(cpu_of "$c")"
        else
            t=$(readlink "$d/fd/0" 2>/dev/null)
            case "$t" in
                /dev/pts/*) [ -n "$(find "$t" -mmin +$CLEAN_IDLE_MIN 2>/dev/null)" ] && { kill "$pp" 2>/dev/null; n=$((n + 1)); } ;;
            esac
        fi
    done
    [ -n "$SF" ] && sleep 3
    for e in $SF; do
        c=${e%%:*}; r=${e#*:}; pp=${r%%:*}; old=${r#*:}
        [ "$(cpu_of "$c")" = "$old" ] && { kill "$pp" "$c" 2>/dev/null; n=$((n + 1)); }
    done
    log "clean idle sessions killed=$n"
    echo "[OK] 已清理 $n 个不活动会话"
}

migrate_conf
case "${1:-toggle}" in
    on)     if [ -n "$2" ]; then start_src "$2"; else start_ssh; fi ;;
    off)    if [ -n "$2" ]; then stop_src "$2"; else stop_ssh; fi ;;
    status) if [ -n "$2" ]; then show_src "$2"; else show_status; fi ;;
    port)   if [ -n "$3" ]; then set_port "$2" "$3"; else get_port "$2"; fi ;;
    list)   list_src ;;
    clean)  clean_sessions ;;
    auth)     case "$2" in embrith|koreader) set_auth "$3" "$4" "$2" ;; *) set_auth "$2" "$3" embrith ;; esac ;;
    authinfo) show_authinfo "$2" ;;
    toggle) if [ -n "$2" ]; then if src_running "$2"; then stop_src "$2"; else start_src "$2"; fi
            elif scan; then stop_ssh; else start_ssh; fi ;;
    *)      echo "用法: $0 [on|off|toggle|status|auth|authinfo|port|list|clean] [来源]"; exit 2 ;;
esac