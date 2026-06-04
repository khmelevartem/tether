# Logging

Tether logs through one Kotlin Multiplatform façade — [KydraLog](https://github.com/PocketByte/kotlin-kydra-log) — so that `commonMain` code can log directly and every platform writes to its native sink (Logcat on Android, OSLog on Apple, a print stream on JVM). Why this façade and not another: [adr-logging-kydra.md](adr/adr-logging-kydra.md).

The rest of this doc is the contract any new logging call site must respect.

## Goal

A reader debugging a cross-platform issue sees the same logger names and the same level semantics in Logcat, Console.app / Xcode, and a Desktop print stream. The CLI starts without spurious framework warnings. Release builds do not leak DEBUG.

## Logger names

Every named logger uses the prefix `Tether.` followed by a dot-separated path that identifies the subsystem and, when more than one implementation of the same subsystem exists per platform, the implementation:

- One name per source-set-shared subsystem (`Tether.FileServer`, `Tether.FileClient`).
- When the same subsystem has parallel implementations whose logs must be distinguishable at a glance, the implementation joins the path (`Tether.MdnsDiscovery.JmDNS`, `Tether.MdnsDiscovery.Bonjour`).
- Platform-only subsystems carry the platform when no common-side sibling exists (`Tether.FGService` lives only on Android — no need to qualify; `Tether.MdnsDiscovery.Android` qualifies because cross-platform peers exist).

The name is the **tag** passed to the underlying writer. On Android it appears in Logcat's tag column, on Apple in the OSLog subsystem column, on Desktop as the prefix in the printed line. Truncate nothing in source — Logcat's 23-char tag limit is no longer enforced on modern Android, and a long-but-greppable name is more useful than a short cryptic one.


## Levels

Four levels, present-tense semantics:

- **ERROR** — an operation we promised the caller has failed and the user-visible outcome is degraded. Always logged. Includes a short message; stack traces go through the same call.
- **WARNING** — something the system recovered from, or a configuration that will bite later if not addressed. Always logged. A failed rendezvous probe — a `/hello` the caller retries on the next discovery cycle, with discovery still holding the peer — is WARNING, not ERROR: nothing the caller was promised has failed yet.
- **INFO** — lifecycle events the operator wants to see in production support cases: service start/stop, peer paired, transfer started/finished with byte count.
- **DEBUG** — per-step internal state useful for engineering only. Off by default in every configuration except explicit local opt-in. Internal rendezvous steps — announcing presence, sending a `/hello` probe to a peer — are DEBUG everywhere: they are sub-steps of a lifecycle event, not the event, and at default level they would only add noise.

VERBOSE is not used. If a DEBUG call site is too chatty for routine debugging, split the subsystem's logger and gate the chatty half with a separate tag filter, do not introduce a fifth level.

The level encodes the **operational meaning**, not the size of the message. A two-line ERROR is still ERROR; a multi-paragraph DEBUG is still DEBUG.

## DEBUG gating

DEBUG is off by default. Per platform:

- **Android** — DEBUG is enabled when the running build is `debuggable` (`ApplicationInfo.FLAG_DEBUGGABLE`). Release APKs go to INFO. No env var on Android; the build flag is the single source of truth.
- **Apple (iOS + macOS)** — DEBUG is enabled when the Kotlin/Native binary is built in `DEBUG` configuration (`Platform.isDebugBinary`). Release framework goes to INFO.
- **Desktop (JVM)** — DEBUG is enabled when the JVM system property `tether.log.debug` is set (e.g. `-Dtether.log.debug=true`) **or** the environment variable `TETHER_LOG_DEBUG` is set to `true`. Both knobs exist because the CLI is launched via the wrapper shell script (env var is natural there) and the Compose UI is launched via Gradle (system property is natural there).

The single-source-of-truth rule: each platform has exactly one gating mechanism resolved at writer initialisation. Per-tag overrides are *not* supported — if a subsystem needs a different default, raise its WARNING-or-higher signal in code, do not invent a new level threshold.

## Writer initialisation

Each platform initialises KydraLog exactly once at process start, before any subsystem can log:

- **Android** — `Application.onCreate()`.
- **Apple** — the `MainViewController` constructor on iOS and the equivalent entry point on macOS, both delegating to one shared `initLogging()` in `appleMain`.
- **Desktop UI** — `DesktopBackend` initialisation (before subsystems are constructed).
- **Desktop CLI** — first statement in `Main.kt`'s `run` block, before any Clikt-managed work.

`commonMain` code never calls `KydraLog.init*`. Initialisation is platform-side; common-side code only obtains tagged logger handles.

If a subsystem logs before initialisation runs, KydraLog's auto-init path catches it with the platform default — events are not lost. Reviewers should still flag pre-init logging in PRs because the level filter is not yet applied at that point.

## Desktop streams

The Desktop CLI splits its two output kinds across the two console streams:

- **stdout** carries product output the user reads as the tool's answer: the startup banner, the device/port lines, and the bracketed status lines (`[peers]`, `[list]`, `[send]`). These are the CLI's UX, not logging — they go through the CLI's own output channels, never through a logger.
- **stderr** carries subsystem diagnostics by default. KydraLog's stock JVM writer prints to stdout ([KydraLog source](https://github.com/PocketByte/kotlin-kydra-log/blob/master/library/src/jvmCommonMain/kotlin/ru/pocketbyte/kydra/log/PrintLogger.kt)); the Desktop writer instead targets stderr at initialisation so diagnostics stay off the product channel. Ktor's framework logs reach stderr independently through the SLF4J binding (see [adr-logging-kydra.md](adr/adr-logging-kydra.md) §Costs accepted).

The stderr default applies to the whole Desktop (JVM) process — both the CLI and the Compose UI initialise the same writer. The split is motivated by the CLI: it is the entry point that emits bracketed product output on stdout, which diagnostics must not corrupt. The Compose UI has no stdout product channel, so it inherits the same stderr routing harmlessly.

One toggle moves the subsystem stream onto stdout for a session where the operator watches stdout and wants diagnostics interleaved there: the JVM system property `tether.log.stdout` *or* the environment variable `TETHER_LOG_STDOUT`, set to `true` to enable — the same env-plus-property pairing the debug knob uses, for the same launch-context reason. The selector is Desktop-wide, applying to whichever entry point the process started from. This selects the writer's **stream**; the debug knob selects the **level**. The two are orthogonal: either, both, or neither may be set, and neither implies the other.

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

## Sensitive data

Logs from this app land on the same device the user runs it on — Logcat on Android, OSLog on Apple, a console stream on Desktop. There is no central aggregation. The threat model is therefore narrow: a bug report or screen share exfiltrates whatever was in the log at that moment, and a separate process with log-read permission can see it. Logs are not a network exfiltration channel.

Three categories never appear in any log line at any level:

- **Payload bytes** — file content, stream chunks, hex dumps, `toString()` of binary buffers.
- **Private cryptographic material** — raw bytes, base64, PEM.
- **Auth tokens, session IDs, nonces** — including short prefixes (`token=abc1...`); a prefix is enough to scope an attack. Public keys and their fingerprints are fine — they are identifying but not exfiltratable.

Local-identifier values — file names, absolute paths, peer device names (user-set), peer host:port — may appear at any level for operator correlation. Reviewer judgement, not a hard rule: keep them out of WARN/ERROR when an alternative correlator (logger tag, opaque id, fingerprint) carries the same information. Authors should remember that logs surfaced via bug reports leak whatever they contain.

Exception messages reaching the log are inspected for embedded values before they go in. A library `IllegalArgumentException` may carry a secret in its message; log a synthetic summary and keep the original on the return path only.

The contract is enforced at the write-site; there is no runtime filter. Reviewers reject diff hunks that violate the three never categories.

## What this doc does *not* commit to

- The exact KydraLog version. Pinned in [`gradle/libs.versions.toml`](../../gradle/libs.versions.toml).
- The full enumeration of logger names. New subsystems add their own following the convention above; the canonical list is whatever `rg 'KydraLog\.withTag|logger\(' composeApp/src` returns at HEAD.
- The Ktor framework log level. Ktor logs through SLF4J on JVM; the binding choice and its threshold are recorded in the ADR — concrete level may be tuned without amending this doc.
- Future structured-logging (key=value, JSON) output. KydraLog writers emit plain strings today; introducing structured output is a separate decision.
