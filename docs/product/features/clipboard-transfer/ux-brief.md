# UX brief — Clipboard transfer

**Spec:** [spec.md](spec.md)
**Status:** `ready`

---

> Clipboard transfer moves the current clipboard contents to a trusted peer, ready to paste. The central UX invariant: **the clipboard is small, ephemeral, and "what I just copied"** — so its controls live on the existing per-peer surface (the PeerCard), its sends are fire-and-forget, and arriving content is governed entirely by the receiver's own setting. Everything in this brief depends on that invariant; the file-transfer progress machinery, transfer history, and device-list row contract are referenced, never redefined.

## Information architecture

This feature introduces no new top-level screen. It **extends the PeerCard** (baseline owned by [device-list/ux-brief.md](../device-list/ux-brief.md), already extended with transfer-active states and the Auto-send toggle by [file-transfer/ux-brief.md](../file-transfer/ux-brief.md)) with three per-peer clipboard controls (Send clipboard, Clipboard sync, and a per-peer receive mode), an inline send-feedback state, and a **per-peer Clipboard inbox section** that holds received items in Notify mode. It introduces one **OS notification** (notify mode) that carries an inline Copy action and opens the peer's card.

The Clipboard inbox is **not** a separate top-level screen or nav destination: received items for a peer surface as a section inside *that peer's* expanded PeerCard, scoped per peer.

