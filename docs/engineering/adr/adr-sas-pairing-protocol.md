# SAS pairing protocol — authenticate static identity keys, no app-level ephemeral key exchange

**Status:** Accepted — 2026-06-06
**Issue:** [#10](https://github.com/khmelevartem/tether/issues/10)

## Context

Pairing is the trust gate: until two devices complete the first-encounter SAS comparison, no file moves. The [threat model](../../security/threat-model.md#sas-pairing-model) fixes the correctness conditions the SAS scheme must satisfy (both keys covered, length, commit-before-reveal, session binding, defence against blind confirmation). What it did not pin down was *which key* the SAS authenticates and *whether the pairing protocol runs its own key exchange* — a question that became load-bearing once two adjacent decisions converged on the same material:

- The pinned-TLS channel ([#140](https://github.com/khmelevartem/tether/issues/140), [channel-encryption ADR](adr-channel-encryption.md)) pins the TLS certificate to a device's persisted public key and proves possession of the matching private key on every connection. That key is the per-install root of trust from [`device-identity.md`](../device-identity.md).
- The requirement that both sides store the peer's key only after a bilateral SAS match ([#10](https://github.com/khmelevartem/tether/issues/10)) makes mutual confirmation the gate before trust is committed.

A static identity keypair is therefore already mandatory and already pinned regardless of pairing's internal shape. The open question was whether pairing should additionally run an application-level ephemeral key exchange (the textbook SAS-over-ephemeral-ECDH construction), or authenticate the static identity keys directly.

Static, reusable identity keys raise a second question the threat model's commit-before-reveal condition does **not** answer on its own: an attacker who knows both keys in advance can grind a colliding key pair offline and then commit it honestly in the live handshake. Closing that hole requires a per-handshake nonce, and *how the nonce is established and bound* is part of this decision, not an implementation detail — an under-specified nonce reopens exactly that hole.

## Decision drivers

| Driver | Why it matters for Tether |
|---|---|
| "Pair once, never re-pair" | A device must recognise a paired peer across sessions and reboots — recognition needs a persisted identity, not a discarded session key. |
| No redundant crypto | Per-session confidentiality and forward secrecy are a property the channel already owns; duplicating them inside pairing adds attack surface for no gain. |
| Minimal pre-trust protocol | The pairing endpoint is open before trust is established; every extra round and every extra piece of session-key state is exposed surface that must be hardened. |
| SAS freshness | Each pairing attempt must produce an unpredictable SAS so a precomputed-collision attack cannot work against static, reusable identity keys. |

## Considered options

### Option 1 — Authenticate the static identity keys; SAS over the full transcript bound by a contributory, co-committed nonce (chosen)

Pairing exchanges the two devices' static long-term identity public keys under commit-before-reveal. Each side also generates a random nonce share and commits it in the **same commit phase** as its key; the effective nonce is a combination of both shares, so neither side — nor a MITM relaying between them — fixes or predicts it unilaterally. The SAS is a truncation of a hash over the **full transcript** — both commitments, both full identity public keys, both nonce shares, and a role tag per side — not over the raw keys plus a nonce. On bilateral confirmation each side commits the peer's identity public key to its trust store. No session key is derived during pairing. Confidentiality, forward secrecy, and per-connection proof-of-private-key for every later session come from the pinned TLS channel, whose ECDHE cipher suites give per-session forward secrecy.

The contributory, co-committed nonce is what makes the whole SAS input unpredictable until the reveal. With static keys and no nonce, the SAS is a fixed function of two keys the attacker can learn in advance: a MITM precomputes a colliding key pair `f(IK_A, IK_M) = f(IK_M', IK_B)` **offline** — for a ~20-bit SAS this is a birthday search over ~2¹⁰ candidates — then commits the precomputed keys honestly in the live handshake, satisfying commit-before-reveal without ever being adaptive. A nonce closes this hole **only if it is unpredictable to the attacker before the attacker must commit its keys**: a single-party or in-the-clear-early nonce just lets the attacker grind keys against the known nonce inside the handshake window, which at ~20 bits (~10³ birthday candidates) is fast enough to be a live risk. Co-committing both shares with the keys removes both the offline and the adaptive-online path. Binding the role tags (initiator / responder) into the transcript removes residual unknown-key-share / reflection ambiguity — each side's view is bound to a direction, not just to a symmetric key set.

Closes: persistent recognition, MITM-during-pairing (via the threat model's correctness conditions), SAS precomputation and adaptive online collision (via the contributory, co-committed nonce), role / identity misbinding (via transcript binding). Costs: the pairing exchange itself has no forward secrecy — accepted, because it carries only public keys, never a secret.

### Option 2 — App-level ephemeral ECDH inside the pairing protocol

Pairing runs its own ephemeral Diffie-Hellman, derives a session key, and computes the SAS over the ephemeral public keys (the classic ZRTP-style construction). Forward-secret at the pairing layer. Closes nothing Option 1 leaves open: the ephemeral key is discarded after the session, so persistent recognition still requires a static identity key alongside it, and per-session forward secrecy is already delivered by the channel. Costs: extra protocol rounds, session-key state in the pre-trust path, and a second crypto surface to maintain — all for no marginal security gain.

### Option 3 — Authenticate static identity keys with no nonce (or a naive nonce) in the SAS

Option 1 without a contributory, co-committed nonce: the SAS is a pure function of the two static public keys, or of the keys plus a single-party / early-revealed nonce. Simpler, but because the keys are stable and reused, the SAS for any given device pair is precomputable: with no nonce a MITM grinds a collision entirely offline, and with a naive nonce it grinds against the known nonce inside the handshake window. Fails the threat model's replay/freshness expectation and the offline-precomputation node (pentest B1-T / B4-T / B5-T). Rejected.

## Decision

**Option 1.** Pairing authenticates each device's static long-term identity keypair. The two identity public keys and a per-side nonce share are committed together under commit-before-reveal and revealed only after both commitments are received; the SAS is the truncation of a hash over the full transcript — both commitments, both full identity public keys, both nonce shares, and both role tags. The effective nonce is a combination of the two shares, so it is contributory and unpredictable to either party (and to a relaying MITM) until reveal. Trust is committed only after bilateral confirmation. Pairing derives no session key and provides no forward secrecy of its own — it carries only public keys. Per-session confidentiality, forward secrecy, and proof of private-key possession are delivered by the pinned TLS channel ([#140](https://github.com/khmelevartem/tether/issues/140)), not by a pairing-time key exchange.

The SAS construction:

```
SAS = truncate( H( commit_A ‖ commit_B
                   ‖ IK_A ‖ IK_B
                   ‖ nonce_A ‖ nonce_B
                   ‖ role_tag_A ‖ role_tag_B ) )
```

The SAS is **6 decimal digits (~20 bits)**.

## Protocol ordering (normative)

```
A → B : commit_A = H(IK_A ‖ nonce_A ‖ role_tag_A)
B → A : commit_B = H(IK_B ‖ nonce_B ‖ role_tag_B)
A → B : reveal  IK_A, nonce_A
B → A : reveal  IK_B, nonce_B
both  : verify peer reveal against peer commit; abort on mismatch
both  : compute SAS over the full transcript; display
human : compare; confirm only on match
both  : on bilateral confirm, commit peer IK to trust store
```

No reveal precedes both commitments. No party learns the peer's key or nonce share before its own commitment is sent. Implementation reviewers check against this ordering rather than inferring it from prose.

## Costs accepted

- **No forward secrecy in the pairing exchange.** A future compromise of an identity private key lets an attacker who recorded a past pairing exchange recompute that exchange. This is harmless: the exchange transmits only public keys; there is no pairing-layer secret whose past confidentiality could be lost. Session confidentiality lives one layer down, in the channel's ephemeral ECDHE.
- **Static, reusable SAS input.** The identity keys are stable across pairings, so SAS freshness rests entirely on the per-handshake nonce rather than on fresh key material. The contributory, co-committed nonce is therefore load-bearing, not decorative.
- **One extra committed value per side (the nonce share).** Marginal — it rides inside the existing commitment, adding no round trips beyond what commit-before-reveal already requires.
- **The combination function for the two nonce shares is a spec item** that must be fixed and not silently changed — changing it changes every SAS.

## Consequences

- The crypto material pairing produces is exactly the pinset the channel consumes — pairing and channel encryption share one keypair with no adapter layer.
- The pre-trust pairing protocol stays minimal: key-plus-nonce commitments, reveals, and a confirmation — no session-key negotiation state.
- The SAS subject is the full transcript, so any tampering with commitments, keys, nonce shares, or roles changes the displayed SAS and is caught by human comparison.
- [#10](https://github.com/khmelevartem/tether/issues/10) (commit trust only after mutual confirmation) and [#140](https://github.com/khmelevartem/tether/issues/140) (pinned TLS) both build on the identity key this decision fixes as the SAS subject.

## Revisit if

- **The channel stops providing per-session forward secrecy** (e.g. a cipher-suite change away from ECDHE). The "no forward secrecy in pairing" cost was accepted on the basis that the channel supplies it; losing that reopens whether pairing must.
- **Pairing needs to transmit a secret** (not just public keys) in a future protocol revision. The forward-secrecy cost was accepted because the exchange is secret-free; that premise would no longer hold.
- **Identity keys gain rotation** such that a single pairing must establish trust across multiple successive keys. The static-key assumption behind the nonce-for-freshness trade-off would need re-examination.
- **SAS length drops below the 6-digit (~20-bit) target.** The online-collision bound inside one handshake scales with SAS length; a shorter SAS narrows the margin the contributory nonce relies on.
- **The commitment hash or the nonce-combination function changes.** Both are part of the SAS contract; a change is a protocol break, not an implementation detail.

## References

- [`threat-model.md` §SAS pairing model](../../security/threat-model.md#sas-pairing-model) — parent living doc; the standing SAS correctness rules this ADR's choice satisfies (contributory, co-committed nonce; SAS over the full transcript).
- [`sas-pairing-pentest.md`](../../security/sas-pairing-pentest.md) — attack tree and pentest suite; B1-T (SAS entropy/freshness), B4-T (replay), and B5-T / B5-T2 / B6-T / B7-T (precomputation, contributory nonce, transcript binding) are the per-run-entropy and freshness expectations the nonce satisfies.
- [`device-identity.md`](../device-identity.md) — the per-install static keypair that is the SAS subject and the TLS pin.
- [adr-channel-encryption.md](adr-channel-encryption.md) — the pinned-TLS channel that supplies per-session confidentiality and forward secrecy.
- [#10](https://github.com/khmelevartem/tether/issues/10), [#140](https://github.com/khmelevartem/tether/issues/140) — adjacent issues whose contracts this decision pins down.
