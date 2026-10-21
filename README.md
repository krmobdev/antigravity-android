<div align="center">

  <p>
    <b>English</b> &nbsp;&bull;&nbsp; <a href="README.ru.md">Русский</a>
  </p>

  <br/>

  <img src="docs/assets/logo.png" alt="Antigravity" width="88" height="88" style="border-radius: 20px;" />

  # Antigravity for Android

  **Autonomous AI development environment running natively on Android & ARM64.**

  [![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white)](https://android.com)
  [![Arch](https://img.shields.io/badge/ARM64-v8.0--A%2B-blue?style=flat-square)](https://arm.com)
  [![Release](https://img.shields.io/badge/Version-2.4.1--kr1-blueviolet?style=flat-square)](https://github.com/krmobdev/antigravity-android/releases/tag/v2.4.1-kr1)
  [![License](https://img.shields.io/badge/License-Apache_2.0-orange?style=flat-square)](LICENSE)

  <br/>

  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/assets/hero_dark.png">
    <source media="(prefers-color-scheme: light)" srcset="docs/assets/hero_light.png">
    <img alt="Antigravity Interface" src="docs/assets/hero_dark.png" width="100%" style="border-radius: 12px; border: 1px solid rgba(128,128,128,0.2);" />
  </picture>

</div>

---

### Overview

> **Independent fork** of the [original project by werdio325-png](https://github.com/werdio325-png/antigravity-android). For installation and updates, use **[our fork's releases](https://github.com/krmobdev/antigravity-android/releases/tag/v2.4.1-kr1)**; links to upstream are provided explicitly for attribution only.

**Antigravity for Android** brings autonomous agentic coding directly to mobile hardware.

No PRoot, chroot, or virtualization overhead. Antigravity runs **bare-metal inside native Android userspace (Bionic)**, unleashing the full read/write speed of UFS storage and physical CPU cores. Driven by Gemini models, the agent inspects repositories, authors code across multiple files, compiles binaries, and runs tests completely on-device.

---

### Current Release — v2.4.1-kr1

**Fixed Android WebView long-chat inertial scrolling.** A scoped OverlayScrollbar `ScrollTimeline` probe change selects the existing scroll-event fallback without replacing Chromium native fling or touching the global API. Long chats and Dark/Light theme switching were verified by the owner.

**Upgrade in place:** `com.krmobdev.antigravity`, versionName `2.4.1-kr1`, versionCode `18` (previously `17`). Choose Standard or Region Bypass, not both simultaneously. Do not uninstall the previous fork; verify the published APK's SHA-256 and certificate.

[Release details (English)](docs/releases/v2.4.1-kr1.en.md) · [Русский](docs/releases/v2.4.1-kr1.ru.md)

---

### Reliability Fixes Retained from v2.4.0-kr1

* **Native agent notifications:** Core-driven completion alerts that work independently of a suspended WebView and open the matching conversation.
* **Hca stream recovery:** Refreshes agent state after returning from the background.
* **NDa / Sidebar reconciliation:** Keeps `Working` indicators in sync with Core without changing unread states.

[Full release notes](docs/releases/v2.4.1-kr1.en.md) · [Download APK](https://github.com/krmobdev/antigravity-android/releases/download/v2.4.1-kr1/Antigravity-Android-v2.4.1-kr1.apk)

---

### Highlights from Upstream v2.4

* **Modular Core:** Clean separation into 6 standalone subsystems (`modules/*`) with independent test suites.
* **Native Splash:** Canvas-based dot animation saving ~40 MB RAM with instant startup.
* **Desktop Mode:** Instant toggle in Appearance settings for tablets, external keyboards, and Samsung DeX.
* **Universal ARMv8.0-A Support:** Built-in transparent atomic & SIMD emulation (`liblse_compat.so`) for older CPUs.
* **Background Micro-Daemon:** Ultra-lightweight `agy-daemon` (<10 MB RAM) with cron scheduler, file watcher, and AI task queue.

---

### Key Features

* **Autonomous Agent:** Plans tasks, edits code across project trees, executes shell tools, and fixes build errors.
* **Elevated Control (Shizuku):** Direct ADB-level operations (`uid=2000`) without root for package installs and system diagnostics.
* **Built-in `pkg` Manager:** Fast package manager installing development runtimes (`git`, `python`, `node`, `clang`) from Termux repos.
* **Adaptive Viewports:** Seamless touch-first mobile UI with optional desktop layout.
* **Fork Upgrades:** Future APKs with the same `com.krmobdev.antigravity` application ID and signing certificate can update this fork in place. Data from upstream `com.agy` is not automatically migrated.

---

### Quick Start

1. **Choose an edition on [our release page](https://github.com/krmobdev/antigravity-android/releases/tag/v2.4.1-kr1):**
   * **[Antigravity-Android-v2.4.1-kr1.apk](https://github.com/krmobdev/antigravity-android/releases/download/v2.4.1-kr1/Antigravity-Android-v2.4.1-kr1.apk)** — standard edition retaining the original Google account eligibility checks.
   * **[Antigravity-Android-v2.4.1-kr1-region-bypass.apk](https://github.com/krmobdev/antigravity-android/releases/download/v2.4.1-kr1/Antigravity-Android-v2.4.1-kr1-region-bypass.apk)** — *Region Bypass* edition, with modified local Core account eligibility checks. This does not guarantee access when server-side or regional restrictions apply.
   * Both editions share **the same package ID** `com.krmobdev.antigravity` and **the same release signing certificate**. They **cannot be installed side by side**: installing one replaces the other. Both coexist separately with upstream `com.agy`.
   * Verify both packages using [SHA256SUMS.txt](https://github.com/krmobdev/antigravity-android/releases/download/v2.4.1-kr1/SHA256SUMS.txt).
2. **Grant Storage Permission** (`All Files Access`). *(Optional: enable Shizuku for ADB access)*.
3. **Install Runtimes** via terminal or agent:
   ```bash
   pkg update && pkg install git python nodejs
   ```

---

### Building & Testing

Our signed fork release uses a dedicated build mode and private signing key. Plain `build.sh` generates upstream-style packages; it does **not** reproduce our published fork APK. See the [release signing and local build guide](docs/RELEASE_SIGNING_KR.md).

```bash
# Build our fork release (requires private release signing key and dependencies):
bash modules/builder/build_apk.sh --kr-release

# Bypass edition of the same fork, signed with the same private release key:
bash modules/builder/build_apk.sh --kr-release --region-bypass

# Run all test suites:
bash test_all_modules.sh
```

---

### Repository Structure

```text
antigravity/
├── config/             # App constants, environment paths, and AI rules
├── modules/            # Decoupled core modules (v2.4+)
│   ├── core/           # Binary unpacking, glibc loader, shims, SHA-256 manifest
│   ├── patches/        # Binary patch engine (syscall emulation, resolv, SIGILL)
│   ├── io/             # High-performance I/O Bus: event broker, FIFO pipes, sockets
│   ├── env/            # Path resolver, diagnostics, Flight Recorder logs, CLI
│   ├── builder/        # Gradle-free APK builder (javac, D8, aapt, zipalign, apksigner)
│   └── runner/         # Standalone runtime preparation, port probe, E2E tests
├── app/                # Android host app (native splash, WebView, system bridges)
├── web/                # Local web frontend, desktop mode toggle, themes
├── env/                # Bionic userspace, Termux overlay, pkg manager, liblse_compat
├── docs/assets/        # Screenshots, banners, and diagrams
├── build.sh            # Root APK build script
└── test_all_modules.sh # Test suite for all 6 modules
```

> [!TIP]
> Each module in `modules/`, `app/`, `web/`, and `env/` includes its own dedicated `README.md` with in-depth technical documentation.

---

<div align="center">
  <sub>Licensed under the <a href="LICENSE">Apache 2.0 License</a></sub>
</div>
