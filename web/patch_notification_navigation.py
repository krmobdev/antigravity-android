#!/usr/bin/env python3
"""Add a version-checked Android notification deep-link listener to staged UI."""
from pathlib import Path
import sys

MARKER = "/*AGY_CONVERSATION_DEEPLINK_V1*/"
ANCHOR = 'function fOb(a){W3&&W3.navigate({to:"/c/$cascadeId",params:{cascadeId:a}})}'
REPLACEMENT = ANCHOR + MARKER + '''
window.addEventListener("agy:open-conversation",function(e){
  try{
    var id=e.detail?.conversationId;
    if(typeof id!=="string"||id.length<1||id.length>128||
      !/^[a-zA-Z0-9_+:-]+$/.test(id)) return;
    if(!W3) {
      console.warn("[AGY] router is not ready for notification deep link");
      return;
    }
    fOb(id);
    window.Android?.onNotificationNavigationResult?.(window.__AG_NATIVE_BRIDGE_TOKEN__,id);
  }catch(err){
    console.warn("[AGY] notification navigation failed");
  }
});
'''

CONTEXT_MARKER = "/*AGY_VISIBLE_CONVERSATION_V1*/"
CONTEXT_ANCHOR = '},[e,f,a,b,g,k,l,c]);return null};function mMb'
CONTEXT_REPLACEMENT = '},[e,f,a,b,g,k,l,c]);' + CONTEXT_MARKER + r"""
(0,z.useLayoutEffect)(()=>{
  var id=/^\/c\/[^/]+$/.test(e)&&typeof g==="string"?g:"";
  if(id==="_new"||! /^[A-Za-z0-9_+:-]{1,128}$/.test(id)) id="";
  var report=()=>{try{window.Android?.onVisibleConversation?.(window.__AG_NATIVE_BRIDGE_TOKEN__,id);}catch(_){}};
  report();
  window.addEventListener("agy:host-resume",report);
  return ()=>window.removeEventListener("agy:host-resume",report);
},[e,g]);return null};function mMb"""

def patch_navigation(text):
    if MARKER in text:
        if text.count(REPLACEMENT) == 1: return text
        raise ValueError("partial native notification navigation patch")
    if text.count(ANCHOR) != 1:
        raise ValueError("upstream conversation navigation function changed")
    return text.replace(ANCHOR, REPLACEMENT, 1)

def patch(text):
    text = patch_navigation(text)
    if CONTEXT_MARKER in text:
        if text.count(CONTEXT_REPLACEMENT) == 1: return text
        raise ValueError("partial visible conversation patch")
    if text.count(CONTEXT_ANCHOR) != 1:
        raise ValueError("upstream committed route hook changed")
    return text.replace(CONTEXT_ANCHOR, CONTEXT_REPLACEMENT, 1)

if __name__=="__main__":
    if len(sys.argv)!=3: sys.exit("usage: patch_notification_navigation.py INPUT OUTPUT")
    try:
        source=Path(sys.argv[1]).read_text(encoding="utf8")
        Path(sys.argv[2]).write_text(patch(source),encoding="utf8")
    except ValueError as exc: sys.exit("ERROR: "+str(exc))
