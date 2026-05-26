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

Skill идемпотентен по issue. На каждом вызове первым делом проверь `gh pr list --search "issue:#<N>" --state open`:

- **PR нет** → стартуй Step 1.
- **PR есть и открыт** → ты в pull-request feedback итерации. **Сначала** перепроверь docs-only детекцию (Step 1 классификация) на текущем состоянии issue + diff'а PR: если задача docs-only, делегируй re-entry в `/document <N>` и завершайся (`/document` сам идемпотентен и подхватит этот PR). Иначе остаёшься в code-track re-entry.
- **Code-track re-entry.** На текущей feature-branch могут быть новые комменты ревьюера или коммиты после прошлого прогона. Прочитай **все** human-комменты на PR (`gh api repos/<owner>/<repo>/pulls/<PR>/comments` + `gh pr view <PR> --comments`) и для каждого определи статус: адресован в коммитах после него — или нет. **Дата создания не определяет актуальность** — фильтровать комменты по `created_at > <дата-прошлого-прогона>` запрещено, потому что неадресованный коммент остаётся актуальным независимо от того, насколько он старый. Прогон **обязан** включать на свежем diff'е: Step 4 (inner loop reviewers) → Step 5 (simplify) → Step 6 (full review wave A + adversarial) → Step 7 (smoke, скоуп по diff'у). Из дисциплины на re-entry ничего пропускать нельзя — иначе review-итерации проходят с меньшим качеством, чем первичная имплементация.

Шаг 8 (commit + push + final summary) — в re-entry упрощается: коммит идёт в существующую ветку, force-push не нужен, новый PR не создавать.

**После push в re-entry — обязательно ответь на каждый адресованный inline-коммент** через `gh api -X POST repos/<owner>/<repo>/pulls/<PR>/comments/<comment_id>/replies -f body="<reply>"`. Для каждого коммента: что сделано + SHA коммита (или явное обоснование, если коммент сознательно отклонён). Без ответа ревьюер не видит закрытия loop'а и тред остаётся «висящим»; следующий re-entry опять прочитает его как unaddressed и зря погонит inner loop. Ответ — это сигнал «адресовано», не вежливость.

## No-deflection principle

Когда кто-то — пользователь или ревьюер — задаёт вопрос об артефакте или требует изменения, ответ должен быть **по существу**: либо обоснование, почему артефакт остаётся как есть, либо реальная правка артефакта по сути. Промежуточные действия — деструкция «на всякий случай», полу-правка, оправдание через KDoc / комментарий / документацию вместо изменения кода — это deflection. Запрещено.

Два проявления, оба в inner-loop:

**1. Пользовательский вопрос мид-loop.** Сообщение «точно нужно X?» / «зачем X?» / «не лучше ли A вместо B?» / «может вынести в Y?» / «здесь nullable обязательно?» — это запрос на твоё суждение, не директива. Дефолт:

- **Обоснуй** — что артефакт даёт, чего нет в альтернативе, или почему ограничение оправдано.
- **Уточни встречно** — «снести X совсем или сомневаешься в Y внутри X?», «вынести наружу или переименовать?».

Молчаливое выполнение предполагаемого действия (удалить, переместить, переписать, заменить тип, развернуть nullable) на одном вопросе запрещено. Если бы пользователь хотел действие, он написал бы директивно. Вопрос заслуживает ответа первым; директива придёт после.

**2. Ответ coder'а на reviewer finding.** Если coder адресовал finding «снеси X» / «убери Y» правкой, защищающей X через KDoc / комментарий / параграф в docs (вместо реального удаления / замены / переделки) — deflection, отклоняй. Ревьюер хотел X убрать, не задокументировать. Re-dispatch coder'а с явной формулировкой «правка X сама, без оправдания через документацию».

## Gate semantics — when to stop and ask the user

These MUST-stop gates are **not overridden by session-level autonomy or "skip clarifying questions" hints**, wherever such hints come from. Such hints apply only to execution-stage trivia within an already-agreed scope (naming, formatting, refactoring choices). They do not apply to gate evaluation. The cost of a one-message pause is far lower than the cost of unwinding a unilateral architectural / product decision.

