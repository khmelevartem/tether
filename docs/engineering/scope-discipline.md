# Scope discipline — fold-vs-defer for findings

When a finding surfaces during a task — a bug, a rough edge, a cheap improvement — the question is whether to fix it in the current PR or defer it to a follow-up. This is the single criterion; the skills and review agents reference it rather than restating their own.

## Provenance is not a scope axis

Whether a finding pre-dates the task is irrelevant to whether it is fixed now. "It existed before" is never on its own a reason to skip — every finding, new or pre-existing, is evaluated against the same test below.

## Fix it in this PR when all three hold

1. **In reach** — it is in a file this PR already modifies, or a direct call-site of the changed code.
2. **Behaviour-local and small** — the fix needs no new type, no public-contract change, no new dependency, no new platform, and no new test infrastructure; it is bounded and reviewable in context (tens of lines, not a hidden-volume dig).
3. **Already verifiable** — the checks this PR already runs (its tests, its smoke) cover the fix; it needs no new verification path.

If any of the three is false, **defer** it — a follow-up issue or a `spawn_task` chip. Deferral is for a separate surface, a new target, a contract change, or unpredictable volume, not for "it is old".

## Two overrides

- **Causal coupling.** If this change makes the finding newly reachable, newly severe, or is its cause, it is in-scope by causality — fold it if cheap, otherwise surface it at the scope gate. Never silently defer a problem the current change introduces or worsens.
- **Correctness and security floor.** A correctness or security finding being looked at now is never silently dropped — fix it if it meets the test, otherwise surface it explicitly (a gate decision or a filed follow-up). Dropping it on "out of scope" without a trace is not allowed.

## Volume is not the primary axis

A large but mechanical fix in already-touched files can be in-scope; a small fix that drags in a new contract or a new platform is not. Weigh reach, blast radius, and verifiability — not line count.
