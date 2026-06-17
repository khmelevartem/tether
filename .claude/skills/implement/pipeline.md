# /implement — Pipeline

Ordered step catalog. Each step: name, purpose, `active-when` predicate, what it dispatches by role.

Reviewer selection — [`rosters.md`](rosters.md). Classification rules, branch prefixes, gate specifics, smoke/probe recipe, iteration limits, producer sets — [`config.md`](config.md). Cross-cutting engine — [`SKILL.md`](SKILL.md) §The shared engine.

---

## Step 0 — Re-entry gate

**active-when:** always

**Purpose:** determine whether this is a fresh run or a feedback iteration on an existing open PR, and bring the branch up to date before any work proceeds.

At each invocation, first run:

```bash
gh pr list --search "issue:#<N>" --state open --json number,isDraft,headRefName
```

**No PR** → proceed to Step 1.

**PR exists and is open** → feedback iteration. Before anything else, gate on main drift:

```bash
git fetch origin main --quiet
git merge-base --is-ancestor origin/main HEAD && echo up-to-date || echo behind
```

If `behind` → run `/pull-main` and adjust to whatever it brought before classifying comments or running reviewers. Otherwise iterating on stale canon — incoming PRs may have shifted rules under your feet, and the review wave runs against a main that no longer matches the project's current canon. If `up-to-date` → skip.

Then read **all** human comments on the PR (`gh api repos/<owner>/<repo>/pulls/<PR>/comments` + `gh pr view <PR> --comments`) and for each determine its status: addressed in commits after it — or not.

**Creation date does not determine relevance.** Filtering comments by `created_at > <date-of-previous-run>` is forbidden; an unaddressed comment remains relevant regardless of how old it is.

**Counted is not read.** Returning a `length`/count without fetching the `body` of each comment does not count as reading. While unaddressed comments remain outstanding, the review and consistency waves must not run — they would process a diff that needs to be redone first.

**code-track re-entry.** The run must include on a fresh diff: Step 5 (inner loop) → Step 6 (simplify wave) → Step 8 (full review). Nothing may be skipped — otherwise review iterations run at lower quality than the initial implementation.

**docs-track re-entry.** The run must include on a fresh diff: Step 7 (consistency pass) → Step 8 (full review).

Step 10 (commit + push + final summary) is simplified on re-entry: the commit goes into the existing branch, force-push is not needed, do not create a new PR.

**After push on re-entry — reply to every addressed inline comment** via `gh api -X POST repos/<owner>/<repo>/pulls/<PR>/comments/<comment_id>/replies -f body="<reply>"`. For each comment: what was done + the commit SHA (or explicit reasoning if the comment was deliberately declined). A reply is the "addressed" signal — without it the reviewer cannot see the loop closed and the next re-entry reads it again as unaddressed.

**By-agent attribution.** When a PR comment is scoped to work produced by a specific upstream agent, route it back to that agent first — see SKILL.md §The shared engine → By-agent attribution / re-entry routing.

---

## Step 1 — Recon & setup

**active-when:** always

**Purpose:** read the issue, sweep the doc corpus, set up the worktree, brief the user.

```bash
gh issue view <N> --json title,body,labels,comments
gh pr list --search "issue:#<N>" --state open --json number,isDraft,headRefName
```

**Sweep the repo for prior mentions of `#<N>`** — `grep -rn "#<N>" .` over the working tree. Every hit is either resolved in this PR, or escalated to the user as "can't do here — move to #M?". Never silently leave a `TODO(#<N>)` after merge.

**Comments are not a discussion — they are potentially a canon-update body.** When a comment conflicts with the body — the comment takes priority; escalate to the user in one line.

**Critical reading.** Treat the issue description as a starting point, not a fact. Escalate to the user before starting work if any of these gaps are present:

- Only one platform is mentioned, yet the task is cross-platform.
- Errors and fail-paths are not described.
- It is unclear how to test (no edge cases / runtime check guidance).
- BUGFIX without at least a hypothesised bug cause.
- The deliverable's benefit rests on an assumption not yet established, or it carries a recurring per-run cost (tooling / process / CI / hook / inter-agent-protocol change) that may outweigh that benefit.
- Filler phrases: "fill in if you have something", "should work correctly", "and so on", "describe as you see fit", "record wherever appropriate".
- *(docs-track)* It is unclear from the issue which specific artifacts are expected as output.

Any such gap is a reason to return to the spec/AC ambiguity gate (G-spec/AC ambiguity), not to "patch it along the way".

