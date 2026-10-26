"""Native voice fallback contract tests, without activating any device microphone."""
from pathlib import Path
import subprocess
import unittest

ROOT=Path(__file__).resolve().parents[2]

class NativeVoiceFallbackTest(unittest.TestCase):
    def test_bridge_permission_security_and_cancellation(self):
        js=r"""
const fs=require('fs'), vm=require('vm'), assert=require('assert');
const s=fs.readFileSync('web/src/mobile/composer.js','utf8');
const scope={window:{__AG_NATIVE_BRIDGE_TOKEN__:'unit-token'},atob:x=>x};
vm.createContext(scope);
vm.runInContext(s.slice(0,s.indexOf('/* Compact mobile composer')),scope);
let start=0, stop=0, cancel=0, attached=null, failure=null, discarded=false;
scope.window.AndroidBridge={
 startNativeVoice:token=>{assert.equal(token,'unit-token');start++;return 'OK'},
 stopNativeVoice:token=>{stop++;return JSON.stringify({ok:true,mimeType:'audio/mp4',data:'YWFh',durationSeconds:1.5})},
 cancelNativeVoice:token=>{cancel++}
};
const done=x=>{attached=x},fail=x=>{failure=x};
assert.equal(scope.agyNativeMicStart({name:'NotAllowedError'},done,fail,()=>false),null);
assert.equal(start,0,'browser permission denied must not be bypassed');
assert.equal(scope.agyNativeMicStart({name:'SecurityError'},done,fail,()=>false),null);
let recording=scope.agyNativeMicStart({name:'UnknownError'},done,fail,()=>false);
assert.equal(start,1);
assert(recording?.recorder?.__agyNative);
assert.equal(recording.recorder.state,'recording');
recording.recorder.stop();
assert.equal(recording.recorder.state,'inactive');
assert.equal(stop,1);
assert.equal(attached.mimeType,'audio/mp4');
assert.equal(attached.durationSeconds,1.5);
recording.recorder.stop();
assert.equal(stop,1,'stop must be idempotent');
recording=scope.agyNativeMicStart({name:'UnknownError'},done,fail,()=>true);
recording.recorder.stop();
assert.equal(cancel,1,'cancel must discard captured audio');
assert.equal(stop,1);
scope.window.AndroidBridge.startNativeVoice=()=> 'ERR_NO_PERMISSION';
const failed=scope.agyNativeMicStart({name:'UnknownError'},done,fail,()=>false);
assert.equal(failed.error,'ERR_NO_PERMISSION');
assert.equal(start,2);
scope.window.AndroidBridge=undefined;
assert.equal(scope.agyNativeMicStart({name:'UnknownError'},done,fail,()=>false),null);
"""
        result=subprocess.run(['node','-e',js],cwd=ROOT,capture_output=True,text=True,timeout=30)
        self.assertEqual(result.returncode,0,result.stderr)

    def test_original_voice_mode_selection_preserved(self):
        import sys
        sys.path.insert(0,str(ROOT/"web"))
        from patch_mobile_composer import transform
        upstream=(ROOT/"web/main.js").read_text()
        result=transform(upstream)
        # Original Core: streaming speech-to-text only when its API/flag is
        # available. Otherwise button records a MediaSchema audio attachment.
        original_switch="oe=(0,A.useMemo)(()=>Yf?"
        self.assertEqual(upstream.count(original_switch),1)
        self.assertEqual(result.count(original_switch),1)
        self.assertIn("isStreamingTranscriptionEnabled:Yf",result)
        self.assertNotIn("oe=(0,A.useMemo)(()=>Yf&&!agyNativeMicAvailable()?",result)
        # The Android AAC recorder is ONLY a compatibility implementation of
        # upstream's attachment branch, without forcing the attachment mode.
        self.assertIn('agyNativeMicStart({name:"NativePrimary"}',result)
        self.assertIn('ERR_NO_PERMISSION',(ROOT/"app/src/main/java/com/agy/bridge/NativeVoiceRecorder.java").read_text())

    def test_native_recorder_requires_user_and_token(self):
        bridge=(ROOT/'app/src/main/java/com/agy/bridge/AndroidBridge.java').read_text()
        native=(ROOT/'app/src/main/java/com/agy/bridge/NativeVoiceRecorder.java').read_text()
        activity=(ROOT/'app/src/main/java/com/agy/ui/MainActivity.java').read_text()
        self.assertIn('matchesNativeBridgeToken(token)',bridge)
        self.assertIn('hasWindowFocus()',native)
        self.assertIn('Manifest.permission.RECORD_AUDIO',native)
        self.assertIn('MAX_BYTES',native)
        self.assertIn('readiness(Activity activity)',native)
        self.assertIn('nativeVoiceReadiness(String token)',bridge)
        self.assertIn('if (file != null) file.delete()',native)
        self.assertIn('cancelNativeVoiceOnBackground()',activity)

if __name__=='__main__':
    unittest.main()
