# Testing

Тесты обязательны. При реализации любой функциональности пиши unit и/или интеграционные тесты — ориентируйся на краевые случаи из issue.

## Где что лежит

- `commonTest/` — протокол и shared-логика.
- `jvmTest/` — тесты, общие для Android и Desktop (например, `FileServerTest`); прогоняются в `desktopTest` и `androidUnitTest`.
- `desktopTest/` — Desktop-only (`FileClientTest`, `MdnsDiscoveryTest`).
- `appleTest/` — Apple-таргеты (см. ниже про NSRunLoop).

## Стиль

- `kotlin.test`.
- Для корутин — `runTest` + `TestDispatcher` (из `kotlinx-coroutines-test`), **не `runBlocking`**.
- Управляй временем виртуально через `advanceTimeBy()` / `advanceUntilIdle()` — это ускоряет тесты и делает их детерминированными.

## Реальное время vs виртуальное

- `Thread.sleep` и `System.currentTimeMillis`-polling допустимы **только** для ожидания событий от внешних нативных API (JmDNS, NsdManager и т.п.), которые работают на реальных потоках вне нашего `CoroutineScope`. Внутри тела теста всё остальное — виртуальное время.
- `withTimeout` внутри `runTest` использует **виртуальные** часы даже на `Dispatchers.IO` — не рассчитывай на него как на реальный таймаут.

## Apple targets

NSRunLoop нужно качать вручную — подробнее в [`docs/knowledge/apple-platform.md`](../knowledge/apple-platform.md).

## Запуск

```bash
./gradlew allTests -q                                    # все тесты (обязательно перед commit/push)
./gradlew :composeApp:desktopTest -q                     # только Desktop JVM
./gradlew :composeApp:commonTest -q                      # только common
./gradlew :composeApp:desktopTest --tests "com.tubetoast.tether.network.FileServerTest"
```
