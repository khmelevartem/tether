# Step catalog

Each `##` section is one step. **Walk this file top to bottom — file order is the sequencing authority and is never overridden.** Every step carries an `**Applies to:**` line: run the section if its predicate matches this run's `(track, type, reentry)` profile, otherwise skip in place — never reorder. A step that runs unconditionally says `**Applies to:** every run`. The one ordering exception is §approach-fork, which may pull §smoke forward for fork tasks and announces itself.

**A blocked profile permits one step.** If `classify-state` emitted `status=blocked` (the branch is behind `origin/main`), exactly one step is legal — `sync-main` — regardless of any other step's `**Applies to:**`, including `every run`. No other step runs until a re-run of `classify-state` clears the block.

**Announce each step on entry.** Before executing a section, post a one-line user-visible marker naming the step you are entering (e.g. `→ inner-loop`). Announce a skipped step too, with the reason (`skip recon — re-entry`). The announcement makes the walk auditable: the user sees the real sequence, and drift into a remembered shape shows the moment a step is announced out of order or an expected step is never announced.

**A step's name is a label, not its specification.** Run each step from its section body, not from what its name suggests.

**Worktree precondition.** Before dispatching any agent that writes files, ensure the working directory is `.claude/worktrees/<N>-<short-slug>/`. If missing, create from `origin/main`:

```bash
git fetch origin main --quiet
git worktree add .claude/worktrees/<N>-<short-slug> -b <N>-<short-slug> origin/main
```

This is a one-shot setup, not part of the walk.

---

## Step 0 — read-all

**Applies to:** `reentry=fresh`.

Read the issue in full — title, body, and **every** comment via `gh issue view <N> --comments`. Comments are potentially a canon-update on the body, not a discussion: when a comment conflicts with the body, the comment takes priority — surface the divergence to the user in one line. This is the complete picture `classify` resolves `track` from at the start; on re-entry `classify` reads `track` from the committed `touched` set instead.

---

## Step 1 — classify

**Applies to:** every run.

Run two scripts and read their key=value output:

- `classify-state.sh [<N>]` — the volatile state (`issue`, `reentry`, `pr`, `drift`, `touched`). Re-run it whenever current state matters; with no argument it resolves which issue you are on from the current branch / open PR.
- `classify-task.sh <issue>` — the stable task `type`, a pure function of the issue label. Run it once per walk and hold `type` in context; pass the `issue` that `classify-state.sh` resolved.

If `classify-state.sh` emitted `status=blocked`, stop here — the preamble's blocked-profile rule governs and `track` is left unresolved.

Otherwise decide the one judgment neither script can resolve mechanically:

### Track classification

| Trigger | Track |
|---|---|
| `type=docs` | docs |
| `type=feature` with explicit docs-only marker (`docs-only` / `only docs` / `scope: docs` in body/DoD, or label `docs-only`) | docs |
| Issue without a type label AND deliverable limited exclusively to editing `.claude/` or `docs/` | docs |
| `type=infra` AND deliverable limited exclusively to editing `.claude/` files | docs |
| `type=feature` / `bugfix` / `refactor` / `infra` with deliverable in source sets or build/CI/scripts (even if an ADR is also needed) | code |

On re-entry, resolve `track` from the committed `touched` set against the rows above; the issue body is not re-read. Empty `touched` (build files, CI, root scripts — `classify-state.sh` buckets none of them) is the **code** track, per the last row, not docs.

### Profile

`track` is the one judgment the scripts cannot resolve. After deciding it, announce the resolved profile — `track=<…> type=<…> reentry=<…>` — so every later `**Applies to:**` match reads against an on-screen value, not a re-derived one. Treat `reentry=unknown` as `fresh` for matching. From here, walk the steps per the preamble — each `**Applies to:**` now matches against this on-screen profile.

---

## Step 2 — sync-main

**Applies to:** `drift=behind`.

