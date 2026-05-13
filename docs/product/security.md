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

## Channel Encryption — Open Question

After pairing, file transfers happen over HTTP between the two devices. Whether and how to encrypt that channel is **not decided**. Three options on the table:

### Option A — TLS with pinned, paired keys

HTTPS, self-signed certificates whose public keys are exactly the keys exchanged during pairing.

- **Pros:** Standard, well-understood. Ktor supports it on both client and server. Defeats passive eavesdropping and active MITM after pairing.
- **Cons:** Cert handling on each platform adds complexity. Needs careful implementation to actually pin (not fall back to system trust store).

### Option B — Plain HTTP, trust the LAN

No transport encryption. Pairing only authenticates the peer; transfers ride raw HTTP.

- **Pros:** Simplest. Fastest to ship. Matches the current code state.
- **Cons:** Passive eavesdropper on an open Wi-Fi sees file contents. Acceptable on home networks; not acceptable on public ones.

### Option C — Application-level encryption (e.g. libsodium)

Encrypt payload above HTTP, using session keys derived from paired identities.

- **Pros:** Independent of TLS infrastructure. Easier to reason about correctness.
- **Cons:** Reinventing what TLS does. Performance overhead on streaming. Library availability across all four targets needs verification.

**Decision pending.** Likely path: ship Option B in MVP, upgrade to Option A before any public release. To be revisited when the pairing handshake is implemented.

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

- Channel encryption choice (above).
- Auto-rotation of paired keys after N transfers / N days?
- Visible "this device sent / received X files from Y" log — useful for trust, or privacy-leaky?
