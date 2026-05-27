# Issue creation examples

Two shapes: a single lean issue and an epic + children.

## Example 1 — single issue

User request: "Open an issue about caching search results on the client so navigating back doesn't trigger an API call."

After recon: `CLAUDE.md` lists `pnpm test`, `pnpm lint`. Closed `#128` did a similar LRU for recently-viewed.

After interview: TTL 5 min (user-confirmed), no parent epic, labels `frontend,performance`.

Draft:

> **Title:** Client-side search results caching
>
> ## Context
>
> Search results page on `myorg/shop` frontend, and navigation between it and product cards.
>
> ## Goal
>
> Returning from a product page shows the previous search results instantly, with a background revalidate. No spinner on back-navigation in the first 100 ms.
>
> ## Entry point
>
> `src/features/search/` is the surface. `src/features/recentlyViewed/cache.ts` has a similar LRU + sessionStorage idea and `src/lib/cache-key.ts` already exposes `buildCacheKey` — see `#128` for the precedent.
>
> ## Definition of Done
>
> - [ ] Back-navigation from a product page to recent search results shows the cached list before any network round-trip; revalidate happens in the background and updates the list smoothly if it differs.
> - [ ] Stale cache (>5 min) falls back to a fresh request with the normal loading state.
> - [ ] A change in filters or query is treated as a new key — no stale list flashes.
> - [ ] `pnpm test` and `pnpm lint` pass.
>
> ## Out of scope
>
> - Server-side edge cache for `/api/search`.
> - Autocomplete suggestion caching.
> - Refactoring `SearchResultsPage.tsx` beyond what the integration needs.
>
> ## References
>
> - `#128` — LRU for recently viewed (closed, similar pattern).

After approval:

```bash
gh issue create --title "Client-side search results caching" \
  --body-file /tmp/issue-body.md \
  --label "size:M,type:feature,frontend,performance"
```

Note what the body does **not** contain: no `SearchCache` interface, no file paths as commitments, no error-handling strategy, no non-functional thresholds the user didn't state. The implementer picks the shape during `/implement`.

## Example 2 — epic + sub-issues

User says: "break it into subtasks." Create N+1 issues: a parent (shorter — Context + Goal + epic-level DoD only) and N children (each lean per the template). After all are created, link via `addSubIssue` (see [RELATIONSHIPS.md](RELATIONSHIPS.md)).

Parent body — Context, Goal, **epic-level DoD only** (the children's combined behaviour). No per-child checklist in the parent — sub-issues API renders it. Label `size:L` on the parent is appropriate.

If a task looks `size:L` upfront, raise epic-splitting during the interview.
