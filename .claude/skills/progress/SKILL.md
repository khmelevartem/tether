Снимок прогресса проекта в виде RPG-листа персонажа. Tether — это не проект, это сага. Пользователь — Драконорождённый разработчик. Roadmap — главная сюжетная линия, инфра — побочки и крафт, MVP — финальный данж.

Тон серьёзно-эпический с лёгкой самоиронией. Перевод цифр в RPG-язык обязателен в нарративных блоках; чистые статистические таблицы (Жаркие Сражения, Тяжёлые Походы) — голые цифры допустимы.

## Assets

Фиксированные словари и палитра рядом в `assets/`. Никаких имён, ключевых слов или цветов сверх того, что там лежит — это обеспечивает сравнимость снапшотов между запусками. Изменения в палитре, школах и локациях — через правку JSON-файлов, не инструкции.

- [`assets/schools.json`](assets/schools.json) — 7 кластеров для графа зависимостей, имена и `keywords` для классификации.
- [`assets/locations.json`](assets/locations.json) — 8 source set'ов с атмосферными именами и lore.
- [`assets/keywords.json`](assets/keywords.json) — title-эвристики feature vs infra.
- [`assets/classes.json`](assets/classes.json) — правила выбора класса героя по доминанте PR.
- [`assets/palette.json`](assets/palette.json) — цвета, шрифты, стили рамок артефактов по rarity.

## Что собрать (сырые данные)

1. **PR-статистика через GraphQL.** Один запрос `gh api graphql` с пагинацией по `repository.pullRequests` — за раз получи `number, title, state, createdAt, mergedAt, additions, deletions, changedFiles, commits.totalCount, comments.totalCount, reviews.totalCount, reviewThreads.totalCount`. REST list endpoint (`/pulls`) не возвращает commits/comments/review_threads — нужен GraphQL или per-PR detail call.

2. **Issues.** `gh issue list --state all --limit 500 --json number,title,state,labels,createdAt,closedAt` плюс `gh api 'repos/<owner>/<repo>/issues?state=all&per_page=100' --paginate` — нужно ради `parent_issue_url` и `issue_dependencies_summary`.

3. **Зависимости issue.** Для тех, у кого `issue_dependencies_summary.total_blocked_by > 0` — `gh api 'repos/<owner>/<repo>/issues/<N>/dependencies/blocked_by'`. REST endpoint; `addIssueDependency` mutation в GraphQL у GitHub не существует (это в памяти проекта). Parent — из `parent_issue_url` основного endpoint'а.

4. **MVP-скоуп.** `docs/product/roadmap.md` секция `## MVP`. `docs/product/features/README.md` — статусы фичей. Последний `docs/sprints/sprint-*.md` — активный спринт.

5. **LOC по локациям.** `git ls-files 'composeApp/src/<sourceSet>/'` + подсчёт по `.kt`/`.swift`. Source set'ы в `assets/locations.json`.

6. **Спринт-планы.** Парсинг секции `## Состав` каждого `docs/sprints/sprint-*.md` (regex `## Состав .. (?=^## |\Z)`), извлечь `#N` в эту секцию — множество запланированных задач.

7. **Cutoff для Печати Долга.** `git log --diff-filter=A --follow --format='%aI' -- docs/sprints/sprint-01.md | tail -1` — дата заведения первого спринт-плана. Issues, созданные раньше, в Печати Долга не учитываются.

## Правила расчёта

### Класс персонажа
Один по правилам из `assets/classes.json` (порядковое сопоставление, первое сработавшее). Обоснуй одной строкой («каждый пятый PR — ретро»).

### Уровень и XP
`Level = floor(sqrt(2·merged_PRs + closed_issues))`. XP-бар:
- `xp_total = 2·merged_PRs + closed_issues` (та же формула что у уровня — иначе бар уходит в отрицательные значения)
- `xp_in_level = xp_total − level²`
- `xp_needed = (level+1)² − level²`