You MUST stop and ask the user in these cases (and only these):
- **Spec or AC ambiguity** — issue's DoD is missing/stub, feature spec missing for FEATURE type, blocking open questions in spec. **Mitigation:** dispatch `spec-writer` first; only stop at user if `spec-writer` has clarifying questions or the issue is non-FEATURE without DoD.
- **BUGFIX root cause** — root cause must be confirmed before any fix. **Mitigation:** dispatch `bug-reproducer`; only stop at user if it reports CANNOT REPRODUCE or none of the listed hypotheses match. **The reproducer must always attempt to observe the symptom**, even when the cause looks structurally evident from issue text + code grep. Do not silently proceed as if the bug were confirmed.
- **Cause-vs-Issue divergence** — when `bug-reproducer`'s confirmed cause materially diverges from what the issue body claims (different mechanism / different platform scope / different observable symptom / different severity class), STOP and ask the user to choose: «close #N as misdiagnosis and open a new issue with the real cause» vs «rewrite #N body to match the confirmed cause». Do not silently edit the issue body and continue — that loses the trail of how the diagnosis evolved, and it bundles two different bugs (the one reported, the one found) into one PR's history.
- **Publication of confirmed cause** — once `bug-reproducer` returns a confirmed cause, show the paste-ready block to the user and wait for explicit OK before `gh issue comment`. Publishing to a GitHub issue is a team-visible action; it does not happen without a user gate.
- **Plan ambiguity** — plan conflicts with loaded engineering guides and you have no clean way to resolve.
- **Smoke red/yellow** — smoke verdict is not 🟢 after the inner loop.
- **Final summary to the user** — after the inner loop converges to APPROVE and smoke is green, commit + push + create the PR, then present the PR URL with a short summary (files changed, AC verdict, smoke verdict, any `[UNVERIFIABLE]` findings). The user reviews on GitHub; do not block on explicit OK before push. Before push, verify PR body follows [`.github/pull_request_template.md`](../../../.github/pull_request_template.md): `Closes #<N>` present; every defer-decision made during implementation (skipped scope, TODO/FIXME left in code, follow-up issue planned) appears in `👀 Sanity-check`, not buried at the bottom — user redirects defer-vs-do-now from this section.

Everything else — implementation details, reviewer findings, fix iterations — you handle internally without the user.

## Step 1 — Reconnaissance and setup

```bash
gh issue view <N> --json title,body,labels,comments
gh pr list --search "issue:#<N>" --state open --json number,isDraft,headRefName
```

**Comments — это не дискуссия, это потенциально canon-update body.** При противоречии comment'а с body — приоритет comment'у, эскалируй пользователю одной строкой.

**Critical reading.** Воспринимай описание issue как **стартовую точку, не как факт**. Подсвечивай и эскалируй пользователю до начала работы, если видишь хотя бы один пробел:
- упомянута только одна платформа, хотя задача общая;
- не описаны ошибки и fail-paths;
- непонятно, как тестировать (нет указаний на edge cases / runtime check);
- BUGFIX без хотя бы предполагаемой причины бага;
- фразы-затычки: «дополни если есть чем», «должно работать корректно», «и так далее».

Любой такой пробел — повод вернуться к гейту spec/AC ambiguity, не «допилить по дороге».

**Classification.** На основе issue body — `**Тип:**` поля, описания deliverable, label'ов — отнеси задачу к одной из веток:

| Trigger | Track | Действие |
|---|---|---|
| `**Тип:** DOCS` | docs-only | делегируй в `/document <N>` и завершайся |
| `**Тип:** FEATURE` + явный маркер docs-only (фраза «docs-only» / «only docs» / «scope: docs» в body/DoD, либо label `docs-only`) | docs-only | то же |
| Issue без `**Тип:**` AND deliverable **ограничен исключительно** правкой `.claude/` или `docs/` (никакого кода в исходниках) | docs-only | то же |
| `**Тип:** INFRA` AND deliverable **ограничен исключительно** правкой `.claude/` файлов (skill prompts, agent definitions, hooks) | docs-only | то же |
| `**Тип:** FEATURE` / `BUGFIX` / `REFACTOR` / `INFRA` с deliverable в исходниках или build/CI/scripts (даже если попутно нужен ADR) | code-track | продолжай Step 2–8 |

