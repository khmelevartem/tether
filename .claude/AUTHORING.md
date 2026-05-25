# Authoring `.claude/` artifacts — skill vs command

Two homes for prompt-based artifacts:

- **Skills** — `.claude/skills/<name>/SKILL.md` (+ optional `assets/*.json`). Discoverable by name + description in YAML frontmatter; loaded on demand.
- **Commands** — `.claude/commands/<name>.md`. Plain prompt template invoked by literal slash-command name.

## Choose a skill if any of these hold

- The instruction includes fixed dictionaries / colour palettes / configuration that should be editable separately from prose. Put them in `assets/*.json`.
- The artifact is larger than ~150 lines.
- The instruction encodes «magic» names / fixed identifiers so results stay comparable between runs.
- Skill discovery by description is valuable (the user types a topic, not a literal name).

## Choose a command otherwise

A simple prompt template — no assets, no discovery, short — lives in `.claude/commands/<name>.md`.

## Tone and structure

Match siblings in the same folder. CLAUDE.md §Code style applies to prompt prose; rules from [`docs/engineering/long-lived-artifacts.md`](../docs/engineering/long-lived-artifacts.md) apply too — no incident framing, no historical narrative, link over inline-copy.
