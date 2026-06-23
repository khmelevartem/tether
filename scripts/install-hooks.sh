#!/bin/bash
# Install git pre-commit and pre-push hooks.
# Resolves hook dir via git-common-dir so the script works from both
# the main checkout and any worktree.

set -e

COMMON_DIR=$(git rev-parse --git-common-dir)
HOOK_DIR="$COMMON_DIR/hooks"
HOOK_FILE="$HOOK_DIR/pre-commit"

mkdir -p "$HOOK_DIR"

cat > "$HOOK_FILE" << 'EOF'
#!/bin/bash
# Pre-commit hook: auto-format with KtLint and stage the result

# Self-heal local.properties in worktrees (Gradle needs it for Android SDK path).
COMMON_DIR=$(git rev-parse --git-common-dir 2>/dev/null)
if [ -n "$COMMON_DIR" ]; then
  MAIN_ROOT=$(cd "$COMMON_DIR/.." && pwd)
  if [ ! -f local.properties ] && [ -f "$MAIN_ROOT/local.properties" ]; then
    cp "$MAIN_ROOT/local.properties" local.properties
    echo "ℹ️  copied local.properties from $MAIN_ROOT"
  fi
fi

echo "🔍 Running ktlintFormat (all modules)..."
./gradlew ktlintFormat --quiet

if [ $? -ne 0 ]; then
  echo "❌ ktlintFormat failed — commit aborted."
  exit 1
fi

# Stage any files that ktlintFormat just changed
git diff --name-only | grep '\.kt$' | xargs -r git add

echo "✅ KtLint format done"

# Check TODO/FIXME comments reference a tracker
# Rule: docs/engineering/long-lived-artifacts.md §Deferred work carries a tracker.
# Catches // TODO, /* TODO, * TODO (KDoc), case-insensitive, that lack any digits on the line.
# Kotlin's `TODO("...")` builtin is excluded (no // or * prefix before TODO).
echo "🔖 Checking TODO/FIXME tracker references..."
TODO_VIOLATORS=""
while IFS= read -r f; do
  [ -z "$f" ] && continue
  [ -f "$f" ] || continue
  bad=$(awk 'tolower($0) ~ /(\/\/|\/?\*)[[:space:]]*(todo|fixme)/ && $0 !~ /[0-9]/ { print FILENAME":"NR": "$0 }' "$f" 2>/dev/null) || true
  if [ -n "$bad" ]; then
    TODO_VIOLATORS="${TODO_VIOLATORS}${bad}
"
  fi
done < <(git diff --cached --name-only --diff-filter=ACM | grep '\.kt$')

if [ -n "$TODO_VIOLATORS" ]; then
  echo "❌ TODO/FIXME without a tracker reference — commit aborted."
  echo "   Any form is fine (TODO(#123), todo #123, TODO[123]) — the line must contain digits."
  echo "   See docs/engineering/long-lived-artifacts.md §Deferred work carries a tracker."
  echo ""
  printf "%s" "$TODO_VIOLATORS"
  exit 1
fi
echo "✅ TODOs reference issues"

# Check doc links with lychee
if ! command -v lychee &> /dev/null; then
  echo "ℹ️  lychee not found — skipping link check. Install: brew install lychee or https://github.com/lycheeverse/lychee#installation"
else
  echo "🔗 Running lychee on full doc corpus..."
  lychee --offline --include-fragments --no-progress 'docs/**/*.md' '*.md'
  if [ $? -ne 0 ]; then
    echo "❌ lychee found broken links — commit aborted."
    exit 1
  fi
  echo "✅ Lychee link check done"
fi

exit 0
EOF

chmod +x "$HOOK_FILE"
echo "✅ Pre-commit hook installed at $HOOK_FILE"

# ---------------------------------------------------------------------------
# pre-push: run all tests before push
# ---------------------------------------------------------------------------
PUSH_HOOK_FILE="$HOOK_DIR/pre-push"

cat > "$PUSH_HOOK_FILE" << 'EOF'
#!/bin/bash
# Pre-push hook: run all tests across all modules

# Self-heal local.properties in worktrees (Gradle needs it for Android SDK path).
COMMON_DIR=$(git rev-parse --git-common-dir 2>/dev/null)
if [ -n "$COMMON_DIR" ]; then
  MAIN_ROOT=$(cd "$COMMON_DIR/.." && pwd)
  if [ ! -f local.properties ] && [ -f "$MAIN_ROOT/local.properties" ]; then
    cp "$MAIN_ROOT/local.properties" local.properties
    echo "ℹ️  copied local.properties from $MAIN_ROOT"
  fi
fi

echo "🧪 Running tests + building all targets (CLI, Desktop UI, Android, iOS)..."
./gradlew allTests :composeApp:installCli :composeApp:createDistributable :composeApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64 --quiet

