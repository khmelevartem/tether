# Streaming file server

**Area:** Transfer
**Status:** `idea`
**GitHub Issues:** _tbd_

---

> Stub. Captures the gap. Flesh out before the feature enters a sprint.

## Why

Tether's promise is "original bytes, untouched" and arbitrary file sizes. Naive in-memory buffering breaks both — large files OOM the receiver, and accidental conversion in the pipeline corrupts originals. The receive-side server must stream bytes from socket to disk without buffering the whole file, and must surface enough state for the UI to show progress and let the user cancel.

## What it does (sketch)

- Accepts files of any size without buffering whole bodies in memory.
- Lets the sender cancel; the partial file on the receiver does not linger silently.
- Exposes per-transfer state — bytes received, total expected, completion / failure — for the UI to consume.
- Survives the transient network glitches that happen on home Wi-Fi within the duration of a single transfer (resume across sessions is Post-MVP, see roadmap).

## Open product questions

- What does the receiver see when a transfer fails midway? Discarded silently, kept as partial, surfaced as "incomplete"?
- Where is the file written to on each platform, and what is the user's first encounter with that location?
