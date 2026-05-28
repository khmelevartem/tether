---
name: progress
description: Project progress snapshot as an RPG character sheet from Skyrim — hero class, level+XP from PR statistics, MVP chapters with progress bars, legendary artifacts (top PRs), quest map (dependency graph clustered by schools), Seal of Debt (planned vs improvised). Numbers wrapped in RPG language; bare percentages only in purely statistical tables. Use when the user asks for "progress", "project status", "snapshot", "how things are going" — or explicitly calls `/progress`. For dry numbers without RPG framing — separate command `/progress-boring`.
---

Project progress snapshot as an RPG character sheet. Tether is not a project — it's a saga. The user is the Dragonborn developer. The roadmap is the main story line, infra is side quests and crafting, MVP is the final dungeon.

Tone: seriously epic with light self-irony. Translating numbers into RPG language is mandatory in narrative blocks; pure statistical tables (Hot Battles, Heavy Marches) — bare numbers are acceptable.

## Assets

Fixed dictionaries and palette are in `assets/`. No names, keywords, or colours beyond what is there — this ensures snapshot comparability between runs. Changes to palette, schools, and locations — via editing JSON files, not instructions.

- [`assets/schools.json`](assets/schools.json) — 7 clusters for the dependency graph, names and `keywords` for classification.
- [`assets/locations.json`](assets/locations.json) — 8 source sets with atmospheric names and lore.
- [`assets/keywords.json`](assets/keywords.json) — title heuristics for feature vs infra.
- [`assets/classes.json`](assets/classes.json) — rules for choosing the hero class based on PR dominance.
- [`assets/palette.json`](assets/palette.json) — colours, fonts, artifact frame styles by rarity.

## What to collect (raw data)

Write all outputs to `/tmp/tether-progress-raw/` (create the dir first).

1. **PR statistics via GraphQL.** One paginated `gh api graphql` request over `repository.pullRequests`. Required fields per node: `number, title, state, createdAt, mergedAt, additions, deletions, changedFiles, commits { totalCount nodes { commit { committedDate } } }, comments { totalCount }, reviews { totalCount }, reviewThreads { totalCount }`. Nodes must be sorted ascending by date; only the first node is used for cycleHours. Write to `prs.json`.

   The REST list endpoint (`/pulls`) does not return commits/comments/review_threads — GraphQL is required.

2. **Issues.** `gh api 'repos/<owner>/<repo>/issues?state=all&per_page=100' --paginate` — needed for `parent_issue_url` and `issue_dependencies_summary`. Filter out objects that contain a `pull_request` key. Write to `issues.json`.

3. **Issue dependencies.** For each issue where `issue_dependencies_summary.total_blocked_by > 0`, call `gh api 'repos/<owner>/<repo>/issues/<N>/dependencies/blocked_by'`. Collect results as `{ "<N>": [blocker_numbers] }`. Write to `blocked_by.json` (empty `{}` when none). The `addIssueDependency` GraphQL mutation does not exist in GitHub — use REST only.

4. **LOC by source set.** For each source set in `assets/locations.json`, count lines in `.kt`/`.swift` files under `composeApp/src/<sourceSet>/`. Write to `loc.json` as `{ "<sourceSet>": <int> }`.

5. **Sprint cutoff.** `git log --diff-filter=A --follow --format='%aI' -- docs/sprints/sprint-01.md | tail -1`. Write the ISO date to `sprint_cutoff.txt`.

## MVP chapters — compose in chat

Read `docs/product/roadmap.md` §MVP and `docs/product/features/README.md`. For each of the 7 chapters, judge the completion percent from evidence (merged PRs, feature status files, spec presence). Write the result to `/tmp/tether-progress-mvp.json`:

```json
[
  { "title": "Chapter I: …", "subtitle": "epic one-liner", "percent": 60, "note": "optional one-liner" },
  …
]
```

`percent` scale:
- 100% — feature `done` + merged PRs across all platforms.
- 50–80% — partial (one of the layers / some platforms).
- 20–40% — only spec scoped.
- 5% — no code, no decision.

## Build the snapshot

Find the latest `docs/sprints/sprint-NN.md`. Then run:

```
python3 .claude/skills/progress/build.py \
  --raw-data /tmp/tether-progress-raw \
  --mvp /tmp/tether-progress-mvp.json \
  --sprint docs/sprints/sprint-NN.md \
  --output /tmp/tether-progress.html
```

`build.py` renders the HTML — sections, layout, and all formulae are fixed there. Cosmetic colour/font tuning → `assets/palette.json`. New mechanical section → edit `build.py`. Tone / MVP judgement / diary → here, in chat.

## Calculation rules

### Hero class
One class per the rules in `assets/classes.json` (ordinal matching, first match wins). Justify in one line ("every fifth PR is a retro").

