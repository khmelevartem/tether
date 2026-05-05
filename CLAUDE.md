# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Documentation

Two doc trees in this repo:

- [`docs/product/`](docs/product/README.md) — vision, audience, features, roadmap, monetization, design, security, competitors. Source of truth for *what* and *why*.
- [`docs/engineering/`](docs/engineering/README.md) — architecture principles, module layout, dependency injection rules. Source of truth for *how* code should be written.

**Before implementing anything non-trivial:** read [`docs/engineering/dependency-injection.md`](docs/engineering/dependency-injection.md) — it contains a "does my code fit?" checklist (constructor injection, no global context lookups, no premature interfaces). Skim [`docs/engineering/architecture-principles.md`](docs/engineering/architecture-principles.md) and [`docs/engineering/modules.md`](docs/engineering/modules.md) when the task touches layering, new components, or anything you'd be tempted to extract into a module.

## Project Overview

**Tether** is a Kotlin Multiplatform (KMP) file transfer application targeting Android, iOS, macOS, and Desktop (JVM). It enables peer-to-peer file sharing using mDNS service discovery and a Ktor-based file server.

### Architecture

The project follows KMP best practices with platform-specific and shared code:

- **commonMain**: Shared Kotlin code (UI with Compose Multiplatform, network protocol, file client)
- **Platform-specific sources**: `androidMain/`, `iosMain/`, `macosMain/`, `jvmMain/`, `desktopMain/`, `appleMain/` (iOS + macOS shared code)
- **Source set hierarchy**: `jvmMain` is the intermediate parent for both `androidMain` and `desktopMain` (configured via `applyHierarchyTemplate` in `build.gradle.kts`)
- **Core components**:
  - `protocol/Device.kt` — Protocol definitions for device identification and serialization
  - `network/FileServer.kt` — Ktor-based HTTP server (in `jvmMain`, shared Android + Desktop)
  - `network/FileClient.kt` — HTTP client for file transfer (shared)
  - `discovery/MdnsDiscovery.kt` — Platform-specific mDNS service discovery (implementations in androidMain, appleMain, desktopMain)
  - `App.kt` — Compose Multiplatform UI entry point
  - Platform adapters (Platform.kt + platform-specific implementations)

### Build Configuration

- **Java version**: 21 (Temurin distribution)
- **Gradle features**: Configuration cache and build cache enabled
- **Kotlin style**: Official Kotlin code style enforced
- **macOS support**: Apple Silicon (arm64) only; Intel support can be added via macosX64() if needed
- **Compose Multiplatform**: Experimental macOS feature enabled

## Git Conventions

All git naming is only in English: commit messages, PR titles and etc.
**All commit messages must be prefixed with the GitHub issue number** in the format `#<issue>: <message>`.

Examples:
```
#42: add mDNS discovery for Android
#17: fix FileServer port binding on macOS
#5: refactor Device serialization to use kotlinx.serialization
```

Before making commits, identify the relevant GitHub issue. If no issue exists for the task, ask the user to create one first or clarify which issue applies.

## Common Commands

Все Gradle-команды можно запускать с флагом `-q` (quiet) — он убирает бойлерплейтный вывод и оставляет только ошибки и предупреждения. Используй его по умолчанию.

### Build and Run

**Desktop CLI (debug runner with Ktor + mDNS stub)**
```bash
# Default: random port, device name = "Tether-$USER"
./gradlew :composeApp:run

# Custom name and port
./gradlew :composeApp:run --args="--name MyMac --port 8080"

# Verify server health
curl http://localhost:{port}/health  # → "Tether OK"
```

**Android APK**
```bash
./gradlew :composeApp:assembleDebug
```

**macOS app (requires Apple Silicon Mac)**
```bash
./gradlew :composeApp:runReleaseExecutableMacosArm64
```

**iOS**
```bash
# Use IDE run configuration or open iosApp/ in Xcode
```

### Quality & Testing

**KtLint — никогда не запускай вручную.** Git hook запускает KtLint автоматически при каждом коммите и исправляет форматирование сам. Не трать время на ручное исправление стилевых ошибок — просто коммить.

**Run all tests**

В стандартном цикле разработки запускай все тесты, чтобы проверить, не отломался ли какой-то таргет.

```bash
./gradlew allTests -q
```

**Run tests for a specific module**

Запускай тесты на конкретный модуль или класс, только когда видишь реальную пользу сэкономить немного времени, или когда точно знаешь, что какой-то таргет еще не доделан.

```bash
./gradlew :composeApp:desktopTest -q # Desktop JVM tests only
./gradlew :composeApp:commonTest -q  # Common tests only
```

**Single test class**

Как и предыдущий пункт, должна быть явная причина запустить именно отдельный тест.

```bash
./gradlew :composeApp:desktopTest --tests "com.tubetoast.tether.network.FileServerTest"
```

### Build Troubleshooting

- **Clear build cache**: `./gradlew clean`
- **Rebuild dependencies**: `./gradlew --refresh-dependencies`
- **Verbose output**: Add `--info` or `--debug` flag to any gradle command

## Project Structure

```
composeApp/src/
├── commonMain/          # Shared code (protocol, UI, file operations)
├── commonTest/          # Common tests
├── androidMain/         # Android-specific (mDNS implementation)
├── iosMain/             # iOS-specific
├── appleMain/           # iOS + macOS shared (mDNS implementation)
├── macosMain/           # macOS-specific
├── jvmMain/             # Shared JVM: FileServer, FileClientJvm (parent of androidMain + desktopMain)
├── desktopMain/         # Desktop JVM leaf: CLI Main.kt, MdnsDiscovery.jvm, Platform.jvm
└── desktopTest/         # Desktop JVM tests (FileServerTest, FileClientTest, MdnsDiscoveryTest)
```

## CI/CD

GitHub Actions (`.github/workflows/ci.yml`) runs on all pushes and PRs to main:
1. **KtLint** check — enforces code style
2. **Tests** — runs `./gradlew allTests` (JVM + Common tests)

## Key Development Notes

- **mDNS Discovery**: Platform-specific implementations required (Bonjour on Apple, Jmmdns stub on JVM, platform APIs on Android)
- **File Server**: Ktor-based, JVM-only (desktop debug runner). Listen logic in FileServer.kt
- **Protocol Serialization**: Device.kt uses kotlinx.serialization for peer identification
- **Compose for All Targets**: UI code in commonMain; platform-specific initialization in androidMain/iosMain/macosMain/desktopMain
- **CLI Arguments**: Desktop runner accepts `--name` and `--port` via Clikt framework

## Testing Strategy

**Тесты обязательны.** При реализации любой функциональности пиши тесты — unit и/или интеграционные. Ориентируйся на краевые случаи из описания задачи (issue).

- **Desktop JVM tests** (`desktopTest/`): Server и network интеграционные тесты
- **Common tests** (`commonTest/`): протокол и shared-логика
- Стиль: `kotlin.test`, `runBlocking` для корутин, `withTimeout` для сетевых/асинхронных тестов

## Code Review

When reviewing a PR, follow the process in [`.claude/commands/code-review.md`](.claude/commands/code-review.md).

## Worktree и окружение

**Важно: редактируй файлы только в worktree, не в корне репозитория.**
Перед первым Edit убедись, что путь ведёт в `.claude/worktrees/<branch>/`, а не в корень проекта. Ошибка в пути приведёт к правке основной ветки в обход ревью.
