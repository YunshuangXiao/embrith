#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Embrith contributors# libselinux-no-selinux.py: libselinux android.c: restorecon / setfilecon do nothing when SELinux is not active. args: <android.c 7.x> <android.c 8+> <external dir>
# Called by aosp/05-apply.sh (can be run again). The comment above the call in 05-apply.sh explains why.
import os, re, sys
# Android 7.x keeps it in external/libselinux, Android 8+ in external/selinux/libselinux: try both, then search.
cands = [c for c in sys.argv[1:3] if os.path.isfile(c)]
if not cands:
    for root, dirs, files in os.walk(sys.argv[3]):
        if "android.c" in files and "selinux" in root and root.endswith("src"):
            cands.append(os.path.join(root, "android.c")); break
if not cands:
    sys.exit("libselinux android.c not found under external/ - run diag/16-inspect-selinux.sh and check its output")
p = cands[0]
print("libselinux source: " + p)
s = open(p).read()
if "KINDLE_NO_SELINUX_LIB" in s:
    print("libselinux android.c already patched"); sys.exit(0)
pat = re.compile(r'^((?:static[ \t]+)?int[ \t]+(?:selinux_android_restorecon\w*|selinux_android_setfilecon\w*)[ \t]*\([^)]*\)[ \t\r\n]*\{[ \t]*\n)', re.M)
names = []
def repl(m):
    names.append(re.search(r'(selinux_android_\w+)', m.group(1)).group(1))
    return m.group(1) + "\tif (is_selinux_enabled() <= 0) return 0; /* KINDLE_NO_SELINUX_LIB: container without a loaded policy */\n"
s2 = pat.sub(repl, s)
if not names:
    sys.exit("no selinux_android_restorecon*/setfilecon* function definition found - run diag/16-inspect-selinux.sh and check its output")
open(p, "w").write(s2)
print("libselinux android.c patched: " + ", ".join(names))
