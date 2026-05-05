# Design

How Tether looks and feels. This doc captures principles and key flows; per-screen specs live in feature docs.

## Principles

- **One visual language across all platforms.** Tether does not adopt the native look of each OS. The same layout, typography, palette, and iconography on Android, iOS, macOS, Windows, and Linux. Consistency is part of the value proposition — the user recognizes Tether instantly on any device.
- **Minimalism, not blank.** Few elements per screen, generous spacing, no decorative chrome. But each state (empty, searching, found, transferring, error) has a clear visual identity.
- **Two taps to send.** Pick device → pick file. Anything that adds a step needs to justify itself.
- **Show what's happening.** Discovery, pairing, transfer — every async action has a visible state. No silent waits.
- **Honest empty states.** When no peers are found, say *why* (e.g. "No devices on this Wi-Fi yet" / "Make sure both devices are on the same network").

## Visual Language

| Aspect | Choice |
|--------|--------|
| Type system | One sans-serif across platforms (system-default fallback acceptable, but the same scale/weights everywhere) |
| Palette | Neutral surface + one accent for active state and progress |
| Iconography | One set, custom or from a single open-source family — no platform-native glyphs mixed in |
| Density | Comfortable on touch; the same layout scales to desktop without becoming sparse |
| Dark mode | First-class, not an afterthought |
| Motion | Subtle. Used to confirm state changes (peer appeared, transfer started, transfer finished), never decorative |

## Key Screens

### Device list (primary screen)

The screen the user opens Tether to see.

States:
- **Empty / searching** — animated indicator + hint about Wi-Fi requirement.
- **Peers found** — list of devices with name, platform icon, and pairing status (paired vs. unknown).
- **No network / Wi-Fi off** — clear instruction to turn it on; no list shown.

Key interactions: tap a peer to send → file picker opens. Long-press / right-click for peer details (rename, unpair).

### Transfer / Progress

Visible on both sender and receiver during an active transfer.

States:
- **Sending** — file name, size, progress bar, ETA, cancel.
- **Receiving** — same, plus "Save to…" option (if applicable per platform).
- **Done** — success affirmation, "Open" / "Show in folder" actions.
- **Failed** — reason in plain language, retry action where applicable.

## Tone of Voice

- Plain language. No jargon ("peers" → "devices", "discovery" → "searching", "handshake" → don't surface).
- Short. UI strings under ~6 words where possible.
- Same wording across platforms — no localized neologisms per OS.

## Accessibility (minimum bar)

- All controls reachable by keyboard on desktop; by screen reader on mobile.
- Color is never the only signal of state (icons + text always accompany).
- Contrast meets WCAG AA in both themes.

## Out of Scope (here)

- Pairing screen and onboarding — described in their feature docs once written.
- Settings — minimal in MVP; covered when the surface grows beyond device name.

## Open Questions

- Do we ship a custom icon set in MVP or borrow one (e.g. Lucide, Phosphor)?
- Brand color: pick one in MVP or leave neutral until later?
