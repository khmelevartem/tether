---
name: smoke-test
description: Прогон базового smoke-теста (happy-path) по платформам Tether — Desktop CLI (uber jar + /health + mDNS + stdin commands), Android (если adb-устройство подключено) с upload host→Android, нативная компиляция macosArm64 и iosSimulatorArm64. Используй этот скилл, когда пользователь просит «прогони smoke», «прогони smoke-тест», «basic smoke», «проверь сборку по платформам перед merge», «дымовой тест». Не путать с unit-тестами (`./gradlew allTests`) — smoke это рантайм-проверка стартует ли всё и видят ли друг друга, не корректность логики.
---

# Smoke-test skill

Прогоняет базовые smoke-сценарии по таргетам Tether и выдаёт human-readable отчёт.

**Это smoke, не регресс.** Цель — за 1-3 минуты увидеть, что ничего фундаментально не сломано (CLI стартует, FileServer отвечает, mDNS публикуется, stdin-команды работают, upload roundtrip OK, нативные таргеты компилируются). Корректность бизнес-логики — задача `./gradlew allTests`.

## Чего скилл НЕ проверяет (вне скоупа автоматизации)

В начале прогона **проговори это пользователю** — границы покрытия:

- **Физический iPhone** — нет, требует ручной подписи и доверия сертификата.
- **macOS run** — у `macosArm64` нет entry point, только sanity-компиляция.
- **iOS receive/upload** — `FileServer.apple` это stub, на `start()` бросает `error()`. Только sanity-компиляция `iosSimulatorArm64`.
- **iOS simulator runtime** — в текущей версии скилла не запускаем (требует Xcode-проекта и времени), только compile.
- **Тап по Notification «Stop»** — заменяется на `am force-stop` или broadcast. Что *кнопка нарисована и работает* — проверить вручную.
- **Sleep/wake реального девайса** — `adb shell input keyevent SLEEP/WAKEUP` это аппроксимация, не настоящий power state.
- **Rotation effects на FGS-выживаемость** — на эмуляторе ≠ на реальном устройстве.

В отчёте все эти позиции должны быть в секции **«Manual verification required»**.

## Ключевое решение: uber jar, не gradle run

**Не используй `./gradlew :composeApp:run` для авто-запуска CLI.** Compose Desktop's `run` task в non-TTY среде (когда скилл запускается агентом без терминала) не пробрасывает `System.in` корректно — `readLine()` сразу возвращает null, CLI выходит до того как FileServer успеет связаться с портом.

Вместо этого собирай uber jar и запускай через `java -jar`:

```bash
./gradlew :composeApp:packageUberJarForCurrentOS -q
java -jar composeApp/build/compose/jars/com.tubetoast.tether-macos-arm64-1.0.0.jar --name SmokeMac --port 0
```

При прямом запуске `java -jar` процесс наследует stdin от bash, и FIFO-подачу команд в stdin (`list`, `quit`) можно делать напрямую.

Имя jar для других ОС/архитектур: подставь `linux-amd64`, `windows-x64` и т.п. — но скилл рассчитан на macOS-агента.

## План прогона

Скилл выполняет блоки последовательно. Падение блока не блокирует следующие. Cleanup выполняется **всегда**, даже если ранее были FAIL.

### Блок 0: Подготовка

1. Убедись, что нет работающих инстансов CLI (память: внешние mDNS-сервисы могут глючить тесты):
   ```bash
   pgrep -fl 'com.tubetoast.tether-.*\.jar|composeApp:run' || echo "clean"
   ```
   Если есть — `kill` их.
2. Собрать uber jar:
   ```bash
   ./gradlew :composeApp:packageUberJarForCurrentOS -q
   ls composeApp/build/compose/jars/
   ```
   Запомни имя файла. Для macosArm64 это `com.tubetoast.tether-macos-arm64-1.0.0.jar`.

Если build падает — все остальные блоки SKIP с причиной «uber jar build failed».

### Блок 1: Desktop CLI

Запуск через FIFO (stdin keeper):

```bash
JAR=composeApp/build/compose/jars/com.tubetoast.tether-macos-arm64-1.0.0.jar
LOG=/tmp/smoke-cli.log
mkfifo /tmp/smoke-cli-in
sleep 600 > /tmp/smoke-cli-in &              # keeper holds writer fd open
KEEPER=$!; disown $KEEPER
echo $KEEPER > /tmp/smoke-cli-keeper.pid

nohup java -jar $JAR --name SmokeMac --port 0 < /tmp/smoke-cli-in > $LOG 2>&1 &
JPID=$!; disown $JPID
echo $JPID > /tmp/smoke-cli.pid
```

