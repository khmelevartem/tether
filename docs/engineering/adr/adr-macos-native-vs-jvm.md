# macOS Target — Native (`macosArm64`) over Desktop JVM

**Status:** Accepted — 2026-05-07 (de facto since project init; this ADR records the rationale)
**Issue:** #63

## Context

Tether ships macOS as part of the MVP — the canonical user described in [audience.md](../../product/audience.md) is "Android phone + macOS laptop." The KMP setup configures `macosArm64()` in `composeApp/build.gradle.kts`. The alternative would be to ship macOS via the Desktop JVM target, the same way Windows and Linux are planned to ship.

Until now the choice was implicit: it lived in `build.gradle.kts` and a couple of paragraphs in [tech-stack.md](../../product/tech-stack.md). This ADR makes the trade-off explicit.

## Decision drivers

| | macOS Native (`macosArm64`) | macOS Desktop JVM |
|---|---|---|
| `appleMain` shared with iOS | ✅ same source set, same NSNetService discovery, same future receiver-server | ❌ macOS would need JmDNS, leaving NSNetService for iOS alone — two Apple-side stacks |
| Ktor server | ❌ JVM-only — needs platform-side impl (Network.framework / GCDWebServer) | ✅ runs out of the box |
| Compose-MP maturity | ⚠️ experimental | ✅ mature on JVM |
| Distribution | ✅ native binary, small | ⚠️ `.app` with bundled JRE (~50–80 MB) |
| Standalone `run` task | ❌ launches via IDE / Xcode | ✅ `./gradlew :composeApp:run` works |

## Decision

**macOS ships as a Kotlin/Native target (`macosArm64`).** Apple Silicon only; `macosX64` is deferred until a real user reports it (per tech-stack.md).

### Why Native over JVM

1. **`appleMain` source-set sharing with iOS.** mDNS discovery is already implemented once in `appleMain/MdnsDiscovery.apple.kt` and used by both iOS and macOS. The future iOS receiver-server (Network.framework / GCDWebServer / similar) will live in the same source set. Switching macOS to JVM breaks that share — discovery would have to be reimplemented in JmDNS for macOS, leaving NSNetService for iOS alone. We'd carry two Apple-side stacks instead of one.
2. **Smaller distribution.** A native binary vs. a `.app` with a bundled JRE (50–80 MB). For a file-transfer utility the install footprint is part of the perceived product quality.

### Costs accepted

1. **Compose-MP on macOS native is experimental.** Acceptable for MVP scope; flagged in build configuration.
2. **Ktor server doesn't run on Kotlin/Native.** macOS receiver needs a platform-side implementation. The work is required for iOS regardless; macOS Native gets it as part of that effort, not as extra scope.
3. **No standalone `run` task.** `macosArm64` compiles, but launching is via IDE / Xcode. Less ergonomic than `./gradlew :composeApp:run` on Desktop JVM, but the JVM CLI fills the dev-iteration gap when needed.
4. **Extra build target.** Marginal: a bit more compile time, an extra release artifact, separate test cycle.

## Considered alternatives

- **macOS via Desktop JVM.** Compose-MP is mature on JVM, Ktor runs out of the box, and the path to MVP is shorter. Lose `appleMain` sharing — doubled Apple-side mDNS stack — and `.app` distribution is heavier. Becomes attractive only if Compose-MP macOS native materially blocks UX (see revisit triggers).
- **Drop macOS from MVP.** Conflicts with the canonical user described in [audience.md](../../product/audience.md).

## Revisit if

- **Compose-MP macOS native materially breaks UX** in the skeleton or first product screens — temporarily fall back to macOS JVM and restore Native when Compose stabilises.
- **iOS receiver work is deferred indefinitely (>3 sprints).** The `appleMain` sharing argument weakens if the share is hypothetical for a long time; the Native-only justification then rests on distribution size, which alone may not be enough.
- **An Intel Mac user surfaces** — add `macosX64()` (one line + a small permanent build/test/release tax). Independent of the Native-vs-JVM choice but closely related.

## References

- [tech-stack.md](../../product/tech-stack.md) — current macOS configuration notes
- [audience.md](../../product/audience.md) — canonical user
- [roadmap.md](../../product/roadmap.md) — MVP scope