**Doc discovery.** Dispatch one read-only recon agent (`Explore`) using the brief in [`config.md`](config.md) §Doc-discovery recon brief. Do NOT read the corpus into the orchestrator thread. Hold only the compact digest; read a specific doc verbatim later only when a gate decision needs the exact text. Mention the relevant documents the recon agent surfaced in the briefing to the user.

**Worktree setup — do this BEFORE dispatching any agent that edits files.** Use the branch prefix from [`config.md`](config.md) §Worktree / branch prefix. Branch from `origin/main`, never local `main`.

**Briefing back to the user.** After reading the issue and doing recon, before any question to the user — post a short 3–6 line briefing in chat: what we are doing, why (motivation / context from the issue), classification (track + affected layers/platforms). If asking questions in the same message — attach 1–2 lines of context to each. One briefing per run; do not repeat on re-entry.

---

## Step 2 — Classify → resolve profile

**active-when:** always

**Purpose:** resolve `{track, type, docLayers}` from the issue. This is the single resolution point; downstream steps key on it.

Apply the classification rules from [`config.md`](config.md) §Classification rules. Assign:

- `track` — `code` or `docs`
- `type` — `feature` / `bugfix` / `refactor` / `infra` / `docs`
- `docLayers` — for `track==docs`: the ordered set of artifact layers from the layer-classification table in `config.md`. For `track==code`: the incidental set (e.g. `{ADR}` when a code feature needs an architectural decision record).

Classification ambiguity — one question to the user (fold into G-sub-agent open question gate) before dispatching anything.

**Read-only result of this step:** an ordered list of layers to produce (docs-track), or a confirmed code-track with the profile populated.

---

## Step 3a — Early gate: spec/AC + ux-brief

**active-when:** `type==feature`

**Purpose:** ensure a usable spec and (if UI) ux-brief exist before planning or implementation.

Dispatch `spec-writer` if no spec exists, spec is `(stub)`, or spec has blocking open questions. It drafts questions for the user or produces a scoped spec. Only escalate to user with `spec-writer`'s question list (G-spec/AC ambiguity).

If the FEATURE scope includes user-facing UI (screen, component, navigation — not pure logic/network/infra) AND `docs/product/features/<slug>/ux-brief.md` is missing or stale relative to the spec → dispatch `ux-expert` after `spec-writer`. Open UX questions fold back into this gate. The brief is committed as part of the PR.

**Recovery in inner loop.** If the implementing agent halts in Step 5 reporting "UX brief missing" (skip judgement was wrong, or new UI scope emerged mid-plan) — re-dispatch `ux-expert` and resume. Machine-resolvable; do not escalate to user.

Applies to a feature on either track (`track==code` and `track==docs`).

---

## Step 3b — Reproduce + publish cause

**active-when:** `type==bugfix`

**Purpose:** confirm the bug root cause before planning any fix.

Dispatch `bug-reproducer`. It reproduces locally, verifies each hypothesis, and returns a confirmed cause as structured paste-ready text. It does NOT post to GitHub. If reproduction failed or no hypothesis matched → escalate to user (G-bugfix-root-cause).

**Cause-vs-issue divergence.** When `bug-reproducer`'s confirmed cause materially diverges from the issue body (G-cause-vs-issue divergence) — stop. Present the two options per `config.md` §Gate specifics.

**Publication gate.** After receiving a confirmed cause, show the paste-ready block to the user and ask before `gh issue comment <N>` (G-publication of confirmed cause). The confirmed cause becomes a hard constraint for the implementing agent in Step 5 regardless of whether it was published.

---

## Step 4 — Plan

**active-when:** `track==code`

**Purpose:** produce a short implementation plan before any code is written.

Dispatch the Plan agent (or `general-purpose` if unavailable) to produce: phases, files to touch, validation strategy.

**Choosing the fix level.** When the root cause describes a class of bugs or when parallel implementations contain the same defect — consider a fix one level up: a type / container / contract change that makes the class impossible. Compare costs: N point-fixes vs 1 structural fix. If choosing point-fix — list all parallel defective locations in the plan and file a follow-up before coding.

**Issue scope — starting point, not a cage.** If touching adjacent classes or neighbouring platforms is needed for a quality solution — expand scope in this same PR. Whether to fold any finding or defer it — `docs/engineering/scope-discipline.md`. Exception: if forced expansion falls outside the issue's literal **Out of scope** → G-forced-cascade scope.

