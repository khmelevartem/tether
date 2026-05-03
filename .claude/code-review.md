# Code Review Guide for AI Agents

Structured process for reviewing pull requests. Follow phases in order.
Output is machine-readable; final decision is binary.

---

## Phase 1 — Gather inputs before forming any opinion

```bash
gh pr view <PR> --json title,body,commits,files
gh issue view <N> --json title,body
gh pr diff <PR>
```

Read in this order:
1. **Issue** — source of truth: requirements, constraints, DoD
2. **PR description** — author's claims
3. **Diff** — what was actually done

Gaps between these three are the primary source of review findings.

---

## Phase 2 — DoD check

Extract every acceptance criterion from the issue. For each:

- `DONE` — confirmed in diff
- `MISSING` — not present in diff
- `UNVERIFIABLE` — requires runtime; flag without guessing

---

## Phase 3 — Correctness

For every execution path — normal, exception, concurrent — ask: is the system in a valid state when it exits? Pay attention to partial initialization, resource cleanup ordering, and whether exception handling leaves the caller with a meaningful signal.

---

## Phase 4 — Security

Identify the trust boundary: everything from outside the process is untrusted until validated. For each untrusted value, ask whether validation covers the full range of possible inputs — not just the happy path — and whether it happens before the value is used.

---

## Phase 5 — Test quality

For each test ask: if the behavior under test were broken, would this test actually fail? Cross-reference tests against edge cases in the issue — flag gaps. Check that tests are isolated from each other and will work in CI.

---

## Phase 6 — Repo standards

Check against conventions in `CLAUDE.md`:
- Commit message format
- Scope: diff contains only changes relevant to the issue
- Public API contracts: if the issue says the API is unchanged, verify it

---

## Phase 7 — Reviewing a revised PR

When a PR has been updated in response to prior review comments:

For each previously raised issue, find the fix and ask:
1. Does the fix address the root cause, or only the symptom described in the comment?
2. Does the fix introduce a new problem? Fixes commonly trade one issue for another — especially in concurrency (changing synchronization mechanism) and error handling (adding a catch that masks a different failure).

A fix is complete only when the original concern is resolved without creating a new finding.

---

## Output format

```
PHASE: DoD
  [DONE] ...
  [MISSING] ...
  [UNVERIFIABLE] ...

PHASE: Correctness
  [REQUIRED] file:line — what is wrong and what must change
  [OK] ...

PHASE: Security
  [REQUIRED] ...
  [OK] ...

PHASE: Tests
  [REQUIRED] test name — why it does not provide the intended coverage
  [OK] ...

PHASE: Standards
  [PASS] / [REQUIRED] ...

DECISION: BLOCK | APPROVE

REQUIRED_BEFORE_MERGE:
  1. ...
  2. ...
```

`DECISION: APPROVE` only if there are zero `REQUIRED` items across all phases.
