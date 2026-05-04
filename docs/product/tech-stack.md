# Tech Stack & Architecture Decisions

## Platform Targets

<!-- Which platforms are supported and why.
     Note any platforms explicitly excluded and why. -->

| Platform | Status | Notes |
|----------|--------|-------|
| Android  |        |       |
| iOS      |        |       |
| macOS    |        |       |
| Desktop (JVM) |   |       |

## Core Stack

<!-- Key technology choices with rationale.
     Focus on non-obvious choices — "we use Kotlin because KMP" is obvious,
     "we use Ktor instead of OkHttp because X" is worth capturing. -->

| Component | Technology | Why |
|-----------|-----------|-----|
| UI | Compose Multiplatform | |
| Networking | Ktor | |
| Service Discovery | mDNS (platform-specific) | |
| Serialization | kotlinx.serialization | |

## Architecture Decisions

<!-- Significant decisions that are hard to reverse.
     Format: Decision → Rationale → Tradeoff accepted -->

### [Decision title]
**Decision:** ...
**Why:** ...
**Tradeoff:** ...

---

## Constraints

<!-- Technical constraints that limit design choices:
     min API levels, device capabilities, OS permissions, etc. -->

-
-
