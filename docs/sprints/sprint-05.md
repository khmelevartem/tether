## Цель спринта

Первый юзабельный sender-флоу: Android-пользователь открывает Tether, тапает по устройству, выбирает файл (или папку, или N файлов) и отправляет — Desktop принимает в `~/Downloads/Tether/`. Параллельно — первая реальная имплементация пункта MVP-roadmap по device name (settable name). Паринг намеренно остаётся за бортом — приложение работает на trust-on-first-use, как сейчас в коде.

К концу спринта:

- non-technical пользователь на Android отправляет файлы любого размера (single / multi / folder, in-app picker и system share sheet) на Desktop-инстанс в той же Wi-Fi сети; видит byte-based прогресс с именем файла и скоростью; cancel из любой стороны корректный, без partial-файлов;
- передача файла > 15 секунд (т.е. реалистичный размер на Wi-Fi) больше не падает по CIO default request timeout;
- активная передача на Android при заблокированном экране не рвётся из-за Wi-Fi power-save / Doze;
- устройство анонсирует через mDNS осмысленное имя per platform по умолчанию; имя можно переименовать через CLI и оно переживает рестарт;
- спека Wi-Fi availability переведена в `scoped` — закрывает trio system-specs (`permissions`, `wifi-availability`) и готовит implementation issue на empty-state в device list.

## Состав