```
DeviceListScreen (root, owned by device-list)
└── scrollable list of PeerCards
      └── PeerCard
            ├── Idle (collapsed)              ← owned by device-list / file-transfer
            │     └── + unread-clipboard indicator   ← THIS BRIEF (when items pending for this peer)
            └── Idle (expanded)               ← owned by file-transfer (Auto-send toggle)
                  ├── + Clipboard controls    ← THIS BRIEF
                  │     ├── "Send clipboard" action  → fire-and-forget toast
                  │     │     └── inline error state (empty / unsupported)
                  │     ├── "Clipboard sync" switch (Desktop only as auto-watcher)
                  │     └── receive mode (Auto-apply | Notify; default Notify) — governs items from THIS peer
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

Surfaces introduced: per-peer Clipboard inbox section (lives inside the PeerCard); one OS notification (clipboard-received, with Copy action).
Surfaces touched: PeerCard (Idle collapsed + expanded) — owned by [file-transfer/ux-brief.md](../file-transfer/ux-brief.md) and [device-list/ux-brief.md](../device-list/ux-brief.md).

---

## Clipboard-sync direction — decision

**Chosen model: send-only per side.**

Turning **Clipboard sync** ON for a peer expresses one direction only: "automatically send *my* clipboard to this peer as it changes." It does not pull the peer's clipboard back. What happens to what *arrives* is governed entirely by the **receiver's own** per-peer clipboard receive mode (Auto-apply or Notify), set independently for each peer on each device. A two-way mirror exists only if *both* devices independently enable sync toward each other; even then, each direction is a separate, separately-owned switch.

**Rationale.**

- **Mental-model clarity.** The switch's label and the spec's fixed intent ("automatically send my clipboard to this peer") are send-only. One switch silently changing what lands in *my* clipboard from the peer would contradict its own label.
- **Consistency with the per-peer receiver setting.** The spec already makes receiving a per-receiver choice (Auto-apply vs Notify), and this brief scopes it per peer. A bidirectional send-switch would override or duplicate that choice — two controls fighting over the same behaviour. Send-only keeps send and receive cleanly orthogonal: each device owns its outbound switch and its inbound mode, both per peer.
- **Privacy.** Send-only means a device never reaches out and reads/overwrites the other's clipboard on the strength of a switch flipped on the *first* device. Each side opts in to sending its own data; neither side's clipboard is touched without that side's own receive-mode consent.
- **Loop / echo risk.** A naive bidirectional mirror risks ping-pong: device A's clipboard changes → sent to B → applied on B → B's clipboard "changes" → echoed back to A. Send-only sidesteps this by construction — auto-send fires on *local user* copy events, and an item that arrived via transfer and was auto-applied must not itself re-trigger an auto-send (an echo-suppression rule the implementer owns; see Implementer layout calls). Even with both directions enabled, the suppression rule is per-device and local, not a distributed mirror to keep consistent.

This is settled here; it is not left as an open question.

---

## Screens

### PeerCard — Idle (expanded), clipboard controls

**Purpose.** Give the user, on a specific trusted peer, a one-shot "Send clipboard" action, a per-peer "Clipboard sync" switch, and a per-peer receive mode (what happens to clipboards arriving *from this peer*), sitting beside the existing Auto-send (file) toggle without confusing the two.

**Entry points.** The user expands a PeerCard (taps the chevron `▾`, owned by [file-transfer/ux-brief.md](../file-transfer/ux-brief.md)). The clipboard controls appear in the same expanded inline block, **below** the Auto-send (file) toggle, under a short divider/section label that separates "files" concerns from "clipboard" concerns.

The expanded block, top to bottom:
1. Auto-send (file) toggle — owned by file-transfer, unchanged.
2. Section label: **"Clipboard"** (groups the two controls below, and disambiguates from Auto-send above).
3. **Send clipboard** action (a button, not a toggle).
4. **Clipboard sync** switch (On/Off) with `[i]` info affordance.
5. **Receive mode** choice — Auto-apply | Notify — governing only clipboards arriving from *this* peer; see the [receive-mode control](#peercard--receive-mode-per-peer) below.
6. **Clipboard inbox** section (Notify-mode received items for *this* peer) — present only when this peer has pending items, and only meaningful while this peer's receive mode is Notify; see the [Clipboard inbox section](#peercard--clipboard-inbox-section) screen below.

**Layout.**

- The two clipboard controls are visually distinct in *shape*: Send clipboard is an action button (does something once); Clipboard sync is a switch (persistent state). This shape contrast is the primary cue that one is one-shot and the other is standing.
- Send clipboard sits above the switch so the most common act (send once) is reached first.
- The peer-identity accent already present on a paired PeerCard (owned by device-list) is retained; clipboard controls add no new accent.

**States.** The Send clipboard action is itself a small state machine; the switch is a simple On/Off persisted preference.

#### Send clipboard action

##### a. Ready (default)
- A button labeled **"Send clipboard"** with a leading clipboard glyph (Tabler icon family; one stroke weight).
- Enabled whenever the peer is reachable (online & paired). On an offline-paired peer the button is disabled, matching the device-list rule that offline-paired rows do not start transfers.
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

#### Clipboard sync switch
- **Off (default).** Label "Clipboard sync". No clipboard is auto-sent to this peer.
- **On.** Standing intent: send this device's clipboard to this peer as it changes.
- The switch's *available behaviour differs by platform* — see Per-platform deltas and Platform notes. On mobile it cannot run a background watcher; the brief makes that asymmetry legible rather than letting the switch silently do nothing.

**Interactions.**

- Tap **Send clipboard** (peer online): reads clipboard → on success, "Clipboard sent" toast; on empty, inline "Nothing to send"; on unsupported, inline "Can't send this yet — only text and links".
- Tap **Send clipboard** (peer goes unreachable between expand and tap): inline "Can't reach \<peer\>." No retry button — the user re-taps when the peer is back (consistent with clipboard being fire-and-forget; no delivery receipt to chase).
- Toggle **Clipboard sync**: flips the per-peer preference immediately, no confirm dialog (local-to-peer preference). On mobile, flipping On surfaces the platform note below the switch (see deltas) so the user is not misled.
- Tap `[i]` beside Clipboard sync: tooltip/popover explaining the send-only direction and the mobile limitation (copy below).

**Copy.**

- Section label: "Clipboard"
- Send clipboard button: "Send clipboard"
- Send success toast: "Clipboard sent"
- Empty clipboard inline: "Nothing to send"
- Unsupported content inline: "Can't send this yet — only text and links"
- Peer unreachable on send: "Can't reach \<peer\>."
- Clipboard sync switch label: "Clipboard sync"
- Clipboard sync `[i]` (Desktop/macOS): "When on, Tether sends this device's clipboard to \<peer\> as it changes. It doesn't pull \<peer\>'s clipboard back."
- Clipboard sync `[i]` (mobile, Android/iOS): "Clipboard sync runs only while Tether is on screen — this system blocks reading the clipboard in the background. Use Send clipboard for a one-off."
- Clipboard sync mobile inline note (shown beneath the switch when On, mobile): "Only sends while Tether is on screen."

**Per-platform deltas.**

- **Android:** Clipboard sync switch is present. Because the OS blocks background clipboard reads, the switch only auto-sends while Tether is foregrounded; the mobile inline note and mobile `[i]` copy make this explicit. Send clipboard is the primary mobile path and works exactly like a file send's reach check.
- **iOS:** Same as Android. iOS additionally shows a system paste-confirmation banner the first time an app reads the clipboard programmatically in some OS versions; that is an OS surface, not a Tether one — note for the implementer, do not design around it. Send clipboard reads on user tap (a clear user gesture), which is the friendliest case for the OS paste prompt.
- **macOS:** Clipboard sync switch runs a real background watcher (OS imposes no background clipboard-read restriction). No mobile inline note. Desktop `[i]` copy.
- **Desktop (JVM — Windows, Linux):** Same as macOS — real background watcher. Desktop `[i]` copy.

**Accessibility.**

- Send clipboard: semantic role "button", label "Send clipboard to \<peer\>". Disabled state announced as "dimmed / unavailable" with reason when focused on an offline-paired peer ("\<peer\> is offline").
- Inline errors (empty / unsupported / unreachable): announced as an alert / live-region update tied to the button, not as a transient toast (so a screen-reader user is not racing an auto-dismiss).
- "Clipboard sent" toast: announced once, politely (not assertive) — it is confirmation, not an alert.
- Clipboard sync switch: role "switch", label "Clipboard sync to \<peer\>, currently \<On/Off\>". On mobile, the "only while Tether is on screen" limitation is part of the accessible description so it is not a purely visual caveat.
- Shape contrast (button vs switch) is reinforced by role in the semantics, so a screen-reader user distinguishes one-shot from standing without seeing the shapes.

---

### PeerCard — receive mode (per-peer)

**Purpose.** Let the user choose, for a specific trusted peer, what happens to clipboard content arriving *from that peer*: silently applied, or surfaced for review. Symmetric to the per-peer Clipboard sync send switch — each peer owns both its outbound switch and its inbound mode.

**Entry points.** The expanded PeerCard's Clipboard controls block, directly below the Clipboard sync switch (item 5 in the block list above). It governs only items from this one peer.

**Layout.** A labeled choice with two mutually-exclusive options (segmented control on Desktop/macOS, two radio rows on mobile — implementer's idiom call), grouped under the same "Clipboard" section label as the send controls:
- **Auto-apply** — clipboard content arriving from this peer silently replaces this device's clipboard.
- **Notify** — content arriving from this peer raises a notification and is held in this peer's Clipboard inbox section until you copy or dismiss it.

**Default.** **Notify** is the default for every peer — the receiver is never surprised by a silent clipboard replacement from a peer before opting in. Auto-apply is a deliberate per-peer switch the user flips when they trust a peer enough for the friction-free "just paste it" path. Notify mode itself softens that friction: see the auto-apply hint on this peer's inbox items (decided below), which flips exactly this control.

**States.**
- **Notify selected (default):** one-line description of where this peer's items go.
- **Auto-apply selected:** the choice carries a one-line caution beneath it (see Copy) so the privacy implication is visible at the point of choosing.

This is a per-peer receive setting, symmetric to that peer's send switch and orthogonal to it (see the direction decision above).

**Interactions.**
- Select Auto-apply / Notify for this peer: applies immediately, no confirm dialog. Switching to Auto-apply also dismisses this peer's auto-apply hint (the hint exists only to offer this very flip). Switching back to Notify resumes raising notifications and holding items for this peer.

**Copy.**
- Group label: "Incoming clipboard from \<peer\>"
- Option A: "Auto-apply"
- Option A caption: "Arriving clipboards replace yours automatically."
- Option B: "Notify"
- Option B caption: "Get a notification and keep the item until you copy or dismiss it."

**Per-platform deltas.**
- **Android / iOS / macOS / Desktop:** default — same control, same copy. Idiom for the two-way choice (segmented vs radio) is an implementer call.

**Accessibility.**
- Role "radio group" with two options scoped to this peer; selected state announced. The accessible label names the peer ("Incoming clipboard from \<peer\>"). Captions are part of each option's accessible description so the Auto-apply caution is not vision-only.

---

### PeerCard — Clipboard inbox section

**Purpose.** Within a specific peer's PeerCard, hold the clipboard items that arrived from *that peer* in Notify mode, so a missed or dismissed notification never loses them; let the user copy an item into their own clipboard or dismiss it, scoped to where the item came from. This is a **section of the expanded PeerCard**, not a separate screen or nav destination.

**Entry points.**
- Tapping the **[Copy]** action on the clipboard-received OS notification copies directly (no navigation needed — see the notification screen).
- Tapping the **body** of the clipboard-received OS notification opens the source peer's PeerCard, expanded, scrolled to its Clipboard inbox section.
- Expanding the source peer's PeerCard at any time from the device list — the section is there whenever that peer has pending items, so items are recoverable even if the notification was never seen. This is the notification-independent path, load-bearing on Linux where notification clicks are unreliable.
- An **unread-clipboard indicator** on the collapsed PeerCard (see below) signals which peer has pending items, so the user knows where to look without hunting.

**Layout.**
- The section sits below the Clipboard controls in the expanded card, under its own short label **"Clipboard inbox"**.
- A list of pending items from this peer, most-recent first. Because the section is already scoped to one peer, items do **not** repeat the peer name; they show a content preview (masked when sensitive — see Masked preview), an arrival time, and the affordances **[Copy]** and **[Dismiss]**.
- The most-recent item also carries a dismissible **auto-apply hint** (see state 4) when this peer is not yet set to auto-apply.

**Unread-clipboard indicator (collapsed PeerCard).**
- When a peer has one or more pending inbox items, its collapsed PeerCard shows a small unread marker (a count or dot) in the peer-identity accent, so the device list reveals at a glance which peer is holding clipboard items. The marker clears when the peer's last pending item is copied or dismissed. This reuses the device-list row contract; it adds a clipboard-pending state, it does not redefine the row.

**States.**

##### 1. No pending items (section absent)
- When the peer has no pending items, the Clipboard inbox section is simply **not rendered** in the expanded card — there is no standalone empty screen to land on, so a calm empty illustration would have nowhere to live. The expanded card just shows the Clipboard controls.

##### 2. Item — normal preview
- Preview shows the text (single line, truncated) or, for a typed URL, the link rendered as a link (recognised per spec). Tapping the preview does not navigate — only [Copy] / [Dismiss] act.

##### 3. Item — masked preview (sensitive content)
- When the platform flagged the arriving content as sensitive (passwords, one-time codes), the preview is **masked**: rendered as a dotted/bulleted placeholder (e.g. "••••••••") rather than clear text, both here and in the notification.
- The item is still fully copyable: **[Copy]** places the *real* content into the clipboard; the mask is a display-only protection, never a transformation of the payload.
- A small masked-content indicator (lock-style Tabler glyph + text) tells the user why they see dots: "Sensitive content" — the same phrasing the notification uses.

##### 4. Auto-apply hint (dismissible, on the most-recent item)
- A gentle, **non-nagging** suggestion attached beneath the most-recent inbox item, shown only while this peer's content still routes through Notify (i.e. this peer's receive mode is Notify): "Want \<peer\>'s clipboard to land automatically? Turn on auto-apply." with a quiet **[Turn on auto-apply]** affordance and a **[×]** dismiss.
- It is the soft, opt-in nudge decided for Notify mode: it informs the receiver they can skip the extra Copy step for future items, without pressure.
- **No dark patterns.** The hint is unobtrusive (a quiet caption, not a banner or modal), never blocks Copy/Dismiss, and is **dismissible**. Once dismissed it does not reappear for this peer (a per-peer "don't show again"), so it never becomes a recurring nag. Accepting it sets *this peer's* receive mode to Auto-apply (the per-peer control above) — only items from this one peer land automatically thereafter; every other peer is untouched. The hint states this plainly so the choice is honest.

**Interactions.**
- Tap **[Copy]** on an item: real content (clear, even if the preview was masked) goes into this device's clipboard; the item **leaves the section**. Brief toast: "Copied to clipboard". If it was this peer's last item, the section disappears and the collapsed-card unread marker clears.
- Tap **[Dismiss]** on an item: item leaves the section; nothing is copied. No undo (ephemeral by design; the user can ask the sender to resend).
- Tap **[Turn on auto-apply]** on the hint: switches *this peer's* receive mode to Auto-apply and dismisses the hint. Future items *from this peer* land silently; other peers are unaffected.
- Tap **[×]** on the hint: dismisses the hint for this peer permanently; items from this peer still arrive in Notify mode as before.
- An item is never auto-applied from the section and never silently dropped while pending (spec: a missed/dismissed notification must not lose it — it persists in the peer's section until Copy or Dismiss).

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
- **macOS:** The notification's Copy action copies in place; tapping the body activates Tether and expands the source peer's card at its inbox section. Right-click on an item offers Copy / Dismiss as a context menu in addition to the buttons.
- **Desktop (JVM):** Where the system-tray notification (Windows) supports inline actions, [Copy] copies in place; body click opens the source peer's expanded card. On Linux, notification action and click fidelity vary by DE (consistent with file-transfer's note); the guaranteed path is expanding the peer's card from the device list, surfaced by the unread-clipboard indicator — this is exactly why the in-card section plus the collapsed-card indicator are the dependable recovery surface.

**Accessibility.**
- The Clipboard inbox section: role "list" nested in the PeerCard; each item is a list item exposing two actions (Copy, Dismiss) as accessibility actions in addition to visible buttons.
- Masked item: the accessible label states "Sensitive content, hidden" — a screen reader must **not** read the masked content aloud (defeats the mask). [Copy] still places the real content into the clipboard; the screen reader announces the action, not the secret.
- Copy confirmation announced politely; Dismiss announced as the item being removed.
- Auto-apply hint: announced as a tip (polite, not assertive), with its [Turn on auto-apply] and dismiss actions exposed; it must not steal focus from the Copy/Dismiss actions of the item it sits under.
- Unread-clipboard indicator: the collapsed PeerCard's accessible label includes the pending count for the peer ("\<peer\>, 2 clipboard items waiting") so it is not a vision-only cue.
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
2. User expands the target peer's PeerCard and taps **Send clipboard** (peer is online & paired).
3. Tether reads the clipboard and sends it. A **"Clipboard sent"** toast appears and auto-dismisses. No preview, no receipt.
4. On the receiver: handled per the receiver's own clipboard receive mode — silently applied (Auto-apply) or surfaced as a notification + inbox item (Notify).
Terminal state: sender card back to Ready; nothing recorded in transfer history.

### Flow 2 — Clipboard sync, Desktop sender (primary)
1. User turns **Clipboard sync** On for a peer on a Desktop/macOS device.
2. Whenever the local clipboard changes, its content is auto-sent to that peer with no per-copy action (background watcher; OS permits).
3. Receiver handles each item per their own receive mode.
4. User turns the switch Off → nothing is sent after that.
Terminal state: switch persists Off; watcher stopped.

### Flow 3 — Clipboard sync attempted on mobile (asymmetry made legible)
1. User turns **Clipboard sync** On for a peer on Android/iOS.
2. An inline note appears beneath the switch: "Only sends while Tether is on screen." The `[i]` explains the OS background restriction.
3. While Tether is foregrounded, clipboard changes auto-send; backgrounded, they do not.
Terminal state: the user understands the limit and falls back to **Send clipboard** for reliable one-offs. The gap is explained, never silent.

### Flow 4 — Receiving in Notify mode (default receive mode)
1. An item arrives from a peer whose receive mode is Notify (the default for every peer).
2. A notification shows a preview (masked if sensitive) and carries a **[Copy]** action. The item also lands in the source peer's Clipboard inbox section, and the peer's collapsed card shows the unread-clipboard indicator.
3. Fast path: user taps the notification's **[Copy]** → real content goes onto their clipboard with no navigation; the item leaves the peer's inbox section.
4. Alternative path: user taps the notification body → the source peer's PeerCard opens expanded at its Clipboard inbox section; or the user expands that peer's card from the device list independently. There they tap **[Copy]** (toast "Copied to clipboard") or **[Dismiss]**. Either way the item leaves the section.
5. First time through (and until dismissed per peer), the most-recent inbox item shows a dismissible auto-apply hint inviting the user to let this peer's clipboard land automatically.
Terminal state: the peer's inbox section no longer holds that item; unread indicator clears when its last item is gone.

### Flow 5 — Receiving in Auto-apply mode
1. Item arrives from a peer whose receive mode is Auto-apply.
2. It silently replaces the receiver's clipboard. No notification, no inbox entry.
3. User pastes wherever they were working, without opening Tether.
Terminal state: receiver's clipboard holds the item.

### Flow 6 — Empty clipboard on manual send (failure, non-blocking)
1. User taps **Send clipboard** with nothing sendable on the clipboard.
2. No send, no toast. Inline "Nothing to send" beneath the button.
3. User copies something, taps again → Flow 1.
Terminal state: nothing sent; user told why.

### Flow 7 — Unsupported content on manual send (failure, non-blocking)
1. User taps **Send clipboard** with an image / file reference on the clipboard.
2. No send. Inline "Can't send this yet — only text and links".
Terminal state: nothing sent; user told why; can recover by copying supported content.

### Flow 8 — Sensitive content end to end
1. Sender copies a one-time code / password the OS flags as sensitive; sends (manual or sync).
2. Content is sent (trusted-peer gate is the safety boundary — only paired devices are ever targets/sources).
3. Receiver in Notify mode sees a **masked** preview in both the notification and the peer's inbox-section item. [Copy] — whether the notification's Copy action or the in-card [Copy] — places the real value; the dots are display-only.
Terminal state: sensitive value reaches the trusted peer, never shown in clear in any preview.

### Flow 9 — Notify item missed / dismissed-by-mistake (recovery)
1. Notify-mode item arrives; user misses or swipes away the notification.
2. The item is still in the source peer's Clipboard inbox section (notification dismissal does not drop it); the peer's collapsed card carries the unread-clipboard indicator.
3. User spots the unread indicator on the device list, expands that peer's PeerCard, and copies the item from its Clipboard inbox section.
Terminal state: item recovered and copied, or explicitly dismissed.

### Flow 10 — Peer unreachable on manual send (failure)
1. Peer drops offline between card-expand and the Send clipboard tap.
2. Inline "Can't reach \<peer\>." No send, no retry button.
3. User re-taps when the peer is back online.
Terminal state: nothing sent; consistent with fire-and-forget (no receipt to chase).

### Flow 11 — Accepting the auto-apply hint (Notify → Auto-apply, this peer only)
1. In this peer's Notify mode, the user reads the dismissible auto-apply hint under that peer's most-recent inbox item.
2. User taps **[Turn on auto-apply]** → *this peer's* receive mode switches to Auto-apply (the per-peer control in the same card), the hint clears.
3. Future items *from this peer* land silently per Flow 5; every other peer keeps its own mode.
Terminal state: this peer's receive mode is Auto-apply; no further extra Copy step for it. The user reached it by choice, never by nag.

---

## Navigation

No new top-level navigation and no new screen. The clipboard controls (Send clipboard, Clipboard sync, per-peer receive mode) **and the Clipboard inbox section** all live inside the already-expandable PeerCard (in-place, no push, no modal) — both the receive-mode choice and the received items are scoped per peer within that peer's card, never a separate destination. The clipboard-received notification's **[Copy]** action acts without navigating; its **body tap** routes to the source peer's PeerCard expanded at its Clipboard inbox section (on mobile, Tether routes to that peer on launch). The collapsed-card unread-clipboard indicator points the user to the peer holding items, the notification-independent recovery path. Toasts, inline errors, and the auto-apply hint are transient/in-place and add nothing to the back stack.

---

## Platform notes

### Android
- No background clipboard read; Clipboard sync auto-sends only while foregrounded. Send clipboard is the reliable mobile path. Asymmetry surfaced inline on the switch.

### iOS
- Same background limitation as Android. The OS may show its own paste-permission banner on programmatic clipboard read; that is an OS surface — do not design around it, but reading on an explicit user tap (Send clipboard) is the friendliest trigger.

### macOS
- Real background clipboard watcher for Clipboard sync; OS imposes no restriction. Notification tap activates Tether → inbox.

### Desktop JVM (Windows, Linux)
- Real background clipboard watcher. Notification click-through and inline-action fidelity are reliable on Windows, best-effort on Linux (DE-dependent, mirrors file-transfer's notification fidelity note). The source peer's in-card Clipboard inbox section, surfaced by the collapsed-card unread-clipboard indicator, is the guaranteed recovery path where notification Copy / click is unreliable.

---

## Conceptual components

1. **Clipboard controls block** — the per-peer cluster (section label "Clipboard", Send clipboard action, Clipboard sync switch + `[i]`) appended below the Auto-send (file) toggle inside the expanded PeerCard. Extends PeerCard (Idle expanded), baseline owned by [file-transfer/ux-brief.md](../file-transfer/ux-brief.md).
2. **Send clipboard action** — a one-shot action button with Ready / Sent-toast / empty-inline / unsupported-inline / unreachable-inline states. Shape (button) and label distinguish it from the standing switch beside it.
3. **Fire-and-forget toast** — transient, politely-announced "Clipboard sent" / "Copied to clipboard" confirmation. No progress, no receipt. Distinct from file-transfer's persistent PeerCard terminal states.
4. **Inline control error** — a non-blocking message anchored to the control that was acted on (empty / unsupported / unreachable). Never a toast; announced as an alert. Conceptually akin to device-list's offline-row inline hint (anchored, non-modal, non-auto-dismissing-as-failure).
5. **Clipboard sync switch** — a persisted per-peer On/Off send-only switch, with a platform-conditional inline caveat on mobile.
6. **Clipboard receive-mode choice** — a per-peer two-option control (Auto-apply / Notify) inside the expanded PeerCard's Clipboard controls block, defaulting to Notify for every peer; symmetric to that peer's Clipboard sync send switch.
7. **Clipboard-received notification** — an OS notification carrying source peer + preview (masked-where-sensitive), a direct **Copy** action that places the real content on the clipboard, and a body tap that opens the source peer's PeerCard.
8. **Clipboard inbox section** — a per-peer list of that peer's pending received items inside the expanded PeerCard, with per-item Copy / Dismiss, masked-preview item variant, and the auto-apply hint; absent when the peer has no pending items. Not a standalone screen.
9. **Unread-clipboard indicator** — a per-peer marker on the collapsed PeerCard (count/dot in the peer-identity accent) signalling pending inbox items; clears when the peer's last item is gone. Extends the device-list row's state set, does not redefine the row.
10. **Auto-apply hint** — a dismissible, non-nagging caption under a peer's most-recent inbox item, inviting a switch to Auto-apply; "don't show again" per peer, no dark patterns.
11. **Masked preview** — a display-only dotted rendering of sensitive content used in both the notification and the inbox-section item; the real payload is still copied verbatim by any Copy affordance, and assistive tech must not read the secret aloud.
12. **Peer-identity accent (reused)** — the existing `peerIdentity` marker, reused on the unread-clipboard indicator and the PeerCard to identify the source peer; baseline owned by [device-list/ux-brief.md](../device-list/ux-brief.md), not redefined here.

---

## Open UX questions

_None outstanding._ The three prior open questions are decided:

- **Receive mode scope and default — decided: per-peer, default Notify.** Receive mode is set independently for each peer (symmetric to that peer's Clipboard sync send switch), and every peer defaults to Notify; no silent clipboard replacement from a peer before the user opts in for it. The extra-step friction is softened by the dismissible auto-apply hint on that peer's inbox items, which lets the receiver promote that one peer to auto-apply without pressure. See [PeerCard — receive mode (per-peer)](#peercard--receive-mode-per-peer) and the auto-apply hint state.
- **Clipboard inbox placement — decided: inside the PeerCard, per peer.** Received items surface as a Clipboard inbox section of the source peer's expanded PeerCard, not a separate top-level destination or screen. The notification-independent recovery path is the collapsed-card unread-clipboard indicator plus expanding that peer's card. See [PeerCard — Clipboard inbox section](#peercard--clipboard-inbox-section).
- **Notification Copy action — decided: yes, carried directly.** The clipboard-received notification has a **Copy** action that places the real content on the clipboard (masked previews still copy the real value), alongside a body tap that opens the source peer's card. See [Clipboard-received notification](#clipboard-received-notification-notify-mode).

---

## Implementer layout calls

- **Echo suppression for Clipboard sync (send-only model).** An item that arrived via clipboard transfer and was auto-applied (Auto-apply mode) must not itself re-trigger an outbound auto-send back toward the sender. The suppression mechanism (e.g. tagging auto-applied content, or distinguishing local-user copy events from programmatic clipboard writes) is the implementer's; the user-visible requirement is simply "no echo loop". This is the behavioural backbone of the send-only decision.
- **Unread-clipboard indicator shape.** The collapsed PeerCard's pending marker (count vs dot, exact placement within the row) is the implementer's idiom call, as long as it reads in the peer-identity accent, clears when the peer's last item is gone, and its accessible label states the pending count. It is the notification-independent recovery cue — load-bearing on Linux where notification clicks are unreliable.
- **Auto-apply hint placement within the inbox section.** The hint attaches to the most-recent item by default; exact rendering (inline caption vs trailing affordance) is the implementer's call, provided it stays quiet, never blocks Copy/Dismiss, is dismissible per peer, and shows only in Notify mode. No dark patterns.
- **Receive-mode control shape.** Segmented control on Desktop/macOS, two radio rows on mobile, by default; per peer, defaults to Notify. Either idiom is acceptable as long as the two captions (especially the Auto-apply caution) are visible at choice time and the control sits within the peer's Clipboard controls block.
- **Send-clipboard vs Auto-send visual separation.** The "Clipboard" section label plus the button-vs-switch shape contrast is the required disambiguation. If a divider/label proves too heavy in the expanded card, fall back to clear grouping spacing — but the user must never confuse the file Auto-send toggle with the Clipboard sync switch.
