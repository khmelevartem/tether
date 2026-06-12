---
name: implement
description: Issue-to-PR orchestrator. Detects docs-only issues and delegates to `/document`; for code-track plans, dispatches coder / ui-expert / architect, runs implementer↔reviewers loop, smoke, lands a PR. Stops for user only at human-required gates (spec/AC ambiguity, BUGFIX root-cause uncertainty, plan conflicts with guides, smoke red). Use when starting work on an issue.
---

# /implement — Issue-to-PR orchestrator

You are the orchestrator for implementing a single GitHub issue. You do NOT write code or review code yourself. You dispatch sub-agents and decide when to escalate to the user.

**Goal:** remove the user from the inner `code → review → fix → review` cycle. The user is consulted at human-required gates only.

## Input

Issue number `<N>`.

## Re-entry contract

The skill is idempotent per issue. At each invocation, first check `gh pr list --search "issue:#<N>" --state open`:

- **No PR** → start Step 1.
- **PR exists and is open** → you are in a pull-request feedback iteration. **Before anything else, gate on main drift:**

  ```bash
  git fetch origin main --quiet
  git merge-base --is-ancestor origin/main HEAD && echo up-to-date || echo behind
  ```

  If `behind` → run `/pull-main` and adjust to whatever it brought before classifying comments or running reviewers. Otherwise iterating on stale canon — incoming PRs may have shifted rules under your feet, and the review wave will run against a main that no longer matches the project's current canon. If `up-to-date` → skip.

  Then re-check the docs-only detection (Step 1 classification) on the current state of the issue + PR diff: if the task is docs-only, delegate re-entry to `/document <N>` and exit (`/document` is itself idempotent and will pick up this PR). Otherwise stay in the code-track re-entry.
- **Code-track re-entry.** The current feature branch may have new reviewer comments or commits since the last run. Read **all** human comments on the PR (`gh api repos/<owner>/<repo>/pulls/<PR>/comments` + `gh pr view <PR> --comments`) and for each determine its status: addressed in commits after it — or not. **Creation date does not determine relevance** — filtering comments by `created_at > <date-of-previous-run>` is forbidden, because an unaddressed comment remains relevant regardless of how old it is. The run **must** include on a fresh diff: Step 4 (inner loop reviewers) → Step 5 (simplify) → Step 6 (full review wave A + adversarial) → Step 7 (smoke, scoped to diff). Nothing from the re-entry discipline may be skipped — otherwise review iterations run at lower quality than the initial implementation.

Step 8 (commit + push + final summary) is simplified on re-entry: the commit goes into the existing branch, force-push is not needed, do not create a new PR.

**After push on re-entry — reply to every addressed inline comment** via `gh api -X POST repos/<owner>/<repo>/pulls/<PR>/comments/<comment_id>/replies -f body="<reply>"`. For each comment: what was done + the commit SHA (or explicit reasoning if the comment was deliberately declined). Without a reply the reviewer cannot see the loop closed and the thread stays "hanging"; the next re-entry will read it again as unaddressed and uselessly re-run the inner loop. A reply is the "addressed" signal, not a courtesy.

### Re-entry routing — by-agent attribution

When a PR comment is scoped to work produced by a specific upstream agent (architectural decision, UX brief, UI implementation, spec, etc.), route the comment back to that agent first — not to the coder. The agent owns its work surface; the coder applies the resulting decision.

Attribution heuristics:
- Comment objects to a layering / placement / dependency-direction / mechanism choice → `architect`.
- Comment objects to a spec gap, AC scope, or product framing → `spec-writer`.
- Comment objects to a screen / interaction / state-flow decision → `ux-expert`.
- Comment objects to UI rendering, theme, accessibility specifics → `ui-expert`.
- Comment objects to a glossary / docs entry that the architect wrote → `architect`.
- Comment is a pointwise correctness or style issue with no architectural element → `coder` via the existing inner-loop path.

