# Multi-file transfer

**Area:** Transfer
**Status:** `idea`
**GitHub Issues:** _tbd_

---

> Stub. Captures the gap. Flesh out before the feature enters a sprint.

## Why

[roadmap.md](../roadmap.md) calls for multi-file transfer in MVP: "multi-select picker, sequential per-file transfer over a single session, aggregate progress, cancel". Without it, sending a folder of 30 photos becomes 30 manual taps — exactly the friction Tether exists to remove. Single-file send is a stepping stone, not the destination.

## What it does (sketch)

- The user picks several files in one go (system picker handles this).
- Tether sends them one after another to the chosen device, in a single logical session.
- Progress is shown both per-file and as an aggregate (e.g. "file 7 of 30, 412 MB of 1.2 GB").
- Cancelling cancels the whole batch; partial files do not linger.
- The receiver knows when a batch starts and ends, not just individual files.

## Open product questions

- If one file in the batch fails, do the rest still go? Probably yes (skip + report), but worth confirming.
- Is the order preserved (alphabetical / picker order)?
- Folder send (recursive) — in or out for MVP? Roadmap implies multi-file picker, not folder; flag the boundary.
