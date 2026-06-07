# UX brief — Device name bootstrapping

**Spec:** [spec.md](spec.md)
**Status:** `ready`

---

## Information architecture

This feature adds one surface — the **This-device strip** — to the top of the existing device list screen, owned by [device list](../device-list/ux-brief.md). It introduces no screen, dialog, or navigation. The strip is always present and toggles between a display mode and an edit mode in place.

Screens introduced: none.
Screens touched: device list screen (owned by [device list](../device-list/ux-brief.md)) — this brief contributes the This-device strip above the list.

---

## Screens

### Device list screen — This-device strip

**Purpose.** Tell the user what their own device is currently called, and let them rename it without leaving the list.

**Entry points.** Present whenever the device list screen is shown; no separate entry.

**Layout.** An elevated band pinned above the device list, with a leading vertical accent marker on its left edge (signalling "this device" / "me") and a divider separating it from the list below. The current name is the hero line — the prominent element — with a small "This device" caption beneath it. The edit affordance sits at the trailing edge. In edit mode the name line is replaced in place by an editable field; the "This device" caption stays beneath it; discard and confirm controls sit at the trailing edge (discard then confirm, with confirm emphasised as the primary action). The band keeps its position and the list below does not move.

**States.**

#### 1. Display
Shows the current name as the hero line with the "This device" caption beneath it and the edit affordance at the trailing edge. Default resting state.

#### 2. Editing
Entered by activating the edit affordance. The hero name line becomes an editable field pre-filled with the current name and selected for immediate overtype; the "This device" caption stays beneath it. Discard and confirm controls are shown at the trailing edge, confirm emphasised as primary. Confirm is unavailable while the input is invalid (empty after trim, or over 50 codepoints). Typing past the limit is not blocked but flags the input as invalid.

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
- The band is not a list item or button in Display mode; it is a static labelled value with one nested button (the edit affordance). The affordance carries the label `Rename this device`, not a bare icon.
- Entering Editing moves focus to the field and announces it as an editable name field with the current value.
- Inline errors are announced via a live region when they appear, so the reason a confirm did nothing is spoken, not silent.
- Confirm and cancel are labelled controls reachable by keyboard; on Desktop, focus order runs field → discard → confirm, and escape cancels.

---

## Flows

### Flow 1 — first launch (no rename)
1. App opens to the device list.
2. The This-device strip shows "This device: <OS-derived default>".
3. User proceeds to discover and send. Nothing blocks them.

### Flow 2 — rename (success)
1. User activates the edit affordance → field appears, pre-filled and selected.
2. User overtypes a new valid name and confirms.
3. Strip returns to Display showing the new name.
4. The device list below may briefly clear and repopulate as discovery republishes (see note). This is expected, not an error.

### Flow 3 — rename rejected (invalid)
1. User edits the name to empty/whitespace-only or over 50 codepoints.
2. Confirm is unavailable and the matching inline error shows.
3. User corrects and confirms, or cancels — either way the previous name stays in effect.

### Flow 4 — rename fails to save
1. User confirms a valid name; persistence fails.
2. Surface stays in Editing with the typed text and the save-failed error.
3. User confirms again to retry, or cancels to discard. The previous name stays in effect and is still announced.

---

## Navigation

This feature introduces no screen, modal, or navigation step. The This-device strip is an in-place surface on the existing device list screen and has no back-stack effect; entering and leaving edit mode is a local state toggle within that screen.

---

## Note — post-confirm list flicker

A successful rename causes peers in the list below to briefly disappear and reappear as the device is re-announced under its new name. Treat this as the expected visual signature of a successful rename, not a fault: do not surface an error, spinner-blocking overlay, or empty-state warning for it. The strip itself stays in Display with the new name throughout.

---

## Conceptual components

1. **This-device strip** — persistent elevated band above the device list with a leading accent marker and a divider below; hero name line over a "This device" caption, with display/edit modes; the surface this feature owns.
2. **Inline name field** — single-line editable text field with trim-and-length validation surfaced as a subordinate inline error and a confirm-availability gate.
3. **Subordinate inline error** — a short, low-emphasis message bound to the field, announced to screen readers when it appears.

---

## Open UX questions

- Republish-failure is out of scope: the backend republishes fire-and-forget with no failure signal to observe. If a future change exposes a republish-failure signal to the UI, this surface will need a state for it.
