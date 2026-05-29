package com.tubetoast.tether.discovery

import javax.jmdns.ServiceInfo
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val SERVICE_TYPE = "_tether._tcp.local."

class MdnsDiscoveryTxtRecordTest {
    @Test
    fun `ServiceInfo has at least one TXT property and no empty-string keys`() {
        val info = ServiceInfo.create(SERVICE_TYPE, "TestDevice", 19200, 0, 0, txtProps("test-fingerprint"))
        val names = info.propertyNames.toList()
        assertTrue(names.isNotEmpty(), "TXT record must have at least one property; names=$names")
        assertFalse(names.contains(""), "TXT record must not contain empty key; names=$names")
    }

    @Test
    fun `txtProps includes fingerprint under fp key`() {
        val props = txtProps("abc123")
        assertTrue(props.containsKey("fp"), "txtProps must contain fp key")
        assertTrue(props.containsKey("v"), "txtProps must contain v key")
        assertTrue(props["fp"] == "abc123", "fp value must match fingerprint")
    }
}
