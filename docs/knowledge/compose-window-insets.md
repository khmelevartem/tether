# Compose window insets — root strategy & safeContent vs safeDrawing

How Tether applies system-area insets, and a non-obvious gesture-navigation gotcha.

## Root strategy

`RootContent` applies safe-area insets on the **vertical axis only**:

```kotlin
.windowInsetsPadding(WindowInsets.safeContent.only(WindowInsetsSides.Vertical))
```

Top/bottom system regions (status bar, notch, home/gesture indicator) stay protected; the horizontal axis is left unpadded so screens render flush to the edges. Screens own their horizontal margins via `TetherTheme.spacing` — the root must not add horizontal insets, or mobile content double-pads (root safe-area inset stacked on the screen's own `spacing.lg`). Desktop reports zero insets on every axis, so the modifier is a no-op there.

## safeContent vs safeDrawing — bottom inset differs on gesture nav

- `safeDrawing` = `systemBars` ∪ `displayCutout` (drawn system UI).
- `safeGestures` = system/home-gesture reservation zones.
- `safeContent` = `safeDrawing` ∪ `safeGestures`.

On Android with **gesture navigation**, `safeGestures.bottom` (the home-swipe interception zone) is **wider** than `safeDrawing.bottom` (the thin drawn nav pill). So `safeContent.bottom > safeDrawing.bottom`. Padding the root with `safeDrawing` instead of `safeContent` silently shrinks the bottom inset, letting the bottom-most interactive row sit inside the OS gesture zone (swipes captured by the system, not the app). Use `safeContent` to preserve the gesture margin; `safeContentPadding()` is built on `safeContent`.

## Inset consumption

`windowInsetsPadding` both adds padding and **consumes** that inset — nested `windowInsetsPadding` calls see the remainder, preventing double-application. Narrowing the root to `Vertical` leaves the horizontal inset unconsumed; Tether screens read no insets (fixed `spacing.lg`), so nothing re-applies it — that absence is what yields the flush horizontal edges.

## Full-bleed rows

List rows render full-bleed (no horizontal padding on the `LazyColumn`) so row decoration — the peerIdentity accent bar, dividers — reaches the screen edge, matching `PeerCard`. Inner text keeps its own `spacing` padding for breathing room. See `PeerListScreen`, `TransferDetailsScreen`.
