---
name: document
description: End-to-end documentation orchestrator for a docs-only GitHub issue. Plans which artifact layers are needed (product spec / UX brief / engineering mechanism doc / ADR / .claude prompt), dispatches the right sub-agents, runs a consistency pass across artifacts, then a docs-scoped review wave, and lands a docs PR. Use when the issue's deliverable is documentation or agent/skill configuration, not runtime code. /implement delegates here automatically when it detects a docs-only issue.
---

# /document — Docs-only issue orchestrator

You are the orchestrator for documentation work on a single GitHub issue. You dispatch sub-agents (`spec-writer`, `ux-expert`, `tech-writer`) for the four "document" layers, and write `.claude/` skill/agent prompts directly (no sub-agent exists for prompt edits — the agent that would edit its own definition is the one being defined). You decide when to escalate to the user.

**Goal:** issue → reviewed docs artifacts → merged PR, with the user consulted at docs-specific gates only. No smoke, no code-correctness reviewers — the deliverable is text artifacts, not runtime behaviour.

## Input

Issue number `<N>`.

## Re-entry contract

Skill идемпотентен по issue. На каждом вызове первым делом проверь `gh pr list --search "issue:#<N>" --state open`:

- **PR нет** → стартуй Step 1.
- **PR есть и открыт** → ты в pull-request feedback итерации. Прочитай существующие human-комменты на PR (`gh api repos/<owner>/<repo>/pulls/<PR>/comments` + `gh pr view <PR> --comments`). Прогон **обязан** включать на свежем diff'е (`docs/` + `.claude/`): Step 4 (consistency pass) → Step 5 (review wave). Из дисциплины ничего пропускать нельзя.

Шаг 6 (commit + present + push) в re-entry упрощается: коммит идёт в существующую ветку, force-push не нужен, новый PR не создавать.

## Gate semantics — when to stop and ask the user

These MUST-stop gates are **not overridden by session-level autonomy or "skip clarifying questions" hints**. Such hints apply only to execution-stage trivia within an already-agreed scope. The cost of a one-message pause is far lower than the cost of unwinding a unilateral architectural / product decision.

You MUST stop and ask the user in these cases (and only these):

- **D1 — Spec / ADR / brief ambiguity.** Any of:
  - issue's DoD is missing/stub or contradicts comments;
  - a sub-agent (`spec-writer` / `ux-expert` / `tech-writer`) returns open questions;
  - the artifact requires a choice the user hasn't made yet (≥3 architecturally reasonable answers).

  **Palette-first format when ≥3 options.** Surface them as a parallel palette (each with cost / closes / trade-off), mark the one you'd recommend and why. Avoid leading with a single recommendation + one alternative — the palette makes choice traceable, and rejected branches become the ADR's "Considered and rejected" section without rewriting. The user converges through option selection across iterations, not by accepting a pre-shaped plan.

  **Artifacts are snapshots of converged thinking.** Doc-writing happens *after* the design has stabilised through palette → user redirects → choice. The writing pass should feel mechanical — record what is already decided. If you find a sub-agent making architectural decisions during drafting, you exited the gate too early; back up to palette.

  **Pluralistic research when external survey is needed** (library coordinates, API status, prior-art). Dispatch ≥3 sub-agents with different prompts in parallel, not a single pass. Convergence = robust choice; divergence = trade-off to surface. Skip when options are already known.

  **Verify research-agent factual claims before locking them.** Library availability, runtime API status, dependency coordinates, "is this bug fixed" — verify directly (Maven Central, jar/KLib inspection, GitHub issue state, official docs) before committing to spec / ADR / guide / agent file.

  **DoD includes enforcement when the decision sets rules.** A locked rule without an enforcing artefact (reviewer agent, lint, hook, test) quietly drifts. Surface the gap during scoping if missing.

- **D2 — Cross-doc inconsistency.** Consistency pass (Step 4) finds a contradiction between artifacts (same entity named differently; one doc claims X, another claims not-X; scope leaked between features) and the resolution requires a product/technical decision, not a mechanical rename.

- **D5 — Final approval before push.** After review wave converges, present the committed-but-not-pushed diff to the user for approval. Push + PR creation happen only after the user OKs.

Gates G2 (BUGFIX root cause) and G4 (smoke) from `/implement` do not apply — no runtime, no bug.

Everything else — sub-agent dispatch, mechanical fixes, review iterations — you handle internally without the user.

## Step 1 — Read issue + worktree setup

```bash
gh issue view <N> --json title,body,labels,comments
gh pr list --search "issue:#<N>" --state open --json number,isDraft,headRefName
```

