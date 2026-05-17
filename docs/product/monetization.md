# Monetization

Working hypothesis. Subject to validation — see open questions.

## Hypothesis

**Freemium.** Core peer-to-peer transfer is free forever. Paid tier ("Pro") unlocks convenience features that mostly matter to power users with steady, repeated workflows.

The reasoning: Tether's value proposition (cross-platform, no cloud, no accounts, lossless) collapses if any of it is paywalled. Paying to send a file is the wrong frame. Paying to *automate* repeated sending is a different frame.

## Free, Forever

Everything that defines the product:

- Discovery + pairing on the local network.
- Sending and receiving any file, any size, any number of devices.
- All supported platforms (Android, iOS, macOS, Desktop). No platform-locked features.
- Progress, cancel, retry.

If any of this becomes paid, we have failed the positioning.

## Pro Candidates

These are *not* committed — they're the directions we'd explore first.

| Candidate | Why it fits a paid tier |
|-----------|-------------------------|
| **Folder sync** | A persistent, automated workflow. Power user need. Implementation cost is real (watching, conflict handling). |
| **Multi-peer / group send** | Sending the same file to several devices at once. Useful for small offices, families with many devices. Convenience, not core. |
| **Android↔Android offline transfer (Wi-Fi Direct / NAN)** | P2P fallback when no shared Wi-Fi exists at all — no router, no hotspot. Asymmetric by nature (Apple has no public API), so it's an extra mode on a subset of platforms rather than part of the core promise. |
| **Advanced discovery (Layers 3 + 4)** | The fallback layers in [hotspot-transfer/spec.md](features/hotspot-transfer/spec.md) — HTTP-subnet-scan, UDP-broadcast, QR scan, manual IP entry — kick in when standard mDNS does not reach. Architecturally cheap to gate by user status (each layer is app-initiated; see the slice diagram in [adr-hotspot-discovery.md](../engineering/adr/adr-hotspot-discovery.md)). Free tier keeps Layers 1–2: home Wi-Fi works, iPhone-as-host hotspot usually works. Paid unlocks the layers that close Android-as-host, multicast-blocked networks, and the manual escape hatch. |

Anything else considered later passes the same test: *does removing it break the promise?* If yes — it stays free.

### A note on the "advanced discovery" hypothesis

The hotspot-transfer spec frames "send a file when there is no shared Wi-Fi" as an important case Tether *should* handle. Gating the most common shape of that case — Android phone sharing Wi-Fi — behind paid would partly contradict that framing. Two readings sit in tension here, neither obviously right:

1. **Hotspot is core; paid is for elsewhere.** Keep all four discovery layers free. Paid features are workflow-level (folder sync, multi-peer), not connectivity-level. Cleaner with the "never restrict the core flow" principle.
2. **Home / Apple-host hotspot is core; adverse cases are paid.** Free tier covers the scenarios that work *automatically* on a normal network. Paid covers the cases that need active probing (HTTP-scan, UDP-broadcast) and out-of-band rescue (QR, manual). Honest about engineering cost; risk of feeling arbitrary to users.

This split needs a product decision before any pricing work starts. Recording the hypothesis here so the engineering option stays open; the call is not made.

## Principles

- **Never restrict the core flow.** No file-size limits, no transfer count limits, no "premium speed."
- **No ads. No tracking. Ever.** Even free tier. This is non-negotiable — privacy is part of the product.
- **No dark patterns.** No artificial friction in free flow to push toward Pro.
- **No account required for Pro either.** A purchase is a license, not an identity. (Mechanism — see open questions.)

## Open Questions

- **Is monetization worth pursuing at all?** Tether may stay a portfolio / pet project. Need a market read before committing engineering time to billing infrastructure.
- **One-time purchase vs subscription?** Subscription only makes sense if there's recurring server cost (there isn't, by design). Lean toward one-time per platform, but app-store policies may force subscription.
- **App-store-only vs cross-platform license?** If the user buys Pro on iOS, does it unlock on their Mac? Cross-platform license is user-friendly but requires some account-like mechanism — tension with the no-accounts principle.
- **Pricing.** No anchor yet. Reference points to research: LocalSend (free), Send Anywhere (freemium with size limits), Snapdrop (free).
