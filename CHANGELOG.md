# Changelog / История изменений

## v2.4.1-kr1 — Android Scroll Smoothness Fix

- RU: Устранены наблюдавшиеся рывки и задержки инерционной прокрутки длинных чатов в Android WebView. Точечный fallback компонента OverlayScrollbar; нативный Chromium fling и глобальный `ScrollTimeline` не изменены.
- EN: Fixed observed long-chat fling jumps/stalls with a scoped OverlayScrollbar fallback; native Chromium scrolling and global `ScrollTimeline` remain intact.
- RU: Дополнены тесты сборки, упаковки веб-ресурсов и запрета попадания Python/тестовых скриптов в APK.
- EN: Added generated-web and real staging regression tests; prohibited packaging of Python/build/test helpers.
- Both KR editions: package `com.krmobdev.antigravity`, versionName `2.4.1-kr1`, versionCode `18`. Use the permanent KR release signing identity to upgrade over `17`.
- Feature parity: native notifications, Hca/NDa reconnect, sidebar, routing and agent task tracking retained. Agent-stage notification TODO remains open.
- [RU release notes](docs/releases/v2.4.1-kr1.ru.md) · [EN release notes](docs/releases/v2.4.1-kr1.en.md)

## v2.4.0-kr1 — Background Reliability

- Native agent notifications, background stream recovery, sidebar reconciliation and independent KR release signing.
- [RU](docs/releases/v2.4.0-kr1.ru.md) · [EN](docs/releases/v2.4.0-kr1.en.md)
