# Desktop UI + separating CLI from UI entry points

**Area:** UI / Platform
**Status:** `idea`
**GitHub Issues:** _tbd_

---

> Stub. Captures the gap. Flesh out before the feature enters a sprint.

## Why

The roadmap requires "Device list + transfer screen on every platform" for MVP. Today the desktop target is **CLI only**: `mainClass = MainKt` runs the Clikt-based debug runner; the Compose `App.kt` is not wired into the desktop run task. Two consequences:

1. Desktop has no UI yet, even though the Compose Multiplatform setup is already in place.
2. CLI and UI share one entry point — adding UI without thought either replaces the CLI (loses a debug tool) or hides behind a flag (awkward).

This feature carves out a desktop UI and separates the CLI from it cleanly, so neither blocks the other.

## What it does (sketch)

- Desktop user double-clicks the Tether app and gets the same device-list / transfer experience as on phones.
- Developers retain the CLI for debugging — same binary or a sibling target, with two clearly named launch tasks (e.g. `run` for CLI, `runDesktop` for UI; or split into a separate `:cli` Gradle module if the entanglement gets worse).
- macOS distribution path (`.dmg`) ships the UI app, not the CLI runner.

## Open product questions

- Is the CLI a developer-only tool that ships separately, or a supported user-visible build for power users on Linux/Windows?
- Two run tasks on one target vs. a dedicated `:cli` Gradle module — a build-architecture decision, defer until the UI side is real.
