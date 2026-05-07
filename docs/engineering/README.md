# Engineering Documentation

Implementation-side guidance: how the code is organized, what principles we follow, and what rules new code must satisfy.

Product-side docs (vision, audience, features) live in [`docs/product/`](../product/README.md).

## Sections

- [Architecture Principles](architecture-principles.md) — Clean Architecture as a principle, not dogma. What we follow and what we explicitly skip.
- [Modules](modules.md) — current monolith, target module split, and the triggers that move a piece of code into its own module.
- [Dependency Injection](dependency-injection.md) — DI strategy now (manual composition root) and later (Metro). Concrete rules for new code.
- [Presentation Layer](presentation-layer.md) — Decompose-based components; how to write them, subscribe from Compose, and test.

## Architecture Decision Records

ADRs in [`adr/`](adr/) capture the *why* behind one-time architectural choices. Living docs above; ADRs are append-only history. See [`adr/README.md`](adr/README.md) for conventions.

- [Presentation & Navigation](adr/adr-presentation-and-navigation.md) — chose Decompose over Voyager / custom thin layer / Compose Navigation MP / Premo.
- [macOS target — Native over Desktop JVM](adr/adr-macos-native-vs-jvm.md) — chose `macosArm64` Kotlin/Native to share `appleMain` with iOS.

## For the AI agent / contributor

Before writing code, read [dependency-injection.md](dependency-injection.md) — it has the concrete "does my code fit" checklist.

Before extracting a module, read [modules.md](modules.md) — there are explicit triggers; "feels cleaner" is not one of them.

When in doubt about a layering or abstraction decision, [architecture-principles.md](architecture-principles.md) is the source of truth: we follow Clean Architecture's spirit (stable abstractions inward, volatile details outward), not its checklist.
