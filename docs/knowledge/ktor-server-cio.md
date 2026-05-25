# Ktor server CIO — known gotchas

Things that bit us during `FileServer` work (#81). Check here before re-debugging a flaky upload or a wrong status code.

---

## Body channel closes silently on premature client disconnect

**Symptom:** client cancels an upload mid-stream (timeout, network drop, deliberate `cancel()`); server still responds `200 OK` with a truncated file on disk.

**Root cause:** `call.receiveChannel()` (or `call.receiveStream()` on JVM) returns a `ByteReadChannel` that, in Ktor 3.1, often **closes cleanly** when the client disconnects instead of closing exceptionally. Both signals:

- `body.closedCause` returns `null`
- `body.isClosedForRead` flips to `true`
- the existing `readAvailable` / `copyTo` loop exits normally with a partial byte count

Affects JVM and Native CIO equally. Symmetric across platforms; not a Native-only issue.

**Fix:** validate completion explicitly after the loop, with two complementary checks:

```kotlin
val body = call.receiveChannel()
val bytesCopied = /* write body to disk, return total */

// (1) propagate any exceptional close Ktor *did* set
body.closedCause?.let { throw it }

// (2) when Content-Length is set, also compare received bytes against it —
//     covers the case Ktor closes the channel cleanly despite a truncated body
val expected = call.request.contentLength()
if (expected != null && bytesCopied < expected) {
    error("incomplete upload — got $bytesCopied of $expected bytes")
}
```

If Content-Length is *not* set (the client used chunked transfer encoding without declaring a size), there is no reliable way to detect truncation from the body channel alone — make the client send Content-Length whenever the body size is known. Our `FileClient` does this via `OutgoingContent.ReadChannelContent.contentLength` when `totalBytes` is provided (#93); plain `setBody(channel)` would silently fall back to chunked encoding.

**Reference:** [`FileServerRoutes.kt`](../../composeApp/src/commonMain/kotlin/com/tubetoast/tether/network/FileServerRoutes.kt) — `installFileServerRoutes`; [`FileClient.kt`](../../composeApp/src/commonMain/kotlin/com/tubetoast/tether/network/FileClient.kt) — `asOctetStreamContent`.

---

## `ktor-server-cio` runs on Kotlin/Native since 3.0

Older docs (and our own [adr-macos-native-vs-jvm.md](../engineering/adr/adr-macos-native-vs-jvm.md), pre-update) state that Ktor server is JVM-only. Since Ktor 3.0 (October 2024) the CIO engine is published for `iosArm64`, `iosSimulatorArm64`, and other Native targets.

If you see "Ktor server is JVM-only" in a doc, treat it as historical context. The current architecture decision is recorded in [adr-apple-fileserver-engine.md](../engineering/adr/adr-apple-fileserver-engine.md).

---

## POSIX `fwrite` discards short-write errors

Not a Ktor gotcha but adjacent — when streaming a body to disk via `fopen`/`fwrite` on Native:

- `fwrite` returns the number of items actually written. On a short write (disk full, quota exceeded, I/O error) it returns less than requested and sets `ferror(file)`.
- Discarding the return value silently produces truncated files. JVM's `OutputStream.write` raises an exception in the same condition; the POSIX equivalent has to be checked manually.
- Always also `fflush(file) != 0`-check before responding success — stdio buffers may surface a deferred error there rather than in `fwrite` itself.

**Reference:** [`FileServer.apple.kt`](../../composeApp/src/appleMain/kotlin/com/tubetoast/tether/network/FileServer.apple.kt) — `AppleUploadStorage.writeBody`.
