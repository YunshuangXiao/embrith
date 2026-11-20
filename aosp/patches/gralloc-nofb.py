#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Embrith contributors
# gralloc-nofb.py: gralloc.cpp: never take the /dev/graphics/fb0 path. args: <gralloc.cpp>
# Called by aosp/05-apply.sh (can be run again). The comment above the call in 05-apply.sh explains why.
import sys
p = sys.argv[1]
s = open(p).read()
old = "    if (usage & GRALLOC_USAGE_HW_FB) {\n        err = gralloc_alloc_framebuffer(dev, size, usage, pHandle);"
new = "    if (0 && (usage & GRALLOC_USAGE_HW_FB)) { /* kindle: no fbdev, always plain buffers */\n        err = gralloc_alloc_framebuffer(dev, size, usage, pHandle);"
if new in s:
    print("gralloc already patched")
elif old in s:
    open(p, "w").write(s.replace(old, new)); print("gralloc patched")
else:
    sys.exit("gralloc.cpp: expected text not found, not patched")
