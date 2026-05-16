# Transport

HTTP-транспорт между paired-устройствами: сервер на каждой ноде + клиент на каждой ноде, HTTP/1.1, streaming. Discovery (mDNS) — отдельная подсистема (`MdnsDiscovery` в `commonMain` + `actual` per platform), здесь не рассматривается.

Этот документ описывает *что есть сейчас*. Почему именно так — в трёх ADR: [`adr-network-stack.md`](adr/adr-network-stack.md), [`adr-apple-fileserver-engine.md`](adr/adr-apple-fileserver-engine.md), [`adr-channel-encryption.md`](adr/adr-channel-encryption.md) (с Amendment от 2026-05-16). ADR — снапшоты решений; текущее состояние здесь.

## Текущее состояние

### Версии и движки

| Что | Где | Артефакт | Версия |
|---|---|---|---|
| HTTP server engine | `commonMain` (общий для всех таргетов) | `io.ktor:ktor-server-cio` | Ktor 3.1.3 ([libs.versions.toml:24](../../gradle/libs.versions.toml#L24)) |
| HTTP client engine | `commonMain` (общий) | `io.ktor:ktor-client-cio` | Ktor 3.1.3 |
| HTTP parsing & routing | `commonMain` | `ktor-server-core` + `ktor-server-content-negotiation` | Ktor 3.1.3 |
| JSON | `commonMain` | `ktor-serialization-kotlinx-json` | — |

Server stack сидит в `commonMain` (см. [composeApp/build.gradle.kts:109-115](../../composeApp/build.gradle.kts#L109-L115)) потому что Ktor 3.0+ публикует `ktor-server-cio` для Native (`iosArm64`, `iosSimulatorArm64`, `macosArm64`) — единственная причина, по которой "сервер общий для JVM и Apple" вообще возможно.

### Route surface

Маршруты живут в [`FileServerRoutes.kt`](../../composeApp/src/commonMain/kotlin/com/tubetoast/tether/network/FileServerRoutes.kt) (`commonMain`) и устанавливаются обоими `actual`-имплементациями `FileServer` через `installFileServerRoutes(...)`:

| Метод | Путь | Назначение | Response |
|---|---|---|---|
| `GET` | `/health` | Liveness probe — мdns-кандидат отдаёт строку `"Tether OK"` со статусом 200. | `text/plain` |
| `POST` | `/pair` | Pairing handshake: peer присылает `PairRequest{publicKey}`, сервер сохраняет в `TrustedDeviceStore` и отвечает `PairResponse{publicKey=serverPublicKey}`. Ошибка persistence — явный 500, не молчаливое 200 ([FileServerRoutes.kt:46-61](../../composeApp/src/commonMain/kotlin/com/tubetoast/tether/network/FileServerRoutes.kt#L46-L61)). | `application/json` |
| `POST` | `/upload?name=<file>` | Streaming upload. Тело идёт сразу в `UploadStorage.writeBody`; имя нормализуется (`stripPathComponents`). На успех — `{savedPath}`; на любую ошибку до закрытия запроса — 500 + удаление частично записанного файла. | `application/json` |

Других маршрутов нет. `FileClient.ping(...)` объявлен, но не реализован ([FileClient.kt:47-50](../../composeApp/src/commonMain/kotlin/com/tubetoast/tether/network/FileClient.kt#L47-L50)) — `/health` сегодня дёргает только smoke-тест и mDNS-валидация, не runtime.

### Streaming model

End-to-end через `ByteReadChannel`, без буферизации в память:

- **Клиент.** [`FileClient.send(...)`](../../composeApp/src/commonMain/kotlin/com/tubetoast/tether/network/FileClient.kt#L52) принимает `ByteReadChannel` напрямую и оборачивает в `OutgoingContent.ReadChannelContent` ([`asOctetStreamContent`](../../composeApp/src/commonMain/kotlin/com/tubetoast/tether/network/FileClient.kt#L138)). Если `totalBytes` известен — он публикуется как `contentLength`, иначе тело уходит chunked TE.
- **Сервер.** Route вызывает `call.receiveChannel()` и передаёт канал `UploadStorage.writeBody(...)` — там пишется напрямую в файл.
- **Таймауты.** `HttpClient(CIO)` ставит `HttpTimeout` с `requestTimeoutMillis = INFINITE_TIMEOUT_MS` и `socketTimeoutMillis = INFINITE_TIMEOUT_MS` ([FileClient.kt:39-42](../../composeApp/src/commonMain/kotlin/com/tubetoast/tether/network/FileClient.kt#L39-L42)). Дефолтный 15s Ktor-таймаут отрезал бы любую крупную передачу — рациональ в [#113](https://github.com/khmelevartem/tether/issues/113) ([PR #160](https://github.com/khmelevartem/tether/pull/160)).
- **Watchdog.** Application-layer заменяет socket timeout: `FileClient` крутит coroutine, которая каждую секунду сравнивает `bytesSent` со снапшотом и роняет операцию, если за `noProgressTimeout` (дефолт 60s) прогресс не сдвинулся ([FileClient.kt:79-96](../../composeApp/src/commonMain/kotlin/com/tubetoast/tether/network/FileClient.kt#L79-L96)).

### Truncation detection на приёме

Ktor 3.1 CIO закрывает body-channel **чисто** при premature client disconnect (без `closedCause`), из-за чего наивная "прочитал до EOF, отвечаю 200" приводит к молчаливой записи неполного файла. Route защищается двумя проверками после копирования: `body.closedCause?.let { throw it }` плюс сравнение записанных байтов с `Content-Length` (если он был) — см. [FileServerRoutes.kt:80-84](../../composeApp/src/commonMain/kotlin/com/tubetoast/tether/network/FileServerRoutes.kt#L80-L84). Подробно — в [`docs/knowledge/ktor-server-cio.md`](../knowledge/ktor-server-cio.md).

### Per-platform write strategy

`UploadStorage` — внутренний seam в `commonMain` ([FileServerRoutes.kt:24-36](../../composeApp/src/commonMain/kotlin/com/tubetoast/tether/network/FileServerRoutes.kt#L24-L36)), реализуемый рядом с каждым `FileServer.actual`. Route не знает про FS:

| Платформа | Storage impl | API записи | Default downloads dir |
|---|---|---|---|
| JVM (Desktop + Android) | `JvmUploadStorage` в [FileServer.kt:41](../../composeApp/src/jvmMain/kotlin/com/tubetoast/tether/network/FileServer.kt#L41) | `body.toInputStream().copyTo(File.outputStream(), 64 KiB)` | `~/Downloads/Tether` |
| Apple (iOS + macOS) | `AppleUploadStorage` в [FileServer.apple.kt:52](../../composeApp/src/appleMain/kotlin/com/tubetoast/tether/network/FileServer.apple.kt#L52) | POSIX `fopen`/`fwrite` чанками по 64 KiB; обязательная проверка короткой записи + `fflush` | `<NSDocumentDirectory>/Tether` |

Apple ходит через POSIX, а не через `NSFileHandle`/`kotlinx-io`, потому что binding `NSFileHandle.fileHandleForWritingAtPath` не резолвится в K/N 2.3, а `kotlinx-io` ради одного `copyTo` тащить дорого — рациональ в [`adr-apple-fileserver-engine.md`](adr/adr-apple-fileserver-engine.md#costs-accepted).

### Port allocation

`FileServer.start(port: Int)` — `port=0` означает "OS-assigned ephemeral". После `embeddedServer(...).start(wait=false)` JVM- и Apple-actual оба читают `srv.engine.resolvedConnectors().first().port` и возвращают его наружу ([FileServer.kt:32](../../composeApp/src/jvmMain/kotlin/com/tubetoast/tether/network/FileServer.kt#L32), [FileServer.apple.kt:43](../../composeApp/src/appleMain/kotlin/com/tubetoast/tether/network/FileServer.apple.kt#L43)). Это убирает TOCTOU-гонку, которую бы вызвало "сначала `ServerSocket(0)` для определения порта, потом передать его серверу". Контракт — `start(): Int` возвращает реально забинденный порт; `MdnsDiscovery.advertise(...)` использует именно это значение.

## Source-set layout

```
commonMain
├── network/FileClient.kt              — concrete class (НЕ expect/actual сегодня)
├── network/FileServer.kt              — expect class
├── network/FileServerRoutes.kt        — UploadStorage seam + installFileServerRoutes
└── (ktor-server-* + ktor-client-* в commonMain implementation deps)

jvmMain  (родитель androidMain + desktopMain)
├── network/FileServer.kt              — actual: Ktor CIO + JvmUploadStorage (java.io)
└── network/FileClientJvm.kt           — JVM-only extension: send(Path) overload

appleMain  (родитель iosMain + macosMain)
└── network/FileServer.apple.kt        — actual: Ktor CIO Native + AppleUploadStorage (POSIX + NSFileManager)
```

Маршруты, парсинг HTTP, streaming-логика и watchdog — в `commonMain`. Платформенно расходятся только: (a) FS-сторона `UploadStorage`, (b) тип `downloadsDir` (`File` на JVM, `String`-путь на Apple), (c) то, кто конструирует `FileServer` — соответствующий `*AppContainer`.

## Контракты, на которые опираются другие слои

- **`FileServer.start(): Int` возвращает actually-bound порт.** Этот же `Int` отдаётся в `MdnsDiscovery.advertise(...)`. Если `start()` бросает — порт не публикуется. Повторный вызов на уже запущенном инстансе бросает `IllegalStateException` (`check(server == null)`).
- **`FileServer.stop()` идемпотентен.** Дважды дёрнуть — не падает; `null`-server игнорируется. Останавливается с `gracePeriodMillis=500`, `timeoutMillis=1000`.
- **`FileClient.send(...)` никогда не бросает не-`CancellationException`.** Любая `Throwable` ловится и заворачивается в `SendResult.Failure(message)` ([FileClient.kt:58-64](../../composeApp/src/commonMain/kotlin/com/tubetoast/tether/network/FileClient.kt#L58-L64)). UI получает либо `Success(savedPath)`, либо `Failure(msg)`, других выходов нет.
- **`FileClient` — `Closeable`.** Закрытие закрывает underlying `HttpClient`. Owner — `AppContainer` (см. [dependency-injection.md](dependency-injection.md)).
- **Route-уровень знает `TrustedDeviceStore` + `deviceKeyPair.publicKey`.** Они пробрасываются через `installFileServerRoutes(storage, trustedDeviceStore, serverPublicKey)`. Сам `commonMain` не лезет ни в Keychain, ни в DataStore — это делают `actual`-имплементации этих seam'ов.

## Cross-cutting landmines

- **CIO body-channel quirks, short-write detection, `Content-Length` обязателен при known body size** — [`docs/knowledge/ktor-server-cio.md`](../knowledge/ktor-server-cio.md). Читать перед тем как менять `FileServerRoutes.kt` или `AppleUploadStorage`.
- **`SO_KEEPALIVE` на accepted-сокетах CIO server недоступен.** [KTOR-5572](https://youtrack.jetbrains.com/issue/KTOR-5572) — нет публичного API. Workaround — application-layer keepalive, см. "In-flight" ниже.
- **CIO server на JVM **не поддерживает HTTPS**.** `CIOApplicationEngine.kt:197` бросает `UnsupportedOperationException` при наличии `sslConnector`. Это блокирует TLS-миграцию на текущем JVM-движке — см. [`adr-channel-encryption.md` Amendment](adr/adr-channel-encryption.md#amendment--2026-05-16-pre-implementation).
- **CIO Native (Apple) **не поддерживает TLS** вообще.** `ktor-network-tls/nonJvmMain` — стуб, кидает `"TLS sessions are not supported on Native platform."` Применяется и к серверу, и к клиенту. [KTOR-7262](https://youtrack.jetbrains.com/issue/KTOR-7262).
- **CIO Native iOS-сервер не значится в официальной [server-native.html](https://ktor.io/docs/server-native.html)** (там только macOS / Linux / Windows). Артефакт публикуется и наш `appleTest/FileServerTest.kt` проходит на `iosSimulatorArm64` — но это unofficial-tested-only.

## In-flight изменения

| Issue | Что меняется | Статус кода |
|---|---|---|
| [#25](https://github.com/khmelevartem/tether/issues/25) | Bump Ktor 3.1.3 → 3.2.x. Закрывает malformed `100 Continue` от CIO server на `Expect: 100-continue` (фикс в [Ktor 3.2 changelog](https://ktor.io/changelog/3.2/)). | open; в коде ещё 3.1.3 ([libs.versions.toml:24](../../gradle/libs.versions.toml#L24)). |
| [#91](https://github.com/khmelevartem/tether/issues/91) | Передача на Android-эмулятор зависает по таймауту. Подозрение на NAT/mDNS эмулятора, не на engine — открыт как диагностика. | open; engine-choice не меняет. |
| [#140](https://github.com/khmelevartem/tether/issues/140) | TLS с paired-key pinning. **Принудительные engine-свопы под TLS** (см. Amendment в [`adr-channel-encryption.md`](adr/adr-channel-encryption.md#amendment--2026-05-16-pre-implementation)): JVM server CIO → **Netty** (CIO бросает на HTTPS), Apple server CIO Native → **SecureTransport + `ktor-http-cio` parser** (CIO Native TLS отсутствует). Apple `FileClient` — либо **Darwin engine** (через `NSURLSession.handleChallenge` для SPKI-пиннинга), либо SecureTransport-wrapped raw TCP; pre-flight spike в #140 выбирает. JVM client остаётся на CIO (Ktor `ktor-network-tls/jvmMain` работает поверх `SSLEngine`). **Prerequisite:** [`FileClient`](../../composeApp/src/commonMain/kotlin/com/tubetoast/tether/network/FileClient.kt#L31) сегодня — concrete class в `commonMain`, под Apple-actual нужен предварительный refactor в `expect class`. | open; кода нет. |
| [#164](https://github.com/khmelevartem/tether/issues/164) | Application-layer keepalive вместо socket-level (изначальная reflection-попытка отменена — `KTOR-5572` подтверждает отсутствие публичного API). Пинг во время активных передач. | open; в коде сегодня **нет** keepalive-механизма (verified — grep по `keepalive`/`SO_KEEPALIVE` в `network/` пуст). |
| [#166](https://github.com/khmelevartem/tether/issues/166) | ADR на сетевой стек (этот же документ — его living-doc сторона). | closed (PR #167 merged). |
| [#113](https://github.com/khmelevartem/tether/issues/113) | Дефолтный 15-секундный Ktor-таймаут на `FileClient`. | **closed** ([PR #160](https://github.com/khmelevartem/tether/pull/160)). `HttpTimeout` с infinite request timeout + app-layer watchdog — уже в коде. |

После #140 на Apple больше не будет Ktor `embeddedServer` — останется только `ktor-http-cio` как библиотека парсера, поверх SecureTransport-сокета. "Single engine на пяти таргетах" — свойство только plain-HTTP эпохи.

## Почему именно эти движки

- **CIO везде сегодня — minimum-risk choice, не optimal.** Один код-path на всех таргетах, работающий тест-сьют, отсутствие текущих багов под выбранную нагрузку. Свопы под TLS forced upstream-ограничениями, не предпочтением. Полный разбор alternatives (Netty/Jetty per-engine split, hand-rolled HTTP, raw TCP) — в [`adr-network-stack.md`](adr/adr-network-stack.md).
- **Apple FileServer на Ktor CIO Native, а не hand-rolled NSStream.** Парсер HTTP/1.1 бесплатно, симметрия с JVM-actual, прямой путь к TLS через `ktor-http-cio` (когда #140 заберёт server engine, парсер останется). Рациональ — [`adr-apple-fileserver-engine.md`](adr/adr-apple-fileserver-engine.md).
- **Pinned-TLS поверх HTTP (а не Noise / plain HTTP).** Outline в [`adr-channel-encryption.md`](adr/adr-channel-encryption.md): SPKI-pinning, OS trust store не консультируется, ECDHE-ECDSA-AES-GCM, TLS 1.2+. Amendment от 2026-05-16 фиксирует JVM-Netty/Apple-SecureTransport/Apple-client open-question как обязательные шаги для #140.

## References

- [`adr-network-stack.md`](adr/adr-network-stack.md) — выбор движка, alternatives, "revisit if".
- [`adr-apple-fileserver-engine.md`](adr/adr-apple-fileserver-engine.md) — почему CIO Native на Apple, costs accepted.
- [`adr-channel-encryption.md`](adr/adr-channel-encryption.md) — TLS-decision + Amendment 2026-05-16.
- [`docs/knowledge/ktor-server-cio.md`](../knowledge/ktor-server-cio.md) — CIO gotchas (silent body close, short-write на POSIX).
- [`docs/engineering/modules.md`](modules.md) — source-set hierarchy, target-state module split.
- [`docs/engineering/dependency-injection.md`](dependency-injection.md) — кто конструирует `FileServer` / `FileClient` (composition root в платформенном `*AppContainer`).
- [`docs/product/tech-stack.md`](../product/tech-stack.md) — продуктовая сторона того же выбора.
