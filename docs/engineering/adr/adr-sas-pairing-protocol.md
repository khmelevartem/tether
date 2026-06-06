# SAS pairing protocol — authenticate static identity keys, no app-level ephemeral key exchange

**Status:** Accepted — 2026-06-06
**Issue:** [#10](https://github.com/khmelevartem/tether/issues/10)

## Context

Pairing is the trust gate: until two devices complete the first-encounter SAS comparison, no file moves. The [threat model](../../security/threat-model.md#sas-pairing-model) fixes the correctness conditions the SAS scheme must satisfy (both keys covered, length, commit-before-reveal, session binding, defence against blind confirmation). What it did not pin down was *which key* the SAS authenticates and *whether the pairing protocol runs its own key exchange* — a question that became load-bearing once two adjacent decisions converged on the same material:

- The pinned-TLS channel ([#140](https://github.com/khmelevartem/tether/issues/140), [channel-encryption ADR](adr-channel-encryption.md)) pins the TLS certificate to a device's persisted public key and proves possession of the matching private key on every connection. That key is the per-install root of trust from [`device-identity.md`](../device-identity.md).
- Mutual confirmation before trust is committed ([#361](https://github.com/khmelevartem/tether/issues/361)) requires both sides to store the peer's key only after a bilateral SAS match.

A static identity keypair is therefore already mandatory and already pinned regardless of pairing's internal shape. The open question was whether pairing should additionally run an application-level ephemeral key exchange (the textbook SAS-over-ephemeral-ECDH construction), or authenticate the static identity keys directly.

## Decision drivers

| Driver | Why it matters for Tether |
|---|---|
| "Pair once, never re-pair" | A device must recognise a paired peer across sessions and reboots — recognition needs a persisted identity, not a discarded session key. |
| No redundant crypto | Per-session confidentiality and forward secrecy are a property the channel already owns; duplicating them inside pairing adds attack surface for no gain. |
| Minimal pre-trust protocol | The pairing endpoint is open before trust is established; every extra round and every extra piece of session-key state is exposed surface that must be hardened. |
| SAS freshness | Each pairing attempt must produce an unpredictable SAS so a precomputed-collision attack cannot work against static, reusable identity keys. |

## Considered options

### Option 1 — Authenticate the static identity keys; SAS over both keys plus a per-handshake nonce (chosen)

Pairing exchanges the two devices' static long-term identity public keys under commit-before-reveal. The SAS is computed over **both full identity public keys plus a per-handshake nonce/challenge**. On bilateral confirmation each side commits the peer's identity public key to its trust store. No session key is derived during pairing. Confidentiality, forward secrecy, and per-connection proof-of-private-key for every later session come from the pinned TLS channel, whose ECDHE cipher suites give per-session forward secrecy. The nonce keeps the SAS unpredictable on each attempt even though the identity keys are static and reused.

Closes: persistent recognition, MITM-during-pairing (via the threat model's five conditions), SAS precomputation (via the nonce). Costs: the pairing exchange itself has no forward secrecy — accepted, because it carries only public keys, never a secret.

### Option 2 — App-level ephemeral ECDH inside the pairing protocol

Pairing runs its own ephemeral Diffie-Hellman, derives a session key, and computes the SAS over the ephemeral public keys (the classic ZRTP-style construction). Forward-secret at the pairing layer. Closes nothing Option 1 leaves open: the ephemeral key is discarded after the session, so persistent recognition still requires a static identity key alongside it, and per-session forward secrecy is already delivered by the channel. Costs: extra protocol rounds, session-key state in the pre-trust path, and a second crypto surface to maintain — all for no marginal security gain.

### Option 3 — Authenticate static identity keys with no nonce in the SAS

Option 1 without the per-handshake nonce: the SAS is a pure function of the two static public keys. Simpler, but because the keys are stable and reused, the SAS for any given device pair is fixed and precomputable, and a replayed exchange produces an identical SAS. Fails the threat model's replay/freshness expectation (pentest B1-T / B4-T). Rejected.

## Decision

**Option 1.** Pairing authenticates each device's static long-term identity keypair. The two identity public keys are exchanged under commit-before-reveal; the SAS is derived from both full identity public keys plus a per-handshake nonce; trust is committed only after bilateral confirmation. Pairing derives no session key and provides no forward secrecy of its own — it carries only public keys. Per-session confidentiality, forward secrecy, and proof of private-key possession are delivered by the pinned TLS channel ([#140](https://github.com/khmelevartem/tether/issues/140)), not by a pairing-time key exchange.

## Costs accepted

- **No forward secrecy in the pairing exchange.** A future compromise of an identity private key lets an attacker who recorded a past pairing exchange recompute that exchange. This is harmless: the exchange transmits only public keys; there is no pairing-layer secret whose past confidentiality could be lost. Session confidentiality lives one layer down, in the channel's ephemeral ECDHE.
- **Static, reusable SAS input.** The identity keys are stable across pairings, so SAS freshness rests entirely on the per-handshake nonce rather than on fresh key material. The nonce is therefore load-bearing, not decorative.

## Consequences

- The crypto material pairing produces is exactly the pinset the channel consumes — pairing and channel encryption share one keypair with no adapter layer.
- The pre-trust pairing protocol stays minimal: key commitments, key reveals, a nonce, and a confirmation — no session-key negotiation state.
- [#361](https://github.com/khmelevartem/tether/issues/361) (commit trust only after mutual confirmation) and [#140](https://github.com/khmelevartem/tether/issues/140) (pinned TLS) both build on the identity key this decision fixes as the SAS subject.

## Revisit if

- **The channel stops providing per-session forward secrecy** (e.g. a cipher-suite change away from ECDHE). The "no forward secrecy in pairing" cost was accepted on the basis that the channel supplies it; losing that reopens whether pairing must.
- **Pairing needs to transmit a secret** (not just public keys) in a future protocol revision. The forward-secrecy cost was accepted because the exchange is secret-free; that premise would no longer hold.
- **Identity keys gain rotation** such that a single pairing must establish trust across multiple successive keys. The static-key assumption behind the nonce-for-freshness trade-off would need re-examination.

## References

- [`threat-model.md` §SAS pairing model](../../security/threat-model.md#sas-pairing-model) — parent living doc; the standing SAS correctness rules this ADR's choice satisfies.
- [`sas-pairing-pentest.md`](../../security/sas-pairing-pentest.md) — attack tree and pentest suite; B1-T (SAS entropy/freshness) and B4-T (replay) are the per-run-entropy expectation the nonce satisfies.
- [`device-identity.md`](../device-identity.md) — the per-install static keypair that is the SAS subject and the TLS pin.
- [adr-channel-encryption.md](adr-channel-encryption.md) — the pinned-TLS channel that supplies per-session confidentiality and forward secrecy.
- [#10](https://github.com/khmelevartem/tether/issues/10), [#361](https://github.com/khmelevartem/tether/issues/361), [#140](https://github.com/khmelevartem/tether/issues/140) — adjacent issues whose contracts this decision pins down.
