package com.tubetoast.tether.transfer

import com.tubetoast.tether.preferences.FakePeerPreferencesStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

fun fakePeerTransferEngineRegistry(
    appScope: CoroutineScope,
    inboundEvents: Flow<ReceiveEvent> = MutableSharedFlow(),
): PeerTransferEngineRegistry = PeerTransferEngineRegistry(
    appScope = appScope,
    engineFactory = { id, _ ->
        PeerTransferEngine(
            peer = id,
            batchSenderFactory = fakeBatchSender(),
            inboundEvents = inboundEvents,
            scope = appScope,
            peerPreferencesStore = FakePeerPreferencesStore(),
        )
    },
)
