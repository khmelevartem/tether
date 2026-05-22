# Modules

How the code is organized today, where it's headed, and what triggers each step.

## Current state

All application code lives in a single Gradle module: `:composeApp`. Build tooling lives in `:ktlint-rules` (custom KtLint rules consumed via `ktlintRuleset`; not in the app runtime graph). Source sets follow a custom KMP
hierarchy (configured via `applyHierarchyTemplate` in `build.gradle.kts`):

```
composeApp/src/
├── commonMain/      protocol, FileClient, MdnsDiscovery (expect), FileServer (expect), FileServerRoutes (UploadStorage seam), TrustedDeviceStore (expect), Platform (expect), App.kt
├── commonTest/      DeviceTest, PairingProtocolTest
├── androidMain/     MainActivity, TetherApp, TetherForegroundService, MdnsDiscovery.android, Platform.android
├── iosMain/         MainViewController, Platform.ios
├── appleMain/       MdnsDiscovery.apple, FileServer.apple (Ktor CIO Native + POSIX storage)
├── macosMain/       Platform.macos
├── jvmMain/         FileServer (JVM actual + storage), FileClientJvm, DeviceKeyPair   ← shared Android + Desktop JVM
├── jvmTest/         (empty — FileServerTest moved to desktopTest in #9)
├── desktopMain/     MainUi, DesktopBackend, DesktopAppContainer, MdnsDiscovery.jvm, Platform.jvm, TrustedDeviceStore.desktop  ← Desktop JVM leaf (main compilation)
├── desktopCli/      Main.kt  ← Desktop CLI runner (custom compilation, associateWith main; only place Clikt lives)
└── desktopTest/     FileClientTest, MdnsDiscoveryTest, FileServerTest, FileServerPairTest, TrustedDeviceStoreTest, DeviceKeyPairTest, CliParseTokensTest, CliSendTest
```

Hierarchy: `jvmMain` is the intermediate parent for both `androidMain` and `desktopMain`,
enabling shared JVM code (Ktor server stack) to be visible to both without leaking
desktop-only dependencies (JmDNS, Compose Desktop) into Android. Clikt is scoped one
level further — only the `desktopCli` custom compilation depends on it, isolating CLI
parsing from the UI/backend classpath. Ktor server
itself is now also published for Kotlin/Native (Ktor 3.0+), so `FileServer`'s shared
routing lives in `commonMain` with platform-specific I/O via the `UploadStorage` seam;
see [adr/adr-apple-fileserver-engine.md](adr/adr-apple-fileserver-engine.md).

This is fine *for now*. Layers are visually distinguishable (protocol / discovery / network / UI / platform / cli). The cost is also real: nothing prevents UI code from importing `FileServer` directly, and a change in any layer rebuilds everything.

## Target structure (when we split)

The shape we'll move toward, in priority order:

| Module | Source sets | Depends on | Contents |
|--------|-------------|------------|----------|
| `:protocol` | commonMain only | — | `Device`, `SendResult`, future request/response envelopes. Pure Kotlin + kotlinx.serialization. |
| `:discovery` | common + android + apple + jvm | `:protocol` | `MdnsDiscovery` `expect` + `actual` per platform. The seam UI talks to. |
| `:network` | common + jvm + apple | `:protocol` | `FileClient` (common, Ktor CIO). `FileServer` uses Ktor CIO on JVM and Native; route definitions in commonMain, platform I/O via the `UploadStorage` seam. |
| `:platform` | all | — | `Platform` `expect` + `actual`. Tiny by design; not a kitchen sink. |
| `:cli` | jvmMain | `:network`, `:discovery`, `:platform` | `Main.kt`, Clikt argument parsing. JVM-only. |
| `:composeApp` | all | everything above | Compose UI, Decompose Components, entry points. |

The split is not about Gradle modules per se — it's about **where the dependency arrow gets enforced by the compiler instead of by good intentions.**

### Why this shape

