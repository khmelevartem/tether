# CLAUDE.md

Guidance for Claude Code working in this repo. **Short by design — load deeper docs on demand.**

## What is Tether

KMP file transfer app: Android, iOS, macOS, Desktop (JVM). P2P via mDNS discovery + Ktor file server. Human overview — [README.md](README.md).

## Documentation map

Read these on demand, not all upfront:

- [`docs/product/`](docs/product/README.md) — vision, audience, features, roadmap. Source of truth for *what* / *why*.
- [`docs/engineering/`](docs/engineering/README.md) — architecture, modules, DI, presentation, testing. Source of truth for *how*.
- [`docs/knowledge/`](docs/knowledge/) — solved problems (Apple platform quirks, FGS, etc.). Check here when something looks weird before debugging from scratch.

**Self-check before marking done** — read by what you wrote:
- Any code → [`dependency-injection.md`](docs/engineering/dependency-injection.md)
- UI → [`presentation-layer.md`](docs/engineering/presentation-layer.md)
- New module/component → [`modules.md`](docs/engineering/modules.md) + [`architecture-principles.md`](docs/engineering/architecture-principles.md)
- Tests → [`testing.md`](docs/engineering/testing.md)
- New product spec or update of existing → [`_template.md`](docs/product/features/_template.md). Готовая соседняя спека показывает структуру, но не правила про содержание — шаблон открой отдельно.
- New UX brief or update of existing → [`_ux-brief-template.md`](docs/product/features/_ux-brief-template.md). Соседний готовый бриф показывает форму, но дисциплина (scope cohesion, cross-ref on move, без имён кода) — в шаблоне.

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
./gradlew :composeApp:installCli -q && tether   # Desktop CLI install (см. README.md → Desktop CLI)
./gradlew :composeApp:runDesktopCli -q           # Desktop CLI (dev runner)
./gradlew :composeApp:run -q                     # Desktop Compose UI (Compose plugin default)
./gradlew :composeApp:assembleDebug              # Android APK
```

Desktop исходники разделены на два source set: `desktopMain` — shared backend + Compose UI (Clikt недоступен), `desktopCli` — CLI точка входа (видит `desktopMain` через `associateWith`, Clikt только здесь). Desktop distribution (`packageReleaseDistributionForCurrentOS`) пакует UI. CLI устанавливается через `installCli`.

**KtLint — никогда не запускай вручную.** Git hook делает это сам при коммите. Стилевые ошибки не правь руками — просто коммить.

Подробные команды запуска по платформам — в [README.md](README.md). Тестовые команды — в [`docs/engineering/testing.md`](docs/engineering/testing.md).

## Slash commands и скиллы

**Скиллы** (`.claude/skills/`) — основной путь, multi-agent оркестрация:
- `/implement <N>` — end-to-end оркестратор задачи. Планирует, гоняет coder↔reviewers цикл, smoke, доводит до PR. Пользователь только в гейтах G1-G5 (см. SKILL.md). Идемпотентен: повторный вызов по issue с открытым PR — re-entry в inner loop + simplify + full review + smoke на свежем diff'е. Этот же вход используется для отработки ручного ревью на PR.
- `/code-review <PR>` — параллельный multi-agent review с постингом в GitHub.

**Команды** (`.claude/commands/`):
- `/close-issue`, `/check-review`, `/grooming`, `/retro`, `/quick-issue` — рабочий процесс вокруг issue/PR.
- `/work-on-issue` — **manual fallback** для `/implement`. Используется когда хочешь пройти процесс руками: отладка оркестратора, нестандартная семантика, эксперимент.

`/smoke-test` — runtime happy-path по платформам (Desktop CLI, Desktop↔Desktop send, Android если adb подключён, native compile macosArm64/iosSimulatorArm64). Прогоняй когда сомневаешься в рантайме после нетривиальных правок в сетевой части / FileServer / mDNS / FGS, перед merge runtime-changing PR, и в `/close-issue`. Skip для DOCS-only / `.claude/`-only / comment-only изменений. Smoke не заменяет `allTests`.

## Code style

- **Минимум комментариев.** Перед тем как добавить — попробуй вынести блок в приватный метод: имя метода часто делает комментарий ненужным. Комментарий — только там, где код не может выразить намерение (намеренно проглоченное исключение, неочевидный инвариант внешней библиотеки).
- **KDoc vs `//`.** KDoc — только для контрактов (nullable-семантика, неочевидные пред-/постусловия, неочевидное WHY). Не пересказывай имя метода или сигнатуру — это шум. Если KDoc не добавляет информации относительно кода — сноси его.
- **Долгоживущий артефакт — это правило, а не его история.** Доки, код, комментарии формулируют что есть / что делать / чему равно — без «после ретро по #N», «обнаружено при работе над X», «как обсудили в #Y», без примеров и обоснований из конкретной задачи, в которой артефакт родился. Контекст принятия решения живёт в git/PR, не в файле. Если правило непонятно без отсылки к инциденту — слабая формулировка, переписывай её, а не приклеивай хвост. Применимо ко всему: CLAUDE.md, `docs/`, `.claude/skills/**`, inline-комментарии в коде, тексты ошибок.
- **Inline-пример = форма истории, если он incident-rooted.** Соблазн «приложу пример, чтобы было понятнее» почти всегда означает приложить строку из задачи, родившей правило. Следующий агент матчит пример литерально и пропускает структурно идентичные соседние случаи. Правило: либо обобщай *форму* ошибки (контраст «принцип ловит вот это, но не похожее на него») так, чтобы пример был синтетическим и закрывал класс, — либо не приводи пример вовсе. Если без примера правило не читается — переписывай формулировку, а не подпирай её цитатой из инцидента.
- **Утверждения доков о runtime — снапшот, не правило.** Опираешься на такое утверждение в долгоживущем артефакте (спека, doc, комментарий, скилл) — сверь с кодом до использования, оно могло устареть. Пишешь сам — предпочти продуктовый инвариант («pairing keyed by stable device identity») описанию текущей реализации; если runtime упомянуть неизбежно — оставь минимум, нужный для понимания.
- **Kotlin official style** (enforced by KtLint).
- **Reflection в 3rd-party internals запрещена по умолчанию.** Доступ к private/internal полям сторонних библиотек (kotlinx, Ktor, system services, etc.) через `getDeclaredField` / `isAccessible` / classpath lookup — architectural escape hatch, не routine technique. Любая фича, требующая такой рефлексии, обязана быть **эскалирована пользователю до написания кода** с альтернативами (engine swap, manual primitive, custom protocol, drop feature). Failure mode рефлексии — silent no-op при любом изменении внутренней структуры зависимости; такие фичи не ловятся обычным test green/red.

