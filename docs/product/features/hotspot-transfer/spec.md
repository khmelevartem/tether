# Hotspot transfer — sending files when one device shares Wi-Fi

**Area:** Discovery / Transfer
**Status:** `scoped`
**GitHub Issues:** [#170](https://github.com/khmelevartem/tether/issues/170) (design); implementation issues to follow

---

## Why

Tether's two-device exchange only works if both devices can reach each other over a local network. The most common case where this breaks is also the most common case where people *actually* need to move a file between two phones in person: there is no shared Wi-Fi available, so one of them turns on their phone's hotspot and the other connects to it. Apartment with no router. Hotel room with a captive portal on the lobby network. Friend's place where the Wi-Fi password has been long forgotten. A car. A park.

Today the user's expectation in that moment is reasonable — *we are on the same Wi-Fi now, just send the file* — and Tether does not consistently meet it. The Wi-Fi is technically shared, but the discovery layer often fails to reach across the hotspot's tethering interface. The user ends up troubleshooting silence.

This feature closes that. It is also the second meaningful proof point — after [pairing](../pairing/spec.md) — that Tether is honest about its "no cloud, no accounts, two taps" promise: the moment the underlying connectivity exists, Tether finds it and uses it, without asking the user to know what mDNS is.

## What it does

When two devices are on the same Wi-Fi connection — including the case where one of them is *providing* that Wi-Fi via a phone hotspot — they show up in each other's device list within a few seconds, and files transfer in either direction. The user does nothing different from the normal home-Wi-Fi case. No mode switch, no separate "hotspot mode" screen, no special permission they have to know to grant.

If automatic discovery does not succeed within a short window — because of an OS quirk, an unusual hotspot configuration, or a network that drops the underlying broadcasts — the user can type the other device's IP address (which both phones expose in their own Wi-Fi / hotspot settings). The typed peer is remembered for next time so the user does not have to retype it on subsequent encounters.

## User flows

**Primary flow — phone shares Wi-Fi, the other phone connects**

1. Anna wants to send a video to Boris. There is no shared Wi-Fi nearby.
2. Boris turns on the personal hotspot on his phone; tells Anna the hotspot name and password.
3. Anna joins Boris's hotspot from her phone's normal Wi-Fi settings.
4. Both open Tether. Within a few seconds, each sees the other in the device list — the same device list, with the same row appearance, that they would see on home Wi-Fi.
5. Anna taps Boris, picks the video, completes pairing (if this is their first encounter), and the file transfers.

The roles are interchangeable. Boris can equally send to Anna over the same hotspot; nothing in the flow depends on who is the host.

**Alternative paths**

- **Discovery is slow at first.** If the device list is still empty after a few seconds, Tether tries harder in the background, silently — no "searching…" spinner, no progress text. The peer just appears slightly later. The user sees no visible change unless and until we are confident automatic discovery has truly failed.
- **Discovery fails.** After a longer window with the list still empty, an unobtrusive affordance appears: *"Don't see the other device? Scan a QR code."* Tapping it opens the in-app scanner; the other phone shows its QR from the same affordance. Scanning either side completes the introduction — the peer appears on both. The QR encodes the same information `/hello` carries, so once scanned the device is indistinguishable from any other peer in the list.
- **Two desktops without a convenient camera.** The same affordance offers manual IP entry as a secondary option. The user types the other computer's IP (and, optionally, a port — pre-filled with the right default). Each device shows its own IP in the same surface so the value is easy to dictate. This is the only common case where typing is preferable to scanning.
- **Reconnecting later.** Every device the user has ever paired with stays in the device list as a paired entry, regardless of how it was originally discovered — see [wifi-availability/spec.md](../system/wifi-availability/spec.md) for the row contract that covers offline-paired devices. Next time Anna joins Boris's hotspot, Boris is one tap away from the device list — she does not retype, does not rescan. The same set of paired devices is also surfaced as a secondary "recent peers" section on the out-of-band introduction screen (below the QR scanner and the manual-entry field) so the user can re-engage them without leaving that screen; this is one concept, presented on whichever surface the user is currently on.
- **Hotspot is provided by a laptop or desktop instead of a phone.** Same flow. Tether does not care which device is acting as the AP.
- **Two devices, both on a guest Wi-Fi that blocks peer-to-peer.** Same fallbacks: QR scan or manual entry. Tether does not pretend to solve networks that genuinely block all peer-to-peer traffic.

## What "working" looks like

- Both Anna and Boris turn their phones on, Boris enables hotspot, Anna joins it, both open Tether. Within a few seconds, the other device is visible in the list on both phones. No troubleshooting, no permissions surprise mid-flow.
- Files transfer in either direction at the speed the underlying Wi-Fi link allows. Receiving works whether the device is the hotspot host or a client.
- If the user has to fall back to QR scan or manual entry, the introduction succeeds, and they are not asked to repeat the scan or type the IP again the next time they meet the same device — it already appears as a paired device on the device list (and, equivalently, in the recent-peers section of the out-of-band screen).
- The whole experience uses the same device-list screen, the same pairing dialog, the same transfer progress — there is no separate "hotspot UI". A user who has never been told about hotspot mode does not realise anything special happened.
- Reinstalling Tether on one side, or rebooting either phone, does not change behaviour beyond what pairing already specifies (a reinstall is a fresh first-encounter).

## Platform notes

Hotspot transfer does not introduce platform permissions of its own — the OS prompts the user sees are the same ones described in [permissions/spec.md](../system/permissions/spec.md) for normal discovery. The cases where hotspot adds nothing user-visible beyond what already exists:

- **iOS Personal Hotspot.** Works in both default and "Maximize Compatibility" modes; the user is not asked to flip the toggle.
- **macOS as host.** Internet Sharing from System Settings; discovery picks up the shared interface like any other.
- **Windows / Linux as host.** Windows Mobile Hotspot, `nmcli` / `hostapd` on Linux; same flow.
- **iPhone as host.** No special handling; Bonjour publishes on the hotspot interface.

## Not in this feature

- **Transfer with no Wi-Fi at all** — no router *and* no hotspot. That would require a different transport (Wi-Fi Direct or Wi-Fi Aware), and Apple does not expose a public API for either; it is recorded as an Android-only Pro hypothesis in [monetization.md](../../monetization.md), not part of this feature.
- **Sharing the hotspot password.** Tether does not help the user broadcast their hotspot SSID or password; that is the OS's job. Tether picks up only after the user has joined the network through the normal Wi-Fi UI.
- **Cross-subnet discovery.** If the two devices are connected to the same logical network but through a routed boundary (corporate VLANs, mesh repeaters configured as separate subnets), Tether does not bridge that. Manual entry remains the user's escape hatch.
- **Pairing changes.** Pairing is unchanged — first encounter still shows the 4-digit PIN, regardless of how the two devices found each other.
- **Trust changes.** Discovery still does not imply trust; pairing remains the gate. See [security.md](../../security.md).

## Open product questions

- **Should the app surface that the device is currently sharing Wi-Fi?** A subtle indicator ("You're sharing Wi-Fi — other devices can find you here") could help the host understand why their phone is suddenly visible to a stranger's Tether install. Could equally be a noisy mode-indicator that nobody reads. Decide after first user observations.
- **Idle time before the out-of-band affordance appears.** Too short and the user sees it when discovery is still working; too long and they conclude the app is broken. A small number of seconds — exact value tuned after first usage.
- **QR-only vs camera-less symmetry.** Tether shows the QR on each device's out-of-band screen so the other can scan it. For desktop users without a camera, do we always offer both QR (to be scanned by a phone) and manual entry (typed on the desktop), or pick one path per device class? Lean toward always-both for consistency, but worth observing.
