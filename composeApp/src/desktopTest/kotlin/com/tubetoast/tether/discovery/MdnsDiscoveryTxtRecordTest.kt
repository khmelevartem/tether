package com.tubetoast.tether.discovery

import javax.jmdns.ServiceInfo
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val SERVICE_TYPE = "_tether._tcp.local."
private val TXT_PROPS = mapOf("v" to "1")

// Regression for #91: JmDNS must publish a valid non-empty TXT record so Android NSD does not
// throw IllegalArgumentException("Key cannot be empty") in NsdServiceInfo.setAttribute.
class MdnsDiscoveryTxtRecordTest {
    @Test
    fun `ServiceInfo has at least one TXT property and no empty-string keys`() {
        val info = ServiceInfo.create(SERVICE_TYPE, "TestDevice", 19200, 0, 0, TXT_PROPS)
        val names = info.propertyNames.toList()
        assertTrue(names.isNotEmpty(), "TXT record must have at least one property; names=$names")
        assertFalse(names.contains(""), "TXT record must not contain empty key; names=$names")
    }
}
