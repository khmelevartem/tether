# /implement — Tether-specific configuration

Tether-specific values the framework files reference by name. Replace this file to port the skill to a different project.

---

## Classification rules

Resolve `{track, type, docLayers}` from the issue.

### Label → type → track

| Trigger | Track | Type |
|---|---|---|
| `docs` label | docs | docs |
| `feature` + explicit docs-only marker (phrase "docs-only" / "only docs" / "scope: docs" in body/DoD, or label `docs-only`) | docs | feature |
| Issue without a type label AND deliverable **limited exclusively** to `.claude/` or `docs/` (no code in source sets) | docs | — (treat as docs) |
| `infra` AND deliverable **limited exclusively** to `.claude/` files (skill prompts, agent definitions, hooks) | docs | infra |
| `feature` / `bugfix` / `refactor` / `infra` with deliverable in source sets or build/CI/scripts (even if an ADR is also needed) | code | as labelled |

Legacy issues may carry the type in a `**Type:**` body field, or under the GitHub default labels `enhancement` (= `feature`) / `bug` (= `bugfix`) / `documentation` (= `docs`).

A code-track FEATURE with an incidental ADR stays on code-track — Step 4 dispatches `architect` mid-flight and the ADR is written in the same PR.

### docs-track: layer classification

After resolving `track=docs`, resolve `docLayers` — the ordered set of artifact layers this issue needs:

| Layer | Needed when | Artifact path | Writer |
|---|---|---|---|
| **spec** | Type FEATURE AND `docs/product/features/<slug>/spec.md` is missing, `(stub)`, or has blocking open questions | `docs/product/features/<slug>/spec.md` | `spec-writer` |
| **ux-brief** | FEATURE with user-facing UI (screen / component / navigation) AND `ux-brief.md` is missing or stale relative to spec changes | `docs/product/features/<slug>/ux-brief.md` | `ux-expert` |
| **tech-doc** | Subsystem with a non-trivial mechanism not covered by `docs/engineering/<name>.md`, or existing one is outdated | `docs/engineering/<name>.md` | `architect` |
| **ADR** | Architectural choice that clears the three-way threshold in `docs/engineering/adr/README.md` §ADR threshold (hard-to-reverse + surprising-without-context + real-trade-off) | `docs/engineering/adr/adr-<name>.md` | `architect` |
| **knowledge** | Solved problem / platform quirk / workaround worth capturing (retro or closed BUGFIX — issue says "record this behaviour") | `docs/knowledge/<name>.md` | `architect` |
| **.claude prompt** | Deliverable — editing a skill prompt, agent definition, slash command, hook, or settings; also INFRA tasks that change agent/command behaviour | `.claude/skills/<…>` / `.claude/agents/<…>` / `.claude/commands/<…>` | orchestrator (inline) |

Multiple layers per issue are normal (e.g. FEATURE with UI and a new mechanism → spec + ux-brief + tech-doc; mechanism choice on existing FEATURE → tech-doc + ADR; new skill + its README example → .claude prompt + tech-doc; closed BUGFIX revealing a platform quirk → knowledge).

---

## Worktree / branch prefix

| Track | Branch prefix | Worktree path |
|---|---|---|
| code | `feature/<N>-<short-slug>` | `.claude/worktrees/feature-<N>-<short-slug>` |
| docs | `docs/<N>-<short-slug>` | `.claude/worktrees/docs-<N>-<short-slug>` |

Branch from `origin/main`, never local `main`.

```bash
# code-track
git worktree add .claude/worktrees/feature-<N>-<short-slug> -b feature/<N>-<short-slug> origin/main

# docs-track
git worktree add .claude/worktrees/docs-<N>-<short-slug> -b docs/<N>-<short-slug> origin/main
```

---

## Doc-discovery recon brief

Pass this brief to the recon agent (an `Explore` sub-agent), substituting the issue title + body:

> Read-only sweep for issue #\<N\>. Return a compact digest — binding constraints and relevant paths, no file dumps:
> - **Product features** — `ls docs/product/features/` (+ `README.md` index). Slug(s) matching this issue's scope; the binding constraints from each `spec.md` / `ux-brief.md` in 1-2 lines.
> - **Product context** — `docs/product/*.md` (vision, audience, roadmap, tech stack, security). The framing that binds this issue's scope / audience / timing.
> - **Engineering living docs** — `docs/engineering/*.md`. The present-tense rules whose topic matches the task.
> - **ADR** — `docs/engineering/adr/adr-*.md`. ADRs matching the topic; for each, its **Revisit if** section and whether this task trips a trigger.
> - **Knowledge** — `docs/knowledge/*.md`. Solved-problem notes relevant to the task.
> - **Glossary** — `docs/glossary.md`. The terms this issue's domain touches, with their locked definitions (load-bearing — `review-glossary` blocks drift).

`CLAUDE.md` is harness-injected — not part of the sweep.

For each ADR the digest flags as trigger-tripped, the plan (code-track) or the layer plan (docs-track) must either confirm the ADR is a false trigger or include a reversal sub-plan — see `docs/engineering/adr/README.md` §Reversing an ADR.

---

## Gate specifics

