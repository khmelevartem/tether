package com.tubetoast.tether.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.tubetoast.tether.protocol.PeerIdentity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

interface PeerPreferencesStore {
    fun observeAutoSend(peer: PeerIdentity): Flow<Boolean>

    suspend fun setAutoSend(peer: PeerIdentity, enabled: Boolean)

    suspend fun autoSendEnabledFor(peer: PeerIdentity): Boolean
}

class DefaultPeerPreferencesStore(
    private val dataStore: DataStore<Preferences>,
) : PeerPreferencesStore {
    override fun observeAutoSend(peer: PeerIdentity): Flow<Boolean> =
        dataStore.data.map { it[autoSendKey(peer)] ?: false }

    override suspend fun setAutoSend(peer: PeerIdentity, enabled: Boolean) {
        dataStore.edit { it[autoSendKey(peer)] = enabled }
    }

    override suspend fun autoSendEnabledFor(peer: PeerIdentity): Boolean =
        observeAutoSend(peer).first()

    private fun autoSendKey(peer: PeerIdentity) = booleanPreferencesKey("auto_send:${peer.id}")
}