The originating agent returns its revised work as a chat summary to the orchestrator. The orchestrator then decides next steps: dispatch `coder` to apply the revision, dispatch `ui-expert` for a re-render, re-run reviewers, or escalate to the user when the revision changes scope (new top-level types, layer crossings, deleted contracts). A structural revision is not an inner-loop iteration on the coder's output; running it through the coder skips the agent who owns the decision and risks repackaging the comment incorrectly (see `## No-deflection principle` §Review transmission accuracy).

## No-deflection principle

When someone — the user or a reviewer — asks a question about an artifact or demands a change, the answer must be **substantive**: either a justification for why the artifact stays as-is, or a genuine edit to the artifact itself. Intermediate actions — precautionary destruction, a half-fix, justifying via KDoc / comment / documentation instead of changing the code — are deflection. Forbidden.

Two manifestations, both in the inner loop:

**1. User question mid-loop.** A message like "is X really needed?" / "why X?" / "wouldn't A be better than B?" / "maybe extract to Y?" / "is nullable mandatory here?" — this is a request for your judgement, not a directive. Default:

- **Justify** — what the artifact provides that the alternative lacks, or why the constraint is warranted.
- **Counter-clarify** — "remove X entirely or are you unsure about Y inside X?", "extract externally or rename?".

Silently performing the assumed action (delete, move, rewrite, change type, unwrap nullable) on a single question is forbidden. If the user wanted an action, they would have written it directively. A question deserves an answer first; the directive will follow.

**2. Coder's response to a reviewer finding.** If the coder addressed a finding of "remove X" / "delete Y" by making an edit that defends X via KDoc / comment / a paragraph in docs (instead of actually deleting / replacing / reworking) — that is deflection; reject it. The reviewer wanted X removed, not documented. Re-dispatch the coder with the explicit wording "change X itself, without justifying it through documentation".

## Gate semantics — when to stop and ask the user

These MUST-stop gates are **not overridden by session-level autonomy or "skip clarifying questions" hints**, wherever such hints come from. Such hints apply only to execution-stage trivia within an already-agreed scope (naming, formatting, refactoring choices). They do not apply to gate evaluation. The cost of a one-message pause is far lower than the cost of unwinding a unilateral architectural / product decision.

You MUST stop and ask the user in these cases (and only these):
- **Spec or AC ambiguity** — issue's DoD is missing/stub, feature spec missing for FEATURE type, blocking open questions in spec. **Mitigation:** dispatch `spec-writer` first; only stop at user if `spec-writer` has clarifying questions or the issue is non-FEATURE without DoD.
- **BUGFIX root cause** — root cause must be confirmed before any fix. **Mitigation:** dispatch `bug-reproducer`; only stop at user if it reports CANNOT REPRODUCE or none of the listed hypotheses match. **The reproducer must always attempt to observe the symptom**, even when the cause looks structurally evident from issue text + code grep. Do not silently proceed as if the bug were confirmed.
- **Cause-vs-Issue divergence** — when `bug-reproducer`'s confirmed cause materially diverges from what the issue body claims (different mechanism / different platform scope / different observable symptom / different severity class), STOP and ask the user to choose: «close #N as misdiagnosis and open a new issue with the real cause» vs «rewrite #N body to match the confirmed cause». Do not silently edit the issue body and continue — that loses the trail of how the diagnosis evolved, and it bundles two different bugs (the one reported, the one found) into one PR's history.
- **Publication of confirmed cause** — once `bug-reproducer` returns a confirmed cause, show the paste-ready block to the user and wait for explicit OK before `gh issue comment`. Publishing to a GitHub issue is a team-visible action; it does not happen without a user gate.
- **Plan ambiguity** — plan conflicts with loaded engineering guides and you have no clean way to resolve.
- **Forced-cascade scope expansion** — a change is technically forced (by DoD wording, by not-breaking shipped behaviour, or by repairing verification infra) but falls outside the issue's literal **Out of scope**. "Forced" decides *what*, not *which PR*. Stop and ask the user: **fold** into this PR, **split** (narrow this PR to the kernel, file a follow-up), or **re-frame** the issue. Not a heads-up.
- **Smoke red/yellow** — smoke verdict is not 🟢 after the inner loop.
- **Final summary to the user** — after the inner loop converges to APPROVE and smoke is green, commit + push + create the PR, then present the PR URL with a short summary (files changed, AC verdict, smoke verdict, any `[UNVERIFIABLE]` findings). The user reviews on GitHub; do not block on explicit OK before push. Before push, verify PR body follows [`.github/pull_request_template.md`](../../../.github/pull_request_template.md): `Closes #<N>` present; every defer-decision made during implementation (skipped scope, TODO/FIXME left in code, follow-up issue planned) appears in `👀 Sanity-check`, not buried at the bottom — user redirects defer-vs-do-now from this section.

