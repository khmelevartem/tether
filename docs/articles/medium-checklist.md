# Medium publishing checklist (EN)

Distilled from Medium's distribution and formatting guides (sources at the bottom). Covers `en.md`. Image specs live in [`image-prep.md`](image-prep.md).

## Account and publishing

Anyone publishes immediately — no sandbox. Two paths:

- **Personal profile** — fastest, full control, reach limited to followers plus whatever curation picks up.
- **A publication** (e.g. *ProAndroidDev*, *Kt. Academy*) — editor review first, but a much larger built-in audience. A story can be in only one publication at a time.

## Title, subtitle, cover (first three lines)

Medium expects a fixed layout: **line 1 = title** (big "T"), **line 2 = subtitle** (small "T"), **line 3 = cover image**.

- [ ] Title is the first line and styled as title (big "T").
- [ ] **Subtitle present** as line 2, styled as subtitle (small "T"). It is indexed and shown in the feed/social-card preview — verify the style survived markdown import; if it landed as a body paragraph, re-apply.
- [ ] Title + subtitle + cover **invite the reader in and the content delivers** — not sensationalistic/tabloid, and not overly generic, mysterious, or formulaic.
- [ ] Proper case, no grammatical errors.
- [ ] Cover set as the **feature image** in publish settings; AI-generated covers carry a credit caption (see [`image-prep.md`](image-prep.md)).

## Lead

- [ ] The lead is a **hook**, not a flat thesis (mirrors the RU lead's framing).

## Formatting

- [ ] Section headings use the **primary header** (big "T"), sub-sections the **secondary header** (small "T"). Don't fake headers with bold — it's less readable and the algorithm reads real headers as structure.
- [ ] Short paragraphs; bold for key takeaways, italics for nuance, bullets for lists.
- [ ] **Internal header anchors are not supported.** Flatten any `[text](#anchor)` cross-reference to plain prose ("see the X section below/above"). Anchor-form links break on import.
- [ ] Code is **text, not images** (accessibility + copy-paste).

## Code → gists

Medium's native code block has no real syntax highlighting. The fix is GitHub Gist embeds.

- [ ] Generate one gist for the article with [`scripts/md-code-to-gist.sh`](scripts/md-code-to-gist.sh) — **one gist, one file per block**, numeric-prefixed to hold article order. One gist keeps the GitHub profile to a single entry.
- [ ] Embed each file individually via the `?file=<name>` query so each snippet lands at its own spot — paste each URL on its own line, in article order.
- [ ] **Verify the first embed** renders a single file, not the whole gist. If it shows all files, the `?file=` syntax didn't take — fall back to one gist per block.
- [ ] Non-code fenced blocks (e.g. ASCII diagrams) stay plain Medium code blocks — no gist.
- [ ] Public vs secret gist: **public** gives discovery/forks/stars (good for a showcase); **secret** keeps the profile clean but loses discovery.
- [ ] Record the gist URL and the per-file substitution list in the article's `publishing.md`.

> The gist-creation script must be run by a human: creating gists from repo code is blocked by the agent auto-mode as data exfiltration.

## Distribution and curation

Human curators mark stories for Boost / General / Network distribution; **writing quality is the lever**, not comments or popularity.

Disqualifiers from General Distribution — check against them:

- [ ] No clickbait, including visual clickbait.
- [ ] No misinformation or factually inaccurate claims (fact-check load-bearing statements).
- [ ] Tags match the content (tag-spam disqualifies).
- [ ] No large numbers of `@mentions`.
- [ ] At least 150 words (shorter is ineligible for curation).

## Tags

- [ ] Up to **5 tags**; curators distribute to the followers of those tags. Use specific, on-topic terms.

## Paywall and Partner Program

- [ ] Decide per-article: paywalled (counts against readers' free-articles quota) vs free. For a showcase / attract-contributors goal, **free** maximizes reach.
- [ ] **Friend links** bypass the quota — useful for social sharing.
- [ ] Partner Program (earnings) requires ≥ 100 followers to enroll.

---

Sources:

- [Medium's Distribution Guidelines (Boost / General / Network)](https://help.medium.com/hc/en-us/articles/360006362473-Medium-s-Distribution-Guidelines-How-curators-review-stories-for-Boost-General-and-Network-Distribution)
- [Tips for Formatting Your Title and Headers](https://blog.medium.com/tips-for-formatting-your-title-and-headers-1ff1a016ef75)
- [Writing on Medium: the ultimate guide / nickwolny.com](https://nickwolny.com/writing-on-medium-guide/)
- [6 Ways to Embed Source Code in Medium Articles](https://medium.com/@vegetablecode/6-ways-to-embed-source-code-in-medium-articles-a0b2f0ce24c7)
