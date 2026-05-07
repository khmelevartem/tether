package com.tubetoast.tether.di

/**
 * Implemented by the [android.app.Application] class so Android components with
 * framework-managed empty constructors (Activity, Service, BroadcastReceiver) can
 * reach the composition root without a service-locator global.
 *
 * Access pattern: `(application as AppContainerProvider).container`.
 */
interface AppContainerProvider {
    val container: AndroidAppContainer
}
