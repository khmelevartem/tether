#!/usr/bin/env bash
# Shared environment for smoke-test blocks. Source at the top of every block:
#   . "$(dirname "${BASH_SOURCE[0]}")/smoke-env.sh"
#
# Blocks run in separate shells and coordinate only through fixed filesystem paths, so each
# block re-derives identical, worktree-scoped values from here. The scoping keeps two smoke
# runs in different worktrees from sharing /tmp paths, PID bookkeeping, or kill scope.

TETHER_ROOT="$(git rev-parse --show-toplevel)"

# Stable per-worktree id (same path → same id across blocks; different worktree → different id).
SMOKE_ID="$(printf '%s' "$TETHER_ROOT" | cksum | cut -d' ' -f1)"
SMOKE_DIR="/tmp/smoke-$SMOKE_ID"
mkdir -p "$SMOKE_DIR"

SMOKE_JAR="${JAR:-$(ls "$TETHER_ROOT"/composeApp/build/libs/tether-cli-*.jar \
  "$TETHER_ROOT"/composeApp/build/libs/tether-cli.jar 2>/dev/null | head -1 || true)}"
JAR="$SMOKE_JAR"

# Per-instance paths — fifo / log / pid / fifo-keeper-pid.
FIFO_A="$SMOKE_DIR/cliA-in"; LOG_A="$SMOKE_DIR/cliA.log"; PID_A="$SMOKE_DIR/cliA.pid"; KEEPER_A="$SMOKE_DIR/cliA-keeper.pid"
FIFO_B="$SMOKE_DIR/cliB-in"; LOG_B="$SMOKE_DIR/cliB.log"; PID_B="$SMOKE_DIR/cliB.pid"; KEEPER_B="$SMOKE_DIR/cliB-keeper.pid"
FIFO_C="$SMOKE_DIR/cliC-in"; LOG_C="$SMOKE_DIR/cliC.log"; PID_C="$SMOKE_DIR/cliC.pid"; KEEPER_C="$SMOKE_DIR/cliC-keeper.pid"
IOS_BUILD_LOG="$SMOKE_DIR/ios-build.log"
IOS_LAUNCH_LOG="$SMOKE_DIR/ios-launch.log"

# Basename prefix for files this run sends — lets receiver-dir cleanup target exactly this run.
SMOKE_SEND_PREFIX="smoke-$SMOKE_ID"

# Kill every smoke CLI java process launched from THIS worktree's jar, plus the FIFO keepers.
# Scoped to the worktree jar path, so a concurrent run in another worktree is left untouched.
# (The previous `pkill -f 'com.tubetoast.tether.*\.jar'` never matched `java -jar .../tether-cli.jar`.)
smoke_kill_instances() {
  [ -n "$SMOKE_JAR" ] && pkill -9 -f "$SMOKE_JAR" 2>/dev/null
  for f in "$KEEPER_A" "$KEEPER_B" "$KEEPER_C"; do
    [ -f "$f" ] && kill -9 "$(cat "$f")" 2>/dev/null
  done
  return 0
}

# True if any smoke CLI java process from this worktree is currently alive.
smoke_instances_alive() {
  [ -n "$SMOKE_JAR" ] && pgrep -f "$SMOKE_JAR" >/dev/null 2>&1
}
