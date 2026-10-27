# Dev APK version identity audit — 2026-10-27

The owner's Android settings screenshot exposed stale Dev metadata. Device
PackageManager confirmed com.agy.dev was versionName 2.4.0 / code 16, while
installed production com.krmobdev.antigravity remains 2.4.1-kr1 / code 18
(last update 2026-10-22). Existing unsigned candidates are 2.4.5-kr1 / code 19
but predate dictation fixes and remain blocked.

This was a builder profile defect, not an old source checkout: local dev and
origin/dev both pointed to 542a52a; the v8 APK contained the current 40c1c3b
dictation fix, verified v8 assets and real Core/editor integration.

KR_VERSION_NAME and KR_VERSION_CODE in config/app.env now supply both KR editions
and the isolated Dev profile. Dev adds its 12-character source revision, a dirty
suffix for uncommitted work, or unknown for an archive without Git. Generated
BuildConfig and manifest agree. The existing legacy com.agy profile is separate.
Production installation, protected main and release tags are untouched.

New version regressions failed for both Dev identity cases before the fix and
passed afterward. All 16 builder tests and shell/Python syntax checks passed,
including both KR edition metadata, package/debug separation and mocked signing
guards. No release signing key was accessed.

The rebuilt APK metadata, asset comparison and device-installed identity are
recorded below after local build and installation. No production APK is installed.

## Verified rebuild and installation

Source commit: ca317222416133b3c990f2b82e00ea86bcfbcc19
APK: dist/antigravity-v2.4.5-kr1-dev-versioned.apk
SHA-256: 03632fe2806d37c5d44d8317d9b23dc95a3dc2a3d2563f39d69d07fd2591421d

aapt and installed Android PackageManager agree:
- Dev com.agy.dev: 2.4.5-kr1-dev+ca3172224161, versionCode 19.
- Stable com.krmobdev.antigravity: 2.4.1-kr1, versionCode 18.
- Stable lastUpdateTime remained 2026-10-22 01:14:17 before/after.

All 342 Web/runtime code asset files matched the tested v8 APK byte-for-byte.
The remaining runtime manifest differed only in generated_at; its complete
parsed payload (including file hashes) matched. No audio/UI behavior was changed
by the identity fix. Both KR editions retain 2.4.5-kr1/code 19 in generator tests.
New Dev was installed via Shizuku and its Android app-info screen was opened.
All 16 builder tests passed. Old unsigned APKs remain blocked; no release signing,
production installation, main update, tag or publication was performed.
