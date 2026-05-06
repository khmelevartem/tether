# Feature List

Overview of all features. Click a feature to see its full spec.

## Status legend

| Status | Meaning |
|--------|---------|
| `idea` | Captured, not scoped yet |
| `scoped` | Feature doc written, not started |
| `in progress` | Active development |
| `done` | Shipped |
| `on hold` | Paused, reason noted in doc |

## Features

<!-- One row per product feature. Features are cross-platform by default —
     a separate row per platform is wrong. Per-platform implementation
     issues all go into the same row's "Issues" column. -->

Note on `Status` here vs. GitHub Issues: an issue can exist (and even be in progress) before a feature doc is written. `scoped` in this table means "feature doc exists"; until then, a tracked feature shows `idea` or `in progress` and the doc column links to `_tbd_`.

| Feature | Area | Status | Doc | Issues |
|---------|------|--------|-----|--------|
| mDNS peer discovery | Discovery | done | _tbd_ | #6 (iOS/macOS) |
| Pairing — key exchange & trusted device memory | Pairing | scoped | [pairing-key-exchange.md](pairing-key-exchange.md) | #9 |
| Pairing — handshake & PIN computation | Pairing | idea | _tbd_ | #10 |
| Pairing — PIN confirmation UI | Pairing / UI | idea | [pairing-pin-confirmation.md](pairing-pin-confirmation.md) (stub) | #11 (Android); iOS, Desktop _tbd_ |
| Device list screen | UI | scoped | [device-list.md](device-list.md) | #7 (Android); iOS, Desktop _tbd_ |
| File transfer | Transfer / UI | idea | [file-transfer.md](file-transfer.md) (stub) | #8 (Android send UI); iOS, Desktop, receive-side UI _tbd_ |
| Multi-file transfer | Transfer | idea | [multi-file-transfer.md](multi-file-transfer.md) (stub) | _tbd_ |
| Permissions strategy | Cross-platform / UX | idea | [permissions-strategy.md](permissions-strategy.md) (stub) | _tbd_ |
| Device name bootstrapping | Onboarding | idea | [device-name-bootstrapping.md](device-name-bootstrapping.md) (stub) | _tbd_ |

---

*To add a feature: copy `_template.md`, fill it in, add a row here.*
*Per-platform splits don't get separate rows — list all platform issues in the existing feature's Issues column.*