The branch is behind `origin/main`. Run `/pull-main` to merge fresh main, then re-run `classify-state.sh <N>` and continue the walk on the now-clean profile (`drift=up-to-date`, `touched` present). Reviewing or building before this would diff against a stale `main` and risk a conflicting merge.

---

## Step 3 — reentry-reconcile

**Applies to:** `reentry=pr-feedback`.

Read **every** human inline comment on the PR via `gh api repos/<owner>/<repo>/pulls/<PR>/comments` (paginate). For each, determine: addressed in commits after it, or not. **Creation date does not determine relevance** — filtering by `created_at > <date>` is forbidden; an unaddressed comment remains relevant regardless of age. **Counted is not read** — fetching `length` without `body` does not satisfy this. While unaddressed comments remain, no reviewer step runs.

### Re-entry routing — by-agent attribution

Route each comment to the agent that owns the work surface it objects to, not to the coder by default:

- Layering / placement / dependency-direction / mechanism choice → `architect`.
- Spec gap, AC scope, or product framing → `spec-writer`.
- Screen / interaction / state-flow decision → `ux-expert`.
- UI rendering, theme, accessibility specifics → `ui-expert`.
- Glossary / docs entry the architect wrote → `architect`.
- Pointwise correctness or style with no architectural element → code track: `coder` via the inner-loop path; docs track: re-dispatch the sub-agent that owns the artifact, or apply a mechanical fix inline.

The originating agent returns its revised work to the orchestrator. The orchestrator decides next steps: dispatch `coder` to apply, re-run reviewers, or escalate when the revision changes scope (new top-level types, layer crossings, deleted contracts).

Before re-dispatching reviewers, refresh the roster: run `classify-state.sh` + `select-reviewers.sh` against the current committed `touched` set. Use the returned `inner-loop-reviewers` for this pass — do not eyeball the domain.

The reply-to-every-comment obligation is its own step (`reply-threads`), after the push.

---

## Step 4 — recon

**Applies to:** `reentry=fresh`.

Dispatch ONE read-only recon agent (`Explore`) to sweep the doc corpus and return a compact digest — do NOT read the corpus into the orchestrator thread.

Brief for the recon agent (pass the issue title + body):

> Read-only sweep for issue #\<N\>. Return a compact digest — binding constraints and relevant paths, no file dumps:
>
> - **Product features** — `ls docs/product/features/` (+ `README.md` index). Slug(s) matching this issue; binding constraints from each `spec.md` / `ux-brief.md` in 1-2 lines.
> - **Product context** — `docs/product/*.md`. The framing that binds this issue's scope / audience / timing.
> - **Engineering living docs** — `docs/engineering/*.md`. Present-tense rules whose topic matches the task; flag any rule the planned change would violate.
> - **ADR** — `docs/engineering/adr/adr-*.md`. ADRs matching the topic; for each, its **Revisit if** section and whether this task trips a trigger.
> - **Knowledge** — `docs/knowledge/*.md`. Solved-problem notes relevant to the task.
> - **Glossary** — `docs/glossary.md`. Terms this issue's domain touches, with their locked definitions.
> - **Prior `#<N>` mentions** — ranked list of file:line hits across the repo with one-line summary of what each expects. Every hit must be addressed in this PR or explicitly deferred to another issue.
>
> *(docs track also:)* For each layer in `docLayers`, note whether the target artifact exists, is a stub, or has open questions. Flag whether a doc already covers the subsystem this task targets.

`CLAUDE.md` is harness-injected — not part of the sweep.

---

## Step 5 — briefing

**Applies to:** `reentry=fresh`.

Post a short 3–6 line briefing to the user: what we are doing, why (motivation from the issue), track + affected layers/platforms, surfaced living-doc constraints. Any user-facing questions are asked next at `early-gates` if the profile includes it (code, fresh); otherwise inline, before continuing. One briefing per run; do not repeat on re-entry.

---

## Step 6 — adr-triggers

**Applies to:** `reentry=fresh`.

