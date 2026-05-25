# File transfer wire contract

How a sender hands a single file (or one file from a recursive folder send) to a receiver over the Tether HTTP transport. The engine choice that carries this contract lives in [`adr-network-stack.md`](adr/adr-network-stack.md); this doc owns the *shape* of the request — endpoint, metadata placement, body framing, path-safety boundary.

## Goal

A receiver, given a sequence of files belonging to one folder send, lands them on its local filesystem at paths that **preserve the sender's intended folder structure** and **cannot escape the configured downloads root**, regardless of whether the sender is correct, buggy, or hostile. From the user's side: a folder dragged on one device appears with the same shape on the other. From the system's side: every byte that hits disk hit it through one sanitization gate and one canonical-realisation check, at different layers.

## Endpoint shape

A single endpoint, `POST /upload`, carries every file transfer — flat or nested. Each file is one HTTP request. The sender supplies, per request:

- The **relative POSIX path** of the file inside the send, in the `name` query parameter. For a flat single-file send this is the leaf name (`photo.jpg`); for a folder send it is the path within the chosen folder using `/` separators (`Vacation/2024/IMG_001.jpg`).
- The **file bytes** as the raw request body (`application/octet-stream`), streamed — no buffering on either side.
- `Content-Length` when the sender knows the size; absent for unbounded streams.

The receiver responds:

