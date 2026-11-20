#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Embrith contributors
# init-prop-tolerant.py: property_service.cpp: tolerate fsetxattr failure (-2) when the property area is initialised. args: <property_service.cpp>
# Called by aosp/05-apply.sh (can be run again). The comment above the call in 05-apply.sh explains why.
import sys
p = sys.argv[1]
s = open(p).read()
if "KINDLE_PROP_TOLERANT" in s:
    print("property_service.cpp already patched"); sys.exit(0)
old = "if (__system_property_area_init()) {"
if s.count(old) != 1:
    i = s.find("__system_property_area_init")
    print("---- property_service.cpp: expected text not found exactly once; context: ----")
    print(s[max(0, i-300):i+500] if i >= 0 else "__system_property_area_init not found")
    sys.exit("property_service.cpp NOT patched - see the text above")
new = ('int kindle_r = __system_property_area_init(); /* KINDLE_PROP_TOLERANT */\n'
       '    ERROR("(kindle) property area init returned %d, errno %d\\n", kindle_r, errno);\n'
       '    if (kindle_r != 0 && kindle_r != -2) {')
open(p, "w").write(s.replace(old, new, 1))
print("property_service.cpp patched")