For each ADR the recon digest flagged as trigger-tripped, the plan (code track) or layer list (docs track) must confirm the ADR (false trigger) or include a reversal sub-plan (see [`docs/engineering/adr/README.md`](../../../docs/engineering/adr/README.md) §Reversing an ADR). Announce the verdict per flagged ADR; "none" when the digest flagged none.

---

## Step 7 — prior-mentions

**Applies to:** `reentry=fresh`.

For each prior-`#<N>` mention the digest ranked, resolve it in this PR or escalate to the user as "can't do here — move to #M?". Never silently leave a `TODO(#<N>)` after merge. Announce the disposition per mention; "none" when the digest found none.

---

## Step 8 — critical-reading

**Applies to:** `reentry=fresh`.

Flag and escalate to the user before starting work if any holds:

- only one platform is mentioned, yet the task is cross-platform;
- errors and fail-paths are not described;
- it is unclear how to test;
- BUGFIX without at least a hypothesised cause;
- the deliverable's benefit rests on an unestablished assumption, or it carries a recurring per-run cost that may outweigh that benefit;
- *(docs track)* unclear which specific artifacts are expected as output;
- filler phrases: "fill in if you have something", "describe as you see fit", "and so on".

Announce the flags raised, or "none".

---

## Step 9 — early-gates

**Applies to:** `track=code AND reentry=fresh`.

Operational mechanics for closing pre-implementation gaps. The user-stop conditions live in SKILL.md §Gate semantics — this step is where the orchestrator dispatches sub-agents to clear the resolvable ones.

**Pre-implementation principle.** Open questions found in spec, ux-brief, issue body, or surfaced by a preparation sub-agent are escalated to the user before work starts. Each question carries a recommended option and a one-line rationale — dark spots cause unnecessary inner-loop iterations.

- **FEATURE, spec exists but has blocking open questions** → dispatch `spec-writer` to resolve them. Forward `spec-writer`'s residual unresolvable questions to the user verbatim, with the recommended-option annotation above.
- **FEATURE with user-facing UI AND `ux-brief.md` missing or stale** → dispatch `ux-expert` after `spec-writer`. Open UX questions fold back here: surface verbatim to the user, collect answers, re-dispatch. The brief is committed as part of the PR.

A missing or stub spec / DoD is a SKILL.md gate (no sub-agent mitigation) — do not bypass it here.

---

## Step 10 — bugfix-root-cause

**Applies to:** `track=code AND type=bugfix AND reentry=fresh`.

Dispatch `bug-reproducer`. It reproduces locally, runs minimal experiments per hypothesis, and returns the confirmed cause as paste-ready text. **The reproducer must always attempt to observe the symptom** even when the cause looks structurally evident or the issue names hypotheses directly. It does NOT post to GitHub.

If reproduction failed or no hypothesis matched → SKILL.md §BUGFIX root cause (user stop).

Compare the confirmed cause against the issue body. If it materially diverges (different mechanism / scope / symptom / severity) → SKILL.md §Cause-vs-issue divergence (user stop).

Otherwise post the confirmed cause as a comment on issue #\<N\> via `gh issue comment <N>` (no user stop required). Then keep the confirmed cause as a hard constraint for the coder.

---

## Step 11 — layer-classify

**Applies to:** `track=docs AND reentry=fresh`.

Decide which artifact layers this issue needs.

### Doc-layer classification

| Layer | Needed when | Artifact | Writer |
|---|---|---|---|
| **spec** | FEATURE AND `docs/product/features/<slug>/spec.md` is missing, `(stub)`, or has blocking open questions | `docs/product/features/<slug>/spec.md` | `spec-writer` |
| **ux** | FEATURE with user-facing UI AND `ux-brief.md` is missing or stale relative to spec changes | `docs/product/features/<slug>/ux-brief.md` | `ux-expert` |
| **tech** | Subsystem with a non-trivial mechanism not covered by `docs/engineering/<name>.md`, or existing doc is outdated | `docs/engineering/<name>.md` | `architect` |
| **ADR** | Architectural choice clearing the three-way threshold in `docs/engineering/adr/README.md` §ADR threshold | `docs/engineering/adr/adr-<name>.md` | `architect` |
| **knowledge** | Solved problem / platform quirk worth capturing (from a retro or closed BUGFIX) | `docs/knowledge/<name>.md` | `architect` |
| **.claude** | Deliverable edits a skill prompt, agent definition, slash command, hook, or settings | `.claude/skills/…` / `.claude/agents/…` / `.claude/commands/…` | orchestrator (inline) |

