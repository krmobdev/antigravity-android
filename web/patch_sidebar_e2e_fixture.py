#!/usr/bin/env python3
"""OPTIONAL test-only Redux summary fixture. Never writes to Core/backend.
Set AGY_SIDEBAR_E2E=1 only for an explicitly diagnostic APK.
"""
from pathlib import Path
import sys
MARK="/*AGY_SIDEBAR_E2E_FIXTURE_V1*/"
ANCHOR='function Vya(){var a=null,b=!1;return c=>e=>f=>{if(Uya.match(f)){'
CODE=MARK+'''function Vya(){var a=null,b=!1;return c=>e=>f=>{
if(!window.__AGY_E2E_FIXTURE__){
var saved=Object.create(null);
window.__AGY_E2E_FIXTURE__={
set:function(id,status,active){
if(window.__AGY_E2E_ENABLED__!==true)return "disabled";
if(typeof id!=="string"||!/^[a-zA-Z0-9_+:-]{1,128}$/.test(id))return "bad_id";
var current=c.getState().trajectorySummaries?.summaries?.[id];
if(!current)return "missing_summary";
if(!Object.prototype.hasOwnProperty.call(saved,id))saved[id]=current;
var copy={...current,status:status,notFullyIdle:!!active};
c.dispatch(Fo({updates:[[id,copy]],deletes:[]}));
return "ok";
},
restore:function(id){
if(window.__AGY_E2E_ENABLED__!==true)return "disabled";
if(!Object.prototype.hasOwnProperty.call(saved,id))return "unchanged";
c.dispatch(Fo({updates:[[id,saved[id]]],deletes:[]}));
delete saved[id];return "ok";
},
isHeld:function(id){return Object.prototype.hasOwnProperty.call(saved,id);},
snapshot:function(id){
var entry=c.getState().trajectorySummaries?.summaries?.[id];
return entry?{status:entry.status,notFullyIdle:entry.notFullyIdle}:null;
}
};
}
if(Uya.match(f)){'''
STREAM_ANCHOR='c.dispatch(Fo({updates:Object.entries(l.updates),deletes:l.deletes}))'
STREAM_CODE=('/*AGY_E2E_STREAM_HOLD_V1*/'
 'c.dispatch(Fo({updates:Object.entries(l.updates).filter(([id])=>'
 '!(window.__AGY_E2E_ENABLED__===true&&window.__AGY_E2E_FIXTURE__?.isHeld(id))),'
 'deletes:l.deletes.filter(id=>'
 '!(window.__AGY_E2E_ENABLED__===true&&window.__AGY_E2E_FIXTURE__?.isHeld(id)))}))')
def patch(text):
    if MARK in text:
        if text.count(MARK)!=1 or text.count(CODE)!=1 or text.count(STREAM_CODE)!=1:
            raise ValueError("partial E2E fixture")
        return text
    if text.count(ANCHOR)!=1:
        raise ValueError("Redux middleware anchor changed")
    if text.count(STREAM_ANCHOR)!=1:
        raise ValueError("Redux stream callback changed")
    return text.replace(ANCHOR,CODE,1).replace(STREAM_ANCHOR,STREAM_CODE,1)
if __name__=="__main__":
    if len(sys.argv)!=3: sys.exit("usage: patch_sidebar_e2e_fixture.py INPUT OUTPUT")
    out=patch(Path(sys.argv[1]).read_text())
    Path(sys.argv[2]).write_text(out)