Wait до 30 сек:
```bash
for i in $(seq 1 30); do grep -q 'FileServer started' $LOG && break; sleep 1; done
PORT=$(grep -oE 'port[[:space:]]*:[[:space:]]*[0-9]+' $LOG | grep -oE '[0-9]+' | head -1)
```

Сценарии:
1. **Startup** — port распарсен, java pid жив (`ps -p $JPID`). PASS если оба условия.
2. **`/health`** — `curl -sf --max-time 5 http://localhost:$PORT/health` → должно вернуть `Tether OK`.
3. **Port LISTEN** — `lsof -nP -iTCP:$PORT | head -3` показывает java listener.
4. **mDNS publish** — `( dns-sd -B _tether._tcp. local. 2>&1 & DNSSD_PID=$!; sleep 8; kill $DNSSD_PID 2>/dev/null ) | grep SmokeMac` — найти SmokeMac в browse output.
5. **stdin `list`** — `echo "list" > /tmp/smoke-cli-in &; sleep 1; tail $LOG` — должен напечатать `[list]` или `[peers]` строку.
6. **stdin `quit` graceful** — `echo "quit" > /tmp/smoke-cli-in &`, ждать до 8 сек, проверить `ps -p $JPID` — процесс должен умереть. Если не умер — FAIL «не graceful», `kill -9` и идти дальше.

### Блок 2: Desktop ↔ Desktop send (через CLI)

**Важно:** upload должен идти через **CLI команду `send`**, а не через `curl POST /upload`. Это смоук-тест пользовательского сценария, а не endpoint'а.

Поднимаешь второй CLI-инстанс параллельно с первым (на другом FIFO/log/jar-args), ждёшь, пока mDNS даст обоим увидеть друг друга, и шлёшь команду `send <peer> <path>` в stdin первого.

```bash
JAR=composeApp/build/compose/jars/com.tubetoast.tether-macos-arm64-1.0.0.jar

# Второй инстанс (первый уже запущен из блока 1, имя SmokeMacA)
mkfifo /tmp/smoke-cliB-in
sleep 600 > /tmp/smoke-cliB-in & KEEPER_B=$!; disown $KEEPER_B
echo $KEEPER_B > /tmp/smoke-cliB-keeper.pid
nohup java -jar $JAR --name SmokeMacB --port 0 < /tmp/smoke-cliB-in > /tmp/smoke-cliB.log 2>&1 &
JPID_B=$!; disown $JPID_B
echo $JPID_B > /tmp/smoke-cliB.pid

# Wait until BOTH see each other
for i in $(seq 1 30); do
  grep -q 'SmokeMacA' /tmp/smoke-cliB.log 2>/dev/null && \
  grep -q 'SmokeMacB' /tmp/smoke-cli.log 2>/dev/null && break
  sleep 1
done

# Send file через stdin первого CLI
echo "send-via-cli-$(date +%s)" > /tmp/smoke-send.txt
echo "send SmokeMacB /tmp/smoke-send.txt" > /tmp/smoke-cli-in &
sleep 4

# Проверить что в логе первого CLI: "[send] OK"
grep -E "^\[send\] (OK|FAIL)" /tmp/smoke-cli.log | tail -3

# Проверить что файл реально landed в SmokeMacB's downloads
diff /tmp/smoke-send.txt "$HOME/Downloads/Tether/smoke-send.txt" && echo PASS || echo FAIL
```

PASS если:
1. в логе первого CLI есть `[send] OK — <ms> ms → <savedPath>`
2. содержимое файла идентично оригиналу

Bonus: можно проверить что `[send] FAIL: peer 'NonExistent' not found` корректно обрабатывается — но это уже за рамки smoke.

### Блок 3: Android (условно)

Сначала проверка устройства:
```bash
adb devices | awk '/device$/ && !/List/ {print $1}'
```

Если пусто — весь блок SKIP с причиной «no adb device connected».

Если есть устройство:

