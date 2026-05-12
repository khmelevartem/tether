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

## Удаление тестов

Удалять тесты нежелательно — они защищают инварианты, часть из которых не очевидна по имени теста. До удаления:

1. Перечисли все инварианты, которые тест проверял (не только те, что в имени).
2. Для каждого укажи, чем он защищён после удаления: другим тестом, контрактом типа, property кода.
3. Если хотя бы один инвариант остаётся без защиты — либо не удаляй тест, либо в том же коммите добавь защиту (тест / тип / проверку).

Эта инвентаризация — обязательная часть commit message / PR description при удалении теста.

Альтернативы удалению: `@Ignore` со ссылкой на tracking issue (тест поломан временно), упрощение теста (слишком тяжёлый), вынос в отдельный source set (платформ-специфичен).

## Запуск

```bash
./gradlew allTests -q                                    # все тесты; pre-commit / pre-push хуки прогонят их сами
./gradlew :composeApp:desktopTest -q                     # только Desktop JVM
./gradlew :composeApp:commonTest -q                      # только common
./gradlew :composeApp:desktopTest --tests "com.tubetoast.tether.network.FileServerTest"
```
