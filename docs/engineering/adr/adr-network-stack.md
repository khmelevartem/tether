# Network stack — Ktor CIO (client + server), tactically not fundamentally

**Status:** Accepted — 2026-05-16
**Issue:** [#166](https://github.com/khmelevartem/tether/issues/166)

## Context

Tether's transport is a symmetric HTTP layer: every running instance hosts an HTTP server ([`FileServer`](../../../composeApp/src/commonMain/kotlin/com/tubetoast/tether/network/FileServer.kt)) on a random ephemeral port and is also an HTTP client ([`FileClient`](../../../composeApp/src/commonMain/kotlin/com/tubetoast/tether/network/FileClient.kt)) to other peers. Discovery is mDNS, decoupled and out of scope here.

Active KMP targets: `androidTarget`, `jvm("desktop")`, `iosArm64`, `iosSimulatorArm64`, `macosArm64`. Source-set hierarchy puts `jvmMain` as the intermediate parent of `androidMain` + `desktopMain`, and `appleMain` as the parent of `iosMain` + `macosMain`. The server stack lives in `commonMain` (Ktor 3.1.3) since Ktor 3.0 publishes `ktor-server-cio` for Native; the per-platform `actual`s differ only in the filesystem sink ([`FileServer.kt`](../../../composeApp/src/jvmMain/kotlin/com/tubetoast/tether/network/FileServer.kt) vs [`FileServer.apple.kt`](../../../composeApp/src/appleMain/kotlin/com/tubetoast/tether/network/FileServer.apple.kt)).

The original choice of CIO on both sides was a fast-research decision — not documented as a deliberate selection until now. A cluster of issues ([#113](https://github.com/khmelevartem/tether/issues/113), [#25](https://github.com/khmelevartem/tether/issues/25), [#91](https://github.com/khmelevartem/tether/issues/91), [#119](https://github.com/khmelevartem/tether/issues/119), [#164](https://github.com/khmelevartem/tether/issues/164)) and the workaround in [docs/knowledge/ktor-server-cio.md](../../knowledge/ktor-server-cio.md) prompted this re-evaluation. The narrower Apple-engine choice is already recorded in [adr-apple-fileserver-engine.md](adr-apple-fileserver-engine.md); this ADR is the broader, project-wide decision that subsumes it.

**Hard adjacent constraint — channel encryption ([adr-channel-encryption.md](adr-channel-encryption.md), implemented by [#140](https://github.com/khmelevartem/tether/issues/140)):** that ADR is already Accepted (with a 2026-05-16 Amendment correcting two factual errors found during this ADR's adversarial review — see that doc). After TLS lands, the Ktor CIO server engine cannot stay anywhere:

- **Apple side:** Ktor CIO Native has no TLS at all — `ktor-network-tls/nonJvmMain` is a stub that throws `error("TLS sessions are not supported on Native platform.")` ([KTOR-7262](https://youtrack.jetbrains.com/issue/KTOR-7262), open since 2020, [KTOR-7475](https://youtrack.jetbrains.com/issue/KTOR-7475), [KTOR-5912](https://youtrack.jetbrains.com/issue/KTOR-5912)). Apple `FileServer.actual` is rewritten on SecureTransport + `ktor-http-cio` parser. Apple `FileClient.actual` swaps to either SecureTransport-wrapped raw TCP or the Ktor Darwin engine — pre-flight spike in #140 picks. POC [#138](https://github.com/khmelevartem/tether/pull/138) verified the SecureTransport server path empirically.
- **JVM side:** `ktor-server-cio` on JVM **also** rejects HTTPS connectors at start — `CIOApplicationEngine.kt:197` throws `UnsupportedOperationException("CIO Engine does not currently support HTTPS")` regardless of platform. JVM `FileServer.actual` therefore swaps to Ktor Netty (`ktor-server-netty`) for TLS, retaining the `sslConnector` + `X509TrustManager` SPKI-pinning path that the Java ecosystem provides. JVM client stays on CIO — Ktor CIO **client** on JVM has TLS via `ktor-network-tls/jvmMain` (uses standard `SSLEngine`); the Native stub does not apply.

This means **"Ktor CIO server everywhere" has a known expiry date on every server-side target.** The decision below is honest about that.

## Decision drivers

| Driver | Why it matters for Tether |
|---|---|
| Coverage across Android / JVM / iosArm64 / iosSimulatorArm64 / macosArm64 | The cross-platform promise is the product. A stack that splits along JVM/Native lines duplicates surface and bugs. |
| Server-side support on Kotlin/Native | iOS and macOS must receive, not just send. Ktor server was JVM-only until 3.0. |
| Streaming uploads/downloads without buffering | Product invariant from [file-transfer/spec.md](../../product/features/file-transfer/spec.md): "A file of any size goes through ... Streaming, not buffering." |
| HTTP/1.1 correctness | `Expect: 100-continue`, chunked transfer encoding, header folding, close semantics. Failures here surface as silent truncation or curl-incompatibility. |
| Socket-level control (TCP keepalive, timeouts, SO_RCVBUF/SNDBUF) | Long transfers over flaky Wi-Fi need keepalive. Default request timeouts kill long uploads. |
| Coroutine ergonomics | The rest of the stack is structured concurrency; transport must integrate without bridging. |
| Future TLS-pinned transport ([#140](https://github.com/khmelevartem/tether/issues/140)) | Engine must support TLS termination on the server side and pinning on the client side without re-architecting. |
| iOS app size, ObjC bridge cost on Apple | Apple-only engines pay cinterop tax; pure-Kotlin engines avoid it. |
| Maturity | CIO Native is younger than CIO JVM. Hand-rolled HTTP is mature only if we maintain it. |
| Debuggability | curl, browser dev tools, future relay tooling all assume HTTP. |

Explicitly **out of scope** as a driver: HTTP/2. Tether is one upload per connection; HTTP/2 multiplexing has no use here.

## Options considered

### Option 1 — Status quo: Ktor CIO client + server everywhere

Single engine, common code path on every target. `ktor-server-cio` is published for `iosArm64`, `iosSimulatorArm64`, `macosArm64`, JVM, Android ART (the official native-server doc lists `macosArm64`, `linuxX64/Arm64`, `mingwX64` explicitly; iOS targets are not in that doc list but the artifacts are published and the project's [appleTest/FileServerTest.kt](../../../composeApp/src/appleTest/kotlin/com/tubetoast/tether/network/FileServerTest.kt) round-trip suite passes on `iosSimulatorArm64`).

### Option 2 — Per-engine split: Netty / Jetty server on JVM, CIO Native on Apple

Drop CIO on the JVM side in favour of `ktor-server-netty`. Apple keeps CIO Native because Netty has no Native publication. Client: keep CIO or switch to OkHttp on JVM + Darwin on Apple.

### Option 3 — Per-platform `actual` over non-Ktor primitives

JVM: Ktor (either engine). Apple: hand-rolled HTTP/1.1 over `Network.framework` (`nw_listener_t`) or GCDWebServer via cinterop. Discussed in [adr-apple-fileserver-engine.md](adr-apple-fileserver-engine.md) under "Considered alternatives" and rejected there.

### Option 4 — Self-rolled HTTP/1.1 in `commonMain`

`io.ktor.network.sockets.aSocket()` (`ktor-network` artefact) is published for all our targets — JVM and Native incl. iOS / macOS arm64. Write a minimal HTTP/1.1 parser (request line + headers + chunked TE + Content-Length + Expect: 100-continue) on top. Same code on every platform.

### Option 5 — Drop HTTP, use raw TCP with a custom framing

`length-prefixed-frame { type, payload }` over a `ktor-network` socket. No HTTP semantics at all.

## Comparison

| | 1. Ktor CIO everywhere (status quo) | 2. Netty/Jetty on JVM + CIO Native on Apple | 3. Ktor JVM + non-Ktor Apple | 4. Self-rolled HTTP on `ktor-network` | 5. Raw TCP custom framing |
|---|---|---|---|---|---|
| All five targets covered with one code path | ✅ pre-TLS / ❌ post-#140 (Apple → SecureTransport + `ktor-http-cio` parser; JVM → Netty — both forced, not chosen) | ❌ (two server engines) | ❌ (two server stacks) | ✅ | ✅ |
| Server on Kotlin/Native | ✅ CIO Native | ✅ CIO Native (Apple side only) | ⚠️ requires hand-roll / GCDWebServer | ✅ via `ktor-network` | ✅ via `ktor-network` |
| Streaming body — first class | ✅ `ByteReadChannel` | ✅ on both sides | ⚠️ depends on choice | ⚠️ we own back-pressure | ⚠️ we own everything |
| HTTP/1.1 correctness | ⚠️ several known CIO gotchas (see below) | ✅ Netty is the most battle-tested HTTP/1.1 impl in the JVM ecosystem; CIO Native still in play on Apple → only partial win | ⚠️ Apple half is custom | ❌ we maintain the parser | n/a (no HTTP) |
| `Expect: 100-continue` | ⚠️ CIO server bug, **fixed in Ktor 3.2** (see [3.2 changelog](https://ktor.io/changelog/3.2/)) | ✅ Netty handles correctly; Apple half still on CIO 3.x | ⚠️ depends | ❌ we implement | n/a |
| Per-socket `SO_KEEPALIVE` knob | ❌ CIO server has no API for accepted sockets ([KTOR-5572](https://youtrack.jetbrains.com/issue/KTOR-5572)) | ✅ Netty: `tcpKeepAlive` + `configureBootstrap { childOption(ChannelOption.SO_KEEPALIVE, true) }` on JVM only; Apple half remains without it | ⚠️ Apple half lacks it unless we use `nw_protocol_tcp` | ✅ we own the socket | ✅ we own the socket |
| Request / socket timeouts under our control | ✅ via `HttpTimeout` plugin and `CIOEngineConfig.requestTimeout` / `endpoint { connectTimeout … }` | ✅ | ✅ JVM half; ⚠️ Apple half custom | ✅ | ✅ |
| TLS upgrade path ([#140](https://github.com/khmelevartem/tether/issues/140)) | ⚠️ **JVM: CIO server rejects HTTPS at start** (`CIOApplicationEngine.kt:197`, `UnsupportedOperationException`); must swap to Netty for TLS. **Apple: CIO Native has no TLS** ([KTOR-7262](https://youtrack.jetbrains.com/issue/KTOR-7262)); must swap to SecureTransport + `ktor-http-cio`. Decisions recorded in [adr-channel-encryption.md](adr-channel-encryption.md) (Amendment). | ✅ JVM half via Netty TLS already; Apple half forced to SecureTransport regardless | ⚠️ Apple-side TLS to wire ourselves (`nw_protocol_options_t` is a Swift value type — POC-verified unusable from K/N) | ❌ we wire TLS | ❌ we wire TLS |
| iOS app size / ObjC bridge cost | low (no cinterop) | low (no cinterop) | medium (cinterop on Apple) | low | low |
| Maturity | ⚠️ CIO Native young (3.0+, ~16 months) but covered by our integration tests | ⚠️ same caveat on Apple side | ⚠️ depends | ❌ HTTP parsers are famously bug-prone | ✅ trivially correct (no protocol) |
| Debuggability via curl / browsers / Wireshark | ✅ | ✅ | ✅ | ✅ | ❌ custom protocol — no tooling |
| Code volume to maintain | small | small | medium (two server impls) | large (parser + framing) | medium |
| Reaches Linux / Windows (post-MVP, JVM) | ✅ | ✅ | ✅ | ✅ | ✅ |

## Decision

**Stay on Ktor CIO for both client and server, on all targets where it works, for now (Option 1) — accepted as the minimum-risk choice, not as the unconditionally optimal one.**

The honest framing: once [#140](https://github.com/khmelevartem/tether/issues/140) lands, **the Ktor CIO server engine cannot stay on any target** — Apple is blocked by [KTOR-7262](https://youtrack.jetbrains.com/issue/KTOR-7262) (no TLS on Native at all), JVM by `CIOApplicationEngine.kt:197` (HTTPS connector explicitly rejected with `UnsupportedOperationException`). The "single code path on all five targets" property holds only for the plain-HTTP era. What remains true under this decision:

- **Server, pre-TLS:** keep Ktor CIO everywhere. Works today on all targets; migrating now is churn.
- **Server, post-#140:** the engine swaps are forced, not chosen. JVM → Netty (only in-tree Ktor server engine that honours `sslConnector`). Apple → SecureTransport + `ktor-http-cio` parser. Both decisions live in [adr-channel-encryption.md](adr-channel-encryption.md) (see the 2026-05-16 Amendment); this network-stack ADR does not duplicate them.
- **Client, JVM:** keep Ktor CIO indefinitely — `ktor-network-tls` has a real JVM implementation (`SSLEngine`-backed). Custom `X509TrustManager` for SPKI pinning is the standard route. No forced swap.
- **Client, Apple:** must leave Ktor CIO at TLS time — same `ktor-network-tls/nonJvmMain` stub that blocks the server also blocks the client (verified against `ktor-network-tls-iosarm64-3.1.3-sources.jar`). Replacement is one of: SecureTransport-wrapped raw TCP client (reuses Apple server's TLS plumbing), or Ktor Darwin engine (NSURLSession-backed, with `handleChallenge` SPKI pinning, plus future `URLSessionConfiguration.background` upside for iOS-as-sender). Pre-flight spike in #140 picks between them; result recorded back into the encryption ADR.

The "minimum-risk" framing of the original Decision survives **only for the plain-HTTP era** — every server-side and Apple-client-side engine choice flips at TLS time, and those flips are forced by upstream Ktor limits, not by our preference. The right question to ask, then, is: should we pre-empt the JVM-server engine swap and migrate to Netty *now* — before TLS — to avoid doing two server rewrites in close succession?

**Answer for this ADR: no, defer the JVM Netty migration to #140's scope.** Reasons:

1. The current `ktor-server-cio` JVM is working in production paths and exercised by `allTests` + smoke; replacing it pre-TLS for no current bug is the textbook "wrong time to refactor".
2. The Netty migration on JVM should land in the same PR that wires Apple's SecureTransport, because both are part of the same product-visible change ("transfers are now encrypted end-to-end") and want one shared test surface — see #140 DoD.
3. The Netty swap on JVM is a smaller change than the Apple SecureTransport bridge by an order of magnitude (a few config lines vs ~400 lines of SSL plumbing) — combining them does not produce an outsized PR.

So this ADR's positive recommendation is: **keep Ktor CIO server+client across all targets today; expect Netty + SecureTransport-or-Darwin swaps inside #140's PR, not as separate work; tune CIO timeouts and `Expect: 100-continue` handling as the immediate plain-HTTP follow-ups (#113, #25, #119).**

The "Revisit if" section below carries two explicit follow-up triggers that flow directly from the above:

1. If the app-layer keepalive ping turns out to be insufficient against real-world stalls, **Netty on JVM** is the queued replacement, not "do nothing".
2. The **client-side engine on Apple** (CIO Native vs Darwin / `NSURLSession`) is unsettled. Background-iOS sender support depends on `URLSessionConfiguration.background` ([ios-background-networking.md](../../knowledge/ios-background-networking.md)) — only the Darwin engine can plausibly reach that. If iOS-as-sender background ever becomes a roadmap item, the Apple client engine swap is on the table independently of this ADR.

Neither follow-up blocks the current decision; both are flagged so future readers see them as known soft spots, not gaps in the review.

What changes alongside this decision:

1. **Upgrade Ktor 3.1.3 → 3.2.x.** The `Expect: 100-continue` malformed response from CIO server (driving [#25](https://github.com/khmelevartem/tether/issues/25)) is fixed in 3.2 per the [3.2 changelog](https://ktor.io/changelog/3.2/). Carries 3.2's other CIO fixes for free.
2. ~~**Set explicit timeouts on `FileClient`.**~~ Landed in [#113](https://github.com/khmelevartem/tether/issues/113) ([PR #160](https://github.com/khmelevartem/tether/pull/160)) before this ADR. `HttpTimeout` configured with `requestTimeoutMillis = INFINITE`, finite `connectTimeoutMillis`, dropped `socketTimeoutMillis` in favour of an application-layer watchdog on stalled uploads. Listed for historical completeness.
3. **Accept that `SO_KEEPALIVE` is not configurable on CIO server accept'ed sockets** ([KTOR-5572](https://youtrack.jetbrains.com/issue/KTOR-5572)). The failed reflection-into-CIO-internals attempt in [#164](https://github.com/khmelevartem/tether/issues/164) is the right diagnostic — that surface doesn't exist. Mitigate at the application layer (periodic in-band ping frames during long transfers) and via Android-side `WifiLock` + `WakeLock` ([#150](https://github.com/khmelevartem/tether/issues/150)).

## Costs accepted

1. **No per-socket TCP keepalive on the server side.** Documented above. The application-layer mitigation costs us a small protocol extension but does not require a new engine.
2. **CIO server has no HTTP/2.** Not on the Tether roadmap.
3. **CIO Native iOS server is published-and-tested but not officially listed in the Native server targets doc** ([server-native.html](https://ktor.io/docs/server-native.html) names macOS / Linux / Windows native, not iOS). Our [appleTest/FileServerTest.kt](../../../composeApp/src/appleTest/kotlin/com/tubetoast/tether/network/FileServerTest.kt) is the safety net. The window during which this matters is short — [#140](https://github.com/khmelevartem/tether/issues/140) replaces this engine on Apple entirely.
5. **The Apple side has a known expiry date as a Ktor CIO server consumer.** Once [#140](https://github.com/khmelevartem/tether/issues/140) lands, `composeApp/src/appleMain/.../FileServer.apple.kt` no longer uses Ktor's server engine — only its HTTP-parsing library. This ADR's "single code path" property is therefore time-bounded on Apple. JVM/Android remain on full Ktor CIO indefinitely (TLS is supported there via `sslConnector`).
4. **CIO throughput is below Netty's** ([community report: ~50% lower req/min on a microbenchmark](https://github.com/raharrison/kotlin-ktor-exposed-starter/issues/17)). For a single-upload-per-connection file transfer this is irrelevant — the bottleneck is wire bandwidth, not engine req/sec.

## Revisit if

- **CIO Native server destabilises on iOS** (a Ktor release breaks our iosTest, runtime hangs in production, throughput regressions vs JVM). Fallback: hand-rolled HTTP/1.1 on `ktor-network` sockets, isolated behind the existing `expect/actual` boundary on the Apple side only.
- **A per-socket `SO_KEEPALIVE` requirement becomes load-bearing** (concrete user reports of mid-transfer stalls that the application-layer ping cannot mitigate). Trigger: file a Netty-on-JVM split for the server engine — the post-#140 asymmetry already pays the parity cost, so adding a JVM/Native engine split on top costs less than it would have pre-#140. Netty exposes `tcpKeepAlive` + `configureBootstrap { childOption(ChannelOption.SO_KEEPALIVE, true) }` for per-accepted-socket control. Apple side unaffected (already on SecureTransport).
- **iOS-as-sender background uploads become a roadmap item** (see [ios-background-networking.md](../../knowledge/ios-background-networking.md)). `URLSessionConfiguration.background` is the only Apple-sanctioned channel; it's reachable from Kotlin through the Darwin engine, not CIO Native client. Trigger: swap Apple-side `FileClient` engine to Darwin. JVM/Android client unaffected.
- **`ktor-http-cio` standalone parser path proves unworkable on Apple during the [#140](https://github.com/khmelevartem/tether/issues/140) pre-flight spike.** Per [adr-channel-encryption.md](adr-channel-encryption.md), the fallback is a hand-rolled HTTP/1.1 parser on Apple (~150 lines; Tether uses no chunked TE, no header folding, no pipelining). Network-stack-wise this still keeps HTTP as the wire protocol — only the parser implementation drifts further from Ktor on Apple.
- **Ktor ships TLS on Kotlin/Native** ([KTOR-7262](https://youtrack.jetbrains.com/issue/KTOR-7262) closes). Re-evaluate whether the Apple side can re-converge onto Ktor CIO server — the encryption ADR's `Revisit if` list calls this out as well.
- **Ktor server gets a stable native-engine refresh** (Ktor 4 / new engine `ktor-server-cio2` / Netty-equivalent on Native). Reassess whether the parity argument now favours moving.
- **The Apple receive path is dropped** (product decision: iOS/macOS become sender-only — see [ios-background-networking.md](../../knowledge/ios-background-networking.md) for context that makes this thinkable). Frees us to pick any JVM-only server engine.

## Consequences

Status of currently open transport-layer issues, in the light of this decision:

| Issue | Status after this ADR |
|---|---|
| [#119](https://github.com/khmelevartem/tether/issues/119) — Transport reliability hardening (umbrella) | Reframed: this is the implementation umbrella for the three follow-ups below. The stack stays as is. |
| [#113](https://github.com/khmelevartem/tether/issues/113) — `FileClient` 15s default timeout | **Closed** ([PR #160](https://github.com/khmelevartem/tether/pull/160), 2026-05-16). `HttpTimeout` installed with infinite request timeout + app-layer stalled-upload watchdog. CIO was correct; our config was missing. |
| [#25](https://github.com/khmelevartem/tether/issues/25) — `Expect: 100-continue` curl incompatibility | Closeable on Ktor 3.2 bump per the [3.2 changelog](https://ktor.io/changelog/3.2/). |
| [#91](https://github.com/khmelevartem/tether/issues/91) — Android-emulator hang | Likely emulator NAT / mDNS issue, not engine. Stays open; emulator-specific networking diagnostic, not an engine choice. |
| [#164](https://github.com/khmelevartem/tether/issues/164) — per-socket TCP keepalive | Rescope: application-layer ping during active transfer instead of CIO-internal reflection. `SO_KEEPALIVE` on accepted sockets is upstream-blocked ([KTOR-5572](https://youtrack.jetbrains.com/issue/KTOR-5572)). |

No new code lands as part of this ADR; the listed follow-ups go in their own PRs.

**Relationship to [#140](https://github.com/khmelevartem/tether/issues/140) / [adr-channel-encryption.md](adr-channel-encryption.md):** zero overlap of scope, hard sequencing dependency. This ADR governs the pre-TLS plain-HTTP transport. The encryption ADR governs what replaces the Apple half of that transport once TLS ships. They co-exist, with the encryption ADR taking precedence on the Apple side post-#140.

## References

- Ktor docs: [client-engines.html](https://ktor.io/docs/client-engines.html), [server-engines.html](https://ktor.io/docs/server-engines.html), [server-native.html](https://ktor.io/docs/server-native.html), [client-timeout.html](https://ktor.io/docs/client-timeout.html).
- Release notes: [Ktor 3.0](https://blog.jetbrains.com/kotlin/2024/10/ktor-3-0/), [Ktor 3.1](https://blog.jetbrains.com/kotlin/2025/02/ktor-3-1-0-release/), [Ktor 3.2](https://blog.jetbrains.com/kotlin/2025/06/ktor-3-2-0-is-now-available/), [3.2 changelog](https://ktor.io/changelog/3.2/).
- YouTrack tickets cited: [KTOR-5572](https://youtrack.jetbrains.com/issue/KTOR-5572) (no `SO_KEEPALIVE` on CIO server socket), [KTOR-5056](https://youtrack.jetbrains.com/issue/KTOR-5056) (CIO request timeout on large transfers), [KTOR-4858](https://youtrack.jetbrains.com/issue/KTOR-4858) (CIO + Android emulator).
- [adr-apple-fileserver-engine.md](adr-apple-fileserver-engine.md) — narrower Apple-side decision; this ADR is its broader sibling and does not contradict it.
- [docs/knowledge/ktor-server-cio.md](../../knowledge/ktor-server-cio.md) — accumulated CIO gotchas; living doc.
- [docs/knowledge/ios-background-networking.md](../../knowledge/ios-background-networking.md) — iOS-side architectural constraints that frame the "stay on HTTP" assumption.
- [docs/product/tech-stack.md](../../product/tech-stack.md) — references this ADR as the network-stack rationale.
