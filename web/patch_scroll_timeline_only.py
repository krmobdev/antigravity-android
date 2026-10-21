#!/usr/bin/env python3
"""Diagnostic A/B: disable only the v2.4.0 OverlayScrollbar ScrollTimeline path.

Do not alter Chromium globals, native fling, CSS, or checked-in web/main.js.
This fail-closed transformation runs on generated/main.js only.
"""
from pathlib import Path
import sys

ANCHOR = 'function G$a(){var a=globalThis.ScrollTimeline;return typeof a==="function"?a:null}'
PATCH = 'function G$a(){return null/* AGY_SCROLL_TIMELINE_ONLY_AB */}'

def main() -> int:
    if len(sys.argv) != 2:
        print("Usage: patch_scroll_timeline_only.py generated/main.js", file=sys.stderr)
        return 2
    path = Path(sys.argv[1])
    text = path.read_text(encoding="utf-8")
    if text.count(ANCHOR) != 1 or PATCH in text:
        print("Refusing patch: expected exactly one original G$a() anchor", file=sys.stderr)
        return 1
    modified = text.replace(ANCHOR, PATCH, 1)
    assert len(modified) == len(text) - len(ANCHOR) + len(PATCH)
    path.write_text(modified, encoding="utf-8")
    print("PASS isolated ScrollTimeline A/B: OverlayScrollbar G$a() returns null")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
