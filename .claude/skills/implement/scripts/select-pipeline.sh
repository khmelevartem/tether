#!/usr/bin/env bash
# Emits the manifest: ordered active step IDs + reviewer rosters.
# The model passes the two judgment fields classify.sh cannot resolve.
#
# Usage: select-pipeline.sh <track> <type> <reentry> <touched>
#   track:   docs | code
#   type:    feature | bugfix | refactor | infra | docs | dependency | none | unknown
#   reentry: fresh | pr-feedback | unknown
#   touched: comma-separated subset of {ui,code,platform,docs,engdoc,claude,ux-brief} (empty ok)
#
# Output (stdout):
#   steps: <space-separated ordered step ids>
#   inner-loop-reviewers: <space-separated> (empty when track=docs)
#   wave-a-reviewers: <space-separated>
#   wave-b-reviewers: review-adversarial

set -euo pipefail

TRACK="${1:-}"
TYPE="${2:-unknown}"
REENTRY="${3:-fresh}"
TOUCHED="${4:-}"

if [ -z "$TRACK" ]; then
  echo "error: track argument required (docs|code)" >&2
  exit 1
fi

# ── Helpers ─────────────────────────────────────────────────────────────────

has_touched() {
  case ",$TOUCHED," in *",$1,"*) return 0 ;; esac
  return 1
}

is_refactor() {
  [ "$TYPE" = "refactor" ] && return 0
  return 1
}

# ── Step selection ───────────────────────────────────────────────────────────

if [ "$TRACK" = "code" ]; then
  if [ "$REENTRY" = "pr-feedback" ]; then
    STEPS="classify reentry-reconcile recon inner-loop simplify full-review runtime-verify commit-pr final-summary"
  elif [ "$TYPE" = "bugfix" ]; then
    STEPS="classify recon early-gates bugfix-root-cause plan inner-loop simplify full-review runtime-verify commit-pr final-summary"
  else
    STEPS="classify recon early-gates plan inner-loop simplify full-review runtime-verify commit-pr final-summary"
  fi
elif [ "$TRACK" = "docs" ]; then
  if [ "$REENTRY" = "pr-feedback" ]; then
    STEPS="classify reentry-reconcile consistency full-review commit-pr final-summary"
  else
    STEPS="classify recon layer-classify docs-dispatch consistency full-review commit-pr final-summary"
  fi
else
  echo "error: unknown track '$TRACK'; expected docs or code" >&2
  exit 1
fi

echo "steps: $STEPS"

# ── Inner-loop reviewer roster (code track only) ────────────────────────────

INNER_REVIEWERS=""

if [ "$TRACK" = "code" ]; then
  add_inner() { INNER_REVIEWERS="${INNER_REVIEWERS:+$INNER_REVIEWERS }$1"; }

  # Inner-loop is narrow on purpose: only reviewers whose miss cost compounds
  # across iterations belong here. Mechanical / single-pass / expensive ones
  # (dod, glossary, tests, design-system, visual, ux-brief, reuse) defer to
  # Wave A, which sees the final diff once.
  if ! is_refactor; then
    add_inner "review-correctness"
  fi
  # review-architecture runs for all code-track work; over-inclusion is safe
  # because a reviewer with nothing to flag returns APPROVE.
  add_inner "review-architecture"
  add_inner "review-guides"
  if has_touched "platform"; then
    add_inner "review-platform"
  fi
  if has_touched "ui"; then
    # review-ux-conformance requires a ux-brief for the touched feature; the
    # orchestrator resolves this at dispatch time — we include it here when ui
    # is touched; the orchestrator suppresses dispatch when no brief exists.
    add_inner "review-ux-conformance"
  fi
fi

echo "inner-loop-reviewers: $INNER_REVIEWERS"

# ── Wave A reviewer roster (full-review) ────────────────────────────────────

WAVE_A=""
add_a() { WAVE_A="${WAVE_A:+$WAVE_A }$1"; }

if [ "$TRACK" = "code" ]; then
  add_a "review-dod"
  add_a "review-guides"
  add_a "review-glossary"
  add_a "review-reuse"
  add_a "review-architecture"
  if [ "$TYPE" != "docs" ] && ! is_refactor; then
    add_a "review-correctness"
  fi
  if [ "$TYPE" != "infra" ]; then
    add_a "review-tests"
  fi
  if has_touched "platform"; then
    add_a "review-platform"
  fi
  if has_touched "ui"; then
    add_a "review-ux-conformance"
    add_a "review-design-system"
    add_a "review-visual"
  fi
  if has_touched "ux-brief"; then
    add_a "review-ux-brief"
  fi
else
  add_a "review-dod"
  add_a "review-guides"
  add_a "review-glossary"
  add_a "review-reuse"
  # review-architecture only when docs/engineering/** touched (ADR, living-doc, architecture-principles.md)
  if has_touched "engdoc"; then
    add_a "review-architecture"
  fi
  if has_touched "ux-brief"; then
    add_a "review-ux-brief"
  fi
fi

echo "wave-a-reviewers: $WAVE_A"
echo "wave-b-reviewers: review-adversarial"