Everything else — implementation details, reviewer findings, fix iterations — you handle internally without the user.

## Step 1 — Reconnaissance and setup

```bash
gh issue view <N> --json title,body,labels,comments
gh pr list --search "issue:#<N>" --state open --json number,isDraft,headRefName
```

**Sweep the repo for prior mentions of `#<N>`** — `grep -rn "#<N>" .` over the working tree. Every hit is either resolved in this PR, or escalated to the user as "can't do here — move to #M?". Never silently leave a `TODO(#<N>)` after merge.

**Comments are not a discussion — they are potentially a canon-update body.** When a comment conflicts with the body — the comment takes priority; escalate to the user in one line.

**Critical reading.** Treat the issue description as a **starting point, not a fact**. Flag and escalate to the user before starting work if you see at least one gap:
- only one platform is mentioned, yet the task is cross-platform;
- errors and fail-paths are not described;
- it is unclear how to test (no edge cases / runtime check guidance);
- BUGFIX without at least a hypothesised bug cause;
- the deliverable's benefit rests on an assumption not yet established, or it carries a recurring per-run cost (tooling / process / CI / hook / inter-agent-protocol change) that may outweigh that benefit — weigh cost against benefit and gate on the assumption with the user before sinking design or implementation effort;
- filler phrases: "fill in if you have something", "should work correctly", "and so on".

Any such gap is a reason to return to the spec/AC ambiguity gate, not to "patch it along the way".

**Classification.** Based on the issue's type label (`feature` / `bugfix` / `refactor` / `infra` / `docs` / `dependency`), deliverable description, and any legacy `**Type:**` body field on older issues — assign the task to one of these tracks:

| Trigger | Track | Action |
|---|---|---|
| `docs` label | docs-only | delegate to `/document <N>` and exit |
| `feature` + explicit docs-only marker (phrase "docs-only" / "only docs" / "scope: docs" in body/DoD, or label `docs-only`) | docs-only | same |
| Issue without a type label AND deliverable **limited exclusively** to editing `.claude/` or `docs/` (no code in source sets) | docs-only | same |
| `infra` AND deliverable **limited exclusively** to editing `.claude/` files (skill prompts, agent definitions, hooks) | docs-only | same |
| `feature` / `bugfix` / `refactor` / `infra` with deliverable in source sets or build/CI/scripts (even if an ADR is also needed) | code-track | continue Steps 2–8 |

Legacy issues may carry the type in a `**Type:**` body field, or under the GitHub default labels `enhancement` (= `feature`) / `bug` (= `bugfix`) / `documentation` (= `docs`) — treat all three as equivalent to the matching type label for routing.

When delegating to /document: "This task is docs-only. Running `/document <N>` and exiting." `/document` will handle layer selection, consistency, review and the PR itself. **Do NOT delegate** a code-FEATURE with an incidental ADR — for such tasks Step 4 dispatches `architect` mid-flight and the ADR is written in the same PR as the code.

### Doc discovery

Before planning and any dispatch, dispatch ONE read-only recon agent (`Explore`) to sweep the doc corpus and return a compact digest of binding constraints — do NOT read the corpus into the orchestrator thread yourself. Filenames are designed for topic-match, so the sweep is cheap; the cost to avoid is the full doc contents landing in the orchestrator's context, where they are re-read on every later turn. The orchestrator holds the digest and reads a specific doc verbatim later only when a gate decision needs the exact text.

Brief for the recon agent (pass the issue title + body):

