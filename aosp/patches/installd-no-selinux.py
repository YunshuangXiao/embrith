#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Embrith contributors
# installd-no-selinux.py: installd commands.cpp: restorecon_app_data_lazy is a no-op when SELinux is not active. args: <commands.cpp>
# Called by aosp/05-apply.sh (can be run again). The comment above the call in 05-apply.sh explains why.
import re, sys
p = sys.argv[1]
s = open(p).read()
if "KINDLE_NO_SELINUX_INSTALLD" in s:
    print("installd commands.cpp already patched"); sys.exit(0)
old = "static int restorecon_app_data_lazy(const std::string& path, const char* seinfo, uid_t uid) {\n"
if s.count(old) != 1:
    i = s.find("restorecon_app_data_lazy")
    print(s[max(0, i-300):i+600])
    sys.exit("installd: restorecon_app_data_lazy signature not found exactly once - see the text above")
s = s.replace(old, old + "    if (is_selinux_enabled() <= 0) return 0; /* KINDLE_NO_SELINUX_INSTALLD: no labels in this container */\n", 1)
if "#include <selinux/selinux.h>" not in s:
    s = s.replace("#include <selinux/android.h>\n", "#include <selinux/android.h>\n#include <selinux/selinux.h>\n", 1)
open(p, "w").write(s)
print("installd commands.cpp patched")
