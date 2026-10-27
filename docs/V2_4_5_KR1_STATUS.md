> RELEASE HOLD — 2026-10-27: repeated dictation exposed a microphone lifecycle
> regression. The unsigned APKs listed here were built from 03b5cf6 and are
> superseded by source fixes. Do not sign or publish them. New device acceptance
> and freshly rebuilt candidates are required.

# v2.4.5-kr1 — актуальные unsigned APK для владельца

Дата: 2026-10-27. Ветка сборки: dev.
Исходники APK: 03b5cf6e619ce323e9abea24b1b8540cd1757ce8.
Документационные коммиты после него не меняют собранный код.

Обе редакции заново собраны последовательно на Realme GT 2 Pro в Termux,
через Gradle-free pipeline, без облачных сборок и GitHub Actions.
По команде владельца проверенные копии сохранены в Downloads.

| Редакция | Файл в Downloads | SHA-256 |
| --- | --- | --- |
| Standard | Antigravity-Android-v2.4.5-kr1-unsigned.apk | 9cad6e5b6ef298b3ea42a0d7b40f1b1cd5d2e024f5ba1fe76859304eea9c2b90 |
| Region-bypass | Antigravity-Android-v2.4.5-kr1-region-bypass-unsigned.apk | 2bae07e93aa58a49a9e0991be81382f99c5db067a500e05f10af0ce3644de274 |

Рядом: SHA256SUMS-v2.4.5-kr1-unsigned.txt.

Подтверждено для обеих APK:

- com.krmobdev.antigravity, versionName 2.4.5-kr1, versionCode 19 > stable 18.
- debuggable=false; APK не подписаны (нет v1 entries и APK signing block).
- ZIP integrity и zipalign; ожидаемые размер/SHA/PTY-сигнатуры Core.
- Редакции различаются только Core и его runtime manifest.
- Текущие JS/CSS совпадают со свежим staging, включены единые карточки,
  английские строки и исправление навигации уведомлений; scroll hooks сохранены.
- Проверены имена файлов и распознаваемые credential patterns в исходниках/APK,
  включая большой generated/main.js. Ключ владельца не открывался.
- SHA-256 файлов в Downloads совпадают с результатами локальной сборки.

Полный машиночитаемый отчёт: V2_4_5_KR1_VERIFICATION.json.
Реальные тесты и оставшиеся ограничения: V2_4_5_NAVIGATION_ENGLISH_QA.md.
Владелец принял реальный переход из шторки и Back на главный экран.
Отдельная legacy-проверка старого прямого MainActivity уведомления не пройдена;
подготовка unsigned файлов не означает закрытие всех пунктов ручной приёмки.

Подпись выполняет только владелец интерактивно по HANDOFF_V2_4_5_KR1.md.
Агент APK не подписывал и не устанавливал production-кандидат.
Main, стабильная v2.4.1-kr1, теги и GitHub Release не изменялись.

<details>
<summary>Исторический отчёт: старые APK, SHA и рабочие ветки не относятся к текущей передаче</summary>

> Historical report. The sole working branch is dev. See V2_4_5_KR1_INDEPENDENT_AUDIT.md for current verification; old APK hashes are not a current release handoff.

# Antigravity v2.4.5-kr1 — подготовка кандидата

**UI revision in progress:** owner rejected the oversized composer screenshot. The earlier APK hashes below describe the previous UI and will be replaced after compact-layout acceptance. Dev installation succeeded through Shizuku stdin; the earlier Download installation limitation is resolved by that supported local path. Actual Android attachment previews/removal and synthetic native background notifications passed; Android Back failed and is being fixed.

**Исходники и две unsigned APK подготовлены. Ручная Android-приёмка, подпись и
публикация ещё не выполнены.** APK собраны локально на Realme RMX3301 в Termux,
без Gradle, облачных сборок и GitHub Actions.

## Git и сохранность исходного состояния

- Checkout: `~/anima-workspace/antigravity-android`.
- Origin: https://github.com/krmobdev/antigravity-android.git.
- Перед началом fetch и ls-remote подтвердили main:
  `1cc88bd40667f60e819c5ca415dc7cd69294632d`, расхождение 0/0.
- Работа выполнена в `feature/v2.4.5-kr1`; изменения регулярно коммитились и
  отправлялись без force-push. Main, стабильные теги/история и production APK
  v2.4.1-kr1 не изменялись.
- Исходное незакоммиченное изменение `web/generated/main.js` сохранено и не
  включено в коммиты. До и после всех сборок его SHA-256:
  `4345a61da96e68aa6b0f1e358f0e2109c5d3dca2bc79feabda629e7ced553ebb`.
- Builder собирает web в disposable source snapshot; исходный generated-файл
  не перезаписывается. Отдельный staging regression проверил это трижды.
