# Tether

P2P file transfer between devices on different OSes — over a local Wi-Fi network, no cloud, no accounts, no compression.

**The scenario Tether solves:** a photo from an Android phone to a MacBook today travels via messenger (compression), email (limits), or a cable. Tether replaces this with two taps on the same Wi-Fi — the original file goes directly between devices.

**Targets:** Android, iOS, Desktop (JVM on Windows / Linux / macOS). Kotlin Multiplatform + Compose Multiplatform.

**Status:** early MVP. Discovery, the basic transfer protocol, and Desktop CLI work; UI and pairing are in progress.

## Documentation

- [Vision & principles](docs/product/vision.md) — what we are building and why.
- [Roadmap](docs/product/roadmap.md) — what is in MVP, what comes after, what is deferred.
- [Features](docs/product/features/README.md) — status per feature and links to specs.
- [Tech stack](docs/product/tech-stack.md) — stack choices.
- [Security](docs/product/security.md) — threat model, pairing, encryption.
- [Engineering docs](docs/engineering/README.md) — architecture, modules, DI, testing.

## Quick start

Requires JDK 21 (Temurin).

### Desktop CLI (debug runner)

Starts `FileServer` + mDNS discovery, reads commands from stdin (`list`, `send <peer> <path>`, `quit`).

```bash
# first time — build the uber JAR and install the wrapper to ~/.local/bin
./gradlew :composeApp:installCli -q

# make sure ~/.local/bin is in PATH
export PATH="$PATH:$HOME/.local/bin"

# run with defaults (random port, device name = "Tether-$USER")
tether

# custom name and port
tether --name MyMac --port 8080
```

Check the server is alive: `curl http://localhost:<port>/health` → `Tether OK`.

Logs (off by default on the CLI): `TETHER_LOG_DEBUG=true tether` or `-Dtether.log.debug=true` turns the logger on at DEBUG. Full rules and platform gates — [`docs/engineering/logging.md`](docs/engineering/logging.md).

Example session:
```
> send Phone /tmp/photo.jpg
[send] 12.3 MB / 50.0 MB  (3.4 MB/s)
[send] OK — 14523 ms  →  /tmp/tether-downloads/photo.jpg
```

### Desktop UI

```bash
./gradlew :composeApp:run -q
```

Native app bundle: `./gradlew :composeApp:packageReleaseDistributionForCurrentOS`.

### Android

```bash
./gradlew :composeApp:assembleDebug
```

APK — in `composeApp/build/outputs/apk/debug/`. Or run the `composeApp` run configuration from Android Studio.

### iOS

Open `iosApp/` in Xcode and run, or use the iOS run configuration from the IDE (Android Studio / Fleet with the KMP plugin).

### macOS

Via the Desktop JVM target (same as Windows / Linux): `./gradlew :composeApp:run -q` for a dev run, `./gradlew :composeApp:packageReleaseDistributionForCurrentOS` for an `.app`/`.dmg` bundle with an embedded JRE. Why not Kotlin/Native — see [`adr-macos-native-vs-jvm.md`](docs/engineering/adr/adr-macos-native-vs-jvm.md).

### All targets at once

`scripts/run-all.sh` starts the CLI in parallel (in a separate Terminal window — interactive stdin required), Desktop UI, iOS simulator, and Android emulator. Logs for each target — in `scripts/.run-all/<target>.log`, aggregated `tail -F` in the main window. Ctrl-C shuts everything down.

```bash
./scripts/run-all.sh                          # everything
./scripts/run-all.sh --no-android             # without emulator
./scripts/run-all.sh --ios-device "iPhone 17 Pro"
```

Requires: `tether` in `PATH` (see Desktop CLI above), at least one AVD (create in Android Studio once — Studio itself is not needed when running the script), Xcode installed with simulators. This is a dev convenience for parallel manual verification; it does not replace `./gradlew allTests` and `/smoke-test`.

## For contributors

- [CLAUDE.md](CLAUDE.md) — what an AI agent or new contributor must know: architecture invariants, git conventions, worktree discipline.
- [docs/engineering/](docs/engineering/README.md) — architecture and code-writing rules (DI, modules, testing).
- [.claude/skills/](.claude/skills/) — multi-agent skills and workflow orchestrations (`/implement`, `/code-review`, `/close-issue`, `/retro`, `/grooming`, …).
- [.claude/commands/](.claude/commands/) — single-file prompt templates (`/rebase`, `/progress-boring`).

Tests:
```bash
./gradlew allTests -q
```

KtLint runs automatically via a git hook — do not invoke it manually, do not hand-fix style errors, and do not remove unused imports by hand (KtLint clears them too).

Doc link / `#anchor` validation: `lychee` runs offline on the full doc corpus in the pre-commit hook and online (external URLs) in pre-push; CI gates the same offline check on push. Install with `brew install lychee` (the hook soft-skips if absent).
