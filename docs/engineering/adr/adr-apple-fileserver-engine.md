# Apple FileServer — Ktor CIO on Kotlin/Native over hand-rolled HTTP

**Status:** Accepted — 2026-05-10
**Issue:** [#81](https://github.com/khmelevartem/tether/issues/81)
**Note (2026-05-25):** `macosArm64` Kotlin/Native target removed from the build — see [adr-macos-native-vs-jvm.md](adr-macos-native-vs-jvm.md) §Reversal. The Apple `FileServer` now runs on iOS only; the engine choice and CIO-on-Native rationale below still apply.

## Context

[FileServer](../../../composeApp/src/commonMain/kotlin/com/tubetoast/tether/network/FileServer.kt) is the receive-side HTTP listener: `GET /health` plus `POST /upload?name=<filename>` with a streamed body. The JVM `actual` ([FileServer.kt](../../../composeApp/src/jvmMain/kotlin/com/tubetoast/tether/network/FileServer.kt)) runs on `ktor-server-cio` and is shared between Desktop and Android. Until #81 the Apple `actual` was a stub that threw on `start()`, leaving iOS (and macOS) unable to receive — the device announced itself over mDNS but any inbound TCP would fail.

The earlier [adr-macos-native-vs-jvm.md](adr-macos-native-vs-jvm.md) noted "Ktor server doesn't run on Kotlin/Native" as a known cost of choosing macOS Native. **That has changed.** Ktor 3.0 (October 2024) shipped a Kotlin/Native port of the CIO server engine, published for `iosArm64`, `iosSimulatorArm64`, `macosArm64`, and other Native targets. We are on 3.1.3.

This ADR records the engine choice for the Apple-side `FileServer.actual`.

## Decision drivers

| | Ktor `ktor-server-cio` (Native) | Hand-rolled (NSStream / POSIX) | Apple-only (Network.framework) |
|---|---|---|---|
| HTTP/1.1 parser | ✅ provided | ❌ write our own (chunked TE, header folding, partial reads) | ⚠️ low-level — would still need a parser layer |
| Behaviour parity with JVM `FileServer` | ✅ same engine, same routing DSL, same streaming model | ❌ separate code path, separate bug surface | ❌ separate code path |
| Code volume | ~150 lines of platform actual | likely 400+ lines (parser + listener + lifecycle) | similar to hand-rolled, plus ObjC bridging |
| TLS upgrade path (future) | ✅ Ktor engine handles it | ❌ have to implement it ourselves | ✅ TLS via `nw_protocol_tls` |
| Risk: engine maturity on Native | ⚠️ relatively new (Ktor 3.0+) | ✅ POSIX stable | ✅ stable Apple-supported |
| Risk: ObjC delegate / run-loop interaction | low — coroutine-driven | medium (NSStream callbacks, see [apple-platform.md](../../knowledge/apple-platform.md)) | high — every API delivers via run loop |

## Decision

**The Apple `FileServer.actual` uses `ktor-server-cio` on Kotlin/Native**, mirroring the JVM implementation route-for-route. The server-stack dependencies (`ktor-server-core`, `ktor-server-cio`, `ktor-server-content-negotiation`) move from the `jvmMain` source set up to `commonMain`. Streaming the upload body to disk uses POSIX `fopen`/`fwrite` (the smallest dependency to express "write a `ByteReadChannel` to a file path on Native"). Filesystem metadata operations — directory creation, existence checks, deletion — go through `NSFileManager`.

The server listens on port 0 (OS-assigned ephemeral) and writes received files to `<NSDocumentDirectory>/Tether/`. The downloads-directory choice and its UX implications (Files.app exposure via `UIFileSharingEnabled`, iCloud backup, receive-side notifications) are tracked as open product questions in [file-transfer.md](../../product/features/file-transfer/spec.md) — out of scope for this ADR. (See Amendment — 2026-06-15.)

### Why CIO Native over hand-rolled

1. **No HTTP parser to maintain.** The JVM impl gets HTTP/1.1 framing, query parsing, status codes, and chunked transfer encoding from Ktor. A hand-rolled NSStream or POSIX listener would need all of that — large surface for protocol bugs, every one of which would also have a JVM-side counterpart written differently.
2. **One mental model, two actuals.** The JVM and Apple `FileServer.actual`s now read the same: `embeddedServer(CIO, port) { routing { get("/health"); post("/upload"); post("/batch-begin"); post("/batch-cancel") } }`. Reviewers and future contributors don't context-switch between two server architectures when changing the protocol.
3. **TLS later is cheaper.** When channel encryption lands ([security.md](../../security/README.md)), Ktor's engine already understands TLS. With a hand-rolled listener we'd be writing TLS handling ourselves on Native.

### Costs accepted

1. **CIO Native is younger than its JVM counterpart.** Mitigation: the round-trip integration tests in [appleTest/FileServerTest.kt](../../../composeApp/src/appleTest/kotlin/com/tubetoast/tether/network/FileServerTest.kt) exercise health, upload streaming, error paths, and lifecycle on `iosSimulatorArm64`. If a CIO-Native bug surfaces in production, the revisit triggers below kick in.
2. **POSIX `fopen`/`fwrite` for the file sink is unidiomatic on iOS.** Alternatives (`NSFileHandle.fileHandleForWritingAtPath:`, `kotlinx-io` `SystemFileSystem`) were attempted first; the K/N binding for `NSFileHandle.fileHandleForWritingAtPath` did not resolve cleanly, and pulling `kotlinx-io` as a direct dependency widens the surface for one helper. POSIX is the smallest stable interface.

## Considered alternatives

- **Hand-rolled NSStream + ad-hoc HTTP parser.** Rejected: writing a correct HTTP/1.1 parser and chunked-TE decoder is high-effort high-risk, and we would maintain two protocol implementations. Acceptable only as fallback if CIO Native turns out to be unviable.
- **Apple `Network.framework` (`nw_listener_t`).** Rejected for now: also requires writing the HTTP layer ourselves; the wins (TLS, native flow control) are real but only matter once we add TLS. Reasonable revisit when [security.md](../../security/README.md) lands.
- **`kotlinx-io` `SystemFileSystem` for the file sink.** Rejected for this iteration: fine API, but currently brings no advantage over POSIX while widening the dependency footprint. Worth revisiting if the JVM `FileServer.actual` is also migrated off `java.io` for cross-platform parity.

## Revisit if

- **CIO Native flakes in production** (linker issues on a platform we ship to, runtime hangs, throughput regressions vs JVM) — fall back to a hand-rolled NSStream/POSIX listener for Apple targets only, isolate via the existing `expect/actual` boundary.
- **TLS / channel encryption lands.** Re-evaluate Network.framework for Apple targets — its first-class TLS may justify the bridge cost.
- **The JVM `FileServer.actual` migrates off `java.io`.** Aligning both actuals on `kotlinx-io` could remove the POSIX helper here and unify the streaming-write code path.

## Amendment — 2026-06-15

The downloads root is the app's `Documents/` directory (not a `Tether/` subfolder). It is exposed to the on-device Files app via `UIFileSharingEnabled` + `LSSupportsOpeningDocumentsInPlace`. The root is `Documents/` itself rather than a subfolder to avoid a nested `Tether → Tether` path visible in Files.

## References

- [#81](https://github.com/khmelevartem/tether/issues/81) — Apple FileServer implementation issue
- [adr-macos-native-vs-jvm.md](adr-macos-native-vs-jvm.md) — earlier decision; this ADR updates its premise about Ktor server availability on Native
- [docs/knowledge/apple-platform.md](../../knowledge/apple-platform.md) — Apple-target gotchas (delegate GC, NSRunLoop in tests, Local Network privacy)
- [Ktor releases](https://ktor.io/docs/releases.html) — server-engine Native support landed in 3.0
