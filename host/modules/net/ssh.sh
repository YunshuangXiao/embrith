# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Embrith contributors
# host/net/ssh.sh - SSH access (dropbear) through lib/ssh-toggle.sh.
# Sourced by host/embrith.sh.

# SSH(电脑连 Kindle): 全部交给 ssh-toggle.sh, 优先用内置 bin/dropbearmulti
ssh_state() {
  s=$(timeout 10 sh "$MOD/lib/ssh-toggle.sh" status </dev/null 2>/dev/null | sed -n 's/^SSH=\([-0-9]*\).*/\1/p' | head -1)
  echo "${s:--1}"
}

# 在看门狗循环里同步调用 ssh-toggle.sh: 输出先进文件, 带超时, 不用管道, 
# 防止它启动的后台进程占住管道导致看门狗卡死(退出/重启/状态都会停)
ssh_run() {
  o="$ST/ssh.out"
  timeout 30 sh "$MOD/lib/ssh-toggle.sh" "$@" </dev/null >"$o" 2>&1 || echo "[WARN] ssh-toggle.sh $1 退出码 $?" >>"$o"
  sed "s/^/$(date '+%m-%d %H:%M:%S') ctl: /" "$o" >>"$LOGDIR/ssh.log"   # same file as the log lines of ssh-toggle.sh
  while IFS= read -r l; do say "$l"; done <"$o"
  rm -f "$o"
}

# 登录方式: $1=key|none|password, $2=哈希(不写进日志)
ssh_auth() { ssh_run auth "$1" "$2"; publish_status; }
# KOReader 的登录方式($1=key|none|password): 它的 dropbear 没有自定义密码, password 就是系统密码
ssh_auth_k() { ssh_run auth koreader "$1"; publish_status; }

ssh_toggle() { ssh_run toggle; publish_status; }

# 按来源(embrith|koreader): 切换这个来源的开关 / 设置它的端口($1=来源 $2=端口)
ssh_toggle_src() { ssh_run toggle "$1"; publish_status; }
ssh_port_set() { ssh_run port "$1" "$2"; publish_status; }
# 清理不活动的 SSH 会话(安卓端"内存管理"触发)
ssh_clean() { ssh_run clean; }
