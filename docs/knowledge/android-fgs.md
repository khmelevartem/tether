# Android Foreground Service — Known Issues & Patterns

Problems we've encountered with `TetherForegroundService` and Android FGS APIs.
Check here before re-researching a known issue.

---

## connectedDevice type suppresses notification on Android 14+

**Symptom:** FGS starts without crashing, `FileServer` and mDNS work normally, but
the notification never appears in the notification shade. No errors in logcat.

**Root cause:** `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` on Android 14+ requires
not just the permission declaration but an **active hardware connection** (Bluetooth,
NFC, or USB). Without one, the system silently suppresses the FGS notification even
if `FOREGROUND_SERVICE_CONNECTED_DEVICE` and one of the "any-of" permissions
(`CHANGE_WIFI_MULTICAST_STATE`, `CHANGE_WIFI_STATE`, etc.) are declared correctly.

This was observed on a real device running Android 14+ during #35:
the `SecurityException` from `startForeground()` was fixed by adding
`CHANGE_WIFI_MULTICAST_STATE`, but the notification still did not appear.

**Current fix:** use `dataSync` type instead. It shows the notification reliably
without hardware requirements. 6h-per-24h rolling cap on Android 15+ is a known
trade-off, documented below.

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />

<service
    android:name=".network.TetherForegroundService"
    android:foregroundServiceType="dataSync"
    android:exported="false" />
```

```kotlin
// TetherForegroundService.kt
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
}
```

**Rule:** when changing the FGS type, always verify on a real device that the
notification is visible. Emulators may not reproduce suppression behavior.

---

## dataSync 6h/24h cap on Android 15+

**Symptom:** on API 35+, after ~6h cumulative background FGS runtime in a 24-hour rolling
window, the service is force-stopped by the OS. Two observable failure modes:

- An already-running service that hits the cap: the notification disappears with no
  user-visible message; logcat shows a fatal exception with a "dataSync did not stop
  within its timeout" message.
- A service started after the cap is already exhausted: attempting to restart the service
  raises a start-not-allowed exception with a "Time limit already exhausted for foreground
  service type dataSync" message (visible at the next cold start after a force-quit).

([Android 15 behavior changes — Data sync foreground services](https://developer.android.com/about/versions/15/behavior-changes-15#datasync-timeout))

**Root cause:** Android 15 (API 35) introduced a 6-hour-per-24h cap on `dataSync` FGS
runtime. The timer resets each time the user brings the app to the foreground.

**Why we accept it:** alternatives are worse — `connectedDevice` is suppressed without
active hardware (see section above), `specialUse` taxes every Play release with manual
review. The 24-hour window is rolling and the timer resets on foreground; most usage
patterns involve the user opening the app at least once every few hours to send or receive,
which resets the cap. For a phone left backgrounded all day with no interaction, the service
will eventually die silently — the user notices the missing notification and reopens the app,
at which point the foreground-reset takes effect.

**Detection:**
```
adb logcat | grep -E "dataSync did not stop within its timeout|Time limit already exhausted"
```

**Recovery:** user reopens the app; cold start re-arms the foreground service (see the "Stop button UX — sticky Stop pattern" section below).

---

## bindService(flags=0) is not a reliable "is service running?" check

**Symptom:** service is not running but `bindService(intent, conn, 0)` returns `true`,
causing the caller to skip `startForegroundService`. Result: service never starts,
notification never appears.

**Root cause:** `bindService` return value semantics vary across Android versions and
OEM builds. Without `BIND_AUTO_CREATE`, `true` is supposed to mean "service is already
running and the bind was dispatched", but some implementations return `true` simply
because the intent resolved, regardless of whether the service is actually alive.

**Fix:** do not use `bindService` as a running-check. Calling
`ContextCompat.startForegroundService` on an already-running service is safe —
it routes to `onStartCommand` which returns `START_STICKY` without re-initialising
anything. Use `android:configChanges` in the manifest to prevent Activity
recreation on rotation (the main scenario where double-start was a concern).

```kotlin
// MainActivity.kt — safe, idempotent
private fun startService() {
    ContextCompat.startForegroundService(
        this,
        Intent(this, TetherForegroundService::class.java),
    )
}
```

---

## startForegroundService from onResume can suppress notification on Android 13+

**Symptom:** `POST_NOTIFICATIONS` permission is granted, FGS starts, server works,
but the notification does not appear.

**Root cause:** `onResume` is called after every dialog dismissal, including the
`POST_NOTIFICATIONS` permission dialog itself. If `startForegroundService` is called
from `onResume` and the service calls `startForeground` before the permission is
granted, Android 13+ suppresses the notification. Subsequent `startForegroundService`
calls on the already-running service only trigger `onStartCommand` — they do **not**
re-post the notification.

**Fix:** call `startForegroundService` from `onCreate` (not `onResume`). `onCreate`
runs once per Activity instance and is not re-triggered by dialog dismissals.
For the "restart after Stop" use case, see the sticky-Stop pattern below.

---

## Stop button UX — sticky Stop pattern

**Context:** `TetherForegroundService` has a Stop action in the notification.
After Stop, the service should remain stopped until the user explicitly restarts it.

**Current behaviour:** Stop is sticky until the app's cold start. `startService()`
is called only from `MainActivity.onCreate`, which runs only on cold start
(not on background→foreground transitions).

**Why not onStart/onResume:** calling `startService()` from `onStart` or `onResume`
restarts the service every time the user returns to the app — defeating the Stop
button when the app is in the background.

**Trade-off:** language/density config changes that are not in `android:configChanges`
will recreate the Activity and trigger `onCreate`, restarting the service. Acceptable
for MVP; proper persistent state (SharedPreferences flag) tracked in #58.

```xml
<!-- AndroidManifest.xml — prevents restart on common config changes -->
<activity
    android:name=".MainActivity"
    android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|uiMode|keyboardHidden">