### Категоризация PR / issue
По `assets/keywords.json`. Используется в Хронике Подвигов, Печати Долга, классе персонажа.

### MVP (Главный Сюжет)
7 глав из roadmap с эпическими подзаголовками («Глава II: Печать Доверия — четыре руны связывают двоих»). Каждой — % готовности по доказательствам:
- 100% — feature `done` + смерженные PR по всем платформам.
- 50–80% — частично (один из слоёв / часть платформ).
- 20–40% — только spec scoped.
- 5% — ни кода, ни решения.

Прогресс-бары под статусом, палитра золото→золото-pale (закрытые) и серый (не начатые).

### Артефакты (топ-5 PR)
Вес: `commits·2 + comments + review_threads·3 + (additions+deletions)/200`. Топ-5 с rarity-цветами рамок по `assets/palette.json#artifact_rarity`. Внутри карточки три строки: коммиты, обсуждения (`comments + review_threads`), `+/−` строк.

### Жаркие Сражения / Тяжёлые Походы
- **Жаркие** — топ-5 PR по `comments + review_threads`.
- **Тяжёлые** — топ-5 PR по `additions + deletions`.

Таблицы `#PR | название | значение`, моноширинные цифры справа. Без RPG-перевода в значениях — чистая статистика.

### Печать Долга — план vs импровизация
**Cutoff:** учитываем только issues, созданные **после** даты заведения `sprint-01.md` в git. Ретро-PR не учитываются (идут без отдельного issue).

- `planned ∩ closed` после cutoff — «По свитку спринтов»
- `closed − planned` после cutoff — «Случайные встречи»

Три цифры + двуцветная stacked-полоска (золото ↔ пурпур) + последние 6 в каждой категории.

### Карта Заданий — граф зависимостей
Force-directed граф через D3 v7 с кластеризацией по школам из `assets/schools.json`.

**Layout:**
- Якоря школ на радиальной окружности `R = min(W,H)·0.30` равноудалены по углу.
- Подписи школ на внешнем кольце `R_LBL = min(W·0.46, H·0.48)` с динамическим `text-anchor` по углу (cos<−0.3 → end, cos>0.3 → start, иначе middle).
- Сила притяжения узла к якорю своего кластера: `0.14`.
- Charge `-50`, link distance `38`, collide `13`, alphaDecay `.025`.
- Координаты узлов клампятся в `[PAD, W-PAD]` × `[PAD, H-PAD]` каждый tick.

**Цвета** — `assets/palette.json#graph_nodes` и `#graph_edges`. Сирый узел = ни parent, ни blocked_by, ни blocks (полная изоляция). В цепях = open AND хотя бы один открытый `blocked_by` предок.

**Интерактив:**
- Drag узлов (D3 drag behavior, fx/fy фиксируются на время).
- Scroll wheel / drag-by-empty-space → pan+zoom через `d3.zoom().scaleExtent([0.3, 4])`. Filter: не зумить когда курсор над узлом (иначе конфликтует с node drag).
- Кнопки `+ / − / ⤺` для зума через `zoom.scaleBy` / `zoom.transform(zoomIdentity)`.

**Тултип** — HTML div absolutely positioned внутри graph-box, появляется на `mouseenter` с opacity transition. Содержит: `#N`, полный title, кластер, статус (`открыт`/`закрыт`, `в цепях`/`сирый`).

**Подписи узлов** в `JetBrains Mono 9px` на отдельном top-слое с `paint-order:stroke; stroke:<card_bg>; stroke-width:3px` — halo сохраняет читаемость при наложении на соседние узлы и рёбра.

**Сводка над графом** — 3 карточки: свободные открытые, в цепях, сирые открытые. Числа в соответствующих цветах из палитры.

**Под графом** — две легенды: цвета узлов/рёбер; описание школ (имя + `summary` из `schools.json`).

