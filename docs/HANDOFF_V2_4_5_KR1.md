# v2.4.5-kr1 — передача владельцу

Кандидат для ручной приёмки. Package ID: `com.krmobdev.antigravity`,
versionName `2.4.5-kr1`, versionCode `19` (стабильная версия: 18).

Подготовка выполняется только на Android в Termux. Две редакции:
standard и region-bypass; различаются только политикой Core gates.
Обе имеют один package ID, поэтому одновременно установить их нельзя.

## Неподписанные APK

После финальной проверки пути и SHA-256 фиксируются в
[V2_4_5_KR1_STATUS.md](V2_4_5_KR1_STATUS.md) и отдельном SHA256SUMS.
Неподписанные файлы не устанавливаются и не публикуются в GitHub Release.

Повторная локальная сборка из рабочей ветки, последовательно:

```bash
taskset -c 0-3 bash modules/builder/build_apk.sh --kr-release --unsigned
taskset -c 0-3 bash modules/builder/build_apk.sh --kr-release --region-bypass --unsigned
```

Результаты:
- `dist/antigravity-v2.4.5-kr1-unsigned.apk`
- `dist/antigravity-v2.4.5-kr1-region-bypass-unsigned.apk`

Helper сначала проверяет копии в Downloads, а при их отсутствии автоматически
берёт unsigned APK из dist текущего checkout. Обязательное предварительное
копирование через AnimaBridge не требуется. Имена возможных копий в Downloads:
- `Antigravity-Android-v2.4.5-kr1-unsigned.apk`
- `Antigravity-Android-v2.4.5-kr1-region-bypass-unsigned.apk`

## Подписание — только владельцем

Агент не запускает этот шаг, не читает keystore или пароли и не создаёт ключ.
Владелец сначала проверяет внешнюю резервную копию существующей signing identity,
затем из checkout выполняет на своём экране Termux:

```bash
bash tools/sign_release_interactive.sh standard
bash tools/sign_release_interactive.sh bypass
```

Пароли вводятся только в интерактивные запросы apksigner. Не передавать их в чат,
Git, параметры команд или журналы. Helper отказывается заменять подписанные APK
и до подписи сверяет unsigned SHA-256 с контрольным файлом этого checkout;
после подписи проверяет прежний сертификат, package ID и новую версию.

Ожидаемый публичный SHA-256 сертификата:
`23:AD:AC:86:42:3F:72:98:B0:A8:CC:D4:87:58:E6:E1:9B:E7:65:4D:4F:BD:EC:18:4E:18:4B:7E:16:03:0D:95`.

После подписи владелец независимо проверяет обе редакции:

```bash
apksigner verify --verbose --print-certs /storage/emulated/0/Download/Antigravity-Android-v2.4.5-kr1.apk
aapt dump badging /storage/emulated/0/Download/Antigravity-Android-v2.4.5-kr1.apk
sha256sum /storage/emulated/0/Download/Antigravity-Android-v2.4.5-kr1.apk

apksigner verify --verbose --print-certs /storage/emulated/0/Download/Antigravity-Android-v2.4.5-kr1-region-bypass.apk
aapt dump badging /storage/emulated/0/Download/Antigravity-Android-v2.4.5-kr1-region-bypass.apk
sha256sum /storage/emulated/0/Download/Antigravity-Android-v2.4.5-kr1-region-bypass.apk
```

Проверить scheme v2, сертификат выше, package ID, versionCode=19,
versionName=2.4.5-kr1 и отсутствие application-debuggable.
Хеши подписанных APK будут отличаться от неподписанных: создать новый SHA256SUMS.

## Ручная приёмка

Не удалять production для испытаний. Изолированная dev-проверка не доказывает
совместимость обновления production: после подписи владелец отдельно проверяет
обновление поверх v2.4.1-kr1 с сохранением данных.

- Реальные этапы нескольких Core-агентов, фон/выключенный экран,
  переподключение, завершение/ошибка/отмена, отсутствие ложного Working.
- Completion notifications, FIFO, переходы и прежнее read/unread.
- Терминальный UI: создание, команды, интерактивность, закрытие и повторное открытие.
- Android File Picker: несколько файлов до реального лимита (не более 5),
  отмена, повторный выбор, просмотр, удаление, отправка, ошибка формата/модели,
  возврат из фона; отсутствует ложное сообщение об успешной отправке.
- Composer: длинный ввод, IME, Send/Stop, + sheet, Back,
  модель/reasoning/голос, системная навигация/вырезы, крупный шрифт и TalkBack.
- Light/Dark, пользовательские пресеты и длинный чат с нативным fling.
- Pixel 7: отдельная приёмка, включая разрешение /bin///sh в /system/bin/sh.

Фактически выполненные автоматические и Android-проверки перечисляются в статусе.
Невыполненные пункты остаются PENDING. Merge в main, теги и публикация релиза
требуют отдельного решения владельца.
