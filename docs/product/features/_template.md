# [Feature Name]

**Area:** <!-- e.g. Transfer, Discovery, UI, Pairing, Settings -->
**Status:** `idea` | `scoped` | `in progress` | `done` | `on hold`
**GitHub Issues:** <!-- #42, #43 — filled in when issues are created -->

---

> A feature spec describes **what the user gets and why**, not how it is built.
>
> **Code is not mentioned in the spec, in any form** — no class / interface /
> function names, API signatures, module / source-set / gradle names, file
> paths, library or storage-backend choice. All of that lives in the
> implementation GitHub Issue. If meaning would be lost without naming code,
> rephrase as a product invariant ("pairing keyed by stable identity") rather
> than an implementation description.
>
> **One spec covers all platforms and all implementation milestones of the feature.**
> Don't write a separate "Android X" and "iOS X" spec — write one and put per-platform
> user-visible quirks in `Platform notes`. Don't write a separate "X infrastructure" and
> "X UI" spec — write one. See [README.md](README.md) → «What counts as one feature».

## Why

<!-- The user pain or need this addresses. Anchor it in vision.md / audience.md
     where useful. What can't the user do today, and why does that matter? -->

## What it does

<!-- Describe the feature the way you would explain it to a non-technical user
     of Tether. Behaviour and outcomes, not code. No module names, no APIs. -->

## User flows

<!-- The primary flow as a sequence of user-visible steps and states.
     Then the most important alternative paths and failure cases.
     Optional: a small diagram or bulleted state list if it clarifies. -->

**Primary flow**

1. ...
2. ...

**Alternative paths**

- ...

## What "working" looks like

<!-- Observable, user-visible signs that the feature does its job —
     the kind of thing a non-technical reviewer could verify by using the app.
     Not gradle commands, not unit tests, not green CI. -->

- ...
- ...

## Platform notes

<!-- Optional. Capture user-visible quirks per platform — and only those.
     Examples: an OS permission prompt that appears on iOS but not Android;
     a system picker shape that differs between platforms; a behaviour that
     can't be replicated on one target.
     Implementation differences (NSD vs JmDNS, view models, build tasks)
     do NOT belong here — those live in the issue. If there are no
     user-visible platform differences, remove this section entirely.

     For features with strong platform asymmetry, per-platform top-level
     sections (Android / iOS / macOS / Desktop) may replace this single
     `## Platform notes` block. -->

-

## Not in this feature

<!-- Explicit boundary. Adjacent things that belong to other features
     or to a later iteration. Helps prevent scope creep. -->

-
-

## Open product questions

<!-- Unresolved decisions about user-facing behaviour.
     Implementation choices (which library, which storage backend) live in the issue. -->

-