### Хроника Подвигов
Chart.js area chart по неделям, две заливки (feature золото, infra тёмный янтарь `gold.dim`) + пунктирная кровавая линия «доля feature, %» по правой Y-оси (0–100).

### Книга Знаний
Внизу страницы — раздел с формулами и расшифровками: уровень+XP, вес артефакта, как определяется класс, прогресс MVP-глав, Жаркие/Тяжёлые. Двухколоночный layout в Cinzel-золоте.

## Вывод

HTML `/tmp/tether-progress.html`. Палитра, шрифты, рамки — из `assets/palette.json`. Декоративные рамки секций — `border: 2px double <gold.dim>` с псевдо-элементами-орнаментами (`❧` в углах).

Подключи через CDN:
- `https://cdn.jsdelivr.net/npm/chart.js` — area/bar/doughnut.
- `https://cdn.jsdelivr.net/npm/d3@7` — force-граф.

Шрифты через Google Fonts (Cinzel, IM Fell English SC, JetBrains Mono).

### Структура страницы (сверху вниз)

1. **Header banner** — «Tether Saga» / «Хроники Драконорождённого Разработчика» / дата.
2. **Лист Персонажа** — Класс/Уровень/XP слева, Хроника пути справа (артефакты, свитки, дни, обсуждения, самое жаркое, текущая глава).
3. **Главный Сюжет — Хроника MVP** — таблица 7 глав с прогресс-барами.
4. **Открытые Локации** — карточки + LOC bar-chart, отсортированный по убыванию.
5. **Легендарные Артефакты** — top-5 PR с rarity-рамками.
6. **Жаркие Сражения / Тяжёлые Походы** — две таблицы статистики PR.
7. **Текущая Глава — Спринт N** — название + список квестов.
8. **Карта Заданий** — сводка + D3-граф + легенды (цвета и школы).
9. **Печать Долга** — план vs импровизация с фильтром по дате.
10. **Хроника Подвигов** + **Размах Артефактов** (two-col) — area chart недель + doughnut размеров PR.
11. **Книга Знаний** — формулы.

### Дайджест в чат

После HTML — выведи **запись в дневнике искателя приключений**, 6–10 строк от первого лица:

> *«Двадцать третий день месяца Утренней Звезды. Прошёл ещё одну веху...»*

Внутри:
- одна строка про класс и уровень;
- что выросло сильнее всего по тренду;
- какая глава главного квеста ближе всего к завершению, какая буксует;
- одно тёмное предзнаменование (блокер) или вызов впереди;
- финальная строка «впереди — <следующий MVP-пункт>. Пусть Восемь хранят сборку».

Запрещено в нарративных блоках: голые проценты без RPG-обёртки, «KPI», «velocity», «throughput», смайлы 😀, эмодзи флагов. Разрешено: ✦ ✧ ⚔ ☠ ❧ ◈ — сдержанно.

## Чего не делать

- Не запускай Gradle, тесты, smoke — чистая аналитика.
- Не пиши отчёт в `docs/` — снапшот живёт в `/tmp/`.
- Не категоризируй вручную >10 PR — пиши Python-скрипт в `/tmp/build_progress.py`.
- Не оценивай локацию если LOC = 0 — пиши «не открыто», скрывай из диаграммы.
- Не выдумывай школы/локации/классы/цвета сверх перечисленных в `assets/` — фиксированная палитра обеспечивает сравнимость снапшотов между запусками. Нужна новая школа — добавь её в `schools.json`, не в инструкцию.
- Не используй REST `/pulls` list endpoint для PR-статистики — он не возвращает commits/comments/review_comments. Только GraphQL или per-PR detail.

## Когда нужна сухая версия — `/progress-boring`

Если нужны цифры без RPG-обёртки (для статус-репорта, ретро, копипасты в документ) — используй `/progress-boring` (`.claude/commands/progress-boring.md`). Тот же датасет, обычный dashboard.
