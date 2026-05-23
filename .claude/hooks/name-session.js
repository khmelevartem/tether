#!/usr/bin/env node
// Sets sessionTitle when a session starts with /implement or /code-review.
// Format: "#<N> impl: <title>" or "#<N> review: <title>"

const input = JSON.parse(require('fs').readFileSync('/dev/stdin', 'utf8'));
const prompt = (input.prompt || '').toLowerCase();

const workMatch = prompt.match(/implement\D*(\d+)|изучи\s+issue\s+(\d+)/);
const reviewMatch = prompt.match(/code[_-]review\D*(\d+)/);

let issueNum = null;
let type = null;

if (workMatch) {
  issueNum = workMatch[1] || workMatch[2];
  type = 'impl';
} else if (reviewMatch) {
  issueNum = reviewMatch[1];
  type = 'review';
}

if (!issueNum) process.exit(0);

const { execSync } = require('child_process');
try {
  const title = execSync(
    `gh issue view ${issueNum} --json title --jq '.title'`,
    { encoding: 'utf8', stdio: ['pipe', 'pipe', 'ignore'] }
  ).trim();
  if (!title) process.exit(0);
  console.log(JSON.stringify({
    hookSpecificOutput: {
      hookEventName: 'UserPromptSubmit',
      sessionTitle: `#${issueNum} ${type}: ${title}`,
    },
  }));
} catch (_) {
  process.exit(0);
}
