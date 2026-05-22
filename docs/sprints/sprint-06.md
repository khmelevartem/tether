## Цель спринта

Preparatory wave эпика #8 (file-transfer UI) на всех платформах + замыкание визуального loop'а для агентного цикла `/implement`. После спринта эпик готов к параллельному запуску UI и платформ-wiring (#190/#191/#192/#193/#194) — design-system, навигационный skeleton, server folder-send wire contract, headless screenshot pipeline и vision-reviewer все доступны как фундамент. Архитектурный аудит #190–#195 уже выполнен в sprint-05 (контракты подвинуты под file-transfer ux-brief, см. [`docs/engineering/file-transfer.md`](../engineering/file-transfer.md)) — sprint-07 берёт обновлённые контракты без preparatory-cleanup'а.

К концу спринта:

- `TetherTheme` + базовые reusable composables (Foundation + Compose Unstyled) живут в `commonMain`, `DeviceListScreen` мигрирован с Material 3 на TetherTheme — `composeApp/src/**` больше не зависит от Material 3 (`androidx.compose.material3.*` исчезает из кода вне TetherTheme внутренностей);
- Decompose `RootComponent` + `ChildStack` навигационный скелет с одним child = `DeviceListChild`, заложен restore-safe pattern (`ActivityResource<T>` + pop-on-empty-restore) — `App.kt` больше не вызывает `DeviceListScreen` напрямую;
- `FileServer` принимает relative path в headers/multipart, sanitize-логика отбивает `..`/абсолютные/drive-letter/URL-схемы, добавлен seam `UploadStorage` для платформенной подмены приземляющего хранилища (Android MediaStore vs Desktop FS), receiver на flat-имена не ломается;
- Headless screenshot pipeline: ADR по выбору инструмента (Roborazzi hypothesis), `PreviewFixtures` + `PreviewSurface { … }` в `commonMain`, авто-обнаружение `@Preview` через ComposablePreviewScanner, PNG'и в `composeApp/build/outputs/...`, `review-visual` агент в `/implement` и `/code-review`, превью для существующих экранов (`DeviceListScreen` + новые primitives из #187);
- KtLint custom rule запрещает `runBlocking` в тестовом коде; allowlist для реально-нужных мест (JmDNS/NsdManager/real Ktor server tests) задан per-file опцией или suppress-комментариями.

## Состав

