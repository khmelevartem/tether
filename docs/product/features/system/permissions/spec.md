# Permissions

**Area:** System integration
**Status:** `scoped`
**GitHub Issues:** _tbd_

---

## Why

Tether's core capabilities — discovering peers on the local network, picking and saving files, surfacing transfer notifications — each depend on the OS granting access to a resource. Every platform enforces those grants differently. Without a single cross-platform strategy each feature issue makes its own call on when to ask and what to show when the user says no — producing an inconsistent experience and constant rework.

[vision.md](../../../vision.md) commits to "two taps to send." A poorly timed or unexplained OS prompt breaks that feeling before the file picker even opens. This spec sets the rule once for all four targets — and for Desktop JVM running on each of its three host operating systems.

## What it does

For every OS-mediated resource Tether touches, this spec answers:

- **Runtime.** Does the OS show a prompt? If yes — when, what does Tether do before and after, what does the user see if they say no.
- **Compile-time.** Which manifest entries, Info.plist usage strings, entitlements, and FGS subtypes are required to make the runtime story work. Listed per platform — these are configuration, not user-facing behaviour.

Runtime is the central concern. Where a platform has no runtime prompt for a given resource, that is stated explicitly so a reader does not assume there must be one.

## Cross-platform invariants

1. **Lazy.** Tether asks the OS only for what the current user action requires, immediately before that action — never up front, never speculatively. The one structural exception is `POST_NOTIFICATIONS` on Android, where the dependent action (starting the foreground service) coincides with app launch.
2. **Tether-owned rationale before the OS prompt.** Wherever an OS prompt exists, a brief Tether screen precedes it: one sentence naming the action, one sentence naming what the OS will ask, one button to continue. The same screen precedes any re-prompt the OS still permits. Where the OS does not prompt for that resource on the current platform/version, the rationale screen is not shown either — no redundant screens.
3. **Degraded, not broken.** A denied permission disables only the dependent surface. Tether does not crash, does not hide unrelated surfaces.
4. **Always a path back.** Where the OS provides a per-app permissions page, the empty state after denial offers an "Open Settings" button deep-linking to that page directly. Where no per-app page exists (Desktop firewalls), the empty state names the OS firewall and tells the user what to allow.
5. **Nothing requested that is not used.** A permission Tether will not exercise does not appear in any manifest, Info.plist, or entitlements file.

## Summary

Runtime prompts only. Compile-time declarations are listed per platform below.

| Platform | Resource | Runtime prompt | When | If denied |
|---|---|---|---|---|
| Android | Local network discovery | none | — | — |
| Android | Notifications + FGS | `POST_NOTIFICATIONS` | At app launch (two-strike rule) | Service runs, no FGS notification. After 2nd denial → "Open Settings" affordance |
| Android | File send / save | none | — | — |
| iOS | Local network discovery | system Local Network | First mDNS browse on device-list entry (one-strike) | Empty state with "Open Settings"; no re-prompt |
| iOS | Notifications | `UNUserNotificationCenter` | First completion notification | Transfer still completes; in-app receipt only |
| iOS | File send / save | none | — | — |
| macOS (native, Sequoia 15+) | Local network discovery | system Local Network | First mDNS browse on device-list entry | Empty state with "Open Settings" |
| macOS (native, Sonoma 14 and earlier) | Local network discovery | none | — | — |
| macOS (native) | Notifications | `UNUserNotificationCenter` | First completion notification | In-app receipt only |
| macOS (native) | Inbound network | Application Firewall | First server bind | Outgoing works; incoming blocked until allowed in Network → Firewall |
| macOS (native) | File send / save | none (entitlement at compile-time) | — | — |
| Desktop JVM (Windows admin) | Inbound network | Windows Defender Firewall | First server bind | Allowing → inbound works; help screen on bind failure |
| Desktop JVM (Windows standard user, Linux without per-app firewall) | Inbound network | none (silent block by default policy) | — | `bind()` succeeds; peers can't connect; empty device-list state names firewall as a possible cause |
| Desktop JVM (macOS, Sequoia 15+) | Local network discovery | system Local Network | First mDNS browse | Empty state, help link to Privacy & Security → Local Network |
| Desktop JVM (macOS) | Inbound network | Application Firewall | First server bind | Same as native macOS |
| Desktop JVM (macOS) | Notifications | `UNUserNotificationCenter` | First completion notification | In-app receipt only |
| Desktop JVM (macOS) | File save | Downloads TCC prompt (10.15+) | First write to `~/Downloads/Tether/` | Receive blocked; help screen pointing at Privacy & Security → Files and Folders |
| Desktop JVM (any host) | File send | none | — | — |

## Android

**Required.**