if [ $? -ne 0 ]; then
  echo ""
  echo "❌ Tests or build failed — push aborted."
  echo "💡 Re-run the failing task without --quiet to see details."
  echo ""
  exit 1
fi

echo "✅ Tests passed, all targets compile"

# Online doc-link check — pre-push only; CI/pre-commit stay offline for speed.
if command -v lychee &> /dev/null; then
  echo "🌐 Running lychee on full doc corpus (online, including external URLs)..."
  lychee --include-fragments --no-progress 'docs/**/*.md' '*.md'
  if [ $? -ne 0 ]; then
    echo "❌ lychee found broken links — push aborted."
    exit 1
  fi
  echo "✅ Lychee online link check done"
else
  echo "ℹ️  lychee not found — skipping online link check. Install: brew install lychee"
fi

exit 0
EOF

chmod +x "$PUSH_HOOK_FILE"
echo "✅ Pre-push hook installed at $PUSH_HOOK_FILE"

# ---------------------------------------------------------------------------
# prepare-commit-msg: restore commit subject lost during `git rebase --continue`
#
# Symptom: after resolving a conflict and running `git rebase --continue`,
# COMMIT_EDITMSG loses its subject line — the first meaningful line is either
# a trailer or plain body text, never the original `#<N>: ...` subject.
# Reproducible in Claude Code's environment, likely caused by a GIT_EDITOR
# that truncates COMMIT_EDITMSG.
#
# Fix: when the first meaningful line of COMMIT_EDITMSG does not match our
# commit convention (`#<N>: …`, `retro from #<N>: …`, `plan sprint <N>: …`),
# restore from `$GIT_DIR/rebase-merge/message`,
# which git populates with the original commit message before invoking the editor.
# ---------------------------------------------------------------------------
PREPARE_HOOK_FILE="$HOOK_DIR/prepare-commit-msg"

cat > "$PREPARE_HOOK_FILE" << 'EOF'
#!/bin/bash
# prepare-commit-msg hook: restore subject lost during `git rebase --continue`

COMMIT_MSG_FILE="$1"
GIT_DIR=$(git rev-parse --git-dir 2>/dev/null)
REBASE_MSG="$GIT_DIR/rebase-merge/message"

[ -f "$REBASE_MSG" ] || exit 0

# Strip git template comments ("# " or bare "#") and blank lines.
# Our commit subjects start with "#<digits>:" and survive this filter.
FIRST_LINE=$(grep -vE '^#([[:space:]]|$)' "$COMMIT_MSG_FILE" | grep -v '^[[:space:]]*$' | head -1)

# Restore unless the first meaningful line already matches a valid subject:
# `#<N>: …`, `retro from #<N>: …`, or `plan sprint <N>: …` (see CLAUDE.md §Git conventions).
if ! echo "$FIRST_LINE" | grep -qE '^(#[0-9]+|retro from #[0-9]+|plan sprint [0-9]+):'; then
  cp "$REBASE_MSG" "$COMMIT_MSG_FILE"
  echo "ℹ️  prepare-commit-msg: restored subject from rebase-merge/message" >&2
fi

exit 0
EOF

chmod +x "$PREPARE_HOOK_FILE"
echo "✅ prepare-commit-msg hook installed at $PREPARE_HOOK_FILE"

# ---------------------------------------------------------------------------
# commit-msg: enforce the subject convention after the editor closes.
# Valid subjects (see CLAUDE.md §Git conventions):
#   #<N>: …            retro from #<N>: …            plan sprint <N>: …
# ---------------------------------------------------------------------------
MSG_HOOK_FILE="$HOOK_DIR/commit-msg"

cat > "$MSG_HOOK_FILE" << 'EOF'
#!/bin/bash
# commit-msg hook: enforce commit subject format

COMMIT_MSG_FILE="$1"

# Skip auto-generated merge / squash commits. git passes no source argument to
# commit-msg (only prepare-commit-msg gets it), so detect them from repo state.
GIT_DIR=$(git rev-parse --git-dir)
if git rev-parse -q --verify MERGE_HEAD >/dev/null 2>&1 || [ -f "$GIT_DIR/SQUASH_MSG" ]; then
  exit 0
fi

# Strip git template comments ("# " or bare "#") and blank lines, take the subject.
SUBJECT=$(grep -vE '^#([[:space:]]|$)' "$COMMIT_MSG_FILE" | grep -v '^[[:space:]]*$' | head -1)

if ! echo "$SUBJECT" | grep -qE '^(#[0-9]+:|retro from #[0-9]+:|plan sprint [0-9]+:)'; then
  echo "❌  Commit subject must start with '#<N>: …', 'retro from #<N>: …', or 'plan sprint <N>: …'" >&2
  echo "    Got: \"$SUBJECT\"" >&2
  exit 1
fi

exit 0
EOF

chmod +x "$MSG_HOOK_FILE"
echo "✅ commit-msg hook installed at $MSG_HOOK_FILE"