При делегации в /document: «Эта задача — docs-only. Запускаю `/document <N>` и завершаюсь.» `/document` сам обработает выбор слоёв, консистентность, ревью и PR. **НЕ делегируй** код-FEATURE с попутным ADR — для таких задач Step 4 диспатчит `architect` mid-flight, и ADR пишется в той же PR что и код.

### Doc discovery

Before planning and any dispatch — scan the doc corpus and pull up topically-matching artifacts. Recon is cheap: filenames are designed for topic-match.

- **Product features** — `ls docs/product/features/` (+ `docs/product/features/README.md` as the index). If a slug matches our scope — read its `spec.md` (and `ux-brief.md` if present).
- **Product context** — `docs/product/*.md` covers broad product framing (vision, audience, roadmap, tech stack, security, …). Read lazily, only when you have conceptual doubt about scope, audience or timing relative to that framing.
- **Engineering living docs** — `ls docs/engineering/*.md`. These are **present-tense rules** for their subsystems — comply with the ones whose topic matches the task.
- **ADR** — `ls docs/engineering/adr/adr-*.md`. These are **why it was chosen**. For every ADR matching the task's topic, also read its **Revisit if** section and explicitly assess whether your work has tripped a trigger. If it has — the plan either confirms the ADR (false trigger) or includes a reversal with its own sub-plan (see `docs/engineering/adr/README.md` §Reversing an ADR).
- **Knowledge** — `ls docs/knowledge/*.md`. Solved-problem write-ups (platform quirks, library traps, workarounds) — check before starting so you don't debug from scratch something already recorded.
- **Glossary** — `docs/glossary.md`. Read up front; it's short and load-bearing for terminology — `review-glossary` blocks PRs that drift from it.

`CLAUDE.md` is harness-injected — no separate recon needed.

Mention the relevant documents you found in the briefing to the user (see below).

**Worktree setup — do this BEFORE dispatching any agent that edits files.** If you are not already in `.claude/worktrees/<branch>/`:

```bash
git worktree add .claude/worktrees/feature-<N>-<short-slug> -b feature/<N>-<short-slug> main
cd .claude/worktrees/feature-<N>-<short-slug>
```

All subsequent agent dispatches happen with this as cwd. Skipping this step means `spec-writer` would edit main checkout.

### Briefing back to the user

