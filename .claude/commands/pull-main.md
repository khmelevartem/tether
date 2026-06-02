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

**Mid-flight WIP (uncommitted work present).** When `/pull-main` runs while the inner loop has uncommitted changes — typical of a `/implement` re-entry where the previous run left a half-built coder pass — `git stash push -u` + `merge` + `git stash pop` is the natural sequence, and `stash pop` will frequently produce conflict markers in working-tree files when upstream changed the same regions. Conflict markers in the working tree are not productive: the next coder dispatch reads them as source, gets confused, and the orchestrator spends context resolving them by hand. Preferred recovery: save the WIP files outside the worktree (e.g. `cp <file> /tmp/<N>-wip/`), `git checkout HEAD -- .` + `git clean -fd` to reset clean, `git stash drop` to discard the stash, then re-dispatch the coder against the fresh upstream API — passing the saved /tmp files as reference rather than as a base to patch over. Cheap context-wise compared to manual conflict resolution, and produces a clean diff at the end.

## 3. Assess semantic overlap

Read the incoming commits (`git show <sha>` for the suspicious ones) and answer explicitly:

- Which of them touch layers / files of the current work?
- Are there changes that make the current work stale, incomplete, or semantically conflicting (not line-conflicting)?
- Does the current work need adjustment to the new context?

A clean merge / rebase without textual conflicts **does not mean** the absence of semantic collision. If overlap found — tell the user in one sentence and propose a plan.

If no overlap — one line «main pulled in, no overlap with current work» and continue.
