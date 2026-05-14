---
name: smoke-test
description: Прогон базового smoke-теста (happy-path) по платформам Tether — Desktop CLI (cli jar + /health + mDNS + stdin commands), Desktop↔Desktop send через CLI, Android (если adb-устройство подключено) с send-from-Desktop, нативная компиляция macosArm64 и iosSimulatorArm64. Используй этот скилл, когда пользователь просит «прогони smoke», «прогони smoke-тест», «basic smoke», «basic regression», «проверь сборку по платформам перед merge», «дымовой тест». Не путать с unit-тестами (`./gradlew allTests`) — smoke это рантайм-проверка стартует ли всё и видят ли друг друга, не корректность логики.
---

# Smoke-test skill

Прогоняет базовые smoke-сценарии по таргетам Tether и выдаёт human-readable отчёт.

**Это smoke, не регресс.** Цель — за 1-3 минуты увидеть, что ничего фундаментально не сломано (CLI стартует, FileServer отвечает, mDNS публикуется, stdin-команды работают, send roundtrip OK, нативные таргеты компилируются). Корректность бизнес-логики — задача `./gradlew allTests`.

## Чего скилл НЕ проверяет (вне скоупа автоматизации)

В начале прогона **проговори это пользователю** — границы покрытия:

- **Физический iPhone** — нет, требует ручной подписи и доверия сертификата.
- **macOS run** — у `macosArm64` нет entry point, только sanity-компиляция.
- **iOS receive/send** — `FileServer.apple` это stub, на `start()` бросает `error()`. Только sanity-компиляция `iosSimulatorArm64`.
- **iOS simulator runtime** — в текущей версии скилла не запускаем (требует Xcode-проекта и времени), только compile.
- **Android-инициированный send (Android → Desktop)** — актуально пока на Android нет программного триггера отправки (intent / UI-кнопка / broadcast). Скилл проверяет обратное направление: Desktop → Android через CLI `send`. Когда такой триггер появится — добавить отдельный шаг в Блок 3.
- **Тап по Notification «Stop»** — заменяется на `am force-stop` или broadcast. Что *кнопка нарисована и работает* — проверить вручную.
- **Sleep/wake реального девайса** — `adb shell input keyevent SLEEP/WAKEUP` это аппроксимация, не настоящий power state.
- **Rotation effects на FGS-выживаемость** — на эмуляторе ≠ на реальном устройстве.

В отчёте все эти позиции должны быть в секции **«Manual verification required»**.

## Запуск CLI

```bash
./gradlew :composeApp:cliJar -q
JAR=$(ls composeApp/build/libs/tether-cli*.jar 2>/dev/null | head -1)
[ -z "$JAR" ] && { echo "cli jar not found"; exit 1; }
java -jar "$JAR" --name SmokeMacA --port 0 < fifo
```

FIFO держит stdin открытым для команд `list`, `send`, `quit`.

## План прогона

Скилл выполняет блоки последовательно. Падение блока не блокирует следующие. Cleanup выполняется **всегда**, даже если ранее были FAIL.

### Блок 0: Подготовка

1. Убедись, что нет работающих инстансов CLI:
   ```bash
   pgrep -fl 'com.tubetoast.tether-.*\.jar|composeApp:run' || echo "clean"
   ```
   Если есть — `kill` их (память: внешние mDNS-сервисы могут глючить тесты).
2. Собрать CLI jar:
   ```bash
   ./gradlew :composeApp:cliJar -q
   JAR=$(ls composeApp/build/libs/tether-cli-*.jar composeApp/build/libs/tether-cli.jar 2>/dev/null | head -1)
   ```
   Запомни путь.

Если build падает или JAR не найден — все остальные блоки SKIP с причиной «cli jar build failed».

### Блок 1: Desktop CLI (инстанс A)

Запуск через FIFO (stdin keeper). Имя — `SmokeMacA` (важно: должно совпасть с тем, что Блок 2 ищет в логе инстанса B):

```bash
LOG_A=/tmp/smoke-cliA.log
mkfifo /tmp/smoke-cliA-in
sleep 600 > /tmp/smoke-cliA-in &              # keeper holds writer fd open
KEEPER_A=$!; disown $KEEPER_A
echo $KEEPER_A > /tmp/smoke-cliA-keeper.pid

nohup java -jar "$JAR" --name SmokeMacA --port 0 < /tmp/smoke-cliA-in > "$LOG_A" 2>&1 &
JPID_A=$!; disown $JPID_A
echo $JPID_A > /tmp/smoke-cliA.pid
```

