#!/usr/bin/env python3
"""PostToolUse hook: warns when the orchestrator's own context balloons.

Fires after each sub-agent dispatch (Agent / Task). Tail-reads the live
transcript — never a full parse — takes the latest main-thread context size,
and emits a warning when it crosses a threshold. The orchestrator main thread
is re-read every turn and rebuilt cold after idle gaps, so it is the dominant
cost surface; this is the in-run trigger to pivot before a run goes wrong.

Throttled to fire once per escalation (healthy → warn → alarm), not on every
dispatch. Any error exits 0 silently — a guard must never block a real run.
"""
import hashlib
import json
import os
import sys

WARN = 400_000   # main-thread context tokens — orchestrator hoarding
ALARM = 600_000  # paying heavily per turn just to re-read; pivot now
TAIL_BYTES = 96_000


def latest_main_ctx(path):
    """Latest main-thread assistant context size, via a bounded tail read."""
    with open(path, "rb") as f:
        f.seek(0, os.SEEK_END)
        size = f.tell()
        f.seek(max(0, size - TAIL_BYTES))
        chunk = f.read().decode("utf-8", "ignore")
    for line in reversed(chunk.splitlines()):
        try:
            o = json.loads(line)
        except (ValueError, TypeError):
            continue
        if o.get("type") != "assistant" or o.get("isSidechain"):
            continue
        u = o.get("message", {}).get("usage")
        if u:
            return (u.get("input_tokens", 0)
                    + u.get("cache_read_input_tokens", 0)
                    + u.get("cache_creation_input_tokens", 0))
    return None


def level(ctx):
    if ctx >= ALARM:
        return 2
    if ctx >= WARN:
        return 1
    return 0


def throttle_key(data, path):
    sid = data.get("session_id") or os.path.basename(path)
    return os.path.join("/tmp", "orch-guard-" + hashlib.md5(sid.encode()).hexdigest() + ".lvl")


def main():
    try:
        data = json.loads(sys.stdin.read() or "{}")
    except (ValueError, TypeError):
        return
    path = data.get("transcript_path")
    if not path:
        return
    path = os.path.expanduser(path)
    if not os.path.isfile(path):
        return

    ctx = latest_main_ctx(path)
    if ctx is None:
        return
    lvl = level(ctx)

    keyfile = throttle_key(data, path)
    last = 0
    try:
        with open(keyfile) as f:
            last = int(f.read().strip())
    except (OSError, ValueError):
        pass
    try:
        with open(keyfile, "w") as f:
            f.write(str(lvl))
    except OSError:
        pass

    # Fire only when crossing up into a worse band.
    if lvl <= last or lvl == 0:
        return

    k = ctx // 1000
    if lvl == 2:
        head = f"🔴 Orchestrator context {k}K (>{ALARM // 1000}K)"
        action = ("you are paying heavily every turn just to re-read this thread, "
                  "and any idle gap rebuilds it from cold. Stop accumulating: route "
                  "remaining recon/reads through sub-agents that return digests, and "
                  "summarize the thread before continuing.")
    else:
        head = f"🟠 Orchestrator context {k}K (>{WARN // 1000}K)"
        action = ("the orchestrator is hoarding context instead of delegating. Keep "
                  "file reads and recon inside sub-agents; hold only the plan, finding "
                  "summaries, and gate decisions in this thread.")

    print(json.dumps({
        "systemMessage": f"{head} — {action}",
        "hookSpecificOutput": {
            "hookEventName": "PostToolUse",
            "additionalContext": f"[context-guard] {head}. {action}",
        },
    }))


if __name__ == "__main__":
    try:
        main()
    except Exception:
        pass  # a guard must never break the run
