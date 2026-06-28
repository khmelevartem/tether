# Image preparation

Shared across both platforms. Two kinds of image: the **cover** (one per article, the single largest click lever after the title) and **inline diagrams**.

## Cover

| | Habr (КДПВ) | Medium (feature image) |
|---|---|---|
| Orientation | Horizontal. No tall vertical images. | Horizontal / wide (hero crop). |
| Recommended size | 780×440 | Wide enough for a full-bleed hero (≥1500px wide). |
| Weight | ≤ 1 MB | No hard limit; keep it lean. |
| Format | jpg / png / gif (avoid GIF) | jpg / png |
| Content | No crude or erotic content. | Original preferred; stock OK if chosen with care. |
| AI-generated | Allowed. | Allowed **only if credited** — add a credit caption (e.g. *Cover image: AI-generated*). Uncredited AI art is a curator turn-off and a quality flag. |

- The cover and the title work as one hook. If the title carries wordplay, the cover should illustrate it.
- **Habr quirk:** the cover field (second editor screen, «Отображение публикации в ленте») renders only in the feed / RSS / social card — **not** in the article body. Embed the same image inline at the very top of the body (right after the H1) so a click-through reader still sees a hero. The two uploads serve different surfaces and do not visually duplicate.
- **Medium:** the cover is the third line of the story (after title and subtitle) and is also set as the feature image in publish settings.

## Inline diagrams

- Author diagrams as Obsidian **JSON Canvas** (`*.canvas`) and export to `*.png` next to the source. Keep the `.canvas` so the diagram stays editable.
- Colour convention used in this repo's diagrams: an accent colour marks the active/selected element; alternatives stay uncoloured. State the convention in the inline caption under the diagram.
- On publish, upload each diagram to the platform's image host and replace the relative `*.png` link with the uploaded URL. Neither platform serves images from the repo.

## Alt-text

Write descriptive alt-text, never the filename. It is read by screen readers and shown by the GitHub markdown render. `![Hands wiring a transparent device](cover.png)`, not `![cover](cover.png)`.

## Captions

The source markdown puts a short caption under a diagram as a `> blockquote`. That renders acceptably on GitHub, but a blockquote is semantically a quote, not a caption — handle it per platform on publish:

- **Habr** — the blockquote renders as a styled quote (vertical bar). Acceptable; the editor's native image caption (подпись) is cleaner. Either works.
- **Medium** — a blockquote renders as a **pull-quote** (large decorative quotation), wrong under an image. Move the text into Medium's **native caption** (select the image → caption field appears beneath). Markdown has no syntax for it, so this is a manual step after import.
- **Long / structured captions** (a multi-step list, not a terse line) are explanatory body text, not a caption. Keep them as a normal numbered list under the image — never a blockquote, and not in a caption field (too long, and a list won't fit there). Short one/two-sentence captions are fine as a blockquote in source and as a native caption on Medium.
