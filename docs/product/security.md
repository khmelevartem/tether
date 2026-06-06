# Security & Privacy

Tether moves files between devices on the same local network with no cloud and no accounts. This doc captures the trust model, the pairing flow, and channel encryption at the product level. The engineering-layer STRIDE analysis — per-component attack surface and the conditions each mitigation depends on — lives in [`docs/engineering/threat-model.md`](../engineering/threat-model.md), with the SAS-pairing attack tree and pentest suite in [`docs/knowledge/sas-pairing-pentest.md`](../knowledge/sas-pairing-pentest.md).

> **Status.** This describes the **target** security model. The SAS pairing apparatus (commit-before-reveal, SAS derivation, trust stored only after mutual confirmation) and channel encryption are the model the implementation is built toward, not the current behaviour of every endpoint. Open gaps are tracked in [#10](https://github.com/khmelevartem/tether/issues/10) (SAS handshake), [#361](https://github.com/khmelevartem/tether/issues/361) (mutual confirmation before trust is stored), and [#140](https://github.com/khmelevartem/tether/issues/140) (pinned-TLS channel encryption).

## Threat Model

The local network is **untrusted** — not "home means safe". A hotspot, a café, a weak WPA2 network all put the attacker inside the segment. Who we protect against, in order of priority:

1. **Untrusted peers on the same Wi-Fi.** Coffee shops, coworking spaces, home networks with guests. Other devices on the LAN can see mDNS announces and reach the file server port. They must not be able to send or receive files without explicit user consent (pairing). Every transfer request proves pairing — being paired at the start of a session is not enough.
2. **Passive eavesdropper on an open Wi-Fi.** Someone sniffing traffic on the same network. Closed by TLS on the LAN (see [Channel Encryption](#channel-encryption)): a sniffer sees no file bytes and no file names.
3. **Active MITM on an open Wi-Fi.** Someone able to intercept and modify traffic — substituting keys during pairing, or wedging into the transfer stream. Closed during pairing by SAS comparison with commit-before-reveal (see [Pairing Flow](#pairing-flow)), and on transport by TLS pinned to the paired key.

The mitigations do not depend on the network being honest. ARP spoofing and rogue-AP takeover are both inside the model and both closed by the same pinned-TLS property — the network is never trusted.

Out of scope (accepted risks):
- Nation-state adversaries.
- An already-paired malicious device (insider).
- Malicious code on the user's own device. Malware running as the same user can ask the OS to decrypt a software-stored secret; only hardware-backed storage (TPM / Secure Enclave) closes this fully, which is out of MVP.
- Advanced timing side-channels.
- Attacks on the underlying OS / Wi-Fi router.

A **lost or stolen device** is handled by OS-level security (lock screen, disk encryption, secure storage), not by Tether. Tether does **not** add an app-level passcode or biometric lock — that's the OS's job, and reproducing it inside the app is duplicate work that users would expect to keep working when their phone is unlocked anyway.

## Discovery and Trust

Discovery is unauthenticated by design. Any device on a reachable subnet can announce itself — that is true today through mDNS and remains true through the additional discovery channels described in [tech-stack.md](tech-stack.md) and [`docs/engineering/discovery.md`](../engineering/discovery.md): the `/hello` rendezvous endpoint, HTTP-subnet-scan, and UDP-broadcast fallbacks. None of these widens the trust surface beyond what mDNS already exposes — they only diversify how a peer's existence reaches the device list. The list itself is not a trust claim.

The trust gate is **pairing**. No file moves between two devices until they have completed the first-encounter SAS comparison and exchanged keys. A device announcing under another's name authenticates nothing — new devices surface as unverified, and only the SAS comparison grants trust. Discovery's job is to make sure both devices see each other; pairing decides which of them they will accept files from.

Manual IP entry has the same trust properties: it adds a peer to the device list, not to the trusted-devices store. The user still goes through pairing the first time they exchange a file with a manually-entered peer.

## Pairing Flow

First-time connection between two devices uses **SAS comparison** — a Short Authentication String each device derives independently and shows the user to verify by eye.

1. Device A initiates a connection to Device B (selected from the discovered list).
2. The two devices exchange keys, each commits to its own key before the peer reveals theirs (**commit-before-reveal**), and each independently derives the **same SAS** from the full agreed material — both public keys.
3. Both devices display the SAS; the user confirms it matches on both screens.
4. Public keys are committed to the trust store on both sides only after that confirmation. A user who sees a mismatch and rejects leaves both trust stores unchanged.
5. Subsequent connections between the two devices recognise each other automatically — no re-pairing.

The SAS defends against active MITM during pairing: an attacker who intercepts and substitutes their own key produces a different SAS on each side, and the user catches the mismatch. This holds only when several correctness conditions all hold together — see [`threat-model.md` §SAS pairing model](../engineering/threat-model.md#sas-pairing-model).

The protection is only as strong as the user's attention: blind one-tap confirmation nullifies it, so the comparison is designed to require an active, deliberate match rather than a single button pressed without looking.

Local key storage: per-platform secure storage (Keystore on Android, Keychain on Apple, OS-level protection on Desktop — DPAPI / Credential Manager on Windows, Keychain on macOS). The app never holds a master secret itself or encrypts with a static key baked into the binary. Specifics in [`docs/engineering/device-identity.md`](../engineering/device-identity.md) and implementation issues.

## Channel Encryption

After pairing, file transfers between two paired devices run over **HTTPS with self-signed certificates pinned to the public keys exchanged during pairing**. From MVP onward, with no plain-HTTP intermediate stage.

**What the user gets.** A passive sniffer on open Wi-Fi sees no file bytes and no file names. An active attacker substituting their own certificate is rejected before the first byte of file data — no user prompt, no dialog. Tether can honestly claim "safe on open Wi-Fi" without qualification.

**What we accept in return.** Per-platform TLS work — building self-signed X.509 certificates from each device's EC P-256 keypair (reused from pairing), custom trust verification that pins on `SubjectPublicKeyInfo` and never consults the OS trust store (Android `AndroidCAStore`, JDK `cacerts`, Apple Keychain roots), and an asymmetric server implementation: Ktor CIO on JVM/Android, direct SecureTransport on Apple Native. The asymmetry is implementation detail — the wire protocol and observable behaviour are identical across all four targets.

The engineering rationale (rejection of plain HTTP and application-level encryption alternatives, the verified Kotlin/Native TLS spike, the SecureTransport choice, and the implementation scheme) lives in [adr-channel-encryption.md](../engineering/adr/adr-channel-encryption.md).

### What this requires from pairing and Apple keys

- **Pairing ([#10](https://github.com/khmelevartem/tether/issues/10))** produces the EC P-256 public keys that become the TLS pinset. After pairing, each device stores its peer's `SubjectPublicKeyInfo` in the trust store. Every subsequent TLS handshake to that peer verifies the presented cert's public key matches the stored pin — the OS trust store is never consulted.
- **Apple EC P-256 keys ([#116](https://github.com/khmelevartem/tether/issues/116))** provide the raw keypair material. The implementation issue wraps that material into a self-signed X.509 certificate at startup (cached for the life of the keypair).

### Acceptance criteria for the implementation issue

In product terms — what must be true for the user / the system after the implementation lands:

1. After two devices have paired, every subsequent file transfer between them is end-to-end encrypted. A packet capture on the same Wi-Fi shows no file bytes and no file names in cleartext.
2. An attacker substituting their own self-signed certificate mid-transfer is rejected by both sides before any file byte is transmitted — no user dialog, no override.
3. Transport works on all four targets (Android, iOS, macOS, Desktop JVM) for files of arbitrary size, with throughput within ~15% of plain HTTP on the same hardware.
4. The OS trust store is never consulted at any point in the verification path. Removing a peer from the trust store causes the next connection to that peer to fail closed.
5. `Expect: 100-continue` and the timeouts settled in [#119](https://github.com/khmelevartem/tether/issues/119) continue to behave as specified under TLS.

### When to revisit

This decision is final for MVP. It is revisited only if:

- **Throughput regression** measured at >15% on 1 GB between two devices on home Wi-Fi after the implementation lands. Mitigation: profile and tune (chunk size, cipher suite) before revisiting the choice itself.
- **SecureTransport gets a removal date** from Apple. The choice of SecureTransport on Apple Native is a known structural cost — tracked in the ADR, retargeting path documented there.
- **[KTOR-7262](https://youtrack.jetbrains.com/issue/KTOR-7262) closes** (Ktor ships TLS for Kotlin/Native). Reasonable migration path off the asymmetric server implementation.

## Privacy Invariants

These are non-negotiable:

- **No telemetry without explicit opt-in.** No analytics by default. If telemetry is added later, it ships off by default with a visible toggle.
- **No metadata leaves the device.** File names, sizes, peer identities — none of this is uploaded anywhere.
- **No cloud, no relay, no fallback.** If the LAN is unavailable, transfer fails honestly. We do not silently route through any third party.
- **Discovery announces only what's needed.** Device name (user-controlled) and port. No hardware ID, no email, no phone.

## Logging Policy

Tether does run local logs to help users (and us) debug network issues. Rules:

- **Local-only.** Logs stay on the device. They are never uploaded, even on crash.
- **Default level: warning + error.** Info/debug logs are off by default; user can flip a switch in settings to capture a verbose session when reporting a bug.
- **No file contents, no peer identities beyond the local pairing record.** Log lines record events ("transfer started", "peer disconnected", "discovery failed") and error reasons — never bytes, never file names by default.
- **User-accessible.** The user can view, export, and clear the log from within the app. "Export" produces a file the user can attach to a manual bug report — never an automated upload.

Crash reporting and remote performance metrics are explicitly **out of scope for MVP**. If they're added later, they follow the same rule: opt-in, off by default, visible to the user.

## Open Questions

- Auto-rotation of paired keys after N transfers / N days?
- Visible "this device sent / received X files from Y" log — useful for trust, or privacy-leaky?
