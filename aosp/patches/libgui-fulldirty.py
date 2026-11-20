#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Embrith contributors
# libgui-fulldirty.py: Surface::lock redraws the whole buffer on every lock (default on).
# Software-rendered apps copy the area outside the dirty rectangle back from the previous buffer; on this device that leaves black
# areas (book covers) until a full redraw. debug.embrith.fulldirty=0 turns the full redraw off (read once per app process).
# The switch "full redraw" in settings, advanced, sets the property through host/modules/install/webview.sh (fulldirty_apply).
# usage: python3 libgui-fulldirty.py <frameworks/native/libs/gui/Surface.cpp>   (called by aosp/05-apply.sh, can be run again)
import sys

p = sys.argv[1]
s = open(p).read()
if "KINDLE_FULLDIRTY" in s:
    print("libgui Surface.cpp already patched")
    sys.exit(0)
if "debug.embrith.fulldirty" in s:
    sys.exit("libgui Surface.cpp has an older hand-applied patch: revert it first (patch -R), then run this again")
inc = "#include <utils/Log.h>\n"
anc = "        // figure out if we can copy the frontbuffer back\n"
if s.count(inc) != 1 or s.count(anc) != 1:
    sys.exit("libgui Surface.cpp: anchors not found exactly once, not patched")
blk = (
    "        // KINDLE_FULLDIRTY: redraw the whole buffer on every lock; debug.embrith.fulldirty=0 turns it off (read once per process)\n"
    "        static int fullDirty = -1;\n"
    "        if (fullDirty < 0) {\n"
    "            char v[PROPERTY_VALUE_MAX];\n"
    "            property_get(\"debug.embrith.fulldirty\", v, \"1\");\n"
    "            fullDirty = (v[0] == '0') ? 0 : 1;\n"
    "        }\n"
    "        if (fullDirty) {\n"
    "            newDirtyRegion.set(bounds);\n"
    "        }\n\n")
s = s.replace(inc, inc + "#include <cutils/properties.h>\n", 1).replace(anc, blk + anc, 1)
open(p, "w").write(s)
print("libgui Surface.cpp patched")
