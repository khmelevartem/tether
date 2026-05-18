# [Subsystem name]

> Starter skeleton for engineering living docs. Writing-style rules live in [README.md → Writing style for these guides](README.md). Strip these template comments before merging. Drop sections that do not apply rather than filling them with placeholder text.

One paragraph: what this subsystem is, where it sits in the architecture, link to the ADR(s) that establish *why*. If no ADR exists yet — one sentence on motivation.

A living doc states what should be true of any correct implementation. Where the codebase has not caught up, the gap is an implementation issue, not a contradiction.

## Goal

What this subsystem accomplishes, stated so that someone outside the team can verify it. Two short angles when both apply: what a user observes, what the system guarantees internally.

## [Main shape]

The organising principle of this subsystem — pick a heading that names *what kind* of structure it is. Describe the elements, how they relate, where the seams are. An ASCII diagram is fair game if it removes more confusion than it adds.

Stay at the level of rules and shapes. Concrete identifiers and runtime values belong in code, not here.

## Contracts

External surfaces this subsystem exposes — APIs, message formats, file formats, anything another part of the system or another system depends on. Enough detail to implement the other side. Mark anything still *interim* and link the issue that finalises it.

Skip the section if there is no external contract.

## Cross-cutting concerns

Aspects that span the subsystem and tend to be missed in review. Address only those that are non-trivial here; skip the rest rather than writing filler.

Aspects worth considering:

- **Identity** — does the subsystem need a stable identifier? What becomes that identifier, when does it stabilise, what does it look like before then?
- **Lifecycle** — startup contract, shutdown contract, recovery from transient failures, what owns the coroutine scope or its equivalent.
- **Aging** — what state expires, what extends its life, what triggers cleanup.
- **Authority and trust** — what this subsystem assumes about others, what it enforces itself, where the canonical trust model lives.
- **Mediated resources** — anything gated by the OS, the runtime, or a framework that is easy to forget in review. Delegate the canonical inventory to the doc that owns it; here, only highlight what catches reviewers off guard.
- **Placement** — where the code lives across source sets. Only call out deviations from the project default, with reason.
- **Observability** — what is logged, what is measured, what surfaces to the user on failure.

## What this doc does *not* commit to

Drift protection. List the short-lived choices the doc explicitly does *not* fix — runtime constants, specific identifiers, concrete library versions, interop commitments not formally agreed. Anything whose specific value migrates with the code rather than with the rules.

The point: a living doc states rules. Specific numbers are not rules — they live in code.
