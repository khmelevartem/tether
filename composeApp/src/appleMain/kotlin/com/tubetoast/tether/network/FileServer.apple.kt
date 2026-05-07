package com.tubetoast.tether.network

/**
 * Apple-target stub. Ktor's CIO server engine is JVM-only — a real Apple
 * implementation will land when the iOS receive-side is built (likely on
 * Ktor's `ktor-server-darwin` engine or a hand-rolled NSStream loop).
 *
 * Until then the stub keeps the common `AppContainer.fileServer` contract
 * satisfied so common code can reference the type. Calling [start] throws
 * loudly so any accidental wiring is caught immediately at runtime.
 */
actual class FileServer {
    actual fun start(): Int =
        error("FileServer is not yet implemented for Apple targets")

    actual fun stop() = Unit
}
