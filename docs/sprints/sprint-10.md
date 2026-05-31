## Цель спринта

Разблокировать pairing-эпик — Apple-ключи и handshake-CLI готовы под PIN UI следующего спринта — привести transfer surface к зафиксированным v0-референсам, закодифицировать четырёхслойную архитектуру в едином документе и закрыть остаточные race-условия в `PendingFilesRepository`. Sender-wiring волна (#192 / #193 / #194) сдвигается на sprint-11.

## Состав

| # | Issue | Название | Тип | Размер |
| - | ----- | -------- | --- | ------ |
| 1 | [#333](https://github.com/khmelevartem/tether/issues/333) | UX of share-sheet tap during active transfer | docs | S |
| 2 | [#327](https://github.com/khmelevartem/tether/issues/327) | PeerTransferComponent.onCardClick молча роняет share-sheet payload при активной передаче | bugfix | S |
| 3 | [#336](https://github.com/khmelevartem/tether/issues/336) | Atomic clear in PendingFilesRepository against pending overwrite race | refactor | S |
| 4 | [#317](https://github.com/khmelevartem/tether/issues/317) | Apply v0 visual references to transfer UI | refactor | M |
| 5 | [#321](https://github.com/khmelevartem/tether/issues/321) | Codify the UI → Presentation → Domain → Data layering scheme | docs | M |
| 6 | [#116](https://github.com/khmelevartem/tether/issues/116) | Apple: настоящая EC P-256 пара ключей через Security framework + Keychain | enhancement | S |
| 7 | [#10](https://github.com/khmelevartem/tether/issues/10) | Pairing — handshake, вычисление PIN-кода, CLI-флоу | feature | M |
| 8 | [#328](https://github.com/khmelevartem/tether/issues/328) | Batch send + retry in CLI via PeerTransferEngine | feature | M |

## Следствия

- После #116 + #10 pairing работает сквозным CLI-флоу: JVM↔Apple handshake идёт по настоящему EC P-256, PIN-код считается детерминированно с обеих сторон. Разблокирован #11 (PIN UI на 4 платформах) — единственный оставшийся pairing-таск до feature-completeness.
- После #317 transfer surface получает финальную визуальную поверхность по v0-референсам и `ui-style-guide.md`; sender-wiring волна следующего спринта приклеивается к стабильному визуалу.
- После #321 четырёхслойная архитектура (UI → Presentation → Domain → Data) живёт одним документом вместо россыпи фрагментов в `presentation-layer.md` / `architecture-principles.md` / `modules.md` / `dependency-injection.md`. Любая последующая задача обсуждается на общем словаре слоёв.
- После #327 + #336 `PendingFilesRepository` устойчив к share-sheet payload-drop'ам и к гонке между auto-send consumer'ами и concurrent `setPending` — share-intent / drag-drop entry points (#192 / #193 / #194 следующего спринта) приземляются на детерминированную базу.
- После #328 CLI ходит через тот же `PeerTransferEngine`, что и UI — fix на одной стороне автоматически работает на другой. Удобная база для smoke-валидации sender-wiring в sprint-11.

## Порядок мерджа

#333 → #327 → #336 → #317 → #321 (параллельно с UI) → #116 → #10 → #328 (параллельно с pairing)

#333 — UX-решение по конфликту share-sheet vs active transfer, без него #327 не может выбрать surface (banner / dialog / disabled card). #327 — фикс по принятому решению, ложится до реcкина и освобождает `PeerTransferComponent` от TODO. #336 — атомарный `clearIfMatches` на ту же поверхность `PendingFilesRepository`; идёт сразу после #327, чтобы оба фикса разъехались по одному файлу одной волной. #317 — широкий рефакторинг по common-UI, должен лечь до любых других UI-задач. #321 — чистые docs, параллелится с UI. #116 — изолированный Apple-блокер для #10. #10 — единственная задача с pairing-нагрузкой на `network/FileServerRoutes` (`/pair` route). #328 — изолированный CLI, параллелится с pairing.
