---
name: progress
description: Project progress snapshot as an RPG character sheet from Skyrim — hero class, level+XP from PR statistics, MVP chapters with progress bars, legendary artifacts (top PRs), quest map (dependency graph clustered by schools), Seal of Debt (planned vs improvised). Numbers wrapped in RPG language; bare percentages only in purely statistical tables. Use when the user asks for "progress", "project status", "snapshot", "how things are going" — or explicitly calls `/progress`. For dry numbers without RPG framing — separate command `/progress-boring`.
---

Project progress snapshot as an RPG character sheet. Tether is not a project — it's a saga. The user is the Dragonborn developer. The roadmap is the main story line, infra is side quests and crafting, MVP is the final dungeon.

Tone: seriously epic with light self-irony. Translating numbers into RPG language is mandatory in narrative blocks; pure statistical tables (Hot Battles, Heavy Marches) — bare numbers are acceptable.

## Assets

Fixed dictionaries and palette are nearby in `assets/`. No names, keywords, or colours beyond what is there — this ensures snapshot comparability between runs. Changes to palette, schools, and locations — via editing JSON files, not instructions.

- [`assets/schools.json`](assets/schools.json) — 7 clusters for the dependency graph, names and `keywords` for classification.
- [`assets/locations.json`](assets/locations.json) — 8 source sets with atmospheric names and lore.
- [`assets/keywords.json`](assets/keywords.json) — title heuristics for feature vs infra.
- [`assets/classes.json`](assets/classes.json) — rules for choosing the hero class based on PR dominance.
- [`assets/palette.json`](assets/palette.json) — colours, fonts, artifact frame styles by rarity.

## What to collect (raw data)

1. **PR statistics via GraphQL.** One `gh api graphql` request with pagination over `repository.pullRequests` — fetch at once `number, title, state, createdAt, mergedAt, additions, deletions, changedFiles, commits.totalCount, comments.totalCount, reviews.totalCount, reviewThreads.totalCount`. The REST list endpoint (`/pulls`) does not return commits/comments/review_threads — GraphQL or per-PR detail calls are required.

2. **Issues.** `gh issue list --state all --limit 500 --json number,title,state,labels,createdAt,closedAt` plus `gh api 'repos/<owner>/<repo>/issues?state=all&per_page=100' --paginate` — needed for `parent_issue_url` and `issue_dependencies_summary`.

3. **Issue dependencies.** For those where `issue_dependencies_summary.total_blocked_by > 0` — `gh api 'repos/<owner>/<repo>/issues/<N>/dependencies/blocked_by'`. REST endpoint; the `addIssueDependency` GraphQL mutation does not exist in GitHub (this is in the project memory). Parent — from `parent_issue_url` of the main endpoint.

4. **MVP scope.** `docs/product/roadmap.md` section `## MVP`. `docs/product/features/README.md` — feature statuses. The latest `docs/sprints/sprint-*.md` — the active sprint.

5. **LOC by locations.** `git ls-files 'composeApp/src/<sourceSet>/'` + count over `.kt`/`.swift`. Source sets are in `assets/locations.json`.

6. **Sprint plans.** Parse the `## Composition` section of each `docs/sprints/sprint-*.md` (regex `## Composition .. (?=^## |\Z)`), extract `#N` into that section — the set of planned tasks.

7. **Cutoff for the Seal of Debt.** `git log --diff-filter=A --follow --format='%aI' -- docs/sprints/sprint-01.md | tail -1` — the date the first sprint plan was filed. Issues created before this date are not counted in the Seal of Debt.

## Calculation rules

### Hero class
One class per the rules in `assets/classes.json` (ordinal matching, first match wins). Justify in one line ("every fifth PR is a retro").

### Level and XP
`Level = floor(sqrt(2·merged_PRs + closed_issues))`. XP bar:
- `xp_total = 2·merged_PRs + closed_issues` (same formula as level — otherwise the bar goes negative)
- `xp_in_level = xp_total − level²`
- `xp_needed = (level+1)² − level²`

### PR / issue categorisation
Per `assets/keywords.json`. Used in the Chronicle of Deeds, Seal of Debt, hero class.

### MVP (Main Quest)
7 chapters from the roadmap with epic subtitles ("Chapter II: The Seal of Trust — four runes bind two souls"). Each gets a completion % based on evidence:
- 100% — feature `done` + merged PRs across all platforms.
- 50–80% — partial (one of the layers / some platforms).
- 20–40% — only spec scoped.
- 5% — no code, no decision.

