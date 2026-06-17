# Sprint 13 · Острог на Берегу

**Направления:** iOS · transfer · UI · identity

## Состав

**Итог:** 6/6 закрыто. Все цели достигнуты — iOS round-trip и identity-фундамент под паринг закрыты.

| # | Issue | Название | Тип | Размер | Итог |
| - | ----- | -------- | --- | ------ | ---- |
| 1 | [#224](https://github.com/khmelevartem/tether/issues/224) | iOS Share Sheet → Tether: отправка файлов из Photos / Files / других приложений | feature | L | ✅ закрыто ([PR #452](https://github.com/khmelevartem/tether/pull/452)) |
| 2 | [#417](https://github.com/khmelevartem/tether/issues/417) | Received files reachable by the user on iOS | feature | M | ✅ закрыто ([PR #456](https://github.com/khmelevartem/tether/pull/456)) |
| 3 | [#426](https://github.com/khmelevartem/tether/issues/426) | Mobile screens have unwanted edge padding | bugfix | S | ✅ закрыто ([PR #448](https://github.com/khmelevartem/tether/pull/448)) |
| 4 | [#429](https://github.com/khmelevartem/tether/issues/429) | Derive device fingerprint from EC P-256 public key | feature | S | ✅ закрыто ([PR #451](https://github.com/khmelevartem/tether/pull/451)) |
| 5 | [#425](https://github.com/khmelevartem/tether/issues/425) | Reactive peer snapshot in PeerTransferComponent — fix stale isOnline | refactor | S | ✅ закрыто ([PR #450](https://github.com/khmelevartem/tether/pull/450)) |
| 6 | [#389](https://github.com/khmelevartem/tether/issues/389) | CLI: restore live byte/speed progress line during send | bugfix | S | ✅ закрыто ([PR #449](https://github.com/khmelevartem/tether/pull/449)) |

## Дополнительные результаты

Закрыты в окне спринта вне исходного состава:

- [#461](https://github.com/khmelevartem/tether/issues/461) ([PR #470](https://github.com/khmelevartem/tether/pull/470)) — приём сохраняется в системную галерею (iOS Photos + Android MediaStore) после подтверждённой загрузки: принятые медиа видны в штатной галерее, а не только внутри приложения.
- [#419](https://github.com/khmelevartem/tether/issues/419) ([PR #422](https://github.com/khmelevartem/tether/pull/422)) — spec + UX brief для clipboard-transfer: дизайн-фундамент под эпик #469.
- [#462](https://github.com/khmelevartem/tether/issues/462) ([PR #463](https://github.com/khmelevartem/tether/pull/463)) — epic-aware /progress + актуализированный индекс фич.
- [#443](https://github.com/khmelevartem/tether/issues/443) ([PR #444](https://github.com/khmelevartem/tether/pull/444)) — /implement-оркестратор работает экономнее по контексту и токенам.

## Что разблокирует

- После #224 + #417 iOS получает полный round-trip: отправка через системный share-sheet и доступ к принятым файлам — закрывается iOS-хвост эпика #8.
- После #429 снимается блокер #10 (паринг переходит на fingerprint от настоящего EC-ключа вместо interim random hex) — #10 встаёт в Sprint 14 без переезда.

## Порядок мерджа

#224 → #417 ; #426 || #429 || #425 || #389

`||` — параллельные ветки. #224 и #417 делят iosMain file-handling (Share Extension target / app group и receive-path destination) — мержить последовательно. #426 (common-UI root safe-area insets), #429 (commonMain crypto/identity), #425 (presentation `PeerTransferComponent`) и #389 (jvmMain/CLI) изолированы друг от друга и от iOS-пары.
