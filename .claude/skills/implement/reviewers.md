# Reviewer rosters

This file mirrors, for human audit, the roster logic that `scripts/select-pipeline.sh` encodes; the script is authoritative.

`touched` values: `ui`, `code`, `platform`, `docs`, `engdoc`, `claude`, `ux-brief`.

The `touched`-gated rows are evaluated against the live committed diff at review time; see `steps.md` Step 0 §Step/roster split.

---

## Inner-loop wave (Step 7, code track only)

Fired in parallel on each inner-loop iteration. Delta re-review on iterations 2+: re-dispatch only reviewers that raised `[REQUIRED]` the previous round, plus reviewers whose domain the new changes touch.

| Reviewer | Condition |
|---|---|
| `review-dod` | always |
| `review-correctness` | always, unless type is `docs` or type is `refactor` |
| `review-guides` | always |
| `review-glossary` | always |
| `review-architecture` | always (code track) |
| `review-tests` | always, unless type is `infra` |
| `review-platform` | `platform ∈ touched` |
| `review-ux-conformance` | `ui ∈ touched` AND the touched feature has a `ux-brief.md` |
| `review-ux-brief` | `ux-brief ∈ touched` |
| `review-design-system` | `ui ∈ touched` |
| `review-visual` | `ui ∈ touched` |

`review-reuse` and `review-adversarial` are skipped here — they run in Step 9 (simplify delta) and Step 10 (full review).

`review-architecture` runs for all code-track work. Over-inclusion is safe because a reviewer with nothing to flag returns APPROVE.

**`review-ux-conformance` dispatch note.** Resolve the feature slug from the issue number (a spec link or `docs/product/features/<slug>/` reference in the body, else glob `docs/product/features/**/ux-brief.md` and topic-match the changed paths). Pass the resolved brief path in the prompt. Do not dispatch when no brief exists — the orchestrator owns this gate so the agent is not launched only to self-skip.

---

## Wave A — full pre-PR (Step 10)

Fired in parallel. Both tracks run Wave B (`review-adversarial`) after Wave A.

### Code track

| Reviewer | Condition |
|---|---|
| `review-dod` | always |
| `review-guides` | always |
| `review-glossary` | always |
| `review-reuse` | always |
| `review-architecture` | always |
| `review-correctness` | type is not `docs`; type is not `refactor` |
| `review-tests` | type is not `infra` |
| `review-platform` | `platform ∈ touched` |
| `review-ux-conformance` | `ui ∈ touched` AND the orchestrator resolved a touched feature to an existing `ux-brief.md` |
| `review-ux-brief` | `ux-brief ∈ touched` |
| `review-design-system` | `ui ∈ touched` |
| `review-visual` | `ui ∈ touched` |

### Docs track

| Reviewer | Condition |
|---|---|
| `review-dod` | always |
| `review-guides` | always |
| `review-glossary` | always |
| `review-reuse` | always |
| `review-architecture` | `engdoc ∈ touched` (diff touches an ADR, engineering living-doc, or `architecture-principles.md`) |
| `review-ux-brief` | `ux-brief ∈ touched` |

The other reviewers (`review-correctness`, `review-tests`, `review-platform`, `review-design-system`, `review-ux-conformance`, `review-visual`) do not run on docs track — there is no code or UI implementation to check.

When the PR establishes or extends canon, tell each reviewer to apply the new rule to the diff itself.

### Wave B (both tracks)

`review-adversarial` — runs after Wave A with the combined Wave A findings as input.

---

## Iteration limits

- Inner-loop (Step 7): 4 iterations per track.
- Full review (Step 10): 2 iterations (docs converge faster than code).
