# iOS Background Networking — Constraints and Asymmetric Path

Why Tether on iOS cannot run its current P2P transport in the background, what iOS actually offers, and the asymmetric architecture option that *can* deliver background sends from iOS to non-iOS receivers.

The iOS background story is a hard architectural fork, not a tuning knob.

---

## What Tether's transport does today

A Ktor CIO HTTP server bound to a raw listening TCP socket on every device, discovered via mDNS/Bonjour. The same stack runs on every platform — sender and receiver, Android, iOS, macOS, Desktop JVM. Files move as streaming multipart `POST /upload`.

This works **only while the process owns its network resources**. On iOS, suspended apps don't.

## Hard iOS constraints (what cannot be done)

1. **Arbitrary listening TCP sockets are not permitted in the background.** Once the app suspends (~30 s after backgrounding, or immediately on screen lock if no foreground audio/location/voip session is active), the kernel tears down the listener and any in-flight TCP connections owned by the process. No public entitlement opens this up.
2. **No FGS analogue.** There is no iOS equivalent of Android's foreground service. The `UIBackgroundModes` keys are use-case-specific:
   - `voip` — deprecated for general background networking, App Review rejects misuse.
   - `audio` — requires an active audio session producing real audio; muting and pretending is App Review reject material.
   - `location` — requires legitimate location updates; cannot be used as a "stay alive" backdoor.
   - `fetch` / `processing` — OS-scheduled wake-ups, minutes apart, not a continuous-connection mechanism.
3. **mDNS browsing stops on suspension.** `NSNetServiceBrowser` and `NWBrowser` deliver no callbacks while the app is suspended; the OS does not keep Bonjour discovery alive for third-party apps.
4. **In-flight HTTP requests via `URLSession` default/ephemeral configurations also die** when the app suspends — they share the process lifecycle.

**Conclusion: a peer-to-peer architecture where iOS is *receiver* or *discoverer* in background is impossible** with current iOS APIs and entitlements. This is not a Tether bug; this is iOS sandboxing the network stack.

## What iOS *does* offer: `URLSessionConfiguration.background`

The single sanctioned channel for background networking on iOS:

- **One-shot HTTP(S) requests** (`GET`, `POST`, `PUT`) scheduled by the OS daemon `nsurlsessiond`.
- **Client-only.** No listening sockets, no incoming connections.
- **OS-owned lifecycle.** The app submits a task and is then free to suspend or terminate; `nsurlsessiond` carries the transfer to completion. The app is later relaunched in the background with the completion handler.
- **HTTP/HTTPS only.** Plain TCP, raw sockets, WebSocket — none of these are supported.
- **Discoverable target required.** The URL must be resolvable when the OS daemon decides to run the task — typically a stable internet endpoint. mDNS-only targets that live on the LAN have no stable DNS name; the daemon cannot resolve them.
- **No app-side control over scheduling.** "Discretionary" mode batches work for power/network reasons; even non-discretionary runs are reordered by the OS.

These properties combine into a single shape: **iOS can upload a file in the background to a known HTTPS URL that the OS can resolve without the app being awake.** That is literally the use case Apple designed it for (photo libraries → cloud).

## The asymmetric path: iOS-as-sender in background

A path exists for the narrow case of **iOS sender → non-iOS receiver in background**, at the cost of a parallel transport stack on iOS sender:

1. The receiver (Android / macOS / Desktop) advertises an HTTPS endpoint reachable from the iOS device. On the LAN this means the receiver runs a TLS-terminated HTTPS server with a certificate the iOS device trusts.
2. The iOS sender, while foreground, resolves the receiver via mDNS, captures the IP/port, and constructs an HTTPS URL.
3. The sender submits the upload to `URLSession` configured with `background(withIdentifier:)`.
4. The OS takes over. The app may suspend or terminate; the upload continues.
5. On completion, the app is relaunched in the background to handle the completion callback.

**What this buys.** The user can hit "Send", lock the phone, and the file arrives at the desktop peer — for HTTPS-reachable receivers.

