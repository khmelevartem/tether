# Step catalog

## Principles 

Each `##` section is one step. 

**Walk this file top to bottom — file order is the sequencing authority and is never overridden.** The one ordering exception is §approach-fork, which may pull §smoke forward for fork tasks and announces itself.

**Run the step only if its condition met.** Every step carries an `**Applies to:**` line: run the section if its predicate matches this run's profile, otherwise skip in place — never reorder. A step that runs unconditionally says `**Applies to:** every run`. 

**Run the step by its body, not the name.** If you enter the step, read its entire body and execute it fully, not only what you imagine by its name.

**Announce each step on entry.** Before executing a section, post a one-line user-visible marker naming the step you are entering. Announce a skipped step too, with the reason (`skip recon — re-entry`). The announcement makes the walk auditable: the user sees the real sequence, and drift into a remembered shape shows the moment a step is announced out of order or an expected step is never announced.

**Worktree precondition.** Before dispatching any agent that writes files, ensure the working directory is `.claude/worktrees/<N>-<short-slug>/`. If missing, create from `origin/main`:

```bash
git fetch origin main --quiet
git worktree add .claude/worktrees/<N>-<short-slug> -b <N>-<short-slug> origin/main
```

This is a one-shot setup, not part of the walk.

---
## Step 0 — read-all

**Applies to:** `reentry=fresh`.

Read the issue in full — title, body, and **every** comment via `gh issue view <N> --comments`. Comments are potentially a canon-update on the body, not a discussion: when a comment conflicts with the body, the comment takes priority — surface the divergence to the user in one line. This is the complete picture `classify` resolves `track` from.

---

## Step 1 — classify the task

**Applies to:** `reentry=fresh`

Run `classify-task.sh <issue>` (pass the `issue` that `classify-state.sh` resolved) for the stable task `type` — a pure function of the issue label, so run it once per walk and hold `type` in context.

Then decide the one judgment neither script can resolve mechanically:

### Track classification

| Trigger                                                                                                                              | Track |
| ------------------------------------------------------------------------------------------------------------------------------------ | ----- |
| `type=docs`                                                                                                                          | docs  |
| `type=feature` with explicit docs-only marker (`docs-only` / `only docs` / `scope: docs` in body/DoD, or label `docs-only`)          | docs  |
| Issue without a type label AND deliverable limited exclusively to editing `.claude/` or `docs/`                                      | docs  |
| `type=infra` AND deliverable limited exclusively to editing `.claude/` files                                                         | docs  |
| `type=feature` / `bugfix` / `refactor` / `infra` with deliverable in source sets or build/CI/scripts (even if an ADR is also needed) | code  |
### Profile

`track` is the one judgment the scripts cannot resolve. After deciding it, announce the resolved profile — `track=<…> type=<…>` — so every later `**Applies to:**` match reads against an on-screen value, not a re-derived one. 

Pin the `type` and the `track` you discovered to your session memory - they are stable throughout the whole workflow and will be needed for almost every other step tag.

---
## Step 2 - classify the state

**Applies to:** every run.

Run `classify-state.sh [<N>]` and read its key=value output — the volatile state (`issue`, `reentry`, `pr`, `drift`, `touched`). With no argument it resolves which issue you are on from the current branch / open PR. Re-run it whenever current state matters.

If the branch is behind `origin/main`. Run `/pull-main` to merge fresh main, then re-run `classify-state.sh <N>` and continue the walk.

---
## Step 3 — recon

**Applies to:** `reentry=fresh`.

Dispatch one read-only recon agent (`Explore`) to sweep the doc corpus and return a compact digest — do NOT read the corpus into the orchestrator thread.

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
> For each layer in `docLayers`, note whether the target artifact exists, is a stub, or has open questions. Flag whether a doc already covers the subsystem this task targets.
> 
> Explicitly list all the relevant open questions.

`CLAUDE.md` is harness-injected — not part of the sweep.

---

## Step 4 — early-gates

**Applies to:** `reentry=fresh`.

Flag and escalate to the user before starting work if any holds:

