# Code Review Guide for AI Agents — Multi-Agent

Structured process for reviewing pull requests. **Run as multiple specialised subagents in parallel, then aggregate.** Each subagent has a narrow focus and its own context — no shared assumptions, no shared blind spots. A coordinator agent dispatches them and merges findings into a single GitHub comment.

**Input:** PR number or issue number. If given an issue number, find the related PR first:
```bash
gh issue view <N> --json title,body --jq '.body' | grep -i "pull request\|PR #\|#[0-9]"
gh pr list --search "closes #<N>" --json number,title
```

**Why multi-agent.** A single agent running a long phase checklist accumulates context fatigue and tends toward confirmation: «no findings» blurs «checked thoroughly» with «didn't look hard enough». Splitting the review into independent narrow-focus agents trades a bit of compute for genuinely different lenses — and forces an adversarial probe pass that catches blind spots a single agent shares with itself.

**Attitude across all subagents:** Be attentive and critical about substance — logic, correctness, test coverage, architecture, library idioms, conventions defined in `CLAUDE.md` / `docs/engineering/`. **Style is substantive when it's documented in this project.** The only thing not to flag is pure formatting (whitespace, line length) — ktlint owns that.

---

## Coordinator workflow

1. **Gather inputs once** (coordinator):
   ```bash
   gh pr view <PR> --json title,body,commits,files
   gh issue view <N> --json title,body
   gh pr diff <PR>
   ```
   Classify the PR type:
   ```
   PR_TYPE: FEATURE | BUGFIX | REFACTOR | INFRA | DOCS | DEPENDENCY
   ```

2. **Dispatch subagents #1–6 in parallel** via the `Agent` tool — single message, multiple tool calls. Each subagent gets:
   - PR_TYPE
   - PR number, issue number
   - A focused prompt (templates below)
   - Instruction to return findings in the standard format (see «Findings format» below)

3. **Wait for #1–6 to complete**, then dispatch subagent #7 (Adversarial probe) sequentially with #1–6 findings as input.

4. **Aggregate** all subagent findings into the final report, deduplicate overlapping items, post to GitHub:
   ```bash
   gh pr review <PR> --comment --body "$(cat <<'EOF'
   ## Code Review
   [aggregated findings — see Output format below]
   EOF
   )"
   ```

5. **DECISION: APPROVE** only if zero `REQUIRED` items across all seven subagents. `UNVERIFIABLE` items don't block APPROVE on their own — they appear as explicit questions to the author. **The coordinator does not run the smoke itself**; it verifies the implementer recorded a smoke verdict in the PR body (see subagent #3).

---

## Subagent #1 — Guide compliance

**Focus only on:** conformance to `CLAUDE.md` and `docs/engineering/*`. Treat documented project rules as substantive.