- `200 OK` with `{"savedPath": "<absolute-path-on-receiver>"}` on success.
- `400 Bad Request` with `{"error":"invalid_relative_path"}` when the `name` parameter is missing, empty, or rejected by [path sanitization](#path-sanitization).
- `500 Internal Server Error` with `{"error":"<message>"}` on storage failure (disk full, canonical-realisation rejection, short write).

The receiver does **not** accept a request without a usable `name`. Tether is pre-MVP, no compat surface — silent fallbacks here would only hide bugs.

## Path sanitization

Sanitization is a **two-layer boundary**, with each layer answering a different question. The two are not duplicates of one rule — each catches a class the other cannot.

### Layer 1 — lexical, in the route handler

Before the receiver opens the request body, it runs the raw `name` parameter through a pure-Kotlin sanitizer in `commonMain`. The sanitizer either returns a canonicalised POSIX relative path or signals rejection. The route maps rejection to `400 Bad Request` and never opens the body or the destination file.

Rules the sanitizer enforces:

- **Empty input** rejects.
- **Absolute paths** reject — leading `/`, leading `\`, or Windows drive-letter prefix (`C:`, `C:/foo`, `C:foo`).
- **Traversal segments** reject — any segment equal to `.` or `..` after URL-decoding and after splitting on both `/` and `\`. A segment of `...` or `....` is a literal name, allowed.
- **URL-encoded traversal** rejects after a single explicit decode pass — `%2e%2e`, `..%2f`, `%2e%2e%2f` all collapse to forms the previous rules catch.
- **Malformed UTF-8** in the decoded byte sequence rejects rather than substituting replacement characters. Silent substitution would let an attacker hide bytes from the segment checks via overlong encodings.
- **Embedded separators after decode** are part of the structure, not part of a segment — `\` is normalised to `/`, then empty segments (produced by runs of `/`, a leading `/`, or a trailing `/`) reject. The rejection is not a recovery step: the sender supplied an ambiguous or absolute-looking path and must fix it.
- **NUL byte** in any segment rejects.
- **Unicode and emoji** in segment names are allowed. Tether protects against traversal, not against expressive filenames.

The sanitizer lives in `commonMain` so the sender can pre-validate before hitting the network — a sender that knows its own path is invalid should fail loudly at compose time, not by getting a 400. Reuse, not duplication: the receiver always re-runs the check, regardless of whether the sender ran it.

### Layer 2 — canonical realisation, in `UploadStorage`

After sanitization passes, the relative path is resolved against the platform's downloads root. The storage layer guarantees that the **realised** absolute path stays inside the root — not the lexical path, the path the operating system would actually open. This catches:

- **Symlinks inside the root pointing outside** it. Lexical sanitization sees only the string; only OS-level path realisation resolves the symlink.
- **Case-folding collisions** on case-insensitive volumes (macOS HFS+/APFS default, Windows). Whether two strings name the same entry is a filesystem property, not a string property.
- **Platform-specific path normalisation** that turns a string into a different entry than the lexical reading would suggest.

A failure here surfaces as `IOException` from the storage seam, mapped to `500` by the route handler, and the partial file is removed on the failure path. The check runs **before** the first byte hits the destination — opening for write after realisation, not before.

## Storage seam

`UploadStorage` is the per-platform sink behind a `commonMain` interface. The route handler talks only to the interface; platforms differ only in how bytes reach durable storage and how the canonical-realisation check is implemented.

Responsibilities owned by the seam:

- **Resolve** a sanitised relative path to a destination identifier (an absolute path on filesystem-backed platforms; a MediaStore URI on Android when that lands).
- **Open** the destination for writing, creating parent directories as needed.
- **Enforce** the canonical-realisation rule before writing.
- **Stream** the request body into the destination — no full buffering. Detect and surface short writes (`fwrite` POSIX return, `OutputStream` exceptions).
- **Commit** the file as visible to the user on success, **abort** (delete partial bytes) on failure.

Responsibilities owned by the route handler, not the seam:

- HTTP status mapping.
- Lexical sanitization (Layer 1 above).
- Tracking the active-transfer scope through the transfer-activity tracker.

Per-platform actuals differ along expected axes:

- **JVM / Desktop / Android-app-private** — filesystem under a chosen root, JDK NIO for directory creation and canonicalisation.
- **Android — MediaStore** (out of scope of this doc; tracked by its own issue) — directory creation is implicit in the URI, the canonical-check shape becomes "the resolved URI is under the chosen collection".
- **Apple** — Foundation for directory creation, POSIX `realpath` for canonicalisation, low-level file I/O for streaming.

## Cross-cutting concerns

- **Streaming, not buffering.** A sender that pipes a 50 GiB file streams it through. The route handler holds one fixed-size copy buffer and the storage layer holds whatever its sink requires. No multipart framing, no JSON header frame in the body — the wire is `?name=` plus raw octet-stream specifically so the streaming property is preserved across both Ktor CIO JVM and whatever engine ends up on Apple post-TLS ([`adr-network-stack.md`](adr/adr-network-stack.md)).
- **Authority and trust.** The receiver assumes nothing about the sender: a paired peer is no more trusted with the filesystem than an unpaired one. Path safety is a property of the receiver, not of the pairing protocol.
- **Failure visibility.** `400` and `500` both carry a JSON body with a stable `error` key. The sender surfaces the message to the user verbatim only on `500`; `invalid_relative_path` is a programmer error and surfaces as a generic "couldn't send <file>" — a user dragging a folder shouldn't read about path traversal.
- **Observability.** The route logs the sanitised relative path on success and the rejection reason on `400`; the raw pre-sanitisation string is not logged (avoids logging hostile input verbatim).

## Rejected alternatives

For each, the reason it was rejected — short, because none was close.

- **Dedicated `X-Tether-Relative-Path` HTTP header.** Adjacent in shape to the chosen design but introduces a parallel metadata channel for no gain — the existing `?name=` already carries filename metadata, widening its semantics to "relative path" is a one-word change in the contract description. Reduces wire-shape diversity to debug across four platforms.
- **Path as URL segments (`POST /upload/{percent-encoded-path}`).** Forces every sender platform to percent-encode `/` inside the path, with per-platform encoding quirks (NSURLSession normalises path segments in ways that are hard to predict). Higher integration risk than query parameter for no expressive gain.
- **`multipart/form-data` with a `relativePath` text part and a `file` binary part.** Standards-friendly but adds a multipart parser on the hot path of every file transfer, against the streaming-not-buffering invariant. Multipart parsing differences between the engines in play across `adr-network-stack.md` are exactly the kind of cross-platform surface this contract is trying to keep small.
- **Custom framing inside the body (length-prefixed JSON header, then bytes).** Defeats curl- and Wireshark-level debuggability for no scope this doc covers. Listed for completeness — the rejection is the rule.

## What this doc does *not* commit to

- The exact set of fields in a future "transfer manifest" if folder sends ever need pre-flight negotiation (size totals, peer free space, abort token). The current contract is per-file; a manifest, if added, gets its own endpoint, not a new field in `?name=`.
- The MediaStore-side shape of `UploadStorage` on Android.
- Resume / partial-transfer semantics. Post-MVP; out of scope.
- The Ktor engine carrying these routes — owned by [`adr-network-stack.md`](adr/adr-network-stack.md). The contract above must remain expressible on every engine that ADR allows.
