#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Embrith contributors
# back-to-home.py: Activity.onBackPressed: the last back of a task (the root activity) moves the task to the back, so the home screen shows, instead of finishing the app.
# Only the default path is changed (apps that call super.onBackPressed). The home app itself keeps its own behaviour.
# usage: python3 back-to-home.py <frameworks/base/core/java/android/app/Activity.java>   (called by aosp/05-apply.sh, can be run again)
import re, sys

p = sys.argv[1]
s = open(p).read()
if "KINDLE_BACK_HOME" in s:
    print("Activity.java already patched")
    sys.exit(0)
pat = re.compile(r'(public void onBackPressed\(\) \{.*?popBackStackImmediate\(\)\) \{\n)(\s*)(finishAfterTransition\(\);\n)', re.S)
m = pat.search(s)
if not m or s.count("public void onBackPressed()") != 1:
    sys.exit("Activity.java: onBackPressed does not look as expected - check it by hand, not patched")
ind = m.group(2)
new = (m.group(1)
       + ind + "// KINDLE_BACK_HOME: the last back of a task goes to the home screen, the app stays in the background\n"
       + ind + "if (isTaskRoot() && !\"com.embrith.home\".equals(getPackageName())) {\n"
       + ind + "    moveTaskToBack(true);\n"
       + ind + "} else {\n"
       + ind + "    finishAfterTransition();\n"
       + ind + "}\n")
s = s[:m.start()] + new + s[m.end():]
open(p, "w").write(s)
print("Activity.java patched")