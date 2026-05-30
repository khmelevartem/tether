package com.tubetoast.tether.discovery

import com.tubetoast.tether.network.FileClient
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.protocol.DeviceType
import com.tubetoast.tether.protocol.PeerAnnouncement
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RendezvousAnnouncerTest {
    private val sentHellos = mutableListOf<Pair<Device, PeerAnnouncement>>()

    private val fakeClient = object : FileClient(HttpClient(MockEngine { respond("", HttpStatusCode.OK) })) {
        override suspend fun sendHello(target: Device, ownInfo: PeerAnnouncement): Boolean {
            sentHellos += target to ownInfo
            return true
        }
    }

    private fun failThenSucceedClient(
        failDeviceName: String,
        sent: MutableList<Pair<Device, PeerAnnouncement>> = mutableListOf(),
    ): Pair<FileClient, MutableList<Pair<Device, PeerAnnouncement>>> {
        var failedOnce = false
        val client = object : FileClient(HttpClient(MockEngine { respond("", HttpStatusCode.OK) })) {
            override suspend fun sendHello(target: Device, ownInfo: PeerAnnouncement): Boolean {
                sent += target to ownInfo
                if (target.name == failDeviceName && !failedOnce) {
                    failedOnce = true
                    return false
                }
                return true
            }
        }
        return client to sent
    }

    private val ownAnnouncement = PeerAnnouncement(
        alias = "Me",
        fingerprint = "own-fp",
        port = 9000,
        deviceType = DeviceType.Desktop,
    )

    private val fakeSelfAnnouncementProvider = object : SelfAnnouncementProvider {
        override suspend fun get() = ownAnnouncement
    }

    @Test
    fun `sends hello to newly discovered device`() {
        val store = DiscoveredDevicesStore()
        val scope = TestScope(UnconfinedTestDispatcher())
        val a = RendezvousAnnouncer(
            store = store,
            client = fakeClient,
            selfAnnouncementProvider = fakeSelfAnnouncementProvider,
        )
        a.start(scope)
        store.upsert(Device(name = "PeerA", host = "10.0.0.1", port = 5001))
        scope.advanceUntilIdle()
        assertEquals(1, sentHellos.size)
        assertEquals("PeerA", sentHellos[0].first.name)
        a.stop()
    }

    @Test
    fun `does not send duplicate hellos to same device`() {
        val store = DiscoveredDevicesStore()
        val scope = TestScope(UnconfinedTestDispatcher())
        val a = RendezvousAnnouncer(
            store = store,
            client = fakeClient,
            selfAnnouncementProvider = fakeSelfAnnouncementProvider,
        )
        a.start(scope)
        val device = Device(name = "PeerA", host = "10.0.0.1", port = 5001)
        store.upsert(device)
        scope.advanceUntilIdle()
        store.upsert(device)
        scope.advanceUntilIdle()
        assertEquals(1, sentHellos.size, "hello must be sent only once per device")
        a.stop()
    }

    @Test
    fun `sends hello to each newly discovered device independently`() {
        val store = DiscoveredDevicesStore()
        val scope = TestScope(UnconfinedTestDispatcher())
        val a = RendezvousAnnouncer(
            store = store,
            client = fakeClient,
            selfAnnouncementProvider = fakeSelfAnnouncementProvider,
        )
        a.start(scope)
        store.upsert(Device(name = "PeerA", host = "10.0.0.1", port = 5001))
        store.upsert(Device(name = "PeerB", host = "10.0.0.2", port = 5002))
        scope.advanceUntilIdle()
        assertEquals(2, sentHellos.size)
        assertTrue(sentHellos.any { it.first.name == "PeerA" })
        assertTrue(sentHellos.any { it.first.name == "PeerB" })
        a.stop()
    }

    @Test
    fun `retries hello when first attempt fails and device is re-emitted`() {
        val store = DiscoveredDevicesStore()
        val scope = TestScope(UnconfinedTestDispatcher())
        val (client, sent) = failThenSucceedClient("PeerX")
        val a = RendezvousAnnouncer(
            store = store,
            client = client,
            selfAnnouncementProvider = fakeSelfAnnouncementProvider,
        )
        a.start(scope)

        val deviceX = Device(name = "PeerX", host = "10.0.0.1", port = 5001)
        store.upsert(deviceX)
        scope.advanceUntilIdle()
        // First attempt failed — PeerX not yet acked.
        assertEquals(1, sent.count { it.first.name == "PeerX" }, "should have attempted PeerX once")

        // Trigger re-emission by upserting another device; collector sees all current devices again.
        store.upsert(Device(name = "PeerB", host = "10.0.0.2", port = 5002))
        scope.advanceUntilIdle()
        // Second attempt for PeerX succeeds — it is now acked.
        assertEquals(2, sent.count { it.first.name == "PeerX" }, "should retry PeerX on re-emission")

        // Subsequent re-emissions must not cause another send for PeerX.
        store.upsert(Device(name = "PeerC", host = "10.0.0.3", port = 5003))
        scope.advanceUntilIdle()
        assertEquals(2, sent.count { it.first.name == "PeerX" }, "acked device must not be retried again")

        a.stop()
    }
}
