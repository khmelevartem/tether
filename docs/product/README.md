# Product Documentation

Navigation hub. Start here, then go to the section you need.

For implementation guidance (module layout, architecture principles, DI rules), see [`docs/engineering/`](../engineering/README.md).

## Structure

```
docs/product/
  vision.md        — concept, principles, goals
  audience.md      — target audience
  competitors.md   — comparison with AirDrop, LocalSend, messengers, etc.
  design.md        — UX/UI principles, key screens
  tech-stack.md    — stack and architecture decisions
  security.md      — threat model, pairing, encryption
  monetization.md  — free vs. paid, working hypothesis
  roadmap.md       — MVP / Post-MVP / Later
  features/
    README.md         — feature list
    _template.md      — spec template for new features
    <slug>/
      spec.md         — product spec (what & why, authored by spec-writer)
      ux-brief.md     — UX brief (how-it-feels, authored by ux-expert, optional for non-UI features)
```

## Sections

- [Vision & Principles](vision.md) — what Tether is, why it exists, what guides decisions
- [Target Audience](audience.md) — who uses Tether and what they need
- [Competitors](competitors.md) — AirDrop, LocalSend, messengers — how Tether differs
- [Design](design.md) — visual language, key screens, tone of voice
- [Tech Stack](tech-stack.md) — platform choices, libraries, architecture rationale
- [Security & Privacy](security.md) — threat model, pairing flow, channel encryption
- [Monetization](monetization.md) — free tier, Pro candidates, open questions
- [Roadmap](roadmap.md) — what ships in MVP, what comes next, what's deferred
- [Features](features/README.md) — all features, their status and links to specs

## For the AI agent

When working on a GitHub Issue:
1. Check if the issue links to a feature doc — read it first
2. Use feature docs as the source of acceptance criteria
3. When in doubt about scope, the feature doc overrides the issue description
