#!/usr/bin/env python3
# webview-config.py: config_webview_packages.xml: accept the newer WebView (com.embrith.webview) next to the stock one. args: <xml> <webview-sig.txt>
# Called by aosp/05-apply.sh (can be run again). The comment above the call in 05-apply.sh explains why.
import os, sys
p, sf = sys.argv[1], sys.argv[2]
s = open(p).read()
if "com.embrith.webview" not in s:
    sig = open(sf).read().strip() if os.path.exists(sf) else ""
    tag = ("        <signature>" + sig + "</signature>\n") if sig else ""
    add = '    <webviewprovider description="Embrith WebView" packageName="com.embrith.webview" availableByDefault="false">\n' + tag + '    </webviewprovider>\n'
    s = s.replace("</webviewproviders>", add + "</webviewproviders>")
    open(p, "w").write(s)
