# Logging — KydraLog as the single KMP façade, SLF4J bridged via slf4j-simple

**Status:** Accepted — 2026-05-23
**Issue:** [#74](https://github.com/khmelevartem/tether/issues/74)
**Note (2026-05-25):** `macosArm64` Kotlin/Native target removed from the build — see [adr-macos-native-vs-jvm.md](adr-macos-native-vs-jvm.md) §Reversal. KydraLog's KMP coverage still holds for the active target set (`androidTarget`, `jvm("desktop")`, `iosArm64`, `iosSimulatorArm64`).

## Context

Logging in Tether before this decision is three disjoint practices: `System.err.println("WARN: …")` on Desktop / shared JVM, `android.util.Log.*` on Android, `NSLog(…)` on Apple. Code that legitimately lives in `commonMain` ([`FileServer`](../../../composeApp/src/commonMain/kotlin/com/tubetoast/tether/network/FileServer.kt), [`FileClient`](../../../composeApp/src/commonMain/kotlin/com/tubetoast/tether/network/FileClient.kt), [`FileServerRoutes`](../../../composeApp/src/commonMain/kotlin/com/tubetoast/tether/network/FileServerRoutes.kt)) cannot reach any of those directly; the current workaround is to delegate through callback methods on per-platform implementations (`UploadStorage.logInfo` / `UploadStorage.logError`). Levels are encoded as string prefixes, so they cannot be filtered at runtime.

Ktor on JVM logs through SLF4J. With no provider on the classpath the CLI prints `SLF4J(W): No SLF4J providers were found.` at startup — a visible artefact of the same gap.

The parent living doc that codifies the resulting rules is [`logging.md`](../logging.md). This ADR records *why* the chosen façade is KydraLog and how the eight sub-decisions inside #74 were resolved.

## Decision drivers

| Driver | Why it matters for Tether |
|---|---|
| `commonMain`-side logging | `FileServer` / `FileClient` / `FileServerRoutes` live in `commonMain`; the `UploadStorage` callback workaround should die. |
| KMP coverage for our exact target set | `androidTarget`, `jvm("desktop")`, `iosArm64`, `iosSimulatorArm64`, `macosArm64`. A façade that drops one is unusable. |
| Native sinks per platform | Logcat / OSLog / stderr respectively. A façade that prints through stdout on Android is half a solution. |
| Footprint and dependency weight | The app already carries Ktor + Compose + Decompose. The logger should not double the surface. |
| Configurable level filtering at startup | DEBUG must be silenceable in release without a recompile-only switch. |
| Active maintenance | A logger that stopped getting Kotlin / Native updates would lock our target list. |
| Ktor SLF4J slot on JVM | Whatever we choose has to resolve the `No SLF4J providers were found` warning. |
| Avoid building our own façade | Time-cost; an `expect/actual Logger` plus per-platform writers is well-trodden ground. |

## Considered options

### Option 1 — KydraLog (chosen)

`ru.pocketbyte.kydra:kydra-log:3.0.0`. Single multiplatform artefact, per-target writer classes (`AndroidLogger`, `AppleLogger`, `PrintLogger`), filtering and tagging via decorator wrappers (`.filtered`, `.withTag`), explicit `KydraLog.initDefault(level = …)` on each platform's entry point, auto-init fallback when init is skipped. KMP targets published match ours.

Closes the common-side gap (one façade callable from `commonMain`), the per-platform sink gap (writers route to Logcat / OSLog / stderr natively), and the level-filter gap (`LogLevel` enum, decorator-chain filtering).

Costs: one extra dependency; the project's SLF4J slot is still empty after adding it (handled separately, see decision below); auto-init can mask a missing explicit `initDefault` call, so reviewers must check entry-point wiring.

### Option 2 — Napier (`io.github.aakira:napier`)

Multiplatform façade with the same shape (per-platform `Antilog`s, tagged loggers, log-level filter). Coverage of our target set is comparable. Activity has slowed in 2024-2025; last release on Maven Central is 2.7.1 (Apr 2023).

Closes the same gaps as KydraLog. Costs: stale release cadence — newer Kotlin / Native bumps would land late or not at all; library does not expose a writer chain as flexible as KydraLog's decorator pattern, so per-tag filtering is harder.

### Option 3 — Kermit (`co.touchlab:kermit`)

Touchlab's multiplatform logger; production-grade, larger surface (CrashKit integration, custom log writers, severity per logger). KMP coverage matches.

Closes the same gaps. Costs: dependency weight is noticeably higher than KydraLog or Napier; we'd pay for CrashKit and remote-log surfaces we don't use; default `Logger.withTag` API ties the call site to a Kermit type, harder to swap later than a thin wrapper would be. The issue explicitly names KydraLog as the target — Kermit would require justifying the rejection of that direction.

### Option 4 — SLF4J everywhere (via a thin KMP `expect/actual` wrapper on top)

JVM ships SLF4J as a real provider (e.g. `logback-classic`), Android wraps Logcat via `slf4j-android`, Apple-side we'd have to roll our own `expect/actual` over OSLog because no SLF4J binding exists for Kotlin/Native. End up with one façade name but two implementations: SLF4J on JVM-family, custom on Apple-family.

Closes the SLF4J warning trivially (real provider present on JVM). Costs: the Apple-side custom binding is the same work as picking an existing KMP logger; KMP coverage of `slf4j-android` is JVM-only — we'd be writing the multiplatform glue ourselves on top.

### Option 5 — Status quo with `expect/actual Logger` of our own

No external dependency. `expect fun logger(tag: String): Logger` in `commonMain`; `actual` per source set wraps Logcat / OSLog / `println`. Filtering and tagging written by us.

Closes the common-side gap minimally. Costs: maintenance — every level enum, every decorator, every filter is ours to keep working across Kotlin / Native upgrades; the existing KMP libraries are exactly this code, already vendored. The "don't write your own KMP logger" sentence in the issue ("no need to write our own") rejects this explicitly.

## Decision

**Adopt KydraLog 3.0.0 as the single logging façade across all source sets.** The library closes every driver in the table, ships writers for every Tether target without expect/actual on our side, and lets `commonMain` log directly — which removes the `UploadStorage.logInfo` / `logError` callback workaround.

The eight sub-decisions called out by #74 resolve as follows:

1. **Gradle coordinate and writers.** `ru.pocketbyte.kydra:kydra-log:3.0.0` as a single dependency. Platform writers (`AndroidLogger`, `AppleLogger`, `PrintLogger`) are part of that artefact — no per-target sub-artefacts to wire.

2. **Source-set placement.** The KydraLog dependency goes in `commonMain` only; the artefact resolves per-target through KMP metadata, and `DefaultLoggerFactory.create()` picks the right writer on each platform via expect/actual inside the library. No separate writer dependency per source set.

3. **Logger naming.** `Tether.<Subsystem>[.<Implementation>]` as proposed in #74, locked in [`logging.md`](../logging.md). Names are tags passed to `KydraLog.withTag(default = "…")` at the call site; KydraLog's tag mechanism accepts arbitrary strings without truncation.

4. **DEBUG gating.** Per-platform single source of truth, set at writer initialisation: Android — `ApplicationInfo.FLAG_DEBUGGABLE`; Apple — `Platform.isDebugBinary`; Desktop — JVM system property `tether.log.debug` *or* env var `TETHER_LOG_DEBUG`. Two knobs on Desktop because the UI is launched via Gradle (property is natural) and the CLI is launched via the wrapper script (env var is natural); both resolve to the same DEBUG-on flag in code. Per-tag overrides are explicitly out — adding them would dilute the rule that level is the contract, not the configuration.

5. **Ktor SLF4J warning.** Add `org.slf4j:slf4j-simple` (matched to Ktor's pinned `slf4j-api` version, currently 2.0.x) to `jvmMain`'s runtime classpath, configured with `org.slf4j.simpleLogger.defaultLogLevel=warn`. This eliminates the `No SLF4J providers were found` warning, leaves Ktor's framework logs visible only at WARN+, and avoids the maintenance cost of writing an SLF4J→KydraLog bridge (Ktor framework logs are sparse enough that double-sinking them adds noise without value). `slf4j-nop` was rejected because suppressing Ktor's own WARN/ERROR output would hide real problems (CIO engine warnings we want to see). A custom bridge (`SLF4JServiceProvider` → KydraLog) was rejected as disproportionate for the volume of Ktor log traffic this project produces.

6. **Test logging strategy.** Test source sets install a no-op or WARN-threshold KydraLog writer in shared test setup (one helper per source-set group: `commonTest`, `jvmTest`, `appleTest`). Tests that need to assert against log output install an in-memory writer themselves in `@BeforeTest`. Per-test config is rejected as boilerplate.

7. **`UploadStorage.logInfo` / `logError` callback removal.** Removed in the same PR that wires KydraLog. With KydraLog reachable from `commonMain`, the callbacks have no remaining caller — leaving them as dead interface members would invite re-introduction. The issue allows deferral; we decline the deferral because the marginal scope is small (two interface methods plus their two `actual` implementations) and the value of closing the workaround in the same PR is high.

8. **Writer initialisation timing.** Each platform initialises at the same lifecycle hook: Android `Application.onCreate()`, Apple `MainViewController` constructor delegating to a shared `appleMain` helper, Desktop UI `DesktopBackend` (first construction), Desktop CLI as the first statement in `Main.kt`'s `run`. KydraLog's auto-init covers pre-init logging paths so events are not lost during startup races; reviewers still flag pre-init log calls because the level filter isn't applied yet.

## Costs accepted

1. **One more runtime dependency** (`kydra-log`) plus `slf4j-simple` on the JVM side. Footprint impact on Android APK / iOS framework size is on the order of 30-80 KB combined — measured against the existing Ktor + Compose + Decompose surface this is noise, but it is a non-zero ongoing maintenance dependency.
2. **`slf4j-simple` is a different sink from KydraLog.** Ktor framework logs go to stderr through SLF4J's simple formatter; subsystem logs go through KydraLog's `PrintLogger`. Operators reading CLI stderr see two formats interleaved. Bridging them would be a non-trivial custom `SLF4JServiceProvider` — rejected above as disproportionate. Side effect: the doc reader should know that "all logs unified" means "all subsystem logs unified", with Ktor's own framework chatter remaining a thin second channel.
3. **KydraLog's auto-init can hide a missing explicit `initDefault` call.** If a coder forgets to wire startup init, logs still flow at the default level — masking the configuration bug. Mitigated by the entry-point checklist in `logging.md` and reviewer attention; not enforced by the type system.
4. **KydraLog has a smaller community than Kermit.** If KydraLog's release cadence stalls on a future Kotlin major bump, we'd carry a vendored patch or migrate. The Revisit triggers below cover this.
5. **DEBUG gating is per-platform-mechanism, not one cross-platform knob.** A developer enabling DEBUG locally has three different switches to remember (build flag on Android, build config on Apple, env var / property on Desktop). The alternative — a runtime config file — was rejected as adding a new persistence surface for a developer convenience.

## Consequences

- The `UploadStorage` interface in [`FileServerRoutes.kt`](../../../composeApp/src/commonMain/kotlin/com/tubetoast/tether/network/FileServerRoutes.kt) loses its `logInfo` / `logError` members; `JvmUploadStorage` ([`FileServer.kt`](../../../composeApp/src/jvmMain/kotlin/com/tubetoast/tether/network/FileServer.kt)) and `AppleUploadStorage` ([`FileServer.apple.kt`](../../../composeApp/src/appleMain/kotlin/com/tubetoast/tether/network/FileServer.apple.kt)) lose their `override`s. Callers in `FileServerRoutes` go through `KydraLog.withTag("Tether.FileServerRoutes")` instead.
- `commonMain` gains real INFO/ERROR coverage in `FileServer` start/stop, `FileServerRoutes` upload paths, and `FileClient.doSend` — events that today are invisible until a platform wrapper re-emits them.
- `gradle/libs.versions.toml` gains a `kydraLog` version entry and matching `kydra-log` library entry; a `slf4j-simple` entry is added under the JVM group.
- Future subsystems (Decompose Components per [`presentation-layer.md`](../presentation-layer.md)) inherit the façade automatically — no separate logging work item.
- `./gradlew :composeApp:runDesktopCli -q` no longer prints `SLF4J(W): No SLF4J providers were found` (slf4j-simple provider present on JVM classpath).
- The acceptance grep in [`logging.md`](../logging.md) becomes a standing check; any future re-introduction of raw `println` / `Log.*` / `NSLog` is a reviewable diff hunk.

## Revisit if

- **KydraLog releases stop covering current Kotlin / Native versions** (a Kotlin major bump ships without a corresponding KydraLog release within two minor versions). Trigger: evaluate Kermit; the migration cost is bounded because all logger access is through `KydraLog.withTag` calls localised to one line per subsystem.
- **Operators need structured (JSON / key=value) logs** for log aggregation (Loki, CloudWatch). KydraLog writers emit strings; structured output would need either a custom writer or a different façade. Out of scope today.
- **Ktor framework logs become load-bearing** for production support (e.g. we need INFO from the CIO engine in user-submitted logs). Trigger: replace `slf4j-simple` with a custom `SLF4JServiceProvider` that forwards into KydraLog, unifying the two sink formats. Until that pressure exists, the asymmetry is cheaper than the bridge.
- **iOS-as-sender background networking** ([ios-background-networking.md](../../knowledge/ios-background-networking.md)) ships. Background tasks log into OSLog from a different process state; verify `AppleLogger`'s OSLog subsystem still routes correctly and tighten the writer init if it does not.

## Amendment 2026-06-04

On Desktop the CLI console logger is off by default — silent at every level — while the Compose UI keeps its INFO default.

This refines sub-decision 4: the debug knob's effect on Desktop is entry-point-specific — on/off for the CLI, level-select (INFO→DEBUG) for the Compose UI — amending its "same DEBUG-on flag in code" clause.

The full present-tense rules are in [logging.md](../logging.md) §Desktop streams.

## References

- [logging.md](../logging.md) — the living doc this ADR underpins.
- [KydraLog README & skill](https://github.com/PocketByte/kotlin-kydra-log/blob/master/.claude/skills/kydra-log/SKILL.md) — initialisation API, writer list, decorator chain.
- [SLF4J 2.0 provider mechanism](https://www.slf4j.org/manual.html#swapping) — context for the `slf4j-simple` choice.
- Related ADRs: [adr-network-stack.md](adr-network-stack.md) (Ktor framework that owns the SLF4J slot we fill), [adr-presentation-and-navigation.md](adr-presentation-and-navigation.md) (future Decompose components inherit this façade).
- Issue [#74](https://github.com/khmelevartem/tether/issues/74) — full call-site inventory and DoD.