**What this costs.**
- **Transport divergence on iOS.** Sender uses `URLSession`-background path; receiver uses Ktor (foreground-only on iOS). Two implementations to keep behaviourally identical: progress reporting, cancel semantics, error classification.
- **TLS on every receiver.** Today receivers run plain HTTP on the LAN. Background `URLSession` is HTTPS-preferred and, without `NSAllowsArbitraryLoads`, HTTPS-only. Either every receiver provisions a LAN-trusted cert (operationally heavy: each peer is its own CA from iOS's view), or the iOS app ships an `NSAppTransportSecurity` exception narrowly scoped to LAN ranges — App Review allows this but the justification must be clear.
- **mDNS-to-URL translation.** The URL must be stable enough for `nsurlsessiond` to resolve when the OS gets around to running the task. Resolved IP at submission time can be embedded directly in the URL (`https://192.168.1.42:8443/upload`), but if the receiver's IP changes between submission and execution (DHCP renew, reconnect), the task fails with a network error. mDNS hostname (`receiver.local`) requires `nsurlsessiond` to perform mDNS resolution — empirically unreliable from the system daemon's network context.
- **No iOS-as-receiver win.** This path is sender-only. iOS still cannot receive in background, regardless of what the sender does.
- **App Review surface.** Background uploads to LAN IPs draw scrutiny. Justification ("local file transfer between user's own devices") is acceptable but must be present.

**When this becomes worth doing.** When users explicitly ask for "send from phone, lock screen, walk away, file arrives at laptop" and that's a top-3 friction. Until then, the foreground-active MVP is honest about iOS limits and ships sooner.

## What Tether ships on iOS (MVP product decision)

**iOS operates foreground-active only, on both sender and receiver.** The user keeps Tether visible (or at least frontmost) for transfers to start and continue. Screen lock during a transfer interrupts it.

This is documented in `docs/product/features/file-transfer/spec.md` as the iOS platform note. It is not a temporary bug; it follows directly from iOS's network sandbox.

## What Tether explicitly does NOT do on iOS (Post-MVP, conditional)

- **iOS-as-sender background uploads via `URLSession` background.** Architecturally possible; cost is a parallel sender stack on iOS plus TLS on all receivers. Worth revisiting only when foreground-active proves to be a top user pain.
- **iOS-as-receiver background.** Not architecturally possible with current iOS APIs. Tether would need to switch to a relay-based model (e.g. Apple Push Notification "silent push" wake-up + URLSession download from a relay) — incompatible with the local-first principle in [vision.md](../product/vision.md). Not on any roadmap.
- **iOS background mDNS discovery.** Not possible. Would require relay infrastructure.

## Cross-platform symmetry note

Android, macOS, and Desktop JVM all support listening sockets in the background (Android via foreground service; macOS and Desktop JVM as standard process privileges). Only iOS is asymmetric. Treat any "background behaviour" claim in feature specs as iOS-conditional unless explicitly stated otherwise.

## Prior art: LocalSend

The closest open-source architectural analog — LocalSend, a Flutter-based cross-platform P2P file transfer app — reaches the same conclusion. Their iOS build declares no `UIBackgroundModes` and accepts foreground-only behaviour. LocalSend [issue #1468 "iOS/iPadOS run in background"](https://github.com/localsend/localsend/issues/1468) is the maintainer's standing position: a 24/7-listening peer is not possible on iOS, and the workarounds the community proposes (location-services trick, Live Activities, PiP video) either fail App Review or do not actually enable a TCP listener. Tether's "iOS = foreground-active only" stance is the industry-standard outcome for this class of architecture, not a Tether-specific compromise.

## Testing caveat: simulator ≠ device

The Simulator stops receiving the moment the app leaves the foreground. A device keeps receiving for the ~30 s background-execution grace window after backgrounding/lock, so a small file sent within that window still arrives — that is the grace window, not background receive. To confirm the foreground-only constraint on a device, send **>30 s after locking**: the file does not arrive until the app is foregrounded again.

## References

- Apple, "URL Loading System — Downloading Files in the Background." Search Apple Developer Documentation, not linked here — link rot.
- [LocalSend issue #1468 — iOS/iPadOS run in background](https://github.com/localsend/localsend/issues/1468) — prior art for the same architectural conclusion.
- `docs/knowledge/apple-platform.md` — iOS Local Network Privacy and ObjC delegate GC issues, both also affect Tether's foreground transport.
- `docs/knowledge/android-fgs.md` — symmetric problem on Android, and its solution (FGS), for contrast.
- `composeApp/src/iosMain/kotlin/com/tubetoast/tether/MainViewController.kt` — current foreground-tied startup of `FileServer` and `MdnsDiscovery`.
- `iosApp/iosApp/Info.plist` — currently declares only `NSLocalNetworkUsageDescription` + `NSBonjourServices`; no `UIBackgroundModes`. Reflects the MVP decision.
