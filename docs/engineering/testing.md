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
- Правило `tether:no-run-blocking-in-tests` (`:ktlint-rules`) автоматически запрещает `runBlocking` в test source set'ах. Для легитимных integration-тестов на реальных потоках добавляй `@Suppress("ktlint:tether:no-run-blocking-in-tests")` — на класс, когда класс целиком — integration-suite вокруг одного внешнего API (real CIO server, JmDNS), даже если отдельные тесты этого класса не используют `runBlocking`; на функцию, когда integration-метод стоит в неинтеграционном по природе классе. Рядом с suppress'ом — `//`-комментарий, какое именно real-thread событие ждём.

## Реальное время vs виртуальное

- `Thread.sleep` и `System.currentTimeMillis`-polling допустимы **только** для ожидания событий от внешних нативных API (JmDNS, NsdManager и т.п.), которые работают на реальных потоках вне нашего `CoroutineScope`. Внутри тела теста всё остальное — виртуальное время.
- `withTimeout` внутри `runTest` использует **виртуальные** часы даже на `Dispatchers.IO` — не рассчитывай на него как на реальный таймаут.

## Apple targets

NSRunLoop нужно качать вручную — подробнее в [`docs/knowledge/apple-platform.md`](../knowledge/apple-platform.md).

## Test seams для `expect` классов

Если actual-имплементация в принципе не может failить в тесте без мока платформенного API (`NSUserDefaults.synchronize()` всегда true в Robolectric/sim, DataStore не валится по запросу) — объявляй `expect open class` с `open fun` для методов, которые нужно подменять. Тест объявляет анонимный `object : TrustedDeviceStore(...)` с `override fun saveTrustedKey(...) = throw ...` и подкладывает в production-код через тот же DI-вход, что и реальный store. Это сохраняет DI-граф (тот же тип течёт в `FileServer`) и не плодит интерфейс-обёртку ради одной точки подмены.

Не делай это превентивно — только когда контракт «при ошибке actual должен бросить» нужно проверить end-to-end (HTTP-уровень в нашем случае), а триггер ошибки на платформе недостижим. Пример — `TrustedDeviceStore` в #9: HTTP `/pair → 500` тестируется на каждом actual через throwing-subclass.

## HTTP-клиент в unit-тестах

Класс, держащий `HttpClient`, принимает его через конструктор; production-конфиг строится в `companion object { fun default() }`. Тест передаёт `HttpClient(MockEngine)` с handler'ом, отвечающим на запросы.

Чтобы `delay()` в handler'е и внутренние таймеры клиента подчинялись `TestCoroutineScheduler` под `runTest`, пинь dispatcher на engine:

```kotlin
private fun TestScope.httpFor(handler: ...): HttpClient =
    HttpClient(MockEngine) {
        engine {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler(handler)
        }
    }
```

Без `dispatcher = ...` handler'ы поднимают свой engine dispatcher (real-time) — virtual time `runTest`'а игнорируется, тест становится либо медленным, либо flaky.

Real-CIO server (`embeddedServer(CIO)`) под virtual time **не приводится**: `CIOApplicationEngine` хардкодит `userDispatcher = Dispatchers.IOBridge` и оборачивает route handler'ы в `withContext(userDispatcher)`. Если тест требует именно реального CIO — это integration-уровень, держи его в `FileServerTest` с `runBlocking` и реальным временем.

## Удаление тестов

Удалять тесты нежелательно — они защищают инварианты, часть из которых не очевидна по имени теста. До удаления:

1. Перечисли все инварианты, которые тест проверял (не только те, что в имени).
2. Для каждого укажи, чем он защищён после удаления: другим тестом, контрактом типа, property кода.
3. Если хотя бы один инвариант остаётся без защиты — либо не удаляй тест, либо в том же коммите добавь защиту (тест / тип / проверку).

Эта инвентаризация — обязательная часть commit message / PR description при удалении теста.

Альтернативы удалению: `@Ignore` со ссылкой на tracking issue (тест поломан временно), упрощение теста (слишком тяжёлый), вынос в отдельный source set (платформ-специфичен).

## Screenshot tests

Roborazzi renders every `@Preview` composable to a PNG via Robolectric — no emulator required. ComposablePreviewScanner discovers all `@Preview` functions in `com.tubetoast.tether` via bytecode reflection; one generic parameterised test in `composeApp/src/androidUnitTest/` covers all of them without per-preview boilerplate.

**Record PNGs** (initial capture or after intentional visual change):

```bash
./gradlew :composeApp:recordRoborazziDebug -q
```

PNGs land in `composeApp/build/outputs/roborazzi/`. Filenames encode the composable's FQN and the `@Preview` `name` parameter. `review-visual` reads these PNGs and compares them against the UX brief; baseline-diffing in CI is out of scope.

`captureRoboImage` is a no-op outside the `record*` / `verify*` / `compare*` Roborazzi tasks (which set `-Proborazzi.test.record=true` etc.), so `./gradlew allTests` and pre-commit hooks do not pay the Robolectric cold-start cost for screenshot rendering.

**Rules for new `@Preview`s:**
- Always target the stateless `XxxContent(state, callbacks)` variant of a composable, never the `XxxScreen(component)` wrapper. The wrapper depends on Decompose and cannot render under Robolectric.
- Wrap every preview in `PreviewSurface { }` from `com.tubetoast.tether.ui.preview` for consistent theme + background.
- Use fake state from `PreviewFixtures` in the same package — no inline data fabrication.

## Запуск

```bash
./gradlew allTests -q                                    # все тесты; pre-commit / pre-push хуки прогонят их сами
./gradlew :composeApp:desktopTest -q                     # только Desktop JVM
./gradlew :composeApp:commonTest -q                      # только common
./gradlew :composeApp:desktopTest --tests "com.tubetoast.tether.network.FileServerTest"
```
