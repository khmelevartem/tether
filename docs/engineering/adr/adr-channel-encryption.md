# Channel Encryption — TLS with paired-key pinning, SecureTransport on Apple Native

**Status:** Accepted — 2026-05-13
**Issue:** [#123](https://github.com/khmelevartem/tether/issues/123)
**POC:** [#138](https://github.com/khmelevartem/tether/pull/138) (closed without merge — decision-history artifact)

## Context

Until this ADR landed, [docs/product/security.md](../../product/security.md) listed three options for the transport between paired Tether devices as an open question: (A) TLS with self-signed certificates pinned to paired keys, (B) plain HTTP relying solely on pairing for authentication, (C) application-level encryption (Noise / libsodium-style payload encryption above HTTP). The default in-tree behaviour was effectively B, but it was never chosen — only deferred. Resolving it became urgent because:

- [#10](https://github.com/khmelevartem/tether/issues/10) (PIN handshake) and [#116](https://github.com/khmelevartem/tether/issues/116) (real EC P-256 keys via Apple Keychain with X.509 wire format) were converging on the same crypto material that any of A / C would consume. Without a fixed channel-encryption decision, neither could finalise the shape of the keys it produces.
- [#119](https://github.com/khmelevartem/tether/issues/119) (transport reliability hardening — CIO timeouts, `Expect: 100-continue`, large files) was about to settle plain-HTTP transport behaviour. Adding TLS on top later would re-open everything it touched.

[adr-apple-fileserver-engine.md](adr-apple-fileserver-engine.md) explicitly deferred the Apple-side transport choice to "when TLS lands" — that is this ADR.

## Decision drivers

| Criterion | A — TLS pinned | B — Plain HTTP | C — App-level (Noise / libsodium) |
|---|---|---|---|
| MITM on hostile Wi-Fi (passive / active) | both defeated | neither defeated | both defeated |
| Match with [threat model](../../product/security.md) priority 2 ("passive eavesdropper on open Wi-Fi") | yes | **no** | yes |
| Implementation across 4 targets | standard on JVM/Android; bespoke on Apple Native (POC-verified, see below) | nothing | no mature KMP-wide Noise/libsodium binding — would need per-target wrapper |
| Crypto correctness risk | low — battle-tested TLS implementations on every target | trivial — there is no crypto | **high** — streaming AEAD framing, replay, rekey are subtle and permanent maintenance |
| Throughput on 1 GB (target ≤15% vs plain) | ≤5% expected with hardware AES on all targets | baseline | unverified; framing layer adds variability |
| Compat with pairing ([#10](https://github.com/khmelevartem/tether/issues/10)) | exchanged keys are exactly the keys pinned | nothing required | needs an ECDH step on top of pairing plus session-key state |
| Compat with Apple keys ([#116](https://github.com/khmelevartem/tether/issues/116)) | reuses the X.509 SubjectPublicKeyInfo work; extends to a full self-signed cert | raw key sufficient | raw key sufficient |
| Impact on transport reliability ([#119](https://github.com/khmelevartem/tether/issues/119)) | TLS handshake must interleave with `Expect: 100-continue` and idle timeouts | no impact | no impact |

## Decision

**Option A.** After pairing, transport between two paired Tether devices is TLS over TCP. Each peer presents a self-signed EC P-256 certificate whose public key is the keypair already exchanged during pairing. Each peer verifies the other end's certificate by matching `SubjectPublicKeyInfo` against the value stored in `TrustedDeviceStore`. The system trust store is never consulted.

On JVM / Android — Ktor CIO server with `sslConnector`, Java `SSLContext` on the client side, custom `X509TrustManager` enforcing the SPKI pin. Standard, in-tree.

**On Kotlin/Native (Apple) — SecureTransport directly, not Ktor and not Network.framework.** The Apple `FileServer.actual` is rewritten on top of `SSLContext` / `SSLRead` / `SSLWrite` with `kSSLSessionOptionBreakOnServerAuth` / `kSSLSessionOptionBreakOnClientAuth` for the pin check. Ktor remains on JVM / Android; the wire protocol and observable behaviour are identical across all four targets, but the server engine on Apple is platform-specific.

The decision is final for MVP. There is no two-step "B now, A later" path — see "Considered and rejected" below.

## POC findings

A throwaway spike (`poc-tls-spike/` — see PR linked at the top of this ADR) confirmed the Apple-side path empirically before adoption. Scenarios run:

| # | Server | Client | Result |
|---|---|---|---|
| 1 | JVM (`SSLContext`, port 8443) | Apple Native (SecureTransport) | green — handshake + 100 MB payload + SHA-256 match |
| 2 | Apple Native (SecureTransport, port 8444) | JVM | green |
| 4 | Apple Native server | JVM client with corrupted pin constant | red — handshake fails, exit 1 |
| 5 | JVM server | Apple Native client with corrupted pin constant | red — handshake fails, exit 1 |

Scenarios 4 and 5 are load-bearing: they prove pinning actively rejects mismatched keys rather than silently falling through to the system trust store.

What changed our assumption between the original security.md draft and this ADR:

- **Network.framework is incompatible with Kotlin/Native cinterop in 2.x.** `nw_protocol_options_t` is a Swift value type at runtime; K/N's ObjC bridge expects `NSObject` and crashes on call. This applies to `nw_tls_create_options()` and the entire `nw_protocol_*` surface. There is no per-call workaround.
- **SecureTransport is deprecated by Apple but functional.** Pure C API, fully exposed via `platform.Security.*` in K/N for all Apple targets (`iosArm64`, `iosSimulatorArm64`, `macosArm64`). No Swift value types, no run-loop assumptions, blocking I/O. The POC only ran on `macosArm64` for ergonomics; the K/N klib for `iosArm64` exports the identical SSL* symbol set, so the same code shape transplants. iOS validation is part of the implementation issue.
- **Ktor CIO TLS on Kotlin/Native does not exist.** [KTOR-7262](https://youtrack.jetbrains.com/issue/KTOR-7262), [KTOR-7475](https://youtrack.jetbrains.com/issue/KTOR-7475), [KTOR-5912](https://youtrack.jetbrains.com/issue/KTOR-5912) — the code path throws `IllegalStateException("TLS sessions are not supported on Native platform")`. Confirmed by reading the Ktor source; no POC needed.

Practical gotchas the POC surfaced (for the implementation issue):

- `SSLRead` / `SSLWrite` fail with `paramErr (-50)` for buffers larger than ~64 KB. Chunk to 65536 bytes per call.
- Use `SSLNewContext(Boolean)` instead of `SSLCreateContext` — the K/N 2.3.20 cinterop does not export the `SSLProtocolSide` / `SSLConnectionType` enum constants.
- `SecPKCS12Import` returns identities as non-retained references; `CFRetain` the identity before `CFRelease` on the items array.
- macOS JDK uses IPv6 sockets with `IPV6_V6ONLY=1` by default; K/N POSIX sockets are IPv4. Launch the JVM side with `-Djava.net.preferIPv4Stack=true` or bind explicitly to `0.0.0.0`.
- Kotlin infix operator precedence is left-associative without grouping. `(v and 0xFF00) ushr 8 or (v and 0xFF) shl 8` does not parse as the byte-swap it looks like. Use explicit parens.

## Implementation scheme for Apple TLS in Tether

This is the engineering blueprint the implementation issue executes against. Names are intent, not API contracts.

### Layering

```
commonMain
  ├─ network/FileServer (expect class)                      [unchanged]
  ├─ network/FileClient (expect class)                      [unchanged]
  ├─ security/DeviceKeyPair (expect class)                  [from #116]
  ├─ security/TrustedDeviceStore                            [from #9; SPKI pinset source]
  └─ security/TlsIdentity (new, expect class)
       — wraps DeviceKeyPair into a self-signed X.509 cert,
         cached for the lifetime of the keypair.

jvmMain
  ├─ network/FileServer.kt    — Ktor CIO sslConnector(keyStore, trustManager)
  ├─ network/FileClient*.kt   — SSLSocketFactory wired with KeyManager + TrustManager
  └─ security/TlsIdentity.jvm.kt
       — builds X.509 via java.security + sun.security.x509 / BouncyCastle-lite
         (or hand-rolled DER — only one cert shape needs to be produced).

appleMain
  ├─ network/FileServer.apple.kt — REWRITE: SecureTransport-backed server
  │     ├─ Accept loop on POSIX socket (already POC-validated style)
  │     ├─ Per-connection: SSLNewContext(true) + SSLSetCertificate(identity)
  │     │   + kSSLSessionOptionBreakOnClientAuth + SSLHandshake loop
  │     ├─ After handshake pause: SSLCopyPeerTrust → leaf cert →
  │     │   SecCertificateCopyKey → SecKeyCopyExternalRepresentation →
  │     │   wrap raw P-256 point in SPKI DER → match TrustedDeviceStore.
  │     ├─ Continue handshake; then SSLRead loop → minimal HTTP/1.1 parser
  │     │   (see below) → file write via existing POSIX helpers.
  │     └─ On any verification failure: SSLClose, log, drop connection.
  ├─ network/FileClient.apple.kt — symmetric: SSLContext client side
  │     with kSSLSessionOptionBreakOnServerAuth, same SPKI-match logic.
  └─ security/TlsIdentity.apple.kt
       — builds self-signed cert from the SecKeyRef of #116:
         hand-roll DER for TBSCertificate (constant template + variable
         pubkey + variable signature), sign with SecKeyCreateSignature,
         import as SecIdentityRef via SecPKCS12Import on an in-memory P12,
         or — if SecIdentityCreate is available on the target — directly.
```

### Wire protocol

Unchanged from today. The Apple server **still speaks HTTP/1.1** (currently `GET /health`, `POST /upload?name=`, `POST /pair`). Losing Ktor on Apple means losing the HTTP parser — recovering it requires a small hand-rolled parser in `appleMain`. Tether's HTTP surface is bounded (under five routes, no chunked transfer encoding, no header folding), so the parser is ~150 lines of K/N. This is the trade-off [adr-apple-fileserver-engine.md](adr-apple-fileserver-engine.md) explicitly noted as the fallback when Network.framework didn't fit; with this ADR, that fallback becomes reality.

### Key lifecycle

1. **First launch.** `DeviceKeyPair` generates / loads the keypair (#116 behaviour).
2. **TlsIdentity build.** On first call, `TlsIdentity` produces a self-signed X.509 cert valid 100 years from the keypair (cert validity is meaningless under pinning; expiry would only force regeneration without security benefit). Cached.
3. **Pairing.** [#10](https://github.com/khmelevartem/tether/issues/10) handshake exchanges `DeviceKeyPair.publicKey` between peers. After confirmation, both sides store the peer's SPKI in `TrustedDeviceStore`.
4. **Connection.** Both server and client sides drive the same flow: present own cert, break on peer-auth, extract peer SPKI, match against `TrustedDeviceStore.lookup(peerId)`. On match — continue. On mismatch — close.
5. **Forget.** Removing a peer from `TrustedDeviceStore` makes all future handshakes to that peer fail closed. There is no fallback path; this is the product invariant.

### TLS parameters

- **Cipher suites:** restrict to `TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256` and `TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384`. Both JVM and SecureTransport support them; both have hardware AES on every target hardware Tether ships to.
- **Protocol version:** TLS 1.2 minimum, TLS 1.3 preferred. SecureTransport TLS 1.3 support is mature on iOS 12+ / macOS 10.15+.
- **No SNI required.** Pinning is on SPKI, not hostname. Hostname verification is disabled on every client.
- **No client cert chain.** A single leaf, self-signed. No intermediate CAs, no root.

### Testing strategy

- Unit on JVM: `TlsIdentity.jvm`, custom `X509TrustManager`, round-trip via `SSLSocketPair` localhost.
- Unit on Apple: `TlsIdentity.apple` builds a parsable cert (verify by feeding through JVM `KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(publicKey.encoded))`; this is already the contract pattern from #116).
- Integration on `iosSimulatorArm64`: server in K/N test, client on JVM — single TLS round-trip with 1 MB payload, plus tampered-pin negative case.
- Smoke: extend `.claude/skills/smoke-test/SKILL.md` Desktop↔Desktop block with `openssl s_client -connect ... -showcerts -no-CAfile` and assert the server cert chain length is 1 and matches the device pin.

## Considered and rejected

- **B (plain HTTP).** Rejected on threat-model grounds. Priority 2 ("passive eavesdropper on open Wi-Fi") becomes unmitigated. Tether cannot honestly claim "safe on open Wi-Fi" while shipping this — and that claim is load-bearing for the privacy positioning in [vision.md](../../product/vision.md) principle 4. Implementation simplicity is real but does not pay for the integrity loss.
- **C (Noise / libsodium application-level encryption).** Rejected on maintainability grounds. No mature KMP-wide Noise binding exists; libsodium across `iosArm64` requires cinterop work comparable to the SecureTransport approach below, plus permanent maintenance of streaming AEAD framing, replay protection, and rekey logic — areas where bugs are silently bad (the user thinks they're protected). TLS gives us battle-tested code paths in JVM / SecureTransport; the cost is bounded and one-time per platform.
- **"B in MVP, A later" two-step.** Explicitly rejected. Two wire formats serially is more expensive than one correctly. The marketing claim "safe on open Wi-Fi" cannot truthfully ship with B in production even briefly.
- **Apple-side via Ktor CIO TLS.** Not viable. [KTOR-7262](https://youtrack.jetbrains.com/issue/KTOR-7262) — TLS sessions are not supported on Kotlin/Native; the code throws immediately. Open since 2020.
- **Apple-side via Network.framework (`nw_listener_t` with `nw_tls_create_options`).** Not viable from Kotlin/Native — verified empirically in the POC. `nw_protocol_options_t` is a Swift value type; K/N's ObjC bridge crashes on the boundary. Would require a Swift / ObjC bridge file to be linked into the K/N binary purely to talk to Network.framework — a much larger lift than SecureTransport for no immediate gain.

## Costs accepted

1. **Asymmetric server implementation.** JVM/Android keep Ktor CIO; Apple uses SecureTransport. Two server codepaths to maintain, two test surfaces, parallel feature work. Mitigation: the wire protocol is shared, and the HTTP surface on Apple is tiny (under five routes); the divergence is bounded.
2. **Hand-rolled HTTP/1.1 parser on Apple.** Required because Ktor no longer parses HTTP on this side. Mitigation: bounded scope (no chunked TE, no folding, no pipelining), one-time write, covered by integration tests.
3. **SecureTransport deprecation.** Apple marked SecureTransport deprecated with iOS 13 / macOS 10.15. No removal date — the API still ships in every current release, and the migration target (Network.framework) is what we already ruled out as unusable from K/N. Mitigation: tracked under "Revisit if" below; the day a removal date appears, we re-evaluate.
4. **Self-signed cert builder on Apple Native.** Hand-rolled DER for TBSCertificate is unusual and warrants careful review. Mitigation: only one cert shape (self-signed P-256, no extensions beyond the bare minimum), validated cross-platform by `KeyFactory` on JVM.

## Revisit if

- **[KTOR-7262](https://youtrack.jetbrains.com/issue/KTOR-7262) closes** — Ktor ships TLS for Kotlin/Native. Migrate Apple `FileServer.actual` back to Ktor CIO with `sslConnector`, drop the hand-rolled HTTP parser and the SecureTransport bridge. This is the cleanest exit path.
- **Apple announces a SecureTransport removal date.** Re-evaluate Network.framework via a Swift/ObjC bridge, or — if KTOR-7262 closed by then — straight to Ktor.
- **Measured throughput regression >15%** on 1 GB on home Wi-Fi after the implementation issue lands. Profile and tune (chunk size, cipher suite preference) before changing the choice itself.
- **A real KMP Noise / libsodium binding emerges** with cross-target maturity. Re-evaluate Option C as a uniform path. This is the lowest-priority trigger — the bar is "as mature as `kotlinx-io`, not a community port".

## References

- [#123](https://github.com/khmelevartem/tether/issues/123) — DOCS issue that closes the open question.
- [docs/product/security.md](../../product/security.md) — product-side statement of the decision.
- [docs/product/vision.md](../../product/vision.md) — principle 4 ("Local-first, no cloud"), the load-bearing privacy positioning.
- [adr-apple-fileserver-engine.md](adr-apple-fileserver-engine.md) — prior choice of Ktor CIO Native for the Apple FileServer; this ADR updates its "revisit if TLS lands" trigger.
- [#10](https://github.com/khmelevartem/tether/issues/10), [#116](https://github.com/khmelevartem/tether/issues/116), [#119](https://github.com/khmelevartem/tether/issues/119) — adjacent issues whose contracts this decision pins down.
- [KTOR-7262](https://youtrack.jetbrains.com/issue/KTOR-7262), [KTOR-7475](https://youtrack.jetbrains.com/issue/KTOR-7475), [KTOR-5912](https://youtrack.jetbrains.com/issue/KTOR-5912), [KTOR-2749](https://youtrack.jetbrains.com/issue/KTOR-2749) — upstream Ktor tickets establishing that TLS on Kotlin/Native is unavailable in 3.1.3.
- [Apple — Secure Transport reference](https://developer.apple.com/documentation/security/secure_transport) (deprecated but still shipped).
