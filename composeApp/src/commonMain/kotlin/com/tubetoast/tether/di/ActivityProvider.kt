package com.tubetoast.tether.di

/**
 * Android-only: exposes the currently resumed Activity so container-scoped objects
 * can launch Activity-level operations (e.g. file picker intents).
 * [current] is null on all non-Android platforms and when no Activity is resumed on Android.
 */
expect class PlatformActivity

interface ActivityProvider {
    val current: PlatformActivity?
}