**Track splitting.** Split into parallel tracks ONLY if the plan can enumerate file-level disjoint sets: track A's files ∩ track B's files = ∅. If any file appears in two tracks → execute sequentially.

**Plan conflicts with guides.** G-plan-vs-guides: present to user and stop.

**Approach-fork empirical gate.** When the task forks into more than one viable implementation — pull the runtime check forward. First weigh options and converge on the most suitable candidate (via `architect` when the choice is non-trivial), implement it, and **verify at runtime under the conditions that distinguish the candidates** before entering the review pipeline. An empirically-confirmed approach earns the investment of review → simplify → full-review. Single-implementation tasks keep the default order — review first, runtime at Step 9.

docs-track: layer ordering is already resolved as `docLayers` at Step 2. No separate plan step.

---

## Step 5 — Inner loop: producers ↔ fast reviewers

**active-when:** always

**Purpose:** iteratively produce and fast-review artifacts until all fast-reviewer findings are resolved.

**Producer dispatch — code-track.** For each track (or sequentially if single):

- UI work (Compose, screens, components, theming, navigation) → `ui-expert`
- Feature spec slice → `spec-writer`
- Non-trivial mechanism / library / structural choice that the coder should not make alone → `architect` first (its one-line decision summary becomes a hard constraint for the subsequent `coder` dispatch). Do NOT dispatch `architect` when the plan is wiring up a pattern an existing ADR or living doc already prescribes, applying a documented mechanism to a new caller, doing docs / prose cleanup, or running an ADR sibling sweep — see `architect.md §When invoked` for the full skip list.
- Everything else → `coder`
- Mixed (non-disjoint files) → `coder`, which can pull in `ui-expert` / `architect` via Agent tool

**Producer dispatch — docs-track.** Follow the ordered layer sequence from `docLayers` (resolved at Step 2). Lower layers depend on upper ones for vocabulary and scope:

1. **spec** (if in docLayers) → `spec-writer`
2. **ux-brief** and **tech-doc / ADR / knowledge** in parallel if both needed (file-disjoint by construction: ux-brief in `docs/product/features/<slug>/`, others in `docs/engineering/` or `docs/knowledge/`)
   - **ux-brief** → `ux-expert`
   - **tech-doc / ADR / knowledge** → `architect`
3. **.claude prompt** → orchestrator writes inline. No sub-agent (an agent editing its own definition would race itself). Match tone and structure of siblings in `.claude/skills/*/SKILL.md`, `.claude/agents/*.md`, `.claude/commands/*.md`. Apply `CLAUDE.md §Code style` and `docs/engineering/long-lived-artifacts.md`. If the prompt change encodes a non-trivial behavioural choice — dispatch `architect` first to converge the choice and produce an ADR; only then write the prompt edit.

**Prose discipline carry-forward.** Every dispatch brief must instruct the producing agent to load `docs/engineering/long-lived-artifacts.md` before writing and apply it to every paragraph.

**Commit before reviewer wave.** Before dispatching the reviewer wave — commit the producer's changes on the branch. Reviewers read `git diff main...HEAD` and the working tree must have no uncommitted changes. One commit per inner-loop iteration. See SKILL.md §The shared engine → Review-wave engine.

**Fast reviewer wave.** Dispatch the `fast` column from [`rosters.md`](rosters.md), filtered by each reviewer's predicate against the current profile and diff. Dispatch in parallel. See SKILL.md §The shared engine → Review-wave engine for fan-out, `[REQUIRED]` aggregation, and delta re-review rules.

**Iteration loop:**

1. Producer(s) produce → commit → fast reviewer wave.
2. If all reviewers `APPROVE` and zero `[REQUIRED]` → step done.
3. Else → aggregate `[REQUIRED]` findings. Dispatch the producing agent with:
   > Previous review found these issues that block the PR. Address each. For each finding, classify as pointwise or structural; for structural findings, do a symmetry pass — check sibling files, sibling methods, sibling platforms, sibling source sets for the same anti-pattern, and fix in this same pass. Do not change anything outside the PR's scope.
   >
   > \<list of [REQUIRED] findings with file:line\>

   Apply review transmission accuracy rules from SKILL.md §The shared engine.

   **Red CI test = broken code, not broken test.** Fix the code. Deleting a failing test, weakening assertions/timeouts/inputs — forbidden without explicit user approval. Hypothesis "the test was checking the wrong thing" → escalate to user.

   Go back to step 1.