- **`CHANGE_WIFI_MULTICAST_STATE`** (manifest). Required by some OEM `NsdManager` builds for mDNS multicast.
- **`INTERNET`** (manifest). Implicit; needed for outbound and inbound TCP.
- **`FOREGROUND_SERVICE`** + **`FOREGROUND_SERVICE_DATA_SYNC`** (manifest). The latter is required as the service subtype because Tether targets API 34+. Both are manifest-only, no runtime grant.
- **`POST_NOTIFICATIONS`** (manifest + runtime, API 33+). Tether asks at app launch alongside the foreground-service start — the structural exception to invariant #1, because the service that needs the notification also starts at launch. The Tether rationale screen is shown before the OS prompt. Two-strike rule: first denial leaves it re-requestable on the next `launch()`; second denial flips it to `USER_FIXED` and the OS stops showing the dialog. After the second denial Tether switches to an in-app "Open Settings" affordance — a banner deep-linking to the app's permission page. The transfer service still runs; only the FGS notification is missing (degraded, not broken).

**Not required.**

- Local Network discovery — no runtime grant on currently supported API levels (`NsdManager` works under `CHANGE_WIFI_MULTICAST_STATE`).
- File send — system Photo Picker, system file/folder picker (SAF), and OS share sheet all return scoped URIs without `READ_MEDIA_*` or `MANAGE_EXTERNAL_STORAGE`.
- File save — `MediaStore.Downloads` writes to public `Downloads/Tether/` without `WRITE_EXTERNAL_STORAGE` (legacy, replaced by scoped storage on API 29+; Tether targets API 34+).

## iOS

**Required.**

- **`NSLocalNetworkUsageDescription`** in Info.plist. Verbatim text shown in the OS prompt: "Tether uses the local network to discover and connect to nearby devices for file transfer."
- **`NSBonjourServices`** in Info.plist listing Tether's service type. The deployed Info.plist uses `_tether._tcp.`; this string must stay in sync between Info.plist, Bonjour browse/register calls, and the mDNS service name advertised by Android and Desktop. Apple's published examples use the form without a trailing dot — both forms are accepted in practice; consistency matters more than which form.
- **Local Network runtime prompt.** Fires on the first mDNS browse — tied to entering the device list, not to app launch. Tether's rationale screen is shown immediately before. Single denial is functionally permanent: the OS does not re-show the dialog. Tether moves directly to the "Open Settings" empty state on the device list; the button deep-links to the app's permission page.
- **`UNUserNotificationCenter` authorisation** (runtime). Triggered on the first attempt to schedule a transfer-completion notification. Tether's rationale screen precedes the OS prompt. On denial, transfers still complete; the in-app "Received" list is the only completion surface.

**Not required.**

- File send — `PHPickerViewController` is privacy-preserving since iOS 14, no `NSPhotoLibraryUsageDescription`. `UIDocumentPickerViewController` and OS share sheet add no runtime prompt.
- File save — Tether writes only to its own app container (`On My iPhone → Tether/`); no Photos write (`NSPhotoLibraryAddUsageDescription`), no Files access outside the container.
- Inbound and outbound TCP within the local network are gated by the Local Network grant alone.

## macOS (native Compose target)

The native macOS build runs sandboxed (App Store distribution).

**Required.**

