# v2.4.5-kr1 — нативная заставка запуска

Рабочая ветка: dev. Основа этапа: 5c0222c.
Первичные коммиты: e39e06c (native экран), 983ee34 (доказательство готовности UI).

## Поведение

Минималистичный нативный экран: логотип Antigravity, название, тонкий
индикатор ожидания и текущий этап с подписью «Этап N из 3».
Светлый/тёмный режим берётся из ThemeManager. Дополнительный splash-WebView
больше не создаётся. Постоянной анимации логотипа и минимального времени показа нет.

События:
1. CoreStartupProgress.begin() — подготовка перед установкой Core.
2. Возврат installCore() — запуск ядра.
3. Успешный CoreReadyProbe — подключение интерфейса.
4. Видимый контрол/содержимое в DOM, два animation frame и проверенный
   AndroidBridge.onUiReady — немедленное скрытие заставки.

Общий процент неизвестен и не выдумывается. Модель этапов использует
неизменяемые снимки и номер попытки, игнорируя старые и повторные события.

Убраны два прежних ложных завершения: Native auto-dismiss через 10 секунд и
JavaScript fallback-timeout через 5,5 секунды. Пустой root, spinner-only,
скрытые/inert/прозрачные и находящиеся вне экрана контролы не считаются готовым UI.
Console hint и публичный signal проходят через ту же проверку DOM.

При тайм-ауте UI показывается понятная ошибка и «Повторить». Сбой UI при здоровом
Core перезагружает только интерфейс. Сбой Core сохраняет существующий путь
переустановки/перезапуска. Сырые log tail, токены и приватные пути не попадают
в текст заставки. Копирование/сохранение журнала осталось явным действием.

Deadline проверяет существующий foreground poll. После возвращения незавершённой
загрузки из фона предоставляется новый срок и восстанавливается DOM-наблюдатель.
Для уже готового UI нет ни перезагрузки, ни повторного показа заставки.

## Артефакт

Файл: dist/antigravity-v2.4.5-native-startup-final-dev.apk
Package: com.agy.dev, отдельная debug-редакция.
SHA-256: f3cb30e3d170d1feb78c67ac82c697e76a293ee5a633fe62991a325ea1f61059

Сборка: локальный Gradle-free pipeline в Termux; v2/v3 debug-подписи проверены.
Установка через Shizuku: Success. Release keystore не использовался.
Основной generated/main.js, содержащий голосовые hooks, побайтово совпал с
unified-ui-refined-dev.apk. Бинарник Core также совпал. Runtime manifest
отличается только generated_at; содержимое/хеши файлов совпадают.

## Выполненные проверки

- app/tests/run.sh: PASS — Core stages/notifications/FIFO, PCM lifecycle,
  shell environment, stream parsing, auth/origin и новая модель запуска.
- Новый Java-тест: порядок этапов, immutable snapshot, дубли, конкурентный
  устаревший callback и изоляция повторной попытки — PASS.
- Полный Web selftest: PASS.
- Новый DOM-gate тест: таймер/console/API не обходят доказательство, два кадра,
  hidden/offscreen/transparent root, исчезновение DOM, однократный callback,
  очистка и восстановление наблюдения после истечения срока — PASS.
- Реальный установленный APK: загружен новый proof-gate JS, Core подтвердил
  готовность, Web UI подтвердил готовность, native overlay скрыт — PASS.
- Android mode startup: INSTRUMENTATION_CODE 0. Три этапа, Light/Dark, ошибка
  и callback Retry проверены в изолированном нативном контроллере поверх Dev.
  Снимки рисуют только принадлежащий тесту View, без экрана других приложений.
- Реальный MainActivity Retry на искусственно вызванной UI-timeout ошибке:
  новый UI-ready, скрытая заставка, неизменный readyGeneration Core — PASS.
  Перезагрузка разрешалась тестом только при пустом черновике/без вложений.
- Реальные Home/background/foreground: та же Activity, готовый UI,
  скрытая заставка и неизменное поколение Core — PASS.
- Light/Dark и ошибка осмотрены по нативным снимкам на устройстве.

Дополнительно после финальной установки:
- Realme GT 2 Pro (RMX3301), Android 15: установленный base.apk совпал с SHA-256 выше.
- Проверены актуальные readiness/resume hooks внутри APK и разрешённые верхние каталоги архива.
- Android mode back: четыре цикла model/effort/Usage, Back и backdrop, вложенные
  настройки Developer/Models, сохранение черновика — PASS, INSTRUMENTATION_CODE 0.

## Изменённые файлы

| Область | Основные файлы |
| --- | --- |
| Нативный экран | app/src/main/java/com/agy/ui/splash/NativeStartupView.java, SplashController.java |
| Жизненный цикл | app/src/main/java/com/agy/ui/MainActivity.java |
| Источник этапов | app/src/main/java/com/agy/core/CoreStartupProgress.java, CoreRuntimeState.java, CoreLauncher.java, CoreProcessSupervisor.java |
| Готовность UI | web/src/patches/startup_ready/signal.js, detect-ui.js, arm.js, public-api.js |
| Тесты | app/tests/CoreStartupProgressTest.java, run.sh; web/tests/startup_ready.behavior.cjs, selftest.sh; tests/instrumentation-v245/src/com/krmobdev/v245qa/MobileCandidateProbe.java |
| Документация | app/README.md, web/README.md, tests/instrumentation-v245/README.md, этот отчёт |

## Границы проверки

Не выдавать fixture за реальное падение или повреждение Core. Порча/удаление
runtime и пользовательских данных ради проверки не выполнялись. Copy/Save Logs
не нажимались; буфер обмена и пользовательские файлы не изменялись.
Длительная пауза незавершённой загрузки проверена поведением DOM-gate, а не
60-секундным тестом реальной приостановки Android. Pixel 7/Android 17,
Android 8/12, TalkBack и большой шрифт требуют отдельной приёмки.

Это Dev-этап. Общая релизная регрессия и две актуальные unsigned KR APK остаются
в общей матрице подготовки v2.4.5-kr1; релиз не опубликован.
