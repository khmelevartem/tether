---
name: implement
description: End-to-end implementation orchestrator for a GitHub issue. Plans, dispatches coder sub-agent, runs a fast review loop (coder ↔ reviewers) without user in the loop, runs smoke, and reports to user only at human-required gates (AC ambiguity, root cause uncertainty, smoke failure, final approval). Use when starting work on an issue.
---

# /implement — Issue-to-PR orchestrator

You are the orchestrator for implementing a single GitHub issue. You do NOT write code or review code yourself. You dispatch sub-agents and decide when to escalate to the user.

**Goal:** remove the user from the inner `code → review → fix → review` cycle. The user is consulted at human-required gates only.

## Input

Issue number `<N>`.

## Gate semantics — when to stop and ask the user

You MUST stop and ask the user in these cases (and only these):
- **G1. Spec or AC ambiguity** — issue's DoD is missing/stub, feature spec missing for FEATURE type, blocking open questions in spec. **Mitigation:** dispatch `spec-writer` first; only stop at user if `spec-writer` has clarifying questions or the issue is non-FEATURE without DoD.
- **G2. BUGFIX root cause** — root cause must be confirmed before any fix. **Mitigation:** dispatch `bug-reproducer`; only stop at user if it reports CANNOT REPRODUCE or none of the listed hypotheses match.
- **G2.5. Publication of confirmed cause** — once `bug-reproducer` returns a confirmed cause, show the paste-ready block to the user and wait for explicit OK before `gh issue comment`. Publishing to a GitHub issue is a team-visible action; it does not happen without a user gate.
- **G3. Plan ambiguity** — plan conflicts with loaded engineering guides and you have no clean way to resolve.
- **G4. Smoke red/yellow** — smoke verdict is not 🟢 after the inner loop.
- **G5. Final approval before push** — after the inner loop converges to APPROVE and smoke is green, present the committed-but-not-pushed diff to the user for approval. Push + PR creation happen only after the user OKs.

Everything else — implementation details, reviewer findings, fix iterations — you handle internally without the user.

## Step 1 — Read issue + worktree setup

```bash
gh issue view <N> --json title,body,labels,comments
gh pr list --search "issue:#<N>" --state open --json number,isDraft,headRefName
```

Classify PR type. For FEATURE, look up `docs/product/features/README.md` for spec (specs live at `docs/product/features/<slug>/spec.md`).

**Critical reading.** Воспринимай описание issue как **стартовую точку, не как факт**. Подсвечивай и эскалируй пользователю до начала работы, если видишь хотя бы один пробел:
- упомянута только одна платформа, хотя задача общая;
- не описаны ошибки и fail-paths;
- непонятно, как тестировать (нет указаний на edge cases / runtime check);
- BUGFIX без хотя бы предполагаемой причины бага;
- фразы-затычки: «дополни если есть чем», «должно работать корректно», «и так далее».

Любой такой пробел — повод вернуться к G1, не «допилить по дороге».

**Existing draft PR.** Если по issue уже есть открытый PR (даже draft) — НЕ пропускай Step 5 (inner loop) и Step 7 (full pre-PR review) на текущем diff'е. Без них orchestrator превращается в одного исполнителя, и весь quality framework обходится.

**Worktree setup — do this BEFORE dispatching any agent that edits files.** If you are not already in `.claude/worktrees/<branch>/`:

```bash
git worktree add .claude/worktrees/feature-<N>-<short-slug> -b feature/<N>-<short-slug> main
cd .claude/worktrees/feature-<N>-<short-slug>
```

All subsequent agent dispatches happen with this as cwd. Skipping this step means `spec-writer` would edit main checkout.

## Step 2 — Resolve gates G1, G2

**G1 handling.** If FEATURE and (no spec, or spec is `(stub)`, or spec has blocking open questions) → dispatch `spec-writer`. It will draft questions for the user or produce a scoped spec. Only escalate to user with `spec-writer`'s question list.

**G2 handling.** If BUGFIX → dispatch `bug-reproducer`. It reproduces locally, verifies each hypothesis, and returns a confirmed cause as structured paste-ready text. It does NOT post to GitHub. If reproduction failed or no hypothesis matched → escalate to user.

**G2.5 handling.** After receiving a confirmed cause from `bug-reproducer`, show the paste-ready block to the user and ask: «Опубликовать как комментарий к issue #<N>?» Wait for explicit OK before `gh issue comment <N>`. Reason: a team-visible side effect must not happen without an explicit gate, even if the orchestrator is doing it instead of the agent — that just moves the problem one level up. If the user says no — keep the cause locally as a constraint for `coder`; do not publish.

