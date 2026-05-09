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

## Phase 3 — Platform specifics

*Apply only if diff touches: `androidMain/`, `iosMain/`, `macosMain/`, `jvmMain/`, `desktopMain/`, `appleMain/`. Otherwise skip.*

Tether is a multiplatform project (Kotlin Multiplatform) supporting Android, iOS, macOS, Desktop (JVM), with Linux planned. Verify:

- **API level compatibility** (Android): If code uses API-specific features, are there version checks (`Build.VERSION.SDK_INT >= ...`)? Deprecated APIs must be suppressed with `@Suppress("DEPRECATION")` and have fallbacks for older API levels.
- **Permissions** (Android): Do new Android features require permissions in `AndroidManifest.xml`? Network access, location, camera, etc.
- **Platform parity**: If one platform gets a feature, check if other platforms need corresponding implementations to satisfy the `expect/actual` contract in `commonMain/`.
- **No regressions**: Changes to platform-specific code shouldn't break the build or runtime behavior of other platforms.

**Apple-specific checks** (apply when diff touches `appleMain/`, `iosMain/`, `macosMain/`):
- **ObjC delegate GC**: every ObjC object whose `.delegate` is set must have a matching Kotlin strong reference (a class field). ObjC `delegate` properties are `weak`; without a strong ref the Kotlin object is GC'd before callbacks fire. See `docs/knowledge/apple-platform.md`.
- **iOS Local Network Privacy**: if the feature uses local network / mDNS / Bonjour, verify `iosApp/iosApp/Info.plist` has `NSLocalNetworkUsageDescription` and `NSBonjourServices`. Missing these causes silent failure on device (works in simulator).

**Example findings:**
- `[REQUIRED]` `serviceInfo.host` is deprecated on API 34+; add version check with `Build.VERSION.SDK_INT >= UPSIDE_DOWN_CAKE`.
- `[REQUIRED]` New feature added to `androidMain/` but `expect` declaration in `commonMain/` has no matching `actual` for `iosMain/` — add stub or full implementation.
- `[UNVERIFIABLE]` New feature uses location APIs on Android but no corresponding iOS implementation — does the `expect` interface require location? Ask author.

---

## Phase 4 — Correctness

*Skip for: DOCS, DEPENDENCY (unless dependency introduces API changes).*

For every execution path — normal, exception, concurrent — ask: is the system in a valid state when it exits? Pay attention to:
- Partial initialization and resource cleanup ordering
- Whether exception handling leaves the caller with a meaningful signal
- Race conditions and shared mutable state without synchronization
- Resource leaks: streams, sockets, coroutines without cancellation

**For BUGFIX additionally:** Does the fix address the root cause, or only the symptom? Could the same root cause manifest elsewhere in the codebase?

---

## Phase 5 — Security

*Skip for: DOCS, REFACTOR (behavior-preserving only).*

Identify the trust boundary: everything from outside the process is untrusted until validated. For each untrusted value, ask whether validation covers the full range of possible inputs — not just the happy path — and whether it happens before the value is used.

---

## Phase 6 — Test quality

*Skip for: DOCS, INFRA.*

**For FEATURE / BUGFIX:** For each test ask: if the behavior under test were broken, would this test actually fail? Cross-reference tests against edge cases in the issue — flag gaps. Check that tests are isolated from each other and will work in CI.

**Regression gap check (BUGFIX / REFACTOR):** For every piece of logic the PR removes or replaces, ask: was that logic covered by a test? If no — flag it as a gap. A missing test for the old behavior means a silent regression is possible the moment someone touches that code again. The fix must either add the missing test or explicitly document why the old behavior is intentionally abandoned.

**For REFACTOR:** Do not require new tests. Instead verify: existing tests still pass (green in CI), test coverage has not decreased, and no test was deleted or weakened to make the refactor pass.

---

## Phase 7 — Repo standards

Check against conventions in `CLAUDE.md`:
- Commit message format
- Scope: diff contains only changes relevant to the issue
- Breaking changes: for any change to a serialized protocol (e.g. `Device.kt`) or public API, verify backward compatibility explicitly — regardless of what the issue says

---

## Phase 8 — Reviewing a revised PR

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

PHASE: Platform specifics
  [REQUIRED] ...
  [OK] ...

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

### Post review to GitHub

**Always attach the review as a comment to the PR on GitHub.** Do not leave findings locally:

```bash
gh pr comment <PR> --body "$(cat <<'EOF'
## Code Review — Issue #<N>

[paste the review output here, formatted as markdown]
EOF
)"
```

The PR author and reviewers need to see findings in the GitHub UI. Findings left only in local output are invisible to the team.