- Код APK собран из `6f188844dc53f35e35276ea62b1f161057dab333`.
  Последующие изменения документации/owner-helper не меняют содержимое APK.

## Реализовано и проверено

| Этап | Изменения | Фактические проверки |
|---|---|---|
| PTY | CoreEnvironment/CoreShellEnvironment; upstream-derived pty_shell; pipeline/manifest; runtime cache SHA; ANDROID_DATA для aapt | Java shell policy, patch unit/integration, реальные Core RPC: create, tty stdin/stdout, интерактивный read/input, reconnect, close и повторное создание — PASS |
| Уведомления | CoreAgentProgress, native stream/observer, tracker, coalescing, outcome в completion FIFO | 32 проверки reducer, параллельные задачи/дубли/rehydration/stale step counts, отмена/ошибка, terminal/new-run races, FIFO/persistence/correction, Android compilation — PASS |
| Вложения | Карточки 200x96, просмотр вместо удаления, отдельное удаление, UTF-8 pre-viewer, image/audio/video preview, picker lifecycle | 21 behaviour assertion, многократный/отменённый выбор, ошибка чтения, конкурентный лимит, Blob URL cleanup/late reads, unsafe/binary/large text — PASS |
| Composer | Просторный multiline input, минимум 48 CSS px на мобильном viewport, адаптивный + sheet | Mobile/desktop menu, существующие actions/disabled, close/Back cleanup, исходная send/stop/queue/model/voice логика сохранена; source/browser harness — PASS |

### PTY: совместимость без форсирования

Наш pristine Core: 181358800 байт, SHA-256
`3971986e8bad5a26f087fc2c81ad28bd6cf19bee4492df98f3f7e0e4f2dc224d`.

В нём ровно одна строка /bin/bash (0x428fe3f), в Go string table между send-pack
и --config=. Модуль не использует фиксированный offset записи: проверяет
контекст, точное число clean/replacement signatures, длину и idempotence.
Несовместимость/отсутствие/неоднозначность отклоняются даже при allow-missing.

На подключённом Android /bin ведёт в /system/bin, /bin///sh — в исполняемый
/system/bin/sh. На Pixel 7 это ещё нужно подтвердить. Проверены оба Core:
standard `abf5881f9983fbad00c1cdf903c81f84cf36b8a21364b3476f6d19568b528527`;
bypass `f25135e4b4a2c4a8e4cb319dc36b13eb98fa4718fa80aaae5a7819935370ff27`.

Shell выбирается по исполняемости: prefix bash → runtime bash → system sh.
TERM=xterm-256color; TERMINFO использует читаемую базу prefix; чужое
унаследованное значение удаляется. Отсутствие shell завершается явной ошибкой.
Прямой PTY smoke использует временные HOME/runtime, случайный loopback port и
упакованный TLS-сертификат. Production/accounts/model quota не затрагиваются.

### Уведомления: достоверность и границы

Используются поля bundled Core schema:
mainTrajectoryUpdate.lastStepType, stepsUpdate.totalLength, lastStepError,
killed и CASCADE_RUN_STATUS_CANCELING. Публичные тексты — фиксированный whitelist.
Команда не объявляется тестом: для run-command выводится «Выполняет команду».
При неизвестном событии — «Агент выполняет задачу». Raw toolSummary больше не
передаётся из WebView. Таймер только объединяет полученные события.

Watermarks каждой беседы переживают reconnect в том же процессе. Ниже предыдущего
step count, conflicting equal-index types и retired trajectories отбрасываются.
В schema нет глобального event sequence: универсальную защиту от всех возможных
неупорядоченных событий и cold-start сценариев без ручной приёмки не заявляем.
Холодный старт ждёт новых данных Core. Каналы, FIFO spacing, navigation,
Core running и read/unread не менялись. UI-first IDLE не обрывает native stream
до подтверждения результата Core.

### Вложения и Composer: реальные ограничения

Сохранены исходные format/size checks, Core artifact upload/delete и ошибки
отправки. Не заявлена совместимость десяти файлов. Cap не более 5 и ниже, если
так сообщает текущий Core flag; нулевой лимит остаётся нулём.

Текстовый просмотр до 1 МиБ — только UTF-8 text node внутри pre, без HTML/Markdown
execution. NUL/binary/oversize отклоняются. SVG не исполняется как изображение.
Неподдерживаемый preview объясняется; существующий Core openFile остаётся там,
где поддерживается. Превью не имитирует успешную отправку. Реальную отправку
разных форматов выбранной моделью ещё нужно проверить.

Разрешения chat picker временные; постоянные project-tree grants сохранены.
Не добавлены JS inertia, тяжёлые анимации или глобальный ScrollTimeline override.

## Полная программная регрессия

