# Sprint 12 · Узы и Стражи

**Направления:** pairing · sender wiring · security · tooling

## Состав

**Итог:** 5/7 закрыто. #10 переехал (новый блокер [#429](https://github.com/khmelevartem/tether/issues/429)), #361 снят со скоупа — поглощён в переопределённый #10.

| # | Issue | Название | Тип | Размер | Итог |
| - | ----- | -------- | --- | ------ | ---- |
| 1 | [#10](https://github.com/khmelevartem/tether/issues/10) | Паринг — handshake, вычисление PIN-кода, CLI-флоу | feature | M | ❌ не сделано (блокер [#429](https://github.com/khmelevartem/tether/issues/429); попытка [#362](https://github.com/khmelevartem/tether/pull/362) закрыта без мерджа) |
| 2 | [#378](https://github.com/khmelevartem/tether/issues/378) | Independent threat model and attack-surface analysis for pairing and transfer | docs | L | ✅ закрыто ([PR #394](https://github.com/khmelevartem/tether/pull/394)) |
| 3 | [#361](https://github.com/khmelevartem/tether/issues/361) | Pairing mutual confirmation before trust is stored | feature | M | ❌ снят (NOT_PLANNED — confirm-before-trust поглощён в переопределённый #10) |
| 4 | [#193](https://github.com/khmelevartem/tether/issues/193) | Desktop sender wiring: AWT file picker на EDT и drag-and-drop по окну | feature | M | ✅ закрыто ([PR #401](https://github.com/khmelevartem/tether/pull/401)) |
| 5 | [#194](https://github.com/khmelevartem/tether/issues/194) | iOS sender wiring: UIDocumentPickerViewController и bookmark-based FileSource | feature | M | ✅ закрыто ([PR #416](https://github.com/khmelevartem/tether/pull/416)) |
| 6 | [#198](https://github.com/khmelevartem/tether/issues/198) | Structured findings + synthesis pass в /code-review | infra | M | ✅ закрыто ([PR #396](https://github.com/khmelevartem/tether/pull/396)) |
| 7 | [#354](https://github.com/khmelevartem/tether/issues/354) | Block writes outside the current worktree via PreToolUse hook | infra | S | ✅ закрыто ([PR #400](https://github.com/khmelevartem/tether/pull/400)) |

## Дополнительные результаты

Закрыты в окне спринта вне исходного состава:

- [#148](https://github.com/khmelevartem/tether/issues/148) ([PR #402](https://github.com/khmelevartem/tether/pull/402)) — «This device» на device list с inline-rename: закрывает UI-хвост device-name-bootstrapping.
- [#367](https://github.com/khmelevartem/tether/issues/367) ([PR #414](https://github.com/khmelevartem/tether/pull/414)) — opt-in persistent CLI identity + fingerprint-keyed PeerIdentity: стабильная идентичность пира между рестартами, фундамент под паринг.
- [#418](https://github.com/khmelevartem/tether/issues/418) ([PR #423](https://github.com/khmelevartem/tether/pull/423)) — packaged desktop installers запускаются (bundled JDK, Windows icon): первый задел под эпик #430.
- [#214](https://github.com/khmelevartem/tether/issues/214) ([PR #420](https://github.com/khmelevartem/tether/pull/420)) — review-ux-brief: UX-доменный ревьюер для ux-brief.md.
- [#404](https://github.com/khmelevartem/tether/issues/404) ([PR #406](https://github.com/khmelevartem/tether/pull/406), [PR #415](https://github.com/khmelevartem/tether/pull/415)) — smoke-test изолируется по воркт­ри и надёжно гасит свои CLI-инстансы.
- [#397](https://github.com/khmelevartem/tether/issues/397) ([PR #398](https://github.com/khmelevartem/tether/pull/398)) — sizing по review-усилию в create-issue/close-issue + cost в /progress.

## Что разблокирует

- После #10 + #378 + #361 паринг закрывает MVP-главу «Четырёхзначная Клятва»: trust устанавливается только после взаимного подтверждения, а #11 (PIN-диалоги Android/iOS/Desktop) получает готовый сквозной контракт.
- После #193 + #194 sender есть на всех четырёх платформах — file-transfer перестаёт быть Android-only, и #224 (iOS Share Sheet) встаёт на готовый iOS FileSource.

## Порядок мерджа

#10 || #378 → #361 ; #193 → #194 ; #198 || #354

`||` — параллельные ветки. #361 заблокирован #10 и #378 (взят в спринт по явному решению — мержить строго после обоих). #193 и #194 делят commonMain sender-seam от #192 — если кто-то из них его трогает, мержить последовательно. #198 и #354 — изолированный `.claude/`-тулинг (skill-файлы vs хук в settings), параллельны. #10 (pairing commonMain + jvmMain/CLI) и #378 (docs-only) друг от друга независимы.