| #   | Issue                                                     | Название                                                                                | Тип     | Размер |
| --- | --------------------------------------------------------- | --------------------------------------------------------------------------------------- | ------- | ------ |
| 1   | [#187](https://github.com/khmelevartem/tether/issues/187) | TetherTheme + reusable UI primitives + миграция DeviceListScreen с Material 3           | feature | L      |
| 2   | [#188](https://github.com/khmelevartem/tether/issues/188) | Decompose RootComponent + ChildStack navigation skeleton с restore-safe pattern         | refactor | M     |
| 3   | [#189](https://github.com/khmelevartem/tether/issues/189) | FileServer wire contract для folder send: sanitize пути и UploadStorage seam            | feature | M      |
| 4   | [#127](https://github.com/khmelevartem/tether/issues/127) | Headless screenshot-рендер Compose previews (Roborazzi + ComposablePreviewScanner) + `review-visual` агент | infra | L |
| 5   | [#169](https://github.com/khmelevartem/tether/issues/169) | KtLint custom rule: запрет `runBlocking` в тестовом коде                                | infra   | S      |

### Внешний контекст

- **#147 (Device name backend)** — закрывается на стыке sprint-05/06 (user-confirmed «доделываю»). Разблокирует #148 (Device name UI), который в sprint-06 не входит из-за конфликта с #187 по `DeviceListScreen`.
- **#206 (`/document` оркестратор + `architect` агент)** — смержен в main ([994f515](https://github.com/khmelevartem/tether/commit/994f515), PR #207) на стыке sprint-05/06; используется для предстоящего пересмотра #190–#195 эпика #8.
- **#186 (file-transfer UX brief)** — закрыт в sprint-05, является source-of-truth для #187/#188/#189/#127 (последний рендерит previews экранов, определённых брифом) и для последующего пересмотра #190–#195.

## Параллелизм по слоям

| Слой                                                                                                            | Задачи |
| --------------------------------------------------------------------------------------------------------------- | ------ |
| `commonMain/presentation/theme` + `commonMain/presentation/ui` + `commonMain/presentation/devicelist` (миграция) | #187   |
| `commonMain/presentation/root` (`RootComponent`, `RootChild`) + `App.kt` (4 платформенных entry-point'а)         | #188   |
| `commonMain/network/FileServer*` (routes + sanitize) + `androidMain/jvmMain/appleMain/UploadStorage.*` (actuals) | #189   |
| `composeApp/build.gradle.kts` (Roborazzi/инструмент из ADR + `androidUnitTest` deps), `commonMain/ui/preview/PreviewFixtures.kt`, `androidUnitTest/PreviewScreenshotTest.kt`, `docs/engineering/adr/adr-screenshot-testing.md`, `.claude/agents/review-visual.md`, `.claude/skills/{implement,code-review}/SKILL.md` | #127 |
| `build-logic/` или `composeApp/build.gradle.kts` (KtLint custom rule registration) + suppress-аннотации в test-файлах | #169 |

Пять треков с допустимыми точками трения:

- **#187 ↔ #188** — оба правят `App.kt` и общий DI (`AppContainer`). #187 — рендер `TetherTheme { … }`; #188 — рендер `RootContent(component)`. По очерёдности merge'а: #188 первым (вводит `Root`), #187 вторым (оборачивает Root в TetherTheme). При обратном порядке — rebase ~ строчного уровня.
- **#187 ↔ #127** — оба пишут `@Preview` в `commonMain`. #127 строит infra (`PreviewFixtures`, `PreviewSurface { … }`); #187 пишет previews для primitives через эту infra. Порядок: #127 первым, тогда #187 уже использует готовые helpers.
- **#187 ↔ #169** — KtLint custom rule может задеть тесты, которые #187 пишет под примитивы. #169 первым; #187 пишет тесты сразу под новый стиль.
- **#127 ↔ #169** — оба правят `composeApp/build.gradle.kts` (#127 — плагин Roborazzi + `androidUnitTest` deps; #169 — registration custom rule). Разные блоки, конфликт построчного уровня.
- **#127 ↔ #189** — независимы (разные слои).

Порядок мерджа для минимизации ребейзов: #169 → #127 → #188 → #187 → #189.

## Цепочки блокировок наружу

- **#187 →** разблокирует #191 (Transfer screens + DeviceList pending-banner) — sprint-07.
- **#188 →** разблокирует #190 (BatchSender + TransferComponent state-machine) и далее #191 — sprint-07.
- **#189 →** разблокирует #195 (Receiver UI с прогрессом, folder-aware) — sprint-07.
- **#127 →** замыкает visual-loop для агентного цикла на всех UI-задачах эпика (#187, #191, #195) и device-list-track (#148, #203). До закрытия #127 review-ux работает только по тексту кода, без визуальной проверки. Параллельно разблокирует адекватный preview-suite для #187 (DoD #187 требует `@Preview` light + dark для каждого primitive).
- **#169 →** дисциплинарный, наружу никого формально не разблокирует, но снимает источник review-loop'ов под `runBlocking` в новых тестах sprint-06 и далее.

## Связанные продуктовые спеки

| Issue | Спека / engineering doc                                                                                                       |
| ----- | ----------------------------------------------------------------------------------------------------------------------------- |
| #187  | [file-transfer/ux-brief.md](../product/features/file-transfer/ux-brief.md), [`docs/engineering/ui-style-guide.md`](../engineering/ui-style-guide.md) (Material 3 ban) |
| #188  | [file-transfer/ux-brief.md](../product/features/file-transfer/ux-brief.md) (nav targets), Decompose presentation-layer rules ([`docs/engineering/presentation-layer.md`](../engineering/presentation-layer.md)) |
| #189  | [file-transfer/spec.md](../product/features/file-transfer/spec.md) (folder send AC), [file-transfer/ux-brief.md](../product/features/file-transfer/ux-brief.md) (folder progress) |
| #127  | INFRA, не привязан к продуктовой спеке; сам создаёт `docs/engineering/adr/adr-screenshot-testing.md` (новый ADR) и расширяет [`docs/engineering/testing.md`](../engineering/testing.md) (Screenshot tests). Потребитель — `review-ux` / `review-visual` против всех `docs/product/features/*/ux-brief.md`. |
| #169  | [`docs/engineering/testing.md§Стиль`](../engineering/testing.md) — запрет `runBlocking` уже зафиксирован, custom rule делает его enforced |

## Не вошло намеренно

- **#190 (BatchSender state-machine), #191 (Transfer UI screens), #192/#193/#194 (per-platform sender wiring), #195 (Receiver UI)** — все blocked-by задачами sprint-06 (#187/#188/#189). По правилу `grooming.md`: «если #A блокирует #B, нельзя брать обе в один спринт». **Кроме того**, при сверке этих задач с file-transfer ux-brief найдены значимые расхождения в декомпозиции (см. аудит ниже), которые нужно отразить в issue-телах до старта sprint-07.
- **#202 (LocalNetworkAvailability impl)** — отложена решением owner'а; полагаемся на existing «assume reachable» поведение discovery. Подбираем позже, когда возникнет реальный потребитель (UI empty-state, hotspot-fallback).
- **#148 (Device name UI)** — формально разблокирован #147, но конфликтует с #187 на `DeviceListScreen` (которая мигрирует с Material 3 и получает новую структуру). Sprint-07 после стабилизации TetherTheme.
- **#203 (NoLocalNetworkState UI)** — blocked-by #202. Пока #202 отложена — #203 тоже.
- **#201 (device list row contract paired × reachable)** — зависит от pairing-фичи (#10 ещё в backlog) и реальной информации о reachable; пока pairing не реализован, contract нечего реализовать. Sprint-07+ после #10.
- **#164 (TCP keepalive на FileServer)** — конфликтует с #189 по `FileServer*.kt`. Sprint-07.
- **#74 (KydraLog логгер)** — конфликтует со всеми задачами по `FileClient`/`FileServer`/`MdnsDiscovery`/discovery wiring. Sprint-07+.
- **#176/#177 (Hotspot transfer Phase A/B)** — engineering design (#170) сделан, но имплементация ниже приоритетом MVP-cross-platform sender. Sprint-07+.
- **#10 (pairing CLI), #11 (Pairing PIN UI)** — продуктово отложен, runtime не enforce'ит trust. Sprint-07+.
- **#119/#91/#25 (transport hardening umbrella, Android emulator hang, Expect: 100-continue)** — низкий приоритет относительно sender-флоу.
- **#140 (TLS-pinned transport), #116 (Apple EC P-256 keys)** — L+ имплементации после #123 ADR, перепишут тот же FileClient слой, что трогает #189. Sprint-08+.
- **#198 (структурированные findings в `/code-review`), #197 (auto-очередь спринта)** — INFRA-улучшения процесса; не блокируют код-трек. Sprint-07+.
- **#173 (trim tech-stack.md)** — docs-only S, не конфликтует ни с чем, можно подобрать filler-ом в любой момент. Sprint-07.
- **#182 (test AndroidMediaStoreUploadStorage)** — узкое тестовое покрытие; после #189 актуальность зависит от того, остался ли исходный writeViaMediaStore branch.
- **#58/#59 (Android service UI/dataSync cap)** — конфликт по `TetherForegroundService.kt`. Sprint-08+.
- **#100/#41/#36** — пост-MVP / низкий приоритет.

## Полезный инкремент

После спринта:

1. **Эпик #8 готов к параллельному развёртыванию по платформам** — preparatory wave (#187 theme, #188 nav, #189 server contract) закрыт; следующий спринт может одновременно стартовать #190 (state-machine), #191 (UI) и далее #192/#193/#194 без preparatory-cleanup'а. Декомпозиция уже консистентна с ux-brief: аудит эпика #8 и переписывание контрактов #190–#195 выполнены в sprint-05, архитектурные решения зафиксированы в [`docs/engineering/file-transfer.md`](../engineering/file-transfer.md). Sprint-07 стартует с подвинутыми #190/#191 (выросли после аудита) + #222 (settings surface как первый пользователь `RootComponent.Child`) + #148 (Device name UI после стабилизации TetherTheme).
2. **Material 3 окончательно удалён из `composeApp/src/**`** — закрыт `ui-style-guide.md` Material 3 ban; визуальная идентичность Tether приземлена через свой TetherTheme.
3. **Folder send wire contract зафиксирован** (#189) — sender может передавать рекурсивно с сохранением структуры; receiver принимает sanitized paths. Cross-platform sender (#192/#193/#194) реализует контракт, а не изобретает его.
4. **Замкнут визуальный loop для агентного цикла** (#127) — `@Preview` рендерятся headless в PNG, `review-visual` агент сверяет визуал с UX-брифом. `/implement` на UI-задачах больше не требует человека для визуальной проверки; `review-ux` дополнен пиксельной стороной.
5. **Дисциплина тестов закреплена в lint** (#169) — `runBlocking` в новых тестах ловится автоматически, review-агенты больше не должны его обсуждать.

Все 5 задач имеют либо спеку, либо engineering doc, либо UX brief в `docs/`. Спеки писать не нужно.
