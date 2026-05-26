## Цель спринта

Прицельный заход на file-transfer: общий UI-слой передачи (TransferScreen, dialogs, PeerCard expansion) поверх state-machine из #190, плюс закрытие двух concurrent-upload рейсов в FileServer. После спринта появляется видимый transfer-флоу на всех платформах, а receiver больше не теряет данные при параллельных upload'ах в одну папку.

## Состав

| # | Issue | Название | Тип | Размер |
| - | ----- | -------- | --- | ------ |
| 1 | [#191](https://github.com/khmelevartem/tether/issues/191) | Transfer UI: TransferScreen, dialogs и DeviceList pending-banner с превью | feature | L |
| 2 | [#258](https://github.com/khmelevartem/tether/issues/258) | FileServer: closed concurrent-upload races (silent overwrite + abort vs concurrent write) | bug | M |
| 3 | [#244](https://github.com/khmelevartem/tether/issues/244) | Визуальное воплощение BrandMark — от скелета к дизайну | feature | M |
| 4 | [#274](https://github.com/khmelevartem/tether/issues/274) | Промоутить command→skill: close-issue, retro, check-review, sprint-pick | infra | M |
| 5 | [#247](https://github.com/khmelevartem/tether/issues/247) | Translate all docs and .claude/ content to English | infra | L |

## Следствия

- После #191 разблокируется sender-wiring волна #192 / #193 / #194 и receiver UI #195 — все они становятся тонкими потребителями экранов и dialogs из этого спринта.
- После #258 FileServer пригоден для конкурентных folder-send нагрузок: исчезает silent overwrite и abort-vs-write race, появляются тесты на параллельные upload'ы.
- После #274 четыре multi-step процедуры (close-issue, retro, check-review, sprint-pick) запускаются по фразе-триггеру, литеральный slash остаётся как fallback.
- После #247 долгоживущие артефакты унифицированы по языку; русский остаётся только в чате, что снижает трение при работе агентов и моделей.

## Порядок мерджа

#258 → #244 → #191 → #274 → #247

#258 первым — изолированный backend-фикс. #244 правит BrandMark composable, который потребляет #191 → удобнее зайти раньше. #191 несёт основную UI-массу. #274 двигает файлы внутри `.claude/` — лучше до массового перевода. #247 последним: затрагивает почти все долгоживущие `.md`, любой merge после него — тяжёлый rebase.
