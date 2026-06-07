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

## What counts as one feature

**One feature = one user-visible product capability.** A row in this table is a thing the user experiences as one. Multiple GitHub issues can implement the same feature — they go into the same row's "Issues" column, not into separate rows.

**Don't split a feature along these axes:**

- **Platform.** "Android device list" and "iOS device list" are not two features — they are one feature shipping on multiple platforms. Per-platform user-visible quirks live in the spec's `Platform notes` section.
- **Implementation layer.** "File send UI" and "streaming server" are not two features — they are two halves of one transfer experience. The user does not see the seam.
- **Implementation milestone.** "Pairing — key exchange", "PIN computation", "PIN UI" are not three features — they are three milestones of one pairing experience. None of them ships user value alone.
- **Quantity / scale.** "Single-file send" and "multi-file send" are not two features — they are N≥1 of the same surface. Behaviour-under-conditions (parallel vs sequential, batch failure semantics) lives in `Open product questions`.

**Quick test:** can the milestone / piece you are tempted to extract ship user-visible value on its own, *without* the rest? If no, it is a milestone, not a feature.

If a piece does pass that test (e.g. *fan-out — one file to many peers*), it is a separate feature with its own row.

## Features

Note on `Status` here vs. GitHub Issues: an issue can exist (and even be in progress) before a feature doc is written. `scoped` in this table means "feature doc exists"; until then, a tracked feature shows `idea` or `in progress` and the doc column links to `_tbd_`.

| Feature | Area | Status | Doc | Issues |
|---------|------|--------|-----|--------|
| mDNS peer discovery | Discovery | done | _tbd_ | #6 (iOS/macOS) |
| Hotspot transfer | Discovery / Transfer | scoped | [hotspot-transfer/spec.md](hotspot-transfer/spec.md) | [#170](https://github.com/khmelevartem/tether/issues/170) (design); implementation _tbd_ |
| Pairing | Pairing / Security | scoped | [pairing/spec.md](pairing/spec.md) | #9 (key exchange), #10 (PIN + CLI), #11 (Android UI); iOS, Desktop UI _tbd_ |
| Device list screen | UI | in progress | [device-list/spec.md](device-list/spec.md) | #7 — Android + iOS done; Desktop _tbd_ |
| File transfer | Transfer / UI | scoped | [file-transfer/spec.md](file-transfer/spec.md) | #8 (Android send UI), #81 (iOS FileServer receive); iOS UI, Desktop send UI, receive-side UI _tbd_ |
| Device name bootstrapping | Onboarding | done | [device-name-bootstrapping/spec.md](device-name-bootstrapping/spec.md) | #147 (backend), #148 (UI) |

### System integration

Small, separate features about how Tether reacts to the OS state around it. Each is its own concern; the [`system/`](system/) subfolder groups them. Add a new spec here when a new system surface earns its own behaviour.

| Feature | Status | Doc | Issues |
|---------|--------|-----|--------|
| Permissions | scoped | [system/permissions/spec.md](system/permissions/spec.md) | _tbd_ |
| Wi-Fi availability | scoped | [system/wifi-availability/spec.md](system/wifi-availability/spec.md) | _tbd_ |

---

*To add a feature: copy `_template.md`, fill it in, add a row here. Before writing, read «What counts as one feature» above.*