- **`NSLocalNetworkUsageDescription`** + **`NSBonjourServices`** in Info.plist — same strings as iOS.
- **Local Network runtime prompt** — enforced starting **macOS 15 Sequoia**. On macOS 14 Sonoma and earlier the prompt is not shown, and Tether skips its rationale screen too (per invariant #2). On Sequoia and later, the rationale screen is shown at device-list entry, immediately before the OS prompt. On denial, Tether shows the "Open Settings" empty state; the button opens the app's permission row in System Settings → Privacy & Security → Local Network.
- **`UNUserNotificationCenter` authorisation** (runtime). Same flow as iOS — rationale screen first, in-app receipt list as fallback on denial.
- **Application Firewall** (runtime, system-owned). The first time Tether binds the file server, the OS shows "Allow incoming network connections from Tether?" — system-owned, no Tether interposing. On denial, outgoing transfers still work; incoming fails until the user allows Tether in System Settings → Network → Firewall. Tether shows a help screen naming "macOS Firewall" and the setting path.
- **`com.apple.security.network.client`** + **`com.apple.security.network.server`** entitlements. App Sandbox is enabled. Both are required for the file-server endpoint and outbound peer connections.
- **`com.apple.security.files.downloads.read-write`** entitlement. Save location is `~/Downloads/Tether/`; without the entitlement App Sandbox would block programmatic writes outside the app container. NSSavePanel-per-session is the alternative — rejected for friction.

**Not required.**

- File send — NSOpenPanel / share menu return user-selected items without a prior prompt (Powerbox grant is built into the sandbox model).
- File save runtime prompt — the entitlement above is install-time, not runtime; no TCC prompt for `~/Downloads/` under the sandbox model.

## Desktop JVM

The same Tether JVM binary ships on Windows, Linux, and macOS. The macOS host is governed by macOS privacy policies regardless of which Compose target the user installed; Windows and Linux differ.

**Required.**

- **macOS-host packaging.** `NSLocalNetworkUsageDescription` and `NSBonjourServices` embedded in the jpackage-produced `.app` bundle's Info.plist (same strings as the native macOS build). Without them, mDNS browse on Sequoia+ is silently blocked.
- **macOS-host Local Network runtime prompt** (Sequoia 15+). Same OS prompt as the native macOS build — the OS does not distinguish JVM from native processes. Tether shows its rationale screen at device-list entry on Sequoia+ only; on Sonoma and earlier, no rationale (no OS prompt to precede). On denial, the Desktop empty state shows a help link describing the path in System Settings (no first-class deep-link affordance for JVM apps).
- **macOS-host `UNUserNotificationCenter` authorisation** (runtime). Same as native macOS — rationale screen first, in-app list as fallback on denial.
- **macOS-host Application Firewall dialog** (runtime, system-owned). Same dialog and flow as native macOS.
- **macOS-host Downloads TCC prompt** (runtime, macOS 10.15+). On the first incoming transfer, the OS prompts: "Tether would like to access files in your Downloads folder." Triggered by the OS, not by Tether. Unlike the sandboxed native build, the unsandboxed JVM build cannot opt into this via entitlement — TCC governs unsandboxed access to Downloads/Documents/Desktop/iCloud/removable volumes. Tether shows its rationale screen immediately before the first incoming transfer is accepted. On denial, the receive cannot complete (no fallback save path); Tether shows a help screen pointing at Privacy & Security → Files and Folders. Retry on next foreground.
- **Windows inbound firewall.** Behaviour depends on account privilege:
  - **Administrator account:** Windows Defender Firewall alert on first bind. Allowing creates the firewall rule and inbound works thereafter.
  - **Standard user account:** no system dialog. `bind()` succeeds, but inbound is silently dropped by default policy. Tether cannot reliably distinguish this from "no peers on the network." The device-list empty state offers both possibilities: "No devices yet — check that another device is running Tether on the same Wi-Fi, and that this computer's firewall allows Tether to accept incoming connections." A help link, not a deep-link (no per-app OS permissions page on Windows).
- **CLI build (`tether-cli`, no UI).** If `bind()` fails, the process exits with a non-zero code and a descriptive stderr message identifying the port and the likely cause. No silent fallback.

**Not required.**

- File send — system file dialog and drag-and-drop on any host, no permission.
- File save on Windows/Linux — JVM has direct filesystem access to `~/Downloads/Tether/` (or the Windows user-profile equivalent).
- Notifications on Windows/Linux — system-tray toasts and libnotify both surface without a Tether-owned prompt. (If Windows ever requires per-app notification consent, the iOS/macOS rationale-then-OS-prompt pattern from invariant #2 applies automatically — same flow, no new spec work.)
- Local Network discovery on Windows/Linux — not gated by a per-app permission.

## What "working" looks like

- Opening the device list on iOS, macOS Sequoia 15+, or Desktop JVM on macOS Sequoia 15+ shows the Tether rationale screen, then the OS Local Network prompt, then the populating list. On macOS Sonoma 14 and earlier, no rationale and no OS prompt — the list populates directly.
- Picking a file or sharing one into Tether, on any platform, opens the system picker / share sheet without any prior permission dialog.
- Saving a received file lands it in the publicly-visible folder for that OS (`Downloads/Tether/` on Android; `On My iPhone → Tether/` on iOS; `~/Downloads/Tether/` on macOS and Desktop) without any prior prompt — except on Desktop JVM running on macOS, where the user sees the OS Downloads-folder TCC prompt on the first receive.
- On Android, the first launch shows the Tether rationale screen, then the `POST_NOTIFICATIONS` prompt. Denying once still re-prompts on the next launch; denying twice surfaces the in-app "Open Settings" affordance. The transfer service runs either way.
- A denied notification permission on iOS, macOS, or Desktop JVM on macOS does not prevent transfers; the in-app "Received" list is the surface.
- Denying iOS Local Network produces an empty device list with "Open Settings"; tapping it lands on Tether's permission row, not the settings root.
- On the macOS Application Firewall dialog, allowing lets peers connect; denying blocks incoming and Tether's help screen names "macOS Firewall" and the setting path.
- On Windows under a standard user account, no firewall dialog appears and the empty device-list state names the firewall as a possible cause.
- A CLI invocation that cannot bind the server port exits with a non-zero status and a clear message on stderr.

## Not in this feature

- **Wi-Fi availability** — detecting whether the device is on a Wi-Fi network is a separate system surface. See [wifi-availability.md](../wifi-availability/spec.md).
- **Channel encryption** — pinned TLS between paired devices is settled, not a permission question. See [security.md](../../../security.md).
- **Localisation of rationale strings.** Translating Info.plist descriptions and Tether-owned dialog copy is out of scope.
- **Implementation issues for the permissions flow** — per-platform code is scoped in separate feature issues created after this spec.

## Open product questions

- **Android multicast — accepted as-is.** No runtime grant for mDNS multicast on currently supported API levels. If a future Android release adds one, the cross-platform invariants apply unchanged and the Android section is updated then; not preempted now.
- **JVM-on-macOS packaging and distribution.** The Desktop JVM bundle on macOS needs the Info.plist entries above embedded by jpackage, plus signing/notarization to avoid Gatekeeper warnings. Long-term the project should pick a recommended macOS distribution (native Compose vs Desktop JVM) or document the choice; both currently ship.
