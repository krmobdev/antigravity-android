# Device validation — 2026-10-20

Base: PR #15, commit `9fcb1b180548bfc7144b8f0269e7f7bfa7cdf532`.
Target: `com.agy.dev`; the official `com.agy` installation was not changed.

Final Shizuku instrumentation result:

```
RESULT passed=true checks=25 controlledNativeTransitions=true realAgentRequests=0
INSTRUMENTATION_CODE: -1
RETURN originalActivity=true originalWebView=true
```

The test used real physical input events in the sidebar and SystemUI. It exited
the chat to the list before Home, waited for a controlled native completion,
tapped the actual heads-up notification, and returned using Android Back.
The original Activity and WebView were retained; replacement is a test failure.

Verified on screen and through NotificationManager:

* The open foreground chat receives the DEFAULT channel and no heads-up banner.
* Another chat and a background app receive the HIGH channel and actual SystemUI
  heads-up banners. The device was unlocked and interactive, with DND off.
* The notification tap opens the matching conversation, then Back returns to the
  retained list.
* The second queued completion is initially absent and later appears; actual
  Android notification post times are separated by the five-second interval.
* Opening a queued conversation changes its completion to the non-banner channel.

The UI test controls native transitions. It sends no agent prompts and does not
claim live Core execution or another real-agent duration regression. Native
observer Core execution was not re-run for these notification policy checks.

Additional checks passed:

* 17 Python/Node web patch and behavior tests.
* APK web staging, JavaScript syntax, and preservation of upstream web/main.js.
* AgentTaskTracker: 13 assertions; ConnectFrameReader: 10 assertions.
* Deterministic queue scenarios: FIFO spacing, changed visibility, cancellation,
  duplicate entries, restored queue/cooldown, bounded overflow without loss.
* Dev APK build and signature verification.

Tested Dev APK SHA-256:
`2092a5d3de39c5e6c4bc32e4d3d6ecc7875688f256521e27c7cd04654258aa18`

Final instrumentation APK SHA-256:
`721751fb914c88afb4a5f19c56b90dfeeae612e6684824a5766fb52d07bf5015`

Raw phone job: `b82001000000000000000000000000b6`. Its stdout and stderr must
both be read: this device splits instrumentation status records between them.
Raw logs remain outside the public source tree; they contain conversation IDs.

---

## Draft PR #1 — финальная пользовательская приёмка трёх патчей v2.4.0 (20.10.2026)

Этот раздел дополняет более ранний контролируемый тест уведомлений из начала документа. Цель: **Native Notifications**, **Hca**, **NDa / Sidebar** в отдельной `com.agy.dev`. Никакой новой системы read/unread и никаких изменений официальной `com.agy`.

### 1. Native Notifications — real Core / Android: PASS

Нативная диагностика 21:17 установила завершение Core после остановки Activity: 21:16:53 `onStop`, 21:17:12.822 Core `IDLE/fullyIdle`, 21:17:12.854 успешная публикация уведомления; переход из уведомления принят маршрутизатором 21:17:45.

В реальном испытании двух одновременно работающих разных разговоров A/B (одинаковые пользовательские запросы и заголовки ожидаемы):

| Этап | A | B |
|---|---|---|
| Native Core `RUNNING` | 22:18:05.699 | 22:18:17.943 |
| Native Core `IDLE/fullyIdle` | 22:19:03.437 | 22:19:18.243 |
| Tracker `FINISHED`, `source=native` | 22:19:03.438 | 22:19:18.244 |
| Android `notify()` опубликован | 22:19:03.456 | 22:19:18.269 |

Два агента исполнялись параллельно около 45,5 секунд. `Activity.onPause` 22:18:31.315 и `onStop` 22:18:31.369 случились до обоих завершений. В журнале две разные нативные подписки и завершения, два успешных отдельных поста, **0 ошибок нативного потока и 0 дублей завершения**. Уведомления остались в шторке одновременно. Постоянное `Core running` — уведомление foreground-сервиса, не третий результат агента.

**FIFO:** два Core-завершения разделили приблизительно 14,806 с, две публикации — около 14,813 с; задержка 5 с не понадобилась. Пятиисекундный FIFO и устранение дублей подтверждены программными тестами; отдельное контролируемое устройство-тестирование предыдущего этапа есть выше в документе, но не является реальной коллизией двух Core-завершений. Не заявлять, что реальная коллизия испытана.

### 2. Read/unread и ложный Working — Android UI: PASS

По пользовательским скриншотам 22:36:03 после обычного запуска `com.agy.dev` через значок (без нажатия уведомлений и без открытия обоих разговоров) оба разных завершённых разговора показывают **синие точки непрочитанности** и не показывают `Working`. Ранее просмотренные разговоры отображают обычное относительное время. Android-шторка сохраняла оба уведомления о завершении и отдельный Core foreground status.

