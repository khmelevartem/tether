#!/usr/bin/env bash
set -euo pipefail

. "$(dirname "${BASH_SOURCE[0]}")/smoke-env.sh"

set +e
smoke_reset
echo "reset: cleared this worktree's smoke leftovers"
set -e

cd "$TETHER_ROOT"

./gradlew :composeApp:cliJar -q

JAR=$(ls composeApp/build/libs/tether-cli-*.jar composeApp/build/libs/tether-cli.jar 2>/dev/null | head -1 || true)
[ -z "$JAR" ] && { echo "FAIL: cli jar not found after build"; exit 1; }

echo "JAR=$JAR"
