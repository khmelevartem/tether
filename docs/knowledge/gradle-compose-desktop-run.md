# Gradle JavaExec + non-TTY → CLI exits before port bind

In a non-TTY environment (agent subprocess, CI) a `JavaExec` task passes `NullInputStream` instead of `System.in` to the worker. `readLine()` returns null immediately, `runBlocking` unwinds, and `System.exit(0)` fires before Netty has a chance to bind the port. Symptom: `FileServer started → http://...` is printed in the log, but `curl /health` responds with `connection refused`.

Affects `:composeApp:runDesktopCli` (CLI dev runner). Workaround for automation (smoke tests, CI, tests with stdin) — CLI fat jar:

```bash
./gradlew :composeApp:cliJar -q
java -jar "$(ls composeApp/build/libs/tether-cli*.jar | head -1)" --name X --port 0 < fifo
```

Plain `java` inherits stdin from bash; the FIFO keeper keeps the fd open.

Alternative — fix the task in `composeApp/build.gradle.kts`:
```kotlin
tasks.named<JavaExec>("runDesktopCli") { standardInput = System.`in` }
```

In IDE "Run" → not reproducible (TTY is present).