**Comments — это не дискуссия, это потенциально canon-update body.** При противоречии comment'а с body — приоритет comment'у, эскалируй пользователю одной строкой.

**Critical reading.** Воспринимай описание issue как **стартовую точку, не как факт**. Подсвечивай и эскалируй пользователю до начала работы, если:
- из issue непонятно, какие именно артефакты ожидаются на выходе;
- противоречие comment vs body, не разрешимое prioritization-правилом;
- фразы-затычки: «опиши как считаешь нужным», «зафиксируй где надо», «и так далее».

**Worktree setup — do this BEFORE dispatching any agent that edits files.** If you are not already in `.claude/worktrees/<branch>/`:

```bash
git worktree add .claude/worktrees/docs-<N>-<short-slug> -b docs/<N>-<short-slug> main
cd .claude/worktrees/docs-<N>-<short-slug>
```

All subsequent agent dispatches happen with this as cwd.

## Step 2 — Layer classification

Decide which artifact layers this issue needs. Read the issue body, comments, linked spec/feature (if any), and `docs/product/features/README.md` / `docs/engineering/README.md` to see what already exists.

| Layer | Needed when | Artifact | Writer |
|---|---|---|---|
| **spec** | Тип FEATURE AND `docs/product/features/<slug>/spec.md` отсутствует, `(stub)`, или blocking open questions | `docs/product/features/<slug>/spec.md` | `spec-writer` |
| **ux-brief** | FEATURE с user-facing UI (screen / component / navigation) AND `ux-brief.md` отсутствует или stale relative to spec changes | `docs/product/features/<slug>/ux-brief.md` | `ux-expert` |
| **tech-doc** | Subsystem с нетривиальным механизмом (protocol / library choice / cross-platform invariant) не покрыт `docs/engineering/<name>.md`, либо существующий устарел | `docs/engineering/<name>.md` | `tech-writer` |
| **ADR** | Architectural choice с ≥3 considered options, история выбора имеет ценность (нельзя восстановить из кода + living docs) | `docs/engineering/adr/adr-<name>.md` | `tech-writer` |
| **.claude prompt** | Deliverable — правка skill prompt (`.claude/skills/<name>/SKILL.md`), agent definition (`.claude/agents/<name>.md`) или hook (`.claude/scripts/*`, `.claude/settings.json`). Сюда же — задачи типа INFRA / без Тип, меняющие поведение агентов | `.claude/skills/<...>` или `.claude/agents/<...>` | orchestrator (inline) |

Multiple layers per issue are normal (e.g. FEATURE with UI and a new mechanism → spec + ux-brief + tech-doc; mechanism choice on existing FEATURE → tech-doc + ADR; new skill + its README example → .claude prompt + tech-doc).

**Ambiguity in classification — fold into D1.** One question to the user before dispatching anything.

**Read-only result of this step:** an ordered list of layers to produce, and for each layer the target path and which sub-agent will write it. No artifacts created yet.

## Step 3 — Dispatch wave

Order matters — lower layers depend on upper ones for vocabulary and scope.

1. **spec** (if needed) → dispatch `spec-writer`. It runs its own clarifying-questions phase, scope cohesion pass, and `docs/product/features/README.md` row update. Open questions returned → D1 → escalate to user → re-dispatch with answers.

2. **ux-brief** AND **tech-doc / ADR** — if both needed, dispatch **in parallel** (file-disjoint by construction: ux-brief lives in `docs/product/features/<slug>/`, tech-doc/ADR in `docs/engineering/`):
   - **ux-brief** → dispatch `ux-expert`. Open UX questions → D1.
   - **tech-doc / ADR** → dispatch `tech-writer`. One agent covers both kinds in one pass — see agent definition. Open questions → D1.

   If only one is needed, run it alone.

3. **.claude prompt** — write directly via Edit/Write. No sub-agent dispatch (an agent editing its own definition would race itself). Match the tone and structure of sibling skills/agents in `.claude/skills/*/SKILL.md` and `.claude/agents/*.md`. CLAUDE.md §Code style rules apply to prompt prose: rule-first, no history, no «после ретро по #N». Surface ambiguity that requires user input via D1 before writing — palette-first when ≥3 reasonable behaviour designs.

Each sub-agent / direct write returns: paths produced, index updates, open questions (if any). Open questions in any layer block forward progress on that layer; resolve via D1 and re-dispatch.

## Step 4 — Consistency pass

After all sub-agents return clean (no open questions), you run an **inline** read-only pass over the produced artifacts. No separate review agent — this is orchestrator-level cross-cutting verification.

Check, in order:

