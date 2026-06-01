# Sprint 10 · Завет Четырёх Клинков

**Направления:** паринг · UI-полотно · документация · надёжность pending-буфера · CLI

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

## Что разблокирует

- После #116 + #10 разблокирован #11 (PIN UI на 4 платформах) — единственный оставшийся pairing-таск до feature-completeness.
- После #317 sender-wiring волна следующего спринта (#192 / #193 / #194) приземляется на стабильный визуал, а не на движущуюся поверхность.
- После #327 + #336 share-intent / drag-drop entry points sprint-11 приземляются на детерминированный `PendingFilesRepository` без race-условий.
- После #321 любая последующая задача обсуждается на общем словаре четырёх слоёв вместо россыпи фрагментов.
- После #328 CLI и UI ходят через один `PeerTransferEngine` — smoke-валидация sender-wiring в sprint-11 дешевле.

## Порядок мерджа

#333 → #327 → #336 → #317 → #321 || #116 → #10 || #328

`||` маркирует параллельные ветки. #333 — UX-решение, без него #327 не может выбрать surface. #327 → #336 — оба правят `PendingFilesRepository`, одной волной. #317 — широкий рефакторинг по common-UI, ложится до любых других UI-задач. #321 — чистые docs. #116 — изолированный Apple-блокер для #10. #328 — изолированный CLI.
