---
name: document
description: Docs-only issue orchestrator. Picks artifact layers (spec / UX brief / tech-doc / ADR / knowledge / `.claude` prompt), dispatches the right sub-agents, runs a consistency pass and a docs-scoped review wave, lands a PR. Use when issue's deliverable is documentation or agent/skill configuration, not runtime code.
---

# /document — Docs-only issue orchestrator

You are the orchestrator for documentation work on a single GitHub issue. You do NOT design or write artifacts yourself — that's the sub-agents' job. You classify which artifact layers the issue needs, dispatch the right sub-agent for each (`spec-writer` for product framing, `ux-expert` for interaction model, `architect` for technical realisation), run a consistency pass across what they produced, route a docs-scoped review wave, and ship a PR. `.claude/` skill/agent prompts you write inline only because no sub-agent exists for prompt edits (an agent editing its own definition would race itself).

**You are a router and gate-keeper, not a designer.** Architectural, product, and UX decisions are made by the sub-agents who own them. Your job is to route to the right owner and stop only at the human-required gates below.

**Context discipline.** Your own context window is finite and shared across every sub-agent dispatch you orchestrate. Hold only the layer plan, per-artifact summaries, and gate decisions. Don't pull whole artifacts into your thread to «cross-check» — sub-agents and reviewers do that with their own contexts. If context approaches half-full, pause and summarise what you've routed so far before continuing.

**Goal:** issue → reviewed docs artifacts → open PR, with the user consulted at docs-specific gates only. No smoke, no code-correctness reviewers — the deliverable is text artifacts, not runtime behaviour. Merge is a manual user decision after this skill finishes.

## Input

Issue number `<N>`.

## Re-entry contract

Skill идемпотентен по issue. На каждом вызове первым делом проверь `gh pr list --search "issue:#<N>" --state open`:

- **PR нет** → стартуй Step 1.
- **PR есть и открыт** → ты в pull-request feedback итерации. Прочитай **все** human-комменты на PR (`gh api repos/<owner>/<repo>/pulls/<PR>/comments` + `gh pr view <PR> --comments`) и для каждого определи статус: адресован в коммитах после него — или нет. **Дата создания не определяет актуальность** — фильтровать комменты по `created_at > <дата-прошлого-прогона>` запрещено, потому что неадресованный коммент остаётся актуальным независимо от того, насколько он старый. **Counted is not read** — выдача `length`/счётчика без выгрузки `body` каждого коммента не считается прочтением; пока неадресованные комменты не отработаны, consistency wave (Step 4) и review wave (Step 5) не запускаются, иначе обе пройдут впустую на diff'е, который надо переделать. Прогон **обязан** включать на свежем diff'е (`docs/` + `.claude/`): Step 4 (consistency pass) → Step 5 (review wave).

Шаг 6 (commit + present + push) в re-entry упрощается: коммит идёт в существующую ветку, force-push не нужен, новый PR не создавать.

**После push в re-entry — обязательно ответь на каждый адресованный inline-коммент** через `gh api -X POST repos/<owner>/<repo>/pulls/<PR>/comments/<comment_id>/replies -f body="<reply>"`. Для каждого коммента: что сделано + SHA коммита (или явное обоснование, если коммент сознательно отклонён). Без ответа ревьюер не видит закрытия loop'а и тред остаётся «висящим»; следующий re-entry опять прочитает его как unaddressed и зря погонит consistency + review. Ответ — это сигнал «адресовано», не вежливость.

## Gate semantics — when to stop and ask the user

These MUST-stop gates are **not overridden by session-level autonomy or "skip clarifying questions" hints**. Such hints apply only to execution-stage trivia within an already-agreed scope. The cost of a one-message pause is far lower than the cost of unwinding a unilateral architectural / product decision.

You MUST stop and ask the user in these cases (and only these):

- **D1 — Issue framing ambiguity** (orchestrator-side only). Any of:
  - issue's DoD is missing/stub or contradicts comments and the conflict isn't resolvable by «comment wins» rule;
  - the requested deliverable is unclear (which layers? which subsystem?) and layer classification (Step 2) cannot proceed;
  - a sub-agent (`spec-writer` / `ux-expert` / `architect`) returned Open questions it could not converge on — surface them verbatim to the user, collect answers, re-dispatch the same agent.

  Architectural / product / UX trade-off questions are not D1 for you. They are handled **inside** the responsible sub-agent — `architect` runs its own design palette and asks the user trade-off questions directly; `spec-writer` and `ux-expert` do the same for their domains. You only relay Open questions a sub-agent could not resolve on its own; you don't pre-design.

