All findings are resolved. Make one simplification pass over the diff: remove dead branches, inline single-use helpers, collapse trivial wrappers.

**For every comment / KDoc / prose paragraph in the diff — including `.claude/skills/**`, `docs/`, and Markdown — apply CLAUDE.md §Code style and [`docs/engineering/long-lived-artifacts.md`](../../../../docs/engineering/long-lived-artifacts.md).**

**Do not rephrase prose for brevity.** If a sentence is load-bearing and free of the issues above, leave its wording alone. Cut whole sentences when they fail the rule; otherwise keep them as written.

Do not change behaviour; do not touch anything outside the diff. Run `./gradlew allTests -q` after.
