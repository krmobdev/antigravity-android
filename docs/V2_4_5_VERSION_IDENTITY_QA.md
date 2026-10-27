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
