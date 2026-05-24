package com.tubetoast.tether.discovery

import javax.jmdns.ServiceInfo
import kotlin.test.Test
import kotlin.test.assertFalse

private const val SERVICE_TYPE = "_tether._tcp.local."

// Regression test for #91: JmDNS registration must not produce a TXT record with an empty-string key.
// Android NSD throws IllegalArgumentException("Key cannot be empty") in NsdServiceInfo.setAttribute
// if the TXT record wire bytes contain a zero-length entry.
class MdnsDiscoveryTxtRecordTest {
    @Test
    fun `ServiceInfo registration produces no empty-string property key`() {
        val info = ServiceInfo.create(SERVICE_TYPE, "TestDevice", 19200, 0, 0, byteArrayOf())
        val names = info.propertyNames.toList()
        assertFalse(names.contains(""), "TXT record must not contain empty key; names=$names")
    }
}
