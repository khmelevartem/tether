# Gradle JavaExec + non-TTY → CLI выходит до bind порта

В non-TTY (subprocess агента, CI) `JavaExec`-таска отдаёт worker'у `NullInputStream` вместо `System.in`. `readLine()` мгновенно возвращает null, `runBlocking` отматывается, `System.exit(0)` срабатывает раньше, чем Netty успевает забиндить порт. Симптом: в логе печатается `FileServer started → http://...`, но `curl /health` отвечает `connection refused`.

Затрагивает `:composeApp:runDesktopCli` (CLI dev runner). Workaround для автоматизации (smoke, CI, тесты с stdin) — CLI fat jar:

```bash
./gradlew :composeApp:cliJar -q
java -jar "$(ls composeApp/build/libs/tether-cli*.jar | head -1)" --name X --port 0 < fifo
```

Прямой `java` наследует stdin от bash; FIFO-keeper держит fd открытым.

Альтернатива — починить task в `composeApp/build.gradle.kts`:
```kotlin
tasks.named<JavaExec>("runDesktopCli") { standardInput = System.`in` }
```

В IDE «Run» → не воспроизводится (TTY есть).