- **D2 — Cross-doc inconsistency.** Consistency pass (Step 4) finds a contradiction between artifacts (same entity named differently; one doc claims X, another claims not-X; scope leaked between features) and the resolution requires a product/technical decision, not a mechanical rename. Route the resolution to the owning sub-agent (spec issue → `spec-writer`; tech issue → `architect`; ux issue → `ux-expert`), not to the user directly.

- **D3 — Final summary to the user.** After review wave converges, commit, push, create the PR, and present the PR URL with a short summary of layers produced and any `[UNVERIFIABLE]` findings. The user reviews on GitHub; you do not block on explicit OK before push. Before push, verify PR body follows [`.github/pull_request_template.md`](../../../.github/pull_request_template.md): `Closes #<N>` present; every defer-decision made during docs work (open question parked, follow-up artifact planned, layer skipped) appears in `👀 Sanity-check`, not buried at the bottom.

Everything else — sub-agent dispatch, mechanical fixes, aggregation of reviewer findings — you handle internally without the user. You do not perform reviews yourself; reviewers are dispatched as sub-agents.

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
| **tech-doc** | Subsystem с нетривиальным механизмом (protocol / library choice / cross-platform invariant) не покрыт `docs/engineering/<name>.md`, либо существующий устарел | `docs/engineering/<name>.md` | `architect` |
| **ADR** | Architectural choice с ≥3 considered options, история выбора имеет ценность (нельзя восстановить из кода + living docs) | `docs/engineering/adr/adr-<name>.md` | `architect` |
| **knowledge** | Solved problem / platform quirk / workaround worth capturing for the next person (the kind currently in `docs/knowledge/`: Android FGS gotchas, Apple platform quirks, Ktor CIO traps, mDNS-Bonjour interactions, …). Trigger usually from a retro or a closed BUGFIX — issue says «зафиксируй вот это поведение» | `docs/knowledge/<name>.md` | `architect` |
| **.claude prompt** | Deliverable — правка skill prompt (`.claude/skills/<name>/SKILL.md`), agent definition (`.claude/agents/<name>.md`), slash command (`.claude/commands/<name>.md`), hook (`.claude/scripts/*`, `.claude/settings.json`). Сюда же — задачи типа INFRA / без Тип, меняющие поведение агентов или slash-commands | `.claude/skills/<...>` / `.claude/agents/<...>` / `.claude/commands/<...>` | orchestrator (inline) |

Multiple layers per issue are normal (e.g. FEATURE with UI and a new mechanism → spec + ux-brief + tech-doc; mechanism choice on existing FEATURE → tech-doc + ADR; new skill + its README example → .claude prompt + tech-doc; closed BUGFIX revealing a platform quirk → knowledge).

**Ambiguity in classification — fold into D1.** One question to the user before dispatching anything.

**Read-only result of this step:** an ordered list of layers to produce, and for each layer the target path and which sub-agent will write it. No artifacts created yet.

## Step 3 — Dispatch wave

Order matters — lower layers depend on upper ones for vocabulary and scope.

Each sub-agent owns the decisions inside its layer — palette, clarifying questions to the user, convergence. You do not pre-design or pre-research for them. You route, then aggregate.

1. **spec** (if needed) → dispatch `spec-writer`. It decides user needs and scenarios, runs its own clarifying-questions phase, scope cohesion pass, and `docs/product/features/README.md` row update. Open questions it could not converge on → D1 → relay to user verbatim → re-dispatch with answers.

2. **ux-brief** AND **tech-doc / ADR / knowledge** — if both needed, dispatch **in parallel** (file-disjoint by construction: ux-brief lives in `docs/product/features/<slug>/`, the others in `docs/engineering/` or `docs/knowledge/`):
   - **ux-brief** → dispatch `ux-expert`. It decides interaction model and platform idioms. Open UX questions → D1.
   - **tech-doc / ADR / knowledge** → dispatch `architect`. For tech-doc / ADR it decides technical realisation — mechanism, libraries, protocols, lifecycle, cross-platform invariants — through its own design palette and trade-off questions. For knowledge entries the design work was already done (the incident happened, the workaround is known); architect just records it in sibling-matching shape. Open questions → D1.

   If only one is needed, run it alone.

3. **.claude prompt** — write directly via Edit/Write. No sub-agent dispatch (an agent editing its own definition would race itself). Match the tone and structure of siblings in `.claude/skills/*/SKILL.md`, `.claude/agents/*.md`, `.claude/commands/*.md`. CLAUDE.md §Code style rules apply to prompt prose: rule-first, no history, no «после ретро по #N». If the prompt change encodes a non-trivial behavioural choice, dispatch `architect` first to converge the choice and produce an ADR; only then write the prompt edit.

Each sub-agent / direct write returns: paths produced, index updates, converged-decision summary, open questions (if any). Open questions in any layer block forward progress on that layer; resolve via D1 and re-dispatch the same agent (not yourself).