Multiple layers per issue are normal. Classification ambiguity → SKILL.md §Framing ambiguity (user stop).

Result of this step: an ordered list of layers to produce, with target path and writer for each. No artifacts created yet.

---

## Step 12 — plan

**Applies to:** `reentry=fresh`.

Use the built-in `Plan` agent (or `general-purpose` if unavailable) to produce a short implementation plan: phases, artifacts to touch, validation strategy. The agent reads the recon digest's flagged engineering rules and surfaces any plan↔guide conflict explicitly.

**Skip when the scope is already clear.** A `size:S` task whose work is unambiguous needs no plan — announce `skip plan — size:S, scope clear` and go straight to the build (`inner-loop` / `docs-dispatch`).

**Issue scope is a starting point, not a cage.** If touching adjacent classes or neighbouring platforms is needed for a quality solution — expand scope in this PR. Whether to fold a finding or defer it → [`docs/engineering/scope-discipline.md`](../../../docs/engineering/scope-discipline.md). Forced cascade outside the literal **Out of scope** list → SKILL.md gate; do not silently fold.

---

## Step 13 — fix-level

**Applies to:** `track=code AND reentry=fresh`.

When the root cause describes a class of bugs, or parallel implementations contain the same defect — consider fixing one level up: a type / container / contract change that makes the class impossible. Compare costs: N point-fixes vs 1 structural fix. If you choose point-fix — list parallel defective locations explicitly and file a follow-up issue before coding. Announce the decision: `fix-level: structural`, or `fix-level: point-fix → siblings <list>, follow-up #<M> filed` — so the follow-up obligation is on-screen, not assumed.

---

## Step 14 — lane-split

**Applies to:** `track=code AND reentry=fresh`.

Default is a single sequential lane. Split into parallel lanes ONLY if the plan enumerates file-level disjoint sets: lane A files ∩ lane B files = ∅. List explicit file paths per lane. Any overlap → execute sequentially. (Use "lane" here — "track" is reserved for docs vs code.)

---

## Step 15 — approach-fork

**Applies to:** `track=code AND reentry=fresh`.

When the task forks into more than one viable implementation, converge on the most suitable one (via `architect` when the choice is non-trivial), implement that candidate, and verify it at runtime under the conditions that distinguish the candidates before investing the full review pipeline. Only an empirically-confirmed approach earns that investment. This pulls `smoke` forward for fork tasks; single-implementation tasks keep the default order.

---

## Step 16 — docs-dispatch

**Applies to:** `track=docs AND reentry=fresh`.

Order matters — lower layers depend on upper ones for vocabulary and scope.

Each sub-agent owns the decisions inside its layer. The orchestrator routes, relays, then aggregates. Do not pre-design or pre-research for sub-agents.

**Prose discipline carry-forward.** Every dispatch brief must instruct the sub-agent to load [`docs/engineering/long-lived-artifacts.md`](../../../docs/engineering/long-lived-artifacts.md) before writing and apply it to every paragraph.

1. **spec** (if in `docLayers`) → dispatch `spec-writer`. It decides user needs and scenarios, runs its own clarifying-questions phase, scope cohesion pass, and `docs/product/features/README.md` row update. Open questions → relay verbatim to the user → re-dispatch with answers.