> Read-only sweep for issue #<N>. Return a compact digest — binding constraints and relevant paths, no file dumps:
> - **Product features** — `ls docs/product/features/` (+ `README.md` index). Slug(s) matching this issue's scope; the binding constraints from each `spec.md` / `ux-brief.md` in 1-2 lines.
> - **Product context** — `docs/product/*.md` (vision, audience, roadmap, tech stack, security). The framing that binds this issue's scope / audience / timing.
> - **Engineering living docs** — `docs/engineering/*.md`. The present-tense rules whose topic matches the task.
> - **ADR** — `docs/engineering/adr/adr-*.md`. ADRs matching the topic; for each, its **Revisit if** section and whether this task trips a trigger.
> - **Knowledge** — `docs/knowledge/*.md`. Solved-problem notes relevant to the task.
> - **Glossary** — `docs/glossary.md`. The terms this issue's domain touches, with their locked definitions (load-bearing — `review-glossary` blocks drift).

For each ADR the digest flags as trigger-tripped, the plan either confirms the ADR (false trigger) or includes a reversal with its own sub-plan (see `docs/engineering/adr/README.md` §Reversing an ADR). `CLAUDE.md` is harness-injected — not part of the sweep.

Mention the relevant documents the recon agent surfaced in the briefing to the user (see below).

**Worktree setup — do this BEFORE dispatching any agent that edits files.** If you are not already in `.claude/worktrees/<branch>/`:

```bash
git fetch origin main --quiet
git worktree add .claude/worktrees/feature-<N>-<short-slug> -b feature/<N>-<short-slug> origin/main
cd .claude/worktrees/feature-<N>-<short-slug>
```

Branch from `origin/main`, never local `main` — a stale base produces avoidable mid-flight rebase conflicts. All subsequent agent dispatches happen with this as cwd. Skipping this step means `spec-writer` would edit main checkout.

### Briefing back to the user

After reading the issue and doing recon, **before** any question to the user (gate questions, open questions from sub-agents, classification ambiguities) — post a short 3–6 line briefing in chat: what we are doing, why (motivation / context from the issue), classification (track + affected layers/platforms). If you are asking questions in the same message — attach 1–2 lines of context to each (what the issue says, what options are on the table), so the user can answer without going to GitHub to re-read the body. One briefing per run; do not repeat on re-entry.

## Step 2 — Resolve early gates

### Spec / UX brief / AC ambiguity

If FEATURE and (no spec, or spec is `(stub)`, or spec has blocking open questions) → dispatch `spec-writer`. It will draft questions for the user or produce a scoped spec. Only escalate to user with `spec-writer`'s question list.

If the FEATURE scope includes user-facing UI (screen, component, navigation — not pure logic/network/infra) AND `docs/product/features/<slug>/ux-brief.md` is missing or stale relative to the spec → dispatch `ux-expert` after `spec-writer`. It produces or updates the brief; `ui-expert` later consumes it as a contract. Open UX questions returned by `ux-expert` fold back into this gate: surface verbatim to the user, collect answers, re-dispatch. The brief is committed as part of the PR.

**Recovery in inner loop.** If `ui-expert` halts in Step 4 reporting "UX brief missing" (the skip judgement was wrong, or new UI scope emerged mid-plan) — re-dispatch `ux-expert` and resume. This is machine-resolvable; do not escalate to user.

### BUGFIX root cause

If BUGFIX → dispatch `bug-reproducer`. It reproduces locally, verifies each hypothesis, and returns a confirmed cause as structured paste-ready text. It does NOT post to GitHub. If reproduction failed or no hypothesis matched → escalate to user.

### Publication of confirmed cause

After receiving a confirmed cause from `bug-reproducer`, show the paste-ready block to the user and ask: "Publish as a comment on issue #<N>?" Wait for explicit OK before `gh issue comment <N>`. Reason: a team-visible side effect must not happen without an explicit gate, even if the orchestrator is doing it instead of the agent — that just moves the problem one level up. If the user says no — keep the cause locally as a constraint for `coder`; do not publish.

