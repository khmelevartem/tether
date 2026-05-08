# Gradle `:run` task + Compose Desktop — stdin gotcha

Заметка из работы над smoke-test скиллом (#69, PR #70).

---

## Симптом

`./gradlew :composeApp:run --args="..."` отрабатывает без ошибок, в stdout появляется:

```
=== Tether debug runner ===
device : SmokeMac
port   : 49267
FileServer started  →  http://localhost:49267/health
mDNS started → advertising 'SmokeMac' on port 49267
Commands: send <peer-name> <path>, list, quit
[peers] none
```

…а через секунду процесс **уже мёртв**, порт не слушает, `curl http://localhost:$PORT/health` — `connection refused`. Gradle daemon в IDLE, JVM-worker'а нет.

## Причина

Compose Desktop's `application { ... }` блок (Compose Multiplatform plugin) создаёт `JavaExec`-task `:run`, у которой `standardInput` по умолчанию = `NullInputStream`. В TTY-терминале IDE/IntelliJ это переопределяется и stdin прокидывается; в **non-TTY** окружении (subprocess агента, CI, любой `bash` без `tty`) — нет.

В коде `Main.kt`:
```kotlin
while (running) {
    val line = readLine() ?: break  // ← null EOF мгновенно
    ...
}
System.exit(0)
```

`readLine()` сразу возвращает null, цикл выходит, `System.exit(0)` — JVM завершается. **FileServer.start() при этом запускается асинхронно** (Netty bind в отдельном потоке), и часто не успевает реально забиндиться до момента exit. Поэтому строка `FileServer started` уже напечатана (в ней реальный порт от sychronous bind result), но порт не слушает к моменту, когда внешний наблюдатель попытается curl'нуть.

## Workaround

Для interactive automation (скиллы, тесты, агентский запуск) — **собирать uber jar и запускать через `java -jar`**. Прямой `java`-процесс наследует stdin от bash, и FIFO/pipe-подача команд работает:

```bash
./gradlew :composeApp:packageUberJarForCurrentOS -q
JAR=$(ls composeApp/build/compose/jars/*.jar | head -1)

mkfifo /tmp/in
sleep 600 > /tmp/in &              # keeper держит writer fd открытым
java -jar "$JAR" --name MyMac --port 0 < /tmp/in > /tmp/log 2>&1 &

# Теперь команды можно слать в stdin:
echo "list" > /tmp/in
echo "quit" > /tmp/in
```

Альтернатива (не делалось, но возможно): добавить в `composeApp/build.gradle.kts` явное `tasks.named<JavaExec>("run") { standardInput = System.\`in\` }` — это починит `:run` для non-TTY. Не делали в #70, потому что для smoke-скилла uber jar устойчивее (не зависит от Gradle daemon, явные процесс/PID, проще FIFO).

## Когда это важно

- Любая автоматизация, которая ожидает interactive CLI (`list`, `send`, `quit`).
- CI-прогоны smoke.
- Тесты с прокидыванием stdin.

В обычном «открыть IDE → нажать Run» это **не воспроизводится** — потому Gradle :run живёт.
