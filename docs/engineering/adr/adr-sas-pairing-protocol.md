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

## Amendment — 2026-06-06 — nonce establishment and transcript binding

Option 1 above fixes *what* the SAS authenticates (the two static identity public keys) and *that* a per-handshake nonce keeps the SAS fresh. It leaves *how the nonce is established and bound* unspecified. That gap is load-bearing: the nonce exists specifically to close the offline-precomputation hole that commit-before-reveal does **not** close for static keys, and an under-specified nonce reopens exactly that hole. The original Option 1 body remains above as historical record; this amendment specifies the nonce establishment and SAS input that the implementation follows.

### The hole the nonce must close

With static, reusable identity keys and no nonce, the SAS is a fixed function of two keys the attacker can learn in advance. A MITM precomputes a colliding key pair `f(IK_A, IK_M) = f(IK_M', IK_B)` **offline** — for a ~20-bit SAS this is a birthday search over ~2¹⁰ candidates — then commits the precomputed keys honestly in the live handshake. Commit-before-reveal is satisfied and provides no protection, because the attacker was never adaptive: it ground the collision against known static keys before the exchange began. The nonce is what forces the attack back online and bounds it to a single handshake.

### Why a naive nonce is not enough

The nonce closes the hole **only if it is unpredictable to the attacker before the attacker must commit its keys.** If one side sends the nonce in the clear before key commitments are locked, the attacker grinds keys against the now-known nonce inside the handshake window — a ~20-bit target with ~10³ birthday candidates is fast enough that this is a live risk, not a hypothetical.

### Decision

The per-handshake nonce is **contributory and committed alongside the keys**, and the SAS is computed over the **full transcript**, not over the raw keys plus a nonce.

1. **Contributory nonce.** Each side generates a random share (`nonce_A`, `nonce_B`). The effective nonce is a combination of both shares. Neither side alone determines the result, so neither side — nor a MITM relaying between them — fixes or predicts it unilaterally.
2. **Nonce shares committed in the same commit phase as the keys.** A side's commitment covers both its identity public key and its nonce share. Shares are revealed only after both commitments are received. This makes the entire SAS input unpredictable to either party until the reveal, killing both offline precomputation and adaptive online collision.
3. **SAS over the full transcript.** The SAS is a truncation of a hash over everything exchanged, not over an isolated key-pair-plus-nonce:
   ```
   SAS = truncate( H( commit_A ‖ commit_B
                      ‖ IK_A ‖ IK_B
                      ‖ nonce_A ‖ nonce_B
                      ‖ role_tag_A ‖ role_tag_B ) )
   ```
   Including both full identity keys keeps B3-T (full-coverage) satisfied. Including the commitments and both nonce shares binds freshness into the SAS. Including role tags (initiator / responder) removes residual unknown-key-share / reflection ambiguity — each side's view is unambiguously bound to a direction, not just to a symmetric key set.
4. **Truncation length unchanged.** SAS length stays at the ≥ ~20-bit target from Option 1; this amendment changes the *input* to the hash, not the output length.

### Ordering (normative)

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

No reveal precedes both commitments. No party learns the peer's key or nonce share before its own commitment is sent.

### Costs accepted

- **One extra committed value per side (the nonce share).** Marginal — it rides inside the existing commitment, adding no round trips beyond what commit-before-reveal already requires.
- **The combination function for the two nonce shares is a spec item** that must be fixed and not silently changed (changing it changes every SAS). Recorded here rather than left to implementation.

### Consequences

- The nonce is a contributory value, not a single-party one; Option 1's "nonce is load-bearing" statement is operationalised in a way a MITM cannot predict or fix.
- The SAS subject is the full transcript, so any tampering with commitments, keys, nonce shares, or roles changes the displayed SAS and is caught by human comparison.
- Implementation reviewers have a normative ordering to check against, rather than inferring it from prose.

### Revisit if

- **SAS length drops below the ~20-bit target.** The online-collision bound inside one handshake scales with SAS length; a shorter SAS narrows the margin this amendment relies on.
- **The commitment hash or nonce-combination function changes.** Both are part of the SAS contract; a change is a protocol break, not an implementation detail.

### References

- [`threat-model.md` §SAS pairing model](../../security/threat-model.md#sas-pairing-model) — the standing rule this amendment refines (contributory, co-committed nonce; SAS over the full transcript).
- [`sas-pairing-pentest.md`](../../security/sas-pairing-pentest.md) — Group B cases B5-T / B5-T2 / B6-T / B7-T cover precomputation, the contributory nonce, and transcript binding.

## References

- [`threat-model.md` §SAS pairing model](../../security/threat-model.md#sas-pairing-model) — parent living doc; the standing SAS correctness rules this ADR's choice satisfies.
- [`sas-pairing-pentest.md`](../../security/sas-pairing-pentest.md) — attack tree and pentest suite; B1-T (SAS entropy/freshness) and B4-T (replay) are the per-run-entropy expectation the nonce satisfies.
- [`device-identity.md`](../device-identity.md) — the per-install static keypair that is the SAS subject and the TLS pin.
- [adr-channel-encryption.md](adr-channel-encryption.md) — the pinned-TLS channel that supplies per-session confidentiality and forward secrecy.
- [#10](https://github.com/khmelevartem/tether/issues/10), [#361](https://github.com/khmelevartem/tether/issues/361), [#140](https://github.com/khmelevartem/tether/issues/140) — adjacent issues whose contracts this decision pins down.
