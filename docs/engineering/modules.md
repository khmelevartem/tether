# Modules

How the code is organized today, where it's headed, and what triggers each step.

## Current state

All code lives in a single Gradle module: `:composeApp`. Source sets follow a custom KMP
hierarchy (configured via `applyHierarchyTemplate` in `build.gradle.kts`):

```
composeApp/src/
├── commonMain/      protocol, FileClient, MdnsDiscovery (expect), Platform (expect), App.kt
├── commonTest/      DeviceTest
├── androidMain/     MainActivity, TetherApp, TetherForegroundService, MdnsDiscovery.android, Platform.android
├── iosMain/         MainViewController, Platform.ios
├── appleMain/       MdnsDiscovery.apple (stub)
├── macosMain/       Platform.macos
├── jvmMain/         FileServer, FileClientJvm           ← shared Android + Desktop JVM
├── jvmTest/         FileServerTest                      ← runs in both desktopTest and androidUnitTest
├── desktopMain/     Main.kt (CLI), MdnsDiscovery.jvm, Platform.jvm  ← Desktop JVM leaf
└── desktopTest/     FileClientTest, MdnsDiscoveryTest
```

Hierarchy: `jvmMain` is the intermediate parent for both `androidMain` and `desktopMain`,
enabling shared JVM code (Ktor server) to be visible to both without leaking
desktop-only dependencies (Clikt, JmDNS, Compose Desktop) into Android.

This is fine *for now*. Layers are visually distinguishable (protocol / discovery / network / UI / platform / cli). The cost is also real: nothing prevents UI code from importing `FileServer` directly, and a change in any layer rebuilds everything.

## Target structure (when we split)

The shape we'll move toward, in priority order:

| Module | Source sets | Depends on | Contents |
|--------|-------------|------------|----------|
| `:protocol` | commonMain only | — | `Device`, `SendResult`, future request/response envelopes. Pure Kotlin + kotlinx.serialization. |
| `:discovery` | common + android + apple + jvm | `:protocol` | `MdnsDiscovery` `expect` + `actual` per platform. The seam UI talks to. |
| `:network` | common + jvm | `:protocol` | `FileClient` (common, Ktor CIO). `FileServer` lives in the JVM source set — there is no Native Ktor server. |
| `:platform` | all | — | `Platform` `expect` + `actual`. Tiny by design; not a kitchen sink. |
| `:cli` | jvmMain | `:network`, `:discovery`, `:platform` | `Main.kt`, Clikt argument parsing. JVM-only. |
| `:composeApp` | all | everything above | Compose UI, Decompose Components, entry points. |

The split is not about Gradle modules per se — it's about **where the dependency arrow gets enforced by the compiler instead of by good intentions.**

### Why this shape

- **`:protocol` first.** It's pure, has no transitive dependencies, and depending on it from a test or a future module is free. Easy win.
- **`:discovery` and `:network` separated.** They sit at different priorities and have different platform constraints — discovery needs all platforms, server is JVM-only. Sharing a module forces compromises on both.
- **JVM-only code stays in JVM source sets.** `FileServer` does not become its own module — it just lives in `:network`'s `jvmMain` so the dependency graph is honest about where the server can run.
- **`:cli` is its own module.** It pulls Clikt (JVM-only) and orchestrates other modules — but it's not part of the app users install. Keeping it separate lets the app target stay smaller and the server-side code stay cleanly testable.
- **`:composeApp` last.** It's the most volatile module; everything depends *on it*'s parent direction (downward), nothing depends on it.

## When to extract — triggers, not vibes

Don't extract a module because the code "feels like it should be split." Extract when one of these is true:

1. **A second consumer appears.** A second app target, a CLI tool, a benchmark — anything that needs the same code without dragging the rest along. (`:cli` triggers this for `:network` + `:discovery`.)
2. **Compile-time isolation is needed.** UI code keeps reaching into low-level network internals and we want the compiler — not a code review — to stop it.
3. **Build time hurts.** A change to one screen recompiles `FileServer` tests for no reason. (Won't matter until the project is many times its current size.)
4. **A platform constraint becomes load-bearing.** E.g. `FileServer` is JVM-only and we want a non-JVM target to *not* even see it transitively. Today it works because everything is in one module; a future iOS receiver implementation will force this.
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
- iOS `FileServer` story (Ktor server is JVM-only). Likely a separate module `:network-ios` with a Kotlin/Native or platform-API implementation. Decide when iOS receiver work begins.
