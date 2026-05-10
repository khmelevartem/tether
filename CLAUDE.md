# CLAUDE.md

Guidance for Claude Code working in this repo. **Short by design — load deeper docs on demand.**

## What is Tether

KMP file transfer app: Android, iOS, macOS, Desktop (JVM). P2P via mDNS discovery + Ktor file server. Human overview — [README.md](README.md).

## Documentation map

Read these on demand, not all upfront:

- [`docs/product/`](docs/product/README.md) — vision, audience, features, roadmap. Source of truth for *what* / *why*.
- [`docs/engineering/`](docs/engineering/README.md) — architecture, modules, DI, presentation, testing. Source of truth for *how*.
- [`docs/knowledge/`](docs/knowledge/) — solved problems (Apple platform quirks, FGS, etc.). Check here when something looks weird before debugging from scratch.

**Before non-trivial implementation:** read [`docs/engineering/dependency-injection.md`](docs/engineering/dependency-injection.md) (DI checklist). For new components / layering decisions — also [`architecture-principles.md`](docs/engineering/architecture-principles.md) and [`modules.md`](docs/engineering/modules.md). For UI — [`presentation-layer.md`](docs/engineering/presentation-layer.md). For tests — [`testing.md`](docs/engineering/testing.md).

## Architecture invariants

- **Common-first.** Всё, что может жить в `commonMain` — там и лежит. Платформенные source sets (`androidMain`, `appleMain`, `jvmMain`, `desktopMain`, `iosMain`, `macosMain`) — только для кода, требующего platform API. При выборе между `expect/actual` в `commonMain` и копированием в `platformMain` — `expect/actual`.
- **Source set hierarchy:** `jvmMain` — общий родитель для `androidMain` и `desktopMain` (через `applyHierarchyTemplate` в `build.gradle.kts`). `appleMain` — общий для `iosMain` и `macosMain`.
- **macOS:** Apple Silicon (`macosArm64`) only.

## Git conventions

All git naming in English. **Все commit messages обязаны начинаться с номера issue:** `#<issue>: <message>`.

```
#42: add mDNS discovery for Android
```

Перед коммитом убедись, что issue существует. Если нет — попроси пользователя создать.

## Common commands

Все Gradle команды запускай с `-q` (убирает бойлерплейт).

```bash
./gradlew allTests -q                            # pre-commit / pre-push хуки прогонят это сами
./gradlew :composeApp:installJar -q && tether    # Desktop CLI (см. README.md → Desktop CLI)
./gradlew :composeApp:assembleDebug              # Android APK
```

**KtLint — никогда не запускай вручную.** Git hook делает это сам при коммите. Стилевые ошибки не правь руками — просто коммить.

Подробные команды запуска по платформам — в [README.md](README.md). Тестовые команды — в [`docs/engineering/testing.md`](docs/engineering/testing.md).

## Slash commands и скиллы

В `.claude/commands/` лежат рабочие команды (`/work-on-issue`, `/close-issue`, `/code-review`, `/check-review`, `/grooming`, `/retro`, `/quick-issue`).

`/smoke-test` — runtime happy-path по платформам (Desktop CLI, Desktop↔Desktop send, Android если adb подключён, native compile macosArm64/iosSimulatorArm64). Прогоняй когда сомневаешься в рантайме после нетривиальных правок в сетевой части / FileServer / mDNS / FGS, перед merge runtime-changing PR, и в `/close-issue`. Skip для DOCS-only / `.claude/`-only / comment-only изменений. Smoke не заменяет `allTests`.

## Code style

- **Минимум комментариев.** Перед тем как добавить — попробуй вынести блок в приватный метод: имя метода часто делает комментарий ненужным. Комментарий — только там, где код не может выразить намерение (намеренно проглоченное исключение, неочевидный инвариант внешней библиотеки).
- **KDoc vs `//`.** KDoc — только для контрактов (nullable-семантика, неочевидные пред-/постусловия, неочевидное WHY). Не пересказывай имя метода или сигнатуру — это шум. Если KDoc не добавляет информации относительно кода — сноси его.
- **Kotlin official style** (enforced by KtLint).

## Worktree и окружение

**Редактируй файлы только в worktree, не в корне репозитория.** Перед первым Edit убедись, что путь ведёт в `.claude/worktrees/<branch>/`, а не в корень. Ошибка в пути = правка main в обход ревью.

**Обновление инструкций** (CLAUDE.md и других файлов в `.claude/`): правь только в текущем worktree. Корневой CLAUDE.md живёт на main — туда изменения попадают через PR-флоу.

**Авто-очистка worktree.** При остановке сессии Claude Code `Stop` hook автоматически удаляет worktree'ы, для которых remote-ветка удалена и PR смержен (`gh pr list --state merged`). Работает для squash, regular и fast-forward merge.

**Проверяй `pwd` перед Bash-командами.** Bash-сессии не сохраняют `cd` между вызовами. Перед diagnostic-сессией (smoke, ручная проверка, сборка для тестирования) первой командой делай `pwd && git rev-parse --short HEAD`, чтобы убедиться что ты в worktree, а не в `/Users/artem/StudioProjects/tether` на main.
