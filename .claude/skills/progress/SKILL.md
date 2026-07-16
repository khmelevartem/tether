---
name: progress
description: Project progress snapshot as an RPG character sheet from Skyrim — hero class, level+XP from PR statistics, MVP chapters with progress bars, legendary artifacts (top PRs), quest map (dependency graph clustered by schools), Seal of Debt (planned vs improvised). Numbers wrapped in RPG language; bare percentages only in purely statistical tables. Use when the user asks for "progress", "project status", "snapshot", "how things are going" — or explicitly calls `/progress`. For dry numbers without RPG framing — separate command `/progress-boring`.
---

Repo-specific values for this project live in `.claude/project.json` — consult it; references below name their config keys.

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

Outputs into `--raw-data`: `prs.json`, `issues.json`, `blocked_by.json`, `loc.json`, `sprint_cutoff.txt`. Do not hand-roll these in chat — `collect.py` owns collection (fields, pagination, GraphQL-vs-REST quirks are documented in the script); edit it if collection needs to change.

## MVP chapters — compose in chat

Read `roadmap.md` §MVP and `features/README.md` (both under `docCorpus.productDir` / `docCorpus.featuresDir`). **One chapter per MVP product feature** (the feature index is the unit, *not* the roadmap bullet): merge roadmap bullets that belong to the same feature — multi-file transfer + live progress + cancel + receiver are all one *File transfer* chapter, backed by its epic(s). A feature may legitimately span more than one epic (File transfer = `#8` UI + `#119` reliability); that's fine, list both. Aim for ≤ 8 chapters. Completed (100 %) chapters are hidden behind a "показать завершённые" toggle by default, so keep them in the list — don't drop them. Chapters cover MVP *product* features only; infra, Post-MVP, and system-integration epics stay out. The coverage caption under the table is **computed** — it lists every open `EPIC:`-titled hub no chapter references, so it never goes stale when new epics appear (you don't hand-maintain that list). For each chapter name the backing epic where the feature has one. Write the result to `/tmp/tether-progress-mvp.json`:

```json
[
  { "title": "Обряд Узнавания", "subtitle": "эпичная строка-одностишие", "epic": [457] },
  { "title": "Шепчущие Маяки", "subtitle": "…", "percent": 85 },
  …
]
```

Text is in Russian (the whole report is). `title` — just the evocative chapter name (the seal column already renders the Roman numeral, so no `Глава N:` prefix). `subtitle` — a one-line flavour caption.

- `epic` (optional) — list of backing issue numbers from the features dir's `README.md` (`docCorpus.featuresDir`), e.g. `[457]` or `[8, 119]`. Each renders as the issue's **full title**, clickable, linking to GitHub. The chapters table has no free-text evidence column — put the backing issues here, not prose.
- `percent` — **auto-computed when omitted**: `build.py` takes the size-weighted completion of the epics' direct sub-issues (`Σ weight(closed) ÷ Σ weight(all)`, weights from `.claude/sizing-bands.json`, `unlabeled`→`valor_size.unlabeled`). Prefer this — it's the answer to "progress by tasks, not by eye". Supply an explicit `percent` only to **override**: chapters with no epic (shipped mDNS / device-name → manual), a subset of a shared epic (live-progress draws on #8 but isn't all of it), or a decision-plus-impl pair (#123/#140) where the weighted child count misreads. When you do override, that's a judgement call — keep it honest.

## Build the snapshot

Find the latest `docs/sprints/sprint-NN.md`. Then run:

```
python3 .claude/skills/progress/build.py \
  --raw-data /tmp/tether-progress-raw \
  --mvp /tmp/tether-progress-mvp.json \
  --sprint docs/sprints/sprint-NN.md \
  --output /tmp/tether-progress.html
```

`build.py` owns every section — formulae, layout, the dependency graph, and the in-report **Book of Knowledge** that explains the formulae to the reader. None of that is restated in this skill (the prose only drifts from the code); to change a section or formula, edit `build.py`, cosmetics → `assets/palette.json`. What stays a chat-time judgment, because the script can't make it:

- **MVP chapters** — compose them (§MVP chapters above).
- **Hero class** — `build.py` picks the class from `assets/classes.json`; you write the one-line *why* (e.g. "every fifth PR is a retro").
- **Tone and the diary** — §Digest in chat below.

## Digest in chat

After running `build.py`, read the figures off the rendered report and output an **adventurer's diary entry**, 6–10 lines in first person. Form:

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

## When the dry version is needed — `/progress-boring`

If numbers without RPG framing are needed (for a status report, retro, document paste) — use `/progress-boring` (`.claude/commands/progress-boring.md`). Same dataset, a regular dashboard.
