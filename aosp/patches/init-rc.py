#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Embrith contributors
# init-rc.py: init.rc: our rc first, no ueventd/console, healthd not critical, never reload an SELinux policy. args: <init.rc>
# Called by aosp/05-apply.sh (can be run again). The comment above the call in 05-apply.sh explains why.
import sys
p = sys.argv[1]
s = open(p).read()
edits = [  # (old, new, text that proves it was already applied)
 ("import /init.environ.rc\n", "import /init.kindle.rc\nimport /init.environ.rc\n", "import /init.kindle.rc"),
 ("\n    start ueventd\n", "\n    # (kindle) ueventd is not used in the container\n", "(kindle) ueventd is not used"),
 ("service healthd /sbin/healthd\n    class core\n    critical\n", "service healthd /sbin/healthd\n    class core\n", None),
 ("    start console\n", "    # (kindle) start console\n", "# (kindle) start console"),
 ("service ueventd /sbin/ueventd\n    class core\n    critical\n", "service ueventd /sbin/ueventd\n    class core\n    critical\n    disabled\n", "service ueventd /sbin/ueventd\n    class core\n    critical\n    disabled"),
 ("    setprop selinux.reload_policy 1\n", "    # (kindle) setprop selinux.reload_policy 1\n", "# (kindle) setprop selinux.reload_policy 1"),
 ("\n    init_user0\n", "\n    # (kindle) init_user0 (waits for vold, which starts later)\n", "# (kindle) init_user0"),
 ("\n    class_start core\n", "\n    trigger embrith-core\n", "trigger embrith-core"),
 ("    start vold\n    installkey /data\n", "    trigger embrith-vold\n    installkey /data\n", "trigger embrith-vold"),
]
changed = 0
for old, new, proof in edits:
    if proof and proof in s:
        continue
    if old in s:
        s = s.replace(old, new, 1); changed += 1
    elif proof is None:
        continue   # healthd edit: already applied (the old text is gone)
    else:
        sys.exit("init.rc: expected text not found: %r" % old)
open(p, "w").write(s)
print("init.rc patched (%d edits)" % changed)