2. **ux** AND **tech / ADR / knowledge** — if both needed, dispatch in parallel (file-disjoint by construction):
   - **ux** → dispatch `ux-expert`. Open UX questions → relay verbatim to the user → re-dispatch with answers.
   - **tech / ADR / knowledge** → dispatch `architect`. For knowledge entries the design work was already done; architect records it. Open questions → relay verbatim to the user → re-dispatch with answers.

   If only one is needed, run it alone.

3. **.claude** — write directly via Edit/Write (no sub-agent dispatch — an agent editing its own definition would race itself). Apply [`docs/engineering/long-lived-artifacts.md`](../../../docs/engineering/long-lived-artifacts.md). If the prompt change encodes a non-trivial behavioural choice, dispatch `architect` first to converge the choice and produce an ADR; only then write the prompt edit.

Each sub-agent / direct write returns: paths produced, index updates, converged-decision summary, open questions (if any). Open questions in any layer block forward progress on that layer.

---

## Step 17 — inner-loop

**Applies to:** `track=code`.

Per lane (or sequentially if single lane):

**Iteration:**

1. Dispatch the implementing agent with the plan slice:
   - **UI work** (Compose, screens, components, theming, navigation) → `ui-expert`.
   - **Architectural design point** — plan surfaces a non-trivial mechanism / library / structural choice `coder` should not make alone → `architect` first. It returns a one-line decision summary that becomes a hard constraint for the subsequent `coder` dispatch. Do NOT dispatch `architect` when the plan is wiring a pattern an existing ADR prescribes, applying a documented mechanism to a new caller, doing docs/prose cleanup, or running an ADR sibling sweep — see `architect.md §When invoked`.
   - **Everything else** → `coder`. Mixed work — split into sub-lanes if disjoint files; else dispatch `coder` which can pull in `ui-expert` / `architect` via Agent tool.

   **Prose discipline carry-forward.** When the plan slice includes prose edits, the dispatch brief must instruct the implementing agent to load [`docs/engineering/long-lived-artifacts.md`](../../../docs/engineering/long-lived-artifacts.md) before writing.

2. **Commit before dispatching the reviewer wave.** Reviewers read `git diff main...HEAD` — the working tree must have no uncommitted changes when they run. Otherwise some agents read a stale state and send phantom `[REQUIRED]` flags. One source of truth = one commit per iteration.

3. **Refresh roster, then dispatch.** Run `classify-state.sh` to recompute `touched` from the live committed diff, then `select-reviewers.sh` to get the current `inner-loop-reviewers` roster. Dispatch exactly those reviewers. When dispatching `review-ux-conformance`: resolve the feature slug from the issue number (spec link or `docs/product/features/<slug>/` reference in the body, else glob `docs/product/features/**/ux-brief.md` and topic-match the changed paths); pass the resolved brief path in the prompt. Suppress the dispatch entirely when no brief exists — brief-existence is not scriptable; the orchestrator owns this gate. Announce the outcome (`ux-conformance: brief <path> → dispatched` / `ux-conformance: no brief → suppressed`) so this orchestrator-owned judgment is not silently skipped behind the scripted roster.

   On iterations 2+: re-dispatch only reviewers that raised `[REQUIRED]` the previous round, plus any added by the refreshed roster when the new changes pulled in a new domain. Full-roster coverage is restored at `full-review`.

   **Missing UX brief mid-loop.** If `ui-expert` halts reporting "UX brief missing" → STOP and escalate to the user. Either recon missed the UI scope, or the loop expanded into UI work outside the original plan; do not silently dispatch `ux-expert` to backfill.

4. If every reviewer says `APPROVE` and zero `[REQUIRED]` → lane done.

