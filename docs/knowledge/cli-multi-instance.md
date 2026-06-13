# CLI multi-instance on the same host

## Symptom

Two CLI processes on the same host don't see each other in `[peers]`. Desktop↔Desktop smoke is blocked when no second physical device is available.

## Cause

mDNS discovery filters peers whose `fp` TXT record matches the local fingerprint (self-suppression). The fingerprint is derived from the device's EC key pair, so two processes that load the same key pair — from a shared config directory (Linux / macOS: `~/.config/tether`; Windows: `%APPDATA%\Tether`) — derive the same fingerprint, and each silently discards the other.

## Solution

By default the CLI uses a per-process ephemeral identity: each invocation generates a fresh EC key pair in a temporary directory and derives its fingerprint from that key. Distinct processes therefore have distinct fingerprints and see each other normally.

Passing `--config-dir <dir>` opts into a persistent identity: the name and key pair are stored in that directory and survive restart, and the fingerprint is derived from the persisted key. Two CLI instances launched with different `--config-dir` paths each carry a distinct persistent identity and continue to see each other normally.

Production app installations are not affected: there is no scenario where two app installations run simultaneously under the same OS user account, so self-suppression by fingerprint remains load-bearing.

## Consequences of ephemeral identity

Two behaviours follow from the per-process, non-persisted identity when several CLIs run on one host:

- **Same-base-name instances stay distinguishable.** When instances share the default device name, mDNS infrastructure assigns canonical names (`… (2)`, `(3)`, …). A peer's name recorded on first discovery (the mDNS-canonical form) is preserved when that peer later announces via `/hello` under its raw configured name — so same-base-name instances stay distinguishable. `[peers]` / `list` show the distinct canonical names across all platforms.
- **A restarted CLI is a new peer by default.** Without `--config-dir`, restarting an instance generates a fresh key pair and thus a fresh fingerprint, so the sender treats it as a different `PeerIdentity` — a `retry` queued against the pre-restart instance does not resume. Passing `--config-dir <dir>` opts into a persisted identity (name + key pair stored in that directory), so a restarted instance keeps the same `PeerIdentity` and a queued `retry` resumes.

## See also

- [docs/engineering/discovery.md §Identity and self-suppression](../engineering/discovery.md#identity-and-self-suppression)
- [#346](https://github.com/khmelevartem/tether/issues/346)
