#!/usr/bin/env python3
"""Verify two unsigned KR artifacts without accessing signing material."""
from pathlib import Path
import hashlib,json,re,struct,subprocess,zipfile
ROOT=Path(__file__).resolve().parents[3]
NAMES=["antigravity-v2.4.5-kr1-unsigned.apk","antigravity-v2.4.5-kr1-region-bypass-unsigned.apk"]
CORE="assets/runtime/bin/liblanguage_server.so"
EXPECTED=["abf5881f9983fbad00c1cdf903c81f84cf36b8a21364b3476f6d19568b528527","f25135e4b4a2c4a8e4cb319dc36b13eb98fa4718fa80aaae5a7819935370ff27"]
FORBIDDEN=re.compile(r"(?:^|/)(?:\.git|\.ssh|\.antigravity-release-identity|hosts\.yml|\.env)(?:/|$)|\.(?:jks|keystore|p12|pfx)$|(?:store-password|key-password|backup-archive-password)",re.I)
MARKERS=[b"AGY_ATTACHMENTS_KR1",b"AGY_COMPOSER_KR1",b"AGY_SCROLL_TIMELINE_ONLY_AB",b"AGY_V24_TRANSPORT_RECONNECT",b"AGY_V24_SUMMARY_RECONNECT",b"AGY_V24_DEEPLINK"]

def no_credential_text(data, name):
    # Only artifact/source text is inspected, never external signing material.
    key_header=any(line.startswith(b"-") and b"PRIVATE" in line and b"KEY" in line for line in data.splitlines())
    token=re.search(rb"gh[pousr]_[A-Za-z0-9]{36,}|github_pat_[A-Za-z0-9_]{40,}",data)
    assert not key_header and token is None,("Credential text in artifact/source",name)


VOICE_MARKERS=[
    b'__AGY_AUDIO_DIAG_BUILD__="2026-10-27-pcm-delivery-v8"',
    b"await run.delivery.drain()",
    b"ERR_CORE_NO_TRANSCRIPT",
    b"AGY_DICTATION_LIMIT_MS=210000",
    b"agyNativePcmStop(true)",
]

def verify_voice_payload(main):
    for marker in VOICE_MARKERS:
        assert marker in main, ("Stale/missing dictation fix",marker)
    no_credential_text(main,"assets/web/generated/main.js")


def digest(path):
    h=hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda:f.read(1048576),b""):h.update(chunk)
    return h.hexdigest()

