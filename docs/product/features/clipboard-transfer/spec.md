# Clipboard transfer — send what you copied to a trusted device

**Area:** Transfer / UI
**Status:** `scoped`
**GitHub Issues:** [#419](https://github.com/khmelevartem/tether/issues/419) (spec & UX brief); implementation _tbd_

---

## Why

Copying a link, a code snippet, an address, or a one-time code on one device and needing it on another is a constant small friction Tether's users already feel. Today the only way across that gap is to message it to yourself, email it, or read it off one screen and retype it on the other — exactly the lossy, account-bound detours [vision.md](../../vision.md) exists to remove. The user already has two devices that recognise each other through [pairing](../pairing/spec.md); the clipboard is just another thing worth moving directly between them.

[File transfer](../file-transfer/spec.md) moves files the user explicitly picks. The clipboard is different: it is small, it is what the user just copied, and the natural intent is often "send it now without ceremony" or even "keep it in sync while I work across both screens". This feature gives that its own surface so the user does not have to save a snippet to a file just to move it.

## What it does

The user sends the current contents of their clipboard to a trusted peer, and the peer receives it ready to paste. Trust is the existing pairing relationship — only a [trusted device](../pairing/spec.md) can be a target or a source, and this feature never changes how pairing works.

There are two ways to send:

- **Send clipboard (manual).** A one-shot action targeting a chosen trusted peer: the user copies something, picks the peer, and sends. It is fire-and-forget — a brief "Clipboard sent" confirmation, no preview step, no delivery receipt.
- **Clipboard sync (per-peer).** A switch the user turns on for a specific trusted peer expressing the intent "automatically send my clipboard to this peer as it changes", so copying on one device makes the content available on the other without a manual step each time.

Clipboard sync is send-only per side. Turning it on for a peer means "automatically send my clipboard to this peer"; it never pulls the peer's clipboard back. What happens to arriving content is governed entirely by the receiver's own per-peer receive mode. A two-way mirror exists only when both devices independently turn on sync toward each other — each direction is a separate switch, owned by the side that sends. This keeps the switch's mental model honest (its label is send-only), keeps send and the receive mode cleanly orthogonal, and protects privacy: a device never reads or overwrites another's clipboard on the strength of a switch flipped elsewhere. Sync fires on the local user's own copy events only; content that arrives and is auto-applied never re-triggers an outbound send, so two mirrored sides do not form an echo loop.

The receiver controls what happens to arriving clipboard content, independently and symmetrically to the sender:

- **Auto-apply.** Arriving content silently replaces the receiver's own clipboard, ready to paste immediately.
- **Notify.** The receiver gets a notification with a preview, and the item is held in a pending clipboard inbox so a missed or dismissed notification does not lose it. The receiver copies it into their own clipboard when they want it, or dismisses it. The inbox holds at most 5 pending items per peer: a pending item is never auto-applied and is never dropped while it stays within that 5-item window; when a new item arrives with 5 already pending, the oldest pending item is displaced to make room — the only way an item leaves the inbox other than being copied or dismissed. This cap keeps the feature ephemeral and free of accumulated history.

Clipboard transfers are ephemeral. They are never recorded in transfer history (a future feature, not yet built — see [roadmap.md](../../roadmap.md)), and the receiver's inbox holds an item only until it is copied or dismissed.

The supported payload is text, with typed URLs recognised and treated as links rather than plain text. Richer payloads — images, rich text, and files placed on the clipboard — are deliberately outside this feature; see [Not in this feature](#not-in-this-feature).

## User flows

**Primary — manual send to a trusted peer**

1. User copies text (or a URL) on their device.
2. User invokes Send clipboard and picks a trusted peer (or the action is offered directly on a peer).
3. Tether sends the current clipboard content and shows a brief "Clipboard sent" confirmation.
4. On the receiver, the content is handled per the receiver's own setting — silently applied, or surfaced as a notification plus a pending inbox item.

**Primary — clipboard sync**

1. User turns on Clipboard sync for a specific trusted peer.
2. From then on, when the user's clipboard changes, its content is sent to that peer automatically, with no per-copy action.
3. The receiver handles each arriving item per their own auto-apply / notify setting.
4. User turns the switch off to stop; nothing is sent after that.

**Receiving — notify mode**

1. An item arrives while the receiver is in Notify mode.
2. A notification shows a preview of the content (masked when the content is sensitive — see below).
3. The item also appears in the pending clipboard inbox.
4. User copies it into their clipboard, or dismisses it. Either way the item then leaves the inbox.

**Alternative paths and failure cases**

- **Empty clipboard on manual send.** The Send clipboard action stays available and shows an inline "Nothing to send" — the user is not blocked, just told why nothing happened.
- **Unsupported content on manual send.** When the clipboard holds something outside the supported payload — anything that is not text or a typed URL — the action shows an inline "Unsupported content" rather than sending an empty or garbled item.
- **Sensitive content (passwords, one-time codes).** When the platform flags clipboard content as sensitive, it is still sent, but every preview of it — in the notification and in the inbox — is masked (dotted out) rather than shown in clear. The safety of sending such content rests on the trusted-peer gate: only a paired device can ever be a target.
- **Receiver inbox not emptied.** If the receiver neither copies nor dismisses a notify-mode item, it stays in the pending inbox; it is never auto-applied. It leaves only when copied, dismissed, or displaced as the oldest item once a sixth item arrives against the 5-item per-peer cap.

## What "working" looks like

- The user copies a link on one device, sends clipboard to a trusted peer, and can paste that exact link on the other device moments later.
- With Clipboard sync on for a peer, copying on one device makes the content available on the other with no further action.
- A manual send is fire-and-forget: one brief confirmation, no preview prompt, no delivery receipt to acknowledge.
- The receiver, in Notify mode, can recover an item from the pending inbox even after dismissing or missing its notification.
- The receiver, in Auto-apply mode, can paste the arrived content immediately without touching Tether.
- Sending with an empty clipboard, or with unsupported content, shows an inline reason and never sends a broken item.
- Sensitive content reaches the trusted peer but is never shown in clear in any preview.
- No clipboard transfer is recorded anywhere as history, and a copied-or-dismissed inbox item is gone afterwards.
- Only trusted (paired) devices appear as send targets or are accepted as sources.

## Platform notes

- **Desktop (Windows, Linux, macOS).** Clipboard sync can watch the clipboard while Tether runs, so turning the switch on for a peer sends new clipboard content as it changes without further action. The OS imposes no background restriction on reading the clipboard here.
- **Mobile (Android, iOS).** The OS blocks reading the clipboard while the app is in the background. Clipboard sync's automatic background sending therefore does not run on mobile; on mobile the feature is manual Send clipboard only, delivered the same way a file send is. Receiving (auto-apply or notify) works on mobile as on desktop.

## Not in this feature

- **Changes to pairing or trust.** This feature consumes the existing [pairing](../pairing/spec.md) relationship and never modifies how devices become trusted.
- **Image, rich-text, and files-on-clipboard payloads.** Deliberately not built. A clipboard item here is small and ephemeral — sent and pasted without progress, cancel, or retry. A non-text payload breaks that invariant: it is large enough to need the file-transfer transport machinery (progress, cancellation, resumable delivery) this feature is shaped to avoid. For a file placed on the clipboard the boundary with [file transfer](../file-transfer/spec.md) dissolves entirely — a clipboard file is just transferring a file. Any rich-payload tier therefore belongs to transport-reliability work ([#119](https://github.com/khmelevartem/tether/issues/119)), not inside this lightweight clipboard feature.
- **Background automatic clipboard sync on mobile.** Parked as a future hypothesis contingent on a properly designed mobile UX that works within the OS clipboard-read restriction; manual send is the mobile path until such a design exists.
- **Recording clipboard transfers in transfer history.** Clipboard transfers are ephemeral by design and are not logged. Transfer history is itself a future feature, not yet built (see [roadmap.md](../../roadmap.md)).
- **A persistent clipboard archive on the receiver.** The pending inbox holds an item only until it is copied or dismissed; it is not a searchable history.

## Open product questions

- **Manual-vs-auto split.** Whether manual Send clipboard and Clipboard sync are presented and built as one capability with a switch, or as two distinct surfaces, is deferred to the implementing design.
- **Monetization (hypothesis only, nothing committed).** Mirrors how [file transfer](../file-transfer/spec.md) parks fan-out and how [monetization.md](../../monetization.md) frames Pro candidates. Free core: manual, single-peer clipboard send of text and URLs. Pro candidates to explore: the Clipboard sync switch, and multi-peer clipboard (sync on for several peers, or manual fan-out to many peers) — the same workflow-automation and multi-peer framing as folder sync and multi-peer send in [monetization.md](../../monetization.md). The call is not made; recorded so the option stays open.
