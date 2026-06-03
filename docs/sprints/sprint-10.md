# Sprint 10 · Завет Четырёх Клинков

**Направления:** паринг · UI-полотно · документация · надёжность pending-буфера · CLI

## Состав

**Итог: 7/8 задач закрыто. Pairing handshake (#10) перенесён в следующий спринт — не готов.**

| # | Issue | Название | Тип | Размер | Итог |
| - | ----- | -------- | --- | ------ | ---- |
| 1 | [#333](https://github.com/khmelevartem/tether/issues/333) | UX of share-sheet tap during active transfer | docs | S | ✅ закрыт ([PR #334](https://github.com/khmelevartem/tether/pull/334)) |
| 2 | [#327](https://github.com/khmelevartem/tether/issues/327) | PeerTransferComponent.onCardClick молча роняет share-sheet payload при активной передаче | bugfix | S | ✅ закрыт ([PR #349](https://github.com/khmelevartem/tether/pull/349)) |
| 3 | [#336](https://github.com/khmelevartem/tether/issues/336) | Atomic clear in PendingFilesRepository against pending overwrite race | refactor | S | ✅ закрыт ([PR #337](https://github.com/khmelevartem/tether/pull/337)) |
| 4 | [#317](https://github.com/khmelevartem/tether/issues/317) | Apply v0 visual references to transfer UI | refactor | M | ✅ закрыт ([PR #360](https://github.com/khmelevartem/tether/pull/360)) |
| 5 | [#321](https://github.com/khmelevartem/tether/issues/321) | Codify the UI → Presentation → Domain → Data layering scheme | docs | M | ✅ закрыт ([PR #356](https://github.com/khmelevartem/tether/pull/356)) |
| 6 | [#116](https://github.com/khmelevartem/tether/issues/116) | Apple: настоящая EC P-256 пара ключей через Security framework + Keychain | enhancement | S | ✅ закрыт ([PR #351](https://github.com/khmelevartem/tether/pull/351)) |
| 7 | [#10](https://github.com/khmelevartem/tether/issues/10) | Pairing — handshake, вычисление PIN-кода, CLI-флоу | feature | M | ❌ не сделан, перенесён в sprint-11 |
| 8 | [#328](https://github.com/khmelevartem/tether/issues/328) | Batch send + retry in CLI via PeerTransferEngine | feature | M | ✅ закрыт ([PR #343](https://github.com/khmelevartem/tether/pull/343)) |

## Дополнительные результаты

- [#346](https://github.com/khmelevartem/tether/issues/346) ([PR #350](https://github.com/khmelevartem/tether/pull/350), [PR #366](https://github.com/khmelevartem/tether/pull/366)) — Ephemeral fingerprint для CLI: два экземпляра на одном хосте перестали глушить друг друга; smoke Block 2 стал проходимым. Побочно исправлены баги smoke-test скрипта после экстракции SKILL.md.
- [PR #365](https://github.com/khmelevartem/tether/pull/365) ([#17](https://github.com/khmelevartem/tether/issues/17)) — Graceful mDNS deregister перед kill CLI в smoke-cleanup: устранён flake, при котором повторный запуск smoke видел зомби-запись от предыдущего процесса.

## Что разблокирует

- После #116 + #10 разблокирован #11 (PIN UI на 4 платформах) — единственный оставшийся pairing-таск до feature-completeness.
- После #317 sender-wiring волна следующего спринта (#192 / #193 / #194) приземляется на стабильный визуал, а не на движущуюся поверхность.
- После #327 + #336 share-intent / drag-drop entry points sprint-11 приземляются на детерминированный `PendingFilesRepository` без race-условий.
- После #321 любая последующая задача обсуждается на общем словаре четырёх слоёв вместо россыпи фрагментов.
- После #328 CLI и UI ходят через один `PeerTransferEngine` — smoke-валидация sender-wiring в sprint-11 дешевле.

## Порядок мерджа

#333 → #327 → #336 → #317 → #321 || #116 → #10 || #328

`||` маркирует параллельные ветки. #333 — UX-решение, без него #327 не может выбрать surface. #327 → #336 — оба правят `PendingFilesRepository`, одной волной. #317 — широкий рефакторинг по common-UI, ложится до любых других UI-задач. #321 — чистые docs. #116 — изолированный Apple-блокер для #10. #328 — изолированный CLI.
