#!/bin/bash
# 只读: 收集 init 里和 SELinux 相关的源码, 输出到 ~/init-selinux.txt
cd ~/aosp/system/core/init || exit 1
{
echo "== defines"; grep -rn "ALLOW_DISABLE_SELINUX\|ALLOW_PERMISSIVE_SELINUX" . ../../../build/core/*.mk Android.mk 2>/dev/null | head -20
echo "== uses"; grep -n "selinux" init.cpp | head -80
echo "== selinux_initialize"; grep -n "selinux_initialize" -A70 init.cpp | head -140
echo "== main (first part)"; grep -n "int main" -A110 init.cpp | head -170
} > ~/init-selinux.txt 2>&1
wc -c ~/init-selinux.txt; echo "已写入 ~/init-selinux.txt"
