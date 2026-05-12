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

## Test seams для `expect` классов

Если actual-имплементация в принципе не может failить в тесте без мока платформенного API (`NSUserDefaults.synchronize()` всегда true в Robolectric/sim, DataStore не валится по запросу) — объявляй `expect open class` с `open fun` для методов, которые нужно подменять. Тест объявляет анонимный `object : TrustedDeviceStore(...)` с `override fun saveTrustedKey(...) = throw ...` и подкладывает в production-код через тот же DI-вход, что и реальный store. Это сохраняет DI-граф (тот же тип течёт в `FileServer`) и не плодит интерфейс-обёртку ради одной точки подмены.

Не делай это превентивно — только когда контракт «при ошибке actual должен бросить» нужно проверить end-to-end (HTTP-уровень в нашем случае), а триггер ошибки на платформе недостижим. Пример — `TrustedDeviceStore` в #9: HTTP `/pair → 500` тестируется на каждом actual через throwing-subclass.

## Запуск

```bash
./gradlew allTests -q                                    # все тесты; pre-commit / pre-push хуки прогонят их сами
./gradlew :composeApp:desktopTest -q                     # только Desktop JVM
./gradlew :composeApp:commonTest -q                      # только common
./gradlew :composeApp:desktopTest --tests "com.tubetoast.tether.network.FileServerTest"
```
