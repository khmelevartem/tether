# /implement — Reviewer rosters

The orchestrator evaluates each reviewer's `When` condition against the resolved profile and the actual diff, and dispatches only those whose condition holds in the relevant column. The conditions are data the orchestrator evaluates — not reviewer self-selection; a reviewer is never launched merely to decide whether it applies.

| Reviewer | When (type / diff conditions) | code · fast | code · full | docs · full |
|---|---|---|---|---|
| `review-dod` | — | ✓ | ✓ | ✓ |
| `review-guides` | — | ✓ | ✓ | ✓ |
| `review-glossary` | — | ✓ | ✓ | ✓ |
| `review-reuse` | — | — | ✓ | ✓ |
| `review-correctness` | type ∉ {refactor-cosmetic} | ✓ | ✓ | — |
| `review-tests` | type ∉ {infra} | ✓ | ✓ | — |
| `review-architecture` | code: not trivial-one-callsite-bugfix, not cosmetic-refactor · docs: diff touches ADR / engineering living-doc / architecture-principles | ✓ | ✓ | ✓ (docs condition) |
| `review-platform` | diff touches a platform source set | ✓ | ✓ | — |
| `review-ux-conformance` | diff touches `composeApp/src/**` AND the touched feature has a `ux-brief.md` | ✓ | ✓ | — |
| `review-ux-brief` | diff touches `docs/product/features/**/ux-brief.md` | ✓ | ✓ | ✓ |
| `review-design-system` | diff touches `composeApp/src/**` | ✓ | ✓ | — |
| `review-visual` | diff touches `composeApp/src/**` | ✓ | ✓ | — |
| `review-adversarial` | full pre-PR review Wave B (after Wave A) | — | ✓ | ✓ |

## Notes

- `review-reuse` is absent from the fast wave (duplication accumulates across iterations, not within one) — it runs in the simplify-wave delta and in the full review. `review-adversarial` runs in the full review's Wave B with the combined Wave A findings as input.
- `review-ux-conformance`: the orchestrator resolves the feature slug (spec link, a `docs/product/features/<slug>/` reference, else topic-match against `docs/product/features/**/ux-brief.md`) and passes the brief path. No brief → its condition is false → not dispatched.
- `review-visual` is dispatched whenever its condition holds; a missing brief narrows its checklist but does not change whether it runs.