Wait до 30 сек (поллинг лога надёжнее, чем `sleep` на удачу):
```bash
for i in $(seq 1 30); do grep -q 'FileServer started' $LOG_A && break; sleep 1; done
PORT_A=$(grep -oE 'port[[:space:]]*:[[:space:]]*[0-9]+' $LOG_A | grep -oE '[0-9]+' | head -1)
```

Сценарии:
1. **Startup** — port распарсен, java pid жив (`ps -p $JPID_A`). PASS если оба условия.
2. **`/health`** — `curl -sf --max-time 5 http://localhost:$PORT_A/health` → должно вернуть `Tether OK`.
3. **`/pair` — формат публичного ключа.** Эндпоинт возвращает X.509-encoded EC P-256 SubjectPublicKeyInfo: ровно 91 байт, первый байт `0x30` (DER `SEQUENCE`), байт 26 — `0x04` (uncompressed EC point marker). Проверяем форму, не только что-то-вернулось — placeholder вернул бы непустой ответ и прошёл бы поверхностную проверку.
   ```bash
   PAIR_RESP=$(curl -sf --max-time 5 -X POST http://localhost:$PORT_A/pair \
     -H "Content-Type: application/json" \
     -d '{"publicKey":[1,2,3], "deviceName":"smoke"}')
   echo "$PAIR_RESP" | jq -e '.publicKey | length == 91 and .[0] == 48 and .[26] == 4' > /dev/null \
     && echo "PASS: X.509 EC P-256 SubjectPublicKeyInfo" \
     || { echo "FAIL: bad publicKey shape: $PAIR_RESP"; }
   ```
4. **Port LISTEN** — `lsof -nP -iTCP:$PORT_A | head -3` показывает java listener.
5. **mDNS publish (primary)** — поллим CLI-лог, ищем `mDNS started → advertising 'SmokeMacA' on port`. Это уже подтверждение публикации (CLI сам пишет это после успешного `discovery.start()`).
6. **mDNS publish (secondary, опционально)** — `( dns-sd -B _tether._tcp. local. 2>&1 & DNSSD_PID=$!; sleep 8; kill $DNSSD_PID 2>/dev/null ) | grep SmokeMacA`. Если `dns-sd` нет (Linux) — этот шаг SKIP, общий результат всё равно PASS по primary.
7. **stdin `list`** — `echo "list" > /tmp/smoke-cliA-in &; sleep 1; tail $LOG_A` — должен напечатать `[list]` или `[peers]` строку.
8. **stdin `quit` graceful** — `echo "quit" > /tmp/smoke-cliA-in &`, ждать до 8 сек, проверить `ps -p $JPID_A` — процесс должен умереть. Если не умер — FAIL «не graceful», `kill -9` и идти дальше.

**Замечание для Блока 2:** инстанс A держим живым до конца Блока 2, `quit` шлём только после успешного send. Иначе придётся перезапускать.

### Блок 2: Desktop ↔ Desktop send (через CLI)

**Важно:** send должен идти через **CLI команду `send`**, а не через `curl POST /upload`. Это смоук-тест пользовательского сценария, а не endpoint'а.

Поднимаешь второй CLI-инстанс (`SmokeMacB`) параллельно с A, ждёшь, пока mDNS даст обоим увидеть друг друга, шлёшь `send SmokeMacB <path>` в stdin A.

```bash
LOG_B=/tmp/smoke-cliB.log
mkfifo /tmp/smoke-cliB-in
sleep 600 > /tmp/smoke-cliB-in & KEEPER_B=$!; disown $KEEPER_B
echo $KEEPER_B > /tmp/smoke-cliB-keeper.pid
nohup java -jar "$JAR" --name SmokeMacB --port 0 < /tmp/smoke-cliB-in > "$LOG_B" 2>&1 &
JPID_B=$!; disown $JPID_B
echo $JPID_B > /tmp/smoke-cliB.pid

# Wait until BOTH see each other (имена должны точно совпадать с --name выше)
for i in $(seq 1 30); do
  grep -q 'SmokeMacA' $LOG_B 2>/dev/null && \
  grep -q 'SmokeMacB' $LOG_A 2>/dev/null && break
  sleep 1
done

# Send через stdin A
echo "send-via-cli-$(date +%s)" > /tmp/smoke-send.txt
echo "send SmokeMacB /tmp/smoke-send.txt" > /tmp/smoke-cliA-in &

# Поллим лог A на финальную строку send (не на удачу через sleep N)
for i in $(seq 1 15); do
  grep -qE "^\[send\] (OK|FAIL)" $LOG_A && break
  sleep 1
done
SEND_LINE=$(grep -E "^\[send\] (OK|FAIL)" $LOG_A | tail -1)
echo "$SEND_LINE"

# Парсим savedPath из строки "[send] OK — <ms> ms  →  <savedPath>" — не угадываем директорию
SAVED_B=$(echo "$SEND_LINE" | sed -nE 's/.*→[[:space:]]+(.+)$/\1/p')
if [ -n "$SAVED_B" ] && [ -f "$SAVED_B" ]; then
  diff /tmp/smoke-send.txt "$SAVED_B" && echo PASS || echo FAIL
else
  echo "FAIL: savedPath not parsed or file missing"
fi
```

