#!/usr/bin/env python3
"""Version-pinned mobile attachment integration. Fail closed before writing."""
from pathlib import Path
import sys

MARKER = "/* AGY_ATTACHMENTS_KR1 */"
ROOT = Path(__file__).resolve().parent

def once(source, old, new):
    if source.count(old) != 1:
        raise ValueError("Attachment client anchor mismatch: " + old[:70])
    return source.replace(old, new)

def transform(source):
    if MARKER in source:
        if source.count(MARKER) != 1 or "function AgyAttachmentCard" not in source:
            raise ValueError("Incomplete mobile attachment patch")
        return source
    start, end = source.find("var uYb=({selectedMedia:"), source.find(";var vYb=({title:")
    if start < 0 or end <= start or source.count("var uYb=({selectedMedia:") != 1:
        raise ValueError("Attachment renderer incompatible")
    source = source[:start] + MARKER + "\n" + (ROOT/"src/mobile/attachments.js").read_text() + source[end:]
    source = once(source, "A.createElement(uYb,{selectedMedia:Ya,setSelectedMedia:cb})",
                  "A.createElement(uYb,{selectedMedia:Ya,setSelectedMedia:cb,limit:Sd})")
    source = once(source, 'Sd=WN("max-media-attachments",5)',
                  'Sd=Math.max(1,Math.min(5,WN("max-media-attachments",5)))')
    source = once(source, 'e=e.target;e.files&&a(e.files)',
                  'e=e.target;const agyFiles=Array.from(e.files||[]);e.value="";agyFiles.length&&a(agyFiles)')
    source = once(source, "var u5a=(a,b,c,e,f=5)=>{var g=$t(),h=",
                  "var u5a=(a,b,c,e,f=5)=>{f=Math.max(1,Math.min(5,f));var agyPending=(0,A.useRef)(0);var g=$t(),h=")
    source = once(source, "var r=f-b;if(r<=0)", "var r=f-b-agyPending.current;if(r<=0)")
    source = once(source, "q(await n(Promise.allSettled(t.map(h))))",
                  "agyPending.current+=t.length;try{q(await n(Promise.allSettled(t.map(h))))}finally{agyPending.current-=t.length}")
    source = once(source, 'r&&(e?.(null),q(await n(h(r))))}finally{n()}},[h,e])',
                  'r&&(e?.(null),q(await n(k([r]))))}finally{n()}},[k,e])')
    begin, end = source.index("var u5a="), source.index(";var v5a=")
    hook = source[begin:end]
    hook = hook.replace('g("validation_check_failed",x)', 'e?.("Failed to read attachment"),g("validation_check_failed",x)')
    return source[:begin] + hook + source[end:]

def main():
    path = Path(sys.argv[1])
    original = path.read_text()
    result = transform(original)
    if result != original:
        path.write_text(result)
    print("mobile attachment integration: OK")
if __name__ == "__main__":
    main()
