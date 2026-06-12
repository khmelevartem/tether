# Sprint 13 · Острог на Берегу

**Направления:** iOS · transfer · UI · identity

## Состав

| # | Issue | Название | Тип | Размер |
| - | ----- | -------- | --- | ------ |
| 1 | [#224](https://github.com/khmelevartem/tether/issues/224) | iOS Share Sheet → Tether: отправка файлов из Photos / Files / других приложений | feature | L |
| 2 | [#417](https://github.com/khmelevartem/tether/issues/417) | Received files reachable by the user on iOS | feature | M |
| 3 | [#426](https://github.com/khmelevartem/tether/issues/426) | Mobile screens have unwanted edge padding | bugfix | S |
| 4 | [#429](https://github.com/khmelevartem/tether/issues/429) | Derive device fingerprint from EC P-256 public key | feature | S |
| 5 | [#425](https://github.com/khmelevartem/tether/issues/425) | Reactive peer snapshot in PeerTransferComponent — fix stale isOnline | refactor | S |
| 6 | [#389](https://github.com/khmelevartem/tether/issues/389) | CLI: restore live byte/speed progress line during send | bugfix | S |

## Что разблокирует

- После #224 + #417 iOS получает полный round-trip: отправка через системный share-sheet и доступ к принятым файлам — закрывается iOS-хвост эпика #8.
- После #429 снимается блокер #10 (паринг переходит на fingerprint от настоящего EC-ключа вместо interim random hex) — #10 встаёт в Sprint 14 без переезда.

## Порядок мерджа

#224 → #417 ; #426 || #429 || #425 || #389

`||` — параллельные ветки. #224 и #417 делят iosMain file-handling (Share Extension target / app group и receive-path destination) — мержить последовательно. #426 (common-UI `safeContentPadding`), #429 (commonMain crypto/identity), #425 (presentation `PeerTransferComponent`) и #389 (jvmMain/CLI) изолированы друг от друга и от iOS-пары.