def verify(path,expected):
    badging=subprocess.check_output(["aapt","dump","badging",str(path)],text=True)
    first=badging.splitlines()[0]
    assert "name='com.krmobdev.antigravity'" in first
    assert "versionCode='19'" in first and "versionName='2.4.5-kr1'" in first
    assert "application-debuggable" not in badging
    tree=subprocess.check_output(["aapt","dump","xmltree",str(path),"AndroidManifest.xml"],text=True)
    debug=[line for line in tree.splitlines() if "android:debuggable" in line]
    assert len(debug)==1 and debug[0].strip().endswith("0x0"),debug
    subprocess.run(["zipalign","-c","4",str(path)],capture_output=True,check=True)
    with zipfile.ZipFile(path) as z:
        assert z.testzip() is None,"ZIP integrity"
        names=z.namelist()
        assert not any(re.match(r"META-INF/.*\.(RSA|DSA|EC|SF)$",n,re.I) for n in names)
        with path.open("rb") as f:
            f.seek(-min(path.stat().st_size,65557),2);tail=f.read()
            at=tail.rfind(b"PK\x05\x06");assert at>=0
            central=struct.unpack_from("<I",tail,at+16)[0];f.seek(central-16)
            assert f.read(16)!=b"APK Sig Block 42","Signing block present"
        assert subprocess.run(["apksigner","verify",str(path)],capture_output=True).returncode!=0
        assert not [n for n in names if FORBIDDEN.search(n)],"Forbidden credential filename in APK"
        assert not any(n.startswith("assets/web/") and n.endswith((".py",".pyc",".sh",".cjs")) for n in names)
        main=z.read("assets/web/generated/main.js")
        verify_voice_payload(main)
        staged=ROOT/"build/staging/assets/web"
        assert main==(staged/"generated/main.js").read_bytes(),"APK JavaScript differs from fresh staging"
        for stylesheet in ("mobile-attachments.css","mobile-composer.css"):
            assert z.read("assets/web/"+stylesheet)==(staged/stylesheet).read_bytes(),"Stale APK CSS"
        for marker in MARKERS:assert main.count(marker)==1,marker
        for name in ["mobile-attachments.css","mobile-composer.css"]:assert "assets/web/"+name in names
        assert b"AGY_V24_VISIBLE_READ_RECEIPT" not in main
        assert not any(n.startswith("assets/web/src/") or n.startswith("assets/web/tests/") for n in names)
        aux=z.read("assets/web/generated/patches/web_optimizations.js")
        assert b"metadata.toolSummary" not in aux
        for item in z.infolist():
            if item.file_size<=1048576 and item.filename.endswith((".json",".txt",".md",".sh",".py",".js",".yaml",".yml",".conf",".pem")):
                no_credential_text(z.read(item.filename),item.filename)
        h=hashlib.sha256();raw=patched=0;tail=b""
        with z.open(CORE) as stream:
            for chunk in iter(lambda:stream.read(1048576),b""):
                h.update(chunk);scan=tail+chunk
                raw+=scan.count(b"/bin/bash");patched+=scan.count(b"/bin///sh");tail=scan[-8:]
        assert h.hexdigest()==expected,"Wrong edition/stale Core"
        assert z.getinfo(CORE).file_size==181358800 and raw==0 and patched==1
        return {"file":path.name,"bytes":path.stat().st_size,"sha256":digest(path),"core_sha256":expected,
                "package":"com.krmobdev.antigravity","versionName":"2.4.5-kr1","versionCode":19,
                "debuggable":False,"unsigned":True,"zip_integrity":True,"zipalign":True,
                "mobile_and_scroll_hooks":True,"voice_delivery_v8":True,"staging_assets_match":True,
                "credential_filename_scan":True,"credential_text_scan":True,"large_generated_main_credential_scan":True}

def main():
    paths=[ROOT/"dist"/name for name in NAMES]
    results=[verify(path,sha) for path,sha in zip(paths,EXPECTED)]
    with zipfile.ZipFile(paths[0]) as a,zipfile.ZipFile(paths[1]) as b:
        assert set(a.namelist())==set(b.namelist()),"Edition asset names differ"
        differences=[n for n in a.namelist() if (a.getinfo(n).CRC,a.getinfo(n).file_size)!=(b.getinfo(n).CRC,b.getinfo(n).file_size)]
        assert set(differences)<={CORE,"assets/runtime/manifest.json"},differences
    changed=subprocess.check_output(["git","diff","--name-only","1cc88bd..HEAD"],cwd=ROOT,text=True).splitlines()
    assert not [n for n in changed if FORBIDDEN.search(n)],"Forbidden credential filename in Git changes"
    for name in changed:
        path=ROOT/name
        if path.is_file() and path.suffix in (".md",".java",".js",".cjs",".py",".sh",".json",".css"):
            no_credential_text(path.read_bytes(),name)
    report={"source_commit":subprocess.check_output(["git","rev-parse","HEAD"],cwd=ROOT,text=True).strip(),"artifacts":results,"edition_differences":differences,
            "git_change_credential_filename_scan":True,"manual_acceptance":"PENDING: docs/HANDOFF_V2_4_5_KR1.md"}
    (ROOT/"dist/v2.4.5-kr1-verification.json").write_text(json.dumps(report,indent=2)+"\n")
    (ROOT/"docs/V2_4_5_KR1_SHA256SUMS.txt").write_text("".join(r["sha256"]+"  "+r["file"]+"\n" for r in results))
    print(json.dumps(report,indent=2))
if __name__=="__main__":main()