Progress bars below the status, palette gold→gold-pale (completed) and grey (not started).

### Artifacts (top-5 PRs)
Weight: `commits·2 + comments + review_threads·3 + (additions+deletions)/200`. Top-5 with rarity-colour frames per `assets/palette.json#artifact_rarity`. Inside each card three lines: commits, discussions (`comments + review_threads`), `+/−` lines.

### Hot Battles / Heavy Marches
- **Hot** — top-5 PRs by `comments + review_threads`.
- **Heavy** — top-5 PRs by `additions + deletions`.

Tables `#PR | title | value`, monospace numbers right-aligned. No RPG translation in values — pure statistics.

### Seal of Debt — planned vs improvised
**Cutoff:** count only issues created **after** the date `sprint-01.md` was filed in git. Retro-PRs are not counted (they have no separate issue).

- `planned ∩ closed` after cutoff — "By the Sprint Scroll"
- `closed − planned` after cutoff — "Random Encounters"

Three numbers + a two-colour stacked bar (gold ↔ purple) + the last 6 in each category.

### Quest Map — dependency graph
Force-directed graph via D3 v7 with clustering by schools from `assets/schools.json`.

**Layout:**
- School anchors on a radial circle `R = min(W,H)·0.30` equidistant by angle.
- School labels on the outer ring `R_LBL = min(W·0.46, H·0.48)` with dynamic `text-anchor` by angle (cos<−0.3 → end, cos>0.3 → start, otherwise middle).
- Node attraction force toward its cluster anchor: `0.14`.
- Charge `-50`, link distance `38`, collide `13`, alphaDecay `.025`.
- Node coordinates clamped to `[PAD, W-PAD]` × `[PAD, H-PAD]` on each tick.

**Colours** — `assets/palette.json#graph_nodes` and `#graph_edges`. Lone node = no parent, no blocked_by, no blocks (complete isolation). In chains = open AND at least one open `blocked_by` ancestor.

**Interactivity:**
- Node drag (D3 drag behaviour, fx/fy fixed during drag).
- Scroll wheel / drag-on-empty-space → pan+zoom via `d3.zoom().scaleExtent([0.3, 4])`. Filter: do not zoom when the cursor is over a node (otherwise conflicts with node drag).
- `+ / − / ⤺` buttons for zoom via `zoom.scaleBy` / `zoom.transform(zoomIdentity)`.

**Tooltip** — HTML div absolutely positioned inside the graph box, appears on `mouseenter` with opacity transition. Contains: `#N`, full title, cluster, status (`open`/`closed`, `in chains`/`lone`).

**Node labels** in `JetBrains Mono 9px` on a separate top layer with `paint-order:stroke; stroke:<card_bg>; stroke-width:3px` — halo preserves readability when overlapping neighbouring nodes and edges.

**Summary above the graph** — 3 cards: free open nodes, in chains, lone open nodes. Numbers in the corresponding palette colours.

**Below the graph** — two legends: node/edge colours; school descriptions (name + `summary` from `schools.json`).

### Glory of Days — daily hero speed

Sum of "valour" of quests closed on a given day. Quest valour =

```
valor(task) = base(size) + 0.3·comments + LOC/200 + cycleHours/24
```

- `base(size)` — `assets/palette.json#valor_size` (S=1, M=3, L=8, no label=2). Mapped to `size:S` / `size:M` / `size:L` labels on the issue closed by the PR.
- `comments` = `comments.totalCount + reviewThreads.totalCount` of the PR (discussion weight).
- `LOC` = `additions + deletions` of the PR (code churn weight).
- `cycleHours` — hours between the first PR commit (`commits.nodes[0].commit.committedDate`, sorted by date asc) and `mergedAt`. Long cycle = the task hit a wall.

Linking PR ↔ issue by the `#N:` prefix in the PR title (or in the first commit). If the PR is a retro / has no issue — `base = 1`.

`valor(day D) = Σ valor(task)` for PRs with `mergedAt::date == D`, broken into two bars — `valor_feature` and `valor_infra` (category from `assets/keywords.json`). A day with no merges — `0` in both bars.

**Rendering.** Chart.js stacked area chart by day (from the date `sprint-01.md` was filed to today). Two stacked areas: bottom — feature (gold `gold.primary`), top — infra (dark amber `gold.dim`). Top boundary of the stack = total daily valour. Semi-transparent fills (alpha ≈ 0.7), no points on nodes.

