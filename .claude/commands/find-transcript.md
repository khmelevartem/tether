---
description: Locate the exact transcript .jsonl file for the current Claude Code session
allowed-tools: Bash(find:*), Bash(ls:*), Bash(head:*), Bash(grep:*)
---

Session transcript path:

!`find ~/.claude/projects -maxdepth 2 -name "${CLAUDE_CODE_SESSION_ID}.jsonl" -exec ls -1S {} + 2>/dev/null | head -1 | grep . || echo "NOT FOUND: CLAUDE_CODE_SESSION_ID=${CLAUDE_CODE_SESSION_ID}"`

Report the path above verbatim. Do not search, grep, or reason about which file it is — the `find` result is authoritative.

The search spans every project directory because a session that ran in a git worktree is migrated to the origin repo's project directory when the worktree is removed after merge, leaving a smaller stub behind under the worktree's directory. Sorting by size and taking the first entry yields the complete transcript, not the stub.
