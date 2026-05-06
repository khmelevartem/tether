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

echo "🧪 Running tests (all modules)..."
./gradlew allTests --quiet

if [ $? -ne 0 ]; then
  echo ""
  echo "❌ Tests failed — push aborted."
  echo "💡 Run './gradlew allTests' to see the details."
  echo ""
  exit 1
fi

echo "✅ All tests passed"
exit 0
EOF

chmod +x "$PUSH_HOOK_FILE"
echo "✅ Pre-push hook installed at $PUSH_HOOK_FILE"
