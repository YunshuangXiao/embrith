#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Embrith contributors
# alarm-fallback.py: AlarmManagerService.java: the handler fallback delivers alarms like AlarmThread does. args: <AlarmManagerService.java>
# Called by aosp/05-apply.sh (can be run again). The comment above the call in 05-apply.sh explains why.
import re, sys
p = sys.argv[1]
s = open(p).read()
if "KINDLE_ALARM_FALLBACK" in s:
    print("AlarmManagerService.java already patched"); sys.exit(0)
if not re.search(r'void\s+deliverAlarmsLocked\(\s*ArrayList<Alarm>\s+\w+,\s*long\s+\w+\)', s) or "void rescheduleKernelAlarmsLocked()" not in s:
    for m in re.finditer(r'.*(deliverAlarmsLocked|rescheduleKernelAlarmsLocked)\s*\(.*', s):
        print(m.group(0).strip())
    sys.exit("AlarmManagerService: deliverAlarmsLocked / rescheduleKernelAlarmsLocked do not look as expected - see the lines above")
pat = re.compile(r'(case ALARM_EVENT: \{\n)(.*?)(\n                    break;\n                \})', re.S)
m = pat.search(s)
if not m or "alarm.operation.send();" not in m.group(2):
    sys.exit("AlarmManagerService: the ALARM_EVENT handler does not look as expected - check sed -n 2605,2640p of the file")
new = m.group(1) + """                    /* KINDLE_ALARM_FALLBACK: see 05-apply.sh. Deliver like AlarmThread does (PendingIntent and listener alarms). */
                    ArrayList<Alarm> triggerList = new ArrayList<Alarm>();
                    synchronized (mLock) {
                        final long nowRTC = System.currentTimeMillis();
                        final long nowELAPSED = SystemClock.elapsedRealtime();
                        triggerAlarmsLocked(triggerList, nowELAPSED, nowRTC);
                        deliverAlarmsLocked(triggerList, nowELAPSED);
                        rescheduleKernelAlarmsLocked();
                        updateNextAlarmClockLocked();
                    }""" + m.group(3)
s = s[:m.start()] + new + s[m.end():]
open(p, "w").write(s)
print("AlarmManagerService.java patched")