- app/tests/run.sh — PASS (tracker, stage/coalescing, completion FIFO/auth/Connect,
  shell policy; complete Java compile — 229 classes).
- modules/core — 17 tests PASS.
- modules/patches — 14 tests PASS.
- modules/io — 13 tests PASS.
- modules/env — 25 tests PASS.
- modules/builder — 8 tests PASS.
- web unittest discovery — 20 tests PASS, без skips.
- web selftest — PASS: Hca/NDa reconnect, React Working, navigation, sidebar,
  native completion proof, mobile attachments/composer и scroll fix.
- web staging regression — PASS: standard/bypass/rebuild, protected generated
  input, отсутствие исходников/build helpers в assets.
- Реальный isolated Core PTY smoke — PASS, две terminal generations.
- Обе финальные unsigned KR APK — successful builds, manifest/ZIP/zipalign,
  Core SHA/signatures, mobile/scroll hooks и credential filename/text scans PASS.
  Текстовый scan охватывает малые текстовые assets и изменённые Git text files;
  это ограниченный scan, не математическая гарантия отсутствия любых секретов.

Первый расширенный web прогон выявил 1 failure/11 errors в старых v2.3 wrappers.
Их идентичность baseline подтверждена git diff. Обёртки перенесены на фактически
используемые v2.4 patch_runtime_v24/sidebar APIs и актуальный token-gated bridge.
После исправления — все 20 PASS; тесты не отключались.

Все сборки шли последовательно через taskset -c 0-3. Direct Python probe
подтвердил [0,1,2,3], но один shell child сообщил [0,1,2,3,4,6]; исключительное
выполнение всех дочерних процессов на efficiency cores не заявляем.

## Финальные APK

Package `com.krmobdev.antigravity`; versionName `2.4.5-kr1`; versionCode `19`
(стабильный был 18); debuggable=false. Подписи v1/v2/v3 нет: owner signing required.

| Файл в dist | Байт | SHA-256 |
|---|---:|---|
| antigravity-v2.4.5-kr1-unsigned.apk | 103458757 | 283ab03a1a114d563120a8bf1520a9bbe7f1053029d1e77aa5d94dc1aa1d342c |
| antigravity-v2.4.5-kr1-region-bypass-unsigned.apk | 103458797 | 853a1d8b6f0c6d1e137f6c3963ca5582bfd01e9f91e3f83cc741a05509e26990 |

Обе содержат одинаковый набор из 405 entries; различаются только
assets/runtime/bin/liblanguage_server.so и assets/runtime/manifest.json.
Порядок ZIP entries может различаться и не является изменением функциональности.
Отчёт: `dist/v2.4.5-kr1-verification.json`; контрольные суммы:
[V2_4_5_KR1_SHA256SUMS.txt](V2_4_5_KR1_SHA256SUMS.txt).

Также собрана отдельная debug QA APK `dist/antigravity-v2.4.5-kr1-dev-qa.apk`,
package com.agy.dev, с публичным debug key. Это не релизный артефакт.
Её установка не выполнена: AnimaBridge отклонял Download с PATH_DENIED;
effective allowed_roots оставался только anima-workspace. Shizuku ready,
но install_apk требует enabled Download. Доверенный ADB transport / локальный
wireless connect service отсутствовали. Production не удалялась/не обновлялась.

## Что осталось владельцу

**НЕ ПРОЙДЕНО; обязательно ручная приёмка:**

- Реальные Core agent stages в фоне и при выключенном экране; несколько задач,
  ошибки/отмена, reconnect/cold start, delivery и сохранение read/unread/FIFO.
- PTY WebView UI и Pixel 7 (на Realme реальные Core terminal RPC уже PASS).
- Android File Picker, реальная отправка/ошибки выбранной модели, возврат из фона.
- Composer с IME, вырезами/навигацией, крупным шрифтом/TalkBack,
  Light/Dark/пользовательскими пресетами; реальный длинный чат/fling.
- Android app launch/crash acceptance и обновление production поверх v2.4.1-kr1.
  Успешная сборка/Java/RPC не доказывает отсутствие UI crashes.

[Owner handoff и подпись](HANDOFF_V2_4_5_KR1.md).
Релизный keystore/пароли агент не читал, не копировал и не использовал;
новый релизный ключ не создавался. Неподписанные APK не публиковались.
Merge main, release tags, GitHub Release и Actions не выполнялись.

</details>

## Microphone restart follow-up — 2026-10-27

Source fixes 09a60a2 and 1e83b60 pushed to dev. Full analysis and verified
results: [microphone retry audit](V2_4_5_MICROPHONE_RETRY_AUDIT.md).
Twelve real AudioRecord restart cycles passed with instrumentation code 0.
Updated Dev installed. Owner speech/countdown acceptance remains pending.
Previous unsigned hashes are historical and excluded from release approval.