**Iteration limit.** From [`config.md`](config.md) §Iteration limits: 4 for code-track, 2 for docs-track. If not converged → escalate to user with remaining findings; signals a plan/scope problem the loop cannot fix.

---

## Step 6 — Simplify wave

**active-when:** `track==code`

**Purpose:** remove scaffolding accumulated during iterative fix cycles.

After all code-track inner loops converge, dispatch the implementing agent once more:

> All findings are resolved. Make one simplification pass over the diff: remove dead branches, inline single-use helpers, collapse trivial wrappers.
> **For every comment / KDoc / prose paragraph in the diff — including `.claude/skills/**`, `docs/`, and Markdown — apply CLAUDE.md §Code style and `docs/engineering/long-lived-artifacts.md`.** A sentence may be left only if it carries non-obvious context or further instructions. Remove: history of implementing or decision-making (except ADRs); mentions of things that do not exist in the artifact (a feature considered and dropped, a button we decided not to add) unless their absence is itself a non-obvious invariant a reader would otherwise assume; repetition of a fact already stated nearby.
> **Do not rephrase prose for brevity.** If a sentence is load-bearing and free of the issues above, leave its wording alone. Cut whole sentences when they fail the rule above; otherwise keep them as written. Word-count reduction on well-formed sentences is not a goal.
> Do not change behavior; do not touch anything outside the diff. Run `./gradlew allTests -q` after.

If anything was simplified — commit the simplification, then re-review the simplified diff with a **delta set**: `review-reuse` always, plus only the reviewers whose domain the simplification touched (`review-correctness` / `review-tests` if logic moved; `review-design-system` / `review-visual` if Compose changed). The authoritative full-roster pass is Step 8, immediately after — do not duplicate it here.

docs-track: inactive. Today's docs-track has no simplify step.

---

## Step 7 — Consistency pass

**active-when:** `track==docs`

**Purpose:** cross-cutting verification of produced doc artifacts before the review wave.

After all sub-agents return clean (no open questions), run an inline read-only pass over the produced artifacts. Check, in order:

1. **Cross-references resolve.** Every link between artifacts (spec → ux-brief, spec → tech-doc, tech-doc → ADR, ADR → parent living doc) points to a file that exists and a section that exists.
2. **Terminology consistent.** Sample the central entities across all touched artifacts. Same concept = same name everywhere. Variation = either mechanical rename or G-cross-doc inconsistency.
3. **Scope cohesion.** For each artifact: does every section depend on the central invariant of this artifact's feature/subsystem? Sections describing concepts that survive without that invariant belong to a different artifact. Mechanical move = do it; concept-level scope dispute = G-cross-doc inconsistency.
4. **ADR parent-living-doc invariant.** If an ADR was created, the parent living doc exists and is referenced from the ADR Context section. If missing — dispatch `architect` to add the parent doc in this same pass.
5. **Indexes updated.** `docs/product/features/README.md` row added/updated if a spec was touched; `docs/engineering/README.md` entry added if a living doc or ADR was created.
6. **Relocation completeness.** When the diff removes or moves a decision / section out of a doc, verify: (a) it is homed somewhere in canon — not simply deleted; (b) every inbound link to the removed anchor is repointed (`grep -rn '<old-file>.md#<anchor>' docs/ *.md`). Missing home = G-cross-doc inconsistency; dangling link = mechanical fix, apply directly.

Mechanical fixes (rename, add missing link, add missing index row) — apply directly. Conceptual fixes → G-cross-doc inconsistency → route to owning sub-agent.

code-track: inactive.

---

## Step 8 — Full pre-PR review (Wave A + adversarial)

**active-when:** always

**Purpose:** full-roster pre-PR review before the branch is pushed. This is not `/code-review` — that skill requires an existing PR and posts via `gh pr review`. Here the PR does not exist yet. Findings are consumed locally only.

Before Wave A — working tree must be clean (committed). If there are uncommitted edits after Step 6 or Step 7 — commit them. Reviewers read `git diff main...HEAD`.

**Wave A.** Dispatch the `full-A` column from [`rosters.md`](rosters.md) in parallel, filtered by each reviewer's predicate. Each agent reviews the local working tree (`git diff main...HEAD`). Pass the issue number and the relevant brief path (for `review-ux-conformance`).

If the PR establishes or extends canon (docs-track), tell each reviewer to apply the new rule to the diff itself.

**Wave B.** Dispatch `review-adversarial` with the combined Wave A findings as input.

