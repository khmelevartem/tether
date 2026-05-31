package com.tubetoast.tether.preferences

import com.tubetoast.tether.transfer.PeerIdentity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakePeerPreferencesStore : PeerPreferencesStore {
    private val store = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    /** Replaces the stored value for [peer] and emits to active observers. */
    fun setAutoSendSync(peer: PeerIdentity, enabled: Boolean) {
        store.value = store.value + (peer.id to enabled)
    }

    override fun observeAutoSend(peer: PeerIdentity): Flow<Boolean> =
        store.map { it[peer.id] ?: false }

    override suspend fun setAutoSend(peer: PeerIdentity, enabled: Boolean) {
        setAutoSendSync(peer, enabled)
    }

    override suspend fun autoSendEnabledFor(peer: PeerIdentity): Boolean =
        store.value[peer.id] ?: false
}