PASS если:
1. в логе A есть `[send] OK — <ms> ms → <savedPath>`
2. файл по `savedPath` идентичен оригиналу

Cleanup инстанса B и квит A — в Блоке 5.

### Блок 3: Android (условно)

Сначала проверка устройства:
```bash
adb devices | awk '/device$/ && !/List/ {print $1}'
```

Если пусто — весь блок SKIP с причиной «no adb device connected».

Если есть устройство:

1. **Install:** `./gradlew -q :composeApp:installDebug`
2. **Logcat clear:** `adb logcat -c`
3. **Start activity:**
   ```bash
   adb shell am start -n com.tubetoast.tether/.MainActivity
   ```
4. **Ждём `NSD service registered` как anchor готовности** (вместо слепого `sleep 8`).
   Anchor для метрики cross-discovery ставится **после** того как Android-сторона опубликовалась — чтобы дельта мерила только пропагацию через сеть + JmDNS resolve, а не Android boot/init/наш wait-loop:
   ```bash
   DEADLINE=$(($(date +%s) + 12))
   while [ $(date +%s) -lt $DEADLINE ]; do
     adb logcat -d 2>/dev/null | grep -q "NSD service registered" && break
     sleep 1
   done
   NSD_READY_MS=$(python3 -c "import time; print(int(time.time() * 1000))")
   ```
   Парсим:
   - `TetherFGService: FileServer started on port <N>` → `ANDROID_PORT`
   - `Starting NSD: name=Tether-...` и `NSD service registered: ...` — разница времён = NSD probing latency, выводим в Details.
5. **Получить IP** (надёжный вариант через `ip addr`, не `ip route` — формат у некоторых вендоров отличается):
   ```bash
   ANDROID_IP=$(adb shell ip addr show wlan0 2>&1 | grep "inet " | awk '{print $2}' | cut -d/ -f1 | head -1)
   ```
   Если эмулятор и IP `10.0.2.x` — host-доступ через `adb forward tcp:18080 tcp:$ANDROID_PORT` и `localhost:18080`. Для физ. устройства — прямо `$ANDROID_IP:$ANDROID_PORT`.
6. **`/health` sanity:** `curl -sf http://$ANDROID_IP:$ANDROID_PORT/health` → `Tether OK`. Это единственное место, где curl допустим — endpoint sanity, не пользовательский flow.
7. **Cross-discovery с замером времени:** в stdin Desktop CLI поллим лог, ищем `Tether-<MODEL>` пока не появится. Дельта считается от `NSD_READY_MS` (момент когда Android уже опубликовался), не от `am start`:
   ```bash
   for i in $(seq 1 30); do
     sleep 1
     grep -E "\[peers\] .*Tether-" $LOG_A | grep -v "none" | tail -1 | grep -q . && break
   done
   NOW_MS=$(python3 -c "import time; print(int(time.time() * 1000))")
   DELTA_MS=$((NOW_MS - NSD_READY_MS))
   ANDROID_NAME=$(grep -oE 'Tether-[A-Za-z0-9_]+' $LOG_A | head -1)
   echo "cross-discovery: ${DELTA_MS}ms, peer=$ANDROID_NAME"
   ```
   В отчёт: `Android | cross-discovery | ✓ PASS | 250 ms` — network-propagation + JmDNS resolve.
