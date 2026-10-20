#!/usr/bin/env python3
"""Verify that actual signed APK ships v2.4 stream, sidebar and notification hooks."""
from pathlib import Path
import hashlib
import sys
from zipfile import ZipFile

if len(sys.argv) != 2:
    raise SystemExit("usage: verify_integration_apk.py path/to/dev.apk")
repo = Path(__file__).resolve().parents[2]
apk = Path(sys.argv[1])
source = repo / "web/main.js"
base = __import__("subprocess").check_output(
    ["git", "show", "HEAD:web/main.js"], cwd=repo)
assert source.read_bytes() == base, "checked-in upstream web/main.js was modified"
with ZipFile(apk) as zipf:
    files = zipf.namelist()
    webmain = [x for x in files if x.endswith("/generated/main.js")]
    patches = [x for x in files if x.endswith("/generated/patches/web_optimizations.js")]
    assert len(webmain) == 1, webmain
    assert len(patches) == 1, patches
    data = zipf.read(webmain[0])
    aux = zipf.read(patches[0])
    for item in (b"AGY_V24_TRANSPORT_RECONNECT",
                 b"AGY_V24_SUMMARY_RECONNECT", b"AGY_V24_DEEPLINK",
                 b"AGY_V24_VISIBLE_READ_RECEIPT", b"function agyV24ReadReceipt(",
                 b"window.__AGY_NOTIFY_AGENT_STATE__"):
        assert item in data, "missing main patch: " + item.decode()
    for item in (b"__AGY_NOTIFY_AGENT_STATE__",
                 b"__AGY_VERIFIED_AGENT_GET__",
                 b"__AG_NATIVE_BRIDGE_TOKEN__"):
        assert item in aux, "missing notification/sidebar fragment: " + item.decode()
    assert b"AGY_RESUME_STREAM_V1" not in data
    assert b"onConversationSummary(window.__AG_NATIVE_BRIDGE_TOKEN__" in aux
    # Deployment must contain upstream modular runtime and source-independent build results.
    assert any(x.endswith("/runtime/bin/liblanguage_server.so") for x in files)
    assert any(x.endswith("/generated/bridge.core.js") for x in files)
    print("PASS signed APK staged web Hca/NDa/deeplink/sidebar/notifications")
    print("main bundle:", webmain[0], len(data), "bytes")
    print("web patches:", patches[0], len(aux), "bytes")
    print("web/main.js unchanged SHA-256:", hashlib.sha256(source.read_bytes()).hexdigest())
    print("APK SHA-256:", hashlib.sha256(apk.read_bytes()).hexdigest())
