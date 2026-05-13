# poc-tls-spike

**Hypothesis:** TLS between JVM (Ktor/SSLContext) and Kotlin/Native macOS (SecureTransport) works with self-signed EC P-256 certificates and SPKI key pinning in both directions.

**Verdict: CONFIRMED** — all four scenarios below pass.

---

## What was tested

| Scenario | Server | Client | Expected exit | Result |
|----------|--------|--------|---------------|--------|
| S1 | JVM (SSLContext, port 8443) | macOS K/N (SecureTransport) | 0 (success) | PASS |
| S2 | macOS K/N (SecureTransport, port 8444) | JVM (SSLContext) | 0 (success) | PASS |
| S4 | macOS K/N server | JVM client with corrupted pin | 1 (pin mismatch) | PASS |
| S5 | JVM server | macOS K/N client with corrupted pin | 1 (pin mismatch) | PASS |

Each run transfers a 100 MB payload and verifies SHA-256 integrity end-to-end.

---

## Key findings

**Network.framework is incompatible with Kotlin/Native.**
`nw_protocol_options_t` is a Swift value type at runtime (`_TtGC7Network15ProtocolOptionsVS_11TLSProtocol_`). K/N's ObjC bridge expects NSObject for this type. Any function returning `nw_protocol_options_t` (including `nw_tls_create_options()`) crashes the process at the bridge. This is a fundamental K/N 2.x limitation — Network.framework TLS cannot be used from K/N.

**SecureTransport (deprecated macOS 10.15) works cleanly from K/N.**
Pure C API, no Swift value types, full access via `platform.Security.*`. Used `SSLNewContext(Boolean)` instead of `SSLCreateContext` because the `SSLProtocolSide`/`SSLConnectionType` enum constants are not exported by K/N 2.3.20.

**SPKI pinning is viable on both sides.**
- JVM: custom `X509TrustManager.checkServerTrusted` → `cert.publicKey.encoded` → compare DER bytes.
- macOS K/N: `kSSLSessionOptionBreakOnServerAuth` pauses handshake after server cert → `SSLCopyPeerTrust` → `SecCertificateCopyKey` → `SecKeyCopyExternalRepresentation` → manually wrap raw EC point in SPKI DER → compare.

**SecureTransport read/write must be chunked.**
`SSLRead`/`SSLWrite` fail with `paramErr (-50)` for calls larger than ~64 KB. Chunk to 65536 bytes per call.

**Kotlin infix operator precedence trap.**
```kotlin
// WRONG — left-associative, evaluates as (((a ushr 8) or b) shl 8):
(v and 0xFF00) ushr 8 or (v and 0xFF) shl 8
// CORRECT:
((v and 0xFF00) ushr 8) or ((v and 0xFF) shl 8)
```

**JVM ServerSocket on macOS defaults to IPv6 with IPV6_V6ONLY=1.**
macOS K/N POSIX sockets are IPv4. Launch JVM server with `-Djava.net.preferIPv4Stack=true`.

**SecPKCS12Import items array must not be released before retaining the identity.**
`CFDictionaryGetValue` returns a non-retained reference. `CFRetain(identity)` before `CFRelease(items)`.

---

## Certs and keys

Both certs are self-signed EC P-256, generated once and committed. Passphrase for all P12 files: `poc`.

| File | Use |
|------|-----|
| `keys/jvm.p12` | JVM server keystore (also in `src/jvmMain/resources/jvm.p12`) |
| `keys/apple.p12` | macOS K/N server identity (also in `src/jvmMain/resources/apple.p12` for JVM client pin check) |
| `keys/jvm-spki.der` | DER SPKI of jvm.crt — hardcoded in macOS client for pinning |
| `keys/apple-spki.der` | DER SPKI of apple.crt — hardcoded in JVM client for pinning |

---

## Running

Build:

```bash
./gradlew :poc-tls-spike:fatJar
./gradlew :poc-tls-spike:linkDebugExecutableMacosArm64
```

S1 — JVM server + macOS client:

```bash
java -Djava.net.preferIPv4Stack=true -jar poc-tls-spike/build/libs/poc-fat.jar server &
poc-tls-spike/build/bin/macosArm64/debugExecutable/macos-poc.kexe client 127.0.0.1 8443
# expect exit 0
```

S2 — macOS server + JVM client:

```bash
poc-tls-spike/build/bin/macosArm64/debugExecutable/macos-poc.kexe server &
java -Djava.net.preferIPv4Stack=true -jar poc-tls-spike/build/libs/poc-fat.jar client 127.0.0.1 8444
# expect exit 0
```

Pin-failure scenarios (S4, S5): corrupt the first byte of the expected SPKI constant in the respective source file, rebuild, and re-run. Client must exit 1.

---

## Not in scope

- iOS / iOS simulator validation — the POC binary is built for `macosArm64` only. The `platform.Security.*` cinterop klib in Kotlin/Native 2.3.20 exports the same `SSLContext` / `SSLNewContext` / `SSLRead` / `SSLWrite` symbol set for `iosArm64` and `iosSimulatorArm64`, so the code shape transplants; iOS validation is part of the implementation issue, not the spike.
- Certificate rotation / expiry.
- Production key management (POC reads PKCS12 from CWD; production uses Keychain via #116).
- Asymmetric authentication (POC pins one direction at a time; production uses mutual TLS — both peers verify each other against `TrustedDeviceStore`).