5. Else → aggregate `[REQUIRED]` findings, dispatch the implementing agent again:

   > Previous review found these issues that block the PR. Address each. For each finding, classify as pointwise or structural; for structural findings, do a symmetry pass — check sibling files, sibling methods, sibling platforms, sibling source sets for the same anti-pattern, and fix in this same pass. If a required adjacent fix falls outside the PR's scope, escalate to the orchestrator — do not silently skip.
   >
   > \<list of `[REQUIRED]` findings with file:line\>

   **Red CI test = broken code, not broken test.** Fix the code. Weakening assertions, deleting failing tests, rewriting as narrower fast-checks — all forbidden without explicit user approval. "The test was checking the wrong thing" → escalate to the user, do not resolve independently.

   **Review transmission accuracy.** Pass findings close to the reviewer's original wording; do not narrow or soften. If several findings converge on one principle — name the principle explicitly and list ALL sites where it applies. If interpretation is unclear → escalate to the user before re-dispatching, not after the next review round.

   Go back to step 2.

**Iteration limit:** 4 per lane. If not converged after 4 → escalate to the user with remaining findings; signals a plan/scope problem the loop cannot fix.

---

## Step 18 — simplify

**Applies to:** `track=code`.

After all lanes converge. Iterative fix cycles accumulate scaffolding and duplication; this pass removes it.

Dispatch the implementing agent once:

> All findings are resolved. Make one simplification pass over the diff: remove dead branches, inline single-use helpers, collapse trivial wrappers.
>
> **For every comment / KDoc / prose paragraph in the diff — including `.claude/skills/**`, `docs/`, and Markdown — apply CLAUDE.md §Code style and [`docs/engineering/long-lived-artifacts.md`](../../../docs/engineering/long-lived-artifacts.md).** A sentence may be left only if it carries non-obvious context or further instructions. Remove: history of implementing or decision-making (except ADRs); mentions of things that do not exist in the artifact (a feature considered and dropped, a button decided against) unless their absence is itself a non-obvious invariant; repetition of a fact already stated nearby.
>
> **Do not rephrase prose for brevity.** If a sentence is load-bearing and free of the issues above, leave its wording alone. Cut whole sentences when they fail the rule; otherwise keep them as written.
>
> Do not change behaviour; do not touch anything outside the diff. Run `./gradlew allTests -q` after.

If anything was simplified — commit.

---

## Step 19 — full-review

**Applies to:** every run.

Pre-PR review wave, inline — no GitHub publication.

1. **Working tree must be clean** (committed). Reviewers read `git diff main...HEAD`.
2. **Wave A** in parallel: run `classify-state.sh` + `select-reviewers.sh` to get the `wave-a-reviewers` roster. Dispatch exactly those reviewers. Suppress `review-ux-conformance` dispatch when the touched feature has no `ux-brief.md` (brief-existence is not scriptable). Each agent receives the issue number and reviews the working tree.
3. **Wave B**: dispatch exactly the reviewer(s) named in `select-reviewers.sh`'s `wave-b-reviewers` output with the combined Wave A findings as input. (Roster authority is the script — the steps don't name specific reviewers.)
4. Aggregate. Apply any `[REQUIRED]` via the implementing/writing agent (with the symmetry-pass instruction for code, or via the responsible sub-agent for docs). Re-run until approved or 2 iterations.

For docs track: tell each reviewer to apply the new rule to the diff itself when the PR establishes or extends canon.

No `gh pr review` here — findings are consumed locally only.

---

## Step 20 — smoke

**Applies to:** `track=code`.

Runtime feature behaviour (user path, network exchange, lifecycle). If the deliverable carries none — announce `skip smoke — no runtime feature behaviour` and move on.

Selection from `classify-state.sh`'s `touched` set:

| `touched` contains | Run |
|---|---|
| `code` without `platform` | All smoke blocks (Desktop, Android if device attached, iOS sim) — the change is in common sources |
| `platform` (and possibly `code`) | Smoke block for each platform whose source set is in the diff |
| `touched` is empty (build files, CI, root scripts — nothing `classify-state.sh` buckets) | All smoke blocks — a build-system change can break any target |
| `touched` is non-empty and only `docs` / `claude` / `engdoc` / `ux-brief` | Nothing |

`ui` always co-occurs with `code` — handled by the first row.

If the PR introduces a new critical happy-path not covered by smoke — extend `.claude/skills/smoke-test/SKILL.md` in this same PR before running.

