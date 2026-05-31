# Sprint 05 · Первый Караван

## Цель спринта

Первый юзабельный sender-флоу: Android-пользователь открывает Tether, тапает по устройству, выбирает файл (или папку, или N файлов) и отправляет — Desktop принимает в `~/Downloads/Tether/`. Параллельно — первая реальная имплементация пункта MVP-roadmap по device name (settable name). Паринг намеренно остаётся за бортом — приложение работает на trust-on-first-use, как сейчас в коде.

К концу спринта:

- non-technical пользователь на Android отправляет файлы любого размера (single / multi / folder, in-app picker и system share sheet) на Desktop-инстанс в той же Wi-Fi сети; видит byte-based прогресс с именем файла и скоростью; cancel из любой стороны корректный, без partial-файлов;
- передача файла > 15 секунд (т.е. реалистичный размер на Wi-Fi) больше не падает по CIO default request timeout;
- активная передача на Android при заблокированном экране не рвётся из-за Wi-Fi power-save / Doze;
- устройство анонсирует через mDNS осмысленное имя per platform по умолчанию; имя можно переименовать через CLI и оно переживает рестарт;
- спека Wi-Fi availability переведена в `scoped` — закрывает trio system-specs (`permissions`, `wifi-availability`) и готовит implementation issue на empty-state в device list.

## Состав

