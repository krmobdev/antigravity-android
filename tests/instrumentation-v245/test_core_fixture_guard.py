"""The actual QA JavaScript guards must never mistake user text for our fixture."""
from pathlib import Path
import json,re,subprocess,unittest

ROOT=Path(__file__).resolve().parents[2]
SOURCE=ROOT/'tests/instrumentation-v245/src/com/krmobdev/v245qa'

def java_body(filename,start,end):
    text=(SOURCE/filename).read_text().split(start,1)[1].split(end,1)[0]
    return ''.join(json.loads(value) for value in re.findall(r'"(?:\\.|[^"\\])*"',text))

class DictationFixtureGuardTest(unittest.TestCase):
    def test_actual_guard_preserves_non_fixture_drafts(self):
        metrics=java_body('DictationCoreFixture.java','private static final String METRICS=','void run()')
        clear=java_body('MobileCandidateProbe.java','final String guard=','JSONObject before=state(guard)')
        script=r"""
const vm=require('vm'),assert=require('assert');
const config=JSON.parse(process.argv[1]);
for(const [raw,empty,owned]of [
 ['',true,false],['   ',true,false],
 ['This is a test.',false,true],
 ['This is a test! This is a test.',false,true],
 ['this is a test\n',false,true],
 ['private draft',false,false],
 ['this is a test 123',false,false],
 ['Привет',false,false],
 ['This is a test. Привет',false,false],
 ['🙂',false,false],['123',false,false],['...',false,false]
]){
 const document={querySelector:selector=>selector.includes('contenteditable')?{textContent:raw}:null};
 const box={document,window:{}};
 const metrics=JSON.parse(vm.runInNewContext('(function(){'+config.metrics+'})()',box));
 const clear=JSON.parse(vm.runInNewContext('(function(){'+config.clear+'})()',box));
 assert.equal(metrics.empty,empty);
 assert.equal(clear.empty,empty);
 assert.equal(clear.owned,owned,'only the exact public fixture can be deleted');
 if(!empty&&!owned)assert.equal(metrics.fixtureOnly,false);
}
console.log('PASS actual fixture draft guards: English, non-English, numbers, emoji, punctuation and exact fixture');
"""
        result=subprocess.run(['node','-e',script,json.dumps({'metrics':metrics,'clear':clear})],
            cwd=ROOT,capture_output=True,text=True,timeout=20)
        self.assertEqual(result.returncode,0,result.stdout+result.stderr)

if __name__=='__main__':unittest.main()