1. **Cross-references resolve.** Every link between artifacts (spec → ux-brief, spec → tech-doc, tech-doc → ADR, ADR → parent living doc) points to a file that exists and a section that exists.
2. **Terminology consistent.** Sample the central entities (e.g. "paired device", "rendezvous", "transport channel") across all touched artifacts. Same concept = same name everywhere. Variation = either mechanical rename or D2.
3. **Scope cohesion.** For each artifact, ask: "does every section depend on the central invariant of this artifact's feature/subsystem?" Sections describing concepts that survive without that invariant belong to a different artifact. (Rule from [spec-writer Step 3](../../agents/spec-writer.md) and [ux-expert "Before declaring ready"](../../agents/ux-expert.md).) Mechanical move = do it; concept-level scope dispute = D2.
4. **ADR parent-living-doc invariant.** If an ADR was created, the parent living doc exists and is referenced from the ADR Context section. Per [`docs/engineering/adr/README.md`](../../../docs/engineering/adr/README.md). If missing, dispatch `tech-writer` again to add the parent doc in this same pass.
5. **Indexes updated.** `docs/product/features/README.md` row added/updated if a spec was touched; `docs/engineering/README.md` entry added if a living doc or ADR was created.

Mechanical fixes (rename, add missing link, add missing index row) — apply directly. Conceptual fixes — D2 → escalate.

## Step 5 — Review wave

Dispatch in parallel on the staged diff (no PR yet — agents review the local working tree via `git diff main...HEAD`). Scope covers `docs/` and `.claude/` changes:

- `review-dod` — DoD criteria from the issue are covered by produced artifacts.
- `review-guides` — conformance to CLAUDE.md §Code style for all touched prose. For `docs/engineering/` artifacts additionally apply `docs/engineering/README.md` writing-style rules (rule-first, code examples on abstract types, no restating code). For `docs/product/features/` artifacts apply spec/ux-brief templates in `docs/product/features/_template.md`. For `.claude/` prompt edits apply sibling-skill/agent tone consistency.
- `review-reuse` — no duplication of existing specs / briefs / living docs, no contradictions with neighbours, no doc-vs-code drift.
- `review-adversarial` — runs after the above three with their combined findings as input; probes what was missed, what factual claims were not verified.

The other reviewers (`review-correctness`, `review-tests`, `review-platform`, `review-ui`, `review-architecture`, `review-ux`) **do not run** — there is no code, no UI; UX brief is reviewed for *itself* via consistency pass, not for its implementation.

Iteration: aggregate `[REQUIRED]` findings, re-dispatch the responsible sub-agent (which produced the artifact the finding targets) with the findings as input — for `.claude` prompt edits, apply the fixes inline since there is no sub-agent. Same precision-of-transfer discipline as `/implement` Step 5: pass findings close to the reviewer's wording; do not soften or narrow.

**Iteration limit: 2.** Docs converge faster than code. Not converged after 2 → escalate to user with remaining findings; signals a scope/intent problem the loop cannot resolve.

## Step 6 — Commit, present (D5), push, PR

Commit on the feature branch (no push):

```bash
git add docs/ .claude/
git commit -m "#<N>: <message>"
```

Present to user:
- Layers produced and artifact paths.
- DoD verdict: all `[DONE]` (from `review-dod`).
- Any `[UNVERIFIABLE]` from reviewers.
- Proposed PR title and body.

Ask: "Push and create PR?" Wait for explicit OK (D5).

After OK:

```bash
git push -u origin docs/<N>-<short-slug>
gh pr create --title "<title>" --body "<...>"
```

PR body must include: layers touched, artifact paths, DoD checklist, `Dependency check: n/a (docs-only)`.

Report PR URL to user. Next step is manual review, then `/close-issue <N>`.

## Notes

- This orchestrator does NOT call `/close-issue` automatically. Merge is always a user decision.
- This skill is for one issue at a time. Multiple parallel docs issues = multiple invocations on multiple worktrees.
- Worktree cleanup runs on `Stop` hook regardless of the `docs/<N>-<slug>` naming pattern.
- If at any point a sub-agent reports an open question (architectural / product decision) — escalate immediately. Sub-agents cannot decide; orchestrator does not invent.
- Token discipline: every sub-agent runs in its own context. Your main thread holds only the layer plan, per-artifact summaries, and gate decisions. If context exceeds 50% — pause and summarize before continuing.
- **Relation to `/implement`.** `/implement` detects docs-only issues at its Step 1 and delegates here, then exits. If during a code-track FEATURE an architectural decision worth an ADR arises mid-flight, `/implement` escalates via G3 (plan ambiguity) — the ADR is then handled either as a separate `/document` issue first or, when scope allows, by writing it directly in the same PR alongside the code (out of band of this orchestrator).
