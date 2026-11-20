#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Embrith contributors
# ethernet-as-wifi.py: EthernetNetworkFactory.java: ethernet reports itself as Wi-Fi. args: <EthernetNetworkFactory.java>
# Called by aosp/05-apply.sh (can be run again). The comment above the call in 05-apply.sh explains why.
import sys
p = sys.argv[1]
s = open(p).read()
pairs = [
    ("ConnectivityManager.TYPE_ETHERNET", "ConnectivityManager.TYPE_WIFI"),
    ("NetworkCapabilities.TRANSPORT_ETHERNET", "NetworkCapabilities.TRANSPORT_WIFI"),
    ('NETWORK_TYPE = "Ethernet"', 'NETWORK_TYPE = "WIFI"'),
]
if not any(old in s for old, new in pairs):
    print("EthernetNetworkFactory already patched")
else:
    for old, new in pairs:
        s = s.replace(old, new)
    open(p, "w").write(s)
    print("EthernetNetworkFactory patched")
