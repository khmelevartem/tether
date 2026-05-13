# Engineering Documentation

Implementation-side guidance: how the code is organized, what principles we follow, and what rules new code must satisfy.

Product-side docs (vision, audience, features) live in [`docs/product/`](../product/README.md).

## Sections

- [Architecture Principles](architecture-principles.md) — Clean Architecture as a principle, not dogma. What we follow and what we explicitly skip.
- [Modules](modules.md) — current monolith, target module split, and the triggers that move a piece of code into its own module.
- [Dependency Injection](dependency-injection.md) — DI strategy now (manual composition root) and later (Metro). Concrete rules for new code.
- [Presentation Layer](presentation-layer.md) — Decompose-based components; how to write them, subscribe from Compose, and test.
- [Testing](testing.md) — структура тестов по source sets, стиль (`runTest`/виртуальное время), особенности Apple-таргетов.

## Writing style for these guides

These documents codify principles, not the current shape of the codebase — they should age slowly.

- **Code examples on abstract types**, not on real project classes. Examples pinned to concrete names rot at every rename.
- **Don't restate what the code already shows** (hierarchies, signatures, source set layout). The code is the one source of truth that drifts last; link to it instead of copying.
- **Lead with the rule.** Rationale and examples follow.

## Architecture Decision Records

ADRs in [`adr/`](adr/) capture the *why* behind one-time architectural choices. Living docs above; ADRs are append-only history. See [`adr/README.md`](adr/README.md) for conventions.

- [Presentation & Navigation](adr/adr-presentation-and-navigation.md) — chose Decompose over Voyager / custom thin layer / Compose Navigation MP / Premo.
- [macOS target — Native over Desktop JVM](adr/adr-macos-native-vs-jvm.md) — chose `macosArm64` Kotlin/Native to share `appleMain` with iOS.