8. **Send Desktop → Android (через CLI):**
   ```bash
   if [ -z "$ANDROID_NAME" ]; then
     echo "SKIP: cross-discovery did not surface Android peer (likely emulator NAT)"
   else
     echo "send-to-android-$(date +%s)" > /tmp/smoke-android.txt
     echo "send $ANDROID_NAME /tmp/smoke-android.txt" > /tmp/smoke-cliA-in &
     for i in $(seq 1 15); do
       grep -qE "^\[send\] (OK|FAIL)" $LOG_A && break
       sleep 1
     done
     SEND_LINE=$(grep -E "^\[send\] (OK|FAIL)" $LOG_A | tail -1)
     # На Android savedPath абсолютный — `cat` его напрямую через adb shell
     SAVED_PATH=$(echo "$SEND_LINE" | sed -nE 's/.*→[[:space:]]+(.+)$/\1/p')
     adb shell cat "$SAVED_PATH" 2>/dev/null | diff - /tmp/smoke-android.txt && echo PASS || echo FAIL
   fi
   ```
   PASS если в логе Desktop CLI `[send] OK` И файл на Android по распарсенному savedPath идентичен. Если ANDROID_NAME пустой — это **SKIP, не FAIL** (явная причина: cross-discovery недоступен).
9. **Stop service:** `adb shell am force-stop com.tubetoast.tether`. PASS если приложение умерло. (Тап Notification «Stop» — manual.)

**Direction note:** этот блок проверяет Desktop → Android. Обратное направление (Android → Desktop) автоматизировать нельзя — см. секцию «Чего скилл НЕ проверяет».

Помечай каждый под-сценарий отдельно: install, FGS+mDNS up (с NSD probing latency), /health sanity, cross-discovery (с ms), send-desktop-to-android, stop.

### Блок 4: Native compile sanity

```bash
./gradlew -q :composeApp:compileKotlinMacosArm64
./gradlew -q :composeApp:compileKotlinIosSimulatorArm64
```

PASS если exit=0. FAIL — приложить последние ~30 строк stderr.

### Блок 5: Cleanup

Выполняется **всегда**:
- `kill $(cat /tmp/smoke-cliA.pid /tmp/smoke-cliB.pid /tmp/smoke-cliA-keeper.pid /tmp/smoke-cliB-keeper.pid 2>/dev/null) 2>/dev/null`
- `pkill -f 'com.tubetoast.tether.*\.jar'` (страховка)
- `rm -f /tmp/smoke-cli*-in /tmp/smoke-cli*.log /tmp/smoke-cli*.pid /tmp/smoke-cli*-keeper.pid /tmp/smoke-send.txt /tmp/smoke-android.txt`
- Файлы в `~/Downloads/Tether/`, которые сами создали — убираем по `savedPath` из лога A (не по угаданному пути).
- `adb shell rm -f /sdcard/Android/data/com.tubetoast.tether/files/Tether/smoke-android.txt` (или по `SAVED_PATH` если парсили)
- `adb shell am force-stop com.tubetoast.tether`

## Формат отчёта

В конце прогона печатаешь markdown-отчёт:

```markdown
# Smoke test report

**Дата:** YYYY-MM-DD HH:MM
**Branch:** <git branch>
**Commit:** <short sha>
**CLI Jar:** <auto-detected name> (built in Ns)

## Summary

- **PASS:** N
- **FAIL:** M
- **SKIP:** K
- **Total:** N+M+K
- **Verdict:** 🟢 GREEN | 🟡 YELLOW (есть SKIP, FAIL=0) | 🔴 RED (есть FAIL)

## Results

| Block | Scenario | Result | Details |
|---|---|---|---|
| Build | cli jar | ✓ PASS | <Ns>, jar=<name> |
| Desktop CLI A | startup + port | ✓ PASS | port=49507, pid=83952 |
| Desktop CLI A | /health | ✓ PASS | "Tether OK" |
| Desktop CLI A | /pair X.509 EC P-256 | ✓ PASS | 91 bytes, DER prefix OK |
| Desktop CLI A | port LISTEN | ✓ PASS | java *:49507 |
| Desktop CLI A | mDNS publish (log) | ✓ PASS | advertising 'SmokeMacA' |
| Desktop CLI A | mDNS publish (dns-sd) | ✓ PASS | SmokeMacA в browse |
| Desktop CLI A | stdin `list` | ✓ PASS | peer printed |
| Desktop↔Desktop | send via CLI | ✓ PASS | savedPath parsed, diff empty |
| Desktop CLI A | graceful `quit` | ✓ PASS | exit in 3s |
| Android | adb device | ✓ PASS | <serial>, model, API |
| Android | installDebug | ✓ PASS | 4s |
| Android | FGS + FileServer | ✓ PASS | port=42367 |
| Android | NSD probing latency | ✓ PASS | 950ms (start→registered) |
| Android | mDNS publish | ✓ PASS | Tether-<MODEL> |
| Android | /health (over WiFi) | ✓ PASS | "Tether OK" |
| Android | cross-discovery | ✓ PASS | 2154ms (launch→peer-on-Desktop) |
| Android | send Desktop→Android | ✓ PASS | savedPath parsed, diff empty |
| Android | force-stop | ✓ PASS | process killed |
| Native | compileKotlinMacosArm64 | ✓ PASS | 1s |
| Native | compileKotlinIosSimulatorArm64 | ✓ PASS | 2s |

## Failures

(подробности FAIL: команда, stderr tail, гипотеза причины)

## Manual verification required (не покрыто smoke)

- iOS simulator runtime — запустить `iosApp/iosApp.xcodeproj` в Xcode, проверить mDNS publish.
- Физический iPhone — установить через Xcode, проверить кросс-обнаружение с Desktop.
- macOS run — нет entry point.
- iOS receive (FileServer.apple — stub) — пропускаем по дизайну.
- **Android-инициированный send (Android → Desktop)** — нет CLI на Android, скилл проверяет обратное направление.
- Notification «Stop» button on Android — проверить тап вручную; smoke использует `am force-stop`.
- Sleep/wake real device — `adb input keyevent` ≠ реальный power state.
- Rotation persistence — на реальном устройстве, эмулятор недостаточен.

## Environment

- OS: Darwin <version> arm64
- Java: <java -version>
- adb device: <serial / model / API> или "none"
- Xcode: <xcodebuild -version | head -1>
- dns-sd: <path> или "not available"
```

