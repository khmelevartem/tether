## Цель спринта

Прицельный заход на file-transfer: общий UI-слой передачи (TransferScreen, dialogs, PeerCard expansion) поверх state-machine из #190, плюс закрытие двух concurrent-upload рейсов в FileServer. После спринта появляется видимый transfer-флоу на всех платформах, а receiver больше не теряет данные при параллельных upload'ах в одну папку.

## Состав

| # | Issue | Название | Тип | Размер | Итог |
| - | ----- | -------- | --- | ------ | ---- |
| 1 | [#191](https://github.com/khmelevartem/tether/issues/191) | Transfer UI: TransferScreen, dialogs и DeviceList pending-banner с превью | feature | L | 🟡 в работе, закрывается внутри окна sprint-08 |
| 2 | [#258](https://github.com/khmelevartem/tether/issues/258) | FileServer: closed concurrent-upload races (silent overwrite + abort vs concurrent write) | bug | M | ✅ закрыт ([a9a281f](https://github.com/khmelevartem/tether/commit/a9a281f), [PR #282](https://github.com/khmelevartem/tether/pull/282)) |
| 3 | [#244](https://github.com/khmelevartem/tether/issues/244) | Визуальное воплощение BrandMark — от скелета к дизайну | feature | M | 🟡 декомпозирован: spec расчищен ([2fcd035](https://github.com/khmelevartem/tether/commit/2fcd035), [PR #288](https://github.com/khmelevartem/tether/pull/288)), визуальная реализация выделена в [#287](https://github.com/khmelevartem/tether/issues/287) |
| 4 | [#274](https://github.com/khmelevartem/tether/issues/274) | Промоутить command→skill: close-issue, retro, check-review, sprint-pick | infra | M | ✅ закрыт ([6ff8e00](https://github.com/khmelevartem/tether/commit/6ff8e00), [PR #280](https://github.com/khmelevartem/tether/pull/280); follow-up [5564df4](https://github.com/khmelevartem/tether/commit/5564df4), [PR #281](https://github.com/khmelevartem/tether/pull/281)) |
| 5 | [#247](https://github.com/khmelevartem/tether/issues/247) | Translate all docs and .claude/ content to English | infra | L | ✅ закрыт ([1ffddee](https://github.com/khmelevartem/tether/commit/1ffddee)) |

**Итого:** 4/5 закрыты внутри окна, #244 раздвоен (spec в этом спринте, визуал в #287).

## Дополнительные результаты

Закрыты в окне спринта вне исходного состава:

- [#295](https://github.com/khmelevartem/tether/issues/295) — выбран Compose Unstyled как библиотека bottom-sheet/modal примитивов ([957ff36](https://github.com/khmelevartem/tether/commit/957ff36), [PR #301](https://github.com/khmelevartem/tether/pull/301)). Анблокирует диалоги в #191.
- [#299](https://github.com/khmelevartem/tether/issues/299) — `/progress` HTML-генератор вынесен в переиспользуемый build.py ([58eac1c](https://github.com/khmelevartem/tether/commit/58eac1c), [PR #300](https://github.com/khmelevartem/tether/pull/300)).
- [#296](https://github.com/khmelevartem/tether/issues/296) — ужаты architect и review-architecture агенты ([e0b22b5](https://github.com/khmelevartem/tether/commit/e0b22b5), [PR #298](https://github.com/khmelevartem/tether/pull/298)).
- [#293](https://github.com/khmelevartem/tether/issues/293) — `/github-issue-author` и `/quick-issue` объединены в один скилл с более тонкими issue-bodies ([debe2e3](https://github.com/khmelevartem/tether/commit/debe2e3), [PR #294](https://github.com/khmelevartem/tether/pull/294)).
- [#17](https://github.com/khmelevartem/tether/issues/17) — три инфраструктурных коммита в зонтичном issue: orphaned tester agent удалён + design-rules сведены в testing.md ([514b053](https://github.com/khmelevartem/tether/commit/514b053), [PR #302](https://github.com/khmelevartem/tether/pull/302)); review-glossary пропускает industry-standard терминологию ([6d24a78](https://github.com/khmelevartem/tether/commit/6d24a78), [PR #297](https://github.com/khmelevartem/tether/pull/297)); commit/PR title format закреплён hook'ом + CLAUDE.md ([57ce45d](https://github.com/khmelevartem/tether/commit/57ce45d)).

## Следствия

- После #191 разблокируется sender-wiring волна #192 / #193 / #194 и receiver UI #195 — все они становятся тонкими потребителями экранов и dialogs из этого спринта.
- После #258 FileServer пригоден для конкурентных folder-send нагрузок: исчезает silent overwrite и abort-vs-write race, появляются тесты на параллельные upload'ы.
- После #274 четыре multi-step процедуры (close-issue, retro, check-review, sprint-pick) запускаются по фразе-триггеру, литеральный slash остаётся как fallback.
- После #247 долгоживущие артефакты унифицированы по языку; русский остаётся только в чате, что снижает трение при работе агентов и моделей.

## Порядок мерджа

#258 → #244 → #191 → #274 → #247

#258 первым — изолированный backend-фикс. #244 правит BrandMark composable, который потребляет #191 → удобнее зайти раньше. #191 несёт основную UI-массу. #274 двигает файлы внутри `.claude/` — лучше до массового перевода. #247 последним: затрагивает почти все долгоживущие `.md`, любой merge после него — тяжёлый rebase.
