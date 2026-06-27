#!/usr/bin/env bash
# Emits the reviewer rosters for a run, computed from the live committed diff.
# Called at each review step (inner-loop, full-review); the model never
# self-selects reviewers.
#
# Usage: select-reviewers.sh <track> <type> <touched>
#   track:   docs | code
#   type:    feature | bugfix | refactor | infra | docs | dependency | none | unknown
#   touched: comma-separated subset of {ui,code,platform,docs,engdoc,claude,ux-brief} (empty ok)
#
# Output (stdout):
#   inner-loop-reviewers: <space-separated> (narrow fast-wave roster per track)
#   wave-a-reviewers: <space-separated>
#   wave-b-reviewers: review-adversarial

set -euo pipefail

TRACK="${1:-}"
TYPE="${2:-unknown}"
TOUCHED="${3:-}"

if [ -z "$TRACK" ]; then
  echo "error: track argument required (docs|code)" >&2
  exit 1
fi
if [ "$TRACK" != "code" ] && [ "$TRACK" != "docs" ]; then
  echo "error: unknown track '$TRACK'; expected docs or code" >&2
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

# Documentation artifacts under review — `review-consistency` covers these
# whatever the track, so a code change that also edits docs still gets the
# cross-cutting consistency pass.
has_doc_artifact() {
  has_touched "docs" || has_touched "engdoc" || has_touched "ux-brief" || has_touched "claude"
}

# ── Inner-loop reviewer roster ──────────────────────────────────────────────
# Narrow on purpose: only reviewers whose miss cost compounds — across iterations
# (code) or down the dependent-layer DAG (docs). Wave A is the broad final gate.

INNER_REVIEWERS=""
add_inner() { INNER_REVIEWERS="${INNER_REVIEWERS:+$INNER_REVIEWERS }$1"; }

if [ "$TRACK" = "code" ]; then
  if ! is_refactor; then
    add_inner "review-correctness"
  fi
  # review-architecture runs unconditionally for code-track work; over-inclusion
  # is harmless, so no per-change threshold is applied.
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
else
  # docs track — foundation gate before dependent layers are written on top.
  # consistency (cross-refs / scope) and architecture (engdoc / ADR) compound
  # down the layer DAG; glossary fixes are mechanical and left to the final wave.
  if has_doc_artifact; then
    add_inner "review-consistency"
  fi
  if has_touched "engdoc"; then
    add_inner "review-architecture"
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
  if has_doc_artifact; then
    add_a "review-consistency"
  fi
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
  if has_doc_artifact; then
    add_a "review-consistency"
  fi
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
