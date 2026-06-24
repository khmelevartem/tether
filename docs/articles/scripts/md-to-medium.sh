#!/usr/bin/env bash
#
# Regenerate the Medium-ready copy of an article: take the canonical markdown and
# replace every fenced code block of one language with the matching gist embed URL
# (?file=<name>), in order. Non-matching fenced blocks (e.g. ASCII diagrams) and
# all prose/images are left untouched.
#
# The canonical <article>.md keeps real fenced code (renders highlighted on
# GitHub); this derived <article>.medium-ready.md is a throwaway snapshot you
# paste whole into the Medium editor. Re-run after any code edit to the source.
#
# Usage:
#   bash md-to-medium.sh <article.md> <gist-url> [lang]
#     <article.md>  canonical source, e.g. docs/articles/<slug>/en.md
#     <gist-url>    full gist URL, e.g. https://gist.github.com/<user>/<id>
#     [lang]        fenced language to replace; default: kotlin
#
# Writes <dir>/<base>.medium-ready.md. The gist file order (alphabetical, so use
# the NN- prefixes from md-code-to-gist.sh) must match the code-block order in the
# article. The script errors if the counts differ.
#
# Requires: gh authenticated (read-only `gh gist view`).
set -euo pipefail

MD="${1:?usage: md-to-medium.sh <article.md> <gist-url> [lang]}"
GIST_URL="${2:?missing gist URL}"
LANG="${3:-kotlin}"

[[ -f "$MD" ]] || { echo "no such file: $MD" >&2; exit 1; }

BASE="${GIST_URL%%\?*}"          # strip any ?query
ID="${BASE##*/}"                 # last path segment = gist id

# Ordered embed URLs, one per gist file (sorted → relies on NN- filename prefixes).
# while-read instead of mapfile: mapfile is bash 4+, and macOS ships bash 3.2.
NAMES=()
while IFS= read -r nm; do NAMES+=("$nm"); done < <(gh gist view "$ID" --files | sort)
[[ ${#NAMES[@]} -gt 0 ]] || { echo "gist $ID has no files (or gh failed)" >&2; exit 1; }

URLS="$(mktemp)"
for nm in "${NAMES[@]}"; do echo "$BASE?file=$nm" >> "$URLS"; done

BLOCKS="$(grep -c "^\`\`\`$LANG$" "$MD" || true)"
if [[ "$BLOCKS" -ne "${#NAMES[@]}" ]]; then
  echo "mismatch: $BLOCKS \`\`\`$LANG blocks in $MD vs ${#NAMES[@]} gist files" >&2
  exit 1
fi

OUT="${MD%.md}.medium-ready.md"
awk -v urls="$URLS" -v fence="\`\`\`$LANG" '
  BEGIN { n=0; while ((getline line < urls) > 0) u[++n]=line; i=0; state="OUT" }
  state=="OUT" {
    if ($0==fence) { state="CODE"; next }
    if ($0 ~ /^```/) { state="PASS"; print; next }   # any other fenced block (```bash, bare ```) — pass through verbatim
    print; next
  }
  state=="CODE" { if ($0=="```") { print u[++i]; state="OUT" } next }
  state=="PASS" { print; if ($0=="```") state="OUT"; next }
' "$MD" > "$OUT"

echo "wrote $OUT — replaced ${#NAMES[@]} $LANG block(s)"
