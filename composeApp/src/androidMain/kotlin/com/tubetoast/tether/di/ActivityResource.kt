package com.tubetoast.tether.di

/**
 * Mutable holder for an Android-Activity-scoped dependency injected into a process-scoped
 * container. The Activity sets the value in `onCreate`; consumers read it lazily (typically
 * via a provider lambda) so they always observe the most recent Activity instance's resource.
 *
 * This exists because Android constructs the Application (and our container) before any
 * Activity, while resources like `ActivityResultLauncher`s require an Activity reference.
 * The standard DI pattern (constructor injection) can't model this; this holder names the
 * inherent mutable seam so it's not mistaken for ordinary container state.
 */
internal class ActivityResource<T : Any> {
    var current: T? = null
}
