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
- The same `tamed-cover.jpg` is also embedded inline at the very top of the article body (right after the H1) — Habr's cover field does not render in the article body, so without the inline copy a reader who clicked through would land on a body with no hero image. The two uploads serve different surfaces (feed thumbnail + social card vs. in-body hero above the lead), so they do not visually duplicate.

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

### Title, subtitle, and cover image

Medium expects a specific layout in the first lines of a story: line 1 is the **title**, line 2 is the **subtitle**, line 3 is the **cover image**. The source already follows that order (H1, italic subtitle line, cover image). When importing the markdown:
- Verify the title and subtitle picked up the correct typographic styles in the Medium editor — title uses the big "T", subtitle uses the small "T". If the imported subtitle landed as a body paragraph, re-select it and apply the subtitle style; otherwise it won't appear in the feed/social-card preview.
- Set the cover image as the **feature image** in the publish-settings panel (the same `hand-wired-cover.png`) so it's used for Medium's feed cards, the social preview, and the publication's "by this author" widgets.
- Since `hand-wired-cover.png` is AI-generated, add a short credit caption to the image in the editor (e.g. *Cover image: AI-generated*). Medium's distribution guidelines explicitly accept AI cover art when it's credited as such; uncredited AI art is flagged as a quality issue by some curators.

### Distribution and tags

Medium uses human curation: curators read articles and mark quality content for **further distribution** — placement in reader dashboards and the daily/weekly digest emails. Distribution, not comments, is what drives reach; quality of writing is the lever. ([Medium guide](https://nickwolny.com/writing-on-medium-guide/)) Getting into a strong publication materially improves the odds, because the piece is then distributed to that publication's followers as well.

Up to **5 tags** per article; curators distribute to the followers of those tags. Use `Kotlin`, `Kotlin Multiplatform`, `Dependency Injection`, `Android`, `Software Architecture`. ([Medium guide](https://nickwolny.com/writing-on-medium-guide/))

### Code formatting — the one real friction point

Medium's native code block has **no real syntax highlighting**. The established fix for technical articles is to embed **GitHub Gists**: paste a gist URL on its own line in the Medium editor and it embeds with proper Kotlin highlighting. ([code highlighting on Medium](https://medium.com/@vegetablecode/6-ways-to-embed-source-code-in-medium-articles-a0b2f0ce24c7))

All 15 Kotlin blocks of the EN draft already live in one public gist, one file per block: [gist/a4a4e1ac…](https://gist.github.com/khmelevartem/a4a4e1ac8ea773b90e1f089fbe0fed2f). A single gist keeps the GitHub profile to one entry; each file is embedded individually via the `?file=` query so it lands at the right spot in the article.

**Substitution checklist.** Walk the EN article top to bottom; replace each ```` ```kotlin ```` block, in order, with the matching embed URL on its own line. The ASCII source-set tree (the one non-Kotlin ```` ``` ```` block) stays a plain Medium code block — no highlighting needed. After pasting, verify the first embed renders a single file, not the whole gist; if it shows all 15, the `?file=` syntax didn't take and each file needs its own gist instead.

1. **The core idea — `AppContainer`**
   <https://gist.github.com/khmelevartem/a4a4e1ac8ea773b90e1f089fbe0fed2f?file=01-AppContainer.kt>
2. **Principles — constructor injection (bad/good)**
   <https://gist.github.com/khmelevartem/a4a4e1ac8ea773b90e1f089fbe0fed2f?file=02-constructor-injection.kt>
3. **Principles — platform context (bad/good)**
   <https://gist.github.com/khmelevartem/a4a4e1ac8ea773b90e1f089fbe0fed2f?file=03-platform-context.kt>
4. **Principles — composable anti-pattern**
   <https://gist.github.com/khmelevartem/a4a4e1ac8ea773b90e1f089fbe0fed2f?file=04-composable-antipattern.kt>
5. **Principles — named components, not data (bad/good)**
   <https://gist.github.com/khmelevartem/a4a4e1ac8ea773b90e1f089fbe0fed2f?file=05-container-named-components.kt>
6. **Configuration — `AppConfig` / `AndroidAppConfig`**
   <https://gist.github.com/khmelevartem/a4a4e1ac8ea773b90e1f089fbe0fed2f?file=06-AppConfig.kt>
7. **Provider pattern for Android**
   <https://gist.github.com/khmelevartem/a4a4e1ac8ea773b90e1f089fbe0fed2f?file=07-android-provider.kt>
8. **Composing the container — platform axis**
   <https://gist.github.com/khmelevartem/a4a4e1ac8ea773b90e1f089fbe0fed2f?file=08-compose-platform-axis.kt>
9. **Composing the container — flavor axis**
   <https://gist.github.com/khmelevartem/a4a4e1ac8ea773b90e1f089fbe0fed2f?file=09-compose-flavor-axis.kt>
10. **Testability — `FakeContainer`**
    <https://gist.github.com/khmelevartem/a4a4e1ac8ea773b90e1f089fbe0fed2f?file=10-FakeContainer.kt>
11. **Public and internal containers**
    <https://gist.github.com/khmelevartem/a4a4e1ac8ea773b90e1f089fbe0fed2f?file=11-public-internal-containers.kt>
12. **Library entry point — `createLibContainer`**
    <https://gist.github.com/khmelevartem/a4a4e1ac8ea773b90e1f089fbe0fed2f?file=12-createLibContainer.kt>
13. **Library entry point — app holds the container**
    <https://gist.github.com/khmelevartem/a4a4e1ac8ea773b90e1f089fbe0fed2f?file=13-app-holds-lib-container.kt>
14. **Library entry point — inject from the container**
    <https://gist.github.com/khmelevartem/a4a4e1ac8ea773b90e1f089fbe0fed2f?file=14-inject-from-lib-container.kt>
15. **Static access — typed providers**
    <https://gist.github.com/khmelevartem/a4a4e1ac8ea773b90e1f089fbe0fed2f?file=15-typed-providers.kt>

If a Kotlin block in `en.md` is later edited, sync the matching file in the gist: `gh gist edit a4a4e1ac8ea773b90e1f089fbe0fed2f`.

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