- only one platform is mentioned, yet the task is cross-platform;
- errors and fail-paths are not described;
- it is unclear how to test;
- BUGFIX without at least a hypothesised cause;
- the deliverable's benefit rests on an unestablished assumption, or it carries a recurring per-run cost that may outweigh that benefit;
- *(docs track)* unclear which specific artifacts are expected as output;
- filler phrases: "fill in if you have something", "describe as you see fit", "and so on".
- spec or ux-brief exists but has blocking open questions

Announce the flags raised, or "none".

---

## Step 5 — bugfix-root-cause

**Applies to:** `track=code AND type=bugfix AND reentry=fresh`.

Dispatch `bug-reproducer`. It reproduces locally, runs minimal experiments per hypothesis, and returns the confirmed cause as paste-ready text. **The reproducer must always attempt to observe the symptom** even when the cause looks structurally evident or the issue names hypotheses directly. It does NOT post to GitHub.

If reproduction failed or no hypothesis matched, stop and escalate to the user.

Otherwise post the confirmed cause as a comment on issue #\<N\> via `gh issue comment <N>` (no user stop required). Then keep the confirmed cause as a hard constraint for the coder.

---

## Step 6 — fix-level

**Applies to:** `track=code AND reentry=fresh`.

When the root cause describes a class of bugs, or parallel implementations contain the same defect — consider fixing one level up: a type / container / contract change that makes the class impossible. Compare costs: N point-fixes vs 1 structural fix. If you choose point-fix — list parallel defective locations explicitly and file a follow-up issue before coding. Announce the decision: `fix-level: structural`, or `fix-level: point-fix → siblings <list>, follow-up #<M> filed` — so the follow-up obligation is on-screen, not assumed.


---

## Step 7 — layer-classify

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
## Step 8 — briefing

**Applies to:** `reentry=fresh`.

Post a short 3–6 line briefing to the user: what we are doing, why (motivation from the issue), track + affected layers/platforms, surfaced living-doc constraints. Any user-facing questions are asked next at `early-gates` if the profile includes it (code, fresh); otherwise inline, before continuing. One briefing per run; do not repeat on re-entry.

---

## Step 9 — plan

**Applies to:** `reentry=fresh`.

**Skip when the scope is already clear.** A `size:S` task whose work is unambiguous needs no plan — announce `skip plan — size:S, scope clear` and go straight to the next step.

Use the built-in `Plan` agent (or `general-purpose` if unavailable) to produce a short implementation plan: phases, artifacts to touch, validation strategy. The agent reads the recon digest's flagged engineering rules and surfaces any plan↔guide conflict explicitly.
 
When the task forks into more than one viable implementation, converge on the most suitable one and state your decisions alongside the rejected alternatives to the user.

---
## Step 10 - user-interview

**Applies to:** `reentry=fresh`

If there is no opened questions that affect the decision architecture or task intention, proceed. Minor questions that implementers can resolve on their own, must be amended to the plan and passed to implementers to resolve.

If there are any open questions that implementation bases on, left in documentation or returned by the plan agent, stop immediately and ask user. Give brief context, highlight recommended variant, provide explanation of costs and consequences on each. 

When all the critical questions are answered and the intention gaps are fulfilled, go back to `plan` step.

---

## Step 11 — reentry-reconcile

**Applies to:** `reentry=pr-feedback`.

TODO #500: ADD PR-LEVEL COMMENTS HANDLING TOO.

Read **every** human inline comment on the PR via `gh api repos/<owner>/<repo>/pulls/<PR>/comments` (paginate). For each, determine: addressed in commits after it, or not. 

**Creation date does not determine relevance** — filtering by `created_at > <date>` is forbidden; an unaddressed comment remains relevant regardless of age.

**Judge every comment if it is worth fixing.** The user might simply not understand some details and wants a justification or clarification for the implementation. 

Classify every comment worth fixing: is it **pointwise or structural**.

---

## Step 12 - transform input to actions

**Applies to:** every run

Form a list of actionable blocks (consequential or parallel) to dispatch to writing agents in the next step composed from only relevant inputs (your judge) you discovered earlier in **this** run: 

* issue
* recon's digest
* prior mentions
* documentation
* user instructions
* fork decisions
* comments
* reviewers findings
* fail description

