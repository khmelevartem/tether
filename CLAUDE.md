# CLAUDE.md

Guidance for Claude Code working in this repo. **Short by design — load deeper docs on demand.**

## What is Tether

KMP file transfer app: Android, iOS, Desktop (JVM — Windows / Linux / macOS). P2P via mDNS discovery + Ktor file server. Human overview — [README.md](README.md).

## Documentation map

Read on demand. Match what you're touching to the corresponding canon:

- **Vision / product framing** — [`docs/product/`](docs/product/README.md). Source of truth for *what* / *why*.
- **Engineering architecture** — [`docs/engineering/`](docs/engineering/README.md). Source of truth for *how*. Per-area:
  - Any code → [`dependency-injection.md`](docs/engineering/dependency-injection.md)
  - UI → [`presentation-layer.md`](docs/engineering/presentation-layer.md)
  - New module / component → [`modules.md`](docs/engineering/modules.md) + [`architecture-principles.md`](docs/engineering/architecture-principles.md)
  - Tests → [`testing.md`](docs/engineering/testing.md)
- **Feature specs / UX briefs** — `docs/product/features/<slug>/{spec,ux-brief}.md`. New / updated: copy structure from [`_template.md`](docs/product/features/_template.md) and [`_ux-brief-template.md`](docs/product/features/_ux-brief-template.md).
- **Solved problems / platform quirks** — [`docs/knowledge/`](docs/knowledge/). Check first when something looks weird before debugging from scratch.
- **Domain terminology** — [`docs/glossary.md`](docs/glossary.md); `review-glossary` blocks drift in PRs.

## Architecture invariants

- **Common-first.** Everything that can live in `commonMain` — lives there. Platform source sets (`androidMain`, `appleMain`, `jvmMain`, `desktopMain`, `iosMain`) — only for code that requires platform API. When choosing between `expect/actual` in `commonMain` and copying into `platformMain` — `expect/actual`.
- **Source set hierarchy and Desktop UI/CLI split** — see [`modules.md`](docs/engineering/modules.md).

## Git conventions

All git naming in English. **All commit messages and PR titles must start with the issue number:** `#<issue>: <message>` (e.g., `#42: add mDNS discovery for Android`). Retro commits and PRs use `retro from #<issue>: <message>`.

All issue titles, issue bodies, and PR descriptions must be written in English. Russian is only permitted in interactive chat.

Before committing, make sure the issue exists. If it does not — ask the user to create it.

To pull main into the branch — `/rebase` (rebases onto fresh main and shows what came in). It runs from both `/close-issue` and mid-flight.

## Common commands

Run all Gradle commands with `-q`. Do not run KtLint manually — the git hook does it automatically on commit; do not fix style errors by hand either. Do not clean up unused imports by hand either — KtLint removes them on commit.

Full list of commands by platform — [README.md](README.md). Test commands — [`testing.md`](docs/engineering/testing.md). Parallel run of all targets — `scripts/run-all.sh`.

## Slash commands and skills

Index and selection rules (skill vs command) — [`.claude/README.md`](.claude/README.md).

## Code style

- **Minimal comments.** Before adding one — try extracting the block into a private method: the method name often makes the comment unnecessary. A comment only where code cannot express intent (deliberately swallowed exception, non-obvious external-library invariant).
- **KDoc vs `//`.** KDoc — only for contracts (nullable semantics, non-obvious pre-/postconditions, non-obvious WHY). Do not restate the method name or signature — that is noise. If KDoc adds no information relative to the code — remove it.
- **Kotlin official style** (enforced by KtLint).
- **One top-level class per file.** Data classes, sealed types, enums included. Nested types stay nested only when they are private implementation details of the enclosing class — never consumed across the file boundary. If a type is reachable to callers, it gets its own file.
- **Long-lived artifacts discipline** (CLAUDE.md, `docs/`, `.claude/`, KDoc, inline comments) — [`long-lived-artifacts.md`](docs/engineering/long-lived-artifacts.md).
