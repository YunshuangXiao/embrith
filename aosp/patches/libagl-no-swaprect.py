#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Embrith contributors
# libagl-no-swaprect.py: libagl egl.cpp: no EGL_ANDROID_swap_rectangle. args: <egl.cpp>
# Called by aosp/05-apply.sh (can be run again). The comment above the call in 05-apply.sh explains why.
import sys
p = sys.argv[1]
s = open(p).read()
old = '        "EGL_ANDROID_swap_rectangle "\n'
new = '        // "EGL_ANDROID_swap_rectangle " /* KINDLE_NO_SWAP_RECT */\n'
if "KINDLE_NO_SWAP_RECT" in s:
    print("libagl egl.cpp already patched")
elif s.count(old) == 1:
    open(p, "w").write(s.replace(old, new, 1)); print("libagl egl.cpp patched")
else:
    sys.exit("libagl egl.cpp: swap_rectangle line not found exactly once, not patched")
