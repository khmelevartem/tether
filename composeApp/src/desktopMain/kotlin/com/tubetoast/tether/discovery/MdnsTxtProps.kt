package com.tubetoast.tether.discovery

// Must be non-empty: Android NSD throws on a TXT record with an empty-string key.
internal val TXT_PROPS: Map<String, String> = mapOf("v" to "1")
