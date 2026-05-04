# Product Documentation

Navigation hub. Start here, then go to the section you need.

## Structure

```
docs/product/
  vision.md        — concept, principles, goals
  audience.md      — target audience
  tech-stack.md    — stack and architecture decisions
  features/
    README.md      — feature list and roadmap
    _template.md   — template for new feature docs
    *.md           — individual feature specs
```

## Sections

- [Vision & Principles](vision.md) — what Tether is, why it exists, what guides decisions
- [Target Audience](audience.md) — who uses Tether and what they need
- [Tech Stack](tech-stack.md) — platform choices, libraries, architecture rationale
- [Features](features/README.md) — all features, their status and links to specs

## For the AI agent

When working on a GitHub Issue:
1. Check if the issue links to a feature doc — read it first
2. Use feature docs as the source of acceptance criteria
3. When in doubt about scope, the feature doc overrides the issue description