**Iteration.** Aggregate `[REQUIRED]` findings. Apply via the producing agent (with the symmetry-pass instruction). Invokes the review-wave engine from SKILL.md §The shared engine — including delta re-review rules and the iteration limit from [`config.md`](config.md) §Iteration limits (2 for both tracks).

---

## Step 9 — Runtime verification

**active-when:** `track==code`

**Purpose:** verify that the deliverable actually works at runtime or that a new enforcement mechanism is wired in.

Two branches, not mutually exclusive. See [`config.md`](config.md) §Runtime verification recipe for routing tables.

### 9a — Smoke (feature behaviour)

When the deliverable is runtime feature behaviour (user path, network exchange, lifecycle). Run `/smoke-test` blocks for the diff-touches rows in `config.md`. If the PR introduces a new critical happy-path not covered by smoke — extend `.claude/skills/smoke-test/SKILL.md` in this same PR before running.

### 9b — Enforcement probe (static check is wired in)

When the deliverable is the enforcement mechanism itself (custom lint rule, CI guard, git hook, custom Gradle check, ktlint/detekt rule, schema validator). Unit tests do not prove the mechanism is wired in via ServiceLoader / Gradle / hook chain — inject through the same door a real violator would use. Steps are in `config.md` §Runtime verification recipe.

### Verdict

Record verdict (🟢/🟡/🔴) per branch run, plus blocks/probe path. If any branch is 🟡/🔴 → G-smoke/probe red: present to user and stop.

docs-track: inactive.

---

## Step 10 — Land (commit, push, PR, summary)

**active-when:** always

**Purpose:** commit, push, create the PR, present the final summary to the user.

Only after Step 8 is 🟢 and (code-track) Step 9 is 🟢.

```bash
git add <relevant files>
git commit -m "#<N>: <message>"
git push -u origin <branch>
gh pr create --title "<title>" --body-file <path>
```

Read the PR template at the path in [`config.md`](config.md) §PR template path before composing the body. Write the complete body to a temp file and pass `--body-file` — never `--body` with an in-shell-built string, which silently corrupts multiline markdown (a dropped `Closes #<N>` then leaves the issue open after merge).

`Closes #<N>` is required. `👀 Sanity-check` must list every defer-decision made during the run (skipped scope, TODO/FIXME left in diff, follow-up issue planned, open question parked, layer skipped). Do not bury defer-decisions at the bottom; they belong in `Sanity-check` so the user can redirect defer-vs-do-now from that section. Add smoke verdict + `## Dependency check` (code-track) as trailing sections only when non-trivial.

**code-track** final summary to user:
- PR URL.
- Files changed (summary).
- AC: all `[DONE]` (from `review-dod`).
- Smoke: 🟢 with blocks executed.
- Any `[UNVERIFIABLE]` from reviewers.
- Manual test plan — 1–2 sentences focused on regression smoke and shipping behaviour; explicitly say "backend-only, nothing to test manually" if there is no visible diff.

**docs-track** final summary to user:
- PR URL.
- Layers produced and artifact paths.
- DoD verdict: all `[DONE]` (from `review-dod`).
- Any `[UNVERIFIABLE]` from reviewers.

**On re-entry:** commit into the existing branch, no force-push, do not create a new PR. Reply to every addressed inline comment per Step 0.

---

## Gate catalog

Each gate below has its own `active-when` and specifics in [`config.md`](config.md) §Gate specifics.

| Gate | active-when | What triggers it |
|---|---|---|
| G-spec/AC ambiguity | `type==feature` | Missing/stub spec, blocking open questions, or open UX questions |
| G-bugfix-root-cause | `type==bugfix` | Reproduction fails or no hypothesis matched |
| G-cause-vs-issue divergence | `type==bugfix` | Confirmed cause materially differs from issue body |
| G-publication of confirmed cause | `type==bugfix` | Before posting confirmed cause to GitHub |
| G-plan-vs-guides | `track==code` | Plan conflicts with engineering guides |
| G-forced-cascade scope | `track==code` | Forced change outside issue's literal Out of scope |
| G-cross-doc inconsistency | `track==docs` | Contradiction between artifacts needing a product/technical decision |
| G-sub-agent open question | always | Sub-agent returns a question it could not converge on |
| G-smoke/probe red | `track==code` | Smoke verdict not 🟢, or enforcement probe passes green |
| G-final summary | always | After loop converges — commit, push, PR, present summary |