The confirmed root cause becomes a hard constraint for the `coder` in Step 4 regardless of whether it was published.

## Step 3 — Plan

Use the built-in `Plan` agent (or `general-purpose` if plan unavailable) to produce a short implementation plan: phases, files to touch, validation strategy.

**Choosing the fix level.** The issue identifies where the bug manifests, not necessarily where to fix it. When the root cause describes a class of bugs (not a single instance) or when parallel implementations contain the same defect — consider a fix one level up: a type / container / contract change that makes the class of bugs impossible. Compare costs: N point-fixes vs 1 structural fix. If you choose point-fix — explicitly list in the plan the parallel locations that remain defective, and file a follow-up issue before starting coding.

**Issue scope — starting point, not a cage.** The file list in the issue is a starting point. If touching adjacent classes or neighbouring platforms is needed for a quality solution — expand scope in this same PR. A follow-up issue only when expansion genuinely breaks the PR (new target, broad public contract edit, multiplicative volume growth, discovery of a separate bug). Notes / TODOs the implementer added along the way — finish them here.

**Exception — forced cascade outside literal scope.** When the expansion is forced but falls outside the issue's literal **Out of scope** — route through the `Forced-cascade scope expansion` gate (§Gate semantics), do not silently fold.

**Track splitting.** Default is **sequential single-track** execution. Split into parallel tracks ONLY if the plan can enumerate file-level disjoint sets: track A's files ∩ track B's files = ∅. The plan must list explicit file paths per track. If any file appears in two tracks → tracks are not independent → execute sequentially.

**Plan conflicts with guides.** If the plan conflicts with loaded engineering guides → present to user, stop. Otherwise, accept and continue.

**Approach-fork empirical gate.** When the task forks into more than one viable implementation — bugfix fix-hypotheses, or any design choice with several candidate mechanisms — first weigh the options and converge on the most suitable one (via `architect` when the choice is non-trivial), then implement *that* candidate and **verify it at runtime under the conditions that distinguish the candidates** — notably each affected OS / platform — before spending the review → simplify → full-review pipeline on it. Only an empirically-confirmed approach earns that investment. Static review, including the adversarial pass, reasons about code in the abstract; an approach whose correctness rests on platform or third-party-library runtime behaviour must be *run* to be trusted, and running it early turns a wrong approach into a cheap pivot instead of a full implement-and-revert cycle. This pulls the runtime check (Step 7) forward for fork tasks; single-implementation tasks keep the default order — review first, runtime verification at Step 7.

## Step 4 — Inner loop: coder ↔ fast reviewers

Per track (or sequentially if single track):

**Iteration:**