## Как вызывать

Когда пользователь просит «прогони smoke»:

1. Печатаешь короткий план (1-2 строки): «прогоню Desktop CLI через cli jar, Desktop↔Desktop send, Android если есть устройство, native compile. Что не проверяю — см. отчёт».
2. Запускаешь блоки.
3. Печатаешь отчёт.
4. Если verdict 🔴 — даёшь recommend: какой блок упал и куда смотреть.

Не уточняй у пользователя — скилл должен быть «zero-question»: всё, что неавтоматизируемо, идёт в Manual verification.

## Edge cases при прогоне

- **Gradle daemon занят** — не убивай его, переиспользуется.
- **CLI jar устаревший (изменился код)** — `cliJar` сам пересоберёт что нужно. Не делай `clean`.
- **Имя jar может содержать версию** — определяй динамически через glob `composeApp/build/libs/tether-cli-*.jar composeApp/build/libs/tether-cli.jar | head -1`. Не хардкодь имя файла.
- **`dns-sd` не на macOS** — Linux нет; secondary mDNS check SKIP с причиной «dns-sd not available». Primary check (grep CLI лога на `mDNS started`) всё равно работает.
- **`timeout` на macOS отсутствует** — pattern `( cmd & PID=$!; sleep N; kill $PID )` вместо `timeout`.
- **FIFO writer keeper умер раньше времени** — readLine() вернёт null, CLI выйдет; проверяй `ps -p $KEEPER`.
- **Эмулятор Android в NAT (10.0.2.x)** — cross-discovery не работает (multicast в NAT блокируется), `ANDROID_NAME` пустой → send-блок SKIP с понятной причиной, не FAIL. Health доступен через `adb forward`.
- **`ip route` ненадёжен на части вендоров** (ColorOS, MIUI отдают подсеть вместо src) — используй `ip addr show wlan0`.
- **Несколько adb-устройств** — выбирай первое или fail с уточнением. Не вешай скилл на специфичный serial.
- **`savedPath` всегда парсить из лога**, не угадывать `$HOME/Downloads/Tether/...`. Пользователь может изменить директорию загрузок.

## Что НЕ делать

- **Не используй `./gradlew :composeApp:run`** — это Compose UI, не CLI.
- **Не запускай `allTests`** — это другой инструмент. Smoke ≤3 минуты.
- **Не модифицируй код приложения** даже если видишь проблему. Сообщай в отчёте, заводи issue отдельно.
- **Не лезь в `~/Downloads`** дальше своих файлов — там пользовательский контент.
- **Не запускай `./gradlew clean`** — съест кеш и замедлит следующий прогон.
- **Не отправляй ничего в сеть** кроме localhost и `$ANDROID_IP` (последний — только если adb-устройство подключено).
- **Не угадывай пути назначения файлов** — всегда парси из `[send] OK — ... → <savedPath>`.
