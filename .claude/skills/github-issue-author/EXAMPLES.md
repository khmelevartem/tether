# Issue creation examples

## Example 1: single issue, feature

**User request:** "Create an issue in myorg/shop about the fact that we need to cache search results on the frontend so that navigating back does not trigger an API request."

**After scouting:** the repo has `AGENTS.md` with commands `pnpm test`, `pnpm lint`, `pnpm test:e2e`. Found a similar closed issue #128 — LRU cache for recently viewed.

**After interview:** the user confirmed TTL of 5 minutes, no parent epic, labels `frontend,performance`.

**Draft:**

> **Title:** Client-side search results caching
>
> **Type:** FEATURE
>
> ## Context
>
> The front end of an online store (myorg/shop), the search results page, and navigation between it and product cards.
>
> ## Why
>
> Currently, returning from a product page to the results page triggers a repeat request to `/api/search`, which feels laggy and loads the API. The user sees a loader instead of an instant display of an already-familiar list. The goal is to display the previous results instantly, refreshing them in the background (stale-while-revalidate).
>
> ## How it should work
>
> ### Main scenario
>
> 1. The user performs a search and sees results. The result is saved to the cache under the key `query + filters + page`.
> 2. The user clicks on a product and navigates to its card.
> 3. The user presses "back" — the results are displayed instantly from the cache; a revalidate request runs in the background.
> 4. If the revalidate response differs — the list updates smoothly.
>
> ### Edge cases
>
> - Cache is stale (older than 5 minutes) — show skeleton, wait for API response.
> - Filters changed — cache under a new key; do not show the old one.
> - Revalidate error — keep the cached result, show a toast.
>
> ## Contract
>
> ```ts
> // src/features/search/cache.ts (new file)
> export interface SearchCache {
>   get(key: SearchKey): CachedResult | null
>   set(key: SearchKey, value: SearchResult): void
> }
> export type SearchKey = { query: string; filters: FilterSet; page: number }
> export type CachedResult = { value: SearchResult; storedAt: number }
> ```
>
> Changed external contracts: none.
> New/changed DB tables: none.
>
> ## Technical details
>
> ### Affected modules
>
> - `src/features/search/api.ts` — fetch wrapper, integration point with the cache
> - `src/features/search/cache.ts` (new) — in-memory + sessionStorage LRU
> - `src/features/search/SearchResultsPage.tsx` — consuming the revalidate flow
>
> ### Code landmarks
>
> - `src/features/recentlyViewed/cache.ts` — similar LRU + sessionStorage, can be used as a reference
> - `src/lib/cache-key.ts` — has `buildCacheKey`, use it instead of writing your own
>
> ```bash
> rg "sessionStorage" src/features/    # places where it is already used
> rg "stale-while-revalidate"          # whether the pattern exists in the project
> ```
>
> ### Non-functional requirements
>
> Cache size — no more than 50 recent queries, eviction by LRU. Do not cache responses with `Cache-Control: no-store`.
>
> ### Error handling
>
> On revalidate error — `logger.warn`, do not invalidate cache. On sessionStorage write error (quota) — fall back to in-memory only, `logger.debug`.
>
> ## Out of scope
>
> - Server-side edge cache for `/api/search` — separate issue (see "Consequences").
> - Caching autocomplete `/api/search/suggest` — different surface.
> - Refactoring `SearchResultsPage.tsx` beyond the minimum necessary integration.
>
> ## Definition of Done
>
> - [ ] `pnpm test src/features/search/cache.test.ts` passes, covers: add, evict-on-overflow (>50), ttl-expiry
> - [ ] `pnpm lint` with no new errors
> - [ ] `pnpm test:e2e -- search-cache` — the "search → product card → back" scenario passes without a request to `/api/search` in the first 100ms
> - [ ] Manual check: open DevTools → Network, perform a search, navigate to a product, go back — no repeat request to `/api/search`
> - [ ] Lighthouse Performance ≥ 85 on `/search` (`pnpm lighthouse --url /search`)
>
> ## References
>
> - #128 — LRU for recently viewed (closed, pattern taken from there)
>
> ## Consequences
>
> - Create an issue for the server-side edge cache for `/api/search`
> - Decide whether autocomplete results need caching — separate discussion

After approval:

```bash
gh issue create --repo myorg/shop \
  --title "Client-side search results caching" \
  --body-file /tmp/issue-body.md \
  --label "frontend,performance"
```

## Example 2: epic + sub-issues

If the user says "break it into subtasks", create N+1 issues: one parent (brief, overview) and N children (using the full template), then link them via `addSubIssue`.

The parent issue in this case has a **shortened** body — Context, Why, overall DoD. Contract and technical details live in the children. The label `size:L` on the parent issue is appropriate.

If a task clearly warrants `size:L` — that is a signal to suggest breaking it into an epic during the interview stage.