Индикатор `Working` в v2.4 связан с выполнением и `trajectorySummaries`, синяя точка — с отдельным вычислением unread. Патч `a9afe4c4` подключён к реальным React `VAb/z_`, и тесты вызывают извлечённые React-функции из итогового JS. Реальная UI-проверка подтверждает отсутствие застывшего `Working` после возврата и корректное сохранение unread. Нативная доказанная `IDLE/fullyIdle` не скрывает новый фактический `RUNNING`; это отдельно подтверждено тестами.

**Readfix не включён:** коммит `432228d` отменён `f33e724`; исходные upstream read/unread обработчики не менялись.

### 3. Hca — два реальных положительных испытания

**Первое, 100 секунд** — наблюдения пользователя: десять последовательных команд `sleep 10`, промежуточный прогресс от `10/100` до `100/100`. Пользователь дважды сворачивал приложение, блокировал экран и возвращался обычным ярлыком. После первого возврата дважды подтверждал обычное выполнение действий агента; эта система вне области сегодняшней работы. Обновляющийся прогресс сохранился, все десять команд завершились, один итоговый ответ, `Working` исчез без перезапуска. Отдельное время нативной переподписки Hca по этому испытанию не установлено: вывод основан на наблюдениях пользователя.

**Второе испытание** — пользовательские скриншоты: 23:13:21 разговор в статусе выполнения; 23:13:56 текст ответа `30/30`, `ГОТОВО`; 23:14:04 тот же завершённый ответ; 23:14:07 вместо `Working` отображается `now`; 23:14:11 после повторного входа сохранён единственный ответ. Это не испытание на 50 или 100 секунд; точную длительность только по скриншотам не определять.

**Реальные нативные диагностические события второго испытания** (обезличенный разговор A):

| Время | Событие |
|---|---|
| 23:12:46.249 | Native Core `RUNNING`, наблюдатель подписан |
| 23:13:00.161 / 23:13:01.160 | Activity `onPause/onStop` |
| 23:13:01.378 | Activity `onResume` |
| 23:13:04.025 / 23:13:04.974 | Повторно `onPause/onStop` |
| 23:13:20.962 | Activity `onResume` во время выполнения |
| 23:13:53.523 | Core `IDLE/fullyIdle` |
| 23:13:53.524 | Tracker `FINISHED` / нативный наблюдатель `FINISHED` |
| 23:13:53.537 | Публикация завершения в канале текущего чата, `popup=false` |

Нативных ошибок потока и повторных завершений в окне 23:10–23:16 нет. Журнал фиксирует переходы Activity и состояния Core, **не содержит независимого события повторной подписки JS Hca**. Точное `agy:host-resume` → transport abort → новая подписка → snapshot подтверждено **исполнением настоящего пропатченного Hca с подставным Core-потоком** в регрессионном тесте, а не отдельной записью живого Core. Совокупность пользовательских испытаний и тестов — положительная приёмка восстановления.

### 4. NDa / Sidebar — PASS в доступных пределах

На настоящем устройстве подтверждено: `Working` присутствовал во время задания и исчезал после выполнения; после фоновой работы двух агентов они имели отдельные непрочитанные отметки без ложного `Working`.

В программных тестах: `NDa` после host resume прерывает старую summary-подписку, отклоняет устаревшие кадры и принимает новый снапшот отдельных разговоров; Hca rehydration не перезапускает задание. Отдельный production-журнал фактического subscribe/unsubscribe `NDa` отсутствует, поэтому **не утверждаем, что эти конкретные операции напрямую подтверждены Android-журналом**.

### 5. Внешний вид длинного пользовательского сообщения

В пользовательских снимках около 23:13:56/23:14:04 исходное длинное сообщение визуально свёрнуто/обрезано внутри карточки с тенью или градиентом. **Фриз не установлен**, связи с Hca не доказано. Исследование штатного upstream поведения, обрезки, прокрутки и раскрытия отложено: [NEXT_UPDATE_TODO.md](NEXT_UPDATE_TODO.md).

### 6. Состояние релизных артефактов, границы приёмки

Последний собранный и установленный dev APK: `com.agy.dev`, `Download/Antigravity-v2.4.0-three-patches-dev-20261020.apk`, SHA-256 `2099e2a7cd320db39f6ade8f874958315994607adda3a01ebbbd03d1cb456251`. Подпись APK v2/v3 и наличие трёх staged hooks проверены; deferred readfix отсутствует, исходный `web/main.js` сохранён. В контролируемых VM/Java-тестах пройдены React `VAb/z_`, `Hca`, `NDa`, FIFO, tracker, ConnectRPC и Gateway token.

**Итог:** по предоставленным реальным пользовательским испытаниям и доступным журналам новых блокирующих дефектов трёх патчей не выявлено. Пятиисекундное замедление FIFO при одновременном прибытии двух завершений и отдельные live-события переподписки Hca/NDa остаются границами доказательств, а не зафиксированными дефектами. PR #1 можно рекомендовать к merge **после явного разрешения владельца**; самостоятельно не сливать в `main`, не публиковать Release, не изменять `com.agy`.
