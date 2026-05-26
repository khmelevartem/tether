<!--
Goal — the reviewer quickly sees the essence + places where the author is uncertain.
Delete empty sections. `Closes #N` — required.

Anti-patterns (do not write these):
- AC checklist that duplicates the issue DoD
- "Test plan" / "Smoke: N/A" for trivial and DOCS-only PRs
- A file-by-file retelling of the diff
- A long preamble — start straight with "why"
-->

**What / why.** 1-2 sentences. What effect or invariant changes.

**👀 Sanity-check.** Choices you are uncertain about; defer decisions ("deferred X to follow-up" / TODO in Y) — put them here, not in Notes below; non-obvious spots `file:line`. Delete the section if there is nothing.
- 

**Scope.** Omit for local PRs.
- ❌ not touched: X (lives in Y / deferred to #N)
- 📎 affected: #M / doc Z — updated / deferred

Closes #
