# Project-context adapter — render templates at install time, commit the rendered instance

**Status:** Accepted — 2026-07-06
**Issue:** [#465](https://github.com/khmelevartem/tether/issues/465)

## Context

The `.claude/` framework — skills, agents, commands, and the roster/state scripts — hardcodes this repository's specifics. Epic [#464](https://github.com/khmelevartem/tether/issues/464) wants that framework reusable across projects; this decision decouples the repo-specifics into a single adapter so a consuming project supplies only its own configuration.

Two distinct consumers read the framework, and they differ in kind. The scripts are deterministic bash: they can read a config file at runtime and behave identically every time. The prompts are markdown read by an LLM at dispatch time, where determinism is a property that must be engineered, not assumed. The first cut had both consumers read `.claude/project.json` at runtime. Making the LLM resolve config keys mid-run is the weak point: it lowers runtime determinism (the model must consult and resolve a key instead of reading a concrete path), it degrades prompt prose, and — per [`scope-discipline.md` §Two overrides](../scope-discipline.md#two-overrides) — it was introduced by this very change, so it is in-scope to resolve here rather than ship and clean up later.

## Decision drivers

| Driver | Why it matters |
|---|---|
| Runtime determinism for the LLM | An agent should read a concrete path, not resolve a config key mid-run; every runtime indirection is a place the model can diverge. |
| Portability toward a standalone framework repo | The framework should live in its own repository, with a consuming repo supplying only its config; the boundary drawn here is what [#466](https://github.com/khmelevartem/tether/issues/466) extracts. |
| Skills hot-reload | A skill file created or edited after session start is picked up on its next invocation without a restart; new skills become available mid-session. |
| Agents do not hot-reload | The sub-agent registry is frozen at session boot; an agent definition created mid-session is not dispatchable — the Agent tool rejects it. |
| Worktrees do not receive gitignored files | Creating a worktree checks out only tracked files, so gitignored content is absent in a freshly created worktree. |

The three harness properties above were each established by direct in-session experiment against the Claude Code harness, not read from documentation; the harness does not document them.

## Considered options

The decision splits along four sub-questions. Each is recorded with its rejected alternatives, because the chosen shape only makes sense against what it declined.

### Adapter representation

**Chosen — a single `project.json` read directly by both consumers.** One machine-readable file is the sole repo-specific input.

**Rejected — two files: a machine-readable JSON plus a prose "project profile" document.** The prose half reads more cleanly on its own, but it imposes a permanent two-file sync burden and makes the prose a second-class consumer that can silently drift from the JSON.

### How prompts consume config

**Chosen — render.** A script substitutes config values into prompt *templates* at install time, so at runtime the LLM reads only concrete, fully-resolved prompts.

**Rejected — the LLM resolves config keys at runtime.** Non-deterministic, degrades the prose, and is exactly the causal-coupling debt this change would otherwise introduce and defer.

### Where rendered output lives

**Chosen — commit the rendered instance and guard it with a regeneration gate.** The rendered prompts are checked in as a generated artifact; a gate re-renders and fails if the committed output diverges from what the templates and config produce.

**Rejected — gitignore the rendered output and render it via a session-start hook.** Refuted by the worktree and agent-registry drivers together: a freshly created worktree has no rendered files at boot, and the agent registry is frozen at boot and never refreshes, so an agent-heavy run in a fresh worktree would start with an empty agent registry — a silent failure. Skills alone could survive this because they hot-reload; agents cannot.

**Rejected — a hybrid that commits agents but gitignores and renders skills and commands.** Removes the fresh-worktree agent failure, but introduces a split-brain committed/generated boundary and a residual dependency on undocumented session-start-versus-discovery ordering. The complexity is not worth what it buys.

### Scripts

**Chosen — scripts stay config-driven, reading the committed `project.json` at runtime.** They are already deterministic and golden-tested; rendering them would add a representation without removing a runtime read they can safely perform.

## Decision

We adopt a **render-at-install** adapter: repo-specifics live in one committed `project.json`, prompt templates carry placeholders that a renderer substitutes at install time, and the rendered instance is committed as a generated artifact kept honest by a regeneration gate. Scripts stay config-driven at runtime.

The framework *source* is the unit that later extracts into its own repository ([#466](https://github.com/khmelevartem/tether/issues/466)): it holds the prompt templates mirroring the output tree, the generic scripts, the renderer, a config validator, the config schema, and the tests. The rendered instance under the operational `.claude/{agents,skills,commands}` locations is committed alongside it. `project.json` (filled) and its empty template are the only repo-specific inputs, both committed.

Placeholders are dotted config keys; the renderer substitutes them in markdown and copies scripts verbatim, deterministically and idempotently. A validator runs before render and separates fatal from soft failure: invalid JSON, a required key absent, or a placeholder with no config value blocks the render; an optional layer being absent warns and proceeds by graceful degradation.

The gate is enforced by the existing skill-test CI surface. It checks placeholder coverage (every placeholder resolves to a config key), no-leak (no template embeds a hardcoded repo literal — which turns the "no hardcoded repo path in a shipped prompt" acceptance criterion into a machine-enforced check), render idempotence, and rendered-output validity (link check plus the existing golden script tests).

## Costs accepted

- **A committed generated artifact.** The rendered instance is a second representation of the templates; it produces diff noise on every render and is only kept honest by the regeneration gate.
- **Editing a prompt is a two-step edit.** A prompt change means editing its template and re-rendering, never editing the shipped file directly.
- **Config changes lag one session.** A config change takes effect only from the next session, because the agent registry is frozen at boot. This is named as an accepted consequence rather than worked around.

## Consequences

- The [#465](https://github.com/khmelevartem/tether/issues/465) acceptance criterion moves from a human grep to a CI gate.
- The framework source becomes extractable into its own repository; [#466](https://github.com/khmelevartem/tether/issues/466) packaging builds directly on the boundary this draws.

## Revisit if

- **The harness gains agent hot-reload, or the session-start hook is documented to run before sub-agent discovery.** Either makes gitignore-and-render-at-session-start viable and lets the committed-artifact cost be dropped. Re-check the two harness properties before acting.
- **Render or placeholder volume grows enough to warrant a real build step** rather than string substitution.

## References

- Parent living doc: the render/install mechanism is not built yet (this ADR is doc-only), so its operational living doc will be authored when the mechanism is implemented; the framework's current structural overview lives in [`.claude/README.md`](../../../.claude/README.md).
- [`scope-discipline.md` §Two overrides](../scope-discipline.md#two-overrides) — why the runtime-read coupling is resolved in this change rather than deferred.
- [`long-lived-artifacts.md`](../long-lived-artifacts.md) — writing discipline this ADR follows.
- Issue [#465](https://github.com/khmelevartem/tether/issues/465) and its scope-expansion comment; epic [#464](https://github.com/khmelevartem/tether/issues/464); follow-up [#466](https://github.com/khmelevartem/tether/issues/466).
