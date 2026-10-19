#!/usr/bin/env python3
"""Observational XW/VW React-commit instrumentation, applied ONLY to staged web/main.js.

Does not modify any spinner predicates, store, native bridge, or navigation.
Always fail closed when minified anchors change.
"""
from pathlib import Path
import sys

MARKER = "/*AGY_XW_COMMIT_DIAG_V1*/"
VW_OLD = "window.__AGY_DIAG_SIDEBAR__?.(a,b,c,e,f);return((b?e:c)&&!verifiedIdle)||f}"
VW_NEW = ("var __agyVWResult=((b?e:c)&&!verifiedIdle)||f;"
          "window.__AGY_DIAG_VW_CAPTURE__?.(a,b,c,e,f,verifiedIdle,__agyVWResult);"
          "return __agyVWResult}")
CHILD_OLD = 'm=m==="running"&&!window.__AGY_VERIFIED_AGENT_GET__?.(a);/*AGY_XW_RUNNING_RECONCILE_V1*/'
CHILD_NEW = 'var __agyYTRender=m;' + CHILD_OLD
BEFORE_STATUS_OLD = 'h=g===R.CascadeRunStatus.IDLE&&h===!1;g=z.createElement(Spb,{cascadeId:a});c=z.createElement(ZU,'
BEFORE_STATUS_NEW = ('h=g===R.CascadeRunStatus.IDLE&&h===!1;'
                     'var __agySummaryStatus=g,__agyPending=c;'
                     'g=z.createElement(Spb,{cascadeId:a});c=z.createElement(ZU,')
TAIL_OLD = ('return b==="idleOnly"?c:z.createElement("span",'
            '{className:"flex items-center gap-1.5"},c,g)};var Tpb=')
COMMIT = (
    'var __agyVw=window.__AGY_DIAG_VW_TAKE__?.(a),'
    '__agyCommit={id:a,component:(0,z.useId)(),'
    'selected:__agyVw?.selected,summaryStatus:__agySummaryStatus,'
    'notFullyIdle:__agyVw?.summaryActive,agentActive:__agyVw?.agentActive,'
    'starting:__agyVw?.starting,verifiedIdle:__agyVw?.verifiedIdle,'
    'vwResult:f,yTResult:__agyYTRender??"none",'
    'childRunning:m,isLoading:!!(f||m),isBackgroundWorking:!!h,'
    'isArchived:!!l,pending:!!__agyPending,isUnread:!!(k||n),'
    'timestampMode:b};'
    '__agyCommit.renderedIndicator=window.__AGY_DIAG_RESOLVE_INDICATOR__?.(__agyCommit);'
    '(0,z.useLayoutEffect)(()=>{window.__AGY_DIAG_XW_COMMIT__?.(__agyCommit)});'
)
TAIL_NEW = MARKER + COMMIT + TAIL_OLD


def patch(text: str) -> str:
    if MARKER in text:
        if (text.count(MARKER) == 1 and
            text.count(VW_NEW) == 1 and text.count(CHILD_NEW) == 1 and
            text.count(BEFORE_STATUS_NEW) == 1 and text.count(TAIL_NEW) == 1):
            return text
        raise ValueError("partial commit diagnostics patch")
    if "/*AGY_SIDEBAR_RECONCILE_V1*/" not in text or "/*AGY_XW_RUNNING_RECONCILE_V1*/" not in text:
        raise ValueError("reconciliation patch must run before commit diagnostics")
    for before, after in ((VW_OLD, VW_NEW), (CHILD_OLD, CHILD_NEW),
                          (BEFORE_STATUS_OLD, BEFORE_STATUS_NEW),
                          (TAIL_OLD, TAIL_NEW)):
        if text.count(before) != 1:
            raise ValueError("upstream commit diagnostics anchor changed: " + before[:90])
        text = text.replace(before, after, 1)
    return text


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit("usage: patch_sidebar_commit_diag.py INPUT OUTPUT")
    try:
        src = Path(sys.argv[1]).read_text(encoding="utf-8")
        Path(sys.argv[2]).write_text(patch(src), encoding="utf-8")
    except ValueError as exc:
        sys.exit("ERROR: " + str(exc))
