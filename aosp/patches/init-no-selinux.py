#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Embrith contributors
# init-no-selinux.py: init.cpp: never load/reload an SELinux policy into the Kindle kernel. args: <init.cpp>
# Called by aosp/05-apply.sh (can be run again). The comment above the call in 05-apply.sh explains why.
import sys
p = sys.argv[1]
s = open(p).read()
if "KINDLE_NO_SELINUX" in s:
    print("init.cpp already patched"); sys.exit(0)
restorecon_block = '''    NOTICE("Running restorecon...\\n");
    restorecon("/dev");
    restorecon("/dev/socket");
    restorecon("/dev/__properties__");
    restorecon("/property_contexts");
    restorecon_recursive("/sys");
'''
edits = [
 ("    if (in_kernel_domain) {\n", "    if (false && in_kernel_domain) { /* KINDLE_NO_SELINUX: never load a policy into the Kindle kernel */\n"),
 ('        if (restorecon("/init") == -1) {\n', '        if (false && restorecon("/init") == -1) { /* KINDLE_NO_SELINUX */\n'),
 (restorecon_block, '    NOTICE("(kindle) restorecon skipped\\n"); /* KINDLE_NO_SELINUX */\n'),
 ("    if (selinux_android_reload_policy() == -1) {\n", "    if (true) { return 0; } /* KINDLE_NO_SELINUX: never reload a policy */\n    if (selinux_android_reload_policy() == -1) {\n"),
]
for old, new in edits:
    if s.count(old) != 1:
        print("---- init.cpp: expected text not found exactly once: ----")
        print(old)
        sys.exit("init.cpp NOT patched")
for old, new in edits:
    s = s.replace(old, new, 1)
open(p, "w").write(s)
print("init.cpp patched")
