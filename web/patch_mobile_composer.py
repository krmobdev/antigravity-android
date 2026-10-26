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
    settings_start='A.createElement("div",{className:"flex items-center gap-0 min-w-0"},K!==void 0'
    if source.count(settings_start)!=1:raise ValueError("Composer settings anchor mismatch")
    left=source.index(settings_start)
    settings_end=',ca)),A.createElement("div",{className:"shrink-0 flex items-center gap-0.5"}'
    right=source.index(settings_end,left)+len(',ca)')
    desktop=source[left:right]
    pair='A.createElement("div",{className:"flex items-center gap-1 min-w-0"},h,k)'
    extra=once(desktop,pair,'A.createElement("div",{className:"flex items-center gap-1 min-w-0"},k)')
    source=source[:left]+'A.createElement(AgyComposerModelControl,{primary:h,desktop:'+desktop+'})'+source[right:]
    source=once(source,'A.createElement(zYb,{items:ia,dropdownPlacement:r,onOpenChange:Ma=>{Ma&&la()}})',
        'A.createElement(zYb,{items:ia,extraControls:'+extra+',dropdownPlacement:r,onOpenChange:Ma=>{Ma&&la()}})')
    return source

if __name__=="__main__":
    p=Path(sys.argv[1]);s=p.read_text();result=transform(s)
    if result!=s:p.write_text(result)
    print("mobile composer integration: OK")
