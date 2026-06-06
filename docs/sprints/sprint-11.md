# Sprint 11 · Пепел и Железо

**Направления:** CLI · smoke · Android sender · pairing

## Состав

**Итог:** 5/6 задач закрыто. #10 — частично: реальная работа вынесена в #361 и #378, перенос в sprint-12.

| # | Issue | Название | Тип | Размер | Итог |
| - | ----- | -------- | --- | ------ | ---- |
| 1 | [#345](https://github.com/khmelevartem/tether/issues/345) | CLI: два экземпляра на одном хосте не видят друг друга | bugfix | S | ✅ closed (дубль [#346](https://github.com/khmelevartem/tether/issues/346), PR #350) |
| 2 | [#368](https://github.com/khmelevartem/tether/issues/368) | Same-named peers stay distinguishable in the peer list | bugfix | S | ✅ closed (PR #379) |
| 3 | [#352](https://github.com/khmelevartem/tether/issues/352) | Spaces in filenames arrive as '+' on receiver | bugfix | S | ✅ closed (PR #373) |
| 4 | [#348](https://github.com/khmelevartem/tether/issues/348) | CLI: design and apply a convenient log format | refactor | M | ✅ closed (PR #385) |
| 5 | [#192](https://github.com/khmelevartem/tether/issues/192) | Android sender wiring: SAF picker, share-sheet и MediaStore receiver | feature | M | ✅ closed (PR #383, follow-up #391) |
| 6 | [#10](https://github.com/khmelevartem/tether/issues/10) | Паринг — handshake, вычисление PIN-кода, CLI-флоу | feature | M | 🟡 partial — PR #362 открыт; части вынесены в #361/#378, перенос в sprint-12 |

## Дополнительные результаты

Закрыто в окне спринта вне исходного состава:

- **#375** (PR #377) — стабилизация флэйки `CliSendTest` и `FileClientTest`: прогон тестов перестал падать на гонках, smoke и unit-проходы читаемы.
- **#392** (PR #393) — `/progress`: карта заданий по умолчанию скрывает закрытые задачи (галочка возвращает), а сбор сырых данных вынесен в воспроизводимый `collect.py`.

## Что разблокирует

- После #345 smoke Block 2 (Desktop↔Desktop send) проходит без внешнего Android-устройства — CI-smoke становится полностью автономным.
- После #352 + #368 + #348 wire-протокол, peer-list и CLI-вывод доведены до production-качества: Android sender (#192) и следующие платформенные sender'ы (#193/#194) не наследуют известные баги, а smoke читается без шума.
- После #10 паринг становится сквозным E2E-флоу на CLI: Android UI подтверждения (#6c) и мобильные платформы могут опираться на готовый commonMain-контракт.

## Порядок мерджа

#345 || #368 || #352 → #348 || #192 || #10

`||` — параллельные ветки. #352 и #348 оба правят `FileClient.kt` — мержить последовательно. #345 (desktopCli fingerprint), #368 (hello handler), #192 (androidMain) и #10 (pairing commonMain + jvmMain) полностью изолированы от остальных.
