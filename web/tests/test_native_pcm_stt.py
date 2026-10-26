"""Original Antigravity dictation must retain upstream RPC and editor semantics."""
from pathlib import Path
import subprocess
import unittest
import sys

ROOT=Path(__file__).resolve().parents[2]

class NativePcmSttTest(unittest.TestCase):
    def test_original_stream_rpc_unchanged(self):
        sys.path.insert(0,str(ROOT/'web'))
        from patch_mobile_composer import transform
        before=(ROOT/'web/main.js').read_text()
        after=transform(before)
        self.assertIn('oe=(0,A.useMemo)(()=>Yf?',after)
        self.assertIn('isStreamingTranscriptionEnabled:Yf',after)
        self.assertIn('n.streamAudioTranscription(p(Uia,{mimeType:"audio/pcm;rate=16000"',after)
        self.assertIn('agyNativePcmStart(agyPcmChunk',after)
        self.assertIn('agyNativePcmStop();var fa=L.current',after)
        self.assertIn('agyNativePcmStop();H.current',after)
        self.assertNotIn('oe=(0,A.useMemo)(()=>Yf&&!agyNativeMicAvailable()',after)

    def test_native_pcm_helper_transport_and_cleanup(self):
        js=r"""
const fs=require('fs'),vm=require('vm'),assert=require('assert');
const src=fs.readFileSync('web/src/mobile/composer.js','utf8');
const begin=src.indexOf('let agyNativePcmSession=');
const end=src.indexOf('/* Compact mobile composer',begin);
assert(begin>=0 && end>begin);
let started=0,stopped=0,read=0,chunks=[],errors=[],scheduled=[];
const native={
 startNativePcm:(token)=>{assert.equal(token,'unit');started++;return 'OK'},
 readNativePcm:(token)=>{read++;return read==1?'AQIDBA==':''},
 stopNativePcm:(token)=>{stopped++}
};
const box={window:{AndroidBridge:native,__AG_NATIVE_BRIDGE_TOKEN__:'unit'},
 setTimeout:fn=>{scheduled.push(fn);return scheduled.length},
 clearTimeout:()=>{},atob:x=>Buffer.from(x,'base64').toString('binary')};
vm.createContext(box);vm.runInContext(src.slice(begin,end),box);
assert(box.agyNativePcmAvailable());
box.agyNativePcmStart(x=>chunks.push(Array.from(x)),e=>errors.push(e));
assert.equal(started,1);
assert.equal(scheduled.length,1);
scheduled.shift()();
assert.deepEqual(chunks,[[1,2,3,4]]);
assert.equal(errors.length,0);
box.agyNativePcmStop();
assert.equal(stopped,1);
box.agyNativePcmStop();
assert.equal(stopped,1);
"""
        p=subprocess.run(['node','-e',js],cwd=ROOT,capture_output=True,text=True,timeout=30)
        self.assertEqual(p.returncode,0,p.stderr)

    def test_native_capture_has_permission_bounds_and_no_storage(self):
        s=(ROOT/'app/src/main/java/com/agy/bridge/NativePcmCapture.java').read_text()
        bridge=(ROOT/'app/src/main/java/com/agy/bridge/AndroidBridge.java').read_text()
        self.assertIn('Manifest.permission.RECORD_AUDIO',s)
        self.assertIn('activity.hasWindowFocus()',s)
        session=(ROOT/'app/src/main/java/com/agy/bridge/PcmCaptureSession.java').read_text()
        self.assertIn('LIMIT_MS',session)
        self.assertIn('new PcmCaptureSession',s)
        self.assertNotIn('FileOutputStream',session)
        self.assertIn('FRAME_BYTES=1280',s)
        self.assertNotIn('FileOutputStream',s)
        self.assertIn('matchesNativeBridgeToken(token)',bridge)
        self.assertIn('nativePcmCapture.stop()',bridge)

if __name__=='__main__':
    unittest.main()
