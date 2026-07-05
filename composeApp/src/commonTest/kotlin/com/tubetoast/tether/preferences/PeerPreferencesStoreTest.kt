package com.tubetoast.tether.preferences

import com.tubetoast.tether.protocol.PeerIdentity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PeerPreferencesStoreTest {
    private val peerA = PeerIdentity("peer-a")
    private val peerB = PeerIdentity("peer-b")

    private lateinit var temp: TempDataStore
    private lateinit var store: PeerPreferencesStore

    @BeforeTest
    fun setUp() {
        temp = TempDataStore()
        store = DefaultPeerPreferencesStore(temp.dataStore)
    }

    @AfterTest
    fun tearDown() {
        temp.tearDown()
    }

    @Test
    fun `observeAutoSend default is false`() = runTest {
        assertFalse(store.observeAutoSend(peerA).first())
    }

    @Test
    fun `setAutoSend true then observeAutoSend emits true`() = runTest {
        store.setAutoSend(peerA, true)
        assertTrue(store.observeAutoSend(peerA).first())
    }

    @Test
    fun `setAutoSend per peer is isolated`() = runTest {
        store.setAutoSend(peerA, true)
        assertFalse(store.observeAutoSend(peerB).first())
    }

    @Test
    fun `autoSendEnabledFor returns persisted value`() = runTest {
        store.setAutoSend(peerA, true)
        assertTrue(store.autoSendEnabledFor(peerA))
        assertFalse(store.autoSendEnabledFor(peerB))
    }

    @Test
    fun `setAutoSend toggle back to false is observed`() = runTest {
        store.setAutoSend(peerA, true)
        store.setAutoSend(peerA, false)
        assertFalse(store.observeAutoSend(peerA).first())
    }
}
