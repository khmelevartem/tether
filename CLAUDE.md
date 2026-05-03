# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Tether** is a Kotlin Multiplatform (KMP) file transfer application targeting Android, iOS, macOS, and Desktop (JVM). It enables peer-to-peer file sharing using mDNS service discovery and a Ktor-based file server.

### Architecture

The project follows KMP best practices with platform-specific and shared code:

- **commonMain**: Shared Kotlin code (UI with Compose Multiplatform, network protocol, file client)
- **Platform-specific sources**: `androidMain/`, `iosMain/`, `macosMain/`, `jvmMain/`, `appleMain/` (iOS + macOS shared code)
- **Core components**:
  - `protocol/Device.kt` — Protocol definitions for device identification and serialization
  - `network/FileServer.kt` — Ktor-based HTTP server (JVM only, in desktopMain/jvmMain)
  - `network/FileClient.kt` — HTTP client for file transfer (shared)
  - `discovery/MdnsDiscovery.kt` — Platform-specific mDNS service discovery (implementations in androidMain, appleMain, jvmMain)
  - `App.kt` — Compose Multiplatform UI entry point
  - Platform adapters (Platform.kt + platform-specific implementations)

### Build Configuration

- **Java version**: 21 (Temurin distribution)
- **Gradle features**: Configuration cache and build cache enabled
- **Kotlin style**: Official Kotlin code style enforced
- **macOS support**: Apple Silicon (arm64) only; Intel support can be added via macosX64() if needed
- **Compose Multiplatform**: Experimental macOS feature enabled

## Git Conventions

**All commit messages must be prefixed with the GitHub issue number** in the format `#<issue>: <message>`.

Examples:
```
#42: add mDNS discovery for Android
#17: fix FileServer port binding on macOS
#5: refactor Device serialization to use kotlinx.serialization
```

Before making commits, identify the relevant GitHub issue. If no issue exists for the task, ask the user to create one first or clarify which issue applies.

## Common Commands

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

Тесты запускай с флагом `-q`, чтобы выкинуть лишнюю информацию из вывода.

**Run all tests**

В стандартном цикле разработки запускай все тесты, чтобы проеврить, не отломался ли какой-то таргет.

```bash
./gradlew allTests -q
```

**Run tests for a specific module**

Запускай тесты на конкретный модуль или класс, только когда видишь реальную пользу сэкономить немного времени, или когда точно знаешь, что какой-то таргет еще не доделан.

```bash
./gradlew :composeApp:jvmTest -q    # JVM tests only
./gradlew :composeApp:commonTest -q # Common tests only
```

**Single test class**

Как и предыдущий пункт, должна быть явная причина запустить именно отдельный тест.

```bash
./gradlew :composeApp:jvmTest --tests "com.tubetoast.tether.network.FileServerTest"
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
├── jvmMain/             # Desktop JVM (contains FileServer, CLI Main.kt)
├── jvmTest/             # JVM tests (FileServerTest)
├── macosMain/           # macOS-specific
├── desktopMain/         # Desktop-specific shared code
└── appleMain/           # iOS + macOS shared (mDNS implementation)
```

## CI/CD

GitHub Actions (`.github/workflows/ci.yml`) runs on all pushes and PRs to main:
1. **KtLint** check — enforces code style
2. **Tests** — runs `./gradlew allTests` (JVM + Common tests)

## Key Development Notes

- **mDNS Discovery**: Platform-specific implementations required (Bonjour on Apple, Jmmdns stub on JVM, platform APIs on Android)
- **File Server**: Ktor-based, JVM-only (desktop debug runner). Listen logic in FileServer.kt
- **Protocol Serialization**: Device.kt uses kotlinx.serialization for peer identification
- **Compose for All Targets**: UI code in commonMain; platform-specific initialization in androidMain/iosMain/macosMain/jvmMain
- **CLI Arguments**: Desktop runner accepts `--name` and `--port` via Clikt framework

## Testing Strategy

**Тесты обязательны.** При реализации любой функциональности пиши тесты — unit и/или интеграционные. Ориентируйся на краевые случаи из описания задачи (issue).

- **JVM tests** (`jvmTest/`): Server и network интеграционные тесты
- **Common tests** (`commonTest/`): протокол и shared-логика
- Стиль: `kotlin.test`, `runBlocking` для корутин, `withTimeout` для сетевых/асинхронных тестов

## Code Review

When reviewing a PR, follow the process in [`.claude/code-review.md`](.claude/code-review.md).

## Worktree и окружение

При работе в git worktree (`.claude/worktrees/*`) скопируй `local.properties` из корня репозитория в директорию worktree — иначе pre-push хук (`./gradlew allTests`) не найдёт Android SDK и заблокирует push:

```bash
cp /path/to/repo/local.properties /path/to/worktree/local.properties
```
