## Цель спринта

Снять два архитектурных блокера MVP, разнести Desktop entry points под будущий UI и закрыть структурный класс багов в discovery. Спринт целенаправленно tilted в сторону продуктовой проработки — после него следующий спринт сможет быть implementation-heavy. К концу спринта:

- решение по channel encryption принято и зафиксировано в [security.md](../product/security.md); снят последний архитектурный блокер pairing и transport;
- permissions strategy переведена в статус `scoped` — разблокированы все UI-задачи, требующие Local Network, FGS / `POST_NOTIFICATIONS`, firewall prompt;
- Desktop-таргет имеет две точки входа (CLI и UI), дистрибутив указывает на UI — следующий спринт может писать Desktop device list / send screen;
- discovery работает через единое common-first хранилище peer'ов — закрыт системно класс «два устройства с одинаковым именем», не точечно для одной платформы;
- спека device name bootstrapping переведена в `scoped` — следующий спринт сможет реализовать settable device name (MVP-item из roadmap).

## Состав

| #   | Issue                                                       | Название                                                            | Тип     | Размер | Итог |
| --- | ----------------------------------------------------------- | ------------------------------------------------------------------- | ------- | ------ | ---- |
| 1   | [#123](https://github.com/khmelevartem/tether/issues/123)   | Channel encryption decision: TLS-pinned vs plain HTTP vs Noise      | docs    | M      | ✅ closed ([791fc3f](https://github.com/khmelevartem/tether/commit/791fc3f), PR #139) |
| 2   | [#122](https://github.com/khmelevartem/tether/issues/122)   | Спека permissions strategy: довести до `scoped`                     | docs    | S      | ✅ closed ([2104d85](https://github.com/khmelevartem/tether/commit/2104d85), PR #135) |
| 3   | [#55](https://github.com/khmelevartem/tether/issues/55)     | Desktop таргет: развести CLI и UI точки входа                       | refactor | M     | ✅ closed ([d020bb9](https://github.com/khmelevartem/tether/commit/d020bb9), PR #134) |
| 4   | [#111](https://github.com/khmelevartem/tether/issues/111)   | Discovery: единое common-first хранилище peer'ов (DiscoveredDevicesStore) | refactor | M | ✅ closed ([2723e27](https://github.com/khmelevartem/tether/commit/2723e27), PR #133) |
| 5   | [#120](https://github.com/khmelevartem/tether/issues/120)   | Продуктовая спека device name bootstrapping → `scoped`              | docs    | S      | ✅ closed ([13340a4](https://github.com/khmelevartem/tether/commit/13340a4), PR #132) |

**Итог:** 5/5 задач закрыты. Все цели спринта достигнуты.

### Доп. результаты, пришедшие в этот же временной интервал (вне состава)

- **#107** ([d9205f5](https://github.com/khmelevartem/tether/commit/d9205f5), PR #153) — file-transfer spec → `scoped`. Был помечен «не вошёл, blocked by #123» — взят сразу после мерджа #123 в этом же спринте.
- **#81** — `FileServer.apple` (приём файлов на iOS) — закрыт. iOS поднялся из «scaffold» в полноценный таргет.
- **#145** ([1192f6f](https://github.com/khmelevartem/tether/commit/1192f6f), PR #151) — visual identity locked: teal+copper, Tabler icons, Compose Unstyled, Material 3 запрещён. Новые `docs/engineering/ui-style-guide.md` и `ui-brand-mark.md`; ADR `adr-visual-identity.md`.
- **#124** — closed as superseded by #107 (multi-file folded в `file-transfer.md` как N≥1). Гигиена бэклога.

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
| #123  | правит [security.md](../product/security.md) (закрыт channel encryption open question) + новый [adr-channel-encryption.md](../engineering/adr/adr-channel-encryption.md) |
| #122  | правит [system/permissions/spec.md](../product/features/system/permissions/spec.md) (`idea` → `scoped`) |
| #55   | без продуктовой спеки — рефактор entry points                                                        |
| #111  | без продуктовой спеки — рефактор discovery layer                                                     |
| #120  | правит [device-name-bootstrapping/spec.md](../product/features/device-name-bootstrapping/spec.md) (`idea` → `scoped`) |
| #107 (доп.) | правит [file-transfer/spec.md](../product/features/file-transfer/spec.md) (`idea` → `scoped`) |
| #145 (доп.) | правит [design.md](../product/design.md), добавляет [ui-style-guide.md](../engineering/ui-style-guide.md), `ui-brand-mark.md` (removed — see #287), [adr-visual-identity.md](../engineering/adr/adr-visual-identity.md) |

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

## Полезный инкремент (факт)

1. **Криптография MVP решена** (#123) — снят последний архитектурный блокер pairing и transport. ADR `adr-channel-encryption.md` фиксирует TLS pinned + SecureTransport на Apple Native.
2. **Permissions strategy зафиксирована** (#122) — UI-волна (Android send UI, pairing PIN UI на всех платформах, Android service control) разблокирована единой стратегией.
3. **Desktop UI готов писаться** (#55) — `./gradlew :composeApp:run` (Compose plugin default, UI) и `:composeApp:runDesktopCli` (изолированная CLI compilation) на месте. CLI вынесен в собственный source set `desktopCli`, Clikt живёт только там.
4. **Discovery без race-conditions по identity** (#111) — единый `DiscoveredDevicesStore` в `commonMain`; класс багов с одинаковыми именами закрыт системно по всем 4 платформам.
5. **Device name MVP сформулирован** (#120) — defaults per platform, момент first-launch rename, mDNS conflict resolution зафиксированы.

### Дополнительно

6. **File-transfer spec scoped** (#107) — `file-transfer/spec.md` закрыл 6 open questions, multi-file fold'нут в N≥1 единой surface. Разблокирует #8 и #11 продуктово.
7. **iOS receive работает** (#81) — `FileServer.apple` больше не stub; iOS становится полноценным receiver-таргетом.
8. **Визуальная идентичность зафиксирована** (#145) — все будущие UI-задачи получают неотменяемую дизайн-базу (без Material 3, без свободных дизайн-решений на проде).
