# Threat model

STRIDE-by-component threat model for Tether's P2P file transfer within a single local network. This is the engineering-layer analysis behind the product-level trust framing in [`security.md`](README.md); that doc summarises the trust model and pairing flow for a broad audience, this one holds the per-component attack surface and the conditions each mitigation depends on.

A living doc states what should be true of any correct implementation. Where the code has not caught up, the gap is an implementation issue, not a contradiction.

Method: STRIDE per component. Scope: the HTTP server on each device, the mDNS announce, and pairing through SAS comparison.

## Assets and trust boundaries

### What is protected

- **File contents.** The original, uncompressed — full personal data (photos, video, documents).
- **File integrity** on receipt — bytes are not substituted in transit.
- **Pairing identity** of paired devices and the associated secret.
- **Device availability** — incoming traffic must not hang the app or fill the disk.
- **Metadata** — device names, the fact and direction of a transfer.

### Trust boundaries

- **The local network is untrusted.** This is the base assumption — not "home means safe". A hotspot, a café, a weak WPA2 network: the attacker is already inside the segment.
- **The mDNS plane is open**, unauthenticated by design.
- **A paired device is trusted** only after successful pairing and only within its rights.
- **Everything before pairing is untrusted input.**

### Entry points

Components that listen on the network and accept external input:

1. The mDNS listener.
2. The pairing HTTP endpoint — open before trust is established.
3. The file-transfer HTTP endpoints — behind pairing.
4. The device-selection UI — indirectly, by rendering untrusted strings.

## SAS pairing model

Trust is established by **SAS comparison** (Short Authentication String), not by entry of a secret. The two devices exchange their **static long-term identity public keys** under commit-before-reveal; each independently derives a short human-readable value from **both full identity public keys plus a per-handshake nonce**, and displays it. The user compares the values on the two screens and confirms they match. The peer's identity key is committed to the trust store only after **bilateral confirmation** on both sides.

