# CLAUDE.md

Guidance for Claude Code working in this repo. **Short by design — load deeper docs on demand.**

## What is Tether

KMP file transfer app: Android, iOS, Desktop (JVM — Windows / Linux / macOS). P2P via mDNS discovery + Ktor file server. Human overview — [README.md](README.md).

## Documentation map

Read on demand. Match what you're touching to the corresponding canon:

- **Vision / product framing** — [`docs/product/`](docs/product/README.md). Source of truth for *what* / *why*.
- **Engineering architecture** — [`docs/engineering/`](docs/engineering/README.md). Source of truth for *how*. Per-area:
  - Any code → [`dependency-injection.md`](docs/engineering/dependency-injection.md)
  - Where does this code go? (layer placement / allowed imports) → [`layering.md`](docs/engineering/layering.md)
  - UI → [`presentation-layer.md`](docs/engineering/presentation-layer.md)
  - New module / component → [`modules.md`](docs/engineering/modules.md) + [`architecture-principles.md`](docs/engineering/architecture-principles.md)
  - Tests → [`testing.md`](docs/engineering/testing.md)
- **Security** — [`docs/security/`](docs/security/README.md). Threat model, attack analysis, pairing/channel-encryption security framing.
- **Feature specs / UX briefs** — `docs/product/features/<slug>/{spec,ux-brief}.md`. New / updated: copy structure from [`_template.md`](docs/product/features/_template.md) and [`_ux-brief-template.md`](docs/product/features/_ux-brief-template.md).
- **Solved problems / platform quirks** — [`docs/knowledge/`](docs/knowledge/). Check first when something looks weird before debugging from scratch.
- **Domain terminology** — [`docs/glossary.md`](docs/glossary.md); `review-glossary` blocks drift in PRs.
- **Public articles / publishing** — [`docs/articles/`](docs/articles/README.md). Articles drawn from the codebase, plus the reusable Habr/Medium publishing playbook (per-platform checklists, image prep, gist scripts).

## Architecture invariants

- **Common-first.** Everything that can live in `commonMain` — lives there. Platform source sets (`androidMain`, `appleMain`, `jvmMain`, `desktopMain`, `iosMain`) — only for code that requires platform API. When choosing between `expect/actual` in `commonMain` and copying into `platformMain` — `expect/actual`.
- **Source set hierarchy and Desktop UI/CLI split** — see [`modules.md`](docs/engineering/modules.md).

## Git conventions

All git naming in English. **All commit messages and PR titles must start with the issue number:** `#<issue>: <message>` (e.g., `#42: add mDNS discovery for Android`). Two prefixes stand in for an issue number where no backing issue is warranted: retro commits and PRs use `retro from #<issue>: <message>`; sprint-planning commits and PRs (close the previous sprint, compose the next — see [`docs/sprints/`](docs/sprints/)) use `plan sprint <N>: <message>`.

All issue titles, issue bodies, and PR descriptions must be written in English. Russian is only permitted in interactive chat.

Before committing, make sure the issue exists. If it does not — ask the user to create it. Sprint-planning commits under `plan sprint <N>` are the exception: planning needs no backing issue.

To pull main into the branch — `/pull-main` (merges fresh main and shows what came in). It runs from both `/close-issue` and mid-flight.

Writes to files inside the repo but outside the active worktree are blocked by a PreToolUse hook (`.claude/hooks/block-cross-worktree-writes.sh`); for a deliberate cross-worktree write set `TETHER_SKIP_WORKTREE_HOOK=1`.

## Common commands

Run all Gradle commands with `-q`. Do not run KtLint manually — the git hook does it automatically on commit; do not fix style errors by hand either. Do not clean up unused imports by hand either — KtLint removes them on commit.

Doc links and `#anchors` are validated by `lychee` — offline on every commit and in CI, online (external URLs) on pre-push. A broken link or missing anchor blocks the relevant gate.

Full list of commands by platform — [README.md](README.md). Test commands — [`testing.md`](docs/engineering/testing.md). Parallel run of all targets — `scripts/run-all.sh`.

## Slash commands and skills

Index and selection rules (skill vs command) — [`.claude/README.md`](.claude/README.md).

## Code style

- **Minimal comments.** Before adding one — try extracting the block into a private method: the method name often makes the comment unnecessary. A comment only where code cannot express intent (deliberately swallowed exception, non-obvious external-library invariant).
- **KDoc vs `//`.** KDoc — only for contracts (nullable semantics, non-obvious pre-/postconditions, non-obvious WHY). Do not restate the method name or signature — that is noise. If KDoc adds no information relative to the code — remove it.
- **Kotlin official style** (enforced by KtLint).
- **Long-lived artifacts discipline** (CLAUDE.md, `docs/`, `.claude/`, KDoc, inline comments) — [`long-lived-artifacts.md`](docs/engineering/long-lived-artifacts.md).
