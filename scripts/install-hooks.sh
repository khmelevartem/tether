#!/bin/bash
# Install git pre-commit hook

HOOK_DIR=".git/hooks"
HOOK_FILE="$HOOK_DIR/pre-commit"

mkdir -p "$HOOK_DIR"

cat > "$HOOK_FILE" << 'EOF'
#!/bin/bash
# Pre-commit hook: auto-format with KtLint and stage the result

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
