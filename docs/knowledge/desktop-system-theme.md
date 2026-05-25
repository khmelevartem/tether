# Desktop — observing system theme changes

Compose Multiplatform `androidx.compose.foundation.isSystemInDarkTheme()` on Desktop reads from `LocalSystemTheme`, which is a `staticCompositionLocalOf { currentSystemTheme.asComposeSystemTheme() }`. The value is captured once at composition initialisation and never updates — toggling macOS dark mode at runtime does not propagate. Confirmed in CMP 1.10.3.

## Workaround

In the Desktop entry point, override `LocalSystemTheme` with a `produceState` that polls `org.jetbrains.skiko.currentSystemTheme`:

```kotlin
application {
    val systemTheme by produceState(currentSystemTheme.asComposeSystemTheme()) {
        while (true) {
            delay(500)
            val next = currentSystemTheme.asComposeSystemTheme()
            if (next != value) value = next
        }
    }
    CompositionLocalProvider(LocalSystemTheme provides systemTheme) {
        Window(...) { content() }
    }
}
```

`LocalSystemTheme` is `@InternalComposeUiApi` — `@OptIn` is required.

500 ms polling is cheap; skiko's `currentSystemTheme` reads OS state on each call without observers.

## macOS native title bar

The Compose `Window`'s native title bar follows JVM/AWT appearance, not the Compose theme. By default it does not track system dark mode either. Fix in the entry point, before any Swing/AWT class loads:

```kotlin
System.setProperty("apple.awt.application.appearance", "system")
```

Combined with the polling above, both Compose content and the native title bar follow system appearance live.

## Why not let `application { Window { ... } }` provide it

CMP `Window` does not register an OS-appearance observer. The Skia layer it embeds exposes `currentSystemTheme` as a read-each-time call, but does not push changes through `LocalSystemTheme`. Watch CMP release notes — once Window starts re-providing `LocalSystemTheme` reactively, the polling block can be removed.