1. **Install:** `./gradlew -q :composeApp:installDebug`
2. **Logcat clear:** `adb logcat -c`
3. **Start activity:** `adb shell am start -n com.tubetoast.tether/.MainActivity`
4. **Wait + grep logs (8 сек):**
   ```bash
   sleep 8
   adb logcat -d 2>&1 | grep -E "TetherFGService|MdnsDiscovery|FileServer started|NSD service registered" | head -20
   ```
   Ищем строки:
   - `TetherFGService: FileServer started on port <N>` → парсим `<N>` как `ANDROID_PORT`
   - `mDNS started: name=Tether-...` → PASS «Android FGS+mDNS up»
5. **Получить IP:**
   ```bash
   ANDROID_IP=$(adb shell ip route 2>&1 | awk '/wlan0.*src/ {print $9}' | head -1)
   ```
   Если эмулятор и IP `10.0.2.x` — это NAT, host достучится через `adb forward tcp:18080 tcp:$ANDROID_PORT` и localhost:18080. Для физ. устройства — прямо `$ANDROID_IP:$ANDROID_PORT`.
6. **`/health` sanity:** `curl -sf http://$ANDROID_IP:$ANDROID_PORT/health` → `Tether OK`. Это единственное место, где curl допустим — endpoint sanity, не пользовательский flow.
7. **Cross-discovery:** в stdin Desktop CLI послать `list`, проверить что в логе появилось имя `Tether-<MODEL>` (только для физ. устройства в одной WiFi; эмулятор в NAT — обычно не виден).
8. **Send host → Android (через CLI):**
   ```bash
   ANDROID_NAME=$(grep -oE 'Tether-[A-Za-z0-9_]+' /tmp/smoke-cli.log | head -1)
   echo "send-to-android-$(date +%s)" > /tmp/smoke-android.txt
   echo "send $ANDROID_NAME /tmp/smoke-android.txt" > /tmp/smoke-cli-in &
   sleep 5
   grep -E "^\[send\] (OK|FAIL)" /tmp/smoke-cli.log | tail -3
   adb shell cat /sdcard/Android/data/com.tubetoast.tether/files/Tether/smoke-android.txt | diff - /tmp/smoke-android.txt
   ```
   PASS если в логе Desktop CLI `[send] OK — <ms> ms → <savedPath>` И файл на Android идентичен.
9. **Stop service:** `adb shell am force-stop com.tubetoast.tether`. PASS если приложение умерло.

Помечай каждый под-сценарий отдельно: install, FGS+mDNS up, /health sanity, cross-discovery, send-to-android, stop.

### Блок 4: Native compile sanity

```bash
./gradlew -q :composeApp:compileKotlinMacosArm64
./gradlew -q :composeApp:compileKotlinIosSimulatorArm64
```

PASS если exit=0. FAIL — приложить последние ~30 строк stderr.

### Блок 5: Cleanup

Выполняется **всегда**:
- `kill $(cat /tmp/smoke-cli.pid /tmp/smoke-cliB.pid /tmp/smoke-cli-keeper.pid /tmp/smoke-cliB-keeper.pid 2>/dev/null) 2>/dev/null`
- `pkill -f 'com.tubetoast.tether.*\.jar'` (страховка)
- `rm -f /tmp/smoke-cli*-in /tmp/smoke-cli*.log /tmp/smoke-cli*.pid /tmp/smoke-cli*-keeper.pid /tmp/smoke-port.txt /tmp/smoke-send.txt /tmp/smoke-android.txt`
- `rm -f $HOME/Downloads/Tether/smoke-send.txt` (сами создали — сами убираем)
- `adb shell rm -f /sdcard/Android/data/com.tubetoast.tether/files/Tether/smoke-android.txt`
- `adb shell am force-stop com.tubetoast.tether`

## Формат отчёта

В конце прогона печатаешь markdown-отчёт ровно по этой структуре:

