# Sprint 14 · Раскопки в Саартале

**Направления:** .claude framework · publication · receiver · discovery · UI

## Состав

| # | Issue | Название | Тип | Размер |
| - | ----- | -------- | --- | ------ |
| 1 | [#465](https://github.com/khmelevartem/tether/issues/465) | Project-context adapter: decouple framework from repo-specific paths and conventions | infra | L |
| 2 | [#479](https://github.com/khmelevartem/tether/issues/479) | Manual DI in KMP — article on Habr and Medium | docs | M |
| 3 | [#195](https://github.com/khmelevartem/tether/issues/195) | Receiver UI: FileServer events, PeerCard inbound states, платформенные уведомления | feature | M |
| 4 | [#326](https://github.com/khmelevartem/tether/issues/326) | Discovery: idle-expiry for /hello and fallback-channel peers | feature | S |
| 5 | [#222](https://github.com/khmelevartem/tether/issues/222) | Settings navigation surface — единый host для секций настроек на 4 платформах | feature | M |

## Что разблокирует

- После #465 размораживается эпик #464: #466 (упаковка плагина) встаёт на готовый адаптер, а за ним #467/#468 (видео и статья о фреймворке) — путь к внешней видимости фреймворка открыт.
- После #222 будущие settings-секции (#223 save location / large-selection, device-name rename, theme override) втыкаются в готовый host как self-contained sections, не таща за собой собственный route и back-handling.

## Порядок мерджа

#465 || #479 ; (#222 || #326) → #195

`||` — параллельные ветки. #465 (`.claude/`) и #479 (`docs/` + внешняя публикация) изолированы от всего остального и друг от друга. #195 — точка схождения: делит `FileServerRoutes.kt` с #326 (событийный поток на `/upload` vs `/hello`-upsert) и `RootComponent` + `DeviceListScreen` с #222 (inbound-стейты PeerCard vs settings-child и шестерёнка в top bar). Поэтому #195 мержится строго после #222 и #326; сами #222 и #326 друг от друга независимы и идут параллельно.
