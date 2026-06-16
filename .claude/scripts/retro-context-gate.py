#!/usr/bin/env python3
"""Retro context-bias gate.

A retro run in the same context that produced the work inherits that work's
framing — the agent just argued for these decisions and rationalises them, and
the signals it is least able to see from inside (an option pruned, an approach
abandoned) are the most valuable ones. This prints the CURRENT main-thread
context size for the newest session in the current project dir, plus a
PASS / ABORT verdict against a threshold; the retro skill aborts on ABORT and
asks the user to compact or start a fresh session first.

Current, not peak: peak stays high after a /compact, so only the latest-turn
context drops once the conversation is reset — that is the signal we gate on.

Errors open (PASS / UNKNOWN): a measurement failure must never block a real
retro. Same posture as the orchestrator-context-guard hook it borrows from.

Usage:
  retro-context-gate.py [--threshold N] [--dir <project-dir>]
"""
import argparse
import glob
import json
import os
import sys

THRESHOLD = 60_000   # current main-thread context tokens above which bias is likely
TAIL_BYTES = 96_000


def project_dir_from_cwd():
    # Claude Code encodes the project dir as the absolute cwd with every '/' and '.' → '-'.
    enc = os.getcwd().replace("/", "-").replace(".", "-")
    return os.path.expanduser(f"~/.claude/projects/{enc}")


def newest_jsonl(d):
    files = glob.glob(os.path.join(d, "*.jsonl"))
    return max(files, key=os.path.getmtime) if files else None


def latest_main_ctx(path):
    """Latest main-thread (non-sidechain) assistant context size, via a bounded tail read."""
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


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--threshold", type=int, default=THRESHOLD)
    ap.add_argument("--dir", default=None)
    a = ap.parse_args()

    d = a.dir or project_dir_from_cwd()
    jf = newest_jsonl(d) if os.path.isdir(d) else None
    if not jf:
        print(f"UNKNOWN — no session transcript under {d}; cannot measure context. "
              "Proceed, but ground the retro in the transcript and commit sequence, not in-context memory.")
        return
    ctx = latest_main_ctx(jf)
    if ctx is None:
        print(f"UNKNOWN — could not read context from {os.path.basename(jf)}; erroring open. Proceed with care.")
        return

    k = ctx // 1000
    t = a.threshold // 1000
    if ctx > a.threshold:
        print(f"ABORT — current context {k}K > {t}K threshold. This retro is running inside the "
              "work's own context and will inherit its framing. Stop now: ask the user to /compact "
              "(or start a fresh session) and re-invoke /retro, then re-derive facts from the transcript.")
        sys.exit(3)
    print(f"PASS — current context {k}K ≤ {t}K. Safe to run the retro.")


if __name__ == "__main__":
    try:
        main()
    except SystemExit:
        raise
    except Exception as e:  # never block a real retro on a gate bug
        print(f"UNKNOWN — gate error ({e}); erroring open. Proceed.")
