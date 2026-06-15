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

Run `collect.py` — it resolves `owner/repo` from `gh repo view` and writes every input `build.py` consumes:

```
python3 .claude/skills/progress/collect.py --raw-data /tmp/tether-progress-raw --repo-root .
```

Outputs into `--raw-data`: `prs.json`, `issues.json`, `blocked_by.json`, `loc.json`, `sprint_cutoff.txt`. Do not hand-roll these in chat — edit `collect.py` if collection needs to change.

What it gathers, and the quirks baked into the script:

1. **PR statistics via GraphQL** → `prs.json`. Paginated over `repository.pullRequests`, sorted ascending by date (only the first commit node feeds cycleHours). Fields: `number, title, state, createdAt, mergedAt, additions, deletions, changedFiles, commits { totalCount nodes { commit { committedDate } } }, comments { totalCount }, reviews { totalCount }, reviewThreads { totalCount }`. The REST `/pulls` list endpoint omits commits/comments/review_threads — GraphQL is required.
2. **Issues** → `issues.json`. `--paginate --slurp` over `repos/<owner>/<repo>/issues?state=all`, objects with a `pull_request` key filtered out. Carries `parent_issue_url` and `issue_dependencies_summary`.
3. **Issue dependencies** → `blocked_by.json`. For each issue with `total_blocked_by > 0`, REST `…/dependencies/blocked_by`, collected as `{ "<N>": [blockers] }` (`{}` when none). The `addIssueDependency` GraphQL mutation does not exist — REST only.
4. **LOC by source set** → `loc.json`. Lines and files in `.kt`/`.swift` under `composeApp/src/<source_set>/` for each entry in `assets/locations.json`; emits `<sourceSet>` and `<sourceSet>_files` (0 when the dir is absent).
5. **Sprint cutoff** → `sprint_cutoff.txt`. Last `git log --diff-filter=A --follow` date for `docs/sprints/sprint-01.md`.

## MVP chapters — compose in chat

Read `docs/product/roadmap.md` §MVP and `docs/product/features/README.md`. One chapter per roadmap §MVP item (currently 8 — keep the chapter set aligned with the roadmap, not a fixed count). For each, judge the completion percent from evidence and name the backing epic where the feature has one. Write the result to `/tmp/tether-progress-mvp.json`:

```json
[
  { "title": "Шепчущие Маяки", "subtitle": "эпичная строка-одностишие", "percent": 60, "epic": [457] },
  …
]
```

Text is in Russian (the whole report is). `title` — just the evocative chapter name (the seal column already renders the Roman numeral, so no `Глава N:` prefix). `subtitle` — a one-line flavour caption.

- `epic` (optional) — list of backing issue numbers from `docs/product/features/README.md`, e.g. `[457]` or `[8, 119]`. Each renders as the issue's **full title**, clickable, linking to GitHub (resolved from the collected issues by number). Omit for roadmap items with no backing issue (e.g. shipped mDNS / device-name). The chapters table has no free-text evidence column — put the backing issues here, not prose.
- `percent` — prefer the backing epic's sub-issue completion (closed children ÷ total) as the anchor, then adjust for platform/layer coverage. Scale:
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
One chapter per roadmap §MVP item (see §MVP chapters above), each with an epic subtitle and an optional backing-epic ref. Each gets a completion %. Progress bars: `gold.primary` fill for >0%, `text.muted_dim` for 0%.

### Artifacts (top-5 PRs)
Weight: `commits·2 + comments + review_threads·3 + (additions+deletions)/200`. Top-5 with rarity-colour frames per `assets/palette.json#artifact_rarity`. Inside each card: commits, discussions (`comments + review_threads`), `+/−` lines.

### Hot Battles / Heavy Marches
- **Hot** — top-5 PRs by `comments + review_threads`.
- **Heavy** — top-5 PRs by `additions + deletions`.

Tables `#PR | title | value`, monospace numbers right-aligned. No RPG translation in values — pure statistics.

### Seal of Debt — planned vs improvised
**Planned** = issues referenced in any `## Состав` section across `docs/sprints/sprint-*.md`. **Cutoff:** count only issues created **after** the date `sprint-01.md` was filed in git. Retro-PRs are not counted.

- `planned ∩ closed` after cutoff — "By the Sprint Scroll"
- `closed − planned` after cutoff — "Random Encounters"

Three numbers + a two-colour stacked bar (gold ↔ purple).

### Quest Map — dependency graph
Force-directed graph via D3 v7 with clustering by schools from `assets/schools.json`.

**Layout:**
- School anchors on a radial circle `R = min(W,H)·0.30` equidistant by angle.
- School labels on the outer ring `R_LBL = min(W·0.46, H·0.48)` with dynamic `text-anchor` by angle (cos<−0.3 → end, cos>0.3 → start, otherwise middle).
- Node attraction force toward its cluster anchor: `0.14`.
- Charge `-50`, link distance `38`, collide `13`, alphaDecay `.025`.
- Node coordinates clamped to `[PAD, W-PAD]` × `[PAD, H-PAD]` on each tick.

**Colours** — `assets/palette.json#graph_nodes` and `#graph_edges`. Lone node = no parent, no blocked_by, no blocks, and not an epic (complete isolation). In chains = open AND at least one open `blocked_by` ancestor. Epic node = parents at least one sub-issue **and** has `EPIC:` in its title (both required — a hub without the `EPIC:` prefix is a plain node, just never "lone"); rendered larger (r 10 open / 7 closed) in `graph_nodes.epic` purple with a bright ring and a bigger bold label. Epic styling takes priority over free/blocked/orphan; a closed epic keeps the closed fill but a purple ring.

**Interactivity:**
- Node drag (D3 drag behaviour, fx/fy fixed during drag).
- Scroll wheel / drag-on-empty-space → pan+zoom via `d3.zoom().scaleExtent([0.3, 4])`. Filter: do not zoom when the cursor is over a node.
- `+ / − / ⤺` buttons for zoom.

**Tooltip** — appears on `mouseenter` with opacity transition. Contains: `#N`, full title, cluster, status (`эпик` prefix when the node is an epic, then `open`/`closed`, `in chains`/`lone`).

**Node labels** in `JetBrains Mono 9px` with stroke halo for readability.

**Summary above the graph** — 3 cards: free open nodes, in chains, lone open nodes.

**Below the graph** — two legends: node/edge colours (epic swatch first); school descriptions (name + `summary` from `schools.json`).

### Glory of Days — daily hero speed

Sum of "valour" of quests closed on a given day:

```
valor(task) = base(size) + 0.3·comments + LOC/200 + cycleHours/24
```

- `base(size)` — `S`/`M`/`L` from [`.claude/sizing-bands.json`](../../sizing-bands.json) (mean review burden per size; regenerate with `python3 .claude/scripts/review-burden.py --write`); `unlabeled`/`retro` from `assets/palette.json#valor_size`. Mapped via the `size:` label on the issue closed by the PR.
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
