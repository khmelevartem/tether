# Security & Privacy

Tether moves files between devices on the same local network with no cloud and no accounts. This doc captures the trust model Tether intends to provide, the threats it defends against, and where today's behaviour falls short of that intent.

The reader to keep in mind is non-technical and assumes "my devices on my Wi-Fi" is a private space ([audience.md](audience.md)). The model is measured against that expectation, not against a security-aware operator's.

A note on tense throughout: Tether's intended posture and its current behaviour diverge in MVP. Where they differ, the intent is stated as intent and the present reality is stated plainly alongside it. The intended mechanisms — channel encryption, the PIN handshake, caller authentication on every post-pairing request — are specified and partly designed but not yet in the shipping transfer path.

## What the wire does today

Transfers travel in cleartext. Everything Tether puts on the LAN — announcements, public keys, filenames, file contents — is readable by anyone sharing the network, with no encryption between two devices.

A device on the network can act without proving who it is. It can push a file into another device's downloads directory unsolicited, and it can get its own key recorded as a trusted device, all without any out-of-band confirmation or proof that it holds the matching private key. The PIN-comparison handshake described under [Pairing Flow](#pairing-flow) is product intent and does not yet gate any of this.

These two facts — cleartext transport and the absence of any caller proof — are the root of most threats below, and the gap between Tether's intended posture and what it currently delivers.

## Assets at risk

- **Transferred file contents.** The payload the user moves between their own devices — the product's entire reason to exist ([vision.md](vision.md)).
- **File metadata.** The relative path and filename ride alongside the transfer; size and timing are observable from the byte stream. The structure of a folder send is reconstructable from the sequence of paths.
- **Device identity keys.** The per-install EC keypair is the root of trust. The private half is held by the platform (owner-only file on Desktop, Keychain on Apple, Keystore on Android). The public half is broadcast and pinned by peers — public by design; confidentiality at rest is intentionally not provided.
- **Pairing trust.** The set of device identities a device will silently accept. What an attacker wants is to get their own key into this set, or to impersonate a key already in it.
- **Presence and social graph.** That a person runs Tether, on what device type, under what display name, and which other devices they pair with. Announcements carry alias, device type, and fingerprint in cleartext.

## Threat Model

Who Tether protects against, in order of priority. "In scope" means Tether intends to defend it; the severity reflects impact on the user's files and trust, the likelihood reflects how little the attacker needs.

### T1 — Eavesdropping on file contents and metadata (in scope, critical)

A passive sniffer sharing the broadcast domain — open or shared-password Wi-Fi, a guest network, a coffee-shop AP — reads any frame on the segment. Against cleartext HTTP this is the strongest cheap adversary: file contents, filenames, sizes, aliases, public keys, and the full pairing exchange are all readable with no active step. This is the user's normal environment ([audience.md](audience.md)), so likelihood is high. **Intended defence:** channel encryption (see [Channel Encryption](#channel-encryption)). **Today:** undefended — the entire transfer is cleartext.

### T2 — Man-in-the-middle on pairing (in scope, critical)

