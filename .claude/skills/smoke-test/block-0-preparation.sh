#!/usr/bin/env bash
set -euo pipefail

. "$(dirname "${BASH_SOURCE[0]}")/smoke-env.sh"

# Kill any lingering CLI instances from a previous run of THIS worktree (scoped to its jar path,
# so a concurrent smoke run in another worktree is untouched).
set +e
if smoke_instances_alive; then
  echo "killing lingering instances from previous run"
  smoke_kill_instances
else
  echo "clean"
fi
rm -f "$SMOKE_DIR"/cli* 2>/dev/null
set -e

cd "$TETHER_ROOT"

./gradlew :composeApp:cliJar -q

JAR=$(ls composeApp/build/libs/tether-cli-*.jar composeApp/build/libs/tether-cli.jar 2>/dev/null | head -1 || true)
[ -z "$JAR" ] && { echo "FAIL: cli jar not found after build"; exit 1; }

echo "JAR=$JAR"