```

---

## Wi-Fi / wake locks during active transfers

**Symptom:** transfers of large files abort mid-way when the screen is locked.
No network error on the sender side; the receiver's upload handler gets a closed channel.

**Root cause:** Wi-Fi power-save mode (radio parks itself when the screen turns off) and
Android Doze (CPU throttled, process suspended) can tear the TCP connection before the
transfer completes. The FGS type does not prevent either.

**Fix:** hold a `WifiManager.WifiLock` (`WIFI_MODE_FULL_HIGH_PERF`) and a
`PowerManager.WakeLock` (`PARTIAL_WAKE_LOCK`) for the duration of each active transfer.
Release both when the last concurrent transfer finishes.

**Known platform limitation — WifiLock on API 29+.** `WIFI_MODE_FULL_HIGH_PERF` is
deprecated since API 29 and the OS silently substitutes any acquired lock with
`WIFI_MODE_FULL_LOW_LATENCY`, which only activates while the screen is on and the app is
foreground. There is no public replacement that keeps the Wi-Fi radio active during
screen-off on Android 10+. Practical consequence:

- API 24-28: WifiLock + WakeLock both effective — full fix.
- API 29+: only WakeLock is effective. Wi-Fi radio power-save mid-transfer remains a
  possible failure mode on screen-off; the WakeLock prevents CPU throttling but cannot
  keep the radio awake. The lock is still acquired (no harm) so the fix is forward-
  compatible if Android ever exposes a screen-off Wi-Fi lock.

**Anti-pattern:** holding the locks for the entire FGS lifetime keeps the radio and CPU
active even when no transfer is running, draining the battery with no user benefit.

**Implementation:**

- `AndroidTransferLockHolder` (`androidMain`) owns both locks with `setReferenceCounted(false)`.
  Reference counting is delegated to `DefaultTransferActivityTracker`; the holder's acquire/release
  are idempotent guards (`isHeld` check).
- `DefaultTransferActivityTracker` (`commonMain`) wraps each upload (server-side) and send
  (client-side) with `withActiveTransfer { … }`. On `0 → 1` it calls `onFirstEnter`; on `N → 0`
  it calls `onLastExit`. Concurrent transfers share a single lock.
- `TetherForegroundService.onDestroy` calls `transferActivityTracker.releaseAll()` before stopping
  the server, releasing any locks held by in-flight transfers killed by the OS.

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

```kotlin
// AndroidAppContainer.kt
private val lockHolder = AndroidTransferLockHolder(application)
override val transferActivityTracker: TransferActivityTracker = DefaultTransferActivityTracker(
    onFirstEnter = lockHolder::acquire,
    onLastExit = lockHolder::release,
)
```

