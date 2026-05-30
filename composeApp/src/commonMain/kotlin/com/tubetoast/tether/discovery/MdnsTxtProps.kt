package com.tubetoast.tether.discovery

// Must be non-empty: Android NSD throws on resolve when a peer publishes a TXT record with an empty-string key.
internal fun txtProps(fingerprint: String): Map<String, String> = mapOf("fp" to fingerprint)