The confirmed root cause becomes a hard constraint for the `coder` in Step 5 regardless of whether it was published.

Load relevant engineering guides from `docs/engineering/` — only those actually touching the task:

| Task involves | Read |
|---|---|
| any code | `dependency-injection.md` (DI checklist) |
| new component / layer / module split | `architecture-principles.md`, `modules.md` |
| UI / Compose | `presentation-layer.md` |
| new tests | `testing.md` |

## Step 3 — UX brief (FEATURE with user-facing UI only)

Skip unless the issue is `FEATURE` AND the task scope includes UI work (screen, component, navigation — not pure logic/network/infra).

If applicable: dispatch `ux-expert` with the spec slug. It produces or updates `docs/product/features/<slug>/ux-brief.md` — the UX brief that `ui-expert` will consume as a contract in Step 5. The brief is committed as part of the PR.

**Open UX questions** returned by `ux-expert` fold into Gate G1: surface them to the user, collect answers, re-dispatch `ux-expert`. Do not proceed to Step 4 with an unresolved UX-questions section.

**Recovery in inner loop.** If `ui-expert` halts in Step 5 reporting "UX brief missing" (the Step 3 skip judgement was wrong, or new UI scope emerged mid-plan) — re-dispatch `ux-expert` and resume the inner loop. This is machine-resolvable; do not escalate to user.

## Step 4 — Plan

Use the built-in `Plan` agent (or `general-purpose` if plan unavailable) to produce a short implementation plan: phases, files to touch, validation strategy.

**Выбор уровня фикса.** Issue указывает место бага, но не обязательно место фикса. Когда root cause описывает класс багов (а не один экземпляр) или когда параллельные реализации содержат тот же дефект — рассмотри фикс на уровень выше: изменение типа / контейнера / контракта, делающее класс багов невозможным. Сравни стоимость: N point-фиксов vs 1 структурный. Если выбираешь point — явно перечисли в плане параллельные места, остающиеся с дефектом, и заведи follow-up issue до начала кодинга.

**Track splitting.** Default is **sequential single-track** execution. Split into parallel tracks ONLY if the plan can enumerate file-level disjoint sets: track A's files ∩ track B's files = ∅. The plan must list explicit file paths per track. If any file appears in two tracks → tracks are not independent → execute sequentially.

Apply Gate G3 if the plan conflicts with guides → present to user, stop. Otherwise, accept and continue.

## Step 5 — Inner loop: coder ↔ fast reviewers

Per track (or sequentially if single track):

**Iteration:**

1. Dispatch the implementing agent with the plan slice:
   - **UI work** (Compose, screens, components, theming, navigation) → `ui-expert`
   - **Everything else** (network, discovery, protocol, persistence, build, infra) → `coder`
   - **Mixed** — split into sub-tracks if disjoint files, else dispatch `coder` which can pull in `ui-expert` via Agent tool.
2. Once the implementing agent reports green tests, dispatch a **fast reviewer wave** in parallel:
   - `review-dod` (always)
   - `review-correctness` (always unless DOCS/REFACTOR)
   - `review-guides` (always)
   - `review-tests` (always unless DOCS/INFRA)
   - `review-platform` (if diff touches platform source sets)
   - `review-ux` (if diff touches `composeApp/src/**` — the agent itself decides skip vs. block on missing brief)
   Skip `review-reuse` and `review-adversarial` here — they run in the simplify wave (Step 6) and the full review (Step 7).
3. If every reviewer says `APPROVE` and zero `[REQUIRED]` → track done.
4. Else → aggregate `[REQUIRED]` findings, dispatch the implementing agent again with the findings as input:

> Previous review found these issues that block the PR. Address each. For each finding, classify as pointwise or structural; for structural findings, do a symmetry pass per your agent definition — check sibling files, sibling methods, sibling platforms, sibling source sets for the same anti-pattern, and fix in this same pass. Do not change anything outside the PR's scope.
>
> <list of [REQUIRED] findings with file:line>

   Go back to step 2.

**Iteration limit:** 4 inner iterations per track. If not converged after 4 — escalate to user with remaining findings; this signals a plan/scope problem the loop cannot fix.

## Step 6 — Simplify wave