**For structural findings, the action must contain a symmetry pass** — check sibling files, sibling methods, sibling platforms, sibling source sets for the same anti-pattern, and fix in this same pass. If a required adjacent fix falls outside the PR's scope, the agent will have to escalate to the orchestrator — do not silently skip.

**Review transmission accuracy.** Pass findings close to the reviewer's original wording; do not narrow or soften. If several findings converge on one principle — name the principle explicitly and list ALL sites where it applies. If interpretation is unclear → escalate to the user before re-dispatching, not after the next review round.

Use the produced action list to form promts for sub-agents in the next step.

---

## Step 13a - code-dispatch

**Applies to:** `track=code`

Dispatch every corresponding writing agent with a proper promt :

- non-trivial mechanism / library / structural choice → `architect` first
- UI work (Compose, screens, components, theming, navigation) → `ui-expert`
- Everything else → `coder`

---

## Step 13b — docs-dispatch

**Applies to:** `track=docs`

Dispatch every corresponding writing agent with a proper promt :

1. documentation:
	- Layering / placement / dependency-direction / mechanism choice / new glossary entry → `architect`
	- Spec gap, AC scope, or product framing → `spec-writer`
	- Screen / interaction / state-flow decision → `ux-expert`
	- UI rendering, theme, accessibility specifics → `ui-expert`
	- Pointwise correctness or style → apply a mechanical fix inline
	- .claude/ needing a non-trivial behavioural choice → `architect` for the decision, then write directly
	- .claude/ trivial or short-scoped fixes → write directly

Order matters — lower layers depend on upper ones for vocabulary and scope.

**Prose discipline carry-forward.** Every dispatch brief must instruct the sub-agent to load and every direct edit must follow the principles of  [`docs/engineering/long-lived-artifacts.md`](../../../docs/engineering/long-lived-artifacts.md) before writing and apply it to every paragraph.

---
## Step 14 - commit

**Applies to:** every run.

**Commit changes** with relevant message. Do not push yet.

---

## Step 15 - runtime-evidence

**Applies to:** `track=code`.

Probe the implemented happy-path scenario with minimal efforts. 

Come with your own algorithm for it or check, if it is already covered in `.claude/skills/smoke-test/SKILL.md`.

Ask the user to check only as last resort, if you cannot invoke the scenario with the tools available for you.

If it passes successfully → move forward.
If it fails → go back to `transform input to actions` step with a clear problem description, logs if any and a reason, if it is obvious from the observed behaviour or logs.

---
## Step 16 - fast-review

**Applies to:** every run.

What round of `fast-review` is it?

- 1-4 → **Refresh roster, then dispatch review.** Run `classify-state.sh` to recompute `touched` from the live committed diff, then `select-reviewers.sh <track> <type> <touched>` to get the current `inner-loop-reviewers` roster. Dispatch exactly those reviewers. 
	- When reviewing the UI changes: resolve the feature slug from the issue number (spec link or `docs/product/features/<slug>/` reference in the body, else glob `docs/product/features/**/ux-brief.md` and topic-match the changed paths); pass the resolved brief path in the `review-ux-conformance` prompt. Suppress the dispatch entirely when no brief exists. Announce the outcome (`ux-conformance: brief <path> → dispatched` / `ux-conformance: no brief → suppressed`) so this orchestrator-owned judgment is not silently skipped behind the scripted roster.
- 5+ → escalate to the user with remaining findings; signals a plan/scope problem the loop cannot fix.
   
If every reviewer says `APPROVE` and zero `[REQUIRED]` → step done, reset the counter, move forward.
Else → aggregate `[REQUIRED]` findings, go back to `transform input to actions` step.

---
## Step 17 — simplify

**Applies to:** `track=code`.

Remove scaffolding and duplication by dispatching the implementing agent once:

> All findings are resolved. Make one simplification pass over the diff: remove dead branches, inline single-use helpers, collapse trivial wrappers.
>
> **For every comment / KDoc / prose paragraph in the diff — including `.claude/skills/**`, `docs/`, and Markdown — apply CLAUDE.md §Code style and [`docs/engineering/long-lived-artifacts.md`](../../../docs/engineering/long-lived-artifacts.md).**
>
> **Do not rephrase prose for brevity.** If a sentence is load-bearing and free of the issues above, leave its wording alone. Cut whole sentences when they fail the rule; otherwise keep them as written.
>
> Do not change behaviour; do not touch anything outside the diff. Run `./gradlew allTests -q` after.