1. Dispatch the implementing agent with the plan slice:
   - **UI work** (Compose, screens, components, theming, navigation) → `ui-expert`
   - **Feature spec** → `spec-writer`
   - **Architectural design point** — plan from Step 3 surfaces a non-trivial mechanism / library / structural choice that `coder` should not make alone → `architect` first. It converges the choice (its own palette + trade-off questions it surfaces for you to relay to the user; ADR/living doc only when the orchestrator's brief explicitly asks and the user has approved), returns a one-line decision summary; that summary then becomes a hard constraint for the subsequent `coder` dispatch in the same track. **Do NOT dispatch architect** when the plan is wiring up a pattern an existing ADR or living doc already prescribes, applying a documented mechanism to a new caller, doing docs / prose cleanup, or running an ADR sibling sweep — see [architect.md §When invoked](../../agents/architect.md#when-invoked) for the full skip list. Routine FEATURE / REFACTOR work that follows the existing canon goes straight to `coder`.
   - **Everything else** (network, discovery, protocol, persistence, build, infra) → `coder`
   - **Mixed** — split into sub-tracks if disjoint files, else dispatch `coder` which can pull in `ui-expert` / `architect` via Agent tool.

   **Prose discipline carry-forward.** When the plan slice includes prose edits (specs, KDocs, comments, READMEs, ADRs, `.claude/` prompts), the dispatch brief must instruct the implementing agent to load [`docs/engineering/long-lived-artifacts.md`](../../../docs/engineering/long-lived-artifacts.md) before writing and apply it to every paragraph.
2. **Before dispatching the reviewer wave — commit the coder's changes** on the feature branch (a new commit or `--amend`, your call). Reviewers read `git diff main...HEAD` and **the working tree must have no uncommitted changes** at the time they run. Otherwise some agents read only the committed state and send stale [REQUIRED] flags for problems already fixed but not yet visible to them — the orchestrator wastes context parsing phantom flags, plus there is a risk of false-blocks. One source of truth = one commit per inner-loop iteration.
3. Dispatch a **fast reviewer wave** in parallel:
   - `review-dod` (always)
   - `review-correctness` (always unless DOCS/REFACTOR)
   - `review-guides` (always)
   - `review-glossary` (always)
   - `review-architecture` (always unless DOCS or trivial one-call-site BUGFIX / cosmetic refactor)
   - `review-tests` (always unless DOCS/INFRA)
   - `review-platform` (if diff touches platform source sets)
   - `review-ux-conformance` (only if diff touches `composeApp/src/**` AND a touched feature has a `ux-brief.md`. Resolve the slug from the issue — a spec link or `docs/product/features/<slug>/` reference in the body, else glob `docs/product/features/**/ux-brief.md` and topic-match the changed paths — and pass the brief path in the prompt; don't dispatch when no brief exists. The orchestrator owns this gate so the agent isn't launched only to self-skip. Pre-PR here: resolve from the issue number, not `gh pr view`.)
   - `review-ux-brief` (if diff touches `docs/product/features/**/ux-brief.md` — judges the brief's UX-domain quality)
   - `review-design-system` (if diff touches `composeApp/src/**`)
   - `review-visual` (if diff touches `composeApp/src/**` — the agent itself renders PNGs via Roborazzi; a missing brief narrows its checklist but does not skip it)
   Skip `review-reuse` and `review-adversarial` here — they run in the simplify wave (Step 5) and the full review (Step 6).

   **Delta re-review (iterations 2+).** The full wave above runs on the first iteration to establish a baseline. On every later iteration re-dispatch only: (a) reviewers that raised a `[REQUIRED]` finding the previous round, and (b) reviewers whose domain the new changes touch (a fix that adds Compose pulls in `review-design-system` / `review-visual` even if they were silent before). A reviewer that returned `APPROVE` on code its domain did not change this round returns the same verdict — re-running it spends tokens to re-derive a known result. Track each reviewer's last verdict and whether its domain was touched; that pair decides re-dispatch. Full-roster coverage is restored at Step 6.
4. If every reviewer says `APPROVE` and zero `[REQUIRED]` → track done.
5. Else → aggregate `[REQUIRED]` findings, dispatch the implementing agent again with the findings as input. Apply the same commit-before-review discipline as step 2:

> Previous review found these issues that block the PR. Address each. For each finding, classify as pointwise or structural; for structural findings, do a symmetry pass per your agent definition — check sibling files, sibling methods, sibling platforms, sibling source sets for the same anti-pattern, and fix in this same pass. Do not change anything outside the PR's scope.
>
> <list of [REQUIRED] findings with file:line>

   **Red CI test = broken code, not broken test.** Default — fix the code. Deleting a failing test, rewriting it as a narrower fast-check, weakening assertions/timeouts/inputs — all forbidden without explicit user approval. Hypothesis "the test was checking the wrong thing" — escalate to the user, do not resolve independently.

   **Review transmission accuracy.** The coder receives context cold and does not verify the orchestrator — if you repackaged "remove X" as "justify X via KDoc", the coder will do exactly the latter. Pass findings as close as possible to the reviewer's original wording; do not narrow or soften. If several findings converge on one principle — name the principle explicitly in the instruction and list ALL sites where it applies, even if not all were mentioned in comments. If you are unsure about interpretation — escalate to the user BEFORE dispatching, not after the next review round. See also `## No-deflection principle` — a coder's defensive-via-docs response to "remove X" is rejected by the same rule.

   Go back to step 2.

**Iteration limit:** 4 inner iterations per track. If not converged after 4 — escalate to user with remaining findings; this signals a plan/scope problem the loop cannot fix.

## Step 5 — Simplify wave

After all tracks converge. Iterative fix cycles accumulate scaffolding (temp helpers added then never removed, defensive branches, comments restating code) AND duplication (each iteration adds private helpers that fast reviewers don't cross-check across tracks).

Dispatch the implementing agent once more:

> All findings are resolved. Make one simplification pass over the diff: remove dead branches, inline single-use helpers, collapse trivial wrappers. 
> **For every comment / KDoc / prose paragraph in the diff — including `.claude/skills/**`, `docs/`, and Markdown — apply CLAUDE.md §Code style and [`docs/engineering/long-lived-artifacts.md`](../../../docs/engineering/long-lived-artifacts.md).** A sentence may be left only if it carries non-obvious context or further instructions. Remove: history of implementing or decision-making (except ADRs); mentions of things that do not exist in the artifact (a feature considered and dropped, a button we decided not to add) unless their absence is itself a non-obvious invariant a reader would otherwise assume; repetition of a fact already stated nearby.
> **Do not rephrase prose for brevity.** If a sentence is load-bearing and free of the issues above, leave its wording alone. Cut whole sentences when they fail the rule above; otherwise keep them as written. Word-count reduction on well-formed sentences is not a goal.
> Do not change behavior; do not touch anything outside the diff. Run `./gradlew allTests -q` after.

If anything was simplified — **commit the simplification** (see Step 4 discipline — reviewers read only the committed diff), then re-review the simplified diff with a **delta set**: `review-reuse` always (duplication is what most likely accumulated across iterations and tracks — this is the gate's whole point), plus only the reviewers whose domain the simplification actually touched (`review-correctness` / `review-tests` if logic moved; `review-design-system` / `review-visual` if Compose changed). The authoritative full-roster pass is Step 6, immediately after — do not duplicate it here. If clean, proceed.

## Step 6 — Full pre-PR review (inline, not via /code-review skill)

`/code-review` skill requires an existing PR (it posts via `gh pr review`). At this step the PR does not exist yet — Step 8 creates it. So instead of calling the skill, **orchestrate the same agent fan-out inline, without GitHub publication**:

1. Before Wave A — the working tree must be clean (committed). If there are uncommitted edits after Step 5 — commit them. Reviewers read `git diff main...HEAD`; uncommitted state causes stale-view findings.
2. Wave A in parallel: `review-dod`, `review-guides`, `review-glossary`, `review-reuse`, plus (if applicable to PR type / diff) `review-architecture`, `review-correctness`, `review-tests`, `review-platform`, `review-ux-conformance`, `review-ux-brief`, `review-design-system`, `review-visual`. Each agent receives the issue number and is told to review the local working tree (`git diff main...HEAD`) instead of a PR. `review-design-system` and `review-visual` run whenever the diff touches `composeApp/src/**`; `review-ux-conformance` runs only when Compose is touched **and** the orchestrator resolved a touched feature to an existing `ux-brief.md` (pass the brief path); `review-ux-brief` runs whenever the diff touches any `docs/product/features/**/ux-brief.md`.
3. Wave B: `review-adversarial` with the combined Wave A findings as input.
4. Aggregate. Apply any `[REQUIRED]` via the implementing agent (with the symmetry-pass instruction). Re-run until approved or 2 iterations.

No `gh pr review` here — findings are consumed locally only. The post-PR `/code-review` skill will be invoked separately after Step 8 if a reviewer requests it, or as part of normal team review.

## Step 7 — Runtime verification (smoke OR enforcement-probe)

### Smoke verdict

Two branches. Chosen by the nature of the deliverable, not mutually exclusive — for a PR that changes both a feature and an enforcer, do both.

### 7a — Smoke (feature behavior)

When the PR deliverable is runtime feature behaviour (user path, network exchange, lifecycle).

Run `/smoke-test` blocks relevant to the diff:

| Diff touches | Run |
|---|---|
| `FileServer` / `FileClient` / CLI / network protocol | Desktop CLI + Desktop↔Desktop blocks |
| Android FGS / mDNS / Android networking | Android block (if device attached) |
| native source sets (`iosMain`, `appleMain`) | native compile block |
| DOCS-only, `.claude/`-only, comments-only | nothing |
| Other production code | judgement call — when in doubt, run Desktop blocks |

If the PR introduces a new critical happy-path not covered by smoke (start-time failure point, cross-platform UI, new external interface) — extend `.claude/skills/smoke-test/SKILL.md` in this same PR before running. Keep blocks lean; smoke runs often.

### 7b — Enforcement probe (static check is wired in)

When the PR deliverable is the enforcement mechanism itself (custom lint rule, CI guard, git hook, custom Gradle check, ktlint/detekt rule, schema validator). Unit tests of the mechanism do not prove it is wired in via ServiceLoader / Gradle / hook chain — an injection is needed through the same door a real violator would use.

Steps:
1. Create a minimal artifact violating the check in a real location in the codebase (where the enforcer should fire — `src/.../Test.kt` for a ktlint test rule, `.github/workflows/` for a CI guard).
2. Run the corresponding Gradle/CI task (`./gradlew ktlintCheck`, `./gradlew <task>`, `git commit` for a pre-commit hook).
3. Verify: build FAILED **with the expected message** (rule id / hook name).
4. Delete the probe; confirm via `git status -s` that nothing remains.

If step 3 passes green — the enforcer is not wired in, despite green unit tests. This is a red gate; escalate to the user.

### Verdict

Record the verdict (🟢/🟡/🔴) per branch run, plus blocks/probe path.

If any branch is 🟡/🔴 → present to user, stop.

## Step 8 — Commit, push, PR (final summary)

Only after Step 7 is 🟢. Commit on the feature branch, push, create the PR:

```bash
git add <relevant files>
git commit -m "#<N>: <message>"
git push -u origin feature/<N>-<short-slug>
gh pr create --title "<title>" --body "<...>"
```

Read [`.github/pull_request_template.md`](../../../.github/pull_request_template.md) before composing the body. `Closes #<N>` is required. `👀 Sanity-check` must list every defer-decision (skipped scope, TODO/FIXME left in diff, follow-up planned). Add smoke verdict + `## Dependency check` (if new deps) as trailing sections only when non-trivial; for green smoke and no new deps, omit.

Report to the user:
- PR URL.
- Files changed (summary).
- AC: all `[DONE]` (from `review-dod`).
- Smoke: 🟢 with blocks executed.
- Any `[UNVERIFIABLE]` from reviewers.
- Manual test plan — 1–2 sentences focused on regression smoke and shipping behaviour; explicitly say "backend-only, nothing to test manually" if there is no visible diff.

Next step is manual review on GitHub, then `/close-issue <N>`.

## Notes

- This orchestrator does NOT call `/close-issue` automatically. Merge is always a user decision.
- Worktree cleanup: `.claude/scripts/cleanup-worktrees.sh` runs on `Stop` hook and removes any worktree whose remote branch is gone and whose PR is merged — it iterates **all** worktrees regardless of naming, so the `feature/<N>-<slug>` pattern is auto-cleaned after merge. No manual cleanup needed.
- If at any iteration the implementing agent reports an open question (not a fixable finding — e.g., "the issue says X but the existing pattern is Y, which to follow?") — escalate to user immediately. Agents cannot decide architectural questions.
- Token discipline: every sub-agent runs in its own context; your main thread holds only the plan, per-iteration finding summaries, and gate decisions. **Do not Read doc or source files into the orchestrator thread to understand them** — route understanding through a sub-agent that returns a digest (see §Doc discovery), and Read a file verbatim only when a gate decision needs its exact text. Inline Bash is for git / `gh` / smoke control, not for bulk file inspection. The orchestrator's context is re-read on every turn and rebuilt from cold after any idle gap past the cache TTL, so what lives there is paid for repeatedly — keep it lean. If it still exceeds 50% of the window — pause and summarize before continuing.
- This skill is for one issue at a time. Multiple parallel issues = multiple invocations on multiple worktrees.