After all tracks converge. Iterative fix cycles accumulate scaffolding (temp helpers added then never removed, defensive branches, comments restating code) AND duplication (each iteration adds private helpers that fast reviewers don't cross-check across tracks).

Dispatch the implementing agent once more:

> All findings are resolved. Make one simplification pass over the diff: remove dead branches, inline single-use helpers, drop comments restating code, collapse trivial wrappers. Do not change behavior; do not touch anything outside the diff. Run `./gradlew allTests -q` after.

If anything was simplified — re-run **the same set of agents that ran in Step 5 for this PR type** (i.e. dod + guides + correctness + tests + platform-if-touched + ux-if-touched) **plus `review-reuse`** on the simplified diff. `review-reuse` is critical here because duplication is what most likely accumulated across iterations and tracks. `review-platform` and `review-ux` follow the same skip rules as Step 5 (platform set touched / `composeApp/src/**` touched). If clean, proceed.

## Step 7 — Full pre-PR review (inline, not via /code-review skill)

`/code-review` skill requires an existing PR (it posts via `gh pr review`). At this step the PR does not exist yet — Step 10 creates it. So instead of calling the skill, **orchestrate the same agent fan-out inline, without GitHub publication**:

1. Wave A in parallel: `review-dod`, `review-guides`, `review-reuse`, plus (if applicable to PR type / diff) `review-correctness`, `review-tests`, `review-platform`, `review-ux`. Each agent receives the issue number and is told to review the local working tree (`git diff main...HEAD`) instead of a PR. `review-ux` runs whenever the diff touches `composeApp/src/**`; the agent itself decides skip vs. block.
2. Wave B: `review-adversarial` with the combined Wave A findings as input.
3. Aggregate. Apply any `[REQUIRED]` via the implementing agent (with the symmetry-pass instruction). Re-run until approved or 2 iterations.

No `gh pr review` here — findings are consumed locally only. The post-PR `/code-review` skill will be invoked separately after Step 10 if a reviewer requests it, or as part of normal team review.

## Step 8 — Smoke

Run `/smoke-test` blocks relevant to the diff. Selection heuristic:

| Diff touches | Run |
|---|---|
| `FileServer` / `FileClient` / CLI / network protocol | Desktop CLI + Desktop↔Desktop blocks |
| Android FGS / mDNS / Android networking | Android block (if device attached) |
| native source sets (`iosMain`, `macosMain`, `appleMain`) | native compile block |
| DOCS-only, `.claude/`-only, comments-only | nothing |
| Other production code | judgement call — when in doubt, run Desktop blocks |

If the PR introduces a new critical happy-path not covered by smoke (start-time failure point, cross-platform UI, new external interface) — extend `.claude/skills/smoke-test/SKILL.md` in this same PR before running. Keep blocks lean; smoke runs often.

Record the verdict (🟢/🟡/🔴) and blocks executed.

Apply Gate G4: if 🟡/🔴 → present to user, stop.

## Step 9 — Commit locally, present to user (Gate G5)

Only after Step 8 is 🟢. Commit on the feature branch (no push):

```bash
git add <relevant files>
git commit -m "#<N>: <message>"
```

Present to user:
- Files changed (summary)
- AC: all `[DONE]` (from review-dod)
- Smoke: 🟢 with blocks
- Any `[UNVERIFIABLE]` from reviewers
- Proposed PR title and body

Ask: "Push and create PR?" Wait for explicit OK.

## Step 10 — Push + PR (only after G5 OK)

```bash
git push -u origin feature/<N>-<short-slug>
gh pr create --title "<title>" --body "<...>"
```

PR body must include: AC verdict (DONE checklist), `## Dependency check` (if new deps), smoke verdict.

Report PR URL to user. Next step is manual verification, then `/close-issue <N>`.

## Notes

- This orchestrator does NOT call `/close-issue` automatically. Merge is always a user decision.
- Worktree cleanup: `.claude/scripts/cleanup-worktrees.sh` runs on `Stop` hook and removes any worktree whose remote branch is gone and whose PR is merged — it iterates **all** worktrees regardless of naming, so the `feature/<N>-<slug>` pattern is auto-cleaned after merge. No manual cleanup needed.
- If at any iteration the implementing agent reports an open question (not a fixable finding — e.g., "the issue says X but the existing pattern is Y, which to follow?") — escalate to user immediately. Agents cannot decide architectural questions.
- Token discipline: every sub-agent runs in its own context. Your main thread holds only the plan, per-iteration finding summaries, and gate decisions. If context exceeds 50% — pause and summarize before continuing.
- This skill is for one issue at a time. Multiple parallel issues = multiple invocations on multiple worktrees.
