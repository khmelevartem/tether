package com.tubetoast.tether.di

/**
 * The platform's current foreground UI host. Container-scoped objects use this to launch
 * platform-level operations (e.g. file picker intents) against the currently resumed context.
 *
 * [current] is null when no host is active. On Android it is the resumed ComponentActivity;
 * on other platforms it is null until those platforms implement their host in #193/#194.
 */
expect class PlatformActivity

interface ActivityProvider {
    val current: PlatformActivity?
}
