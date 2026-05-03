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
1. **Issue** — source of truth: requirements, constraints, DoD. If no issue exists, use the PR description as DoD source.
2. **PR description** — author's claims
3. **Diff** — what was actually done

Gaps between these three are the primary source of review findings.

Classify the PR type before proceeding:

```
PR_TYPE: FEATURE | BUGFIX | REFACTOR | INFRA | DOCS | DEPENDENCY
```

This determines which phases apply and how they are interpreted (see phase headers).

---

## Phase 2 — DoD check

Extract every acceptance criterion from the issue (or PR description if no issue). For each:

- `DONE` — confirmed in diff
- `MISSING` — not present in diff
- `UNVERIFIABLE` — requires runtime; include as an explicit question to the author in output; does not block APPROVE on its own unless the criterion is safety-critical

---

## Phase 3 — Correctness

*Skip for: DOCS, DEPENDENCY (unless dependency introduces API changes).*

For every execution path — normal, exception, concurrent — ask: is the system in a valid state when it exits? Pay attention to:
- Partial initialization and resource cleanup ordering
- Whether exception handling leaves the caller with a meaningful signal
- Race conditions and shared mutable state without synchronization
- Resource leaks: streams, sockets, coroutines without cancellation

**For BUGFIX additionally:** Does the fix address the root cause, or only the symptom? Could the same root cause manifest elsewhere in the codebase?

---

## Phase 4 — Security

*Skip for: DOCS, REFACTOR (behavior-preserving only).*

Identify the trust boundary: everything from outside the process is untrusted until validated. For each untrusted value, ask whether validation covers the full range of possible inputs — not just the happy path — and whether it happens before the value is used.

---

## Phase 5 — Test quality

*Skip for: DOCS, INFRA.*

**For FEATURE / BUGFIX:** For each test ask: if the behavior under test were broken, would this test actually fail? Cross-reference tests against edge cases in the issue — flag gaps. Check that tests are isolated from each other and will work in CI.

**For REFACTOR:** Do not require new tests. Instead verify: existing tests still pass (green in CI), test coverage has not decreased, and no test was deleted or weakened to make the refactor pass.

---

## Phase 6 — Repo standards

Check against conventions in `CLAUDE.md`:
- Commit message format
- Scope: diff contains only changes relevant to the issue
- Breaking changes: for any change to a serialized protocol (e.g. `Device.kt`) or public API, verify backward compatibility explicitly — regardless of what the issue says

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
PR_TYPE: <type>

PHASE: DoD
  [DONE] ...
  [MISSING] ...
  [UNVERIFIABLE] question for the author

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

`DECISION: APPROVE` only if there are zero `REQUIRED` items across all phases. `UNVERIFIABLE` items do not block APPROVE unless safety-critical; they must appear as explicit questions to the author.
