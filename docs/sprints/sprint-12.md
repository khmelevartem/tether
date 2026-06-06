# Sprint 12 · Узы и Стражи

**Направления:** pairing · sender wiring · security · tooling

## Состав

| # | Issue | Название | Тип | Размер |
| - | ----- | -------- | --- | ------ |
| 1 | [#10](https://github.com/khmelevartem/tether/issues/10) | Паринг — handshake, вычисление PIN-кода, CLI-флоу | feature | M |
| 2 | [#378](https://github.com/khmelevartem/tether/issues/378) | Independent threat model and attack-surface analysis for pairing and transfer | docs | L |
| 3 | [#361](https://github.com/khmelevartem/tether/issues/361) | Pairing mutual confirmation before trust is stored | feature | M |
| 4 | [#193](https://github.com/khmelevartem/tether/issues/193) | Desktop sender wiring: AWT file picker на EDT и drag-and-drop по окну | feature | M |
| 5 | [#194](https://github.com/khmelevartem/tether/issues/194) | iOS sender wiring: UIDocumentPickerViewController и bookmark-based FileSource | feature | M |
| 6 | [#198](https://github.com/khmelevartem/tether/issues/198) | Structured findings + synthesis pass в /code-review | infra | M |
| 7 | [#354](https://github.com/khmelevartem/tether/issues/354) | Block writes outside the current worktree via PreToolUse hook | infra | S |

## Что разблокирует

- После #10 + #378 + #361 паринг закрывает MVP-главу «Четырёхзначная Клятва»: trust устанавливается только после взаимного подтверждения, а #11 (PIN-диалоги Android/iOS/Desktop) получает готовый сквозной контракт.
- После #193 + #194 sender есть на всех четырёх платформах — file-transfer перестаёт быть Android-only, и #224 (iOS Share Sheet) встаёт на готовый iOS FileSource.

## Порядок мерджа

#10 || #378 → #361 ; #193 → #194 ; #198 || #354

`||` — параллельные ветки. #361 заблокирован #10 и #378 (взят в спринт по явному решению — мержить строго после обоих). #193 и #194 делят commonMain sender-seam от #192 — если кто-то из них его трогает, мержить последовательно. #198 и #354 — изолированный `.claude/`-тулинг (skill-файлы vs хук в settings), параллельны. #10 (pairing commonMain + jvmMain/CLI) и #378 (docs-only) друг от друга независимы.
