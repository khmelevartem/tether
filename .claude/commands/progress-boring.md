Снимок прогресса проекта: инфра/фичи по PR + готовность MVP по roadmap.

## Что собрать

1. **PR-статистика через GraphQL.** Один запрос `gh api graphql` с пагинацией по `repository.pullRequests` — за раз `number, title, state, createdAt, mergedAt, additions, deletions, changedFiles, commits.totalCount, comments.totalCount, reviews.totalCount, reviewThreads.totalCount`. REST list endpoint `/pulls` не возвращает `commits`/`comments`/`review_comments` per PR — нужен GraphQL или per-PR detail call.

2. **MVP-скоуп.** Прочитай `docs/product/roadmap.md` (секция `## MVP`) — список MVP-пунктов. Прочитай `docs/product/features/README.md` — статусы фичей и связанные issues. Прочитай последний `docs/sprints/sprint-*.md` (по большему номеру) — что в работе сейчас.

## Категоризация PR

**Feature** — PR трогает продуктовый код или заполняет/правит продуктовую спеку:
- `composeApp/src/**` (кроме `build.gradle.kts`-only), реализация discovery / FileServer / pairing / UI;
- `docs/product/features/**` — заполнение спек фичей;
- `docs/product/vision.md`, `roadmap.md`, `security.md`, `monetization.md` — продуктовое содержание.

**Infra** — всё остальное:
- `.claude/**` (скиллы, агенты, hooks, commands);
- `docs/engineering/**`, ADR'ы;
- ретро-PR (заголовок начинается с `retro`);
- sprint planning, `docs/sprints/**`;
- Gradle build, CI, конфиги;
- чистые рефакторы без user-visible изменений.

Если PR смешанный — отнеси по доминанте (>50% diff). Сомневаешься — спроси у пользователя один раз для всего батча.

## MVP-готовность

Для каждого пункта из `roadmap.md ## MVP` оцени готовность (0–100%) по доказательствам:

- 100% — feature имеет статус `done` в `features/README.md` И есть смерженные PR, покрывающие все платформы.
- 50–80% — частично: либо protocol-слой без UI, либо платформ-парность неполная (например, Android+iOS done, Desktop tbd).
- 10–30% — только спека `scoped`, кода нет; или только один из нескольких компонентов.
- 0% — ни кода, ни спеки в статусе ≥`scoped`.

Не угадывай — каждую оценку обоснуй ссылкой на PR номера и/или строки в `features/README.md`. Если по доказательствам не ясно — отметь `?` и спроси.

## Вывод

Один HTML-файл `/tmp/tether-progress.html` (видим в Launch preview panel), содержащий:

1. **KPI-плитка сверху**: всего смержено PR · % feature / % infra · % MVP (средневзвешенно, равные веса по пунктам) · активные дни · текущий спринт + его номер.
2. **Stacked bar по неделям** (feature vs infra) — динамика соотношения.
3. **Bubble scatter** PR: x=commits, y=comments, размер=LOC, цвет=категория. Подпись «правый верх — тяжёлые PR».
4. **Таблица MVP** (7 строк): пункт roadmap | статус фичи | % готовности | доказательства (PR-номера, ссылка на спеку).
5. **Топ-5 тяжёлых PR** (по `commits + comments`).
6. **Блок «что дальше»**: что в работе в текущем спринте (issues), что блокирует MVP.

Стиль — тёмная тема, Chart.js через CDN.

После сборки HTML — короткий текстовый дайджест в чат (5–7 строк), без повторения цифр из таблицы:
- одна фраза про соотношение и тренд по неделям;
- общий процент MVP + какие 2-3 пункта тянут вниз;
- 1-2 наблюдения, которых не видно по цифрам (например, «весь UI-слой ещё впереди», «спринт 4 снимает архитектурные блокеры»).

## Чего не делать

- Не запускай Gradle, тесты, smoke — это чистая аналитика.
- Не пиши markdown-отчёт в `docs/` — это эфемерный снимок, живёт в `/tmp/`.
- Не категоризируй вручную больше 10 PR без оптимизации — если их много, напиши короткий Python-скрипт.
