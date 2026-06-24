# Habr publishing checklist (RU)

Distilled from Habr's official guides (sources at the bottom). Covers `ru.md`. Image specs live in [`image-prep.md`](image-prep.md).

## Account and the sandbox

A new account cannot publish to the main site directly — the first article goes through the **Песочница** (sandbox), where moderators review it. A direct invite lands the article on the main page; otherwise it goes to the public sandbox, needs rework, or is rejected. Short posts are reviewed in a couple of days, long ones up to a week or more.

These reliably **fail** moderation — check the draft against the list:

- [ ] Not a news item, announcement, press release, ad, vacancy, question, or request for help.
- [ ] Text uniqueness ≥ 75% (original material, not compiled from sources).
- [ ] Not written by a generative model. Habr rejects AI-generated text.
- [ ] Less than 50% of the article is lists.
- [ ] Squarely IT-related.
- [ ] Clean formatting: no wall-of-text, broken lines, typos, excessive emphasis, emoji, or unjustified caps.
- [ ] If translated, the translation reads natively.

## Title (заголовок)

The single biggest lever on views (~88% influence per Habr's surveys).

- [ ] Catchy, on-topic, keeps some intrigue.
- [ ] **Matches the content.** A clickbait mismatch risks demotion to the public sandbox and reader downvotes.
- [ ] Key technical keywords kept in the title (e.g. the framework / platform name).

## Lead (текст до ката)

Shown in the feed before the "Читать далее" cut (~75% influence on views).

- [ ] The lead is a **hook**, not a flat thesis — signals the topic and leaves intrigue.
- [ ] Lead is present and formatted (an unformatted/missing lead makes the feed card look empty).

## Cover (КДПВ)

Картинка для привлечения внимания (~28% influence). Full specs in [`image-prep.md`](image-prep.md).

- [ ] Uploaded into the editor's dedicated **cover field** (second screen, «Отображение публикации в ленте») — not only inline.
- [ ] The **same image embedded inline at the top of the body** (the cover field does not render in the article body).
- [ ] Horizontal, ≤ 1 MB, ~780×440, jpg/png.

## Formatting (Habr Flavored Markdown)

- [ ] Markdown mode enabled in the new editor before typing (or paste markdown and let it convert).
- [ ] Section headings as H2, sub-sections as H3 (→ `<h2>`/`<h3>`).
- [ ] Blank line between paragraphs.
- [ ] Code in fenced ```` ```kotlin ```` blocks — Habr highlights `kotlin` (and `java`, `swift`, `bash`, `json`, `xml`, ~40 more) **natively**. No gist embedding needed (unlike Medium).
- [ ] Inline diagrams uploaded to Habr's image host; relative `*.png` links replaced with the uploaded URLs.

## Hubs and tags

- [ ] Up to **5 hubs** (хабы), each genuinely matching the content — more relevant hubs = more reach.
- [ ] Free-form **tags** on top of the hubs (key terms only).

## Before submitting

- [ ] Read the official [first-article checklist](https://habr.com/ru/companies/habr/articles/736940/) once — it is the best single predictor of passing moderation.
- [ ] Fact-check load-bearing claims.

---

Sources:

- [Как написать первую статью на Хабр: полный чек-лист](https://habr.com/ru/companies/habr/articles/736940/)
- [Песочница / Устройство сайта](https://habr.com/ru/docs/help/sandbox/)
- [Песочница Хабра: как получить инвайт](https://habr.com/ru/companies/habr/articles/881676/)
- [Оформляем публикацию / Для авторов](https://habr.com/ru/docs/authors/design/)
- [Habr Flavored Markdown](https://habr.com/ru/docs/help/markdown/)
