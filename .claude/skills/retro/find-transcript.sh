#!/usr/bin/env bash
# Print the current session's transcript .jsonl path.
#
# A session that ran in a git worktree is migrated to the origin repo's
# project directory when the worktree is removed after merge, leaving a
# smaller stub behind under the worktree's directory. Sorting matches by
# size and taking the first yields the complete transcript, not the stub.
find ~/.claude/projects -maxdepth 2 -name "${CLAUDE_CODE_SESSION_ID}.jsonl" -exec ls -1S {} + 2>/dev/null \
  | head -1 | grep . \
  || echo "NOT FOUND: CLAUDE_CODE_SESSION_ID=${CLAUDE_CODE_SESSION_ID}"
