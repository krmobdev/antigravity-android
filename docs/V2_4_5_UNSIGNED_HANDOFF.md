# Current unsigned APK handoff — 2026-10-27

Source: 4002f2dcbe139a2f68661521ab54dbfa911c3abf. Main remains 1cc88bd; no merge, signing, tag or GitHub Release was performed.
Both APKs rebuilt sequentially in Android Termux: com.krmobdev.antigravity, 2.4.5-kr1/code 19, debuggable=false, unsigned, aligned, current v8 code and expected Core hashes.

Files in /storage/emulated/0/Download:

- Antigravity-Android-v2.4.5-kr1-unsigned.apk — SHA-256 c39c2f4ae3a82ce7aaaa69da39b1983c91e73a540f1d7dc7cc28a453f619be92
- Antigravity-Android-v2.4.5-kr1-region-bypass-unsigned.apk — SHA-256 33300acc1971cb439e1142ba0854b54af68f9ec8d8c088d276a9ac9aaa11e199

Old copies were preserved in a timestamped obsolete folder in Downloads. No key file was accessed.

Owner-only signing in Termux:

    cd ~/anima-workspace/antigravity-android
    bash tools/sign_release_interactive.sh standard /storage/emulated/0/Download/antigravity-kr-release.jks
    bash tools/sign_release_interactive.sh bypass /storage/emulated/0/Download/antigravity-kr-release.jks

The owner enters passwords locally. Signed APKs appear in Downloads without the -unsigned suffix.
Main integration and publication are not performed. The legacy direct-Activity probe remains unaccepted and is not reported as PASS. Current notification workflow was accepted by the owner; programmatic build/Web/Java/Core/PTY checks passed.
