# Brand Mark — `•—•`

Executable spec and design rationale for Tether's signature glyph. For the decision record see [`adr/adr-visual-identity.md`](adr/adr-visual-identity.md). For the product-level description see [`docs/product/design.md`](../product/design.md).

## Geometry

The glyph consists of two filled circles connected by a straight horizontal line:

- Each dot has radius R, derived from the component's height divided by 2.
- The centers of the two dots are spaced 4R apart on a horizontal axis.
- The connecting line has stroke weight 1.2R and runs horizontally between the two dot centers, butting into each dot with no gap.
- Left dot color: `accent` (teal — `#2F7D6B` light / `#3FA08A` dark). Semantics: your device.
- Right dot color: `peerIdentity` (`#C77E47` light / `#D89968` dark). Semantics: the peer.
- Line color: `textPrimary` tone. Neutral connector — not the accent color.

## States

**Idle:** both dots filled, line fully drawn, full opacity. No animation.

**Searching:** the right dot is hollow — outlined, not filled — with stroke weight 1.2R. Its opacity oscillates between 0.4 and 0.7: 2000ms from 0.4 to 0.7, then 2000ms from 0.7 back to 0.4, reversing at each endpoint, running indefinitely with linear easing. The left dot remains solid teal. The line is fully drawn at full opacity. Communicates: your device is broadcasting; waiting for a peer.

Only compose the searching animation while the component is in the searching state. The loop runs continuously while in the composition — gate its instantiation at the call site, not inside the component.

**Transfer progress:** the line fills with `accent` color from left to right, proportional to bytes transferred out of total bytes. The right dot remains `peerIdentity`. The left dot is solid teal. At 100%, the line is fully filled with `accent`.

**Success:** the line is fully filled with `accent`. The right dot performs a synchronisation celebration: its color transitions from `peerIdentity` to `accent` over 200ms ease-out, holds at `accent` for 300ms, then transitions back to `peerIdentity` over 200ms ease-in. Concurrently with the outgoing transition, the right dot performs a brief scale pulse — ease-out, 200ms, scaling to 1.05× and returning to normal size. After the sequence (~700ms total), the right dot rests at `peerIdentity` color again. Semantics: at completion, the tether is briefly "one" — both ends share the active color — before identity is restored.

**Error:** the line is truncated at the failure point — filled with `accent` up to the proportion reached, unfilled beyond. The right dot becomes hollow with a stroke in `error` color.

**Disconnected:** both dots filled (left in `accent`, right in `peerIdentity`), full opacity. The connecting line is broken — rendered as two equal dashed segments of stroke weight 1.2R, each of length R, separated by a central gap of length 2R (total: R + 2R + R = 4R, butting into each dot with no gap). Stroke color stays in the `textPrimary` tone. No animation. Communicates: both ends exist, no tether between them — the local device is not on a usable network. This is the brand-mark state for the no-local-network screen of the device list ([`features/system/wifi-availability/spec.md`](../product/features/system/wifi-availability/spec.md)).

## Where the mark appears

- App icon — static glyph on the brand surface.
- Splash screen — line draws itself left-to-right over ~400ms on first launch.
- In-app empty/searching state — searching state (hollow right dot + pulse).
- Transfer progress indicator — progress state (line fills L→R).
- Success/error confirmation — success and error states respectively.
- No-local-network screen — disconnected state (dashed line between filled dots).

The mark is identical across Android, iOS, macOS, and Desktop. No platform-specific variant.

## Rationale — why a line, not a knot or arrow or paper plane

**Semantic literality.** The word "tether" denotes a physical line between two points under tension. The mark is the etymology rendered geometrically — not a metaphor for the action (sending), but a diagram of the object (the tether itself). The name and the mark say the same thing.

**Empty category cell.** File-transfer competitors occupy distinct visual niches: AirDrop uses radar arcs, Quick Share uses blue circular arrows, Snapdrop uses a parachute, LocalSend uses a cloud-with-arrow, Telegram uses a paper plane, Messenger and Gmail use arrow-on-cloud variants. No competitor uses two-dots-and-a-line. In an icon grid or app store row, the mark is unmistakable.

**Triple duty.** One primitive earns three UI roles: app icon (90% of brand exposure for a rarely-opened utility app), in-app status indicator (right dot pulses while searching), and transfer progress bar (line fills with accent as bytes move). Most marks earn only one of these roles. The triple-duty property means `peerIdentity` appears live on-screen during use, keeping recall active rather than dormant between launches.

**16px legibility.** Two filled dots plus a horizontal line stay readable at favicon size and in OS notification surfaces. A knot with crossing strands or a paper plane silhouette risks blurring to an unrecognizable blob at small sizes.

**Why not a knot.** A rope knot carries richer "binding" semantics, but: it has no progress-bar affordance (a knot does not fill); anti-aliasing crossing strands on iOS Skia at small sizes is unreliable; and the shape risks reading as wedding, maritime, or wellness symbology without severe geometric discipline.

**Why not two-tone split alone** (Interpretation II, preserved as fallback in the ADR). A diagonal split squircle is strong at icon scale, but the two-tone signal disappears inside the running app — only teal remains active as an interactive accent. The mark loses recall during use, at the moment it matters most (transfer in progress). Interpretation I earns the `peerIdentity` dot on-screen with semantic meaning intact.

**Why not an arrow or paper plane.** These shapes are owned by messengers and mail apps. They would read as "this app sends things" — the correct frame for a message, wrong for Tether. Tether is not the act of sending; it is the persistent connection between two devices. The mark must express the object, not the action.

## Reversal note

Interpretation II (split-background icon) is preserved as a reversible fallback in [adr-visual-identity.md](adr/adr-visual-identity.md) § Fallback. Switch only if a future rendering or recall study demonstrates Interpretation I underperforms.