An active on-path attacker — via a rogue AP the victim joined, ARP spoofing, or a controlled switch port — can read, drop, delay, and rewrite traffic. With no transport authentication and no out-of-band verification, this adversary substitutes their own key in each direction during pairing, ends up trusted by both victims, and is positioned to read and rewrite every later transfer. The user has no signal that it happened. Likelihood is medium: it needs an on-path position, more effort than passive sniffing but a standard LAN capability. **Intended defence:** the PIN-comparison handshake (see [Pairing Flow](#pairing-flow)), backed by authenticated channel encryption. **Today:** undefended — pairing exchanges keys with no verification of either end.

### T3 — Uninvited file push by an unpaired device (in scope, high)

A rogue sender skips pairing entirely and writes a file into the user's downloads directory directly. Likelihood is high — it needs only LAN reachability. The receiver's filesystem boundary stops path escape (see below) but not the unsolicited write itself: a stranger drops arbitrary content uninvited. **Intended defence:** the upload handler accepts only from trusted, paired peers. **Today:** the upload handler performs no caller check; any reachable device can write.

### T4 — Trust injection via open pairing (in scope, high)

A malicious peer becomes a permanently trusted device by sending one pairing request — there is no code to compare and no confirmation in the request path, and the entry survives restarts. Once the trust check is enforced on upload, this becomes the bypass around it. Likelihood is high: a single unauthenticated request. **Intended defence:** trust is granted only after the user confirms the matching PIN on both devices. **Today:** the pairing handler writes the caller's key into the trust store unconditionally.

### T5 — Discovery-list spoofing / lure (in scope, medium)

An attacker on the segment forges discovery records and rendezvous calls to plant an entry impersonating the user's own laptop — own address, own fingerprint, a spoofed alias — to lure a send to the attacker's endpoint. The source address is taken from the TCP connection and cannot be forged mid-exchange, but the displayed name and type can. Severity is medium: it does not directly read or write files, but it steers the user into T1/T2 against an attacker-controlled endpoint. Likelihood is high (one forged request), bounded because a same-named peer stays distinguishable in the list and a lure still depends on the user picking it. **Intended defence:** none at the discovery layer — discovery is unauthenticated by design ([discovery.md § Trust](../engineering/discovery.md#trust)); the lure is neutralised downstream by the PIN handshake catching the wrong endpoint. **Today:** the rendezvous endpoint accepts any alias, device type, and fingerprint, and the downstream handshake does not yet exist.

### T6 — Presence and social-graph disclosure (in scope, low)

A passive sniffer observes that a person runs Tether, on what device, under what name, and — via cleartext pairing exchanges — with which peers. Severity is low: this is metadata, not contents, and the product already treats "device is on the network" as inherently visible ([vision.md § Non-Goals](vision.md#non-goals)). Likelihood is high (passive). It is listed to bound the claim: presence is observable by design, so leaking it is not a separately defended asset; pairing *relationships* leaking via cleartext is a consequence of T1's transport gap and is resolved with it.

### T7 — Lost or stolen device (in scope, OS-delegated)

Trusted-peer keys and the private key are stored locally and exposed to whoever holds an unlocked device. Tether relies entirely on OS-level device security — lock screen, disk encryption, secure storage. Tether does **not** add an app-level passcode or biometric lock: that is the OS's job, and reproducing it inside the app is duplicate work users would expect to keep working when their phone is unlocked anyway.

## Out of scope

Excluded by conscious decision, not because the adversary is weak:

- **Nation-state adversaries.** Resourced traffic analysis, cryptographic attacks, supply-chain compromise. Defending these would distort every trade-off against the actual user.
- **Malicious code on the user's own device.** Code running with the user's privileges already owns the private key, the trust store, and the plaintext files before they touch the network; no network-layer control helps. Tampering with or forging trust entries at rest is an instance of this — trust entries are unencrypted by deliberate choice since they hold only public keys, and a corrupted entry degrades safely to "untrusted, re-pair".
- **Attacks on the underlying OS or Wi-Fi router.** A compromised platform or gateway sits below every guarantee Tether can make; the app trusts the OS for key storage and the network stack for delivery.
- **Denial of service on the LAN.** A same-LAN attacker can flood, deauth, or blackhole traffic regardless of Tether. The product commits to failing honestly when the LAN cannot carry a transfer rather than masking it ([vision.md § Principles](vision.md#principles)); availability under a hostile network is the network's property, not Tether's to guarantee.
- **Physical access to an unlocked device.** Equivalent to being the user; outside any software boundary.

## Trust boundaries

- **Device process ↔ local filesystem.** The receiver treats every sender as hostile for path safety: a paired peer is no more trusted with the filesystem than an unpaired one, and path traversal is rejected at the boundary (see [file-transfer-wire.md](../engineering/file-transfer-wire.md)). This boundary holds in the shipping code.
- **Device ↔ local network.** Crossed by every inbound request. This is where caller authentication is intended to live. Today it is declared but not enforced — the trust store exists but no handler reads it.
- **Trusted ↔ untrusted peer.** Intended to gate file acceptance. Today it gates nothing: trust is written on request and never read on transfer.

The first boundary holds. The second and third are where the model's weight sits, and where the protection decisions below act.

## Protection decisions

One deliberate decision per in-scope threat: the mechanism Tether adopts, or the risk it consciously accepts. The decisions lean on two load-bearing mechanisms that together cover most of the surface — the **mutually-authenticated pinned channel** ([adr-channel-encryption.md](../engineering/adr/adr-channel-encryption.md)) and the **PIN consent gate** ([Pairing Flow](#pairing-flow)) — so the per-threat entries reference them rather than restating them.

The pinned channel is the keystone for traffic between two already-paired devices. Both ends present a self-signed certificate carrying their paired public key and reject any peer whose key is not in the trust store, before the first byte of application data. This makes the channel do double duty: it is both the confidentiality mechanism (T1) and the caller-authentication mechanism for every post-pairing request such as a file transfer (T3) — a peer absent from the trust store cannot complete the handshake, so no separate application-level trust check on the transfer path is required once the channel lands.

The pairing exchange itself is the standing exception, because at that point neither side has the other's key to pin yet — the channel has nothing to authenticate against. Its authenticator is the PIN consent gate (T4): trust is written only after both users confirm the matching code. So the two mechanisms divide the work cleanly — the consent gate guards the one bootstrap exchange that establishes trust, and the pinned channel guards everything that flows once trust exists. The cost of leaning this heavily on the channel is that until it ships, post-pairing traffic has no authentication at all; that gap is the present reality stated throughout this doc.

- **T1 — Eavesdropping.** *Adopt:* channel encryption. The decision and its engineering rationale are settled in [adr-channel-encryption.md](../engineering/adr/adr-channel-encryption.md); the cipher and per-platform server choices belong to that ADR, not here. Implementation tracked in [#140](https://github.com/khmelevartem/tether/issues/140).
- **T2 — MITM on pairing.** *Adopt:* the PIN consent gate, backed by the authenticated channel. The 4-digit code derived from the exchanged keys differs on each side when an on-path attacker substitutes a key, and the user catches the mismatch; the pinned channel then carries that verified identity into every later transfer. Two mechanisms, two issues — the handshake and code derivation in [#10](https://github.com/khmelevartem/tether/issues/10), the channel in [#140](https://github.com/khmelevartem/tether/issues/140).
- **T3 — Uninvited push by an unpaired device.** *Adopt:* caller authentication on every post-pairing request, realised by the mutually-authenticated channel rather than an application-layer check. A peer absent from the trust store fails the handshake and never reaches the transfer path, so that path needs no separate trust gate. The channel's mutual-authentication leg is the load-bearing and least-proven part of the decision; the engineering detail behind that caveat lives in [adr-channel-encryption.md](../engineering/adr/adr-channel-encryption.md). Tracked in [#140](https://github.com/khmelevartem/tether/issues/140).
- **T4 — Trust injection via open pairing.** *Adopt:* the consent gate writes trust only after both users independently confirm the matching PIN. A pairing request alone grants nothing; trust is the product of mutual confirmation, not of reachability. This gate guards the one exchange that establishes trust, and so closes the bypass that would otherwise survive every T3 control. Tracked in [#361](https://github.com/khmelevartem/tether/issues/361).
- **T5 — Discovery-list spoofing / lure.** *Accept the risk at the discovery layer; neutralise it downstream.* Discovery stays unauthenticated by design ([discovery.md § Trust](../engineering/discovery.md#trust)) — authenticating the device list would cost the zero-configuration discovery that is the product's reason to exist, and buys little because the list is not a trust claim. A lure that steers a send to an attacker endpoint is caught at the moment that endpoint cannot produce the matching PIN or the pinned key. The decision is to invest the defence in pairing (T2/T4), not in discovery.
- **T6 — Presence and social-graph disclosure.** *Accept presence; resolve relationship-leak with T1.* That a device runs Tether, under what name and type, is treated as inherently visible on a LAN ([vision.md § Non-Goals](vision.md#non-goals)) — reducing it would require suppressing the announcements that make discovery work, a trade the product declines. The one part that is *not* inherent — which peers a device pairs with, currently readable from cleartext pairing exchanges — is a consequence of T1's transport gap and is closed when the channel encrypts pairing alongside transfer ([#140](https://github.com/khmelevartem/tether/issues/140)). Whether to offer an opt-in *quiet mode* that announces less for users who want lower discoverability is an open product question, not a security mechanism — it belongs to a future product spec, not to this model.

## Discovery and Trust

Discovery is unauthenticated by design. Any device on a reachable subnet can announce itself — true through mDNS and through the additional discovery channels described in [tech-stack.md](tech-stack.md) and [`docs/engineering/discovery.md`](../engineering/discovery.md): the rendezvous endpoint, HTTP-subnet-scan, and UDP-broadcast fallbacks. None of these widens the trust surface beyond what mDNS already exposes — they only diversify how a peer's existence reaches the device list. The list itself is not a trust claim.

The trust gate is **pairing**. Tether intends that no file moves between two devices until they have completed the first-encounter PIN comparison and exchanged keys; discovery's job is to make sure both devices see each other, and pairing decides which of them they will accept files from. In the shipping code this gate is not yet enforced (T3, T4).

Manual IP entry has the same trust properties: it adds a peer to the device list, not to the trusted-devices store. The user still goes through pairing the first time they exchange a file with a manually-entered peer.

## Pairing Flow

This is the intended first-time connection between two devices. It is product intent; the PIN comparison and the trust gate it controls are not yet in the shipping request path.

1. Device A initiates a connection to Device B (selected from the discovered list).
2. Both devices display the **same 4-digit numeric code**, derived from the handshake (see issue [#10](https://github.com/khmelevartem/tether/issues/10)).
3. The user confirms the code matches on both screens.
4. Public keys are exchanged and stored locally on both devices.
5. Subsequent connections between the two devices recognize each other automatically — no re-pairing.

The 4-digit code is the intended defence against active MITM during pairing (T2): an attacker who intercepts and substitutes their own key produces a different code on each side, and the user catches the mismatch. Until step 2 and step 3 ship, pairing grants trust on request with no comparison.

Local key storage: per-platform secure storage (Keystore on Android, Keychain on Apple, OS keyring on Desktop). Specifics in implementation issues.

## Channel Encryption

This is the intended transport posture, resolved as a decision but not yet shipped. After pairing, file transfers between two paired devices run over **HTTPS with self-signed certificates pinned to the public keys exchanged during pairing**, from MVP onward with no plain-HTTP intermediate stage. Today the transport is plain HTTP (T1, T6); this section describes the target, and the engineering rationale lives in [adr-channel-encryption.md](../engineering/adr/adr-channel-encryption.md).

**What the user is intended to get.** A passive sniffer on open Wi-Fi sees no file bytes and no file names. An active attacker substituting their own certificate is rejected before the first byte of file data — no user prompt, no dialog. Tether can then honestly claim "safe on open Wi-Fi" without qualification.

**What that costs.** Per-platform TLS work, the bulk of it specific to one or another target rather than shared. The wire protocol and observable behaviour stay identical across targets regardless. The engineering trade-offs that buy this — and what each platform pays — live in [adr-channel-encryption.md](../engineering/adr/adr-channel-encryption.md).

### What this requires from pairing and platform keys

- **Pairing ([#10](https://github.com/khmelevartem/tether/issues/10))** produces the EC public keys that become the TLS pinset. After pairing, each device stores its peer's public-key info in the trust store; every subsequent handshake to that peer verifies the presented certificate's public key against the stored pin, and the OS trust store is never consulted.
- **Apple EC keys ([#116](https://github.com/khmelevartem/tether/issues/116))** provide the raw keypair material the implementation wraps into a self-signed certificate at startup.

### Acceptance criteria for the implementation

What must be true once channel encryption lands:

1. After two devices have paired, every subsequent file transfer between them is end-to-end encrypted. A packet capture on the same Wi-Fi shows no file bytes and no file names in cleartext.
2. An attacker substituting their own self-signed certificate mid-transfer is rejected by both sides before any file byte is transmitted — no user dialog, no override.
3. Transport works on all four targets (Android, iOS, macOS, Desktop JVM) for files of arbitrary size, with throughput within ~15% of plain HTTP on the same hardware.
4. The OS trust store is never consulted at any point in the verification path. Removing a peer from the trust store causes the next connection to that peer to fail closed.
5. The transfer-reliability behaviour settled in [#119](https://github.com/khmelevartem/tether/issues/119) continues to behave as specified under TLS.

### When to revisit

This decision is final for MVP. It is revisited only if:

- **Throughput regression** measured at >15% on 1 GB between two devices on home Wi-Fi after the implementation lands. Mitigation: profile and tune before revisiting the choice itself.
- **The Apple-native TLS backend gets a removal date.** A known structural cost — tracked in the ADR, with the retargeting path documented there.
- **[KTOR-7262](https://youtrack.jetbrains.com/issue/KTOR-7262) closes** (Ktor ships TLS for Kotlin/Native). A reasonable migration path off the asymmetric server implementation.

## Privacy Invariants

These are non-negotiable:

- **No telemetry without explicit opt-in.** No analytics by default. If telemetry is added later, it ships off by default with a visible toggle.
- **No metadata leaves the device.** File names, sizes, peer identities — none of this is uploaded anywhere.
- **No cloud, no relay, no fallback.** If the LAN is unavailable, transfer fails honestly. We do not silently route through any third party.
- **Discovery announces only what's needed.** Device name (user-controlled) and port. No hardware ID, no email, no phone.

## Logging Policy

Tether runs local logs to help users (and us) debug network issues. Rules:

- **Local-only.** Logs stay on the device. They are never uploaded, even on crash.
- **Default level: warning + error.** Info/debug logs are off by default; the user can flip a switch in settings to capture a verbose session when reporting a bug.
- **No file contents, no peer identities beyond the local pairing record.** Log lines record events ("transfer started", "peer disconnected", "discovery failed") and error reasons — never bytes, never file names by default.
- **User-accessible.** The user can view, export, and clear the log from within the app. "Export" produces a file the user can attach to a manual bug report — never an automated upload.

Crash reporting and remote performance metrics are explicitly **out of scope for MVP**. If they are added later, they follow the same rule: opt-in, off by default, visible to the user.

## Open Questions

- Auto-rotation of paired keys after N transfers / N days?
- Visible "this device sent / received X files from Y" log — useful for trust, or privacy-leaky?
