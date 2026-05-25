# CLAUDE.md

Guidance for Claude Code working in this repo. **Short by design — load deeper docs on demand.**

## What is Tether

KMP file transfer app: Android, iOS, Desktop (JVM — Windows / Linux / macOS). P2P via mDNS discovery + Ktor file server. Human overview — [README.md](README.md).

## Documentation map

Read on demand, not all upfront:

- [`docs/product/`](docs/product/README.md) — vision, audience, features, roadmap. Source of truth for *what* / *why*.
- [`docs/engineering/`](docs/engineering/README.md) — architecture, modules, DI, presentation, testing. Source of truth for *how*.
- [`docs/knowledge/`](docs/knowledge/) — solved problems (Apple platform quirks, FGS, etc.). Check here when something looks weird before debugging from scratch.

After writing code / UI / tests / specs / UX briefs, read the topically-matching `docs/engineering/*.md` or `docs/product/features/_template.md` before marking done.

## Architecture invariants

- **Common-first.** Всё, что может жить в `commonMain` — там и лежит. Платформенные source sets (`androidMain`, `appleMain`, `jvmMain`, `desktopMain`, `iosMain`) — только для кода, требующего platform API. При выборе между `expect/actual` в `commonMain` и копированием в `platformMain` — `expect/actual`.
- **Source set hierarchy.** `jvmMain` — общий родитель для `androidMain` и `desktopMain` (через `applyHierarchyTemplate` в `build.gradle.kts`). `appleMain` — родитель для `iosMain` (iOS — единственный native-таргет; macOS ships как Desktop JVM .app, см. [`adr-macos-native-vs-jvm.md`](docs/engineering/adr/adr-macos-native-vs-jvm.md)). Desktop UI vs CLI split — см. [`modules.md` §Desktop split](docs/engineering/modules.md#desktop-split-ui-vs-cli).

## Git conventions

All git naming in English. **Все commit messages обязаны начинаться с номера issue:** `#<issue>: <message>` (например, `#42: add mDNS discovery for Android`).

Перед коммитом убедись, что issue существует. Если нет — попроси пользователя создать.

## Common commands

Все Gradle команды запускай с `-q`. KtLint вручную не запускай — git hook делает это сам при коммите, стилевые ошибки тоже не правь руками.

Полный список команд по платформам — [README.md](README.md). Тестовые команды — [`testing.md`](docs/engineering/testing.md). Параллельный запуск всех таргетов — `scripts/run-all.sh`.

## Slash commands и скиллы

`.claude/skills/` — multi-agent оркестрация (`/implement`, `/document`, `/code-review`, `/grooming`, `/smoke-test`). `.claude/commands/` — простые промпт-шаблоны (`/close-issue`, `/check-review`, `/sprint-pick`, `/retro`, `/quick-issue`). Когда новый артефакт — скилл, а когда команда — см. [`.claude/skills/_authoring.md`](.claude/skills/_authoring.md).

## Code style

- **Минимум комментариев.** Перед тем как добавить — попробуй вынести блок в приватный метод: имя метода часто делает комментарий ненужным. Комментарий — только там, где код не может выразить намерение (намеренно проглоченное исключение, неочевидный инвариант внешней библиотеки).
- **KDoc vs `//`.** KDoc — только для контрактов (nullable-семантика, неочевидные пред-/постусловия, неочевидное WHY). Не пересказывай имя метода или сигнатуру — это шум. Если KDoc не добавляет информации относительно кода — сноси его.
- **Kotlin official style** (enforced by KtLint).
- **Дисциплина долгоживущих артефактов** (CLAUDE.md, `docs/`, `.claude/`, KDoc, inline-комментарии) — [`long-lived-artifacts.md`](docs/engineering/long-lived-artifacts.md).
