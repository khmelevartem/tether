# Network stack — Ktor CIO (client + server) across all KMP targets

**Status:** Accepted — 2026-05-16
**Issue:** [#166](https://github.com/khmelevartem/tether/issues/166)

## Context

Tether's transport is a symmetric HTTP layer: every running instance hosts an HTTP server ([`FileServer`](../../../composeApp/src/commonMain/kotlin/com/tubetoast/tether/network/FileServer.kt)) on a random ephemeral port and is also an HTTP client ([`FileClient`](../../../composeApp/src/commonMain/kotlin/com/tubetoast/tether/network/FileClient.kt)) to other peers. Discovery is mDNS, decoupled and out of scope here.

Active KMP targets: `androidTarget`, `jvm("desktop")`, `iosArm64`, `iosSimulatorArm64`, `macosArm64`. Source-set hierarchy puts `jvmMain` as the intermediate parent of `androidMain` + `desktopMain`, and `appleMain` as the parent of `iosMain` + `macosMain`. The server stack lives in `commonMain` (Ktor 3.1.3) since Ktor 3.0 publishes `ktor-server-cio` for Native; the per-platform `actual`s differ only in the filesystem sink ([`FileServer.kt`](../../../composeApp/src/jvmMain/kotlin/com/tubetoast/tether/network/FileServer.kt) vs [`FileServer.apple.kt`](../../../composeApp/src/appleMain/kotlin/com/tubetoast/tether/network/FileServer.apple.kt)).

The original choice of CIO on both sides was a fast-research decision — not documented as a deliberate selection until now. A cluster of issues ([#113](https://github.com/khmelevartem/tether/issues/113), [#25](https://github.com/khmelevartem/tether/issues/25), [#91](https://github.com/khmelevartem/tether/issues/91), [#119](https://github.com/khmelevartem/tether/issues/119), [#164](https://github.com/khmelevartem/tether/issues/164)) and the workaround in [docs/knowledge/ktor-server-cio.md](../../knowledge/ktor-server-cio.md) prompted this re-evaluation. The narrower Apple-engine choice is already recorded in [adr-apple-fileserver-engine.md](adr-apple-fileserver-engine.md); this ADR is the broader, project-wide decision that subsumes it.

**Hard adjacent constraint — channel encryption ([adr-channel-encryption.md](adr-channel-encryption.md), implemented by [#140](https://github.com/khmelevartem/tether/issues/140)):** that ADR is already Accepted. It states that as soon as TLS-with-paired-key-pinning ships, the Apple `FileServer.actual` is rewritten on **SecureTransport directly** with `ktor-http-cio` providing HTTP parsing — Ktor's *server engine* on Apple is dropped. Reason: Ktor CIO has no TLS implementation on Kotlin/Native at all — the code path throws `IllegalStateException("TLS sessions are not supported on Native platform")` ([KTOR-7262](https://youtrack.jetbrains.com/issue/KTOR-7262), open since 2020, [KTOR-7475](https://youtrack.jetbrains.com/issue/KTOR-7475), [KTOR-5912](https://youtrack.jetbrains.com/issue/KTOR-5912)). The POC in [PR #138](https://github.com/khmelevartem/tether/pull/138) verified the SecureTransport path empirically. This means **the "Ktor CIO server everywhere" status quo this ADR is about has a known expiry date on the Apple side**, and the decision below is honest about that.

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
| All five targets covered with one code path | ✅ pre-TLS / ❌ post-#140 (Apple drifts to SecureTransport + `ktor-http-cio` parser per [adr-channel-encryption.md](adr-channel-encryption.md)) | ❌ (two server engines) | ❌ (two server stacks) | ✅ | ✅ |
| Server on Kotlin/Native | ✅ CIO Native | ✅ CIO Native (Apple side only) | ⚠️ requires hand-roll / GCDWebServer | ✅ via `ktor-network` | ✅ via `ktor-network` |
| Streaming body — first class | ✅ `ByteReadChannel` | ✅ on both sides | ⚠️ depends on choice | ⚠️ we own back-pressure | ⚠️ we own everything |
| HTTP/1.1 correctness | ⚠️ several known CIO gotchas (see below) | ✅ Netty is the most battle-tested HTTP/1.1 impl in the JVM ecosystem; CIO Native still in play on Apple → only partial win | ⚠️ Apple half is custom | ❌ we maintain the parser | n/a (no HTTP) |
| `Expect: 100-continue` | ⚠️ CIO server bug, **fixed in Ktor 3.2** (see [3.2 changelog](https://ktor.io/changelog/3.2/)) | ✅ Netty handles correctly; Apple half still on CIO 3.x | ⚠️ depends | ❌ we implement | n/a |
| Per-socket `SO_KEEPALIVE` knob | ❌ CIO server has no API for accepted sockets ([KTOR-5572](https://youtrack.jetbrains.com/issue/KTOR-5572)) | ✅ Netty: `tcpKeepAlive` + `configureBootstrap { childOption(ChannelOption.SO_KEEPALIVE, true) }` on JVM only; Apple half remains without it | ⚠️ Apple half lacks it unless we use `nw_protocol_tcp` | ✅ we own the socket | ✅ we own the socket |
| Request / socket timeouts under our control | ✅ via `HttpTimeout` plugin and `CIOEngineConfig.requestTimeout` / `endpoint { connectTimeout … }` | ✅ | ✅ JVM half; ⚠️ Apple half custom | ✅ | ✅ |
| TLS upgrade path ([#140](https://github.com/khmelevartem/tether/issues/140)) | ⚠️ JVM/Android: Ktor `sslConnector` handles it. **Apple: Ktor CIO Native has no TLS at all** ([KTOR-7262](https://youtrack.jetbrains.com/issue/KTOR-7262)) — must drop the engine, go to SecureTransport + `ktor-http-cio` parser. Decision already taken in [adr-channel-encryption.md](adr-channel-encryption.md). | ✅ JVM half via Netty TLS; Apple half hits the same KTOR-7262 wall | ⚠️ Apple-side TLS to wire ourselves (Network.framework gives it, but `nw_protocol_options_t` is a Swift value type — POC-verified unusable from K/N) | ❌ we wire TLS | ❌ we wire TLS |
| iOS app size / ObjC bridge cost | low (no cinterop) | low (no cinterop) | medium (cinterop on Apple) | low | low |
| Maturity | ⚠️ CIO Native young (3.0+, ~16 months) but covered by our integration tests | ⚠️ same caveat on Apple side | ⚠️ depends | ❌ HTTP parsers are famously bug-prone | ✅ trivially correct (no protocol) |
| Debuggability via curl / browsers / Wireshark | ✅ | ✅ | ✅ | ✅ | ❌ custom protocol — no tooling |
| Code volume to maintain | small | small | medium (two server impls) | large (parser + framing) | medium |
| Reaches Linux / Windows (post-MVP, JVM) | ✅ | ✅ | ✅ | ✅ | ✅ |

## Decision

**For the plain-HTTP (pre-TLS) transport: stay on Ktor CIO for both client and server across all five targets (Option 1).** The premise of the original quick-research choice holds up: every alternative either splits the stack along JVM/Native lines (Option 2 and 3), trades engine maturity for parser-maintenance work (Option 4), or loses HTTP tooling for marginal gain (Option 5). The pain points that triggered this ADR are tuning gaps and a few CIO bugs, not architectural failures of the engine choice.

**For the post-TLS transport: defer to [adr-channel-encryption.md](adr-channel-encryption.md).** That ADR has already chosen the Apple-side path: SecureTransport + `ktor-http-cio` parser + a small in-file route table, replacing the Ktor CIO server engine on `appleMain` only. JVM/Android stay on Ktor CIO with `sslConnector`. The asymmetric server implementation post-#140 is an accepted cost of channel encryption, not of this network-stack choice — channel encryption forces the engine swap on Apple regardless of which transport ADR was in place. The encryption ADR remains the canonical source for the Apple TLS server architecture; this ADR does not contradict it.

What changes alongside this decision:

1. **Upgrade Ktor 3.1.3 → 3.2.x.** The `Expect: 100-continue` malformed response from CIO server (driving [#25](https://github.com/khmelevartem/tether/issues/25)) is fixed in 3.2 per the [3.2 changelog](https://ktor.io/changelog/3.2/). Carries 3.2's other CIO fixes for free.
2. **Set explicit timeouts on `FileClient`.** Default `requestTimeoutMillis` of 15s under `CIOEngineConfig` ([client-timeout.html](https://ktor.io/docs/client-timeout.html)) is incompatible with the streaming invariant. Install `HttpTimeout` and set `requestTimeoutMillis = INFINITE` (or unset); keep `connectTimeoutMillis` and `socketTimeoutMillis` finite.
3. **Accept that `SO_KEEPALIVE` is not configurable on CIO server accept'ed sockets** ([KTOR-5572](https://youtrack.jetbrains.com/issue/KTOR-5572)). The failed reflection-into-CIO-internals attempt in [#164](https://github.com/khmelevartem/tether/issues/164) is the right diagnostic — that surface doesn't exist. Mitigate at the application layer (periodic in-band ping frames during long transfers) and via Android-side `WifiLock` + `WakeLock` ([#150](https://github.com/khmelevartem/tether/issues/150)).

## Costs accepted

1. **No per-socket TCP keepalive on the server side.** Documented above. The application-layer mitigation costs us a small protocol extension but does not require a new engine.
2. **CIO server has no HTTP/2.** Not on the Tether roadmap.
3. **CIO Native iOS server is published-and-tested but not officially listed in the Native server targets doc** ([server-native.html](https://ktor.io/docs/server-native.html) names macOS / Linux / Windows native, not iOS). Our [appleTest/FileServerTest.kt](../../../composeApp/src/appleTest/kotlin/com/tubetoast/tether/network/FileServerTest.kt) is the safety net. The window during which this matters is short — [#140](https://github.com/khmelevartem/tether/issues/140) replaces this engine on Apple entirely.
5. **The Apple side has a known expiry date as a Ktor CIO server consumer.** Once [#140](https://github.com/khmelevartem/tether/issues/140) lands, `composeApp/src/appleMain/.../FileServer.apple.kt` no longer uses Ktor's server engine — only its HTTP-parsing library. This ADR's "single code path" property is therefore time-bounded on Apple. JVM/Android remain on full Ktor CIO indefinitely (TLS is supported there via `sslConnector`).
4. **CIO throughput is below Netty's** ([community report: ~50% lower req/min on a microbenchmark](https://github.com/raharrison/kotlin-ktor-exposed-starter/issues/17)). For a single-upload-per-connection file transfer this is irrelevant — the bottleneck is wire bandwidth, not engine req/sec.

## Revisit if

- **CIO Native server destabilises on iOS** (a Ktor release breaks our iosTest, runtime hangs in production, throughput regressions vs JVM). Fallback: hand-rolled HTTP/1.1 on `ktor-network` sockets, isolated behind the existing `expect/actual` boundary on the Apple side only.
- **A per-socket `SO_KEEPALIVE` requirement becomes load-bearing** (concrete user reports of mid-transfer stalls that the application-layer ping cannot mitigate). Trigger: file a Netty-on-JVM split for the server engine, accept the duplication.
- **`ktor-http-cio` standalone parser path proves unworkable on Apple during the [#140](https://github.com/khmelevartem/tether/issues/140) pre-flight spike.** Per [adr-channel-encryption.md](adr-channel-encryption.md), the fallback is a hand-rolled HTTP/1.1 parser on Apple (~150 lines; Tether uses no chunked TE, no header folding, no pipelining). Network-stack-wise this still keeps HTTP as the wire protocol — only the parser implementation drifts further from Ktor on Apple.
- **Ktor ships TLS on Kotlin/Native** ([KTOR-7262](https://youtrack.jetbrains.com/issue/KTOR-7262) closes). Re-evaluate whether the Apple side can re-converge onto Ktor CIO server — the encryption ADR's `Revisit if` list calls this out as well.
- **Ktor server gets a stable native-engine refresh** (Ktor 4 / new engine `ktor-server-cio2` / Netty-equivalent on Native). Reassess whether the parity argument now favours moving.
- **The Apple receive path is dropped** (product decision: iOS/macOS become sender-only — see [ios-background-networking.md](../../knowledge/ios-background-networking.md) for context that makes this thinkable). Frees us to pick any JVM-only server engine.

## Consequences

Status of currently open transport-layer issues, in the light of this decision:

| Issue | Status after this ADR |
|---|---|
| [#119](https://github.com/khmelevartem/tether/issues/119) — Transport reliability hardening (umbrella) | Reframed: this is the implementation umbrella for the three follow-ups below. The stack stays as is. |
| [#113](https://github.com/khmelevartem/tether/issues/113) — `FileClient` 15s default timeout | Fix as-is: install `HttpTimeout` with infinite request timeout on `FileClient`. CIO is correct here, our config is missing. |
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
