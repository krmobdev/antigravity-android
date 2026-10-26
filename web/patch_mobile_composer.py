#!/usr/bin/env python3
"""Pinned composer layout/menu hooks; existing send/model/voice logic is retained."""
from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parent
MARKER="/* AGY_COMPOSER_KR1 */"

def once(source,old,new):
    if source.count(old)!=1:raise ValueError("Composer anchor mismatch: "+old[:65])
    return source.replace(old,new)

def transform(source):
    if MARKER in source:
        if source.count(MARKER)!=1 or "var zYb=AgyMobileContextMenu;" not in source:
            raise ValueError("Incomplete composer integration")
        return source
    source=once(source,"var zYb=({items:a,dropdownPlacement:b,onOpenChange:c})",
                "var agyOriginalContextMenu=({items:a,dropdownPlacement:b,onOpenChange:c})")
    source=once(source,";const AYb=()=>Promise.resolve(p(kia));",
                ";"+MARKER+"\n"+(ROOT/"src/mobile/composer.js").read_text()+"\nconst AYb=()=>Promise.resolve(p(kia));")
    source=once(source,'className:"flex flex-col w-full shrink-0",style:Ja?',
                'className:"agy-composer flex flex-col w-full shrink-0",style:Ja?')
    source=once(source,'ref:Us,className:"relative flex flex-col gap-0 p-1 rounded-',
                'ref:Us,className:"agy-composer-surface relative flex flex-col gap-0 p-1 rounded-')
    source=once(source,'className:"@container/input-footer flex w-full items-center justify-between gap-1 p-0.5"',
                'className:"agy-composer-footer @container/input-footer flex w-full items-center justify-between gap-1 p-0.5"')
    return source

if __name__=="__main__":
    p=Path(sys.argv[1]);s=p.read_text();result=transform(s)
    if result!=s:p.write_text(result)
    print("mobile composer integration: OK")
