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
exit 0
EOF

chmod +x "$PUSH_HOOK_FILE"
echo "✅ Pre-push hook installed at $PUSH_HOOK_FILE"

# ---------------------------------------------------------------------------
# prepare-commit-msg: restore commit subject lost during `git rebase --continue`
#
# Symptom: after resolving a conflict and running `git rebase --continue`,
# the resulting commit has only the `Co-Authored-By` trailer as its subject,
# the original subject line is gone. Reproducible in Claude Code's environment,
# likely caused by a GIT_EDITOR that truncates COMMIT_EDITMSG.
#
# Fix: when we detect this corruption (first meaningful line is a known trailer),
# restore the message from `$GIT_DIR/rebase-merge/message`, which git itself
# populates with the original commit message before invoking the editor.
# ---------------------------------------------------------------------------
PREPARE_HOOK_FILE="$HOOK_DIR/prepare-commit-msg"

cat > "$PREPARE_HOOK_FILE" << 'EOF'
#!/bin/bash
# prepare-commit-msg hook: restore subject lost during `git rebase --continue`

COMMIT_MSG_FILE="$1"
GIT_DIR=$(git rev-parse --git-dir 2>/dev/null)
REBASE_MSG="$GIT_DIR/rebase-merge/message"

# Only act during an in-progress rebase that has a saved original message
[ -f "$REBASE_MSG" ] || exit 0

# First meaningful line (skip comments and blanks)
FIRST_LINE=$(grep -v '^#' "$COMMIT_MSG_FILE" | grep -v '^[[:space:]]*$' | head -1)

# If the subject looks like a trailer, the original subject was lost — restore it.
if echo "$FIRST_LINE" | grep -qiE '^(Co-Authored-By|Signed-off-by|Reviewed-by|Acked-by|Tested-by|Helped-by|Reported-by):'; then
  cp "$REBASE_MSG" "$COMMIT_MSG_FILE"
  echo "ℹ️  prepare-commit-msg: restored subject from rebase-merge/message" >&2
fi

exit 0
EOF

chmod +x "$PREPARE_HOOK_FILE"
echo "✅ prepare-commit-msg hook installed at $PREPARE_HOOK_FILE"