- **`:protocol` first.** It's pure, has no transitive dependencies, and depending on it from a test or a future module is free. Easy win.
- **`:discovery` and `:network` separated.** They sit at different priorities and have different platform constraints — discovery and server now both run cross-platform, but they have different release cadences and different test surfaces.
- **Platform-specific I/O stays in platform source sets.** `FileServer`'s common route lives in `commonMain`; the platform I/O (POSIX on Apple, `java.io.File` on JVM) sits behind the `UploadStorage` seam in each `actual` source set so the route never has to branch by platform.
- **`:cli` is its own module.** It pulls Clikt (JVM-only) and orchestrates other modules — but it's not part of the app users install. Keeping it separate lets the app target stay smaller and the server-side code stay cleanly testable.
- **`:composeApp` last.** It's the most volatile module; everything depends *on it*'s parent direction (downward), nothing depends on it.

## When to extract — triggers, not vibes

Don't extract a module because the code "feels like it should be split." Extract when one of these is true:

1. **A second consumer appears.** A second app target, a CLI tool, a benchmark — anything that needs the same code without dragging the rest along. (`:cli` triggers this for `:network` + `:discovery`.)
2. **Compile-time isolation is needed.** UI code keeps reaching into low-level network internals and we want the compiler — not a code review — to stop it.
3. **Build time hurts.** A change to one screen recompiles `FileServer` tests for no reason. (Won't matter until the project is many times its current size.)
4. **A platform constraint becomes load-bearing.** E.g. one target needs a dependency the others must not transitively see (Clikt on Desktop CLI, Compose UI on apps but not the network module).
5. **A real boundary is being designed.** The Pro vs free split (see [`monetization.md`](../product/monetization.md)) might warrant a `:pro` module so the free build literally can't link Pro code. Decide when Pro features are real.

If none of the above hold, leave the code in `:composeApp`.

## Order of operations (when we start splitting)

1. `:protocol` — cheapest, zero risk. Good first split.
2. `:platform` — also tiny and will be referenced from anywhere.
3. `:discovery` — first non-trivial extraction; forces us to design the `expect`/`actual` boundary properly.
4. `:network` — together with the `:cli` extraction; they motivate each other.
5. `:cli` — last of the "core" splits; it's already JVM-only and stable.
6. Future Pro module — only when there's something to put in it.

## Conventions for module-internal code

Even before we extract anything, follow the conventions a future split would require:

- **No upward imports.** UI does not import from `network/FileServer` directly — it goes through a discovery/transfer interface. Treat the package as if it were already a separate module.
- **`actual` implementations don't leak into `commonMain`.** If something in `commonMain` needs platform behavior, model it as `expect`/`actual`, not as `if (Platform.isAndroid)`.
- **JVM-only code stays in `jvmMain`.** Even single-file utilities — once `FileClientJvm` exists in `jvmMain`, it never silently moves into `commonMain` to "save a duplicate."
- **One layer per package.** `protocol/`, `discovery/`, `network/`, `ui/` — even within a single module, package boundaries are the line.

## Anti-patterns that have already appeared

These map to problems found in the current code (see [architecture-principles.md](architecture-principles.md) for the layering view):

- `MainActivity` directly orchestrating `MdnsDiscovery` lifecycle. Pulls discovery concerns into UI; will fight a future `:composeApp` ↔ `:discovery` split.
- `TetherApp.context` reached from inside `MdnsDiscovery.android`. Crosses the UI ↔ discovery boundary backward. Pass `Context` (or a thin wrapper) explicitly when constructing the Android `actual`.
- `Main.kt` constructing `FileServer` and `MdnsDiscovery` ad-hoc. Belongs to a composition root (see [dependency-injection.md](dependency-injection.md)), not the entry point body.

## Open questions

- Do we want a separate `:ui-common` module that holds shared Compose components, or keep all UI in `:composeApp`? Defer until we have more than ~5 screens.