### G-spec/AC ambiguity (`type==feature`)

Dispatch `spec-writer` first. Stop at user only if `spec-writer` returns clarifying questions or the issue is a non-FEATURE without DoD.

If the FEATURE scope includes user-facing UI AND `docs/product/features/<slug>/ux-brief.md` is missing or stale → dispatch `ux-expert` after `spec-writer`. Open UX questions fold back into this gate.

### G-bugfix-root-cause (`type==bugfix`)

Dispatch `bug-reproducer`. It reproduces locally, verifies each hypothesis, and returns a confirmed cause as structured paste-ready text. Stop at user if: CANNOT REPRODUCE, or no hypothesis matched, or none of the listed hypotheses match after inspection.

The reproducer **must always attempt to observe the symptom**, even when the cause looks structurally evident. Do not silently proceed as if the bug were confirmed.

### G-cause-vs-issue divergence (`type==bugfix`)

When `bug-reproducer`'s confirmed cause materially diverges from what the issue body claims (different mechanism / different platform scope / different observable symptom / different severity class) — stop and present two options: close #N as misdiagnosis and open a new issue with the real cause; or rewrite #N body to match the confirmed cause. Do not silently edit the issue body.

### G-publication of confirmed cause (`type==bugfix`)

Show the paste-ready block to the user. Ask: "Publish as a comment on issue #\<N\>?" Wait for explicit OK before `gh issue comment <N>`. A team-visible side effect requires an explicit gate. If the user says no — keep the cause as a constraint for `coder`; do not publish.

### G-plan-vs-guides (`track==code`)

If the plan conflicts with loaded engineering guides — present to user and stop. Resolution options: fold, split, or re-frame.

### G-forced-cascade scope (`track==code`)

When a change is technically forced but falls outside the issue's literal **Out of scope** — stop and ask the user: **fold** into this PR, **split** (narrow this PR to the kernel, file a follow-up), or **re-frame** the issue. "Forced" decides what, not which PR.

### G-cross-doc inconsistency (`track==docs`)

When the consistency pass (Step 7) finds a contradiction between artifacts that requires a product/technical decision — route resolution to the owning sub-agent (spec issue → `spec-writer`; tech issue → `architect`; ux issue → `ux-expert`), not to the user directly. Escalate to user only if the sub-agent itself cannot converge.

### G-sub-agent open question (always)

If a sub-agent returns an open question it could not converge on — surface it verbatim to the user, collect answers, re-dispatch the same agent. Do not pre-answer architectural / product / UX trade-off questions.

### G-smoke/probe red (`track==code`)

Smoke verdict is not 🟢 after the inner loop — stop and present to user. Enforcement-probe passes green when the enforcer is not wired in despite green unit tests — same gate.

### G-final summary (always)

After everything converges and (code-track) smoke is 🟢: commit, push, create the PR, then present the PR URL with a short summary. The user reviews on GitHub; do not block on explicit OK before push.

---

## Runtime verification recipe

### Smoke (7a) — diff-touches → blocks

| Diff touches | Run |
|---|---|
| `FileServer` / `FileClient` / CLI / network protocol | Desktop CLI + Desktop↔Desktop blocks |
| Android FGS / mDNS / Android networking | Android block (if device attached) |
| native source sets (`iosMain`, `appleMain`) | native compile block |
| DOCS-only, `.claude/`-only, comments-only | nothing |
| Other production code | judgement call — when in doubt, run Desktop blocks |

If the PR introduces a new critical happy-path not covered by smoke (start-time failure point, cross-platform UI, new external interface) — extend `.claude/skills/smoke-test/SKILL.md` in this same PR before running.

### Enforcement-probe (7b) — static check is wired in

1. Create a minimal artifact violating the check in a real location (where the enforcer should fire — `src/.../Test.kt` for a ktlint rule, `.github/workflows/` for a CI guard).
2. Run the corresponding task (`./gradlew ktlintCheck`, `./gradlew <task>`, `git commit` for a pre-commit hook).
3. Verify: build FAILED **with the expected message** (rule id / hook name).
4. Delete the probe; confirm via `git status -s` that nothing remains.

If step 3 passes green — the enforcer is not wired in. This is a red gate; escalate to user.

---

## PR template

`.github/pull_request_template.md`

Write the complete body to a temp file and pass `--body-file` to `gh pr create` — never `--body` with an in-shell-built string, which silently corrupts multiline markdown.

---

## Iteration limits

| Loop | code-track | docs-track |
|---|---|---|
| Inner loop (Step 5) | 4 | 2 |
| Full review (Step 8) | 2 | 2 |

---

## Producer sets

| Track | Primary producers | On-demand producers |
|---|---|---|
| code | `coder`, `ui-expert` (for Compose/screens/components/theming/navigation) | `spec-writer` (feature spec slices), `architect` (non-trivial mechanism / library / structural choice) |
| docs | `spec-writer` (spec layer), `ux-expert` (ux-brief layer), `architect` (tech-doc / ADR / knowledge layers), orchestrator inline (`.claude` prompt layer) | — |

`.claude` prompts are written inline by the orchestrator because an agent editing its own definition would race itself.
