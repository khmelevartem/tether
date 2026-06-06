# Issue sizing rubric

`size:S` / `size:M` / `size:L` measure **human review effort**, not the size of the diff. The label answers "how much of the author's attention did this cost to land" — caps are `S` ≤ 4 h, `M` ≤ 1 day, `L` ≤ 3 days; larger is an epic with sub-issues. Code volume is a weak proxy: a 2 000-line mechanical translation that merges with no comments is `S`; a 200-line change that takes ten rounds of review is `L`.

The label is set twice — estimated at filing ([`create-issue`](skills/create-issue/SKILL.md)) and reconciled against actuals at merge ([`close-issue`](skills/close-issue/SKILL.md)). Both points use this rubric, so the label means the same thing throughout its life and the analytics built on it ([`progress`](skills/progress/SKILL.md) XP, [`grooming`](skills/grooming/SKILL.md) auto-eligibility) rest on an honest signal.

## Ground-truth signal — review burden

At merge the size is decided by **review burden**, measured from the PR:

- **Review comments** — the count of the author's top-level (ROOT) inline review comments. These are the substantive feedback points. Replies inside a thread are excluded — many are `/check-review` auto-acknowledgements, not effort.
- **Review rounds** — the number of distinct review sessions: ROOT comments clustered by time, a gap of more than 60 minutes starting a new cluster. This is the count of "I read it, sent feedback, waited for a fix" cycles.

Both are largely independent of code volume — they capture what the work cost the reviewer, which is the thing being sized.

**Demoted signals.** Lines changed and files touched are a weak prior only. **Commit count is not used** — atomic-commit discipline makes a stack of commits between two review comments a single step, not many. Issue-body length is not a signal at all: large features are routinely described in two lines and balloon during implementation, so longer bodies do not predict larger work (for features the correlation is even slightly inverse).

**Mechanical work stays small.** A large but mechanical diff — bulk translation, generated code, a rename sweep — draws little review and is sized down accordingly, regardless of LOC.

## Forward estimate — at filing

Before any code exists the review-burden signals are unavailable, so the estimate is a prediction framed as **"how many review rounds will this need":** a well-specified, mechanical, or single-surface task needs few and trends `S`; a fuzzy or wide-blast-radius one needs many and trends `L`. Anchor on the type prior, then adjust by structural scope:

- **Type prior** (from closed-issue actuals): `feature` → `M`/`L`, `bugfix` → `S`/`M`, `refactor` → `M`, `infra` / `docs` → `S`/`M` (rarely `L`).
- **Structural multipliers** that push the estimate up: cross-platform `expect`/`actual` fan-out (one common change vs. four platform actuals), a two-sided contract (client ↔ server, sender ↔ receiver), a new module or a crossed architecture boundary, several independent DoD behaviours.

A heavy body on a `size:L` estimate is a signal to split into an epic, not to write more.

## Current calibration

These bands are **volatile** — they describe the current reviewer's behaviour and are re-derived from closed-issue actuals during [`grooming`](skills/grooming/SKILL.md) (see its self-calibration step), backed by [`scripts/review-burden.py`](scripts/review-burden.py). The method above is stable; only the numbers below move.

| Size | ROOT review comments | Review rounds | LOC (median, prior only) |
|------|----------------------|---------------|--------------------------|
| `S`  | 0–1                  | 0             | ~60                      |
| `M`  | 2–8                  | ~1            | ~280                     |
| `L`  | 10+ (mean; wide tail)| 2–3           | ~1100                    |

Review rounds separate the bands more cleanly than the raw comment count (whose `L` distribution is right-skewed): `S` lands in 0 rounds, `M` in ~1, `L` in 2–3.

Review density (comments per 1000 LOC) decays past ~500 LOC — beyond that point per-line scrutiny drops, so a large LOC count does not buy proportionally more review and must not be read as proportionally more effort.
