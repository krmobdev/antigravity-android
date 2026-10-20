# Permanent KR release signing — Android Termux

Release identity for independent package **`com.krmobdev.antigravity`**. Do not use this key for upstream `com.agy` or integration `com.agy.dev`.

## Certificate (public, safe to share)

- Alias: **`antigravity-kr-release-v1`** (fixed; reuse for all future releases).
- Algorithm: **RSA-4096**, signature **SHA256withRSA**, keystore format **JKS** with two separate cryptographically random passwords.
- Public certificate SHA-256: **`23:AD:AC:86:42:3F:72:98:B0:A8:CC:D4:87:58:E6:E1:9B:E7:65:4D:4F:BD:EC:18:4E:18:4B:7E:16:03:0D:95`**.
- All material was generated on the Android device using Termux Java `keytool`. Passwords were passed by private environment variables, **not by command-line arguments or printed logs**.
- Private keystore and password files live in a mode-0700 directory **outside any Git repository**, under the phone's Termux-only application data: `~/anima-workspace/.antigravity-release-identity/`. The keystore and two distinct password files are mode 0600.
- **Backup status as of the release preparation:** **NOT CONFIRMED**. Termux can be uninstalled or have data cleared; the private Termux-only copy is not a durable backup. Do **not** publish a stable public release until the owner confirms a separate recoverable off-phone copy.

## Owner action required: secure off-phone backup

The owner must select a trusted separate location, e.g. encrypted cloud drive/password-manager attachment or USB-OTG storage kept off the phone. The following three components must be recoverable **together but securely protected**:

1. The private `antigravity-kr-release.jks` keystore.
2. The unique **store password**.
3. The different **key password**.

**Recommended separation:** export only the already-password-protected `.jks` file to the selected off-phone storage; preserve the two randomly generated passwords in a trusted password manager's encrypted vault, **not** as plaintext next to the uploaded file. Alternatively protect all components inside an authenticated encrypted archive with an owner-chosen, strong recovery passphrase (never send the passphrase through ChatGPT or GitHub).

- A copy inside another directory on **the same phone** is not a verified external backup.
- An unencrypted text export of the two passwords to shared Downloads is not acceptable.
- Do not upload keystore or passwords to the repository, a GitHub issue, a ChatGPT message, or public CI logs.
- After export, the owner must **test access/recovery** from the external location, compare the keystore SHA-256 and certificate fingerprint, and explicitly confirm this check before publication.
- Mark external backup as confirmed **only after** successful retrieval, not merely after starting an upload.

## Prepared encrypted OneDrive backup — owner confirmation required

The existing release keystore plus its two independent signing passwords were packed into a **7z AES-256 encrypted archive with filename/header encryption**. I decrypted the archive privately, recovered all files, compared SHA-256 bytes and independently verified the recovered certificate with `keytool`: **PASS**. No new signing key was generated.

- Encrypted file on Android: `/storage/emulated/0/Download/Antigravity-Release-Key-Backup-v2.4.0-kr1.7z`.
- Encrypted archive SHA-256: `d8a56fc82325bf31bef4105a07992a0ed576230aaaf7741196996e7515775691`.
- The archive's **separate random passphrase** is held in the private Termux file `~/anima-workspace/.antigravity-release-identity/backup-archive-password`, outside Git and outside the OneDrive archive.
- The owner should inspect the passphrase locally and store it in a trusted password manager **separate from OneDrive**, not in GitHub, a chat or a diagnostic log. One command for a private Termux screen: `cat ~/anima-workspace/.antigravity-release-identity/backup-archive-password`. The command contains no secret but its output is secret; don't record or share the screen.
- On the phone: **OneDrive → + → Upload → Files → Downloads → Antigravity-Release-Key-Backup-v2.4.0-kr1.7z**. Wait for the upload, reopen the OneDrive folder and verify that the file is present and recoverable.
- **External backup remains NOT CONFIRMED** until the user verifies the OneDrive upload and confirms that the archive passphrase is saved separately.
- Restoration: download the encrypted `.7z` from OneDrive, unlock it in 7-Zip with the separately saved passphrase, place the restored keystore/password files in a protected local folder, and check the certificate SHA-256 fingerprint in this document.

## Repeatable KR release build

From a clean checkout of the intended fork `main` commit with locally available vendor inputs, launch:

```bash
export AGY_RELEASE_KEYSTORE_PATH="$HOME/anima-workspace/.antigravity-release-identity/antigravity-kr-release.jks"
export AGY_RELEASE_KEY_ALIAS="antigravity-kr-release-v1"
export AGY_RELEASE_STORE_PASSWORD_FILE="$HOME/anima-workspace/.antigravity-release-identity/store-password"
export AGY_RELEASE_KEY_PASSWORD_FILE="$HOME/anima-workspace/.antigravity-release-identity/key-password"
bash modules/builder/build_apk.sh --kr-release

# Optional region-bypass edition (same package ID, version and signing key):
bash modules/builder/build_apk.sh --kr-release --region-bypass
```

The build pipeline performs **compile → package → zipalign → apksigner**, refuses missing private material and does not invoke GitHub Actions. Two locally signed editions are available: **standard** (original account eligibility checks) and **region-bypass** (patched local Core gates). They use the same Android package ID, versionCode and signing certificate, so only one edition can be installed at a time. The bypass APK was built from fork source commit `6d13e70008a36c9915bf03d9d76610f0a4f6917b`, which adds the KR bypass build option without changing the three reliability patches. Use `--kr-release`, **never** the legacy plain `build.sh` debug-key workflow for our public fork. The default non-KR build variants retain original functionality.

Verify separately:

```bash
apksigner verify --verbose --print-certs dist/antigravity-v2.4.0-kr1.apk
aapt dump badging dist/antigravity-v2.4.0-kr1.apk
sha256sum dist/antigravity-v2.4.0-kr1.apk
```

Check the expected package, non-debuggable manifest and certificate SHA-256. An installable update needs **the same certificate and package ID** with a larger `versionCode` than 17; do not generate a new signing identity.

## Recovery after Termux replacement

1. Install Termux, Java/keytool, the Android SDK build tools and an up-to-date clone of our fork on the Android phone.
2. Create a private mode-0700 identity directory **outside the clone**.
3. Copy the safely recovered keystore to that directory; restore the two **different** signing password files from the owner-controlled vault, each mode 0600. **Never paste passwords into Git, chat or shell commands**; enter them via an interactive protected editor.
4. Confirm the `antigravity-kr-release-v1` alias and public SHA-256 certificate fingerprint above before building.
5. Build a new version with an increased versionCode and verify that it installs over the existing `com.krmobdev.antigravity` without uninstalling.

## November 2026 — deferred CI/CD

GitHub Actions remain disabled for unattended pushes and tags while the monthly allowance is exhausted. The manual workflow job is temporarily gated off. In November, after the owner confirms the backup and the quota resets:

- Supply the **same** existing keystore as `AGY_RELEASE_KEYSTORE_BASE64`.
- Supply the existing passwords and alias as `AGY_RELEASE_STORE_PASSWORD`, `AGY_RELEASE_KEY_PASSWORD`, `AGY_RELEASE_KEY_ALIAS`.
- Refactor the workflow's remaining legacy debug signing steps to use those secrets securely, test certificate matching and update installation, then enable CI deliberately.
- **Never generate another key** for the same public fork package. Do not enable tag-triggered release publication before all checks pass.

No secrets have been uploaded or configured on GitHub during this manual preparation.
