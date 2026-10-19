#!/usr/bin/env python3
"""Reconcile stale sidebar summary only with confirmed agent state.

The summary notFullyIdle flag can remain true after the agent stream
reports IDLE and fullyIdle. Only suppress that stale summary contribution,
never optimistic/local starting flags.
"""
from pathlib import Path
import sys

MARKER="/*AGY_SIDEBAR_RECONCILE_V1*/"
ANCHOR='function VW(a){var b=En(g=>Rp(g)===a),c=!bM(a),e=nz(b?a:void 0,{selector:UW}),f=ZS(a);return(b?e:c)||f}'
REPLACEMENT='function VW(a){var b=En(g=>Rp(g)===a),c=!bM(a),e=nz(b?a:void 0,{selector:UW}),f=ZS(a),verifiedIdle=z.useSyncExternalStore(window.__AGY_VERIFIED_AGENT_SUBSCRIBE__||function(){return function(){}},()=>window.__AGY_VERIFIED_AGENT_GET__?.(a)===true,()=>false);'+MARKER+'return(b?e:(c&&!verifiedIdle))||f}'
DIAG='window.__AGY_DIAG_SIDEBAR__?.(a,b,c,e,f);'
DIAG_ANCHOR=ANCHOR.replace('return(b?e:c)||f}', DIAG+'return(b?e:c)||f}')
DIAG_REPLACEMENT=REPLACEMENT.replace('return(b?e:(c&&!verifiedIdle))||f}', DIAG+'return(b?e:(c&&!verifiedIdle))||f}')

def patch(text):
    if MARKER in text:
        if text.count(REPLACEMENT)+text.count(DIAG_REPLACEMENT)==1: return text
        raise ValueError("partial sidebar reconciliation patch")
    matches=[(old,new) for old,new in ((ANCHOR,REPLACEMENT),(DIAG_ANCHOR,DIAG_REPLACEMENT)) if text.count(old)]
    if len(matches)!=1 or text.count(matches[0][0])!=1:
        raise ValueError("upstream sidebar spinner helper changed")
    return text.replace(matches[0][0],matches[0][1],1)

if __name__=="__main__":
    if len(sys.argv)!=3: sys.exit("usage: patch_sidebar_reconcile.py INPUT OUTPUT")
    try:
        original=Path(sys.argv[1]).read_text(encoding="utf8")
        Path(sys.argv[2]).write_text(patch(original),encoding="utf8")
    except ValueError as e:
        sys.exit("ERROR: "+str(e))
