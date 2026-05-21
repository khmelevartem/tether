# UX brief — [Feature Name]

**Spec:** [spec.md](spec.md)
**Status:** `draft` | `ready`

---

> A UX brief describes **what the user sees, hears, taps, and how the surface behaves across platforms** — concrete enough that a UI engineer can implement without product decisions left open.
>
> **Code is not mentioned in the brief.** No class / composable / view-model / module / file-path names. Concepts only (PeerCard, banner, sheet). Implementation names live in the GitHub issue.
>
> **One brief per feature, all platforms.** Don't write a separate "Android UX" and "iOS UX". Write one brief; capture per-platform user-visible deltas inline (per-screen `Per-platform deltas` blocks) or in a dedicated `Platform notes` section when the delta is cross-screen (sleep / wake-lock, background constraints).
>
> **Scope cohesion.** Every section must depend on the feature's central UX invariant. For each section ask: "if I remove the central invariant, does this section still belong here?". If no — move it to the owning brief and leave a cross-reference. Components owned by another feature (e.g. the baseline PeerCard) are referenced, not redefined; this brief only documents the extensions it contributes.
>
> **Cross-ref on move / extension.** Whenever this brief extends or touches a surface owned by another brief, name the owner with a link. When content moves out, add a bullet in `Open UX questions` or `Conceptual components` pointing to the new owner.
>
> **Status discipline.** Use `draft` while the brief is incomplete or under iteration; flip to `ready` only after all open UX questions either have answers or are tracked in `Open UX questions`. The `ready` brief is what UI engineers consume.

## Information architecture

<!-- A short paragraph: what screens / dialogs / sheets / settings sections does
     this feature introduce or touch? Optionally an ASCII diagram showing the
     navigation tree and key state transitions between screens.

     End with two explicit lists:
       - Screens introduced: ...
       - Screens touched:    ... (with link to the owning brief)

     Keep the diagram readable — node labels match the screen names used in
     the `Screens` section below. Branches show triggers (e.g. "[tap peer]")
     and outcomes. -->

```
RootScreen
├── ...
└── ...
```

Screens introduced: ...
Screens touched: ... (owned by [...](../<feature>/ux-brief.md))

---

## Screens

<!-- One subsection per screen / dialog / sheet / settings section.
     For surfaces owned by another brief, this section names ONLY what
     this feature contributes (banners, state overlays, new interactions). -->

### <ScreenName>

**Purpose.** <!-- One sentence: what this surface is for, from the user's POV. -->

**Entry points.** <!-- How the user arrives here — taps, share-sheet, deep link,
                       drag-and-drop, app launch. Cross-feature entry points
                       link to the contributing brief. -->

**Layout.** <!-- Top bar, content area, persistent banners, action positions.
                 Describe regions, not pixels. Note state-dependent layout
                 changes here or defer to States below. -->

**States.** <!-- Enumerate the discrete states the surface can be in.
                 For complex surfaces (e.g. a card that is itself a state
                 machine), use `#### <N>. <State name>` subsections with:
                 visual treatment, content, interactions allowed, transition
                 triggers in and out. -->

**Interactions.** <!-- Tap / long-press / right-click / drag / keyboard /
                       pull-to-refresh. What each does, where it leads. -->

**Copy.** <!-- Exact user-facing strings — banner text, button labels,
              empty-state messages, error messages, helper text. Variants
              (singular / plural / partial / complete) listed explicitly. -->

**Per-platform deltas.** <!-- Android / iOS / macOS / Desktop JVM — only
                              user-visible differences. OS share-sheet
                              presence, picker shape, context menu pattern,
                              background constraints. Omit when there are
                              no deltas. -->

**Accessibility.** <!-- Semantic roles (button vs list item vs alert),
                       screen-reader labels and announcements, focus order,
                       live-region behaviour, keyboard navigation,
                       non-text affordance fallbacks. -->

---

## Flows

<!-- Numbered, primary first, then alternative paths and failure cases.
     Each flow is a sequence of user-visible steps — what the user sees,
     what they tap, what happens next. End each flow with the terminal
     state. Failure flows name the recovery affordance (or its absence). -->

### Flow 1 — <name>

1. ...
2. ...

### Flow 2 — <name>

1. ...

---

## Navigation

<!-- How surfaces relate: push vs modal vs in-place expansion vs replacement.
     Back-stack behaviour. Deep-link entry routing. Where the feature lives
     in the app's overall nav structure. -->

---

## Platform notes

<!-- Optional. Cross-screen platform constraints that don't fit any single
     screen's `Per-platform deltas`. Examples: sleep / wake-lock behaviour
     during long operations, background execution limits, OS-level
     notification fidelity.

     If everything platform-specific fits inside per-screen deltas, remove
     this section. -->

### Android
- ...

### iOS
- ...

### macOS
- ...

### Desktop JVM
- ...

---

## Conceptual components

<!-- Reusable UX concepts introduced by this brief. For each: name, what it
     is, where its baseline is owned (if extended from another brief), how
     this brief extends it, and any persistence rules across states. These
     are concepts, not code — no class names. -->

1. **<Component>** — ...
2. **<Component>** — ...

---

## Open UX questions

<!-- Unresolved user-facing decisions. Each item: the question, the leading
     option (if any), what would unblock the decision (signal, prototype,
     research). Implementation choices (library, layout primitive) do NOT
     belong here — those live in the issue.

     If everything is resolved, remove this section. -->

- ...

---

## Implementer layout calls

<!-- Optional. Layout / placement decisions explicitly delegated to the
     implementer because they depend on rendering geometry, platform
     idiom, or component-library specifics. Each item names the fallback
     when the default placement is impractical.

     Remove this section if no calls are deferred. -->

- ...
