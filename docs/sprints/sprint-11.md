# Sprint 11 · Пепел и Железо

**Направления:** CLI · smoke · Android sender

## Состав

| # | Issue | Название | Тип | Размер |
| - | ----- | -------- | --- | ------ |
| 1 | [#345](https://github.com/khmelevartem/tether/issues/345) | CLI: два экземпляра на одном хосте не видят друг друга | bugfix | S |
| 2 | [#368](https://github.com/khmelevartem/tether/issues/368) | Same-named peers stay distinguishable in the peer list | bugfix | S |
| 3 | [#352](https://github.com/khmelevartem/tether/issues/352) | Spaces in filenames arrive as '+' on receiver | bugfix | S |
| 4 | [#348](https://github.com/khmelevartem/tether/issues/348) | CLI: design and apply a convenient log format | refactor | M |
| 5 | [#192](https://github.com/khmelevartem/tether/issues/192) | Android sender wiring: SAF picker, share-sheet и MediaStore receiver | feature | M |

## Что разблокирует

- После #345 smoke Block 2 (Desktop↔Desktop send) проходит без внешнего Android-устройства — CI-smoke становится полностью автономным.
- После #352 + #368 + #348 wire-протокол, peer-list и CLI-вывод доведены до production-качества: Android sender (#192) и следующие платформенные sender'ы (#193/#194) не наследуют известные баги, а smoke читается без шума.

## Порядок мерджа

#345 || #368 || #352 → #348 || #192

`||` — параллельные ветки. #352 и #348 оба правят `FileClient.kt` — мержить последовательно. #345 (desktopCli fingerprint), #368 (hello handler) и #192 (androidMain) полностью изолированы от остальных.
