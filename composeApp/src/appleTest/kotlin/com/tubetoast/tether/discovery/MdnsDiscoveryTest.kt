package com.tubetoast.tether.discovery

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MdnsDiscoveryTest {
    @Test
    fun `stop before start does not throw`() {
        MdnsDiscovery().stop()
    }

    @Test
    fun `start twice without stop throws IllegalStateException`() {
        val discovery = MdnsDiscovery()
        discovery.start("DoubleStart", 19001)
        try {
            assertFailsWith<IllegalStateException> {
                discovery.start("DoubleStart2", 19002)
            }
        } finally {
            discovery.stop()
        }
    }

    @Test
    fun `stop emits empty list`() = runBlocking {
        val discovery = MdnsDiscovery()
        discovery.start("StopEmits", 19003)
        discovery.stop()
        assertTrue(discovery.discoveredDevices.first().isEmpty())
    }

    @Test
    fun `restart — stop then start does not throw`() {
        val discovery = MdnsDiscovery()
        discovery.start("RestartDevice", 19004)
        discovery.stop()
        discovery.start("RestartDevice", 19004)
        discovery.stop()
    }

    @Test
    fun `multiple stops do not throw`() {
        val discovery = MdnsDiscovery()
        discovery.start("MultiStop", 19005)
        discovery.stop()
        discovery.stop()
    }
}