---
## Step 18 - check working tree

**Applies to:** every run.

**Working tree must be clean** (committed). Commit if anything changed.

---

## Step 19 — full-review

**Applies to:** every run.

1. **Wave A** in parallel: run `classify-state.sh` for fresh `touched`, then `select-reviewers.sh <track> <type> <touched>` to get the `wave-a-reviewers` roster. Dispatch exactly those reviewers. Suppress `review-ux-conformance` dispatch when the touched feature has no `ux-brief.md`. Each agent receives the issue number and reviews the working tree.
2. **Wave B**: dispatch exactly the reviewer(s) named in `select-reviewers.sh`'s `wave-b-reviewers` output with the combined Wave A findings as input. (Roster authority is the script — the steps don't name specific reviewers.)

If every reviewer says `APPROVE` and zero `[REQUIRED]` → step done, move forward.
Else → aggregate `[REQUIRED]` findings, go back to `transform input to actions` step.

---

## Step 20 — smoke

**Applies to:** `track=code`.

Run `/smoke-test`. Selection from `classify-state.sh`'s `touched` set:

| `touched` contains                                                                       | Run                                                           |
| ---------------------------------------------------------------------------------------- | ------------------------------------------------------------- |
| `code` without `platform`                                                                | All smoke blocks                                              |
| only `platform` with NO changes in common code                                           | Smoke block for each platform whose source set is in the diff |
| `touched` is empty (build files, CI, root scripts — nothing `classify-state.sh` buckets) | All smoke blocks                                              |
| `touched` is non-empty and only `docs` / `claude` / `engdoc` / `ux-brief`                | Nothing                                                       |

If the PR introduces a new critical happy-path not covered by smoke — extend `.claude/skills/smoke-test/SKILL.md` in this same PR before running.

Record a 🟢/🟡/🔴 verdict naming the smoke blocks executed. A bare pass/fail that names no block is not a smoke result — it signals a compile check stood in for the run; redo. Any 🟡/🔴 → present to the user, stop.

---

## Step 21 — enforcement-probe

**Applies to:** `type=infra`.

If the gate is the deliverable, not the track: an enforcement mechanism (custom lint rule, CI guard, git hook, custom Gradle check, or a `.claude/` hook / script) often lives outside `src/`, so this is the model's judgment to make. If the deliverable is not an enforcer — announce `skip enforcement-probe — no enforcer in deliverable` and move on.

1. Create a minimal artifact violating the check in a real location (where the enforcer should fire).
2. Run the corresponding Gradle/CI task.
3. Verify: build FAILED **with the expected message**.
4. Delete the probe; confirm via `git status -s` nothing remains.

If step 3 passes green — the enforcer is not wired in despite green unit tests. Red gate; escalate to the user.

Record a 🟢/🟡/🔴 verdict naming the enforcement-probe path. Any 🟡/🔴 → present to the user, stop.

---

## Step 22 — push

**Applies to:** every run.

```bash
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

TODO #500: REPLY TO PR-LEVEL COMMENT TOO

After the push, reply to **every** addressed inline comment via `gh api -X POST repos/<owner>/<repo>/pulls/<PR>/comments/<comment_id>/replies -f body="<reply>"`: what was done + the commit SHA, or explicit reasoning if deliberately declined. A reply is the "addressed" signal; without it the next re-entry re-reads the comment as unaddressed.

---

## Step 25 — final-summary

**Applies to:** every run.

Report to the user:

- PR URL.
- AC verdict: all `[DONE]` (from `review-dod`).
- *(code track)* Smoke: 🟢 with blocks executed.
- Any `[UNVERIFIABLE]` from reviewers.
- Manual test plan (code track). Build it as a concrete checklist the user can execute, not prose.
- What is NOT testable manually — backend-only changes, internal refactors with no visible diff. Say so explicitly and name what was already covered by `smoke` 🟢 instead.

## the end
