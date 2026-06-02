#!/usr/bin/env bash
set -euo pipefail

# Kill any lingering CLI instances to avoid mDNS interference.
set +e
pgrep -fl 'com.tubetoast.tether-.*\.jar|composeApp:run' || echo "clean"
pkill -f 'com.tubetoast.tether.*\.jar' 2>/dev/null
set -e

cd "$(git rev-parse --show-toplevel)"

./gradlew :composeApp:cliJar -q

JAR=$(ls composeApp/build/libs/tether-cli-*.jar composeApp/build/libs/tether-cli.jar 2>/dev/null | head -1 || true)
[ -z "$JAR" ] && { echo "FAIL: cli jar not found after build"; exit 1; }

echo "JAR=$JAR"
export JAR
