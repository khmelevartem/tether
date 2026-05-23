---
name: handoff
description: Compact the current conversation into a handoff document for a fresh agent to pick up. Use when the context has grown too large, the work needs to pause across sessions, or another agent will continue.
argument-hint: "What will the next session be used for?"
---

<!--
Adapted from Matt Pocock's `handoff` skill (https://github.com/mattpocock/skills, MIT-licensed, Copyright 2026 Matt Pocock).
Tether-specific changes: explicit `$TMPDIR` resolution, link to git/PR for any artifact already on disk.
-->

Write a handoff document summarising the current conversation so a fresh agent can continue the work. Save to the OS temporary directory (resolve via `$TMPDIR`, fall back to `/tmp`), not the workspace. Print the absolute path when done.

Include a **Suggested skills** section listing the slash commands or skills the next session should invoke first.

Do not duplicate content already captured in other artifacts (specs, ADRs, issues, commits, diffs, this conversation's PR description). Reference them by path or URL instead.

Redact sensitive information: API keys, tokens, credentials, PII.

If the user passed an argument, treat it as a description of what the next session will focus on and tailor the doc accordingly.
