#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Embrith contributors
# init-service-no-selinux.py: service.cpp / util.cpp: services start without SELinux contexts. args: <service.cpp> <util.cpp>
# Called by aosp/05-apply.sh (can be run again). The comment above the call in 05-apply.sh explains why.
import sys
svc, util = sys.argv[1], sys.argv[2]
a = open(svc).read(); b = open(util).read()
if "KINDLE_NO_SELINUX_SVC" in a and "KINDLE_NO_SELINUX_SVC" in b:
    print("service.cpp/util.cpp already patched"); sys.exit(0)
edits_a = [
 ("    std::string scon;\n    if (!seclabel_.empty()) {\n        scon = seclabel_;\n    } else {\n        char* mycon = nullptr;",
  "    std::string scon;\n    if (true) { /* KINDLE_NO_SELINUX_SVC: no SELinux contexts in the container */\n    } else {\n        char* mycon = nullptr;"),
 ("if (setexeccon(seclabel_.c_str()) < 0) {",
  "if (false && setexeccon(seclabel_.c_str()) < 0) { /* KINDLE_NO_SELINUX_SVC */"),
]
edits_b = [
 ("if (setsockcreatecon(socketcon) == -1) {",
  "if (false && setsockcreatecon(socketcon) == -1) { /* KINDLE_NO_SELINUX_SVC */"),
]
def check(text, edits, name):
    for old, new in edits:
        if text.count(old) != 1:
            print("---- %s: expected text not found exactly once: ----" % name); print(old)
            i = text.find("setexeccon" if "exec" in old else ("setsockcreatecon" if "sock" in old else "seclabel_"))
            print("---- context ----"); print(text[max(0, i-400):i+500] if i >= 0 else "(marker not found)")
            sys.exit("%s NOT patched - see the text above" % name)
check(a, edits_a, "service.cpp"); check(b, edits_b, "util.cpp")
for old, new in edits_a: a = a.replace(old, new, 1)
for old, new in edits_b: b = b.replace(old, new, 1)
open(svc, "w").write(a); open(util, "w").write(b)
print("service.cpp/util.cpp patched")
