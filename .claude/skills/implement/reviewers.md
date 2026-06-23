# Reviewer catalog

`scripts/select-pipeline.sh` is the single authority for which reviewers fire per profile. `scripts/select-pipeline.test.sh` pins the expected rosters per profile — consult it for exact per-type reviewer lists. The test runs in CI as part of the test job, so a roster change that breaks the spec blocks the build.

`touched` values: `ui`, `code`, `platform`, `docs`, `engdoc`, `claude`, `ux-brief`. The live committed diff is bucketed into these values at each review step (`inner-loop`, `full-review`); see `steps.md` `classify` §Step/roster split.

---

## Reviewer reference

| Reviewer | Purpose | Wave(s) |
|---|---|---|
| `review-dod` | Verifies every acceptance-criterion item is satisfied | Inner-loop, Wave A |
| `review-correctness` | Checks logic, error paths, and contracts for correctness | Inner-loop, Wave A |
| `review-guides` | Checks conformance to engineering guides and CLAUDE.md rules | Inner-loop, Wave A |
| `review-glossary` | Checks terminology consistency against the project glossary | Inner-loop, Wave A |
| `review-architecture` | Checks layering, dependency direction, and structural decisions; on docs track, reviews engineering artifacts for architectural soundness | Inner-loop + Wave A (code); Wave A when `engdoc ∈ touched` (docs) |
| `review-tests` | Checks test coverage, quality, and testing-guide conformance | Inner-loop, Wave A |
| `review-platform` | Checks platform-specific correctness (Android, iOS, Desktop) | Inner-loop, Wave A |
| `review-ux-conformance` | Checks that the implementation matches the UX brief | Inner-loop, Wave A |
| `review-design-system` | Checks Compose usage, theming, and design-system conformance | Inner-loop, Wave A |
| `review-visual` | Visual fidelity review against specs and screenshots | Inner-loop, Wave A |
| `review-ux-brief` | Reviews the UX brief itself for completeness and correctness | Inner-loop, Wave A |
| `review-reuse` | Checks for duplication and opportunities to reuse existing code or content | Wave A |
| `review-adversarial` | Red-team pass over combined Wave A findings | Wave B |

**`review-ux-conformance` dispatch note.** Resolve the feature slug from the issue number (a spec link or `docs/product/features/<slug>/` reference in the body, else glob `docs/product/features/**/ux-brief.md` and topic-match the changed paths). Pass the resolved brief path in the prompt. Do not dispatch when no brief exists — the orchestrator owns this gate so the agent is not launched only to self-skip.

**`review-architecture` on code track** runs for all code-track work. Over-inclusion is safe because a reviewer with nothing to flag returns APPROVE.

---

## Iteration limits

- `inner-loop`: 4 iterations per track.
- `full-review`: 2 iterations (docs converge faster than code).
