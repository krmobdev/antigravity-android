> Historical report. The sole working branch is dev. See V2_4_5_KR1_INDEPENDENT_AUDIT.md for current verification; old APK hashes are not a current release handoff.

# v2.4.5-kr1 — параллельный QA / передача после лимита (26.10.2026)

Рабочая ветка: `review/v2.4.5-handoff-20261026`.
Worktree на Realme: `~/anima-workspace/antigravity-v245-review-20261026`.
Основа: `feature/v2.4.5-kr1` на коммите `d8d0bbb9` (14 существующих коммитов относительно main).

## Сохранность и границы

- Оригинальный checkout `~/anima-workspace/antigravity-android` **не изменять**: в нём отдельная пользовательская правка `web/generated/main.js` и незакоммиченные последние правки агента.
- Три незавершённые правки (Composer CSS, `patch_mobile_composer.py`, `MobileCandidateProbe.java`) перенесены в **отдельный** worktree, чтобы ничего не терять.
- `main`, опубликованный `v2.4.1-kr1`, release keystore, секреты и GitHub Releases не трогались.
- Применять новые изменения только через git-ветку review. Не запускать два редактора/агента одновременно на одном worktree.

## Выполненные проверки в отдельной копии

- `bash web/tests/selftest.sh`: PASS.
- `bash app/tests/run.sh`: PASS.
- `bash modules/patches/tests/run_all.sh`: PASS, включая сверку Core PTY patch SHA.
- `bash tests/instrumentation-v245/build.sh`: PASS, включая последнюю QA-поправку.
- `git diff --check`: PASS.
- Локальный debug APK `dist/antigravity-v2.4.5-review-dev.apk` собран, подписан **только публичным debug key**, проверен и установлен через Shizuku streaming с package `com.agy.dev`. Production `com.krmobdev.antigravity` не тронут.
- QA-инструментация `com.krmobdev.v245qa` отдельно собрана и установлена.

## Подтверждённые Android-проверки

Первый запуск dev-кандидата:
- Размеры Composer (106 dp), строка панели (50 dp), кнопки >=48 dp: PASS.
- Диалоги model / effort / usage: отображение и общие стили PASS.
- Native notification fixture: два независимых потока статуса, финализация и ошибка/отмена PASS на синтетических событиях.
- Тест Back для цепочки model/effort/usage выявил проблему: `ANDROID_BACK_CLOSES_STOCK_SHEETS=FAIL` при прежнем грубом определении открытия по ненулевому прямоугольнику.

После уточнения теста с учётом `keepMounted=true`:
- Проверки model / effort PASS.
- После первой кнопки Back остаётся один видимый stock sheet (1/6 DOM); после второй — ни одного (0/6).
- При попытке повторно открыть модель и перейти в Usage тест получает `UI_PROBE_ERROR=IllegalStateException` (конкретный источник/жизненный цикл WebView ещё не установлен). Поэтому **Android UI-приёмка остаётся FAIL**.
- Это пока нельзя объявлять исправленным Back; нельзя подписывать и публиковать релиз.

## Следующий шаг для продолжившего работу агента

1. Начинай из `~/anima-workspace/antigravity-v245-review-20261026` на ветке review. Сначала `git status --short --branch` и `git log -1`; не перезаписывай оригинальный checkout.
2. Воспроизведи реальный Back/повторное открытие stock model/effort/usage меню. Диагностируй, не теряется ли фокус Activity/WebView и не срабатывает ли fallback `BackNavigator` при закрытии последнего вложенного меню. Старайся не менять production-навигацию без доказанного корня.
3. Прогони QA-инструментацию до `INSTRUMENTATION_CODE: 0` и всех релевантных Android UI PASS. Не считай shell exit 0 успехом инструмента: проверяй `INSTRUMENTATION_RESULT` и `INSTRUMENTATION_CODE`.
4. После правок сделай локальные регрессии, отдельные коммиты и push только в review-ветку; не допускай потери незакоммиченного `web/generated/main.js` из старого checkout.
5. На утверждённом SHA пересобери **обе** unsigned KR APK заново. Хеши из старого handoff (с коммита `6f18884`) устарели: в них нет последних Composer/voice/menu патчей.
6. Обнови SHA256SUMS, отчёт, ручную приёмку; релизную подпись выполняет только владелец. Pixel 7 и обновление существующей production установки — PENDING.

Важно: автономный тест без отправки модели и без реальной голосовой записи не доказывает весь end-to-end продукт.
