Pull `origin/main` into the current branch and assess what came in.

## 1. What came in

```bash
git fetch origin main
git log $(git merge-base HEAD origin/main)..origin/main --oneline
```

Empty — main already included, stop.

## 2. Merge

Default — `merge` (preserves branch history, no force-push needed). Use `rebase` only when explicitly asked or when linear history is required before a squash-merge.

1. **Uncommitted WIP present** — stash it first so the merge applies to a clean tree: `git stash push -u`.
2. **Merge** — `git merge origin/main`; resolve any conflicts in committed files by hand before step 3.
3. **Stashed in step 1** — restore: `git stash pop`. If the pop surfaces conflict markers, do not resolve by hand: copy the WIP files outside the worktree, run `git checkout HEAD -- . && git clean -fd && git stash drop`, then re-dispatch the implementing agent against fresh upstream with the saved files as reference.

## 3. Assess semantic overlap

Read the incoming commits (`git show <sha>` for the suspicious ones) and answer explicitly:

- Which of them touch layers / files of the current work?
- Are there changes that make the current work stale, incomplete, or semantically conflicting (not line-conflicting)?
- Does the current work need adjustment to the new context?

A clean merge / rebase without textual conflicts **does not mean** the absence of semantic collision. If overlap found — tell the user in one sentence and propose a plan.

If no overlap — one line «main pulled in, no overlap with current work» and continue.
