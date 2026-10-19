#!/usr/bin/env python3
"""Diagnostic-only hook for summary feed and sidebar spinner; no state changes."""
from pathlib import Path
import sys

MARKER="/*AGY_DIAG_MAIN_V1*/"
ANCHORS=(
    ("applyStreamBatch(a,b){a.hasLoadedInitialSummaries=!0;for(let [e,f]of b.payload.updates){",
     "applyStreamBatch(a,b){a.hasLoadedInitialSummaries=!0;"+MARKER+"window.__AGY_DIAG_SUMMARY_UPDATE__?.(b.payload.updates,b.payload.deletes);for(let [e,f]of b.payload.updates){"),
    ("function VW(a){var b=En(g=>Rp(g)===a),c=!bM(a),e=nz(b?a:void 0,{selector:UW}),f=ZS(a);return(b?e:c)||f}",
     "function VW(a){var b=En(g=>Rp(g)===a),c=!bM(a),e=nz(b?a:void 0,{selector:UW}),f=ZS(a);window.__AGY_DIAG_SIDEBAR__?.(a,b,c,e,f);return(b?e:c)||f}")
)

def patch(text):
    if MARKER in text:
        if all(repl in text for _,repl in ANCHORS): return text
        raise ValueError("partial diagnostic patch")
    for old,_ in ANCHORS:
        if text.count(old)!=1: raise ValueError("upstream diagnostic anchor mismatch")
    for old,new in ANCHORS:
        text=text.replace(old,new,1)
    return text

if __name__=="__main__":
    if len(sys.argv)!=3: sys.exit("usage: patch_agent_diag.py INPUT OUTPUT")
    try:
        original=Path(sys.argv[1]).read_text(encoding="utf8")
        Path(sys.argv[2]).write_text(patch(original), encoding="utf8")
    except ValueError as e:
        sys.exit("ERROR: "+str(e))
