Project progress snapshot: infra/features by PR + MVP readiness by roadmap.

Repo-specific paths for this project live in `.claude/project.json` — consult it; references below name their config keys.

## What to collect

1. **PR statistics via GraphQL.** One `gh api graphql` request with pagination over `repository.pullRequests` — fetching `number, title, state, createdAt, mergedAt, additions, deletions, changedFiles, commits.totalCount, comments.totalCount, reviews.totalCount, reviewThreads.totalCount` at once. The REST list endpoint `/pulls` does not return `commits`/`comments`/`review_comments` per PR — GraphQL or per-PR detail calls are needed.

2. **MVP scope.** Read `roadmap.md` (section `## MVP`, under `docCorpus.productDir`) — list of MVP items. Read the features dir's `README.md` (`docCorpus.featuresDir`) — feature statuses and linked issues. Read the latest `docs/sprints/sprint-*.md` (with the highest number) — what is in progress now.

## PR categorization

**Feature** — PR touches product code or fills in / edits a product spec:
- `composeApp/src/**` (except `build.gradle.kts`-only), implementation of discovery / FileServer / pairing / UI;
- the features dir (`docCorpus.featuresDir`) — filling in feature specs;
- the product docs root (`docCorpus.productDir`): `vision.md`, `roadmap.md`, `security.md`, `monetization.md` — product content.

**Infra** — everything else:
- `.claude/**` (skills, agents, hooks, commands);
- the engineering docs root (`docCorpus.engineeringDir`), ADRs;
- retro PRs (title starts with `retro`);
- sprint planning, `docs/sprints/**`;
- Gradle build, CI, configs;
- pure refactors with no user-visible changes.

If a PR is mixed — categorize by the dominant part (>50% of the diff). If in doubt — ask the user once for the whole batch.

## MVP readiness

For each item from `roadmap.md ## MVP` (under `docCorpus.productDir`) assess readiness (0–100%) based on evidence:

- 100% — feature has status `done` in the features dir's `README.md` (`docCorpus.featuresDir`) AND there are merged PRs covering all platforms.
- 50–80% — partial: either protocol layer without UI, or platform parity incomplete (e.g., Android+iOS done, Desktop tbd).
- 10–30% — only spec `scoped`, no code; or only one of several components.
- 0% — neither code nor spec with status ≥`scoped`.

Don't guess — justify each assessment with PR numbers and/or lines in the features dir's `README.md` (`docCorpus.featuresDir`). If the evidence is unclear — mark `?` and ask.

## Velocity per day

Daily velocity = sum of the cost of tasks closed that day. Cost of one task:

```
cost(task) = base(size) + 0.3·comments + LOC/200 + cycleHours/24
```

- `base(size)` — by the issue label of the closed PR: `size:S=1`, `size:M=3`, `size:L=8`, no label=2. Retro / PR without issue: `base=1`.
- `comments` = `comments.totalCount + reviewThreads.totalCount` of the PR.
- `LOC` = `additions + deletions` of the PR.
- `cycleHours` = hours between the first commit of the PR (`commits.nodes[0].commit.committedDate`) and `mergedAt`.

All components are calibrated so that for a typical `size:M` PR (~5 comments, ~400 LOC, ~6h cycle) they give a comparable contribution: `3 + 1.5 + 2 + 0.25 ≈ 7`. Base size sets the floor, activity adds on top.

`velocity(D) = Σ cost(task)` for PRs merged on day `D` (by `mergedAt::date`, UTC). A day without a merge — `0`.

PR ↔ issue linkage by the `#N:` prefix in the PR title or first commit (CLAUDE.md commit convention). Size label — from the issue.

**Window** — one day. A week for a project ~one month old is uninformative.

## Output

One HTML file `/tmp/tether-progress.html` (visible in the Launch preview panel), containing:

1. **KPI tile at the top**: total merged PRs · % feature / % infra · % MVP (weighted average, equal weights per item) · active days · current sprint + its number · average daily velocity.
2. **Stacked bar by week** (feature vs infra) — dynamics of the ratio.
3. **Velocity per day** — line chart of daily cost. Points colored by the day's dominant category (feature gold, infra dark amber); gray line — the value itself. Below it three numbers: total / average / peak day. The goal of the chart is a visual check of the hypothesis "infra-days drive feature spikes".
4. **Bubble scatter** of PRs: x=commits, y=comments, size=LOC, color=category. Label "top right — heavy PRs".
5. **MVP table** (7 rows): roadmap item | feature status | % readiness | evidence (PR numbers, link to spec).
6. **Top 5 heaviest PRs** (by `commits + comments`).
7. **"What's next" block**: what is in progress in the current sprint (issues), what is blocking MVP.

**Style — neutral, no RPG wrapper.** `progress-boring` exists precisely as a sober dashboard, so visually it must not inherit the palette/fonts from `/progress` (Cinzel, gold, parchment, ornaments `❧`). Do not load `.claude/skills/progress/assets/palette.json` and do not use its tokens even if they are already in context — that would be inertia, not a rule.

Specifically:
- **Fonts** — system sans-serif stack (`-apple-system, Segoe UI, Roboto, Inter, sans-serif`), for numbers — monospace (`ui-monospace, SF Mono, Menlo, monospace`). No decorative serif or display fonts.
- **Palette** — neutral dark: background `#0f1115` / `#161922`, text `#e6e8eb` / muted `#8b9098`, accent `#6ea8fe` (cold blue) for feature, `#9aa0a6` (gray) for infra. No crimson/gold.
- **Decoration** — no ornamental borders, no pseudo-elements, no emojis in headings. Simple `1px solid` card borders, flat corners, minimal.
- **Headings** — normal weight (600), no `text-transform: uppercase` and no `letter-spacing`, except for small KPI labels.

Chart.js via CDN. Charts also in neutral tones (same blue+gray, not gold/amber).

After building the HTML — a short text digest in chat (5–7 lines), without repeating figures from the table:
- one phrase on the ratio and weekly trend;
- overall MVP percentage + which 2-3 items are pulling it down;
- one observation on velocity: is there a visible correlation "infra-day → feature spike 1–2 days later", or are days mixed without a pattern;
- 1-2 observations not visible from the numbers (e.g., "the entire UI layer is still ahead", "sprint 4 removes architectural blockers").

## What not to do

- Do not run Gradle, tests, smoke — this is pure analytics.
- Do not write a markdown report in `docs/` — this is an ephemeral snapshot, it lives in `/tmp/`.
- Do not categorize more than 10 PRs manually without optimization — if there are many, write a short Python script.