```markdown
# Smoke test report

**Дата:** YYYY-MM-DD HH:MM
**Branch:** <git branch>
**Commit:** <short sha>
**Jar:** com.tubetoast.tether-macos-arm64-1.0.0.jar (built in Ns)

## Summary

- **PASS:** N
- **FAIL:** M
- **SKIP:** K
- **Total:** N+M+K
- **Verdict:** 🟢 GREEN | 🟡 YELLOW (есть SKIP, FAIL=0) | 🔴 RED (есть FAIL)

## Results

| Block | Scenario | Result | Details |
|---|---|---|---|
| Desktop CLI | uber jar build | ✓ PASS | 8s |
| Desktop CLI | startup + port parse | ✓ PASS | port=49507, pid=83952 |
| Desktop CLI | /health | ✓ PASS | "Tether OK" |
| Desktop CLI | port LISTEN | ✓ PASS | java *:49507 |
| Desktop CLI | mDNS publish | ✓ PASS | SmokeMac в dns-sd -B |
| Desktop CLI | stdin `list` | ✓ PASS | peer printed |
| Desktop CLI | graceful `quit` | ✓ PASS | exit in 3s |
| Desktop↔Desktop | upload roundtrip | ✓ PASS | diff empty |
| Android | adb device | ✓ PASS | <serial>, model, API |
| Android | installDebug | ✓ PASS | 4s |
| Android | FGS + FileServer up | ✓ PASS | port=42367 |
| Android | mDNS publish | ✓ PASS | Tether-<MODEL> |
| Android | /health (over WiFi) | ✓ PASS | "Tether OK" |
| Android | upload host→Android | ✓ PASS | content match |
| Android | cross-discovery (Desktop sees Android) | ~ PARTIAL | seen в browse, deepening |
| Android | force-stop | ✓ PASS | process killed |
| Native | compileKotlinMacosArm64 | ✓ PASS | 1s |
| Native | compileKotlinIosSimulatorArm64 | ✓ PASS | 2s |

## Failures

(подробности FAIL: команда, stderr tail, гипотеза причины)

## Manual verification required (не покрыто smoke)

- iOS simulator runtime — запустить `iosApp/iosApp.xcodeproj` в Xcode, проверить mDNS publish.
- Физический iPhone — установить через Xcode, проверить кросс-обнаружение с Desktop.
- macOS run — нет entry point, не запускается.
- iOS receive (FileServer.apple — stub) — пропускаем.
- Notification «Stop» button on Android — проверить тап вручную; smoke использует `am force-stop`.
- Sleep/wake real device — `adb shell input keyevent` ≠ реальный power state.
- Rotation persistence — на реальном устройстве, эмулятор недостаточен.

## Environment

- OS: Darwin <version> arm64
- Java: <java -version>
- adb device: <serial / model / API> или "none"
- Xcode: <xcodebuild -version | head -1>
- dns-sd: <path>
```

## Как вызывать

Когда пользователь просит «прогони smoke»:

1. Печатаешь короткий план (1-2 строки): «прогоню Desktop CLI через uber jar, Desktop↔Desktop upload, Android если есть устройство, native compile. Что не проверяю — см. отчёт».
2. Запускаешь блоки.
3. Печатаешь отчёт.
4. Если verdict 🔴 — даёшь recommend: какой блок упал и куда смотреть.

Не уточняй у пользователя — скилл должен быть «zero-question»: всё, что неавтоматизируемо, идёт в Manual verification.

## Edge cases при прогоне

- **Gradle daemon занят** — не убивай его, он переиспользуется.
- **Uber jar старый (изменился код)** — `packageUberJarForCurrentOS` сам пересоберёт что нужно. Не делай `clean`.
- **`dns-sd` не на macOS** — Linux нет; в этом случае mDNS browse SKIP с причиной «dns-sd not available».
- **`timeout` на macOS отсутствует** — используй pattern `( cmd & PID=$!; sleep N; kill $PID )` вместо `timeout`.
- **FIFO writer keeper умер раньше времени** — readLine() вернёт null, CLI выйдет; проверяй `ps -p $KEEPER`.
- **Эмулятор Android в NAT (10.0.2.x)** — `adb forward` обязателен, прямой доступ host→android не работает.
- **Несколько adb-устройств** — выбирай первое или fail с уточнением. Не вешай скилл на специфичный serial.

## Что НЕ делать

- **Не используй `./gradlew :composeApp:run`** — баг со stdin (см. выше).
- **Не запускай `allTests`** — это другой инструмент. Smoke ≤3 минуты.
- **Не модифицируй код приложения** даже если видишь проблему. Сообщай в отчёте, заводи issue отдельно.
- **Не лезь в `~/Downloads`** ничего кроме `Tether/smoke.txt` — там пользовательский контент.
- **Не запускай `./gradlew clean`** — съест кеш и замедлит следующий прогон.
- **Не отправляй ничего в сеть** кроме localhost и `$ANDROID_IP` (последний — только если adb-устройство подключено).
