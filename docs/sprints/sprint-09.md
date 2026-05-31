## Цель спринта

Дозакрыть transfer UI-полотно (#191 carryover) и параллельно поднять reliability-слой перед MVP: Android FGS-тип, app-layer keepalive, hotspot Phase A. Sender wiring и receiver UI ждут окончания #191 — едут следующим спринтом.

## Состав

| # | Issue | Название | Тип | Размер | Итог |
| - | ----- | -------- | --- | ------ | ---- |
| 1 | [#191](https://github.com/khmelevartem/tether/issues/191) | Transfer UI: TransferScreen, dialogs и DeviceList pending-banner с превью | feature | L | ✅ closed ([PR #303](https://github.com/khmelevartem/tether/pull/303)) |
| 2 | [#59](https://github.com/khmelevartem/tether/issues/59) | Android FGS: оценить лимит dataSync 6h/сутки на Android 15+ и выбрать тип сервиса | infra | M | ✅ closed ([PR #308](https://github.com/khmelevartem/tether/pull/308)) |
| 3 | [#251](https://github.com/khmelevartem/tether/issues/251) | TrustedDeviceStore: migrate to DataStore backend в commonMain | enhancement | M | ✅ closed ([PR #307](https://github.com/khmelevartem/tether/pull/307)) |
| 4 | [#310](https://github.com/khmelevartem/tether/issues/310) | Survive Android 15+ dataSync FGS quota auto-restart crash loop | bugfix | S | ✅ closed ([PR #312](https://github.com/khmelevartem/tether/pull/312)) |
| 5 | [#164](https://github.com/khmelevartem/tether/issues/164) | Application-layer keepalive на активной передаче FileServer | feature | M | ⏭ перенесена в бэклог; забирать в sprint-11+ после стабилизации pairing и sender-волны |
| 6 | [#176](https://github.com/khmelevartem/tether/issues/176) | Hotspot transfer Phase A: `/hello` rendezvous + Desktop multi-interface mDNS | feature | M | ✅ closed ([PR #316](https://github.com/khmelevartem/tether/pull/316)) |
| 7 | [#173](https://github.com/khmelevartem/tether/issues/173) | Trim docs/product/tech-stack.md — убрать ADR-контент, оставить product summary | docs | S | ✅ closed ([PR #313](https://github.com/khmelevartem/tether/pull/313)) |

**Итог:** 6/7 задач закрыты, цели спринта по transfer UI + reliability достигнуты; app-layer keepalive (#164) сдвинута на следующий цикл.

## Следствия

- После #191 разблокирована sender-wiring волна #192 / #193 / #194 и receiver UI #195 — все они становятся тонкими потребителями TransferScreen и dialogs.
- После #59 + #310 Android FGS на API 35+ ведёт себя предсказуемо: `dataSync` оставлен с задокументированным 6h/24h cap, auto-restart crash loop при срабатывании квоты пойман и больше не засоряет краш-аналитику.
- После #251 `TrustedDeviceStore` уехал в commonMain, `expect/actual` персистентности больше нет; ADR adr-persistence-key-value полностью реализован.
- После #176 работает Desktop-host hotspot и асимметричная видимость в home Wi-Fi через `/hello` rendezvous; разблокируется Phase B (Android-host hotspot, fallback layers, #177).
- App-layer keepalive (#164) не сделана — последняя дыра screen-off transfer остаётся открытой. Возвращаемся к ней отдельно, после pairing-эпика.

## Дополнительные результаты

Закрыто в окне спринта вне исходного состава (только задачи с реально мерджнутым PR):

- [#319](https://github.com/khmelevartem/tether/issues/319) ([PR #325](https://github.com/khmelevartem/tether/pull/325)) — extract per-peer transfer state machine into `PeerTransferEngine` + retain via `PeerTransferEngineRegistry`. Заодно поглотил #318 (`OutboundTransferRepository` оказался лишним слоем поверх engine'а). Разблокировал #321 и #328.
- [#309](https://github.com/khmelevartem/tether/issues/309) ([PR #324](https://github.com/khmelevartem/tether/pull/324) + cleanup [PR #335](https://github.com/khmelevartem/tether/pull/335)) — auto-send to sole online paired peer on share-sheet entry. Достроена AC #5 transfer-фичи.
- [#314](https://github.com/khmelevartem/tether/issues/314) ([PR #323](https://github.com/khmelevartem/tether/pull/323)) — validate doc links and anchors with lychee. Документация защищена от drift.
- [#305](https://github.com/khmelevartem/tether/issues/305) ([PR #306](https://github.com/khmelevartem/tether/pull/306)) — close sprint-08, plan sprint-09 (этот же sprint planning).

## Порядок мерджа

#251 → #59 → #310 → #164 → #176 → #173 → #191

#251 и #59 — изолированные, без UI. #310 идёт после #59 (fix поверх выбранного FGS-типа). #164 и #176 — network-слой, не пересекаются с UI #191; между собой не конфликтуют. #173 — docs-only. #191 последним — самый широкий diff по presentation-слою, любой merge после него — тяжёлый rebase.
