#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Embrith contributors
# init-mmap-rnd.py: init.cpp: 'Unable to set adequate mmap entropy value' must not be fatal. args: <init.cpp>
# Called by aosp/05-apply.sh (can be run again). The comment above the call in 05-apply.sh explains why.
import sys, re
p = sys.argv[1]
s = open(p).read()
if "KINDLE_MMAP_RND" in s:
    print("init.cpp mmap_rnd already patched"); sys.exit(0)
pat = re.compile(r'(ERROR\("Unable to set adequate mmap entropy value!\\n"\);\s*)security_failure\(\);')
if len(pat.findall(s)) != 1:
    i = s.find("adequate mmap entropy")
    print("---- init.cpp: mmap entropy failure text not found exactly once; context: ----")
    print(s[max(0, i-1200):i+400] if i >= 0 else "'adequate mmap entropy' not found")
    sys.exit("init.cpp mmap_rnd NOT patched - see the text above")
s = pat.sub(r'\1/* KINDLE_MMAP_RND: not fatal in the container */', s, count=1)
open(p, "w").write(s)
print("init.cpp mmap_rnd patched")
