# Articles

Long-form technical articles published from the Tether codebase, plus the reusable
playbook for preparing the next one.

## Folder convention

One folder per article: `docs/articles/<slug>/`.

- `ru.md` — Russian version (Habr).
- `en.md` — English version (Medium).
- `publishing.md` — article-specific publishing notes (chosen hubs/tags, the gist URL, the substitution checklist).
- Assets next to the text: covers (`*-cover.{jpg,png}`), diagrams as Obsidian JSON Canvas (`*.canvas`) and their exported `*.png`.

The two language versions are not literal translations — each is tuned to its platform (title, hook, cover). Keep the substance in sync; let the framing diverge.

## Preparing a new article

Platform-agnostic prep, then the two per-platform checklists:

- **Images** — [`image-prep.md`](image-prep.md): cover specs per platform, diagram authoring, alt-text.
- **Habr (RU)** — [`habr-checklist.md`](habr-checklist.md): sandbox, КДПВ, hubs/tags, native code highlighting.
- **Medium (EN)** — [`medium-checklist.md`](medium-checklist.md): title/subtitle/cover layout, gist embeds, curation/distribution.

Scripts:

- [`scripts/md-code-to-gist.sh`](scripts/md-code-to-gist.sh) — split all fenced code blocks of one language from a markdown file into a single multi-file GitHub gist and print per-file Medium embed URLs.

## Why both platforms

Habr and Medium are different language audiences; publishing the same substance to both is fine. If a version is later mirrored elsewhere (personal blog, dev.to), set the **canonical URL** to whichever copy is primary so search ranking is not split.
