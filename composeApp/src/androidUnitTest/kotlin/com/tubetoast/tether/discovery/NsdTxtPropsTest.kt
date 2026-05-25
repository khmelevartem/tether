package com.tubetoast.tether.discovery

import android.net.nsd.NsdServiceInfo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NsdTxtPropsTest {
    @Test
    fun `TXT_PROPS applied to NsdServiceInfo produces non-empty attributes with no empty keys`() {
        val serviceInfo = NsdServiceInfo().apply {
            TXT_PROPS.forEach { (k, v) -> setAttribute(k, v) }
        }
        val attributes = serviceInfo.attributes
        assertFalse(attributes.isEmpty(), "attributes must be non-empty")
        assertTrue(attributes.keys.none { it.isNullOrEmpty() }, "no empty-string key allowed")
    }
}
