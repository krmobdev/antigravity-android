from pathlib import Path
import subprocess,hashlib,json,re,shlex,time,uuid,struct,zlib,sys,os,shutil
ROOT=Path(__file__).resolve().parents[2]
from acceptance_result import accepted
RISH=os.environ.get('AGY_QA_RISH') or shutil.which('rish') or str(ROOT.parent/'shizuku-rish/rish')
def shell(cmd,timeout=80):
 p=subprocess.run([RISH,'-c',cmd],capture_output=True,text=True,timeout=timeout)
 assert p.returncode==0,(cmd.split()[0],p.returncode,(p.stdout+p.stderr)[-1500:])
 return p.stdout+'\n'+p.stderr
def install(path,expected):
 meta=subprocess.run(['aapt','dump','badging',str(path)],capture_output=True,text=True,check=True).stdout
 assert re.search(r"package: name='"+re.escape(expected)+"'",meta),expected
 with path.open('rb') as data:
  p=subprocess.run([RISH,'-c','pm install -r -S '+str(path.stat().st_size)],stdin=data,capture_output=True,text=True,timeout=100)
 assert p.returncode==0 and 'Success' in p.stdout+p.stderr,(p.returncode,(p.stdout+p.stderr)[-1200:])
 print('INSTALLED',expected,hashlib.sha256(path.read_bytes()).hexdigest(),flush=True)
def launch():
 raw=shell('cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p com.agy.dev')
 m=re.search(r'com\.agy\.dev/(?:com\.agy\.ui\.launcher\.)?Launcher(?:Light|Dark)Activity',raw)
 assert m,raw
 shell('am start -W -n '+m.group(0))
 print('LAUNCHED_ENABLED_DEV_COMPONENT',flush=True)
mode=sys.argv[1] if len(sys.argv)>1 else 'picker'
if mode=='install':
 install(ROOT/'dist/antigravity-v2.4.5-navigation-english-dev.apk','com.agy.dev')
 install(ROOT/'tests/instrumentation-v245/out/agy-v245-qa.apk','com.krmobdev.v245qa')
 launch()
 sys.exit(0)
if mode=='qa-install':
 install(ROOT/'tests/instrumentation-v245/out/agy-v245-qa.apk','com.krmobdev.v245qa')
 sys.exit(0)
if mode=='launch':
 launch();sys.exit(0)
assert mode in ['picker','route-reuse','route-intent','route-legacy','back','startup','polish','surfaces']
args=['am','instrument','-w','-r','-e','mode',mode]
folder=None
files={}
if mode=='picker':
 folder=Path('/storage/emulated/0/Download/AGY-QA-'+uuid.uuid4().hex[:10])
 folder.mkdir()
 name='agy-qa-'+uuid.uuid4().hex[:8]
 def chunk(t,d):return struct.pack('>I',len(d))+t+d+struct.pack('>I',zlib.crc32(t+d)&0xffffffff)
 png=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',1,1,8,2,0,0,0))+chunk(b'IDAT',zlib.compress(b'\0\x88\x88\x88'))+chunk(b'IEND',b'')
 text=(name+' owned attachment regression. No account or private data.\n').encode()
 for suffix,data in [('.txt',text),('.png',png)]:
  path=folder/(name+suffix);path.write_bytes(data);files[path]=hashlib.sha256(data).hexdigest()
 from urllib.parse import quote
 uri='content://com.android.externalstorage.documents/document/'+quote('primary:Download/'+folder.name,safe='')
 args+=['-e','fixtureText',name+'.txt','-e','fixtureImage',name+'.png','-e','fixtureFolder',uri]
 print('OWNED_FIXTURE',folder,flush=True)
args+=['com.krmobdev.v245qa/.MobileCandidateProbe']
log=ROOT/'build'/('android-'+mode+'-'+str(int(time.time()))+'.log')
print('RUNNING',mode,flush=True)
p=subprocess.run([RISH,'-c',shlex.join(args)],capture_output=True,text=True,timeout=250)
out=p.stdout+'\n'+p.stderr;log.write_text(out)
print(out,flush=True)
print('LOG',log,flush=True)
expected={'picker':'ANDROID_REAL_FILE_PICKER=PASS_BACKDROP_SELECT_PREVIEW_REPEAT_MULTI_CANCEL_NO_SEND','route-reuse':'ANDROID_NOTIFICATION_REUSE=PASS_REAL_SHADE_TAPS_SAME_HOST_DOCUMENT_DRAFT_AND_BACK','route-intent':'ANDROID_NOTIFICATION_REUSE=PASS_POSTED_PENDING_INTENTS_SAME_HOST_DOCUMENT_DRAFT_AND_BACK','route-legacy':'ANDROID_NOTIFICATION_REUSE=PASS_POSTED_PENDING_INTENTS_WARM_LEGACY_SAME_HOST_DOCUMENT_DRAFT_AND_BACK'}
expected.update({'back':'ANDROID_PROGRESSIVE_BACK=PASS_REAL_KEYS_AND_BACKDROP_NO_SEND','startup':'ANDROID_NATIVE_STARTUP=PASS_REAL_READY_AND_ISOLATED_NATIVE_STAGE_ERROR_RETRY'})
passed=accepted(p.returncode,out,expected.get(mode))
print('STRICT_ACCEPTANCE',mode,'PASS' if passed else 'FAIL',flush=True)
if passed and folder:
 for path,digest in files.items():
  assert path.is_file() and hashlib.sha256(path.read_bytes()).hexdigest()==digest
  path.unlink()
 folder.rmdir()
 print('OWNED_FIXTURE_CLEANED',flush=True)
launch()
assert passed,mode+' device acceptance failed'
