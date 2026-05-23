# Logging

Tether logs through one Kotlin Multiplatform façade — [KydraLog](https://github.com/PocketByte/kotlin-kydra-log) — so that `commonMain` code can log directly and every platform writes to its native sink (Logcat on Android, OSLog on Apple, stderr on JVM). Why this façade and not another: [adr-logging-kydra.md](adr/adr-logging-kydra.md).

The rest of this doc is the contract any new logging call site must respect.

## Goal

A reader debugging a cross-platform issue sees the same logger names and the same level semantics in Logcat, Console.app / Xcode, and Desktop stderr. The CLI starts without spurious framework warnings. Release builds do not leak DEBUG. Tests do not leak any log noise into CI output.

## Logger names

Every named logger uses the prefix `Tether.` followed by a dot-separated path that identifies the subsystem and, when more than one implementation of the same subsystem exists per platform, the implementation:

- One name per source-set-shared subsystem (`Tether.FileServer`, `Tether.FileClient`).
- When the same subsystem has parallel implementations whose logs must be distinguishable at a glance, the implementation joins the path (`Tether.MdnsDiscovery.JmDNS`, `Tether.MdnsDiscovery.Bonjour`).
- Platform-only subsystems carry the platform when no common-side sibling exists (`Tether.FGService` lives only on Android — no need to qualify; `Tether.MdnsDiscovery.Android` qualifies because cross-platform peers exist).

The name is the **tag** passed to the underlying writer. On Android it appears in Logcat's tag column, on Apple in the OSLog subsystem column, on Desktop as the prefix in the printed line. Truncate nothing in source — Logcat's 23-char tag limit is no longer enforced on modern Android, and a long-but-greppable name is more useful than a short cryptic one.

## Levels

Four levels, present-tense semantics:

- **ERROR** — an operation we promised the caller has failed and the user-visible outcome is degraded. Always logged. Includes a short message; stack traces go through the same call.
- **WARNING** — something the system recovered from, or a configuration that will bite later if not addressed. Always logged.
- **INFO** — lifecycle events the operator wants to see in production support cases: service start/stop, peer paired, transfer started/finished with byte count.
- **DEBUG** — per-step internal state useful for engineering only. Off by default in every configuration except explicit local opt-in.

VERBOSE is not used. If a DEBUG call site is too chatty for routine debugging, split the subsystem's logger and gate the chatty half with a separate tag filter, do not introduce a fifth level.

The level encodes the **operational meaning**, not the size of the message. A two-line ERROR is still ERROR; a multi-paragraph DEBUG is still DEBUG.

## DEBUG gating

DEBUG is off by default. Per platform:

- **Android** — DEBUG is enabled when the running build is `debuggable` (`ApplicationInfo.FLAG_DEBUGGABLE`). Release APKs go to INFO. No env var on Android; the build flag is the single source of truth.
- **Apple (iOS + macOS)** — DEBUG is enabled when the Kotlin/Native binary is built in `DEBUG` configuration (`Platform.isDebugBinary`). Release framework goes to INFO.
- **Desktop (JVM)** — DEBUG is enabled when the JVM system property `tether.log.debug` is set (e.g. `-Dtether.log.debug=true`) **or** the environment variable `TETHER_LOG_DEBUG` is non-empty. Both knobs exist because the CLI is launched via the wrapper shell script (env var is natural there) and the Compose UI is launched via Gradle (system property is natural there).

The single-source-of-truth rule: each platform has exactly one gating mechanism resolved at writer initialisation. Per-tag overrides are *not* supported — if a subsystem needs a different default, raise its WARNING-or-higher signal in code, do not invent a new level threshold.

## Writer initialisation

Each platform initialises KydraLog exactly once at process start, before any subsystem can log:

- **Android** — `Application.onCreate()`.
- **Apple** — the `MainViewController` constructor on iOS and the equivalent entry point on macOS, both delegating to one shared `initLogging()` in `appleMain`.
- **Desktop UI** — `DesktopBackend` initialisation (before subsystems are constructed).
- **Desktop CLI** — first statement in `Main.kt`'s `run` block, before any Clikt-managed work.

`commonMain` code never calls `KydraLog.init*`. Initialisation is platform-side; common-side code only obtains tagged logger handles.

If a subsystem logs before initialisation runs, KydraLog's auto-init path catches it with the platform default — events are not lost. Reviewers should still flag pre-init logging in PRs because the level filter is not yet applied at that point.

## Forbidden idioms in production code

These idioms were the pre-KydraLog status quo and must not return:

- `System.err.println(...)` / `System.out.println(...)` / `println(...)` for diagnostic output. The only `println` survivors in `composeApp/src` live in [`desktopCli/.../Main.kt`](../../composeApp/src/desktopCli/kotlin/com/tubetoast/tether/Main.kt) where `output: (String) -> Unit` / `errorOutput: (String) -> Unit` are **product output channels** of the CLI, not logging. They are injected by tests; replacing them with a logger breaks CLI UX and test seeds.
- `android.util.Log.*` directly. Always goes through KydraLog so the tag namespace and level filter apply.
- `platform.Foundation.NSLog(...)` directly. Same reason.
- Encoding the level inside the message string (`"WARN: ..."`, `"DEBUG: ..."`). The level is a writer parameter, never a literal.

A grep is the standing acceptance check:

```bash
rg "System\.(err|out)\.println" composeApp/src --glob '!**/desktopCli/**'
rg "android\.util\.Log" composeApp/src
rg "NSLog" composeApp/src
```

All three should return no production hits. Test source sets may print freely.

## Test logging

Test source sets (`commonTest`, `jvmTest`, `androidUnitTest`, `desktopTest`, `appleTest`) must not produce log output during `./gradlew allTests`. The writer initialised in test setup is a no-op writer or a WARNING-threshold writer — chosen at test-helper level, not per test. A test that needs to assert against logged content uses an in-memory writer it installs itself in `@BeforeTest`.

## DEBUG content discipline

DEBUG carries operational metadata, never payload contents. For file transfer: file name, byte count, peer host:port, response status are DEBUG-eligible. The transferred bytes themselves are not. The contract is enforced at write-site, not at writer level — there is no PII filter; reviewers reject diff hunks that violate it.

## What this doc does *not* commit to

- The exact KydraLog version. Pinned in [`gradle/libs.versions.toml`](../../gradle/libs.versions.toml).
- The full enumeration of logger names. New subsystems add their own following the convention above; the canonical list is whatever `rg 'KydraLog\.withTag|logger\(' composeApp/src` returns at HEAD.
- The Ktor framework log level. Ktor logs through SLF4J on JVM; the binding choice and its threshold are recorded in the ADR — concrete level may be tuned without amending this doc.
- Future structured-logging (key=value, JSON) output. KydraLog writers emit plain strings today; introducing structured output is a separate decision.