| #   | Issue                                                     | Название                                                                                | Тип     | Размер | Итог |
| --- | --------------------------------------------------------- | --------------------------------------------------------------------------------------- | ------- | ------ | ---- |
| 1   | [#8](https://github.com/khmelevartem/tether/issues/8)     | Android UI — выбор файла, передача и прогресс (single + multi + folder, in-app + share) | feature | L      | 🔁 переплан: эпик декомпозирован на под-задачи #186–#195 (UX brief + theme + nav + state + screens + 4 platform-wiring). В sprint-05 закрыт только preparatory #186 (UX brief, [ccb0e25](https://github.com/khmelevartem/tether/commit/ccb0e25), PR #196). Остальное переезжает в sprint-06+ по графу зависимостей. |
| 2   | [#113](https://github.com/khmelevartem/tether/issues/113) | FileClient: загрузка > 15 c падает с Request timeout (CIO default)                      | bug     | S      | ✅ closed ([425604d](https://github.com/khmelevartem/tether/commit/425604d), PR #160) |
| 3   | [#150](https://github.com/khmelevartem/tether/issues/150) | Android FGS: WifiLock + partial WakeLock на время активной передачи                     | feature | S      | ✅ closed ([572bdb4](https://github.com/khmelevartem/tether/commit/572bdb4), PR #165) |
| 4   | [#147](https://github.com/khmelevartem/tether/issues/147) | Device name backend: `DeviceNameStore`, default-имя, mDNS republish, CLI rename         | feature | L      | ✅ closed 2026-05-22 21:29 UTC |
| 5   | [#121](https://github.com/khmelevartem/tether/issues/121) | Спека Wi-Fi availability detection: довести до `scoped`                                 | docs    | S      | ✅ closed ([6d2baae](https://github.com/khmelevartem/tether/commit/6d2baae), PR #159; уточнение row contract — [e0ae4b4](https://github.com/khmelevartem/tether/commit/e0ae4b4), PR #200) |

**Итог:** 4/5 формально закрыты, #8 переплан в эпик с явным графом sub-issues (preparatory часть выполнена). Цель спринта по device name MVP, transport reliability (timeout + Wi-Fi/wake lock) и закрытию trio system-specs достигнута. Первый юзабельный sender-флоу как single-PR результат не достигнут — заменён на эпик #8 с декомпозицией; реальный sender запускается в sprint-06+. К концу спринта эпик #8 прошёл архитектурный аудит против file-transfer ux-brief, sub-issues #187–#195 переписаны под PeerCard-as-sole-surface, заведены sub-issues #223/#224 и foundation #222, появился engineering doc [`file-transfer.md`](../engineering/file-transfer-wire.md) — sprint-07 стартует с консистентной декомпозицией.

## Доп. результаты (вне исходного состава)

| #   | Issue / PR                                                | Что и почему важно                                                                                  |
| --- | --------------------------------------------------------- | --------------------------------------------------------------------------------------------------- |
| #166 | ADR network stack ([cb2cb46](https://github.com/khmelevartem/tether/commit/cb2cb46), PR #167) | Зафиксирован Ktor CIO для всех KMP-таргетов. Готовит #140 (TLS-pinned) и снимает неопределённость по network stack для последующих UI/transfer работ. |
| #170 | Hotspot transfer — design discovery layers ([7a3e48f](https://github.com/khmelevartem/tether/commit/7a3e48f), PR #172) | Engineering design для hotspot-first layered discovery, готовит реализационную цепочку #176/#177. |
| #174 | Стабилизировать CI: убрать flaky three-instances JmDNS test ([edcc84c](https://github.com/khmelevartem/tether/commit/edcc84c), PR #175) | Снят источник CI-флейков, ускоряет агентный цикл и `/code-review`. |
| #184 | review-architecture agent ([0969ef6](https://github.com/khmelevartem/tether/commit/0969ef6), PR #185) | Заведён архитектурный ревьюер для `/code-review` и `/implement` — закрывает gap по high-level decomposition checks. |
| #186 | UX brief для file-transfer ([ccb0e25](https://github.com/khmelevartem/tether/commit/ccb0e25), PR #196) | Preparatory часть эпика #8: UX-бриф + закрытие 3 open questions; основа для всех transfer screens. |
| #210 | Right-size agent models ([88d5412](https://github.com/khmelevartem/tether/commit/88d5412)) | Цена/качество агентов в `/code-review`: review-ui/review-dod → haiku, review-adversarial → opus. |
| #206 | `/document` orchestrator + `architect` agent ([994f515](https://github.com/khmelevartem/tether/commit/994f515), PR #207) | Заведён writer-агент для technical-mechanism docs и ADR'ов; разблокировал собственную работу архитектора по аудиту эпика #8 в этом же окне спринта. |
| #213 | PR description template ([8cebac7](https://github.com/khmelevartem/tether/commit/8cebac7), PR #217) | Шаблон тела PR — снижает friction для ревьюера. |
| #216 | `/progress` redesign as RPG character sheet ([f9ec0ce](https://github.com/khmelevartem/tether/commit/f9ec0ce), PR #218) | Снимок прогресса теперь даёт narrative-вид; старая числовая команда — `/progress-boring`. |
| #219 | `/sprint-pick` экспресс-команда ([6625f5a](https://github.com/khmelevartem/tether/commit/6625f5a), PR #220) | Чтение `docs/sprints/sprint-NN.md` + статус issue/PR — выбор следующей задачи без полного `/grooming`. |
| #212 | Agent prompt template `_template.md` | Шаблон для writer-агентов. |
| —   | Семь retro-PR (#168, #171, #179, #180, #181, #204, #205, #209) | Уточнения процесса agentic-цикла, скиллов `/implement` и `/code-review`. |
| —   | **Аудит эпика #8 + новые sub-issues + engineering doc** (не отдельные issue) | Архитектор переписал тела #187, #188, #190, #191, #192, #193, #194, #195 под PeerCard-as-sole-surface модель из ux-brief; заведены #222 (settings navigation surface), #223 (file-transfer settings UI), #224 (iOS share-extension), #225 (receiver-side per-file cancel — Post-MVP); создан [`docs/engineering/file-transfer.md`](../engineering/file-transfer-wire.md) фиксирующий архитектурные решения (PeerCard sole surface, ChildStack composition, preference-store split, retry rule, reconnection window, wake-lock parity). |

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

1. **Первый юзабельный sender-флоу** (#8) — **не достигнут** как single-PR результат. Эпик #8 переплан в декомпозированный граф из 9 sub-issues (#186–#195). В sprint-05 закрыт preparatory #186 (UX brief + ответы на 3 open question'а), задающий целевые состояния экранов, копию и платформенные идиомы. Реальный sender запускается из sprint-06 (preparatory wave: theme, nav, server contract).
2. **Передача файлов реалистичного размера работает** (#113) — снят 15-секундный потолок CIO default, зафиксирован engine-agnostic паттерн (`install(HttpTimeout)`) ради forward-compat с TLS-rewrite (#140).
3. **Длинные передачи на Android при заблокированном экране не рвутся** (#150) — Wi-Fi/wake lock на время активной передачи, refcount на параллельные передачи. Tether из «работает пока экран горит» переходит в «работает как фоновый сервис на всё время передачи».
4. **Device name MVP реализован** (#147) — первый исполненный пункт MVP-roadmap за пределами discovery/transfer plumbing. mDNS анонсирует осмысленное имя per platform по умолчанию, имя меняется через CLI и переживает рестарт. Разблокирует #148 (Device name UI).
5. **System-specs trio закрыто** (#121) — после `permissions` и `wifi-availability` (`scoped`) и уже-готового `device-name-bootstrapping` (`scoped`) трио системных спек завершено; имплементационные issue заведены (#201/#202/#203).

### Дополнительно

6. **Network stack зафиксирован ADR** (#166) — Ktor CIO для всех KMP-таргетов; убрана неопределённость для последующих transfer/transport работ.
7. **Hotspot transfer engineering design** (#170) — заложен фундамент для реализационной цепочки #176/#177.
8. **CI стабилизирован** (#174) — снят источник флейков в JmDNS three-instances test.
9. **review-architecture agent в `/code-review` и `/implement`** (#184) — закрыт gap по архитектурному review.
10. **Agent-model right-sizing** (#210) — цена/качество мульти-агентного цикла подкручены.
11. **Writer-агент архитектора** (#206) — `/document` оркестратор + `architect` агент в `.claude/agents/`; симметричен `spec-writer` / `ux-expert`. Использован прямо в этом же спринте для аудита эпика #8 (см. главную таблицу «Итог»).
12. **Эпик #8 переориентирован под ux-brief** (без отдельного issue) — 8 sub-issues переписаны, заведены #222–#225, новый engineering doc [`file-transfer.md`](../engineering/file-transfer-wire.md). Sprint-07 (когда подойдёт #190/#191) стартует с консистентной декомпозицией, не с устаревшими контрактами.

## Связанные продуктовые спеки

| Issue | Спека                                                                                                            |
| ----- | ---------------------------------------------------------------------------------------------------------------- |
| #8    | реализует [file-transfer/spec.md](../product/features/file-transfer/spec.md) (`scoped`, доп. результат sprint-04) — декомпозирован в эпик-граф #186–#195 |
| #113  | соответствует AC «file of any size, streaming, no OOM» из [file-transfer/spec.md](../product/features/file-transfer/spec.md) |
| #150  | соответствует AC reliability из [file-transfer/spec.md](../product/features/file-transfer/spec.md)               |
| #147  | реализует [device-name-bootstrapping/spec.md](../product/features/device-name-bootstrapping/spec.md) (`scoped`)  |
| #121  | сама правит [system/wifi-availability/spec.md](../product/features/system/wifi-availability/spec.md) (`idea` → `scoped`); #200 — отдельная пост-merge правка row contract'а: вынос из `wifi-availability` в [device-list/spec.md](../product/features/device-list/spec.md) |
| #186  | создаёт [file-transfer/ux-brief.md](../product/features/file-transfer/ux-brief.md) и правит [file-transfer/spec.md](../product/features/file-transfer/spec.md) — закрыты 3 open question'а |
