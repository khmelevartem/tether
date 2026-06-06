# Competitors

How Tether sits among the tools people use today to move files between devices. The point of this doc is not exhaustive market research — it's to make positioning decisions defensible.

## Comparison

| Tool | Platforms | Cost | Account | Compresses media | LAN-only | Encryption | Persistent pairing |
|------|-----------|------|---------|------------------|----------|------------|-------------------|
| **Tether** (target) | Android, iOS, macOS, Windows, Linux | Free + Pro | No | No | Yes | TBD (see [security.md](../security/README.md)) | Yes (SAS comparison) |
| AirDrop | iOS, macOS only | Free | Apple ID for some flows | No | Yes (Bluetooth + Wi-Fi) | Yes | Implicit via contacts |
| LocalSend | Android, iOS, macOS, Windows, Linux | Free (donations) | No | No | Yes | Yes (TLS, self-signed) | Optional ("favorites") |
| Snapdrop / PairDrop | Web (any browser) | Free | No | No | Yes (same network only) | Yes (WebRTC) | No |
| Send Anywhere | Android, iOS, macOS, Windows | Freemium | Optional | No | Cloud relay | Yes (TLS) | No (6-digit one-time key) |
| Microsoft Phone Link | Android ↔ Windows 11 (limited iPhone) | Free | Required (Microsoft account) | No | Bluetooth + Wi-Fi + cloud | Yes (Microsoft-managed) | Yes (per Microsoft account) |
| Telegram "Saved Messages" / DM | Everywhere | Free | Required | **Yes** for photos/video unless "as file" | No (cloud) | Server-side (cloud) | n/a |
| Email to self | Everywhere | Free | Required | Sometimes (provider-dependent) | No | Provider-dependent | n/a |
| USB cable | Phone ↔ desktop only | Free | No | No | n/a | n/a | n/a |

## Notes per competitor

### AirDrop — the gold standard, locked to one ecosystem

The benchmark for "this should just work." Two taps, no setup, lossless. Tether's UX target is AirDrop-grade. The opportunity Tether takes: AirDrop is useless the moment one of your devices is not made by Apple.

### LocalSend — the closest direct comparable

Open source, cross-platform, LAN-based, no account, no compression. Architecturally very similar to Tether (HTTP server on each peer, mDNS discovery, optional pairing). Differences we can lean on:

- **Polish and consistency.** LocalSend's UI is functional but inconsistent across platforms. Tether's "single visual language" principle (see [design.md](design.md)) is a real differentiator if executed.
- **Onboarding.** LocalSend works but assumes some technical comfort. Tether targets non-technical users (see [audience.md](audience.md)).
- **Pro features.** LocalSend is donation-only; folder sync and multi-peer would be plausible Pro hooks for Tether (see [monetization.md](monetization.md)).

LocalSend is the strongest comparable. If Tether can't articulate why a user should pick it over LocalSend, the project doesn't have a reason to exist.

### Snapdrop / PairDrop — clever, but no install means no persistence

Browser-based, zero-install, runs over WebRTC on the same network. Great for one-shot transfers between strangers. Bad for "my own devices that I use every day" — no pairing, no app icon, no background presence.

### Send Anywhere — cloud relay disguised as P2P

Cross-platform, but routes through their servers. Freemium with size caps. Tether's "no cloud" principle is the explicit anti-pattern.

### Microsoft Phone Link — bundled, single-pair, account-bound

Built into Windows 11. Optimizes a specific axis (Android phone ↔ Windows PC) and includes notifications, calls, and SMS bridging on top of file transfer. Microsoft account required; some routes touch the cloud. For the narrow "I have an Android and a Windows PC" segment it's already preinstalled — that's a real distribution advantage we cannot match. Where Phone Link fails: the user has more than one OS pair (e.g. Android + Mac, or iPhone + Windows beyond what Apple allows), or doesn't want a Microsoft account, or is sending between two phones.

