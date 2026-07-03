---
name: check-review
description: Read the latest review comments on the PR you are working on, classify each as pointwise or structural (re-auditing the whole diff for structural ones), reject reviewer-priority labels in favor of own value assessment, fix valid ones, reply to invalid ones in the original thread via `in_reply_to`, push. Use when the user says "check review", "process PR comments", "address review", "посмотри ревью", "обработай комменты", or invokes `/check-review`.
---

Repo-specific paths for this project live in `.claude/project.json` — consult it; references below name their config keys.

Review the latest comments on the PR you are working on. Assess which of them are valid, fix the valid ones. If there are invalid ones, reply to them explaining why you consider them invalid. Push the changes.

## Important: pointwise fix vs structural finding

Before fixing, classify the finding.

- **Pointwise** — refers to a specific line / file / wording. Examples: "typo", "this check should be strict equality", "a test is missing here". Fixed locally.
- **Structural** — points to a principle being violated at that location. Examples: "features are split by platform, that's wrong", "this function shouldn't know about layer X", "naming contradicts the convention".

If the finding is **structural** — don't limit yourself to fixing only the cited point. **Re-audit everything changed in the PR and adjacent files for the same mistake**. Every violation of the same principle is a candidate for fixing in this same pass. Without a re-audit you will very likely return the PR with the same errors in other places, and the user will catch them again.

**The re-audit must cover all axes, not only the one cited in the finding.** If the reviewer pointed to a divergence between two artifacts (ADR vs practical doc, principle doc vs implementation, new spec vs adjacent docs) — check **all possible consistency axes** between them (naming, scope, lifecycle, timing, wording), not just the specific axis from the finding. A narrow re-audit (limited to the words in the finding's text) often leaves other divergences of the same class, and they come back in the next review round.

**Multiple comments → one principle.** When two or more comments are worded differently but point in the same direction (e.g., to the source of identity, layering, scope storage) — the presumption is: this is **one principle at multiple sites**, not independent considerations. Name the principle explicitly, then do one re-audit for it, then apply the fix everywhere. Interpreting related comments separately is a typical path to a compromise solution that comes back in the next review round.

Signals that a finding is structural:
- words like "wrong", "must not", "should not", "principle", "always / never"
- explanation through a category, not a specific instance (not "this exact line", but "lines like this")
- reference to a guide / spec / another project example
- the same complaint repeated on other lines/files in the same review

If in doubt — ask the author of the finding: "Is this specifically about X, or are there other places where the same principle should be applied?" — it is better to ask than to go through the PR three times.

**Wanting to keep what the reviewer questioned is a signal to ask.** If a comment is of the "why X / why like this" variety, and you have a justification for keeping X (KDoc, comment, renaming) — almost always the reviewer wanted to remove X entirely, not justify it. Defending the status quo through documentation is not the answer expected of you. Before locking in a compromise — ask the author directly.

## Important: evaluate the value of a fix yourself, don't trust priority labels

Do not skip a comment just because the reviewer tagged it "optional" / "non-blocking" / "nice to have" / "follow-up" / "not a blocker". That is the reviewer's assessment of priority — not your assessment of value.

Before agreeing to move an "optional" item out of scope, ask yourself:
- Does the fix cover an item from the DoD of the current task (issue body, acceptance criteria, non-functional requirements, edge cases)?
- Does it catch a class of bugs that are hard to catch any other way (regression risk, subtle behavior)?
- Is the fix scope moderate — tens of lines, no new components / contracts / dependencies / new platforms?

If at least one of these questions is answered "yes", and the overall volume does not exceed a reasonable extension — **do it in this same PR**. The "optional" tag does not cancel the value of the fix or exempt you from it.

"Scope grows critically" = new target / new component / changing a public contract / needing to change adjacent layers / unpredictable volume due to revealing a hidden bug. Volume in lines is not the main criterion; the main criterion is relevance to the current task. Full fold-vs-defer test — the [scope-discipline canon](../../../docs/engineering/scope-discipline.md) (under `docCorpus.engineeringDir`); provenance is not an axis.

Especially careful: when the reviewer themselves referenced a DoD item or edge case from the issue, the "optional" tag in that case often means "not a blocker for me personally", but strictly speaking this is a **gap in the DoD**, and closing it is the responsibility of the current task's assignee, not the next review or a future contributor. Do not offload your DoD onto someone else's backlog.

## Important: responding to comments

**ALWAYS** reply **in the thread of the original comment**, never as a separate top-level PR comment. The link "suggestion → reaction" must be visible in one place — otherwise the reviewer doesn't understand what exactly you replied to, and the thread remains "hanging".

Any inline review comment (including one attached to a review submission) lives in `pulls/PR/comments` and is replied to via `in_reply_to`:

```bash
# 1. Find the COMMENT_ID of the needed comment in the thread
gh api repos/OWNER/REPO/pulls/PR/comments \
  --jq '.[] | {id, user: .user.login, path, line, in_reply_to_id, body}'

# 2. Reply in the same thread (in_reply_to can point to any comment
#    in the thread — GitHub will attach the reply to the root automatically)
gh api repos/OWNER/REPO/pulls/PR/comments \
  -X POST \
  -F in_reply_to=COMMENT_ID \
  -F body="reply text"
```

Write explanations and justifications there — the reviewer will get a notification and see the reply in the context of the code. `issues/PR/comments` is the top-level conversation, not a thread: **do not use it** for replying to reviews.

A review body (general review text not attached to a line) has no separate thread API, so **edit the review body itself and append your reaction at the end** — that way, when reading the review it is immediately visible that a reply was made:

```bash
# Get the current review body
gh api repos/OWNER/REPO/pulls/PR/reviews/REVIEW_ID --jq '.body'

# Update the body — original + separator + reaction
gh api repos/OWNER/REPO/pulls/PR/reviews/REVIEW_ID \
  -X PUT \
  -F body="$(printf '%s\n\n---\n\n%s' "ORIGINAL_REVIEW_TEXT" "reaction text")"
```

Format:

```
review
---
reaction to the review
```

Do not reply to a review body via top-level `issues/PR/comments` — the link to the review is lost.
