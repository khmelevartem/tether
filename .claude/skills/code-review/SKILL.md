---
name: code-review
description: Multi-agent code review for a PR. Fans out to specialized sub-agents in parallel (DoD, guides, platform, reuse, correctness, tests, UX-conformance), then runs the adversarial agent on their combined findings, aggregates, and posts to GitHub. Use when reviewing a PR or when /close-issue triggers review.
---

# /code-review — Multi-agent orchestrator

You are the orchestrator. You do NOT review code yourself. You collect context, fan out to specialized review agents, aggregate findings, and post one comment to GitHub.

## Input

PR number, or issue number (then resolve PR: `gh pr list --search "closes #<N>" --json number`).

## Step 1 — Collect context (shared with all agents)

```bash
gh pr view <PR> --json title,body,commits,files,reviews
gh issue view <N> --json title,body
gh pr diff <PR>
```

Note the PR number `<PR>` and issue number `<N>` — pass both to every agent.

## Step 2 — Render screenshots if needed

If the PR diff touches `composeApp/src/**` AND PR type is not `DOCS` AND `composeApp/build/outputs/roborazzi/` is empty or missing, run:

```bash
./gradlew :composeApp:recordRoborazziDebug -q
```

This matches the same trigger in `/implement` Step 5. `review-visual` reads the resulting PNGs; without them it has nothing to compare.

## Step 3 — Pre-classify PR type

Read PR body and diff. Classify once: `FEATURE | BUGFIX | REFACTOR | INFRA | DOCS | DEPENDENCY`. Some agents skip based on type (see their frontmatter). Note which agents to skip; do not launch skipped ones.

Skip matrix:
- `DOCS` → skip `review-correctness`, `review-platform`, `review-tests`, `review-ux`, `review-visual`, `review-architecture`
- `INFRA` → skip `review-tests`
- pure `REFACTOR` → skip `review-correctness` (only behavior-preserving)
- trivial one-call-site `BUGFIX` or cosmetic refactor (rename / extract method) with no new types / modules / seams → skip `review-architecture`
- diff doesn't touch any platform source set → skip `review-platform`
- diff doesn't touch `composeApp/src/**` → skip `review-ux` and `review-visual` (when Compose is touched always dispatch both; each agent decides skip vs. block on missing brief / PNGs)

## Step 4 — Wave 1: launch all applicable reviewers in parallel

Send a SINGLE message with multiple Agent tool calls (one per agent). Each prompt is identical structure:

> Review PR #<PR> against issue #<N>. PR type: <type>. Follow your agent definition. Return your `PHASE: <…>` block and `DECISION:` line.

Agents to launch (subject to skip matrix):
- `review-dod`
- `review-guides`
- `review-architecture`
- `review-platform`
- `review-reuse`
- `review-correctness`
- `review-tests`
- `review-ux`
- `review-visual` (reads PNGs from the record step in Step 2 above, or from `/implement` if already rendered)

Each runs in its own context; their token usage does not pollute yours. Collect each `PHASE` block verbatim.

## Step 5 — Wave 2: adversarial agent

After Wave 1 returns, launch `review-adversarial` with the combined findings:

> Review PR #<PR> against issue #<N>. PR type: <type>. Below are the findings from the structured review agents. Form hypotheses about what they missed and verify. Follow your agent definition.
>
> <paste each PHASE block from Wave 1>

## Step 6 — Aggregate

Compose the final review: `## Code Review` header, `PR_TYPE: <type>`, each agent's PHASE block in order (use `N/A — <reason>` for skipped agents), then:

```
DECISION: BLOCK | APPROVE
REQUIRED_BEFORE_MERGE:
  1. <every [REQUIRED] item across all phases, with file:line>
```

`DECISION: APPROVE` only if every agent's decision is APPROVE AND there are zero `[REQUIRED]` items.

## Step 7 — Post to GitHub (idempotent)

Before posting, check whether this skill already left a review on this PR:

```bash
gh pr view <PR> --json reviews --jq '.reviews[] | select(.body | startswith("## Code Review")) | .submittedAt' | wc -l
```

If `0` → this is the first review, post as-is with header `## Code Review`.

If `≥1` → this is a re-review. Increment a round counter and post with header `## Code Review — round <N+1>` where N is the previous count. Do NOT edit the prior review; reviewers and authors rely on history.

```bash
gh pr review <PR> --comment --body "$(cat <<'EOF'
<aggregated review with appropriate header>
EOF
)"
```

The review must always land on the PR — findings left locally are invisible to the team.

## Notes

- This file is coordination only — do not duplicate agent content here.
- If a Wave 1 agent fails twice, include `PHASE: <agent> — TOOL ERROR` in output and continue; do not block the whole review.
- Orchestrator does NOT judge findings — if agents disagree, include both; the author resolves.