## Worktree и окружение

**Редактируй файлы только в worktree, не в корне репозитория.** Перед первым Edit убедись, что путь ведёт в `.claude/worktrees/<branch>/`, а не в корень. Ошибка в пути = правка main в обход ревью.

**Обновление инструкций** (CLAUDE.md и других файлов в `.claude/`): правь только в текущем worktree. Корневой CLAUDE.md живёт на main — туда изменения попадают через PR-флоу.

**Авто-очистка worktree.** При остановке сессии Claude Code `Stop` hook автоматически удаляет worktree'ы, для которых remote-ветка удалена и PR смержен (`gh pr list --state merged`). Работает для squash, regular и fast-forward merge.

**Проверяй `pwd` перед Bash-командами.** Bash-сессии не сохраняют `cd` между вызовами. Перед diagnostic-сессией (smoke, ручная проверка, сборка для тестирования) первой командой делай `pwd && git rev-parse --short HEAD`, чтобы убедиться что ты в worktree, а не в `/Users/artem/StudioProjects/tether` на main.

**Rebase / merge main — смотри, что заехало, не только разрешай конфликты.** После `git rebase` / `git merge` main в рабочую ветку прочитай заехавшие коммиты (`git log <prev>..origin/main`) и оцени смысловое пересечение с текущей работой: новые правила в скиллах и `docs/engineering/`, переезд owner-а между фичами, переименования, новые разделы в спеке, по которой работаешь. Конфликт-резолвер ловит только текстовые столкновения; смысловые — нет. Если пересечение значительное — поправь работу под новый контекст или явно сообщи пользователю и предложи план.
