# Competitors

How Tether sits among the tools people use today to move files between devices. The point of this doc is not exhaustive market research — it's to make positioning decisions defensible.

## Comparison

| Tool | Platforms | Cost | Account | Compresses media | LAN-only | Encryption | Persistent pairing |
|------|-----------|------|---------|------------------|----------|------------|-------------------|
| **Tether** (target) | Android, iOS, macOS, Windows, Linux | Free + Pro | No | No | Yes | TBD (see [security.md](security.md)) | Yes (4-digit code) |
| AirDrop | iOS, macOS only | Free | Apple ID for some flows | No | Yes (Bluetooth + Wi-Fi) | Yes | Implicit via contacts |
| LocalSend | Android, iOS, macOS, Windows, Linux | Free (donations) | No | No | Yes | Yes (TLS, self-signed) | Optional ("favorites") |
| Snapdrop / PairDrop | Web (any browser) | Free | No | No | Yes (same network only) | Yes (WebRTC) | No |
| Send Anywhere | Android, iOS, macOS, Windows | Freemium | Optional | No | Cloud relay | Yes (TLS) | No (6-digit one-time key) |
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

## Open Questions

- Is "we look the same on every platform" a strong enough wedge against LocalSend by itself, or does Tether need a feature LocalSend lacks (e.g. folder sync from MVP)?
- Pricing reference: free comparables (LocalSend, Snapdrop) make any Pro tier compete on real convenience, not on "premium" framing. Worth re-checking before committing to monetization (see [monetization.md](monetization.md)).
