# Wi-Fi availability

**Area:** System integration
**Status:** `idea`
**GitHub Issues:** _tbd_

---

> Stub. Captures the gap. Flesh out before the feature enters a sprint.

## Why

Tether is local-first: every capability — discovery, pairing, transfer — depends on the device being on a Wi-Fi network. When Wi-Fi is off, none of it works. Today the app would silently show the empty "Searching for devices…" state, which is misleading: nothing is being searched, the network is simply absent.

The user must understand that the cause of "nothing is happening" is "Wi-Fi is off" — not a Tether bug, not an empty room, not a slow network. One quick toggle in the OS shade and it works again. Tether's job is to make that obvious.

## What it does (sketch)

- Detects the current network state: Wi-Fi on with a network, Wi-Fi off, no network at all (e.g. ethernet-only desktop).
- Surfaces a clear, distinguishable message in places that depend on the network — primarily the [device list](../device-list.md), but also pre-flight on transfer and pairing screens.
- The message is short and actionable: "Wi-Fi is off — turn it on to find devices nearby." Not a generic "no connection" toast.
- When the user turns Wi-Fi back on, the app recovers without needing a manual restart.

## Not in this feature

- Wi-Fi credentials, network selection, captive portals — Tether does not manage networks, only reacts to their state.
- Cellular / mobile-data fallback — out by design, see [vision.md](../../vision.md): "If the LAN can't carry it, we say so honestly."
- Internet connectivity check — Tether is local; reaching the internet is not relevant.
- Ethernet-only desktop as a usable transport — open question, see below. For now Tether's surface is Wi-Fi.

## Open product questions

- **Ethernet-only desktop.** A Mac or Windows tower on Ethernet without Wi-Fi *can* host mDNS and Tether on the LAN — so technically it works. Do we say "Wi-Fi is off" anyway (lying, but consistent), say "no network" (vague), or detect Ethernet and say nothing (correct, but adds platform-specific code)?
- **Recovery latency.** When Wi-Fi flicks back on, how fast does the device list repopulate — instantly, or after the next mDNS scan tick? User-visible.
- **Transient drops mid-transfer.** Wi-Fi briefly disappearing during a transfer is a different surface — covered by transfer-failure UX in [file-transfer.md](../file-transfer.md), not here.
