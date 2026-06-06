# Device identity

Per-install asymmetric keypair, the root of trust pinned during pairing and wrapped into a self-signed certificate for TLS by the [channel-encryption ADR](adr/adr-channel-encryption.md). One common-layer declaration with one actual per OS family.

## Goal

Each install holds **one stable keypair** that survives process restarts and OS updates. The public half is serialised in a **wire format both platforms parse identically** — so a peer on one platform pins a key emitted by a peer on another without per-platform parsing branches.

## Shape

Three responsibilities, separated by layer:

- **Common.** Pure byte arithmetic: prepend the fixed DER prefix that turns a raw uncompressed elliptic-curve point into the standard X.509 public-key envelope. No platform calls.
- **JVM.** Generate the EC keypair through the platform key-pair generator — which already emits the wire format directly — and persist the private half as an owner-only file in the config directory.
- **Apple.** Generate the EC keypair in the Keychain, scoped by an application-defined tag so multiple keys can co-exist without collision. Export the public half in the platform-native point format and wrap it into the shared wire format.

Both platforms produce the same bytes for a given key.

## Contract

The exposed public key is **91 bytes of X.509 SubjectPublicKeyInfo for EC P-256**. The interoperability claim: the JVM EC public-key import API accepts the bytes and round-trips them to an equal public-key instance. Verified by a cross-format unit test on the JVM side and by simulator-bundle smoke that POSTs `/pair` against the running iOS app — the only environment where the Apple Keychain path actually runs, see [docs/knowledge/apple-platform.md](../knowledge/apple-platform.md).

## Cross-cutting

- **Lifecycle.** Generated lazily on first construction, persisted; subsequent constructions return the same bytes. Identity rotation is via uninstall (clearing the Keychain / the config directory).
- **Authority.** The keypair is the root of trust for the install — pinned during pairing and the secret behind the trust store. The threats against this key, and the principle that secures it (OS-bound encryption, never a static binary key, the malware-as-same-user accepted risk, hardware-backed storage out of MVP), are analysed in [`threat-model.md` §Pairing-secret storage](threat-model.md#pairing-secret-storage); the per-platform mechanisms that realise that principle are below under [Secret storage](#secret-storage). Keychain access is wrapped behind a small seam so unit tests inject an in-memory fake — the test harness has no app identity, so the real Keychain returns errors uniformly. Production wiring lives in each platform's composition root.
- **Placement.** Apple code in the Apple source set, JVM code in the JVM source set, the wire-format wrapper in the common layer (pure bytes, no platform API).
- **Observability.** Corruption recovery (load returns a key but extract fails) logs and regenerates once. A non-success Keychain delete is logged because it precedes a likely duplicate-item error on the next generate.

## Secret storage

Each platform persists the private half through the OS-managed secure store, which encrypts it under a key bound to the user / device. This is the per-platform realisation of the principle in [`threat-model.md` §Pairing-secret storage](threat-model.md#pairing-secret-storage).

| Platform | Store |
|----------|-------|
| Windows | OS user-data protection (account-derived encryption), optionally behind the OS credential manager |
| macOS | Keychain |
| iOS | Keychain |
| Android | Keystore |

On Windows the protection derives its key from the account, so the encrypted blob decrypts only for the same user on the same machine — moving it to another machine yields nothing. The OS credential manager is the same protection with storage management built in. A hardware-backed key (bound to a security chip) raises the bar further — the key is not extractable even by same-user malware — but from the Desktop runtime it is notable effort and is absent on many machines, so it is **out of MVP**. A biometric prompt on every access is rejected: it breaks the two-tap transfer flow without closing a threat the account-bound encryption leaves open.

## What this doc does *not* commit to

- The application tag string identifying the Keychain item.
- The exact attribute set passed to the platform key APIs — that's tuned against the platform; the doc states the contract (key persists, public bytes are 91-byte SPKI), not the recipe.
- Curve choice beyond "asymmetric, EC, fixed 91-byte SPKI". Switching curves would touch the wrapper prefix, the channel-encryption ADR, and the trust-store byte layout; it is a deliberate decision, not a knob.
