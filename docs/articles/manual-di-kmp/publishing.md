# Publishing guide — Habr + Medium

Operational steps to get the two drafts live. Platform mechanics change; each non-obvious claim links to the source it came from. Verify against the live platform before acting — these were checked in June 2026.

## Habr (Russian, `ru.md`)

### Account and the sandbox

A brand-new account cannot publish to the main site directly. The first article goes through the **Песочница** (sandbox): you submit it, and moderators (or other users) review it. Roughly under half of submissions get an invite directly; the rest land in the public sandbox, need rework, or are rejected. Once a moderator approves and issues an invite, the account becomes full and the article lands on the main page immediately. Small posts are reviewed in a couple of days; long articles can take a week or more. ([Песочница / help](https://habr.com/ru/docs/help/sandbox/), [how to get an invite](https://habr.com/ru/companies/habr/articles/881676/))

What reliably fails moderation: news, announcements/press releases, advertising, vacancies, questions, requests for help solving a task. ([sandbox help](https://habr.com/ru/docs/help/sandbox/)) This article is a technical deep-dive, which is squarely the kind of content the sandbox is for — the risk is formatting/polish, not topic.

### Formatting

Habr's editor supports **Habr Flavored Markdown (HFM)**. The new editor has a full markdown mode — enable it in settings before you start typing, or paste markdown and let it convert. ([Habr Flavored Markdown](https://habr.com/ru/docs/help/markdown/), [markdown mode in the new editor](https://habr.com/ru/companies/habr/articles/725748/))

Code syntax highlighting includes `kotlin` explicitly (also `java`, `swift`, `bash`, `json`, `xml`, and ~40 more). ([syntax highlighting languages](https://habr.com/ru/docs/help/markdown/)) The fenced ```` ```kotlin ```` blocks in the draft will highlight natively — no Gist embedding needed, unlike Medium.

The draft is already HFM-compatible. Before submitting:
- Upload the four inline diagrams to Habr's image host and replace the relative image links with the uploaded URLs.
- Upload `tamed-cover.jpg` into the editor's dedicated **cover image** field (separate from inline images) — this becomes the **КДПВ** (картинка для привлечения внимания), shown next to the title in the feed and previews. КДПВ is the single largest CTR lever after the title — ~28% influence on views per Habr's own surveys. Leaving the cover field empty means the publication appears in the feed without a thumbnail.
- The same `tamed-cover.jpg` is also embedded inline inside the article (in the «Ручной DI и разработка с ИИ-агентами» section) to anchor the wordplay — keep it there; the in-body image and the cover-field image have different purposes (feed thumbnail vs. visual punchline inside the text).

### Hubs and tags

When submitting, pick **hubs** (хабы) — the thematic feeds the article appears in. For this piece: *Kotlin*, *Программирование*, *Разработка под Android*, *Разработка мобильных приложений*; consider *Разработка под iOS* given the KMP angle. Tags are free-form keywords on top of hubs — add `Kotlin Multiplatform`, `Dependency Injection`, `KMP`, `composition root`, `Android`.

### First-article checklist

The official "how to write your first Habr article" guide is the canonical pre-submit checklist (title, intro hook, formatting, images, hubs). ([first-article checklist](https://habr.com/ru/companies/habr/articles/736940/)) Read it once before you submit; it's the single best predictor of passing moderation.

## Medium (English, `en.md`)

### Account and publishing

Anyone can create a Medium account and publish immediately — no sandbox, no gatekeeping on the first post. You can publish from your personal profile, or submit to a **publication** (a curated multi-author blog). Submitting to a publication means an editor reviews the draft first; after your first accepted piece, later submissions appear directly in the editor's publication menu. A story can be in only one publication at a time. ([Medium guide](https://nickwolny.com/writing-on-medium-guide/))

For a first technical post, two viable paths:
- **Personal profile** — fastest, full control, but reach is limited to your followers plus whatever curation/distribution picks up.
- **A Kotlin/Android publication** (e.g. *ProAndroidDev*, *Kt. Academy*, *Better Programming*-style tech pubs) — slower (editor review) but much larger built-in audience. Submission guidelines live on each publication's page; check current requirements before submitting.

### Distribution and tags

Medium uses human curation: curators read articles and mark quality content for **further distribution** — placement in reader dashboards and the daily/weekly digest emails. Distribution, not comments, is what drives reach; quality of writing is the lever. ([Medium guide](https://nickwolny.com/writing-on-medium-guide/)) Getting into a strong publication materially improves the odds, because the piece is then distributed to that publication's followers as well.

Up to **5 tags** per article; curators distribute to the followers of those tags. Use `Kotlin`, `Kotlin Multiplatform`, `Dependency Injection`, `Android`, `Software Architecture`. ([Medium guide](https://nickwolny.com/writing-on-medium-guide/))

### Code formatting — the one real friction point

Medium's native code block has **no real syntax highlighting**. The established fix for technical articles is to embed **GitHub Gists**: create a gist with the right filename extension (`.kt` for Kotlin), paste the gist URL on its own line in the Medium editor, and it embeds with proper highlighting. ([code highlighting on Medium](https://medium.com/@vegetablecode/6-ways-to-embed-source-code-in-medium-articles-a0b2f0ce24c7))

Practical consequence for the EN draft: each ```` ```kotlin ```` block becomes a small `.kt` gist. That's ~12 gists — tedious but one-time. Alternatively, keep short snippets in Medium's plain code block (legible, just unhighlighted) and reserve gists for the longer examples (the container, the provider, the fake container). Decide per block.

### Paywall and Partner Program

You choose per-article whether to put it behind Medium's paywall (meters against readers' 3-free-articles-a-month) or leave it free. **Friend links** give anyone free access to a paywalled piece and don't count against their quota — useful for sharing on social. The Partner Program (earnings from member reading time) requires a minimum of 100 followers to enroll. ([Medium guide](https://nickwolny.com/writing-on-medium-guide/)) For a showcase/attract-contributors goal, leaving it free (or free + friend links) maximizes reach over revenue.

## Cross-posting and canonical URL

Publishing the same substance on both Habr and Medium is fine — they're different language audiences. If you later mirror the English version on a personal blog or dev.to, set the **canonical URL** to whichever you consider primary so search engines don't split ranking. (Habr and Medium each treat their copy as canonical by default.)

## Where to promote

Match each channel to where KMP/Android developers actually congregate:

- **Kotlin Slack** (`kotlinlang.slack.com`) — `#multiplatform`, `#dependency-injection`, `#feed` channels. The highest-signal audience for this exact topic.
- **Reddit** — r/Kotlin, r/androiddev. Both accept "I wrote about X" posts if the content is substantive, not a thin promo.
- **X/Twitter and Mastodon** — tag the KMP/Android community; the Tether repo link plus the diagram-1 image (the source-set mirror) makes a strong card.
- **Telegram** — Russian-language Kotlin/Android channels for the Habr piece; post the Habr link with a two-line hook.
- **Hacker News** — possible for the English piece, but only the strongest technical writing survives there; submit, don't over-invest.
- **The Tether repo itself** — link both articles from the README and from `docs/engineering/dependency-injection.md` once live, closing the loop between the showcase and the code.

Timing: post mid-week, morning in the target audience's timezone (Habr — MSK; Medium/HN — US business hours). Publish, then seed the promotion channels within the first couple of hours while the article is fresh in each platform's ranking window.

## After publishing — close the issue

Issue #479's DoD requires both published URLs recorded in the issue. Once live:

```bash
gh issue comment 479 --body "Published:
- Habr (RU): <url>
- Medium (EN): <url>"
```

---

Sources:
- [Песочница / Устройство сайта / Хабр](https://habr.com/ru/docs/help/sandbox/)
- [Песочница Хабра: как получить инвайт / Хабр](https://habr.com/ru/companies/habr/articles/881676/)
- [Как написать первую статью на Хабр: чек-лист / Хабр](https://habr.com/ru/companies/habr/articles/736940/)
- [Habr Flavored Markdown / Хабр](https://habr.com/ru/docs/help/markdown/)
- [Мы добавили markdown-режим в новый редактор / Хабр](https://habr.com/ru/companies/habr/articles/725748/)
- [Writing on Medium: the ultimate guide / nickwolny.com](https://nickwolny.com/writing-on-medium-guide/)
- [6 Ways to Embed Source Code in Medium Articles / Medium](https://medium.com/@vegetablecode/6-ways-to-embed-source-code-in-medium-articles-a0b2f0ce24c7)
