# macOS-host JVM discovery must go through Bonjour, not JmDNS

## Symptom

The Desktop CLI on a macOS build via JmDNS does not see mDNS peers from other devices on the same
network (e.g., a real Android phone) — `[peers]` stays empty even after minutes of waiting.
At the same time, `dns-sd -B _tether._tcp local.` sees the same peers instantly. Mac↔Mac via
JmDNS works.

## Cause

On macOS the kernel routes incoming multicast mDNS packets from external interfaces
exclusively to `mDNSResponder` via a privileged path (BPF / kernel control
socket). User-space sockets joined to the multicast group `224.0.0.251` via the standard
`IP_ADD_MEMBERSHIP` **do not receive them** — even with `SO_REUSEPORT`. Loopback multicast
(packets sent from the same Mac) is delivered by the kernel to all subscribers normally, which is
why Mac↔Mac discovery via JmDNS works through the loopback path.

`mDNSResponder` stores external records in its cache but does not re-publish them into local
multicast on its own. A query via `dns-sd -B` will "pump" it, and cached PTR records will
indeed appear in local multicast — but SRV/TXT for external devices do not reach multicast even
via `dns-sd -L`. That is, reaching `serviceResolved` with a real IP/port via a subprocess
workaround is not possible.

Reproducible in pure Python (`socket.SOCK_DGRAM` on UDP 5353 with `IP_ADD_MEMBERSHIP`)
without JmDNS — this is not a JmDNS bug but an architectural behaviour of macOS. A full reset of
`mDNSResponder` (`sudo killall mDNSResponder`) changes nothing — the cache is rebuilt by the
same path.

## Solution

On a macOS-host JVM build, `MdnsDiscovery` uses the Apple DNS-SD API via a JNA binding
to libSystem (`DNSServiceBrowse` → `DNSServiceResolve` → `DNSServiceGetAddrInfo`), and
publishes its own service via `DNSServiceRegister`. This makes the process a client of
mDNSResponder rather than a competing multicast listener.

On Linux/Windows there is no system mDNS daemon, and JmDNS works directly via
raw multicast — JmDNS is kept there. Dispatch is done by `os.name` in `MdnsDiscovery.jvm.kt`.

## Native Apple targets are not affected by this problem

The above is about the **JVM build on a macOS host**. The native iOS target
(`appleMain` / `iosMain` via `NSNetServiceBrowser`) reaches the same
`mDNSResponder` through the system Foundation API, meaning it sits on the
correct side of the kernel filter by default. JmDNS on JVM was a problem
**precisely** because it is an independent user-space multicast listener, not
a mDNSResponder client.

**Name canonicalisation on conflict.** mDNSResponder may rename a
published service (`Self` → `Self (2)`), and the self-filter must use
the name from the publish callback, not the requested one. In JVM-Bonjour
this is done via `Event.OwnNameAssigned` ([`MdnsDiscoveryBonjour.kt`](../../composeApp/src/desktopMain/kotlin/com/tubetoast/tether/discovery/bonjour/MdnsDiscoveryBonjour.kt)).
In native Apple exactly the same pattern is already implemented — `ownServiceName = sender.name`
in the `netServiceDidPublish` callback in [`MdnsDiscovery.apple.kt`](../../composeApp/src/appleMain/kotlin/com/tubetoast/tether/discovery/MdnsDiscovery.apple.kt).
Do not repeat the mistake of "filter by requested name" in new
Apple-target locations.

See also [`apple-platform.md`](apple-platform.md) — it covers
platform-specific patterns (ObjC delegate GC, NSRunLoop in tests,
Local Network Privacy on iOS) that remain relevant for all
NSNetService-based implementations.

## Where to look

- [`MdnsDiscovery.jvm.kt`](../../composeApp/src/desktopMain/kotlin/com/tubetoast/tether/discovery/MdnsDiscovery.jvm.kt) — factory by `os.name`
- [`bonjour/DnsSd.kt`](../../composeApp/src/desktopMain/kotlin/com/tubetoast/tether/discovery/bonjour/DnsSd.kt) — JNA bindings to libSystem
- [`bonjour/MdnsDiscoveryBonjour.kt`](../../composeApp/src/desktopMain/kotlin/com/tubetoast/tether/discovery/bonjour/MdnsDiscoveryBonjour.kt) — browse/resolve/addrinfo implementation via DNS-SD
- [`bonjour/BonjourState.kt`](../../composeApp/src/desktopMain/kotlin/com/tubetoast/tether/discovery/bonjour/BonjourState.kt) — pure-Kotlin state machine, a portable pattern for other Bonjour implementations
- [`MdnsDiscoveryJmdns.kt`](../../composeApp/src/desktopMain/kotlin/com/tubetoast/tether/discovery/MdnsDiscoveryJmdns.kt) — JmDNS variant for Linux/Windows
- [`MdnsDiscovery.apple.kt`](../../composeApp/src/appleMain/kotlin/com/tubetoast/tether/discovery/MdnsDiscovery.apple.kt) — native Apple discovery (NSNetServiceBrowser → mDNSResponder)
- Issue [#47](https://github.com/khmelevartem/tether/issues/47) — diagnostic comments with experimental data