Record a 🟢/🟡/🔴 verdict naming the smoke blocks executed. A bare pass/fail that names no block is not a smoke result — it signals a compile check stood in for the run; redo. Any 🟡/🔴 → present to the user, stop.

---

## Step 21 — enforcement-probe

**Applies to:** every run.

The gate is the deliverable, not the track: an enforcement mechanism (custom lint rule, CI guard, git hook, custom Gradle check, or a `.claude/` hook / script) often lives outside `src/`, so this is the model's judgment to make. If the deliverable is not an enforcer — announce `skip enforcement-probe — no enforcer in deliverable` and move on.

1. Create a minimal artifact violating the check in a real location (where the enforcer should fire).
2. Run the corresponding Gradle/CI task.
3. Verify: build FAILED **with the expected message**.
4. Delete the probe; confirm via `git status -s` nothing remains.

If step 3 passes green — the enforcer is not wired in despite green unit tests. Red gate; escalate to the user.

Record a 🟢/🟡/🔴 verdict naming the enforcement-probe path. Any 🟡/🔴 → present to the user, stop.

---

## Step 22 — commit-push

**Applies to:** every run.

Only after `full-review` has converged and every runtime check that applied is 🟢 — `smoke` and `enforcement-probe`, an announced skip counting as satisfied.

```bash
git add <relevant files>
git commit -m "#<N>: <message>"
git push origin <N>-<short-slug>   # add -u on the first push of a fresh branch
```

No force-push. Do not block on explicit OK before push — green runtime checks (`smoke` / `enforcement-probe`) are the gate, not user approval. Branches and worktrees share the same shape — `<N>-<short-slug>`.

---

## Step 23 — open-pr

**Applies to:** `reentry=fresh`.

Open the PR for the just-pushed branch:

1. Read [`.github/pull_request_template.md`](../../../.github/pull_request_template.md) and compose the body using only the sections it defines — do not add sections of your own.
2. Write the body to a file (e.g. `/tmp/pr-<N>-body.md`); `--body` with an in-shell heredoc silently corrupts multiline markdown and can drop `Closes #<N>`.
3. Then run:

```bash
gh pr create --title "<title>" --body-file /tmp/pr-<N>-body.md
```

---

## Step 24 — reply-threads

**Applies to:** `reentry=pr-feedback`.

After the push, reply to **every** addressed inline comment via `gh api -X POST repos/<owner>/<repo>/pulls/<PR>/comments/<comment_id>/replies -f body="<reply>"`: what was done + the commit SHA, or explicit reasoning if deliberately declined. A reply is the "addressed" signal; without it the next re-entry re-reads the comment as unaddressed.

---

## Step 25 — final-summary

**Applies to:** every run.

Report to the user:

- PR URL.
- Files changed (summary).
- AC verdict: all `[DONE]` (from `review-dod`).
- *(code track)* Smoke: 🟢 with blocks executed.
- Any `[UNVERIFIABLE]` from reviewers.
- *(docs track)* Layers produced and artifact paths.

**Manual test plan (code track).** Build it as a concrete checklist the user can execute, not prose. Cover:

- **Golden path** — the user action that satisfies the issue's primary AC. Name the platform, the entry point (CLI command / screen path), the input, and the expected observable outcome.
- **Edge cases** — every AC that is not the golden path, plus failure modes the diff plausibly introduced (cancellation, empty input, offline, permission denied, simultaneous peers, etc.). One bullet per case.
- **Cross-platform parity** — if `touched` includes `code` (common) or multiple `platform` source sets, list one check per affected platform.
- **Regression sweep** — adjacent features the diff could have broken. Name them; do not say "smoke them".
- **What is NOT testable manually** — backend-only changes, internal refactors with no visible diff. Say so explicitly and name what was already covered by `smoke` 🟢 instead.

If the entire diff is backend-only or has no visible behaviour change: state "backend-only, nothing to test manually" and stop.