### Level and XP
`Level = floor(sqrt(2·merged_PRs + closed_issues))`. XP bar:
- `xp_total = 2·merged_PRs + closed_issues`
- `xp_in_level = xp_total − level²`
- `xp_needed = (level+1)² − level²`

### PR / issue categorisation
Per `assets/keywords.json`. Used in the Chronicle of Deeds, Seal of Debt, hero class.

### MVP (Main Quest)
7 chapters from the roadmap with epic subtitles. Each gets a completion % (see §MVP chapters above). Progress bars: `gold.primary` → `gold.dim` for >0%, `text.muted_dim` for 0%.

### Artifacts (top-5 PRs)
Weight: `commits·2 + comments + review_threads·3 + (additions+deletions)/200`. Top-5 with rarity-colour frames per `assets/palette.json#artifact_rarity`. Inside each card: commits, discussions (`comments + review_threads`), `+/−` lines.

### Hot Battles / Heavy Marches
- **Hot** — top-5 PRs by `comments + review_threads`.
- **Heavy** — top-5 PRs by `additions + deletions`.

Tables `#PR | title | value`, monospace numbers right-aligned. No RPG translation in values — pure statistics.

### Seal of Debt — planned vs improvised
**Cutoff:** count only issues created **after** the date `sprint-01.md` was filed in git. Retro-PRs are not counted.

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
- Scroll wheel / drag-on-empty-space → pan+zoom via `d3.zoom().scaleExtent([0.3, 4])`. Filter: do not zoom when the cursor is over a node.
- `+ / − / ⤺` buttons for zoom.

**Tooltip** — appears on `mouseenter` with opacity transition. Contains: `#N`, full title, cluster, status (`open`/`closed`, `in chains`/`lone`).

**Node labels** in `JetBrains Mono 9px` with stroke halo for readability.

**Summary above the graph** — 3 cards: free open nodes, in chains, lone open nodes.

**Below the graph** — two legends: node/edge colours; school descriptions (name + `summary` from `schools.json`).

### Glory of Days — daily hero speed

Sum of "valour" of quests closed on a given day:

```
valor(task) = base(size) + 0.3·comments + LOC/200 + cycleHours/24
```

- `base(size)` — `assets/palette.json#valor_size` (S=1, M=3, L=8, no label=2). Mapped to `size:S` / `size:M` / `size:L` labels on the issue closed by the PR.
- `comments` = `comments.totalCount + reviewThreads.totalCount` of the PR.
- `LOC` = `additions + deletions` of the PR.
- `cycleHours` — hours between the first PR commit and `mergedAt`.

Linking PR ↔ issue by the `#N:` prefix in the PR title. If the PR is a retro / has no issue — `base = 1`.

`valor(day D) = Σ valor(task)` for PRs with `mergedAt::date == D`, split into `valor_feature` and `valor_infra`.

Rendered as a Chart.js stacked area chart by day (from sprint cutoff to `--today`). Two stacked areas: feature (`gold.primary`) and infra (`gold.dim`). Below: three cards — total valour / average daily valour / most glorious day.

### Artifact Spread
Doughnut of merged PRs by size: S / M / L / unlabeled. Size derived from the `size:S|M|L` label on the linked issue (via `#N:` title prefix); PRs without a matching issue or label → "unlabeled".

### Balance of the Week — feature share over the last 7 days

One number on a card: `feature_share(last 7d), %`, in parentheses — change from the previous 7-day period. Format `25% (−6%)`. Sign rendered explicitly: `(+X%)` in green `#5a8a3a`, `(−X%)` in blood `blood`, `(±0%)` in muted `text.muted`. The card sits in the Character Sheet next to the Journey Chronicle.

### Book of Knowledge
At the bottom of the page — formulae and explanations: level+XP, artifact weight, class determination, MVP chapter progress, Hot/Heavy. Two-column layout.

## Digest in chat

After running `build.py` — output an **adventurer's diary entry**, 6–10 lines in first person. Form:

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
- Don't edit the HTML by hand — cosmetic tuning → `assets/palette.json`; new section → `build.py`.
- Don't assess a location if LOC = 0 — write "not opened", hide from the chart.
- Don't invent schools/locations/classes/colours beyond those listed in `assets/` — the fixed palette ensures snapshot comparability between runs. Need a new school — add it to `schools.json`, not here.
- Don't use the REST `/pulls` list endpoint for PR statistics — it doesn't return commits/comments/review_threads. GraphQL only.

## When the dry version is needed — `/progress-boring`

If numbers without RPG framing are needed (for a status report, retro, document paste) — use `/progress-boring` (`.claude/commands/progress-boring.md`). Same dataset, a regular dashboard.
