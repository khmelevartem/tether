## Цель спринта

Снять два архитектурных блокера MVP, разнести Desktop entry points под будущий UI и закрыть структурный класс багов в discovery. Спринт целенаправленно tilted в сторону продуктовой проработки — после него следующий спринт сможет быть implementation-heavy. К концу спринта:

- решение по channel encryption принято и зафиксировано в [security.md](../product/security.md); снят последний архитектурный блокер pairing и transport;
- permissions strategy переведена в статус `scoped` — разблокированы все UI-задачи, требующие Local Network, FGS / `POST_NOTIFICATIONS`, firewall prompt;
- Desktop-таргет имеет две точки входа (CLI и UI), дистрибутив указывает на UI — следующий спринт может писать Desktop device list / send screen;
- discovery работает через единое common-first хранилище peer'ов — закрыт системно класс «два устройства с одинаковым именем», не точечно для одной платформы;
- спека device name bootstrapping переведена в `scoped` — следующий спринт сможет реализовать settable device name (MVP-item из roadmap).

## Состав

| #   | Issue                                                       | Название                                                            | Тип     | Размер |
| --- | ----------------------------------------------------------- | ------------------------------------------------------------------- | ------- | ------ |
| 1   | [#123](https://github.com/khmelevartem/tether/issues/123)   | Channel encryption decision: TLS-pinned vs plain HTTP vs Noise      | docs    | M      |
| 2   | [#122](https://github.com/khmelevartem/tether/issues/122)   | Спека permissions strategy: довести до `scoped`                     | docs    | S      |
| 3   | [#55](https://github.com/khmelevartem/tether/issues/55)     | Desktop таргет: развести CLI и UI точки входа                       | refactor | M     |
| 4   | [#111](https://github.com/khmelevartem/tether/issues/111)   | Discovery: единое common-first хранилище peer'ов (DiscoveredDevicesStore) | refactor | M |
| 5   | [#120](https://github.com/khmelevartem/tether/issues/120)   | Продуктовая спека device name bootstrapping → `scoped`              | docs    | S      |

## Параллелизм по слоям

| Слой                                                                 | Задачи |
| -------------------------------------------------------------------- | ------ |
| `docs/product/security.md` + `features/`                             | #123   |
| `docs/product/features/system/permissions/spec.md`                   | #122   |
| `composeApp/build.gradle.kts` + Desktop entry point                  | #55    |
| `commonMain` discovery (DiscoveredDevicesStore + 4 platform adapters) | #111  |
| `docs/product/features/identity/device-name-bootstrapping.md`        | #120   |

Пять независимых треков. DOCS-треки (#123, #122, #120) пишутся в разных файлах. Код-треки (#55, #111) трогают разные модули и source sets, конфликтов нет.

## Цепочки блокировок наружу

- **#123 → #10, #107, #116, #119.** Decision разблокирует pairing protocol, file-transfer spec, Apple EC keys, transport hardening.
- **#122 → #8, #11, #58.** Permissions scoping разблокирует Android send UI, Pairing PIN UI на всех платформах, Android service UI control.
- **#55 → Desktop UI epic** (зонтичная не заведена; появится после этого спринта). Без #55 первый Desktop UI коммит ломает CLI.
- **#111 → стабильное discovery** для всех будущих фич, никем напрямую не блокирующее, но снимает класс багов.
- **#120 → settable device name (MVP-item).** Спека готовит implementation issue для следующего спринта.

## Связанные продуктовые спеки

| Issue | Спека                                                                                                |
| ----- | ---------------------------------------------------------------------------------------------------- |
| #123  | сама правит [security.md](../product/security.md) (закрывает channel encryption open question)       |
| #122  | сама правит [system/permissions/spec.md](../product/features/system/permissions/spec.md) (`idea` → `scoped`) |
| #55   | без продуктовой спеки — рефактор entry points                                                        |
| #111  | без продуктовой спеки — рефактор discovery layer                                                     |
| #120  | сама правит [device-name-bootstrapping.md](../product/features/identity/device-name-bootstrapping.md) (`idea` → `scoped`) |

## Не вошло намеренно

- **#10 (паринг — handshake + PIN + CLI-флоу)** — blocked by #123 в этом же спринте. По правилу `grooming.md`: «если #A блокирует #B, нельзя брать обе в один спринт». Идёт в спринт 5.
- **#107 (file-transfer spec → `scoped`), #116 (Apple EC P-256), #119 (transport hardening)** — все blocked by #123. Спринт 5.
- **#8 (Android send UI), #11 (Pairing PIN UI all platforms), #58 (Android service UI control)** — blocked by #122. Спринт 5.
- **#124 (multi-file transfer spec)** — sub-issue #107, каскад через #123. Спринт 5–6.
- **#121 (Wi-Fi availability spec)** — orthogonal DOCS, без MVP-блокировки. Спринт 5 как буферная задача.
- **#41 (macOS native entry point)** — пост-MVP по решению владельца продукта.
- **#74 (KydraLog), #100 (UI localization)** — без MVP-блокировки, не вошли по бюджету.
- **#25, #91, #113** — все child #119, каскад через #123.
- **#36, #59** — отложены.

## Полезный инкремент

После спринта:

1. **Криптография MVP решена** (#123) — снят последний архитектурный блокер pairing и transport. Реализация пойдёт со следующего спринта без архитектурных пересмотров.
2. **Permissions strategy зафиксирована** (#122) — UI-волна (Android send UI, pairing PIN UI на всех платформах, Android service control) разблокирована единой стратегией, а не по три ad-hoc решения на платформу.
3. **Desktop UI готов писаться** (#55) — `./gradlew :composeApp:run` (Compose plugin default, UI) и `:composeApp:runDesktopCli` (изолированная CLI compilation) на месте. Первый Desktop UI коммит не будет ломать CLI.
4. **Discovery без race-conditions по identity** (#111) — единый `DiscoveredDevicesStore` в `commonMain`; класс багов с одинаковыми именами закрыт системно по всем 4 платформам, а не как ad-hoc патч на одну из них.
5. **Device name MVP сформулирован** (#120) — defaults per platform, момент first-launch rename, mDNS conflict resolution зафиксированы. Имплементация settable device name (пункт MVP из roadmap) пойдёт в следующий спринт без продуктовых пробелов.
