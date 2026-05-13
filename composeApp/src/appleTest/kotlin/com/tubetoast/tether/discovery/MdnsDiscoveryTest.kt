package com.tubetoast.tether.discovery

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import platform.CoreFoundation.CFRunLoopRunInMode
import platform.CoreFoundation.kCFRunLoopDefaultMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

// NSNetService delivers callbacks via NSRunLoop, not via coroutine dispatchers.
// runBlocking / TestDispatcher / Turbine don't pump NSRunLoop, so integration tests
// use awaitCondition() which explicitly ticks the run loop in short bursts via
// CFRunLoopRunInMode — a stable CoreFoundation C API available on all Apple targets.
@OptIn(ExperimentalForeignApi::class)
private fun awaitCondition(timeoutSec: Double = 10.0, condition: () -> Boolean): Boolean {
    val deadline = TimeSource.Monotonic.markNow() + timeoutSec.seconds
    while (!condition()) {
        CFRunLoopRunInMode(kCFRunLoopDefaultMode, 0.05, false)
        if (TimeSource.Monotonic.markNow() > deadline) return false
    }
    return true
}

// NOTE: integration tests rely on mDNS multicast on the host machine.
// Stop any running CLI instances before running — external _tether._tcp.
// services may interfere with discovery.
class MdnsDiscoveryTest {
    // --- Lifecycle tests (no network required) ---

    @Test
    fun `stop before start does not throw`() {
        MdnsDiscovery(DiscoveredDevicesStore()).stop()
    }

    @Test
    fun `start twice without stop throws IllegalStateException`() {
        val discovery = MdnsDiscovery(DiscoveredDevicesStore())
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
        val discovery = MdnsDiscovery(DiscoveredDevicesStore())
        discovery.start("StopEmits", 19003)
        discovery.stop()
        assertTrue(discovery.discoveredDevices.first().isEmpty())
    }

    @Test
    fun `restart — stop then start does not throw`() {
        val discovery = MdnsDiscovery(DiscoveredDevicesStore())
        discovery.start("RestartDevice", 19004)
        discovery.stop()
        discovery.start("RestartDevice", 19004)
        discovery.stop()
    }

    @Test
    fun `multiple stops do not throw`() {
        val discovery = MdnsDiscovery(DiscoveredDevicesStore())
        discovery.start("MultiStop", 19005)
        discovery.stop()
        discovery.stop()
    }

    // --- Integration tests (NSRunLoop-pumped) ---

    @Test
    fun `two instances discover each other`() {
        val a = MdnsDiscovery(DiscoveredDevicesStore())
        val b = MdnsDiscovery(DiscoveredDevicesStore())
        try {
            a.start("ApplePeerA", 19110)
            b.start("ApplePeerB", 19111)

            assertTrue(
                awaitCondition {
                    a.discoveredDevices.value.any { it.name == "ApplePeerB" } &&
                        b.discoveredDevices.value.any { it.name == "ApplePeerA" }
                },
                "peers did not discover each other within 10s",
            )
        } finally {
            a.stop()
            b.stop()
        }
    }

    @Test
    fun `instances do not discover themselves`() {
        val a = MdnsDiscovery(DiscoveredDevicesStore())
        val b = MdnsDiscovery(DiscoveredDevicesStore())
        try {
            a.start("AppleSelfA", 19120)
            b.start("AppleSelfB", 19121)

            // wait until cross-discovery is established, then check self-filter
            assertTrue(
                awaitCondition {
                    a.discoveredDevices.value.any { it.name == "AppleSelfB" } &&
                        b.discoveredDevices.value.any { it.name == "AppleSelfA" }
                },
                "cross-discovery did not complete within 10s",
            )

            assertTrue(a.discoveredDevices.value.none { it.name == "AppleSelfA" })
            assertTrue(b.discoveredDevices.value.none { it.name == "AppleSelfB" })
        } finally {
            a.stop()
            b.stop()
        }
    }

    @Test
    fun `discovered device has correct port`() {
        val a = MdnsDiscovery(DiscoveredDevicesStore())
        val b = MdnsDiscovery(DiscoveredDevicesStore())
        try {
            a.start("AppleHostA", 19130)
            b.start("AppleHostB", 19131)

            assertTrue(
                awaitCondition {
                    a.discoveredDevices.value.any { it.name == "AppleHostB" }
                },
                "AppleHostB not discovered within 10s",
            )

            val device = a.discoveredDevices.value.first { it.name == "AppleHostB" }
            assertEquals(19131, device.port)
            assertTrue(device.host.isNotEmpty(), "host should not be empty")
        } finally {
            a.stop()
            b.stop()
        }
    }
}