### Telegram / WhatsApp — what users actually use today

The real default for sending files between own devices for many people. Compresses photos and video unless explicitly sent "as file" — and most users don't know to do that. Requires accounts. This is the *behavioral default* Tether displaces, even though it's the worst technical choice.

### Email / USB — fallbacks, not products

Email has size limits and provider-dependent compression. USB requires the right cable and OS permissions, doesn't work phone↔phone. Both are what users fall back to when nothing else works — they shape baseline expectations but aren't real competition.

## How Tether differs (in priority order)

1. **Genuinely cross-platform with one consistent UI.** AirDrop fails on the platform axis; LocalSend wins on platforms but fragments per-OS visually.
2. **Original bytes always.** Beats messengers on quality.
3. **No account, no phone number, no contact list.** Beats messengers and Send Anywhere on friction and privacy.
4. **LAN speed, no size limits.** Beats anything cloud-based on transfers above a few hundred MB.
5. **Privacy by architecture, not by promise.** No server means no data to leak. Beats every cloud-based option.

## Honestly: why does Tether exist if LocalSend exists?

This deserves a direct answer. LocalSend ships today on every platform we target, has no accounts, no cloud, no compression, and is free. By the time Tether reaches MVP, ~80–90% of what Tether promises will already be a one-tap install away on the App Store, Play Store, and every major desktop OS.

**Stated differentiators, with skepticism:**

- *"Single visual language across platforms."* Maker preference more than user demand. Most non-technical users never compare a phone app to a desktop app side-by-side. Real but small.
- *"Non-technical onboarding."* LocalSend is already easy. Closing this gap requires concrete interaction work, not a stance.
- *"Pro features (folder sync, multi-peer)."* Speculation until shipped. LocalSend can add either at any time.

**Possible real wedges (need proving by execution, not by claim):**

- **iOS receive done right.** LocalSend's iOS app exists, but iOS background receive is structurally difficult — the OS aggressively suspends apps. A polished "we just got a file in the background" experience is a real, hard-won feature. If Tether nails this, it's a wedge. If not, no.
- **An opinionated, fewer-knobs UX.** LocalSend exposes a lot of options (favorites, ports, history, methods). A version that decides for the user (sane defaults, two visible affordances) is *different*, even if "better" is subjective.
- **Pairing-first model.** LocalSend treats pairing as optional ("favorites"). Tether makes pairing the default trust unit, which produces a more "AirDrop-like" experience for repeat use between own devices.
- **Pro that LocalSend can't easily do.** A donation-only OSS project has structural constraints around supporting paid features. If a real Pro market exists for folder sync / multi-peer (see [monetization.md](monetization.md)), Tether can iterate there faster than a community project.

**Honest reasons that aren't "the product is better":**

- KMP exploration / portfolio value for the maintainer.
- Control over direction without depending on a community roadmap.
- Optionality: if any of the wedges above prove out, having an independent codebase to act on them is worth the cost of duplication.

**Bottom line.** There is no a-priori reason a user *must* pick Tether over LocalSend today. The wedge has to be earned through execution — specifically through (a) an iOS receive experience meaningfully better than what LocalSend offers, and (b) a curated UX bar that says "no" to options LocalSend says "yes" to. If after MVP we cannot point at one concrete moment where a typical user will notice Tether is better, the honest move is to stop and contribute to LocalSend instead. This bar is intentional — write it down so we hold ourselves to it.

## Open Questions

- Pricing reference: free comparables (LocalSend, Snapdrop) make any Pro tier compete on real convenience, not on "premium" framing. Worth re-checking before committing to monetization (see [monetization.md](monetization.md)).
- Distribution disadvantage vs Microsoft Phone Link (preinstalled on Windows 11) and AirDrop (preinstalled on Apple). For users locked into one ecosystem these are zero-friction defaults — Tether wins only when their device set crosses ecosystems. Worth quantifying market size of cross-ecosystem households.
