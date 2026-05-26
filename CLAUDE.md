# CLAUDE.md

Guidance for Claude Code working in this repo. **Short by design — load deeper docs on demand.**

## What is Tether

KMP file transfer app: Android, iOS, Desktop (JVM — Windows / Linux / macOS). P2P via mDNS discovery + Ktor file server. Human overview — [README.md](README.md).

## Documentation map

Read on demand. Match what you're touching to the corresponding canon:

- **Vision / product framing** — [`docs/product/`](docs/product/README.md). Source of truth for *what* / *why*.
- **Engineering architecture** — [`docs/engineering/`](docs/engineering/README.md). Source of truth for *how*. Per-area:
  - Any code → [`dependency-injection.md`](docs/engineering/dependency-injection.md)
  - UI → [`presentation-layer.md`](docs/engineering/presentation-layer.md)
  - New module / component → [`modules.md`](docs/engineering/modules.md) + [`architecture-principles.md`](docs/engineering/architecture-principles.md)
  - Tests → [`testing.md`](docs/engineering/testing.md)
- **Feature specs / UX briefs** — `docs/product/features/<slug>/{spec,ux-brief}.md`. New / updated: copy structure from [`_template.md`](docs/product/features/_template.md) and [`_ux-brief-template.md`](docs/product/features/_ux-brief-template.md).
- **Solved problems / platform quirks** — [`docs/knowledge/`](docs/knowledge/). Check first when something looks weird before debugging from scratch.
- **Domain terminology** — [`docs/glossary.md`](docs/glossary.md); `review-glossary` blocks drift in PRs.

## Architecture invariants

- **Common-first.** Всё, что может жить в `commonMain` — там и лежит. Платформенные source sets (`androidMain`, `appleMain`, `jvmMain`, `desktopMain`, `iosMain`) — только для кода, требующего platform API. При выборе между `expect/actual` в `commonMain` и копированием в `platformMain` — `expect/actual`.
- **Source set hierarchy и Desktop UI/CLI split** — см. [`modules.md`](docs/engineering/modules.md).

## Git conventions

All git naming in English. **Все commit messages обязаны начинаться с номера issue:** `#<issue>: <message>` (например, `#42: add mDNS discovery for Android`).

Перед коммитом убедись, что issue существует. Если нет — попроси пользователя создать.

Подтянуть main в ветку — `/rebase` (ребейзит на свежий main и показывает, что заехало). Запускается и в `/close-issue`, и mid-flight.

## Common commands

Все Gradle команды запускай с `-q`. KtLint вручную не запускай — git hook делает это сам при коммите, стилевые ошибки тоже не правь руками.

Полный список команд по платформам — [README.md](README.md). Тестовые команды — [`testing.md`](docs/engineering/testing.md). Параллельный запуск всех таргетов — `scripts/run-all.sh`.

## Slash commands и скиллы

Индекс и правила выбора (skill vs command) — [`.claude/README.md`](.claude/README.md).

## Code style

- **Минимум комментариев.** Перед тем как добавить — попробуй вынести блок в приватный метод: имя метода часто делает комментарий ненужным. Комментарий — только там, где код не может выразить намерение (намеренно проглоченное исключение, неочевидный инвариант внешней библиотеки).
- **KDoc vs `//`.** KDoc — только для контрактов (nullable-семантика, неочевидные пред-/постусловия, неочевидное WHY). Не пересказывай имя метода или сигнатуру — это шум. Если KDoc не добавляет информации относительно кода — сноси его.
- **Kotlin official style** (enforced by KtLint).
- **Дисциплина долгоживущих артефактов** (CLAUDE.md, `docs/`, `.claude/`, KDoc, inline-комментарии) — [`long-lived-artifacts.md`](docs/engineering/long-lived-artifacts.md).
