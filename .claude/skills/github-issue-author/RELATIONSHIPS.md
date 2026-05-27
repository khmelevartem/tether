# Issue relationships

Relationships live in **native GitHub fields**, not body text. The body drifts when edited; native fields don't, and the reviewer bot reads them as structured data.

Three relationship types, three mechanisms:

| Relationship | Mechanism | API |
|---|---|---|
| Parent / sub-issue | GraphQL `addSubIssue` | GraphQL |
| Blocked by / blocks (Relationships in UI) | Issue dependencies | REST |
| Related | `#N` mention in Context or Goal | n/a |

## Sub-issue (parent → child)

```bash
PARENT_ID=$(gh api graphql -f query='
  query($owner: String!, $repo: String!, $number: Int!) {
    repository(owner: $owner, name: $repo) {
      issue(number: $number) { id }
    }
  }' -F owner=OWNER -F repo=REPO -F number=PARENT_NUMBER --jq '.data.repository.issue.id')

CHILD_ID=$(gh api graphql -f query='
  query($owner: String!, $repo: String!, $number: Int!) {
    repository(owner: $owner, name: $repo) {
      issue(number: $number) { id }
    }
  }' -F owner=OWNER -F repo=REPO -F number=CHILD_NUMBER --jq '.data.repository.issue.id')

gh api graphql -f query='
  mutation($parentId: ID!, $childId: ID!) {
    addSubIssue(input: { issueId: $parentId, subIssueId: $childId }) {
      subIssue { number title }
    }
  }' -F parentId="$PARENT_ID" -F childId="$CHILD_ID"
```

If `addSubIssue` errors on unknown field — sub-issues are not enabled in the repo. Fallback: mention `#child` in the parent body.

## Blocked by / blocks (Relationships)

UI calls this **Relationships**; the REST API calls it **issue dependencies** ([GitHub REST docs](https://docs.github.com/en/rest/issues/dependencies)). Use REST — there is no `addIssueDependency` mutation in the [GitHub GraphQL mutations reference](https://docs.github.com/en/graphql/reference/mutations).

```bash
BLOCKER_DB_ID=$(gh api repos/OWNER/REPO/issues/BLOCKER_NUMBER --jq .id)

gh api repos/OWNER/REPO/issues/BLOCKED_NUMBER/dependencies/blocked_by \
  -X POST -F issue_id=$BLOCKER_DB_ID
```

Check current: `gh api repos/OWNER/REPO/issues/N/dependencies/blocked_by` (array of blockers).

If POST returns 404 — Relationships not enabled. Fallback: one line `Blocked by #N` in the blocked issue body, ask user to enable Relationships in settings.

## Related

No native field. Mention `#N` in Context or Goal where it reads naturally. GitHub renders a bidirectional link in the UI. Don't add a `**Related:**` block.

## Series of issues (epic + children)

1. Create parent first, record its number.
2. Create each child, record numbers.
3. For each child, call `addSubIssue`. The parent body needs **no** child list — GitHub renders it from the sub-issues API.

If sub-issues API is unavailable, fall back to a checklist in the parent body:

```markdown
**Children:**
- [ ] #11 — Email authentication
- [ ] #12 — Password recovery
- [ ] #13 — Two-factor via TOTP
```