Below the chart — three cards: total valour over all time / average daily valour / most glorious day (date + value).

Card tone — RPG: "Glory of the Dragonborn", "Average Stride", "Day of the Great Battle". The Y axis itself — bare (this is statistics).

### Balance of the Week — feature share over the last 7 days

One number on a card: `feature_share(last 7d), %`, in parentheses — change from the previous 7-day period. Format `25% (−6%)`. Sign rendered explicitly: `(+X%)` in green `#5a8a3a`, `(−X%)` in blood `blood`, `(±0%)` in muted `text.muted`. The card sits in the "Character Sheet" next to the "Journey Chronicle" — it is a compact replacement for the former Chronicle of Deeds (weekly stacked bar removed; daily valour already shows proportions).

### Book of Knowledge
At the bottom of the page — a section with formulae and explanations: level+XP, artifact weight, how the class is determined, MVP chapter progress, Hot/Heavy. Two-column layout in Cinzel-gold.

## Output

HTML `/tmp/tether-progress.html`. Palette, fonts, frames — from `assets/palette.json`. Decorative section frames — `border: 2px double <gold.dim>` with ornament pseudo-elements (`❧` in corners).

Include via CDN:
- `https://cdn.jsdelivr.net/npm/chart.js` — area/bar/doughnut.
- `https://cdn.jsdelivr.net/npm/d3@7` — force-directed graph.

Fonts via Google Fonts — taken from `assets/palette.json#fonts`. Defaults: Cinzel (headings), Lora (body, readable serif — IM Fell English SC is too decorative for body sizes), JetBrains Mono (numbers).

Base body size — `palette.json#fonts.base_size_px` (16 px), small card labels — `small_size_px` (13 px). Do not go below 11 px anywhere except chart axes and graph node labels — the snapshot must be readable without zooming.

### Page structure (top to bottom)

1. **Header banner** — "Tether Saga" / "Chronicles of the Dragonborn Developer" / date.
2. **Character Sheet** — Class/Level/XP on the left, Journey Chronicle on the right (artifacts, scrolls, days, discussions, hottest, current chapter).
3. **Main Quest — MVP Chronicle** — table of 7 chapters with progress bars.
4. **Open Locations** — cards + LOC bar chart, sorted descending.
5. **Legendary Artifacts** — top-5 PRs with rarity frames.
6. **Hot Battles / Heavy Marches** — two PR statistics tables.
7. **Current Chapter — Sprint N** — title + list of quests.
8. **Quest Map** — summary + D3 graph + legends (colours and schools).
9. **Seal of Debt** — planned vs improvised with date filter.
10. **Glory of Days** — stacked area chart of daily valour (feature + infra) + three cards (total/average/peak).
11. **Artifact Spread** — doughnut of PR sizes.
12. **Book of Knowledge** — formulae.

### Digest in chat

After the HTML — output an **adventurer's diary entry**, 6–10 lines in first person. Form:

- opening line with the entry date in a stylised calendar system (day + month from a Skyrim-style set: Morning Star / Sunrise / Hearthfire / Starset and so on) + one mood sentence;
- one line about class and level;
- what grew the most by trend;
- which main quest chapter is closest to completion, which is stalled;
- one dark omen (blocker) or challenge ahead;
- final line "ahead — <next MVP item>. May the Eight protect the build."

Forbidden in narrative blocks: bare percentages without RPG framing, "KPI", "velocity", "throughput", 😀 emoji, flag emoji. Allowed: ✦ ✧ ⚔ ☠ ❧ ◈ — use sparingly.

## What not to do

- Don't run Gradle, tests, smoke — pure analytics.
- Don't write a report to `docs/` — the snapshot lives in `/tmp/`.
- Don't categorise >10 PRs manually — write a Python script to `/tmp/build_progress.py`.
- Don't assess a location if LOC = 0 — write "not opened", hide from the chart.
- Don't invent schools/locations/classes/colours beyond those listed in `assets/` — the fixed palette ensures snapshot comparability between runs. Need a new school — add it to `schools.json`, not to the instructions.
- Don't use the REST `/pulls` list endpoint for PR statistics — it doesn't return commits/comments/review_comments. GraphQL or per-PR detail only.

## When the dry version is needed — `/progress-boring`

If numbers without RPG framing are needed (for a status report, retro, document paste) — use `/progress-boring` (`.claude/commands/progress-boring.md`). Same dataset, a regular dashboard.
