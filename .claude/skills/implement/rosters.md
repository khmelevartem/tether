# /implement — Reviewer rosters

The orchestrator evaluates each reviewer's `When` condition against the resolved profile and the actual diff, and dispatches only those whose condition holds in the relevant column. The conditions are data the orchestrator evaluates — not reviewer self-selection; a reviewer is never launched merely to decide whether it applies.

| Reviewer | When (type / diff conditions) | code · fast | code · full | docs · full |
|---|---|---|---|---|
| `review-correctness` | type ∉ {refactor} | ✓ | ✓ | — |
| `review-tests` | type ∉ {infra} | ✓ | ✓ | — |
| `review-guides` | — | ✓ | ✓ | ✓ |
| `review-glossary` | — | ✓ | ✓ | ✓ |
| `review-dod` | — | — | ✓ | ✓ |
| `review-reuse` | — | — | ✓ | ✓ |
| `review-architecture` | code: not trivial-one-callsite-bugfix, not cosmetic-refactor · docs: diff touches ADR / engineering living-doc / architecture-principles | — | ✓ | ✓ (docs condition) |
| `review-platform` | diff touches a platform source set | — | ✓ | — |
| `review-ux-conformance` | diff touches `composeApp/src/**` AND the touched feature has a `ux-brief.md` | — | ✓ | — |
| `review-ux-brief` | diff touches `docs/product/features/**/ux-brief.md` | — | ✓ | ✓ |
| `review-design-system` | diff touches `composeApp/src/**` | — | ✓ | — |
| `review-visual` | diff touches `composeApp/src/**` | — | ✓ | — |
| `review-adversarial` | full pre-PR review Wave B (after Wave A) | — | ✓ | ✓ |

## Notes

- **The fast wave is a narrow subset, not a near-copy of the full wave.** It carries only reviewers whose findings are both cheap to produce and meaningful on a partial diff: `review-correctness`, `review-tests`, `review-guides`, `review-glossary`. These catch drift on each inner-loop iteration before it propagates. Everything else runs once, at the full pre-PR review (Step 8), because it is either holistic (needs the complete diff) or expensive:
  - `review-dod` reads the issue's Definition of Done against the diff — mid-loop the work is incomplete, so it would report MISSING on every iteration. It is meaningful only once.
  - `review-reuse` looks for duplication, which accumulates across iterations, not within one — it also runs in the simplify-wave delta (Step 6).
  - `review-architecture`, `review-platform`, `review-ux-conformance`, `review-ux-brief`, `review-design-system` are holistic judgments on the finished change.
  - `review-visual` renders Compose previews to screenshots — far too costly to run per inner-loop iteration; it runs in the simplify-wave delta (when Compose changed) and at Step 8.
  - `review-adversarial` runs in the full review's Wave B with the combined Wave A findings as input.
- **`review-architecture`'s code-side condition is a judgment call, not a mechanical predicate.** "Trivial-one-callsite-bugfix" and "cosmetic-refactor" cannot be derived from the profile or the diff paths alone — the orchestrator decides. The docs-side condition (diff touches an ADR / engineering living-doc / architecture-principles) and every other reviewer's condition are path/profile data the orchestrator evaluates mechanically.
- `review-ux-conformance`: the orchestrator resolves the feature slug (spec link, a `docs/product/features/<slug>/` reference, else topic-match against `docs/product/features/**/ux-brief.md`) and passes the brief path. No brief → its condition is false → not dispatched.
- `review-visual` is dispatched whenever its condition holds; a missing brief narrows its checklist but does not change whether it runs.
