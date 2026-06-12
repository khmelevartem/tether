On-demand cost/context health report for a Claude Code session — surfaces when an orchestrator run is ballooning its own context.

## Scope (from the argument)

- **no argument** → the current worktree's newest session. Resolve the project dir: Claude Code stores transcripts under `~/.claude/projects/<dir>`, where `<dir>` is the absolute cwd with every `/` and `.` replaced by `-`. Run `python3 .claude/scripts/session-cost.py --latest "<that dir>"`.
- **`corpus`** → aggregate over the whole project history: `python3 .claude/scripts/session-cost.py --all '~/.claude/projects/*tether*'`.
- **a path to a `.jsonl`** → that session: `--session <path>`.

## Interpret, don't dump

After running, give a 3–5 line read, not a re-print of the table:

- For a single session: the orchestrator cost share and peak context, and whether either crossed warn (peak ≥400K / share ≥75%) or fail (≥600K / ≥85%). If flagged, name the likely cause from the numbers — high `turns` + low `agent-dispatches` means inline work that belongs in sub-agents; a high `cache_create/read` ratio means idle gaps rebuilt the context from cold.
- For corpus: where the current session sits against the median/p90, and whether any model-tier anomaly appeared (a cheap-tier reviewer that ran on Opus).

## Notes

- The orchestrator's own main thread dominates per-issue cost — it is re-read every turn — so its peak context and cost share are the signals worth watching.
- Pure analytics — no Gradle, tests, or smoke; no writes to `docs/`.
- Thresholds in the script are heuristic; treat a single fail as a prompt to look, not a verdict.
- This is the manual counterpart to the `orchestrator-context-guard` PostToolUse hook, which fires the same signal live during a run.
