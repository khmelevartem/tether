# Publishing — manual-di-kmp

Article-specific publishing record, decisions, and remaining steps. Generic platform mechanics live in the reusable checklists: [habr-checklist.md](../habr-checklist.md), [medium-checklist.md](../medium-checklist.md), [image-prep.md](../image-prep.md).

## Status

- **Habr (RU, `ru.md`)** — submitted to the Песочница (sandbox); awaiting moderation.
- **Medium (EN, `en.md`)** — draft submitted to **Kt. Academy** (primary); fallback **ProAndroidDev**, then personal profile. Awaiting editor review.
  Draft: <https://medium.com/@khmelyovartyom/hand-wired-di-in-kotlin-multiplatform-a-composition-root-instead-of-a-framework-ed995fb21b19>

## Habr specifics

- **Hubs:** *Kotlin*, *Программирование*, *Разработка под Android*, *Разработка мобильных приложений* (consider *Разработка под iOS*).
- **Tags:** `Kotlin Multiplatform`, `Dependency Injection`, `KMP`, `composition root`, `Android`.
- **Type:** Туториал. **Difficulty:** Средний.
- **Cover:** `tamed-cover.jpg` — in both the **КДПВ/cover field** (feed thumbnail) and inline at the top of the body (the cover field does not render in-body).

## Medium specifics

- **Publication path:** Kt. Academy (`contact@kt.academy`, cc `marcinmoskala@gmail.com`) → ProAndroidDev (`editors@proandroiddev.com`) → personal profile. First submission is by emailing the draft link; the in-editor *Submit to publication* only lists publications you are already a writer of. ([Write for Kt. Academy](https://blog.kotlin-academy.com/write-for-kotlin-academy-abebd70937ce), [ProAndroidDev guidelines](https://proandroiddev.com/submission-guidelines-b2efa7f46272))
- **Tags (5):** `Kotlin`, `Kotlin Multiplatform`, `Dependency Injection`, `Android`, `Software Architecture`.
- **Cover:** `hand-wired-cover.jpg` — set as the **feature image**; add an *AI-generated* credit caption.
- **Free, no paywall.**
- **Code → gists:** all 15 Kotlin blocks live in one public gist — [a4a4e1ac…](https://gist.github.com/khmelevartem/a4a4e1ac8ea773b90e1f089fbe0fed2f). To rebuild the Medium draft, generate a paste-whole `en.medium-ready.md` (= `en.md` with each block swapped for its `?file=` embed) with `bash ../scripts/md-to-medium.sh en.md https://gist.github.com/khmelevartem/a4a4e1ac8ea773b90e1f089fbe0fed2f`. After any code edit in `en.md`, regenerate it and sync the gist (`gh gist edit a4a4e1ac8ea773b90e1f089fbe0fed2f`). Verify the first embed renders one file, not the whole gist.
- **Captions:** move each short blockquote caption into Medium's native caption field; keep the provider-pattern list as body text ([image-prep.md](../image-prep.md)).

## Cross-posting and canonical

RU (Habr) and EN (Medium) are different-language audiences — running both is fine. If the EN piece is later mirrored (personal blog, dev.to), set the **canonical URL** to the primary copy so search ranking is not split.

## Where to promote (once live)

- **Kotlin Slack** (`kotlinlang.slack.com`) — `#multiplatform`, `#dependency-injection`, `#feed`.
- **Reddit** — r/Kotlin, r/androiddev (substantive post, not a thin promo).
- **X / Mastodon** — tag the KMP/Android community; repo link + the source-set diagram makes a strong card.
- **Telegram** — RU Kotlin/Android channels for the Habr piece.
- **LinkedIn** — repost with two lines of reflection (serves the hiring-signal goal).
- **Hacker News** — possible for the EN piece; submit, don't over-invest.
- **The Tether repo** — link both articles from the README and `docs/engineering/dependency-injection.md` once live.

Timing: mid-week, morning in the audience timezone (Habr — MSK; Medium/HN — US business hours); seed channels within the first hours of going live.

## After publishing — close #479

The DoD requires both published URLs recorded in the issue:

```bash
gh issue comment 479 --body "Published:
- Habr (RU): <url>
- Medium (EN): <url>"
```
