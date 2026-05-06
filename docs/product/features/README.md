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

<!-- Add a row per feature. Keep sorted by area, then priority within area. -->

Note on `Status` here vs. GitHub Issues: an issue can exist (and even be in progress) before a feature doc is written. `scoped` in this table means "feature doc exists"; until then, a tracked feature shows `idea` or `in progress` and the doc column links to `_tbd_`.

| Feature | Area | Status | Doc | Issues |
|---------|------|--------|-----|--------|
| mDNS peer discovery | Discovery | done | _tbd_ | #6 (iOS/macOS) |
| Pairing — key exchange & trusted device memory | Pairing | scoped | [pairing-key-exchange.md](pairing-key-exchange.md) | #9 |
| Pairing — handshake & PIN computation | Pairing | idea | _tbd_ | #10 |
| Pairing — Android PIN confirmation UI | Pairing | idea | _tbd_ | #11 |
| Pairing — iOS PIN confirmation UI | Pairing | idea | [ios-pairing-confirmation.md](ios-pairing-confirmation.md) (stub) | _tbd_ |
| Android device list screen | UI | scoped | [android-device-list.md](android-device-list.md) | #7 |
| Android file send + progress | UI | idea | _tbd_ | #8 |
| iOS device list screen | UI | idea | [ios-device-list.md](ios-device-list.md) (stub) | _tbd_ |
| iOS file send + progress | UI | idea | [ios-send-progress.md](ios-send-progress.md) (stub) | _tbd_ |
| Desktop UI + CLI/UI entry-point split | UI / Platform | idea | [desktop-ui-and-cli-split.md](desktop-ui-and-cli-split.md) (stub) | _tbd_ |
| Streaming file server | Transfer | in progress | [streaming-file-server.md](streaming-file-server.md) (stub) | #25 |
| Multi-file transfer | Transfer | idea | [multi-file-transfer.md](multi-file-transfer.md) (stub) | _tbd_ |
| Permissions strategy | Cross-platform / UX | idea | [permissions-strategy.md](permissions-strategy.md) (stub) | _tbd_ |
| Device name bootstrapping | Onboarding | idea | [device-name-bootstrapping.md](device-name-bootstrapping.md) (stub) | _tbd_ |

---

*To add a feature: copy `_template.md`, fill it in, add a row here.*