| #   | Issue                                                     | Название                                                                                | Тип     | Размер |
| --- | --------------------------------------------------------- | --------------------------------------------------------------------------------------- | ------- | ------ |
| 1   | [#8](https://github.com/khmelevartem/tether/issues/8)     | Android UI — выбор файла, передача и прогресс (single + multi + folder, in-app + share) | feature | L      |
| 2   | [#113](https://github.com/khmelevartem/tether/issues/113) | FileClient: загрузка > 15 c падает с Request timeout (CIO default)                      | bug     | S      |
| 3   | [#150](https://github.com/khmelevartem/tether/issues/150) | Android FGS: WifiLock + partial WakeLock на время активной передачи                     | feature | S      |
| 4   | [#147](https://github.com/khmelevartem/tether/issues/147) | Device name backend: `DeviceNameStore`, default-имя, mDNS republish, CLI rename         | feature | L      |
| 5   | [#121](https://github.com/khmelevartem/tether/issues/121) | Спека Wi-Fi availability detection: довести до `scoped`                                 | docs    | S      |

### Внешний контекст

- **[#157](https://github.com/khmelevartem/tether/issues/157)** (CI один Gradle invoke, сборка всех таргетов) — уже смержен в main ([6196073](https://github.com/khmelevartem/tether/commit/6196073), PR #158) до старта спринта. Inфра-слот по сборке закрыт; новые задачи спринта получают защиту от регрессий по всем таргетам с первого PR.

## Параллелизм по слоям

| Слой                                                                                                            | Задачи |
| --------------------------------------------------------------------------------------------------------------- | ------ |
| `androidMain` UI (`MainActivity`, `AndroidManifest`) + `commonMain/presentation` (новый transfer-экран Decompose) | #8     |
| `commonMain/network/FileClient.kt` (HTTP timeout config)                                                        | #113   |
| `androidMain/network/TetherForegroundService.kt` + `AndroidManifest` (`WAKE_LOCK`)                              | #150   |
| `commonMain` (`DeviceNameStore`, `DeviceNameRepublisher`, `MdnsDiscovery.republish`) + 4 platform actuals + `desktopCli/Main.kt` + `DesktopBackend.kt` | #147   |
| `docs/product/features/system/wifi-availability/spec.md`                                                        | #121   |

Пять треков с допустимыми точками трения:

- **#8 ↔ #150** — оба правят `AndroidManifest.xml`. #8 добавляет `<intent-filter>` для share-sheet, #150 — `<uses-permission android:name="WAKE_LOCK"/>`. Разные элементы, merge кооперативный.
- **#8 ↔ #113** — оба касаются `FileClient`. Но #113 — 5 строк в конструкторе клиента, #8 не меняет сигнатуру `send()` (callback уже есть). Конфликт минимальный.
- **#147 ↔ #8** — оба касаются DI-графа (`AppContainer`). #147 добавляет `nameStore`/`republisher`, #8 — поля под send-флоу. Разные поля, merge кооперативный.

Порядок мерджа для минимизации ребейзов: #121 → #113 → #150 → #147 → #8.

## Цепочки блокировок наружу

- **#8 →** Android-сторона sender'а готова; следующие шаги — receive-side UI на Android и Desktop send UI (отдельные, незаведённые пока issue).
- **#113 →** разблокирует валидное smoke-тестирование на файлах реалистичного размера. Снимает один из трёх child #119; #91 (Android-эмулятор) и #25 (`Expect: 100-continue`) остаются открытыми.
- **#147 →** разблокирует [#148](https://github.com/khmelevartem/tether/issues/148) (Device name UI), которое формально blocked-by #147. #148 — кандидат в спринт 6.
- **#150 →** терминальная reliability-правка, наружу никого не разблокирует.
- **#121 →** разблокирует implementation issue «`WifiAvailabilityMonitor` + empty-state в device list» (заводится после мерджа спеки).

## Связанные продуктовые спеки

| Issue | Спека                                                                                                            |
| ----- | ---------------------------------------------------------------------------------------------------------------- |
| #8    | реализует [file-transfer/spec.md](../product/features/file-transfer/spec.md) (`scoped`, доп. результат sprint-04) для Android sender |
| #113  | соответствует AC «file of any size, streaming, no OOM» из [file-transfer/spec.md](../product/features/file-transfer/spec.md) |
| #150  | соответствует AC reliability из [file-transfer/spec.md](../product/features/file-transfer/spec.md)               |
| #147  | реализует [device-name-bootstrapping/spec.md](../product/features/device-name-bootstrapping/spec.md) (`scoped`)  |
| #121  | сама правит [system/wifi-availability/spec.md](../product/features/system/wifi-availability/spec.md) (`idea` → `scoped`) |

## Не вошло намеренно

- **#10 (паринг CLI handshake), #11 (Pairing PIN UI на всех платформах)** — паринг продуктово отложен: цель спринта — «минимально работающее приложение без паринга», runtime сейчас не enforce'ит trust в `/upload`. Спринт 6.
- **#148 (Device name UI)** — blocked-by #147 в этом же спринте. По правилу `grooming.md`: «если #A блокирует #B, нельзя брать обе в один спринт». Спринт 6.
- **#74 (KydraLog мультиплатформенный логгер)** — описание только что отрефрешено; конфликтует с #8/#113/#147 по `FileClient.kt` / `FileServer*.kt` / `MdnsDiscovery*`. Спринт 6.
- **#91 (Android-эмулятор: передача зависает), #25 (Expect: 100-continue), #119 (transport hardening umbrella)** — родственники #113 по transport. Импакт ниже, без MVP-блокировки. Спринт 6.
- **#140 (TLS-pinned транспорт по ADR #123), #116 (Apple EC P-256 ключи)** — L+ имплементации после #123 ADR. Перепишут тот же FileClient слой, который сейчас правит #113. Спринт 6+ как отдельная подцепочка.
- **#59 (Android FGS dataSync 6h cap ресёрч), #58 (Android service start/stop UI control)** — обе трогают `TetherForegroundService.kt` / Android service lifecycle, конфликтуют с #150 / #8. Спринт 6.
- **#127 (Roborazzi preview screenshot)** — tooling, ускорил бы агентный цикл по #8 UI, но не блокирует. Спринт 7+ если приоритет поднимется.
- **#100 (UI strings localization)** — L, без MVP-блокировки. #8 явно допускает inline-строки с TODO как временный pattern до закрытия #100. Спринт 7+.
- **#41 (macOS native entry point), #36 (terminology check skill)** — пост-MVP / низкий приоритет.

## Полезный инкремент

После спринта:

1. **Первый юзабельный sender-флоу** (#8) — Android-устройство, реальный пользовательский сценарий: тап → выбор файла/папки/N файлов → byte-based прогресс → файл(ы) в `Downloads/Tether/` на Desktop. Cross-platform паритет ещё не достигнут (Desktop send UI / iOS send UI / receive-side UI остаются как отдельные следующие задачи), но впервые есть демонстрируемая ценность вне CLI.
2. **Передача файлов реалистичного размера работает** (#113) — снимается 15-секундный потолок CIO default, фиксируется engine-agnostic паттерн (`install(HttpTimeout)`) ради forward-compat с TLS-rewrite (#140).
3. **Длинные передачи на Android при заблокированном экране не рвутся** (#150) — Wi-Fi/wake lock на время активной передачи, refcount на параллельные передачи. Tether из «работает пока экран горит» переходит в «работает как фоновый сервис на всё время передачи».
4. **Device name MVP реализован** (#147) — первый исполненный пункт MVP-roadmap за пределами discovery/transfer plumbing. mDNS анонсирует осмысленное имя per platform по умолчанию, имя меняется через CLI и переживает рестарт. Готовит #148 (UI) на следующий спринт.
5. **System-specs trio закрыто** (#121) — после `permissions` и `wifi-availability` (`scoped`) и уже-готового `device-name-bootstrapping` (`scoped`) трио системных спек завершено, имплементационные issue для пустых состояний и Wi-Fi UI заводятся без продуктовых пробелов.
