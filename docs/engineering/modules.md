# Modules

## Source sets (current)

```
composeApp/src/
├── commonMain/      protocol, discovery seam, file transfer, trusted-device store — platform-independent logic
├── commonTest/      tests that run on all targets
├── androidMain/     Android entry points and platform actuals
├── iosMain/         iOS entry point and platform actuals
├── appleMain/       Apple actuals (iOS only — macOS ships via Desktop JVM; see adr-macos-native-vs-jvm.md)
├── jvmMain/         JVM actuals shared by Android and Desktop (Ktor server stack, key-pair storage)
├── desktopMain/     Desktop UI, backend wiring, JVM discovery actual
├── desktopCli/      CLI runner; the only place Clikt lives — associateWith desktopMain, excluded from the app distribution
└── desktopTest/     integration and unit tests for the Desktop JVM target
```

`jvmMain` is the intermediate parent of both `androidMain` and `desktopMain`: shared JVM code (Ktor server stack) is visible to both without leaking desktop-only dependencies into Android.

`desktopCli` is a separate compilation that sees `desktopMain` via `associateWith`. Keeping Clikt out of `desktopMain` lets the UI and backend classpath stay free of CLI argument-parsing dependencies.

## Target structure (when we split)

| Module | Depends on | Contents |
|--------|------------|----------|
| `:protocol` | — | Core types. Pure Kotlin + kotlinx.serialization. |
| `:platform` | — | Platform `expect`/`actual` — tiny by design, not a kitchen sink. |
| `:discovery` | `:protocol` | Discovery seam and platform actuals. |
| `:network` | `:protocol` | File client (common). File server with platform I/O behind the storage seam. |
| `:cli` | `:network`, `:discovery`, `:platform` | CLI runner and argument parsing. JVM-only. |
| `:composeApp` | everything above | Compose UI, navigation components, entry points. |

The split enforces dependency direction at compile time instead of by convention.

## When to extract

Extract when one of these holds — not before:

1. **A second consumer appears** — another target needs the same code without dragging the rest along.
2. **Compile-time isolation is needed** — the compiler should stop a layer violation, not a code review.
3. **A platform constraint is load-bearing** — one target needs a dependency the others must not see transitively.
4. **A real isolation boundary is being designed** — e.g. a free/pro split where the free build must not link Pro code.

If none hold, leave the code in `:composeApp`.

## Conventions

Apply these now, before any split:

- **No upward imports.** UI does not import network internals directly — it goes through a discovery/transfer interface.
- **`actual` implementations don't leak into `commonMain`.** Platform behaviour is modelled as `expect`/`actual`, not as runtime platform checks.
- **JVM-only code stays in `jvmMain`.** It never moves into `commonMain` to avoid a perceived duplicate.
- **One layer per package.** `protocol/`, `discovery/`, `network/`, `ui/` — package boundaries mark the layers even inside a single module.
