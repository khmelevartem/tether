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

**Lint check (KtLint)**
```bash
./gradlew ktlintCheck      # Check code style violations
./gradlew ktlintFormat     # Auto-fix code style
```

**Run all tests**
```bash
./gradlew allTests
```

**Run tests for a specific module**
```bash
./gradlew :composeApp:jvmTest    # JVM tests only
./gradlew :composeApp:commonTest # Common tests only
```

**Single test class**
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

- **JVM tests** (`jvmTest/`): Server and network integration tests (FileServerTest)
- **Common tests** (`commonTest/`): Protocol and shared logic (DeviceTest)
- Platform-specific tests would go in androidTest/, iosTest/, etc. (not yet present)
