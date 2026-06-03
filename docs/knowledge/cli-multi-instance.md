# CLI multi-instance on the same host

## Symptom

Two CLI processes on the same host don't see each other in `[peers]`. Desktop↔Desktop smoke is blocked when no second physical device is available.

## Cause

Both processes share the same DataStore preferences file (Linux / macOS: `~/.config/tether/preferences.preferences_pb`; Windows: `%APPDATA%\Tether\preferences.preferences_pb`). The shared file yields the same fingerprint for both processes. mDNS discovery filters peers whose `fp` TXT record matches the local fingerprint as self-suppression — so each process silently discards the other.

## Solution

The CLI uses per-process ephemeral fingerprints (never persisted to disk), parallel to how it handles ephemeral device names. Each CLI invocation generates a fresh random fingerprint; distinct processes therefore have distinct fingerprints and see each other normally.

Production app installations are not affected: there is no scenario where two app installations run simultaneously under the same OS user account, so self-suppression by fingerprint remains load-bearing.

## Consequences of ephemeral identity

Two behaviours follow from the per-process, non-persisted identity when several CLIs run on one host:

- **Same-base-name instances stay distinguishable.** When instances share the default device name, mDNS infrastructure assigns each a canonical name (`… (2)`, `… (3)`, …). Each instance reports its own canonical name in `/hello`, so the fingerprint-keyed upsert preserves it — `[peers]` / `list` show distinct names. (see [discovery.md](../engineering/discovery.md))
- **A restarted CLI is a new peer.** Restarting an instance generates a fresh fingerprint, so the sender treats it as a different `PeerIdentity` — a `retry` queued against the pre-restart instance does not resume. Stable-across-restart identity (opt-in, like the UI targets) is tracked in [#367](https://github.com/khmelevartem/tether/issues/367).

## See also

- [docs/engineering/discovery.md §Identity and self-suppression](../engineering/discovery.md#identity-and-self-suppression)
- [#346](https://github.com/khmelevartem/tether/issues/346)
