# Desktop — `window.isShowing` is false during first composition

On Compose Multiplatform Desktop, an effect inside `Window { ... }` content (e.g. `DisposableEffect(window)`) runs **before** the AWT frame is realized, so `window.isShowing` is still `false` at cold launch. Confirmed in CMP 1.10.3: `AwtWindow.desktop.kt` runs `setContent` (composing the content, firing its effects) synchronously in a first `DisposableEffect(Unit)`, then defers `window().isVisible = true` to a later AWT tick via `GlobalScope.launch(MainUIDispatcher)` so making the window visible does not block the compose frame. Any `if (window.isShowing) { ... }` guard in the content is therefore dead on first composition.

## Symptom

A one-time action gated on `window.isShowing` at mount never runs on cold launch — it only fires after some later event that re-reads visibility (e.g. `windowDeiconified` on minimize→restore). In Tether this silently kept the foreground `HealthMonitor` from starting until the window was minimized and restored.

## What to do

Do not gate cold-launch startup on `window.isShowing`. Start unconditionally in the mount effect (the window is on its way to the user) and use `WindowListener` for the minimize/restore transitions:

```kotlin
DisposableEffect(window) {
    window.addWindowListener(object : WindowAdapter() {
        override fun windowIconified(e: WindowEvent) = monitor.stop()
        override fun windowDeiconified(e: WindowEvent) = monitor.start(scope)
    })
    monitor.start(scope) // isShowing is false here at cold launch — start regardless
    onDispose { monitor.stop() }
}
```

Not detectable by unit test — only a real GUI launch exercises AWT frame-realization timing.
