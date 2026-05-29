## Цель спринта

Дозакрыть transfer UI-полотно (#191 carryover) и параллельно поднять reliability-слой перед MVP: Android FGS-тип, app-layer keepalive, hotspot Phase A. Sender wiring и receiver UI ждут окончания #191 — едут следующим спринтом.

## Состав

| # | Issue | Название | Тип | Размер |
| - | ----- | -------- | --- | ------ |
| 1 | [#191](https://github.com/khmelevartem/tether/issues/191) | Transfer UI: TransferScreen, dialogs и DeviceList pending-banner с превью | feature | L |
| 2 | [#59](https://github.com/khmelevartem/tether/issues/59) | Android FGS: оценить лимит dataSync 6h/сутки на Android 15+ и выбрать тип сервиса | infra | M |
| 3 | [#251](https://github.com/khmelevartem/tether/issues/251) | TrustedDeviceStore: migrate to DataStore backend in commonMain | enhancement | M |
| 4 | [#310](https://github.com/khmelevartem/tether/issues/310) | Survive Android 15+ dataSync FGS quota auto-restart crash loop | bugfix | S |
| 5 | [#164](https://github.com/khmelevartem/tether/issues/164) | Application-layer keepalive на активной передаче FileServer | feature | M |
| 6 | [#176](https://github.com/khmelevartem/tether/issues/176) | Hotspot transfer Phase A: `/hello` rendezvous + Desktop multi-interface mDNS | feature | M |
| 7 | [#173](https://github.com/khmelevartem/tether/issues/173) | Trim docs/product/tech-stack.md — убрать ADR-контент, оставить product summary | docs | S |

## Следствия

- После #191 разблокируется sender-wiring волна #192 / #193 / #194 и receiver UI #195 — все они становятся тонкими потребителями TransferScreen и dialogs.
- После #59 + #310 Android FGS на API 35+ ведёт себя предсказуемо: либо `dataSync` подтверждён под типичную нагрузку, либо выбран `connectedDevice` / `specialUse`; auto-restart crash loop при срабатывании 6h-квоты больше не засоряет краш-аналитику.
- После #251 `TrustedDeviceStore` уезжает в commonMain, `expect/actual` персистентности больше нет; ADR adr-persistence-key-value полностью реализован.
- После #164 закрыта последняя дыра screen-off transfer: app-layer keepalive держит NAT idle и Wi-Fi power-save под контролем, watchdog корректно отменяет передачу при потере встречной стороны.
- После #176 работает Desktop-host hotspot и асимметричная видимость в home Wi-Fi; разблокируется Phase B (Android-host hotspot, fallback layers).

## Порядок мерджа

#251 → #59 → #310 → #164 → #176 → #173 → #191

#251 и #59 — изолированные, без UI. #310 идёт после #59 (fix поверх выбранного FGS-типа). #164 и #176 — network-слой, не пересекаются с UI #191; между собой не конфликтуют. #173 — docs-only. #191 последним — самый широкий diff по presentation-слою, любой merge после него — тяжёлый rebase.
