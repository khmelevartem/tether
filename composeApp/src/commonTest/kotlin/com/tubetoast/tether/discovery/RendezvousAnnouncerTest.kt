package com.tubetoast.tether.discovery

import com.tubetoast.tether.network.FileClient
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.protocol.DeviceType
import com.tubetoast.tether.protocol.InfoDto
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
    private val sentHellos = mutableListOf<Pair<Device, InfoDto>>()

    private val fakeClient = object : FileClient(HttpClient(MockEngine { respond("", HttpStatusCode.OK) })) {
        override suspend fun sendHello(target: Device, ownInfo: InfoDto): Boolean {
            sentHellos += target to ownInfo
            return true
        }
    }

    private val ownInfo = InfoDto(
        alias = "Me",
        fingerprint = "own-fp",
        port = 9000,
        deviceType = DeviceType.Desktop,
    )

    @Test
    fun `sends hello to newly discovered device`() {
        val store = DiscoveredDevicesStore()
        val scope = TestScope(UnconfinedTestDispatcher())
        val a = RendezvousAnnouncer(
            store = store,
            client = fakeClient,
            ownInfo = { ownInfo },
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
            ownInfo = { ownInfo },
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
            ownInfo = { ownInfo },
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
}
