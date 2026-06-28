#!/usr/bin/env bash
#
# Split every fenced code block of one language out of a markdown article into a
# SINGLE multi-file GitHub gist, then print per-file Medium embed URLs in article
# order. One gist keeps your gist listing to a single entry; embedding each file
# via ?file=<name> places each snippet at its own spot in the article.
#
# Usage:
#   bash md-code-to-gist.sh <article.md> [lang] [--secret]
#     <article.md>  path to the markdown file (e.g. docs/articles/<slug>/en.md)
#     [lang]        fenced language to extract; default: kotlin
#     [--secret]    create a secret gist instead of public
#
# Requires: gh authenticated with the `gist` scope.
#
# Note: run this yourself. Creating gists from repo code is blocked by the agent
# auto-mode as data exfiltration, so an agent cannot run it for you.
set -euo pipefail

MD="${1:?usage: md-code-to-gist.sh <article.md> [lang] [--secret]}"
LANG="${2:-kotlin}"
VIS="--public"
[[ "${3:-}" == "--secret" ]] && VIS="--secret"

# Map language -> file extension (the extension drives Medium's highlighting).
case "$LANG" in
  kotlin) EXT=kt ;;
  python) EXT=py ;;
  javascript) EXT=js ;;
  typescript) EXT=ts ;;
  java) EXT=java ;;
  swift) EXT=swift ;;
  *) EXT="$LANG" ;;
esac

[[ -f "$MD" ]] || { echo "no such file: $MD" >&2; exit 1; }
OUT="$(mktemp -d)"

# Extract each ```<lang> ... ``` block into NN.txt (NN = 1-based order).
awk -v lang="$LANG" -v out="$OUT" '
  $0 == "```" lang { inb=1; n++; f=sprintf("%s/%02d.txt", out, n); next }
  inb && $0 == "```" { inb=0; next }
  inb { print > f }
' "$MD"

shopt -s nullglob
files=()
for raw in "$OUT"/[0-9][0-9].txt; do
  num="$(basename "$raw" .txt)"
  # Derive a readable name from the first declaration in the block; the gist
  # filename becomes the caption Medium shows under the embed.
  decl="$(grep -m1 -oE '(class|interface|object|enum class|fun|val|var)[[:space:]]+[A-Za-z_][A-Za-z0-9_]*' "$raw" \
            | awk '{print $NF}' || true)"
  slug="${decl:-snippet}"
  fname="$OUT/${num}-${slug}.${EXT}"
  mv "$raw" "$fname"
  files+=("$fname")
done

[[ ${#files[@]} -gt 0 ]] || { echo "no \`\`\`$LANG blocks found in $MD" >&2; exit 1; }

echo "Extracted ${#files[@]} ${LANG} block(s). Creating one gist (${VIS#--})..."
url="$(gh gist create "${files[@]}" $VIS --desc "Code from $(basename "$MD")")"

echo
echo "Gist: $url"
echo
echo "=== Medium embed URLs, in article order — paste each on its own line ==="
for f in "${files[@]}"; do
  echo "${url}?file=$(basename "$f")"
done
