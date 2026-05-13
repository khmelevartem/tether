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

These documents codify principles, not the current shape of the codebase. They should age slowly.

- **Code examples use abstract types** (`SomeService`, `SomeRepository`, `PlatformContext`), not real project classes. A guide that names `FileClient`, `MdnsDiscovery` or specific `actual` constructors in its examples will rot whenever those classes are renamed, refactored or split — and will be subtly wrong well before anyone notices.
- **Do not restate what the code already shows.** Class hierarchies, `abstract`/`provides` annotations, constructor signatures and source set layout are visible in the IDE. A guide that duplicates them is two sources of truth — the code is the one that drifts last. Reference real classes by markdown link when an example is genuinely needed; don't pin their current shape into the prose.
- **Lead with the rule.** Concrete examples and rationale follow. A reader who scans the heading and the first paragraph should already know what the rule is.

## Architecture Decision Records

ADRs in [`adr/`](adr/) capture the *why* behind one-time architectural choices. Living docs above; ADRs are append-only history. See [`adr/README.md`](adr/README.md) for conventions.

- [Presentation & Navigation](adr/adr-presentation-and-navigation.md) — chose Decompose over Voyager / custom thin layer / Compose Navigation MP / Premo.
- [macOS target — Native over Desktop JVM](adr/adr-macos-native-vs-jvm.md) — chose `macosArm64` Kotlin/Native to share `appleMain` with iOS.

