# /implement — Reviewer rosters

Reviewer selection table. Columns `fast` and `full-A` mark which reviewers participate in which wave (Step 5 inner loop and Step 8 Wave A). `review-adversarial` runs in Step 8 Wave B only, after Wave A.

| Reviewer | Fires when (predicate over profile + diff) | fast (Step 5) | full-A (Step 8) |
|---|---|---|---|
| `review-dod` | always | ✓ | ✓ |
| `review-guides` | always | ✓ | ✓ |
| `review-glossary` | always | ✓ | ✓ |
| `review-reuse` | always — but skipped in Step 5 fast wave; runs in Step 6 simplify delta and Step 8 | — | ✓ |
| `review-correctness` | `track==code` AND `type ∉ {docs, refactor-cosmetic}` | ✓ | ✓ |
| `review-architecture` | (`track==code` AND NOT trivial-one-callsite-bugfix AND NOT cosmetic-refactor) OR (`track==docs` AND diff touches ADR / engineering living-doc / architecture-principles) | ✓ | ✓ |
| `review-tests` | `track==code` AND `type ∉ {docs, infra}` | ✓ | ✓ |
| `review-platform` | diff touches a platform source set | ✓ | ✓ |
| `review-ux-conformance` | diff touches `composeApp/src/**` AND the touched feature has a `ux-brief.md` (orchestrator resolves slug before dispatch; skip when no brief exists — do not launch the agent to self-skip) | ✓ | ✓ |
| `review-ux-brief` | diff touches `docs/product/features/**/ux-brief.md` | ✓ | ✓ |
| `review-design-system` | diff touches `composeApp/src/**` | ✓ | ✓ |
| `review-visual` | diff touches `composeApp/src/**` (agent renders PNGs via Roborazzi; a missing brief narrows its checklist but does not skip it) | ✓ | ✓ |
| `review-adversarial` | always — Wave B only, after Wave A completes | — | ✓ (Wave B) |

## Notes

**`review-reuse` and `review-adversarial`** are skipped in the fast wave. `review-reuse` runs in the Step 6 simplify-delta pass (duplication is what most likely accumulated across iterations) and in Step 8 Wave A. `review-adversarial` runs in Step 8 Wave B with the combined Wave A findings as input.

**`review-ux-conformance`** gate ownership: the orchestrator resolves the feature slug from the issue number (spec link or `docs/product/features/<slug>/` reference in the body, else glob `docs/product/features/**/ux-brief.md` and topic-match the changed paths) and passes the brief path in the prompt. On pre-PR dispatch (Step 5): resolve from the issue number, not `gh pr view`. Do not dispatch when no brief exists.

**`review-visual`** renders even when a ux-brief is missing — the missing brief narrows its checklist but does not suppress the agent.

**docs-track zeroes the code/UI reviewers:** `review-correctness`, `review-tests`, `review-platform`, `review-ux-conformance`, `review-design-system`, `review-visual` all fire on predicates that require `track==code` or `diff touches composeApp/src/**` — neither holds for a pure docs-track PR, so they are inactive by the table. `review-architecture` switches to its ADR/living-doc predicate.

**code-track carries all applicable reviewers:** `review-architecture` uses its code-track predicate (all non-trivial, non-cosmetic work). Reviewers whose predicates depend on the diff (platform source sets, Compose paths, ux-brief paths) activate or skip based on the actual diff.
