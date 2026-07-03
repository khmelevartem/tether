Repo-specific paths, conventions, and commands for this project live in `.claude/project.json` — consult it; references below name their config keys.

All findings are resolved. Make one simplification pass over the diff: remove dead branches, inline single-use helpers, collapse trivial wrappers.

**For every comment / KDoc / prose paragraph in the diff — including `.claude/skills/**`, `docs/`, and Markdown — apply CLAUDE.md §Code style and the [long-lived-artifacts canon](../../../../docs/engineering/long-lived-artifacts.md) (rooted at `docCorpus.engineeringDir`).**

**Do not rephrase prose for brevity.** If a sentence is load-bearing and free of the issues above, leave its wording alone. Cut whole sentences when they fail the rule; otherwise keep them as written.

Do not change behaviour; do not touch anything outside the diff. Run the project's test command (`commands.allTests`) after.