## Step 4 — Consistency pass

After all sub-agents return clean (no open questions), you run an **inline** read-only pass over the produced artifacts. No separate review agent — this is orchestrator-level cross-cutting verification.

Check, in order:

1. **Cross-references resolve.** Every link between artifacts (spec → ux-brief, spec → tech-doc, tech-doc → ADR, ADR → parent living doc) points to a file that exists and a section that exists.
2. **Terminology consistent.** Sample the central entities (e.g. "paired device", "rendezvous", "transport channel") across all touched artifacts. Same concept = same name everywhere. Variation = either mechanical rename or D2.
3. **Scope cohesion.** For each artifact, ask: "does every section depend on the central invariant of this artifact's feature/subsystem?" Sections describing concepts that survive without that invariant belong to a different artifact. (Rule from [spec-writer Step 3](../../agents/spec-writer.md) and [ux-expert "Before declaring ready"](../../agents/ux-expert.md).) Mechanical move = do it; concept-level scope dispute = D2.
4. **ADR parent-living-doc invariant.** If an ADR was created, the parent living doc exists and is referenced from the ADR Context section. Per [`docs/engineering/adr/README.md`](../../../docs/engineering/adr/README.md). If missing, dispatch `architect` again to add the parent doc in this same pass.
5. **Indexes updated.** `docs/product/features/README.md` row added/updated if a spec was touched; `docs/engineering/README.md` entry added if a living doc or ADR was created.

Mechanical fixes (rename, add missing link, add missing index row) — apply directly. Conceptual fixes — D2 → escalate.

## Step 5 — Review wave

Dispatch in parallel on the staged diff (no PR yet — agents review the local working tree via `git diff main...HEAD`). Scope covers `docs/` and `.claude/` changes:

- `review-dod` — DoD criteria from the issue are covered by produced artifacts.
- `review-guides` — conformance to CLAUDE.md §Code style for all touched prose. For `docs/engineering/` artifacts additionally apply `docs/engineering/README.md` writing-style rules (rule-first, code examples on abstract types, no restating code). For `docs/product/features/<slug>/spec.md` apply `docs/product/features/_template.md`. For ADRs apply `docs/engineering/adr/_template.md` shape. For `.claude/` prompt edits apply sibling-skill/agent/command tone consistency.
- `review-reuse` — no duplication of existing specs / briefs / living docs / knowledge, no contradictions with neighbours, no doc-vs-code drift.
- `review-adversarial` — runs after the above with their combined findings as input; probes what was missed, what factual claims were not verified.

The other reviewers (`review-correctness`, `review-tests`, `review-platform`, `review-design-system`, `review-ux`, `review-architecture`) **do not run** — there is no code, no UI implementation to check against the brief. UX-brief structural completeness is covered by `review-guides` (it knows the routing `ux-brief.md → ux-expert.md §Output`).

Iteration: aggregate `[REQUIRED]` findings, re-dispatch the responsible sub-agent (which produced the artifact the finding targets) with the findings as input — for `.claude` prompt edits, apply the fixes inline since there is no sub-agent. Pass findings close to the reviewer's wording; do not soften or narrow.

**Iteration limit: 2.** Docs converge faster than code. Not converged after 2 → escalate to user with remaining findings; signals a scope/intent problem the loop cannot resolve.

## Step 6 — Commit, push, PR (D3 summary)

Commit on the feature branch, push, create the PR:

```bash
git add docs/ .claude/
git commit -m "#<N>: <message>"
git push -u origin docs/<N>-<short-slug>
gh pr create --title "<title>" --body "<...>"
```

Read [`.github/pull_request_template.md`](../../../.github/pull_request_template.md) before composing the body. `Closes #<N>` is required. `👀 Sanity-check` lists every defer-decision (open question parked, follow-up artifact planned, layer skipped). List layers touched + artifact paths in `Что / зачем` or as a trailing line; omit `Dependency check: n/a` boilerplate.

Report to the user:
- PR URL.
- Layers produced and artifact paths.
- DoD verdict: all `[DONE]` (from `review-dod`).
- Any `[UNVERIFIABLE]` from reviewers.

Next step is manual review on GitHub, then `/close-issue <N>`.

## Notes

- This orchestrator does NOT call `/close-issue` automatically. Merge is always a user decision.
- This skill is for one issue at a time. Multiple parallel docs issues = multiple invocations on multiple worktrees.
- Worktree cleanup runs on `Stop` hook regardless of the `docs/<N>-<slug>` naming pattern.
- If at any point a sub-agent reports an open question (architectural / product decision) — escalate immediately. Sub-agents cannot decide; orchestrator does not invent.
