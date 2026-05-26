Pull `origin/main` into the current branch and assess what came in.

## 1. What came in

```bash
git fetch origin main
git log $(git merge-base HEAD origin/main)..origin/main --oneline
```

Empty — main already included, stop.

## 2. Rebase

Default — `rebase` (linear history on top of fresh main):

```bash
git rebase origin/main
```

If the branch was already pushed, follow up with `git push --force-with-lease` after step 3 passes. Use `merge` instead only when explicitly asked or when the branch has multiple authors / shared work that would suffer from history rewrite.

Resolve conflicts before step 3.

## 3. Assess semantic overlap

Read the incoming commits (`git show <sha>` for the suspicious ones) and answer explicitly:

- Which of them touch layers / files of the current work?
- Are there changes that make the current work stale, incomplete, or semantically conflicting (not line-conflicting)?
- Does the current work need adjustment to the new context?

A clean rebase / merge without textual conflicts **does not mean** the absence of semantic collision. If overlap found — tell the user in one sentence and propose a plan.

If no overlap — one line «main pulled in, no overlap with current work» and continue.
