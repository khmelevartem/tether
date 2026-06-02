Pull `origin/main` into the current branch and assess what came in.

## 1. What came in

```bash
git fetch origin main
git log $(git merge-base HEAD origin/main)..origin/main --oneline
```

Empty — main already included, stop.

## 2. Merge

Default — `merge` (preserves branch history, no force-push needed):

```bash
git merge origin/main
```

If the branch was already pushed, a plain `git push` is enough after step 3 passes. Use `rebase` instead only when explicitly asked or when a linear history is required (e.g. the branch is solo-authored and you want to keep it tidy before squash-merge).

Resolve conflicts before step 3.

## 3. Assess semantic overlap

Read the incoming commits (`git show <sha>` for the suspicious ones) and answer explicitly:

- Which of them touch layers / files of the current work?
- Are there changes that make the current work stale, incomplete, or semantically conflicting (not line-conflicting)?
- Does the current work need adjustment to the new context?

A clean merge / rebase without textual conflicts **does not mean** the absence of semantic collision. If overlap found — tell the user in one sentence and propose a plan.

If no overlap — one line «main pulled in, no overlap with current work» and continue.