The SAS authenticates the static identity keypair — the per-install root of trust ([`device-identity.md`](../engineering/device-identity.md)) that the TLS channel pins to. Pairing derives no session key and provides no forward secrecy of its own: it carries only public keys. Per-session confidentiality, forward secrecy, and proof of private-key possession on every later connection come from the **pinned TLS channel** ([#140](https://github.com/khmelevartem/tether/issues/140)), not from a pairing-time key exchange. The per-handshake nonce keeps the SAS unpredictable on each attempt even though the identity keys are static and reused. Why static identity over an app-level ephemeral key exchange — see [adr-sas-pairing-protocol.md](../engineering/adr/adr-sas-pairing-protocol.md).

Security consequences:

- There is no entered secret, so there is no "PIN brute-force" class, so **PAKE is not required**.
- A distinct risk class appears: an **active MITM forcing a SAS collision**.
- SAS defends against exactly one thing — substitution in the key exchange — and only when the conditions below hold.

### SAS correctness conditions

The SAS scheme is sound only when all five hold. Each is load-bearing; removing any one opens the corresponding attack.

1. **Both keys are covered.** The SAS is derived from the whole agreed material — both public keys in full. Otherwise a MITM fits its own pair to a matching SAS by varying the uncovered part.
2. **Length is 5–6 decimal digits (~17–20 bits).** SAS defends probabilistically: the chance a MITM hits a collision is about one in the number of possible values. A 4-digit code (one in 10,000) is insufficient.
3. **Commit-before-reveal.** A side first sends a hash commitment of its key and reveals the key only after receiving the other side's commitment. Without this, an active MITM sees the victim's key before fixing its own and fits a collision. This is the critical step — without it the scheme is broken at any SAS length.
4. **Session is bound to the SAS.** After confirmation, traffic runs under the key from this exchange; every subsequent request is authenticated by it.
5. **Defence against blind confirmation.** If the user habitually taps "yes", the protection is nil. Mitigations: prominent display, no single-button confirmation without looking, choosing the correct value among several on one device.

## STRIDE by component

STRIDE letters and what each violates: see [legend](#stride-legend).

### Discovery (mDNS)

| Threat | Category | Scenario | Mitigation |
|--------|----------|----------|------------|
| Device spoofing | S | Announce under an existing device's display name; the victim picks the wrong device | Identity is confirmed only by pairing, never by name; new devices are marked unverified in the UI |
| TXT disclosure | I | Version, OS, exact name in the announce → reconnaissance | Minimal fields, no version, neutral default names |
| List flooding | D | Dozens of fake devices fill the UI | Cap on displayed entries, dedup, rate-limit on announce processing |

### Pairing (central component)

| Threat | Category | Scenario | Mitigation |
|--------|----------|----------|------------|
| SAS collision fitting | S / T | An active MITM fits its own key to a matching SAS | Commit-before-reveal plus SAS length of 5–6 decimal digits (~17–20 bits) |
| MITM in key exchange | S / I / T | Wedging between the devices, a separate exchange with each | Human SAS comparison catches the mismatch; the commitment makes fitting impossible |
| Blind confirmation | S | The user taps "yes" without looking | Prominent display, choice among options, no one-tap confirmation |
| TOFU hijack | S | The attacker spoofs first and entrenches as trusted | SAS verification is mandatory on first contact; visible fingerprint |
| Handshake replay | T | Replaying intercepted packets | Nonce / challenge, binding to the session |
| Theft of the pairing secret from disk | I | Reading the stored key | Platform Keystore / Keychain, not a plaintext file |

### Transport

| Threat | Category | Scenario | Mitigation |
|--------|----------|----------|------------|
| Plaintext sniffing | I | `http://` on a shared network → someone else's photos in cleartext | TLS on the LAN, self-signed plus pinning to the pairing identity |
| MITM on transport | S / T | ARP spoofing plus in-stream substitution | TLS plus pinning makes an intercepted stream useless |
| On-the-fly file substitution | T | Altering bytes during transfer | TLS plus a file-hash check on receipt |

### HTTP server (the most underestimated surface)

The listening server runs on the device of a non-technical user. Any bug here is a vulnerability on someone else's phone.

| Threat | Category | Scenario | Mitigation |
|--------|----------|----------|------------|
| Unauthorised request | E | Anyone on the network hits the transfer endpoints | Proof of pairing on **every** request, not only at the handshake |
| Path traversal (receive) | T / E | A filename of `../../` writes outside the target folder | Ignore the path from the request, generate an own name, write only into the sandbox folder |
| Path traversal (serve) | I | A request reads an arbitrary path off the device | Serve only from an explicit staging list, never straight from the filesystem |
| DoS by disk | D | An endless / huge upload fills storage | Size limit, free-space check, quotas |
| DoS by connections | D | Flooding connections hangs the foreground service | Concurrency limit, timeouts, fast rejection of unpaired peers |
| Parse exploit | T / D | Malformed multipart / JSON / headers crash the process or worse | Endpoint fuzzing, strict validation, defensive decoding |
| Openness before pairing | E | The pairing endpoint is reachable by everyone by definition | Minimal logic before trust, hard limits, nothing extra exposed |

The receive-path traversal mitigation and the streaming-not-buffering invariant are owned by [`file-transfer-wire.md`](../engineering/file-transfer-wire.md); this row states the threat, that doc states the boundary.

### Device-selection UI

| Threat | Category | Scenario | Mitigation |
|--------|----------|----------|------------|
| Spoofing via name | S | Two identically-named devices in the list; the victim taps the wrong one | Show "paired before" status, visually distinguish new devices |
| Injection via device name | T | A name with control characters / markup breaks rendering | Sanitise and escape displayed strings |

### Pairing-secret storage

| Threat | Category | Scenario | Mitigation |
|--------|----------|----------|------------|
| Theft of the pairing secret from disk | I | Reading the stored key off disk and using it elsewhere | OS-bound secure storage — the OS encrypts the secret with a key bound to the user / device; the app never holds the master secret itself and never encrypts with a static key baked into the binary |

The secret this protects is the root of trust owned by [`device-identity.md`](../engineering/device-identity.md); the per-platform secure-storage mechanisms live there.

Accepted risk: malware running as the same user on the same machine can ask the OS to decrypt the secret — to it any software store is transparent. Only hardware-backed storage (TPM / Secure Enclave) closes this fully, which is out of MVP. This is a limit of the model, not a missed hole. See [accepted risks](#accepted-risks).

### Network layer (outside the app, inside the model)

| Threat | Category | Scenario | Mitigation |
|--------|----------|----------|------------|
| ARP spoofing → MITM | S / I / T | An attacker inside a weak network intercepts | Closed by TLS plus pinning — the network is never trusted |
| Rogue AP / hotspot takeover | S / I | Substituting the access point | Same: security does not depend on the network's honesty |

## Priorities

By probability × impact:

1. **SAS with commit-before-reveal plus length of 5–6 decimal digits (~17–20 bits).** Closes MITM in pairing. If only one thing is done, this is it.
2. **TLS plus pinning on the LAN.** Without it, "original bytes" means someone else's data on the air.
3. **Authorisation on every request.** Do not trust "already paired at the start of the session".
4. **Server protection: path traversal plus limits.** The server runs on someone else's device.
5. **Defence against blind SAS confirmation.** A cheap measure; without it the human nullifies priority 1.

## Accepted risks

Consciously out of MVP:

- Defence against an insider — an already-paired malicious device.
- Malware running as the same user on the same machine — it bypasses any software secret store (DPAPI / Credential Manager / Keystore). Only TPM / Secure Enclave closes this fully, which is out of MVP. This is a limit of the model, not a missed hole.
- Advanced side-channel attacks (timing).
- Attacks on the device's OS itself.
- Intel-Mac scenarios (the platform is not supported).

## Verification

The attack tree for the SAS-pairing node and the pentest-on-paper test-case suite — analysis documented, no live exploits run — live in [`sas-pairing-pentest.md`](sas-pairing-pentest.md). That suite states the expected behaviour each mitigation here must exhibit against a build.

## STRIDE legend

| Letter | Threat | Violates |
|--------|--------|----------|
| S | Spoofing — identity substitution | Authentication |
| T | Tampering — data substitution | Integrity |
| R | Repudiation — denial of an action | Non-repudiation |
| I | Information disclosure — leak | Confidentiality |
| D | Denial of service | Availability |
| E | Elevation of privilege | Authorisation |

Repudiation is not given its own rows: for disposable P2P transfer without accounts, non-repudiation is not a product goal.

## What this doc does *not* commit to

- Exact SAS length within the "5–6 decimal digits (~17–20 bits)" band — the implementation issue picks the digit count.
- The on-wire framing of the commit, reveal, nonce, and confirmation messages — the byte-level protocol detail lives with the pairing implementation; this doc states that commit-before-reveal over the static identity keys with a per-handshake nonce is required, not its framing. The key the SAS authenticates and the absence of a pairing-time session-key exchange are decided — see [adr-sas-pairing-protocol.md](../engineering/adr/adr-sas-pairing-protocol.md).
- Concrete rate-limit, connection-cap, and size-limit constants — implementation choices that live in code.
- The application-tag and attribute recipe for each platform secret store — owned by [`device-identity.md`](../engineering/device-identity.md).
