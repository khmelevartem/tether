Create an issue for the task: $ARGUMENTS

1. Write a brief draft: title + field `**Type:**` (`FEATURE | BUGFIX | REFACTOR | INFRA | DOCS | DEPENDENCY`) + 2-4 sentences of context + DoD checklist + estimated `size:` (S — up to 4 hours, M — up to one day, L — up to three days). Show it to the user and wait for approval.
2. Create the issue via `gh issue create --body-file /tmp/issue-body.md --label size:<S|M|L>`.
3. If a parent is specified — link via GraphQL `addSubIssue`.
4. Return the number and URL of the created issue.

Do not ask clarifying questions if everything is clear from the description. The draft — in free form, without elaborate templates.
