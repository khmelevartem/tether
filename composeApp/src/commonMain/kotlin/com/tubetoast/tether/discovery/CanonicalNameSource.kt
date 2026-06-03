package com.tubetoast.tether.discovery

import kotlinx.coroutines.flow.StateFlow

/** Exposes the mDNS-canonical name assigned by the platform after the publish callback fires. */
interface CanonicalNameSource {
    val ownPublishedName: StateFlow<String?>
}
