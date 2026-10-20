#!/usr/bin/env python3
"""Fail-closed v2.4-only read receipts; generated/main.js only."""
from pathlib import Path
import sys
MARKER="/* AGY_V24_VISIBLE_READ_RECEIPT */"
HEAD="function FDa(){"
TAIL=";async function GDa("

def patch(s):
    if MARKER in s:
        if s.count(MARKER)==1:return s
        raise ValueError("duplicate read patch")
    start=s.index(HEAD);end=s.index(TAIL,start)
    body=s[start:end]
    old="return()=>f=>g=>{"
    if body.count(old)!=1 or not body.endswith("return f(g)}}"):
        raise ValueError("v2.4 conversation viewing middleware changed")
    body=body.replace(old,"var readJournal={seen:new Set(),scheduled:new Set()};return store=>f=>g=>{",1)
    body=body[:-len("return f(g)}}")]+(
      "const result=f(g);"
      "try{agyV24ReadReceipt(store,g,b,c,a,readJournal)}"
      "catch(__readErr){console.warn('[AGY Read] reconciliation failed',__readErr?.name||'Error')}"
      "return result}}")
    helper=Path(__file__).with_name("read_receipt_v24.js").read_text(encoding="utf8")
    if "function agyV24ReadReceipt(" not in helper:raise ValueError("missing helper")
    return s[:start]+MARKER+"\n"+helper+"\n"+body+s[end:]
if __name__=="__main__":
    try:
        if len(sys.argv)!=3:raise ValueError("expected INPUT OUTPUT")
        a,b=map(Path,sys.argv[1:])
        b.write_text(patch(a.read_text(encoding="utf8")),encoding="utf8")
        print("v2.4 visible conversation read-receipt patch applied")
    except ValueError as exc:raise SystemExit("v2.4 read patch incompatible: "+str(exc))