После прочтения issue и разведки, **перед** любым вопросом пользователю (gate-вопросы, Open questions от sub-agent'ов, classification-неоднозначности) выдай в чат короткий бриф 3–6 строк: что делаем, зачем (мотивация / контекст из issue), классификация (track + затрагиваемые слои/платформы). Если в этом же сообщении задаёшь вопросы — к каждому приложи 1–2 строки контекста (что говорит issue, какие варианты на столе), чтобы пользователь отвечал, не уходя на GitHub перечитывать тело. Бриф один на прогон; в re-entry не повторяй.

## Step 2 — Resolve early gates

### Spec / UX brief / AC ambiguity

If FEATURE and (no spec, or spec is `(stub)`, or spec has blocking open questions) → dispatch `spec-writer`. It will draft questions for the user or produce a scoped spec. Only escalate to user with `spec-writer`'s question list.

If the FEATURE scope includes user-facing UI (screen, component, navigation — not pure logic/network/infra) AND `docs/product/features/<slug>/ux-brief.md` is missing or stale relative to the spec → dispatch `ux-expert` after `spec-writer`. It produces or updates the brief; `ui-expert` later consumes it as a contract. Open UX questions returned by `ux-expert` fold back into this gate: surface verbatim to the user, collect answers, re-dispatch. The brief is committed as part of the PR.

**Recovery in inner loop.** If `ui-expert` halts in Step 4 reporting "UX brief missing" (the skip judgement was wrong, or new UI scope emerged mid-plan) — re-dispatch `ux-expert` and resume. This is machine-resolvable; do not escalate to user.

### BUGFIX root cause

If BUGFIX → dispatch `bug-reproducer`. It reproduces locally, verifies each hypothesis, and returns a confirmed cause as structured paste-ready text. It does NOT post to GitHub. If reproduction failed or no hypothesis matched → escalate to user.

### Publication of confirmed cause

After receiving a confirmed cause from `bug-reproducer`, show the paste-ready block to the user and ask: «Опубликовать как комментарий к issue #<N>?» Wait for explicit OK before `gh issue comment <N>`. Reason: a team-visible side effect must not happen without an explicit gate, even if the orchestrator is doing it instead of the agent — that just moves the problem one level up. If the user says no — keep the cause locally as a constraint for `coder`; do not publish.

The confirmed root cause becomes a hard constraint for the `coder` in Step 4 regardless of whether it was published.

## Step 3 — Plan

Use the built-in `Plan` agent (or `general-purpose` if plan unavailable) to produce a short implementation plan: phases, files to touch, validation strategy.

**Выбор уровня фикса.** Issue указывает место бага, но не обязательно место фикса. Когда root cause описывает класс багов (а не один экземпляр) или когда параллельные реализации содержат тот же дефект — рассмотри фикс на уровень выше: изменение типа / контейнера / контракта, делающее класс багов невозможным. Сравни стоимость: N point-фиксов vs 1 структурный. Если выбираешь point — явно перечисли в плане параллельные места, остающиеся с дефектом, и заведи follow-up issue до начала кодинга.

**Scope issue — стартовая точка, не клетка.** Список файлов в issue — отправная точка. Если для качественного решения нужно тронуть смежные классы или соседние платформы — расширяй scope в этом же PR. Follow-up issue только когда расширение реально ломает PR (новый таргет, широкая правка публичного контракта, кратный рост объёма, обнаружение отдельного бага). Notes / TODO, которые исполнитель сам добавил по ходу — доделываются здесь же.

**Track splitting.** Default is **sequential single-track** execution. Split into parallel tracks ONLY if the plan can enumerate file-level disjoint sets: track A's files ∩ track B's files = ∅. The plan must list explicit file paths per track. If any file appears in two tracks → tracks are not independent → execute sequentially.

**Plan conflicts with guides.** If the plan conflicts with loaded engineering guides → present to user, stop. Otherwise, accept and continue.

## Step 4 — Inner loop: coder ↔ fast reviewers

Per track (or sequentially if single track):

**Iteration:**

1. Dispatch the implementing agent with the plan slice:
   - **UI work** (Compose, screens, components, theming, navigation) → `ui-expert`
   - **Feature spec** → `spec-writer`
   - **Architectural design point** — plan from Step 3 surfaces a non-trivial mechanism / library / structural choice that `coder` should not make alone → `architect` first. It converges the choice (its own palette + user trade-off questions + ADR/living doc), returns a one-line decision summary; that summary then becomes a hard constraint for the subsequent `coder` dispatch in the same track.
   - **Everything else** (network, discovery, protocol, persistence, build, infra) → `coder`
   - **Mixed** — split into sub-tracks if disjoint files, else dispatch `coder` which can pull in `ui-expert` / `architect` via Agent tool.
2. **Перед dispatch'ем reviewer wave — закоммить изменения coder'а** на feature-branch (новый коммит или `--amend`, на твоё усмотрение). Reviewer'ы читают `git diff main...HEAD` и **в working tree не должно быть uncommitted изменений** на момент их запуска. Иначе часть агентов читает только committed state и шлёт stale [REQUIRED] на проблемах, которые уже починены, но не видны им — оркестратор тратит контекст на разбор фантомных flag'ов, плюс риск false-block'a. Один источник истины = один коммит per inner-loop iteration.
3. Dispatch a **fast reviewer wave** in parallel:
   - `review-dod` (always)
   - `review-correctness` (always unless DOCS/REFACTOR)
   - `review-guides` (always)
   - `review-glossary` (always)
   - `review-architecture` (always unless DOCS or trivial one-call-site BUGFIX / cosmetic refactor)
   - `review-tests` (always unless DOCS/INFRA)
   - `review-platform` (if diff touches platform source sets)
   - `review-ux` (if diff touches `composeApp/src/**` — the agent itself decides skip vs. block on missing brief)
   - `review-design-system` (if diff touches `composeApp/src/**`)
   - `review-visual` (if diff touches `composeApp/src/**` — the agent itself renders PNGs via Roborazzi and decides skip vs. block on missing brief)
   Skip `review-reuse` and `review-adversarial` here — they run in the simplify wave (Step 6) and the full review (Step 7).
4. If every reviewer says `APPROVE` and zero `[REQUIRED]` → track done.
5. Else → aggregate `[REQUIRED]` findings, dispatch the implementing agent again with the findings as input. Apply the same commit-before-review discipline as step 2:

> Previous review found these issues that block the PR. Address each. For each finding, classify as pointwise or structural; for structural findings, do a symmetry pass per your agent definition — check sibling files, sibling methods, sibling platforms, sibling source sets for the same anti-pattern, and fix in this same pass. Do not change anything outside the PR's scope.
>
> <list of [REQUIRED] findings with file:line>

   **Red CI test = broken code, not broken test.** Дефолт — чинить код. Удаление failing теста, переписывание в narrower fast-check, ослабление assertion/таймаута/входов — без явного апрува пользователя запрещены. Гипотеза «тест проверял не то» — эскалация к пользователю, не самостоятельное решение.

   **Точность передачи ревью.** Coder получает контекст cold и не верифицирует orchestrator'а — если ты перепаковал «снеси X» в «оправдай X через KDoc», coder сделает ровно последнее. Передавай findings максимально близко к исходным формулировкам ревьюера; не сужай и не смягчай. Если несколько findings сходятся на одном принципе — назови принцип явно в инструкции и перечисли ВСЕ сайты, где он применяется, даже если в комментариях упомянуты не все. Сомневаешься в интерпретации — эскалируй пользователю ДО dispatch'а, не после следующего раунда ревью. См. также `## No-deflection principle` — defensive-via-docs ответ coder'а на «снеси X» отклоняется по тому же правилу.

   Go back to step 2.

**Iteration limit:** 4 inner iterations per track. If not converged after 4 — escalate to user with remaining findings; this signals a plan/scope problem the loop cannot fix.

## Step 5 — Simplify wave

After all tracks converge. Iterative fix cycles accumulate scaffolding (temp helpers added then never removed, defensive branches, comments restating code) AND duplication (each iteration adds private helpers that fast reviewers don't cross-check across tracks).

Dispatch the implementing agent once more:

> All findings are resolved. Make one simplification pass over the diff: remove dead branches, inline single-use helpers, collapse trivial wrappers. 
> **For every comment / KDoc / prose paragraph in the diff — including `.claude/skills/**`, `docs/`, and Markdown — apply CLAUDE.md §Code style rules.** A sentence may be left only if it carries non-obvious context or further instructions. Remove: history of implementing or decision-making (except ADRs); mentions of things that do not exist in the artifact (a feature considered and dropped, a button we decided not to add) unless their absence is itself a non-obvious invariant a reader would otherwise assume; repetition of a fact already stated nearby.
> **Do not rephrase prose for brevity.** If a sentence is load-bearing and free of the issues above, leave its wording alone. Cut whole sentences when they fail the rule above; otherwise keep them as written. Word-count reduction on well-formed sentences is not a goal.
> Do not change behavior; do not touch anything outside the diff. Run `./gradlew allTests -q` after.

If anything was simplified — **закоммить simplification** (см. дисциплину Step 4 — reviewer'ы читают только committed diff), затем re-run **the same set of agents that ran in Step 4 for this PR type** (i.e. dod + guides + glossary + correctness + tests + platform-if-touched + ux-if-touched + design-system-if-touched + visual-if-touched) **plus `review-reuse`** on the simplified diff. `review-reuse` is critical here because duplication is what most likely accumulated across iterations and tracks. `review-platform`, `review-ux`, `review-design-system`, and `review-visual` follow the same skip rules as Step 4 (platform set touched / `composeApp/src/**` touched). If clean, proceed.

## Step 6 — Full pre-PR review (inline, not via /code-review skill)

`/code-review` skill requires an existing PR (it posts via `gh pr review`). At this step the PR does not exist yet — Step 8 creates it. So instead of calling the skill, **orchestrate the same agent fan-out inline, without GitHub publication**:

1. Перед Wave A — working tree должно быть чистым (committed). Если после Step 5 остались uncommitted правки — закоммить. Reviewer'ы читают `git diff main...HEAD`; uncommitted состояние вызывает stale-view findings.
2. Wave A in parallel: `review-dod`, `review-guides`, `review-glossary`, `review-reuse`, plus (if applicable to PR type / diff) `review-architecture`, `review-correctness`, `review-tests`, `review-platform`, `review-ux`, `review-design-system`, `review-visual`. Each agent receives the issue number and is told to review the local working tree (`git diff main...HEAD`) instead of a PR. `review-ux`, `review-design-system`, and `review-visual` all run whenever the diff touches `composeApp/src/**`; each agent decides skip vs. block.
3. Wave B: `review-adversarial` with the combined Wave A findings as input.
4. Aggregate. Apply any `[REQUIRED]` via the implementing agent (with the symmetry-pass instruction). Re-run until approved or 2 iterations.

No `gh pr review` here — findings are consumed locally only. The post-PR `/code-review` skill will be invoked separately after Step 8 if a reviewer requests it, or as part of normal team review.

## Step 7 — Runtime verification (smoke OR enforcement-probe)

### Smoke verdict

Две ветки. Выбирается по природе deliverable'а, не взаимоисключающие — для PR где меняется и фича и enforcer, делаются обе.

### 7a — Smoke (feature behavior)

Когда deliverable PR — runtime-поведение фичи (пользовательский путь, сетевой обмен, lifecycle).

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

Когда deliverable PR — сам enforcement mechanism (custom lint rule, CI guard, git hook, custom Gradle check, ktlint/detekt правило, schema validator). Unit-тесты механизма не доказывают, что он подключён через ServiceLoader / Gradle / hook chain — нужна инъекция через ту же дверь, через которую пройдёт реальный нарушитель.

Шаги:
1. Создай минимальный артефакт, нарушающий проверку, в реальном месте кодовой базы (там, где enforcer должен сработать — `src/.../Test.kt` для ktlint test rule, `.github/workflows/` для CI guard).
2. Запусти соответствующий Gradle/CI task (`./gradlew ktlintCheck`, `./gradlew <task>`, `git commit` для pre-commit hook).
3. Проверь: build FAILED **с ожидаемым сообщением** (rule id / hook name).
4. Удали probe, убедись через `git status -s` что ничего не осталось.

Если шаг 3 проходит зелёно — enforcer не подключён, несмотря на зелёные unit-тесты. Это red gate, эскалируй пользователю.

### Verdict

Record the verdict (🟢/🟡/🔴) per branch run, плюс блоки/probe path.

Если любая ветка 🟡/🔴 → present to user, stop.

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
- Manual test plan — 1–2 предложения с упором на регрессионный smoke и shipping behaviour, явно говори «backend-only, нечего тестировать руками» если нет visible diff'а.

Next step is manual review on GitHub, then `/close-issue <N>`.

## Notes

- This orchestrator does NOT call `/close-issue` automatically. Merge is always a user decision.
- Worktree cleanup: `.claude/scripts/cleanup-worktrees.sh` runs on `Stop` hook and removes any worktree whose remote branch is gone and whose PR is merged — it iterates **all** worktrees regardless of naming, so the `feature/<N>-<slug>` pattern is auto-cleaned after merge. No manual cleanup needed.
- If at any iteration the implementing agent reports an open question (not a fixable finding — e.g., "the issue says X but the existing pattern is Y, which to follow?") — escalate to user immediately. Agents cannot decide architectural questions.
- Token discipline: every sub-agent runs in its own context. Your main thread holds only the plan, per-iteration finding summaries, and gate decisions. If context exceeds 50% — pause and summarize before continuing.
- This skill is for one issue at a time. Multiple parallel issues = multiple invocations on multiple worktrees.
