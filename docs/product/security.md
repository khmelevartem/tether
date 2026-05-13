# Security & Privacy

Tether moves files between devices on the same local network with no cloud and no accounts. This doc captures the trust model, the pairing flow, and the unresolved choice of channel encryption.

## Threat Model

Who we protect against, in order of priority:

1. **Untrusted peers on the same Wi-Fi.** Coffee shops, coworking spaces, home networks with guests. Other devices on the LAN can see mDNS announces and reach the file server port. They must not be able to send or receive files without explicit user consent (pairing).
2. **Passive eavesdropper on an open Wi-Fi.** Someone sniffing unencrypted traffic on the same network. Mitigation depends on the channel-encryption choice (see open question).
3. **Active MITM on an open Wi-Fi.** Someone able to intercept and modify traffic between two paired devices. Mitigation requires authenticated channel encryption.
4. **Lost / stolen device.** Trusted-peer keys stored locally are exposed to whoever has the device. We rely entirely on OS-level device security (lock screen, disk encryption, secure storage). Tether does **not** add an app-level passcode or biometric lock — that's the OS's job, and reproducing it inside the app is duplicate work that users would expect to keep working when their phone is unlocked anyway.

Out of scope:
- Nation-state adversaries.
- Malicious code on the user's own device.
- Attacks on the underlying OS / Wi-Fi router.

## Pairing Flow

First-time connection between two devices:

1. Device A initiates a connection to Device B (selected from the discovered list).
2. Both devices display the **same 4-digit numeric code**, derived from the handshake (see issue [#10](../../README.md)).
3. The user confirms the code matches on both screens.
4. Public keys are exchanged and stored locally on both devices.
5. Subsequent connections between the two devices recognize each other automatically — no re-pairing.

The 4-digit code defends against active MITM during pairing: an attacker who intercepts and substitutes their own key produces a different code on each side, and the user catches the mismatch.

Local key storage: per-platform secure storage (Keystore on Android, Keychain on Apple, OS keyring on Desktop). Specifics in implementation issues.

## Channel Encryption

After pairing, file transfers between two paired devices run over **HTTPS with self-signed certificates pinned to the public keys exchanged during pairing**. From MVP onward, with no plain-HTTP intermediate stage.

**What the user gets.** A passive sniffer on open Wi-Fi sees no file bytes and no file names. An active attacker substituting their own certificate is rejected before the first byte of file data — no user prompt, no dialog. Tether can honestly claim "safe on open Wi-Fi" without qualification.

**What we accept in return.** Per-platform TLS work — building self-signed X.509 certificates from each device's EC P-256 keypair (reused from pairing), custom trust verification that pins on `SubjectPublicKeyInfo` without consulting the system trust store, and an asymmetric server implementation: Ktor CIO on JVM/Android, direct SecureTransport on Apple Native. The asymmetry is implementation detail — the wire protocol and observable behaviour are identical across all four targets.

The engineering rationale (rejection of plain HTTP and application-level encryption alternatives, the verified Kotlin/Native TLS spike, the SecureTransport choice, and the implementation scheme) lives in [adr-channel-encryption.md](../engineering/adr/adr-channel-encryption.md).

### What this requires from pairing and Apple keys

- **Pairing ([#10](https://github.com/khmelevartem/tether/issues/10))** produces the EC P-256 public keys that become the TLS pinset. After pairing, each device stores its peer's `SubjectPublicKeyInfo` in `TrustedDeviceStore`. Every subsequent TLS handshake to that peer verifies the presented cert's public key matches the stored pin — system trust store is never consulted.
- **Apple EC P-256 keys ([#116](https://github.com/khmelevartem/tether/issues/116))** provide the raw keypair material. The implementation issue wraps that material into a self-signed X.509 certificate at startup (cached for the life of the keypair).

### Acceptance criteria for the implementation issue

In product terms — what must be true for the user / the system after the implementation lands:

1. After two devices have paired, every subsequent file transfer between them is end-to-end encrypted. A packet capture on the same Wi-Fi shows no file bytes and no file names in cleartext.
2. An attacker substituting their own self-signed certificate mid-transfer is rejected by both sides before any file byte is transmitted — no user dialog, no override.
3. Transport works on all four targets (Android, iOS, macOS, Desktop JVM) for files of arbitrary size, with throughput within ~15% of plain HTTP on the same hardware.
4. The system trust store is never consulted. Removing a peer from `TrustedDeviceStore` causes the next connection to that peer to fail closed.
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
