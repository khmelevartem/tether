# Transport

HTTP-слой между paired-устройствами. Каждая нода — одновременно сервер и клиент; путь между двумя нодами симметричен. Discovery (mDNS) — отдельная подсистема.

Этот документ — *что есть*. Почему — в [`adr-network-stack.md`](adr/adr-network-stack.md), [`adr-apple-fileserver-engine.md`](adr/adr-apple-fileserver-engine.md), [`adr-channel-encryption.md`](adr/adr-channel-encryption.md).

## Принципы

- **Один HTTP-движок на всех таргетах, пока возможно.** Сегодня — Ktor CIO и на сервере, и на клиенте. Один код-path для маршрутов, парсинга и стриминга в `commonMain`. Платформенно расходятся только filesystem-sink и derived-from-platform параметры (где лежит downloads dir, как пишется файл на диск).
- **Streaming end-to-end, никакой in-memory буферизации.** Тело запроса — `ByteReadChannel` от точки чтения до точки записи. Известный размер публикуется как `Content-Length`, иначе chunked transfer encoding.
- **Application-layer watchdog вместо socket timeout.** Socket-таймауты выключены (`INFINITE`) — крупная передача может занимать минуты на медленной Wi-Fi, и любой конечный socket-таймаут отрубит легитимный аплоад. Зависшее соединение ловит coroutine, которая следит за `bytesSent`-счётчиком и роняет операцию, если прогресс не движется заданное время.
- **`start(): Int` возвращает реально забинденный порт.** Сервер слушает на эфемерном порту (`port = 0`), реальное значение читается из engine'а после старта и публикуется в mDNS. TOCTOU-гонок «забиндили — а порт уже занят» нет.
- **`send` не бросает.** Любая ошибка передачи заворачивается в `SendResult.Failure(message)`. UI получает либо `Success(savedPath)`, либо `Failure`, других исходов нет (за исключением `CancellationException`, которую тащит structured concurrency).
- **Routes конструируются с явными зависимостями.** Storage seam, peer-public-key store, собственный public key — приходят через параметры route-builder'а, не через service locator из `commonMain`.

## Маршруты

Минимальная поверхность, расширяется только при доказанной необходимости:

| Метод | Путь | Назначение |
|---|---|---|
| `GET` | `/health` | Liveness; ответ `200`. Сегодня дёргается smoke-тестом и mDNS-валидацией, не runtime'ом. |
| `POST` | `/pair` | Pairing handshake — обмен публичными ключами. Ошибки persist'а — явный 500, не молчаливое 200. |
| `POST` | `/upload?name=<file>` | Streaming upload. Имя нормализуется (стрипуются `..` / path separators). Любая ошибка до закрытия запроса — 500 + удаление частично записанного файла. |

## Source-set layout

```
commonMain
├── FileServer            (expect)
├── FileClient            (concrete сегодня — см. in-flight)
└── FileServerRoutes      (UploadStorage seam + installFileServerRoutes)

jvmMain    — actual FileServer на Ktor CIO; UploadStorage через java.io
appleMain  — actual FileServer на Ktor CIO Native; UploadStorage через POSIX + NSFileManager
```

Маршруты, парсинг HTTP, стриминг и watchdog живут в `commonMain`. Платформа отвечает только за: тип downloads dir, низкоуровневое чтение/запись файла, конструирование `FileServer` в `*AppContainer`.

## Cross-cutting landmines

- **CIO server закрывает body-channel «чисто» при premature client disconnect.** Без явной проверки наивная «прочитал до EOF — ответил 200» приводит к молчаливой записи неполного файла. Route-уровень парирует двумя проверками после копирования: `closedCause` + сверка прочитанных байтов с `Content-Length`. Деталь — в [`docs/knowledge/ktor-server-cio.md`](../knowledge/ktor-server-cio.md).
- **Apple short-write на POSIX `fwrite`.** Возвращаемое значение надо проверять — disk full / quota дают tail-truncation без exception. `fflush` перед закрытием обязателен, иначе deferred error не всплывёт.
- **`SO_KEEPALIVE` на accepted-сокетах CIO server недоступен.** Public API нет ([KTOR-5572](https://youtrack.jetbrains.com/issue/KTOR-5572)). Workaround — application-layer keepalive (см. ниже).
- **CIO server не поддерживает HTTPS.** При попытке поднять `sslConnector` бросает `UnsupportedOperationException` на любой платформе. Блокирует TLS-миграцию на текущем движке на JVM.
- **CIO Native (Apple) вообще не имеет TLS** — ни на сервере, ни на клиенте ([KTOR-7262](https://youtrack.jetbrains.com/issue/KTOR-7262)). Соответствующий код-path в `ktor-network-tls` на Native — `error()`-стуб.

## In-flight

| Issue | Что меняется |
|---|---|
| [#25](https://github.com/khmelevartem/tether/issues/25) | Bump Ktor minor. Закрывает malformed `100 Continue` на стороне CIO server. |
| [#91](https://github.com/khmelevartem/tether/issues/91) | Передача на Android-эмулятор зависает — диагностика NAT/mDNS эмулятора, не движка. |
| [#140](https://github.com/khmelevartem/tether/issues/140) | TLS с paired-key pinning. **Forced engine swaps** (см. [`adr-channel-encryption.md`](adr/adr-channel-encryption.md) Amendment): JVM server CIO → Netty (CIO бросает на HTTPS), Apple server CIO Native → SecureTransport + `ktor-http-cio` parser. Apple client уходит с CIO (KTOR-7262) — Darwin engine либо SecureTransport-wrapped, выбирается pre-flight спайком. JVM client остаётся. Prerequisite: refactor `FileClient` в `expect class`. |
| [#164](https://github.com/khmelevartem/tether/issues/164) | Application-layer keepalive вместо socket-level (после подтверждения KTOR-5572). |

После #140 «один движок на всех таргетах» перестаёт быть свойством. На Apple останется только `ktor-http-cio` как parser-only-библиотека поверх SecureTransport-сокета.
