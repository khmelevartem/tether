# [Subsystem name]

> Starter template for engineering living docs. Writing-style rules live in [README.md → Writing style for these guides](README.md). Strip the template comments before merging.

One-sentence summary: what subsystem, where it lives in the system. Link to the ADR(s) that establish *why*, if any.

This is a living doc — it states what should be true of any correct implementation. Where the codebase has not yet caught up, treat the gap as an implementation issue, not a contradiction.

## Goal

Two short paragraphs — one user-facing, one engineering-facing. Why this subsystem exists; what success looks like in observable terms.

## [Main shape]

The structural heart of the doc. Pick the section name that fits the subsystem:

- **Layered model** — for stacked architectures (discovery, transport, request pipeline).
- **Module structure** — for cross-cutting concerns split by responsibility.
- **State machine** — for protocol / lifecycle-driven subsystems (pairing, transfer).
- **Routing / dispatch** — for systems whose core is "given X, do Y".

Optionally an ASCII diagram (≤ 20 lines). Then a paragraph per element. Keep operational specifics (concrete class names, version numbers, exact ports) out — those belong in code.

## Contracts

API surfaces, DTOs, message formats, file formats that span the subsystem and are stable. Sufficient detail for someone implementing the other side. Mark `interim` what is placeholder pending another issue.

If the subsystem has no external contract (pure internal mechanism) — skip this section.

## Cross-cutting concerns

Sections that apply to most subsystems. Include only what is non-trivial:

- **Identity / self-suppression** — how the subsystem distinguishes own messages from peer messages; what becomes the stable identity and when.
- **Lifecycle** — startup, shutdown, recovery from transient failures.
- **Liveness / TTL** — how state ages out.
- **Permissions and runtime locks** — delegate to [`features/system/permissions/spec.md`](../product/features/system/permissions/spec.md) for the canonical list; here only call out the locks/entitlements that are easy to forget in code review.
- **Trust** — what assumptions the subsystem makes vs. what other subsystems enforce. Link to [`security.md`](../product/security.md) for the trust model.
- **Source-set placement** — where the code lives ([commonMain by default per architecture-principles.md](architecture-principles.md)); only call out non-default placements with reason.

## What this doc does *not* commit to

Important. Lists the **runtime constants and short-lived choices** the doc explicitly does *not* fix:

- Timing constants (intervals, timeouts, fallback windows) — implementation choice, lives in code.
- Exact ports / file paths — same.
- Concrete library versions or framework choices — those belong in ADRs and `tech-stack.md`.
- Wire-protocol-level interop with external projects unless explicitly committed.

The point of this section: protect the doc from drift. A living doc states *rules*; specific numbers are not rules.
