# UX brief — Device name bootstrapping

**Spec:** [spec.md](spec.md)
**Status:** `ready`

---

## Information architecture

This feature adds one surface — the **This-device strip** — to the top of the existing peer list screen, owned by [device list](../device-list/ux-brief.md). It introduces no screen, dialog, or navigation. The strip is always present and toggles between a display mode and an edit mode in place.

Screens introduced: none.
Screens touched: peer list screen (owned by [device list](../device-list/ux-brief.md)) — this brief contributes the This-device strip above the list.

---

## Screens

### Peer list screen — This-device strip

**Purpose.** Tell the user what their own device is currently called, and let them rename it without leaving the list.

**Entry points.** Present whenever the peer list screen is shown; no separate entry.

**Layout.** A single horizontal strip pinned above the peer list, visually subordinate to a list row (a label, not a tappable peer). Left: a fixed "This device" label and the current name. Right: the edit affordance. In edit mode the name region is replaced in place by an editable field plus confirm and cancel controls; the strip keeps its position and the list below does not move.

**Decisions resolved (the spec's two open product questions).**
- *Surface form:* a persistent strip above the list, chosen over a banner or card so it reads as "this is you" without competing with peer rows or implying a dismissible notice.
- *Affordance form:* an explicit pencil-glyph button that expands the name into an inline field, chosen over a dotted-underline or a separate dialog — the glyph is self-evidently discoverable and inline editing matches the spec's "edit it inline" intent without a modal hop.

**States.**

#### 1. Display
Shows "This device: <name>" with the edit affordance. Default resting state.

#### 2. Editing
Entered by activating the edit affordance. The name region becomes an editable field pre-filled with the current name and selected for immediate overtype. Confirm and cancel controls are shown. Confirm is unavailable while the input is invalid (empty after trim, or over 50 codepoints). The field shows a live count cue only as it nears the limit; it does not block typing past the limit but flags it as invalid.

#### 3. Editing — invalid input
A subordinate inline error sits with the field; confirm stays unavailable. Triggered by empty/whitespace-only input or input over 50 codepoints. Leaving the field invalid and cancelling restores the previous name unchanged.

#### 4. Editing — save failed
After a confirm whose persist fails, the surface stays in edit mode with the typed text intact and shows a subordinate inline error. The previously displayed name is unchanged and still announced. The user can correct or retry by confirming again, or cancel to discard.

**Interactions.**
- Activate edit affordance → enter Editing; field pre-filled and selected, focus and (on touch) the keyboard raised.
- Confirm (visible control, or the platform keyboard's done/return action) → trims and validates; on success persists, returns to Display with the new name; on validation failure stays in Editing with the inline error; on persist failure → Save-failed state.
- Cancel (visible control, or platform back/escape) → discard edits, return to Display with the unchanged name.

**Copy.**
- Label: `This device`
- Display value: the current name (no decoration).
- Edit affordance accessible label: `Rename this device`
- Field placeholder (only if the current name were ever empty, which it is not by spec): `Device name`
- Invalid — empty: `Enter a name.`
- Invalid — too long: `Use 50 characters or fewer.`
- Save failed: `Couldn't save the name. Try again.`
- Confirm control accessible label: `Save name`
- Cancel control accessible label: `Cancel rename`

**Per-platform deltas.** Default (one shared Compose surface). Confirm and cancel are available both as visible controls and via the platform's native text-entry conventions — keyboard done/return confirms, back/escape cancels — so each platform's habitual gesture works without a per-platform layout change.

**Accessibility.**
- The strip is not a list item or button in Display mode; it is a static labelled value with one nested button (the edit affordance). The affordance carries the label `Rename this device`, not a bare icon.
- Entering Editing moves focus to the field and announces it as an editable name field with the current value.
- Inline errors are announced via a live region when they appear, so the reason a confirm did nothing is spoken, not silent.
- Confirm and cancel are labelled controls reachable by keyboard; on Desktop, focus order runs field → confirm → cancel, and escape cancels.

---

## Flows

### Flow 1 — first launch (no rename)
1. App opens to the peer list.
2. The This-device strip shows "This device: <OS-derived default>".
3. User proceeds to discover and send. Nothing blocks them.

### Flow 2 — rename (success)
1. User activates the edit affordance → field appears, pre-filled and selected.
2. User overtypes a new valid name and confirms.
3. Strip returns to Display showing the new name.
4. The peer list below may briefly clear and repopulate as discovery republishes (see note). This is expected, not an error.

### Flow 3 — rename rejected (invalid)
1. User edits the name to empty/whitespace-only or over 50 codepoints.
2. Confirm is unavailable and the matching inline error shows.
3. User corrects and confirms, or cancels — either way the previous name stays in effect.

### Flow 4 — rename fails to save
1. User confirms a valid name; persistence fails.
2. Surface stays in Editing with the typed text and the save-failed error.
3. User confirms again to retry, or cancels to discard. The previous name stays in effect and is still announced.

---

## Note — post-confirm list flicker

A successful rename tears down and re-establishes the local discovery session, so the peer list below the strip may briefly clear and repopulate within a second or two of confirm. Treat this as the expected visual signature of a successful rename, not a fault: do not surface an error, spinner-blocking overlay, or empty-state warning for it. The strip itself stays in Display with the new name throughout.

---

## Conceptual components

1. **This-device strip** — persistent labelled value above the peer list with display/edit modes; the surface this feature owns.
2. **Inline name field** — single-line editable text field with trim-and-length validation surfaced as a subordinate inline error and a confirm-availability gate. No design-system text-input primitive exists yet; its behaviour and appearance are specified here as the contract for that primitive.
3. **Subordinate inline error** — a short, low-emphasis message bound to the field, announced to screen readers when it appears.

---

## Open UX questions

- The spec's republish-failure state (spec line 52) is out of scope for this issue because the backend republishes fire-and-forget with no failure signal to observe. The brief therefore does not design a republish-retry affordance. If a future change exposes a republish-failure signal to the UI, this surface will need a state for it — flagged here so it is not silently lost.
