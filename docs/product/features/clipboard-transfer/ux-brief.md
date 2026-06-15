# UX brief — Clipboard transfer

**Spec:** [spec.md](spec.md)
**Status:** `ready`

---

> Clipboard transfer moves the current clipboard contents to a trusted peer, ready to paste. The central UX invariant: **the clipboard is small, ephemeral, and "what I just copied"** — so its controls live on the existing per-peer surface (the PeerCard), its sends are fire-and-forget, and arriving content is governed entirely by the receiver's own setting. Everything in this brief depends on that invariant; the file-transfer progress machinery, transfer history, and device-list row contract are referenced, never redefined.

## Information architecture

This feature introduces no new top-level screen. It **extends the PeerCard** (baseline owned by [device-list/ux-brief.md](../device-list/ux-brief.md), already extended with transfer-active states and the Auto-send toggle by [file-transfer/ux-brief.md](../file-transfer/ux-brief.md)) with per-peer clipboard controls (a manual Send clipboard action, a Clipboard sync switch on Desktop/macOS only, and a per-peer auto-apply toggle), an inline send-feedback state, and a **per-peer Clipboard inbox section** that holds received items in Notify mode. It introduces one **OS notification** (notify mode) that carries an inline Copy action and opens the peer's card.

The Clipboard inbox is **not** a separate top-level screen or nav destination: received items for a peer surface as a section inside *that peer's* expanded PeerCard, scoped per peer. Because a received item is local to the receiver, it must stay reachable even after the sender goes offline; this feature therefore **extends the device-list row contract** by making the offline-paired (Case 3) row expandable — gaining the expand chevron and unread badge — *while and only while* it holds pending clipboard items (see the Clipboard inbox section's [owned extension](#owned-extension-the-offline-paired-row-becomes-expandable-while-it-holds-clipboard-items); reflect it in [device-list/ux-brief.md](../device-list/ux-brief.md) when that doc is next touched).

```
DeviceListScreen (root, owned by device-list)
└── scrollable list of PeerCards
      └── PeerCard
            ├── Idle (collapsed)              ← owned by device-list / file-transfer
            │     ├── online-paired (Case 2): inherited expand chevron ▾   ← baseline (file-transfer)
            │     ├── offline-paired (Case 3): baseline = hint-only, NO chevron (device-list)
            │     │     └── + chevron ▾ added by THIS BRIEF *while items pending* (owned extension)
            │     └── + unread-clipboard indicator on the expand chevron ▾  ← THIS BRIEF (when items pending for this peer)
            └── Idle (expanded)               ← owned by file-transfer (Auto-send toggle)
                  ├── + Clipboard controls    ← THIS BRIEF
                  │     ├── "Send clipboard" action  → fire-and-forget toast  (shown only when Clipboard sync is OFF / absent)
                  │     │     └── inline error state (empty / unsupported)
                  │     ├── "Clipboard sync" switch (Desktop/macOS only; absent on mobile)
                  │     └── "Auto-apply incoming" toggle (On = Auto-apply, Off = Notify; default Off) — governs items from THIS peer
                  └── + Clipboard inbox section  ← THIS BRIEF (Notify-mode received items, per peer)
                        └── inbox item (masked-where-sensitive preview)
                              ├── [Copy]    → into own clipboard, item leaves section
                              ├── [Dismiss] → item leaves section
                              └── auto-apply hint (dismissible, non-nagging)

Receiving side (Notify mode):
  arriving item ──▶ OS notification (masked-where-sensitive preview)
              │           ├── [Copy] action  → real content to clipboard, item leaves the peer's inbox section
              │           └── tap            → opens the source peer's PeerCard (expanded, at its Clipboard inbox section)
              └──▶ Clipboard inbox section of the source peer's PeerCard (masked-where-sensitive preview)
```

Screens introduced: none — this feature adds no new screen; it extends the existing PeerCard with a per-peer Clipboard inbox section and introduces one OS notification (clipboard-received, with Copy action).
Screens touched: PeerCard (Idle collapsed + expanded) — owned by [file-transfer/ux-brief.md](../file-transfer/ux-brief.md) and [device-list/ux-brief.md](../device-list/ux-brief.md).

---

## Clipboard-sync direction

Clipboard sync is send-only per side — its canonical definition and rationale live in [spec.md](spec.md#what-it-does). This brief describes only the switch's user-facing behaviour, not the decision.

---

## Screens

### PeerCard — Idle (expanded), clipboard controls

**Purpose.** Give the user, on a specific trusted peer, a one-shot "Send clipboard" action, a per-peer "Clipboard sync" switch (Desktop/macOS only), and a per-peer auto-apply toggle (what happens to clipboards arriving *from this peer*), sitting beside the existing Auto-send (file) toggle without confusing the two.

**Entry points.** The user expands a PeerCard (taps the chevron `▾`, owned by [file-transfer/ux-brief.md](../file-transfer/ux-brief.md)). The clipboard controls appear in the same expanded inline block, **below** the Auto-send (file) toggle, under a short divider/section label that separates "files" concerns from "clipboard" concerns.

The expanded block, top to bottom — **Send clipboard shown** (mobile always; Desktop/macOS when Clipboard sync is OFF):
1. Auto-send (file) toggle — owned by file-transfer, unchanged.
2. Section label: **"Clipboard"** (groups the controls below, and disambiguates from Auto-send above).
3. **Send clipboard** action (a button, not a toggle).
4. **Clipboard sync** switch (On/Off) with `[i]` info affordance — **Desktop/macOS only; not present on mobile** (see Per-platform deltas).
5. **Auto-apply incoming** toggle (On = Auto-apply, Off = Notify) — governing only clipboards arriving from *this* peer; see the [auto-apply toggle](#peercard--auto-apply-toggle-per-peer) below.
6. **Clipboard inbox** section (Notify-mode received items for *this* peer) — present only when this peer has pending items, and only meaningful while this peer's auto-apply toggle is Off (Notify); see the [Clipboard inbox section](#peercard--clipboard-inbox-section) screen below.

The expanded block, top to bottom — **Send clipboard hidden** (Desktop/macOS when Clipboard sync is ON): Send clipboard (item 3) is absent because every copy already auto-sends, so the block collapses to:
1. Auto-send (file) toggle — owned by file-transfer, unchanged.
2. Section label: **"Clipboard"**.
3. **Clipboard sync** switch (On) with `[i]` info affordance.
4. **Auto-apply incoming** toggle.
5. **Clipboard inbox** section (when this peer has pending items).

**Layout.**

- The clipboard controls are visually distinct in *shape*: Send clipboard is an action button (does something once); Clipboard sync and Auto-apply incoming are switches (persistent state). This shape contrast is the primary cue that one is one-shot and the others are standing.
- When Send clipboard is shown (mobile, or Desktop/macOS with sync OFF), it sits above the Clipboard sync switch so the most common act in that case (send once) is reached first. This ordering rationale applies only while Send clipboard is shown; when Clipboard sync is ON the button is absent and the sync switch is the first clipboard control, so there is nothing to order above it.
- The peer-identity accent already present on a paired PeerCard (owned by device-list) is retained; clipboard controls add no new accent.

**States.** The Send clipboard action is itself a small state machine; the switches are simple On/Off persisted preferences.

#### Send clipboard action

##### a. Ready (default)
- A button labeled **"Send clipboard"** with a leading clipboard icon.
- **Visibility.** Shown only when manual send is the meaningful path: on mobile (always — there is no Clipboard sync control), and on Desktop/macOS when this peer's Clipboard sync is OFF. When Clipboard sync is ON, manual send is redundant (every copy already auto-sends) so the button is **hidden, not disabled**, for that peer. Turning sync back OFF restores it.
- Enabled whenever the peer is reachable (online & paired). On an offline-paired peer the button is disabled, matching the device-list rule that offline-paired rows do not start transfers. An offline-paired card is only reachable in the first place when it holds pending clipboard items (this feature makes such a row expandable — see [owned extension](#owned-extension-the-offline-paired-row-becomes-expandable-while-it-holds-clipboard-items)); in that expanded card the disabled Send clipboard sits above the still-usable Clipboard inbox section, so the user can Copy / Dismiss received items while sending stays unavailable until the peer returns.
- Tap → attempts to read the local clipboard and send.

##### b. Sent (transient confirmation)
- On a successful send, a **fire-and-forget toast** appears: "Clipboard sent". No preview step, no delivery receipt, no progress bar (clipboard payloads are small; the file-transfer progress machinery is deliberately not used).
- The button returns to Ready immediately; the toast auto-dismisses (~2 s).

##### c. Empty clipboard (inline error)
- If the clipboard holds nothing sendable, **no toast, no send**. An **inline error** appears in-place beneath the Send clipboard button: "Nothing to send".
- Non-blocking: the button stays enabled; the user can copy something and tap again. The inline error clears on the next tap or after a short delay.

##### d. Unsupported content (inline error)
- If the clipboard holds content outside the supported payload (anything that is not text or a typed URL — e.g. an image or a file reference), inline error beneath the button: "Can't send this yet — only text and links".
- Same non-blocking behaviour as (c).

Empty and unsupported are **inline** (anchored to the control the user just acted on), never toasts — the user needs the *reason* tied to the button, and a toast that announces "nothing happened" reads as a failure rather than an explanation.

#### Clipboard sync switch (Desktop/macOS only)
- **Off (default).** Label "Clipboard sync". No clipboard is auto-sent to this peer. The Send clipboard button is shown.
- **On.** Standing intent: send this device's clipboard to this peer as it changes (a background watcher; the OS permits this on Desktop/macOS). The Send clipboard button is hidden for this peer (sync makes it redundant).
- **Not present on mobile.** Android and iOS show no Clipboard sync control at all — the OS blocks background clipboard reads, so the switch could not work in the background and the user cannot affect its availability; showing it would mislead. On mobile, only the manual Send clipboard action exists. See Per-platform deltas and Platform notes.

**Interactions.**

- Tap **Send clipboard** (peer online): reads clipboard → on success, "Clipboard sent" toast; on empty, inline "Nothing to send"; on unsupported, inline "Can't send this yet — only text and links".
- Tap **Send clipboard** (peer goes unreachable between expand and tap): inline "Can't reach \<peer\>." No retry button — the user re-taps when the peer is back (consistent with clipboard being fire-and-forget; no delivery receipt to chase).
- Toggle **Clipboard sync** (Desktop/macOS): flips the per-peer preference immediately, no confirm dialog (local-to-peer preference). Turning it On hides the Send clipboard button for this peer; turning it Off restores it.
- Tap `[i]` beside Clipboard sync: tooltip/popover explaining the send-only direction (copy below).

**Copy.**

- Section label: "Clipboard"
- Send clipboard button: "Send clipboard"
- Send success toast: "Clipboard sent"
- Empty clipboard inline: "Nothing to send"
- Unsupported content inline: "Can't send this yet — only text and links"
- Peer unreachable on send: "Can't reach \<peer\>."
- Clipboard sync switch label (Desktop/macOS): "Clipboard sync"
- Clipboard sync `[i]` (Desktop/macOS): "When on, Tether sends this device's clipboard to \<peer\> as it changes. It doesn't pull \<peer\>'s clipboard back."

**Per-platform deltas.**

- **Android:** No Clipboard sync control — only the manual Send clipboard action. Send clipboard is the mobile path and works exactly like a file send's reach check.
- **iOS:** Same as Android — no Clipboard sync control, manual Send clipboard only. iOS additionally shows a system paste-confirmation banner the first time an app reads the clipboard programmatically in some OS versions; that is an OS surface, not a Tether one — note for the implementer, do not design around it. Send clipboard reads on user tap (a clear user gesture), which is the friendliest case for the OS paste prompt.
- **macOS:** Clipboard sync switch present, running a real background watcher (OS imposes no background clipboard-read restriction). Desktop `[i]` copy. Send clipboard hidden while sync is On.
- **Desktop (JVM — Windows, Linux):** Same as macOS — Clipboard sync switch with a real background watcher; Desktop `[i]` copy; Send clipboard hidden while sync is On.

**Accessibility.**

- Send clipboard: semantic role "button", label "Send clipboard to \<peer\>". Disabled state announced as "dimmed / unavailable" with reason when focused on an offline-paired peer ("\<peer\> is offline").
- Inline errors (empty / unsupported / unreachable): announced as an alert / live-region update tied to the button, not as a transient toast (so a screen-reader user is not racing an auto-dismiss).
- "Clipboard sent" toast: announced once, politely (not assertive) — it is confirmation, not an alert.
- Clipboard sync switch (Desktop/macOS): role "switch", label "Clipboard sync to \<peer\>, currently \<On/Off\>".
- Shape contrast (button vs switch) is reinforced by role in the semantics, so a screen-reader user distinguishes one-shot from standing without seeing the shapes.

---

### PeerCard — auto-apply toggle (per-peer)

**Purpose.** Let the user decide, for a specific trusted peer, what happens to clipboard content arriving *from that peer*: when On, it is silently applied (Auto-apply); when Off, it is surfaced for review (Notify). One per-peer On/Off toggle, symmetric to the per-peer Clipboard sync send switch — each peer owns both its outbound switch and its inbound toggle.

**Entry points.** The expanded PeerCard's Clipboard controls block, directly below the Clipboard sync switch (and below Send clipboard on mobile, where there is no sync switch). It governs only items from this one peer.

**Layout.** A single labeled On/Off switch, grouped under the same "Clipboard" section label as the send controls:
- **On** = Auto-apply — clipboard content arriving from this peer silently replaces this device's clipboard.
- **Off** = Notify — content arriving from this peer raises a notification and is held in this peer's Clipboard inbox section until you copy or dismiss it.

A short caption beneath the toggle states the current behaviour and (when On) the privacy implication, so the consequence is visible at the point of choosing.

**Default.** **Off (Notify)** is the default for every peer — the receiver is never surprised by a silent clipboard replacement from a peer before opting in. Turning the toggle On is a deliberate per-peer choice the user makes when they trust a peer enough for the friction-free "just paste it" path. The auto-apply hint on this peer's inbox items (decided below) flips exactly this toggle On.

**States.**
- **Off (Notify, default):** caption describes where this peer's items go (held in the inbox until copied or dismissed).
- **On (Auto-apply):** caption carries the one-line caution (see Copy) so the privacy implication is visible.

This is a per-peer receive setting, symmetric to that peer's send switch and orthogonal to it (see the [direction pointer](#clipboard-sync-direction) above).

**Interactions.**
- Toggle On / Off for this peer: applies immediately, no confirm dialog. Turning On also dismisses this peer's currently-shown auto-apply hint (the hint exists only to offer this very flip). Turning Off resumes raising notifications and holding items for this peer.

**Copy.**
- Toggle label: "Auto-apply \<peer\>'s clipboard"
- Off caption: "Off — you get a notification and keep each item until you copy or dismiss it."
- On caption: "On — \<peer\>'s clipboards replace yours automatically."

**Per-platform deltas.**
- **Android / iOS / macOS / Desktop:** default — same single On/Off toggle, same copy.

**Accessibility.**
- Role "switch", label "Auto-apply \<peer\>'s clipboard, currently \<On/Off\>". The active caption (including the Auto-apply caution when On) is part of the accessible description so the consequence is not vision-only. The label names the peer so a screen-reader user knows the toggle is scoped to this one peer.

---

### PeerCard — Clipboard inbox section

**Purpose.** Within a specific peer's PeerCard, hold the clipboard items that arrived from *that peer* in Notify mode, so a missed or dismissed notification never loses them; let the user copy an item into their own clipboard or dismiss it, scoped to where the item came from. This is a **section of the expanded PeerCard**, not a separate screen or nav destination.

**Entry points.**
- Tapping the **[Copy]** action on the clipboard-received OS notification copies directly (no navigation needed — see the notification screen).
- Tapping the **body** of the clipboard-received OS notification opens the source peer's PeerCard, expanded, scrolled to its Clipboard inbox section.
- Expanding the source peer's PeerCard at any time from the device list — the section is there whenever that peer has pending items, so items are recoverable even if the notification was never seen, and **whether the source peer is currently online or offline-paired**. (On an offline-paired peer this feature makes the row expandable precisely so its pending items stay reachable — see [owned extension](#owned-extension-the-offline-paired-row-becomes-expandable-while-it-holds-clipboard-items).) This is the notification-independent path, load-bearing on Linux where notification clicks are unreliable.
- An **unread-clipboard indicator** badged on the collapsed PeerCard's expand chevron (see below) signals which peer has pending items and that expanding reveals them, so the user knows where to look — and which gesture reaches the inbox — without hunting and without mistakenly tapping the body (which sends a file).

**Layout.**
- The section sits below the Clipboard controls in the expanded card, under its own short label **"Clipboard inbox"**.
- A list of pending items from this peer, most-recent first. Because the section is already scoped to one peer, items do **not** repeat the peer name; they show a content preview (masked when sensitive — see Masked preview), an arrival time, and the affordances **[Copy]** and **[Dismiss]**.
- **Capped at 5 pending items.** The section holds at most 5 pending items per peer (spec rule). A pending item is never auto-applied and is never dropped while it stays within the 5-item window — it leaves only when copied, dismissed, or displaced. Displacement happens only beyond the cap: when a 6th item arrives with 5 already pending, the oldest pending item is removed to make room. This is the sole way an item leaves the inbox other than Copy or Dismiss, consistent with the clipboard being ephemeral (no history, no overflow surface). The displaced item carries **no separate dismissal cue**: the feature is ephemeral and keeps no history, the list is already visibly full at its cap, and the new item appearing at the top while the bottom one leaves is itself the visible signal of displacement — a toast or banner for an ephemeral overflow would read as an error rather than the expected behaviour of a fixed-size, most-recent-first list. (See state 5.)
- The most-recent item also carries a dismissible **auto-apply hint** (see state 4) when this peer is set to Notify (auto-apply toggle Off) and the hint has not been permanently dismissed.

**Unread-clipboard indicator (collapsed PeerCard).**
- When a peer has one or more pending inbox items, its collapsed PeerCard shows a small unread marker (a count or dot) in the peer-identity accent, **anchored to the expand chevron `▾`** (the affordance that opens the expanded card and its Clipboard inbox section) — a badge *on the chevron*, not floating on the card body. The device list reveals at a glance which peer is holding clipboard items, and the badge's placement teaches the gesture: the cue sits exactly on the control that reaches the inbox, so the user expands rather than tapping the card body to reach it.
- **Why on the chevron, not the body.** The collapsed row's body tap initiates a file-send (owned by device-list / file-transfer). A cue floating on the card body would invite the user to tap the body — triggering a file-send — while reaching for their incoming clipboard. Anchoring the badge to the chevron disambiguates "expand to reach my incoming clipboard" from "tap the body to send a file" without any explanatory copy.
- **Where the chevron comes from, by collapsed state.** Unread clipboard items exist only for trusted/paired peers (clipboard transfer is gated on pairing — only paired devices are ever sources): the online-paired (Case 2) and offline-paired (Case 3) collapsed rows.
  - **Online-paired (Case 2):** the expand chevron is an **inherited baseline** — a trailing affordance contributed by [file-transfer/ux-brief.md](../file-transfer/ux-brief.md) so the user can open the expanded card and its per-peer settings. The unread badge rides on that existing chevron.
  - **Offline-paired (Case 3):** the inherited baseline has **no expand chevron**. The device-list contract makes the Case 3 row a non-button list item whose only tap behaviour is an inline hint; it does not enter the expanded settings card ([device-list/ux-brief.md](../device-list/ux-brief.md)). This feature therefore **adds expandability — and with it the expand chevron and unread badge — to the offline-paired row, but only while that peer holds pending clipboard inbox items.** This is an explicit owned extension by this feature (see [owned extension](#owned-extension-the-offline-paired-row-becomes-expandable-while-it-holds-clipboard-items) below), not an inherited baseline. A received clipboard item is already local to the receiver; it must stay reachable regardless of whether its sender is currently online. With no pending items, the offline-paired row keeps its device-list baseline unchanged (no chevron, hint-only tap).
- So in every collapsed state where an unread item can exist, the chevron is present — inherited on Case 2, added by this feature on Case 3 while items are pending. No fallback anchor is needed.
- The marker clears when the user next **expands** that peer's card — they have now seen what arrived, whether or not they act on each item. (It does not wait for every item to be copied or dismissed; expanding is the "seen it" signal.) Items themselves persist in the inbox section until copied or dismissed.

#### Owned extension: the offline-paired row becomes expandable while it holds clipboard items

- **What this feature adds.** On the offline-paired (Case 3) row, *while and only while* that peer has one or more pending clipboard inbox items, this feature makes the row expandable: it gains the expand chevron `▾` (carrying the unread badge), and expanding opens the same expanded PeerCard, which here renders the Clipboard inbox section so the user can Copy / Dismiss the received items. This is an explicit extension to the device-list row's state set, owned by this feature; the baseline Case 3 row (hint-only, non-button) is unchanged whenever no clipboard items are pending.
- **Why it must hold for an offline peer.** A received clipboard item lives entirely on the receiver's device — it does not depend on the sender being reachable to be read. The spec's no-loss / recovery guarantee (a missed or dismissed Notify item must remain recoverable) would otherwise break the moment the sender goes offline: a peer could send a Notify item while online, the user could miss the notification, the peer could go offline, and the item — though sitting in the user's own inbox — would be stranded behind a row that cannot be expanded. Making the offline-paired row expandable while it holds items closes that gap.
- **The expanded offline-paired card is receive-only.** Expanding an offline-paired card surfaces the Clipboard inbox section (Copy / Dismiss work — those are local actions). The Send clipboard action is present but **disabled** with its offline reason (see the [Send clipboard Ready state](#a-ready-default) and Flow 9): the receiver can always reach what arrived, but cannot send to a peer that is not reachable. The per-peer Clipboard sync and Auto-apply toggles remain settable (they are preferences, not live operations).
- **Contract-sync note.** This extension to the offline-paired (Case 3) row belongs in device-list's own row contract; reflect it there when [device-list/ux-brief.md](../device-list/ux-brief.md) is next touched so the two briefs do not drift. Until then, this brief is the authority for the offline-paired row's expandable-while-pending behaviour.

This reuses the device-list row contract for the online-paired case and **extends** it for the offline-paired case (adding the expandable-while-pending state and its chevron); it does not otherwise redefine the row.

**States.**

##### 1. No pending items (section absent)
- When the peer has no pending items, the Clipboard inbox section is simply **not rendered** in the expanded card — there is no standalone empty screen to land on, so a calm empty illustration would have nowhere to live. The expanded card just shows the Clipboard controls.

##### 2. Item — normal preview
- Preview shows the text (single line, truncated) or, for a typed URL, the link rendered as a link (recognised per spec). Tapping the preview does not navigate — only [Copy] / [Dismiss] act.

##### 3. Item — masked preview (sensitive content)
- When the platform flagged the arriving content as sensitive (passwords, one-time codes), the preview is **masked**: rendered as a dotted/bulleted placeholder (e.g. "••••••••") rather than clear text, both here and in the notification.
- The item is still fully copyable: **[Copy]** places the *real* content into the clipboard; the mask is a display-only protection, never a transformation of the payload.
- A small masked-content indicator (a lock-style icon + text) tells the user why they see dots: "Sensitive content" — the same phrasing the notification uses.

##### 4. Auto-apply hint (dismissible, on the most-recent item)
- A gentle, **non-nagging** suggestion attached beneath the most-recent inbox item, shown only while this peer's auto-apply toggle is Off (Notify) **and** the hint has never been dismissed: "Want \<peer\>'s clipboard to land automatically? Turn on auto-apply." with a quiet **[Turn on auto-apply]** affordance and a **[×]** dismiss.
- It is the soft, opt-in nudge decided for Notify mode: it informs the receiver they can skip the extra Copy step for future items, without pressure.
- **No dark patterns.** The hint is unobtrusive (a quiet caption, not a banner or modal), never blocks Copy/Dismiss, and is **dismissible**. Dismissal is **permanent and global**: once the user taps **[×]**, the auto-apply hint never reappears again — on any peer, ever — so it can never become a recurring nag. Dismissing the hint kills **only the hint**, never the per-peer auto-apply toggle: that toggle stays in its place in the Clipboard controls block on every peer, On or Off as set. So a peer the user later trusts enough to auto-apply is still reachable — the user flips its toggle directly in the expanded card. After permanent dismissal, the hint's one-tap shortcut is gone but the destination it pointed to is not.
- Accepting the hint instead (tapping **[Turn on auto-apply]**) turns On *this peer's* auto-apply toggle — only items from this one peer land automatically thereafter; every other peer is untouched. The hint states this plainly so the choice is honest.

##### 5. At capacity — oldest item displaced
- When this peer already holds 5 pending items and a 6th arrives, the list stays at 5: the new item appears at the top (most-recent first) and the oldest item leaves the bottom. The displacement is shown by the list movement itself, with no toast or banner — an ephemeral, no-history overflow surface treats a full fixed-size list as expected behaviour, not an error to announce.
- The displaced item is gone (the same terminal outcome as Dismiss, with no undo); the user can ask the sender to resend.

**Interactions.**
- Tap **[Copy]** on an item: real content (clear, even if the preview was masked) goes into this device's clipboard; the item **leaves the section**. Brief toast: "Copied to clipboard". If it was this peer's last item, the section disappears.
- Tap **[Dismiss]** on an item: item leaves the section; nothing is copied. No undo (ephemeral by design; the user can ask the sender to resend).
- Tap **[Turn on auto-apply]** on the hint: turns On *this peer's* auto-apply toggle and dismisses the hint. Future items *from this peer* land silently; other peers are unaffected.
- Tap **[×]** on the hint: dismisses the auto-apply hint **permanently and globally** — it never appears again on any peer. Items from this peer still arrive in Notify mode as before.
- The unread-clipboard indicator (collapsed card) clears when the user next expands this peer's card (they have seen what arrived), independent of whether items are copied or dismissed.
- An item is never auto-applied from the section, and is never dropped while it stays within the 5-item window (spec: a missed/dismissed notification must not lose it — it persists in the peer's section until Copy or Dismiss). The only exception is cap displacement (state 5): when a 6th item arrives with 5 already pending, the oldest is removed to make room.

**Copy.**
- Section label: "Clipboard inbox"
- Masked indicator: "Sensitive content"
- Copy action: "Copy"
- Dismiss action: "Dismiss"
- Copy confirmation toast: "Copied to clipboard"
- Auto-apply hint: "Want \<peer\>'s clipboard to land automatically? Turn on auto-apply."
- Auto-apply hint action: "Turn on auto-apply"
- Auto-apply hint dismiss (accessible label): "Dismiss this tip"

**Per-platform deltas.**
- **Android:** Tapping the notification's [Copy] action copies in place; tapping the notification body opens the device list with the source peer's card expanded at its Clipboard inbox section. Dismiss on an item maps to the platform swipe-to-dismiss idiom in addition to the explicit [Dismiss] button.
- **iOS:** Same; the notification's Copy action copies in place, tapping the body opens Tether at the source peer's expanded card (iOS cannot deep-link mid-screen, but Tether routes to the right peer on launch). Swipe-to-dismiss idiom available alongside the button.
- **macOS:** The notification's Copy action copies in place; tapping the body activates Tether and expands the source peer's card at its inbox section.
- **Desktop (JVM):** Where the system-tray notification (Windows) supports inline actions, [Copy] copies in place; body click opens the source peer's expanded card. On Linux, notification action and click fidelity vary by DE (consistent with file-transfer's note); the guaranteed path is expanding the peer's card from the device list, surfaced by the unread-clipboard indicator — this is exactly why the in-card section plus the collapsed-card indicator are the dependable recovery surface. The path holds even when the source peer has since gone offline, because this feature keeps the offline-paired card expandable while it holds pending items (see [owned extension](#owned-extension-the-offline-paired-row-becomes-expandable-while-it-holds-clipboard-items)).

**Accessibility.**
- The Clipboard inbox section: role "list" nested in the PeerCard; each item is a list item exposing two actions (Copy, Dismiss) as accessibility actions in addition to visible buttons.
- Masked item: the accessible label states "Sensitive content, hidden" — a screen reader must **not** read the masked content aloud (defeats the mask). [Copy] still places the real content into the clipboard; the screen reader announces the action, not the secret.
- Copy confirmation announced politely; Dismiss announced as the item being removed.
- Auto-apply hint: announced as a tip (polite, not assertive), with its [Turn on auto-apply] and dismiss actions exposed; it must not steal focus from the Copy/Dismiss actions of the item it sits under.
- Unread-clipboard indicator: the badge rides on the expand chevron, so the count is folded into the chevron's own accessible label rather than the row body — the screen reader announces the affordance *and* the pending count together: **"Expand \<peer\> settings, 2 clipboard items waiting"** (singular "1 clipboard item waiting"). Folding the count into the expand control (not the body) keeps the screen-reader path aligned with the visual one — focus the chevron, hear there are items, activate to reach them — so it is not a vision-only cue and does not steer toward the body's file-send action.
  - **Online-paired (Case 2):** this extends the chevron's base label "Expand \<peer\> settings" (owned by [file-transfer/ux-brief.md](../file-transfer/ux-brief.md)) only while items are pending; with no pending items the chevron keeps its base label.
  - **Offline-paired (Case 3):** the baseline row has no chevron at all (it is a non-button list item — [device-list/ux-brief.md](../device-list/ux-brief.md)). While items are pending, this feature's [owned extension](#owned-extension-the-offline-paired-row-becomes-expandable-while-it-holds-clipboard-items) adds the chevron and it carries the same label form, e.g. **"Expand \<peer\> settings, 2 clipboard items waiting"**. With no pending items the offline-paired row keeps its device-list semantics (list item, "Tap for hint"), no chevron.
- Focus order (Desktop, expanded card): Clipboard controls → inbox items top to bottom, each item Copy then Dismiss, then the auto-apply hint's action then its dismiss → end of card.

---

### Clipboard-received notification (Notify mode)

**Purpose.** Tell the receiver, when in Notify mode, that a clipboard item arrived, with enough preview to decide whether to act — without exposing sensitive content.

**Entry points.** Raised automatically when a clipboard item arrives and the device is in Notify mode. Not raised in Auto-apply mode.

**Layout (OS notification).**
- Title: source peer name.
- Body: content preview — clear text / link for normal content; **masked** ("••••••••" + "Sensitive content") for flagged content.
- A **[Copy]** action on the notification itself: acting on it places the item's real content onto this device's clipboard directly, without opening the app.
- Tapping the notification **body** opens the source peer's PeerCard (expanded, at its Clipboard inbox section — the item is also already there).

**States.**
- **Normal preview:** truncated text or the link.
- **Masked preview:** dotted placeholder; never the clear value, even truncated. The **[Copy]** action still copies the *real* content — the mask is display-only and does not change what Copy places on the clipboard.

**Interactions.**
- Tap **[Copy]** action → real content (clear, even for a masked preview) goes onto this device's clipboard; the item **leaves the source peer's inbox section**; brief confirmation per OS conventions. This is the fast path — no navigation required.
- Tap notification body → open the source peer's expanded PeerCard at its Clipboard inbox section, where the item still waits for Copy / Dismiss.
- A **[Dismiss]** action **where the platform reliably supports it** is a nice-to-have, not required; dismissing the notification without acting leaves the item in the peer's inbox section (it is not dropped). Implementer call per platform (see below).

**Copy.**
- Notification title: "\<peer\>"
- Normal body: the previewed text / URL (truncated by the OS).
- Masked body: "Sensitive content" (with a dotted glyph) — never the real value.
- Notification Copy action: "Copy"

**Per-platform deltas.**
- **Android:** standard notification; the inline **Copy** action is feasible and required, with optional Dismiss. Respect the OS notification channel for clipboard items. Body tap routes to the source peer's expanded card.
- **iOS:** notification; the **Copy** action via notification-category actions is feasible and required. Body tap opens Tether and routes to the source peer's expanded card on launch.
- **macOS:** system notification; the **Copy** action is feasible and required. Body tap activates Tether → source peer's expanded card.
- **Desktop (JVM):** Windows system-tray notification supports the **Copy** action; Linux notification-action fidelity is best-effort (DE-dependent, same fragility as file-transfer completion notifications). Where the Copy action or click-through is not guaranteed (some Linux DEs), the peer's in-card inbox section — surfaced by the unread-clipboard indicator — is the fallback and the guaranteed path.

**Accessibility.**
- The notification follows the OS notification accessibility model. For masked items, the notification body must not contain the clear sensitive value (so assistive tech reading the notification does not leak it) — it reads "Sensitive content from \<peer\>". The **Copy** action is announced by its label ("Copy"); the screen reader announces the action, not the secret, even though Copy places the real value on the clipboard.

---

## Flows

### Flow 1 — Manual send to a trusted peer (primary)
1. User copies text or a URL on their device.
2. User expands the target peer's PeerCard and taps **Send clipboard** (peer is online & paired; the button is present because Clipboard sync is Off or unavailable on mobile).
3. Tether reads the clipboard and sends it. A **"Clipboard sent"** toast appears and auto-dismisses. No preview, no receipt.
4. On the receiver: handled per the receiver's own per-peer auto-apply toggle — silently applied (On) or surfaced as a notification + inbox item (Off / Notify).
Terminal state: sender card back to Ready; nothing recorded in transfer history.

### Flow 2 — Clipboard sync, Desktop/macOS sender (primary)
1. User turns **Clipboard sync** On for a peer on a Desktop/macOS device. The Send clipboard button hides for that peer (sync makes it redundant).
2. Whenever the local clipboard changes, its content is auto-sent to that peer with no per-copy action (background watcher; OS permits).
3. Receiver handles each item per their own auto-apply toggle.
4. User turns the switch Off → nothing is sent after that; the Send clipboard button reappears.
Terminal state: switch persists Off; watcher stopped; manual send available again.

### Flow 3 — Receiving in Notify mode (default)
1. An item arrives from a peer whose auto-apply toggle is Off / Notify (the default for every peer).
2. A notification shows a preview (masked if sensitive) and carries a **[Copy]** action. The item also lands in the source peer's Clipboard inbox section, and the peer's collapsed card shows the unread-clipboard indicator badged on its expand chevron.
3. Fast path: user taps the notification's **[Copy]** → real content goes onto their clipboard with no navigation; the item leaves the peer's inbox section.
4. Alternative path: user taps the notification body → the source peer's PeerCard opens expanded at its Clipboard inbox section; or the user expands that peer's card from the device list independently. Expanding clears the unread indicator. There they tap **[Copy]** (toast "Copied to clipboard") or **[Dismiss]**. Either way the item leaves the section.
5. Until the auto-apply hint has been permanently dismissed, the most-recent inbox item shows it, inviting the user to let this peer's clipboard land automatically.
Terminal state: the peer's inbox section no longer holds that item; unread indicator already cleared on the expand.

### Flow 4 — Receiving in Auto-apply mode
1. Item arrives from a peer whose auto-apply toggle is On.
2. It silently replaces the receiver's clipboard. No notification, no inbox entry.
3. User pastes wherever they were working, without opening Tether.
Terminal state: receiver's clipboard holds the item.

### Flow 5 — Empty clipboard on manual send (failure, non-blocking)
1. User taps **Send clipboard** with nothing sendable on the clipboard.
2. No send, no toast. Inline "Nothing to send" beneath the button.
3. User copies something, taps again → Flow 1.
Terminal state: nothing sent; user told why.

### Flow 6 — Unsupported content on manual send (failure, non-blocking)
1. User taps **Send clipboard** with an image / file reference on the clipboard.
2. No send. Inline "Can't send this yet — only text and links".
Terminal state: nothing sent; user told why; can recover by copying supported content.

### Flow 7 — Sensitive content end to end
1. Sender copies a one-time code / password the OS flags as sensitive; sends (manual or sync).
2. Content is sent (trusted-peer gate is the safety boundary — only paired devices are ever targets/sources).
3. Receiver in Notify mode sees a **masked** preview in both the notification and the peer's inbox-section item. [Copy] — whether the notification's Copy action or the in-card [Copy] — places the real value; the dots are display-only.
Terminal state: sensitive value reaches the trusted peer, never shown in clear in any preview.

### Flow 8 — Notify item missed / dismissed-by-mistake (recovery)
1. Notify-mode item arrives; user misses or swipes away the notification.
2. The item is still in the source peer's Clipboard inbox section (notification dismissal does not drop it); the peer's collapsed card carries the unread-clipboard indicator on its expand chevron.
3. User spots the unread indicator on the chevron in the device list, taps the chevron to expand that peer's PeerCard (the badge teaches the gesture; tapping the body would start a file send). Expanding clears the indicator, and the user copies the item from its Clipboard inbox section.
Terminal state: item recovered and copied, or explicitly dismissed.

### Flow 9 — Notify item recovered after the source peer has gone offline (recovery, no-loss guarantee)
1. A peer sends a Notify-mode clipboard item while online; it lands in that peer's Clipboard inbox section and raises a notification. The user misses or dismisses the notification.
2. The source peer goes offline — its row drops from online-paired (Case 2) to offline-paired (Case 3).
3. Because the peer still holds pending clipboard items, its offline-paired row is **expandable** (this feature's owned extension): it shows the expand chevron with the unread-clipboard badge, instead of the baseline hint-only Case 3 row.
4. User taps the chevron to expand the offline-paired card. The Clipboard inbox section is rendered; **Copy / Dismiss work as normal** (they act on local content). The Send clipboard action is disabled with its offline reason — sending needs the peer back, recovering the received item does not.
5. User taps **[Copy]** → the item goes onto their own clipboard; it leaves the section. Expanding had already cleared the unread indicator.
Terminal state: the received item is recovered even though its sender is offline; nothing was lost. The no-loss / recovery guarantee holds end to end, sender connectivity notwithstanding.

### Flow 10 — Peer unreachable on manual send (failure)
1. Peer drops offline between card-expand and the Send clipboard tap.
2. Inline "Can't reach \<peer\>." No send, no retry button.
3. User re-taps when the peer is back online.
Terminal state: nothing sent; consistent with fire-and-forget (no receipt to chase).

### Flow 11 — Accepting the auto-apply hint (Notify → Auto-apply, this peer only)
1. In this peer's Notify mode, the user reads the dismissible auto-apply hint under that peer's most-recent inbox item.
2. User taps **[Turn on auto-apply]** → *this peer's* auto-apply toggle turns On, the hint clears.
3. Future items *from this peer* land silently per Flow 4; every other peer keeps its own setting.
Terminal state: this peer's auto-apply toggle is On; no further extra Copy step for it. The user reached it by choice, never by nag.

**Entry point after the hint is permanently dismissed.** Once the user has dismissed the auto-apply hint globally (state 4, **[×]**), this flow's hint-driven entry point no longer appears. It degrades — it does not vanish: the user reaches the same destination unaided by expanding the peer's PeerCard and flipping its **Auto-apply incoming** toggle in the Clipboard controls block directly. The one-tap shortcut is gone; the toggle is not.

---

## Navigation

No new top-level navigation and no new screen. The clipboard controls (Send clipboard, Clipboard sync on Desktop/macOS, per-peer auto-apply toggle) **and the Clipboard inbox section** all live inside the expanded PeerCard (in-place, no push, no modal) — both the auto-apply toggle and the received items are scoped per peer within that peer's card, never a separate destination. The online-paired card is expandable by its inherited baseline; the offline-paired card is made expandable by this feature **while it holds pending clipboard items**, so received items stay reachable regardless of the sender's connectivity (see the Clipboard inbox section's [owned extension](#owned-extension-the-offline-paired-row-becomes-expandable-while-it-holds-clipboard-items)). The clipboard-received notification's **[Copy]** action acts without navigating; its **body tap** routes to the source peer's PeerCard expanded at its Clipboard inbox section (on mobile, Tether routes to that peer on launch). The collapsed-card unread-clipboard indicator points the user to the peer holding items, the notification-independent recovery path. Toasts, inline errors, and the auto-apply hint are transient/in-place and add nothing to the back stack.

---

## Platform notes

### Android
- The OS blocks background clipboard reads, so a sync watcher cannot run; the Clipboard sync control is therefore **not shown** at all. Send clipboard is the only mobile path. Receiving (auto-apply or notify) works as on Desktop.

### iOS
- Same background limitation as Android — no Clipboard sync control. The OS may show its own paste-permission banner on programmatic clipboard read; that is an OS surface — do not design around it, but reading on an explicit user tap (Send clipboard) is the friendliest trigger. Receiving works as on Desktop.

### macOS
- Real background clipboard watcher for Clipboard sync; OS imposes no restriction. Send clipboard hides while sync is On. Notification tap activates Tether → inbox.

### Desktop JVM (Windows, Linux)
- Real background clipboard watcher. Notification click-through and inline-action fidelity are reliable on Windows, best-effort on Linux (DE-dependent, mirrors file-transfer's notification fidelity note). The source peer's in-card Clipboard inbox section, surfaced by the collapsed-card unread-clipboard indicator, is the guaranteed recovery path where notification Copy / click is unreliable — and it stays guaranteed even after the source peer goes offline, since this feature keeps the offline-paired card expandable while it holds pending items.

---

## Conceptual components

1. **Clipboard controls block** — the per-peer cluster (section label "Clipboard", Send clipboard action, Clipboard sync switch + `[i]` on Desktop/macOS, auto-apply toggle) appended below the Auto-send (file) toggle inside the expanded PeerCard. Extends PeerCard (Idle expanded), baseline owned by [file-transfer/ux-brief.md](../file-transfer/ux-brief.md).
2. **Send clipboard action** — a one-shot action button with Ready / Sent-toast / empty-inline / unsupported-inline / unreachable-inline states, shown only when Clipboard sync is Off or absent. Shape (button) and label distinguish it from the standing switches beside it.
3. **Fire-and-forget toast** — transient, politely-announced "Clipboard sent" / "Copied to clipboard" confirmation. No progress, no receipt. Distinct from file-transfer's persistent PeerCard terminal states.
4. **Inline control error** — a non-blocking message anchored to the control that was acted on (empty / unsupported / unreachable). Never a toast; announced as an alert. Conceptually akin to device-list's offline-row inline hint (anchored, non-modal, non-auto-dismissing-as-failure).
5. **Clipboard sync switch** — a persisted per-peer On/Off send-only switch, present on Desktop/macOS only (absent on mobile). When On, it hides the Send clipboard action for that peer.
6. **Auto-apply toggle** — a per-peer On/Off switch inside the expanded PeerCard's Clipboard controls block (On = Auto-apply, Off = Notify), defaulting to Off for every peer, with a behaviour caption; symmetric to that peer's Clipboard sync send switch.
7. **Clipboard-received notification** — an OS notification carrying source peer + preview (masked-where-sensitive), a direct **Copy** action that places the real content on the clipboard, and a body tap that opens the source peer's PeerCard.
8. **Clipboard inbox section** — a per-peer list of that peer's pending received items inside the expanded PeerCard, with per-item Copy / Dismiss, masked-preview item variant, and the auto-apply hint; absent when the peer has no pending items. Not a standalone screen.
9. **Unread-clipboard indicator** — a per-peer badge **on the collapsed PeerCard's expand chevron** (count/dot in the peer-identity accent) signalling pending inbox items; the chevron is the affordance that opens the inbox, so the badge teaches "expand to reach incoming clipboard" and stays off the body's file-send tap target. Clears when the user next expands that peer's card (they have seen what arrived), independent of copying/dismissing; its count folds into the chevron's accessible label. On the online-paired (Case 2) row the badge rides the inherited chevron; on the offline-paired (Case 3) row this feature **adds** both the chevron and the badge while items are pending — its owned extension to the device-list row's state set (see the Clipboard inbox section's [owned extension](#owned-extension-the-offline-paired-row-becomes-expandable-while-it-holds-clipboard-items)). It does not otherwise redefine the row.
10. **Auto-apply hint** — a dismissible, non-nagging caption under a peer's most-recent inbox item, inviting the auto-apply toggle On; once dismissed it is gone permanently and globally (never reappears on any peer), no dark patterns.
11. **Masked preview** — a display-only dotted rendering of sensitive content used in both the notification and the inbox-section item; the real payload is still copied verbatim by any Copy affordance, and assistive tech must not read the secret aloud.
12. **Peer-identity accent (reused)** — the existing peer-identity accent, reused on the unread-clipboard indicator and the PeerCard to identify the source peer; baseline owned by [device-list/ux-brief.md](../device-list/ux-brief.md), not redefined here.

---

## Implementer layout calls

- **Echo suppression for Clipboard sync (send-only model).** An item that arrived via clipboard transfer and was auto-applied (Auto-apply mode) must not itself re-trigger an outbound auto-send back toward the sender. The suppression mechanism (e.g. tagging auto-applied content, or distinguishing local-user copy events from programmatic clipboard writes) is the implementer's; the user-visible requirement is simply "no echo loop". This is the behavioural backbone of the send-only decision.
- **Unread-clipboard indicator shape.** The collapsed PeerCard's pending marker (count vs dot, exact badge geometry on the chevron) is the implementer's idiom call, as long as it is **anchored to the expand chevron `▾`** (not the card body, which taps to file-send), reads in the peer-identity accent, **clears when the user next expands that peer's card** (independent of copying/dismissing items), and its count folds into the chevron's accessible label ("Expand \<peer\> settings, N clipboard items waiting"). It is the notification-independent recovery cue — load-bearing on Linux where notification clicks are unreliable.
- **Auto-apply hint placement within the inbox section.** The hint attaches to the most-recent item by default; exact rendering (inline caption vs trailing affordance) is the implementer's call, provided it stays quiet, never blocks Copy/Dismiss, and shows only while the peer's auto-apply toggle is Off (Notify). Its dismissal is permanent and global — once dismissed, the hint is never shown again on any peer. No dark patterns.
- **Auto-apply toggle shape.** A single per-peer On/Off switch (On = Auto-apply, Off = Notify), defaulting to Off, sitting within the peer's Clipboard controls block. The active behaviour caption (especially the Auto-apply caution when On) must be visible at the point of choosing.
- **Send-clipboard vs Auto-send visual separation.** The "Clipboard" section label plus the button-vs-switch shape contrast is the required disambiguation. If a divider/label proves too heavy in the expanded card, fall back to clear grouping spacing — but the user must never confuse the file Auto-send toggle with the Clipboard sync switch.
