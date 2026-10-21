#!/usr/bin/env python3
"""Fail-closed, diagnostic-only event instrumentation of v2.4 chat scroll handlers."""
from pathlib import Path
import sys

path = Path(sys.argv[1])
source = path.read_text(encoding="utf-8")
patches = [
    (
        'var LN=({threshold:a=25,smoothScroll:b=!1,contentChangeDetector:c,disabled:e=!1,onAutoScrollChange:f,onContentResize:g,showScrollToBottomButton:h=!1,scrollToBottomThreshold:k=100,getDistanceFromBottom:l}={})=>{',
        'var LN=({threshold:a=25,smoothScroll:b=!1,contentChangeDetector:c,disabled:e=!1,onAutoScrollChange:f,onContentResize:g,showScrollToBottomButton:h=!1,scrollToBottomThreshold:k=100,getDistanceFromBottom:l}={})=>{'
    ),
    (
        'var H=Math.min(t.current,G.scrollTop);b?G.scrollTo({top:G.scrollHeight,behavior:"smooth"}):G.scrollTo({top:G.scrollHeight,behavior:"instant"});',
        'var H=Math.min(t.current,G.scrollTop);window.__AGY_SCROLL_EVT__?.("AUTO_SCROLL_COMMAND",G,[q.current?1:0,r.current?1:0,b?1:0,t.current]);b?G.scrollTo({top:G.scrollHeight,behavior:"smooth"}):G.scrollTo({top:G.scrollHeight,behavior:"instant"});'
    ),
    (
        'J<t.current&&!M?(q.current=!1,JN.delete(H)):K&&(q.current=\n!0);N!==q.current&&x.current?.(q.current);E(J);t.current=J;',
        'J<t.current&&!M?(q.current=!1,JN.delete(H)):K&&(q.current=\n!0);window.__AGY_SCROLL_EVT__?.("CHAT_SCROLL_HANDLER",H,[N?1:0,q.current?1:0,K?1:0,M?1:0,t.current,J]);N!==q.current&&x.current?.(q.current);E(J);t.current=J;'
    ),
    (
        'new ResizeObserver(()=>{q.current&&!r.current?D(!0):(y.current?.(),E())});H.observe(G);',
        'new ResizeObserver(()=>{window.__AGY_SCROLL_EVT__?.("CHAT_RESIZE_OBSERVER",F,[q.current?1:0,r.current?1:0]);q.current&&!r.current?D(!0):(y.current?.(),E())});H.observe(G);'
    ),
    (
        '(0,A.useLayoutEffect)(()=>{e||(q.current&&!r.current?D(!0):(y.current?.(),E()))},[c,D,e,E]);',
        '(0,A.useLayoutEffect)(()=>{window.__AGY_SCROLL_EVT__?.("CHAT_LAYOUT_EFFECT",m.current,[e?1:0,q.current?1:0,r.current?1:0]);e||(q.current&&!r.current?D(!0):(y.current?.(),E()))},[c,D,e,E]);'
    ),
    (
        'c=(0,A.useCallback)(()=>\n{m.current&&JN.delete(m.current);w(!1);',
        'c=(0,A.useCallback)(()=>\n{window.__AGY_SCROLL_EVT__?.("FORCE_BOTTOM_BUTTON",m.current,null);m.current&&JN.delete(m.current);w(!1);'
    ),
    (
        'if(ba.button===0&&r.current){var da=a.current;if(da&&R()){ba.preventDefault();ba.stopPropagation();',
        'if(ba.button===0&&r.current){var da=a.current;if(da&&R()){window.__AGY_SCROLL_EVT__?.("SCROLLBAR_POINTER_DOWN",da,null);ba.preventDefault();ba.stopPropagation();'
    ),
    (
        'if(u.current&&y.current){var da=a.current;if(da){var qa=G.current.maxThumbOffset;',
        'if(u.current&&y.current){var da=a.current;if(da){window.__AGY_SCROLL_EVT__?.("SCROLLBAR_POINTER_MOVE",da,null);var qa=G.current.maxThumbOffset;'
    ),
]
for old, new in patches:
    count = source.count(old)
    if count != 1:
        sys.exit(f"Unsafe: anchor count {count}, expected 1, prefix={old[:95]!r}")
    if old != new:
        source=source.replace(old,new,1)
path.write_text(source,encoding="utf-8")
print("DIAG target handler anchors patched: " + str(len(patches)-1))