**Inputs to read:**
- `CLAUDE.md`
- `docs/engineering/architecture-principles.md`, `dependency-injection.md`, `presentation-layer.md`, `modules.md`, `testing.md` (those that touch the diff's areas)
- The PR diff

**Check, with concrete diff references:**
- **Comment policy** (`CLAUDE.md` → «Code style»). For each new `//` or `KDoc` in the diff, ask: does the function/class name already say this? If yes — REQUIRED, drop the comment. Exception: comments that document non-obvious external invariants, intentionally swallowed exceptions, or WHY that the code can't express.
- **Naming** (`architecture-principles.md` → «Naming: spell properties out»). Abbreviated names (`srv`, `cfg`, `mgr`) → REQUIRED rename.
- **Library idioms.** Known-fragile patterns must use the right primitive:
  - `MutableStateFlow` / `MutableValue` mutations → `.update { ... }` for read-modify-write, not `.value = ...` (loses atomicity).
  - Decompose retention on Android entry points → `retainedComponent { ... }`.
  - Coroutine scoping → constructor injection or lifecycle-aware factories, never `GlobalScope`.
- **TODO discipline.** Every new `TODO` / `FIXME` must reference an issue (`— #<N>`). Untracked TODOs → REQUIRED.
- **Repo standards.** Commit message format (`#<issue>:`), diff scope matches issue, no incidental refactors that belong in a separate PR.
- **Anonymous-object anti-pattern** (`architecture-principles.md`). New `object : Interface { ... }` inline outside one-shot test fakes → REQUIRED extraction to a named class.

**Don't flag:** pure formatting (ktlint owns it), naming preferences not documented in guides, debates about idiomatic style that aren't pinned in `docs/`.

---

## Subagent #2 — Platform specifics

**Focus only on:** KMP source-set hierarchy, `expect`/`actual` contracts, Android, Apple, Desktop specifics.

**Apply only if diff touches** `androidMain/`, `iosMain/`, `macosMain/`, `jvmMain/`, `desktopMain/`, `appleMain/`. Otherwise return `[SKIPPED]`.

**Check:**
- **Source-set hierarchy** (`CLAUDE.md` → «Architecture invariants»). Code that can live in `commonMain` lives there; platform sets only host code requiring platform API. Stuff in `androidMain` that's pure Kotlin → REQUIRED move.
- **`expect`/`actual` completeness.** For each `expect` in `commonMain/`, every active target has an `actual`. Adding a new feature to one platform without the others → REQUIRED stub or full impl elsewhere.
- **Android:**
  - API level compatibility: deprecated API on API 34+ → version check (`Build.VERSION.SDK_INT >= ...`) + `@Suppress("DEPRECATION")` with fallback.
  - Permissions: new feature requiring permission → entry in `AndroidManifest.xml`.
  - **Lifecycle / entry points**: any change to `MainActivity` (or other entry points) → explicitly verify config-change retention. Components built in `onCreate` without `retainedComponent { ... }` (or equivalent retention) → REQUIRED. State that must survive rotation but isn't in `AppContainer` repository or `StateKeeper` → REQUIRED.
  - Foreground service changes → manifest declaration matches, `START_STICKY` semantics intact, `configChanges` correctly listed.
- **Apple (`appleMain/`, `iosMain/`, `macosMain/`):**
  - **ObjC delegate GC** (`docs/knowledge/apple-platform.md`). Every ObjC object whose `.delegate` is set must have a matching Kotlin strong reference (class field). Without it the delegate is GC'd silently and callbacks don't fire.
  - iOS Local Network Privacy: if feature uses local network / mDNS / Bonjour → `iosApp/iosApp/Info.plist` has `NSLocalNetworkUsageDescription` and `NSBonjourServices`. Missing → REQUIRED (works in simulator, silent failure on device).
- **No regressions across platforms.** Changes to one `actual` shouldn't break the build or runtime of others.

**Don't flag:** correctness in pure-Kotlin business logic (that's #5), tests (that's #6).

---

## Subagent #3 — DoD / scope

**Focus only on:** does the diff deliver what the issue (and feature spec, if any) requires?

**Inputs:**
- `gh issue view <N> --json title,body,comments`
- If issue body references `docs/product/features/<feature>.md` or such file exists for this issue → read it.
- PR diff
- PR body

**Check:**
- **Each acceptance criterion** (from issue DoD section, feature spec «What working looks like», issue edge cases):
  - `[DONE]` — confirmed in diff
  - `[MISSING]` — not present in diff → REQUIRED
  - `[UNVERIFIABLE]` — needs runtime → explicit question to author, doesn't block APPROVE on its own unless safety-critical
- **Scope match.** Diff contains only changes relevant to the issue. Incidental refactor / unrelated touch-ups → REQUIRED removal or separate PR.
- **Smoke verdict recorded in PR body.** If the diff touches entry points, native deps, FGS, manifest, Info.plist, FileServer, CLI, mDNS, or common UI → PR body MUST have a `## Smoke verdict` section with per-block 🟢/🟡/🔴 status. Missing or contains 🟡/🔴 → REQUIRED. (You do not run smoke yourself — you verify the implementer ran it. Verification is static: check the PR body.)
- **Multi-platform UI smoke.** If diff modifies `commonMain` UI and feature spec lists multiple shipping platforms — Smoke verdict must include manual verification on each shipping platform, not just one.
- **Dependency check recorded.** If the diff modifies `gradle/libs.versions.toml` or adds `implementation(...)` / `api(...)` in `build.gradle.kts` → PR body MUST have a `## Dependency check` section confirming the new/upgraded library status (current vs deprecated, last release, alternatives if deprecated). Missing → REQUIRED.

**Don't flag:** internal code quality (other subagents handle), platform specifics (subagent #2).

---

## Subagent #4 — Cross-cutting / reuse

**Focus only on:** does this diff duplicate, contradict, or ignore existing code, abstractions, or documentation in the repo?

**Inputs:** PR diff + grep the rest of the repo.

**Check:**
- **New abstraction (interface, sealed class, top-level extension) → grep for similar existing ones.** For each new interface, sealed class, or public abstraction in the diff:
  - Search for similarly-named or similarly-shaped existing abstractions in `commonMain`, `androidMain`, etc.
  - Method signatures that look like the new one (`(deviceName: String, port: Int)` etc.) — possible structural duplicate even if names differ.
  - If a near-duplicate exists → REQUIRED finding: either consolidate or justify why both must exist.
- **Util / extension reuse.** New helper function → grep existing utility files for the same operation under a different name.
- **Doc-vs-code consistency.** If the diff implements a pattern that `docs/engineering/` describes (`presentation-layer.md` examples, DI checklist, etc.) — open the doc and side-by-side compare the example to the actual code. Divergence → REQUIRED finding (either fix code or update doc in same PR; the team's «doc-as-spec» convention: when the first concrete implementation lands for an architectural sketch, the doc is updated in the same PR).
- **3rd-party API claims must be verified.** Any assertion in the review's own findings (or in code comments / commit messages) about what a 3rd-party library «requires» or «does not support» → must be backed by a doc link or a verified compile/test. Unverified claim → REQUIRED clarification before approve. (Example seen in practice: «Decompose requires `AppCompatActivity`» — wrong; `defaultComponentContext` is an extension on `ComponentActivity`.)
- **Entry-point patterns checklist.** For changes to `MainActivity` / `MainViewController` / `Main.kt`:
  - Who retains state across config change / window resize? Named explicitly?
  - Is init order correct (permissions → service start → DI → component)?
  - Any Apple ObjC delegate set on a local that escapes the function scope?

**Don't flag:** style (subagent #1), tests (subagent #6).

---

## Subagent #5 — Correctness & security

**Focus only on:** is the system in a valid state across normal, exception, concurrent execution paths? Are untrusted inputs validated?

**Skip for:** DOCS, DEPENDENCY (unless deps introduce API changes), REFACTOR (only behaviour-preserving checks: existing tests still cover what changed).

**Check correctness:**
- For every execution path in the diff (normal, exception, concurrent) — is state valid at exit?
- Partial initialisation and resource cleanup ordering.
- Exception handling: does the caller get a meaningful signal, or is the failure silently swallowed?
- Race conditions and shared mutable state without synchronisation.
- Resource leaks: streams, sockets, coroutines without cancellation.
- **For BUGFIX:** does the fix address the root cause, or only the symptom? Could the same root cause manifest elsewhere?

**Check security:**
- Identify the trust boundary: everything from outside the process is untrusted until validated.
- For each untrusted value, ask: is validation covering the full input range, not just the happy path? Does it happen before the value is used?
- New network endpoints, new file paths from external input, new protocol fields — explicit trust analysis.

**Don't flag:** missing tests (subagent #6), platform specifics (subagent #2), library idioms (subagent #1).

---

## Subagent #6 — Test quality

**Focus only on:** do tests actually defend the behaviour the PR introduces or claims to fix?

**Skip for:** DOCS, INFRA (no behavioural code).

**Check:**
- **Coverage vs issue.** Cross-reference tests against the issue's acceptance criteria, edge cases, non-functional requirements. Gaps → REQUIRED. Standard rules:
  - If a scenario is automatable in the current test infrastructure **and** in scope (DoD, listed edge cases, behaviour the PR introduces) — it's `REQUIRED`, not `[OPTIONAL]`. Priority labels («nice-to-have», «follow-up», «не блокер») don't make a relevant test optional.
  - Legitimate reasons to skip a test: scenario needs disproportionate infrastructure (real disk-full, hardware-only condition, real network drop), or scenario is genuinely out of scope. Both must be stated explicitly.
- **For BUGFIX:** is there a test that would have caught this bug? If no — REQUIRED add. The PR can't approve a fix that leaves the regression door open.
- **Regression gap (BUGFIX / REFACTOR):** for each piece of logic the PR removes or replaces — was the old behaviour covered by a test? Missing test for old behaviour means silent regression possible on next touch.
- **For REFACTOR:** existing tests still green, coverage not decreased, no test deleted or weakened to make refactor pass. Don't require *new* tests.
- **Test conventions.** Fakes for cross-test-suite types (e.g. `FakeDeviceDiscovery` for `DeviceDiscovery`) live in `commonTest/.../<area>/Fake<Interface>.kt`, not inline as `private class` in a single test file. Inline placement for a cross-cutting interface → REQUIRED extraction.
- **Test isolation.** Tests work in CI; no order dependence; no shared mutable global state across tests.

**Don't flag:** production code style (subagent #1), correctness in production code (subagent #5).

---

## Subagent #7 — Adversarial probe (runs last)

**Focus only on:** what did subagents #1–6 miss? What is most likely wrong here that no specialised lens caught?

**Inputs:**
- PR diff
- Findings from subagents #1–6 (the coordinator passes them in)
- Issue body

**Process — adversarial:**

1. **Generate 3–5 hypotheses** about what could be wrong in this PR, based on the *shape* of the diff. Examples of hypothesis shapes (use only those that fit; don't pad):
   - «Diff adds a new abstraction X → hypothesis: existing similar abstraction Y exists and this duplicates it.»
   - «Diff touches an Android entry point → hypothesis: retention across config change is missing or wrong.»
   - «Diff changes presentation layer → hypothesis: doc `presentation-layer.md` says one thing, code does another.»
   - «Diff adds `actual` only for one platform → hypothesis: other platforms' `actual` is now broken or inconsistent.»
   - «Diff claims «library X requires Y» (in comments, commits, or prior reviews) → hypothesis: claim is wrong; verify against library docs.»
   - «Diff adds string resources / hardcoded strings → hypothesis: i18n approach unresolved, TODO without issue ref.»
   - «Diff touches Apple `actual` with a `.delegate = self` set → hypothesis: ObjC delegate GC bug.»
   - «PR body claims smoke passed but the change category is one that's likely platform-incomplete → hypothesis: other platforms not tested.»

2. **For each hypothesis** — perform a concrete verification step:
   - grep / read the relevant file
   - cite the doc passage
   - check the diff line
   - cross-reference against another subagent's finding
   
   Don't say «considered, no issue»: spell out the actual check. If hypothesis confirmed → REQUIRED finding citing the specific line/file. If hypothesis rejected → one-line justification.

3. **Final list** in your output:
   - Hypothesis 1: <text>. Check: <action>. Verdict: <confirmed → REQUIRED finding | rejected: <why>>.
   - Hypothesis 2: ... (same)
   - ...

**This subagent's output is mandatory before APPROVE.** If subagent #7 is missing or its hypotheses are all «rejected» without spelling out the check — the coordinator treats it as «didn't actually probe» and blocks APPROVE.

---

## Findings format (each subagent returns)

```
SUBAGENT: <name>

[REQUIRED] file:line — what is wrong and what must change
[REQUIRED] ...
[OK] <brief note on what was checked and looks good>
[UNVERIFIABLE] <question to author>
[SKIPPED] <why this subagent doesn't apply to this PR>
```

If subagent has nothing to flag, return `[OK]` lines covering what was inspected — not a bare «no findings». This makes blind spots visible: a subagent that returns nothing has *not* yet justified what it looked at.

---

## Coordinator output format (posted to GitHub)

```
## Code Review (multi-agent)

PR_TYPE: <type>

### Guide compliance (#1)
[REQUIRED] ...
[OK] ...

### Platform specifics (#2)
[REQUIRED] ...
[OK] ...
[SKIPPED] no platform code touched

### DoD / scope (#3)
[DONE] ...
[MISSING] ...
[UNVERIFIABLE] question for author

### Cross-cutting / reuse (#4)
[REQUIRED] ...
[OK] ...

### Correctness & security (#5)
[REQUIRED] file:line — ...
[OK] ...

### Test quality (#6)
[REQUIRED] test name — why it doesn't cover what it should
[OK] ...

### Adversarial probe (#7)
- Hypothesis 1: ...  Check: ...  Verdict: ...
- Hypothesis 2: ...
- ...
[REQUIRED] (anything confirmed by hypotheses 1–N)

---

DECISION: BLOCK | APPROVE

REQUIRED_BEFORE_MERGE:
1. ...
2. ...
```

`DECISION: APPROVE` only if there are zero `REQUIRED` items across all seven subagents **and** subagent #7 spelled out at least 3 hypotheses with concrete verification steps.

---

## Reviewing a revised PR

When a PR has been updated in response to prior review comments:

- The coordinator re-runs the full multi-agent flow.
- Subagent #7 additionally probes: «for each previously raised issue, find the fix and ask — does it address the root cause, or only the symptom? Does it introduce a new problem (fixes commonly trade one issue for another, especially in concurrency and error handling)?»
- A fix is complete only when the original concern is resolved without creating a new finding.
