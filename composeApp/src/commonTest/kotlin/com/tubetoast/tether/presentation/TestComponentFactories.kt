package com.tubetoast.tether.presentation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.tubetoast.tether.config.DeviceNameStore
import com.tubetoast.tether.config.EphemeralDeviceNamePersistence
import com.tubetoast.tether.peer.FakePeersRepository
import com.tubetoast.tether.peer.Peer
import com.tubetoast.tether.peer.PeersRepository
import com.tubetoast.tether.preferences.FakeFileTransferPreferences
import com.tubetoast.tether.preferences.FakePeerPreferencesStore
import com.tubetoast.tether.presentation.banners.BannersComponent
import com.tubetoast.tether.presentation.banners.PeerConflictRelay
import com.tubetoast.tether.presentation.devicename.DeviceNameComponent
import com.tubetoast.tether.presentation.transfer.PeerTransferComponent
import com.tubetoast.tether.protocol.DeviceType
import com.tubetoast.tether.transfer.FakeFilePicker
import com.tubetoast.tether.transfer.NoOpTransferActivityTracker
import com.tubetoast.tether.transfer.PeerIdentity
import com.tubetoast.tether.transfer.PeerTransferEngine
import com.tubetoast.tether.transfer.PendingFilesRepository
import com.tubetoast.tether.transfer.fakeBatchSender
import com.tubetoast.tether.transfer.fakePeerTransferEngineRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow

internal fun fakePeerTransferComponentFactory(
    coroutineScope: CoroutineScope,
    onDestroyContext: (String) -> Unit = {},
    pendingFilesRepository: PendingFilesRepository = PendingFilesRepository(),
    filePicker: FakeFilePicker = FakeFilePicker(result = emptyList()),
    onShowDetails: (PeerIdentity) -> Unit = {},
    onPeerChosen: (PeerTransferComponent) -> Unit = {},
): (ComponentContext, LifecycleRegistry, Peer) -> PeerTransferComponent =
    { childCtx, childLifecycle, peer ->
        val wrappedLifecycle = object : LifecycleRegistry by childLifecycle {
            override fun onDestroy() {
                onDestroyContext(peer.id.id)
                childLifecycle.onDestroy()
            }
        }
        val engine = PeerTransferEngine(
            peer = peer.id,
            batchSenderFactory = fakeBatchSender(),
            inboundEvents = MutableSharedFlow(),
            scope = coroutineScope,
            peerPreferencesStore = FakePeerPreferencesStore(),
        )
        PeerTransferComponent(
            componentContext = childCtx,
            peer = peer,
            lifecycleRegistry = wrappedLifecycle,
            engine = engine,
            onShowDetails = onShowDetails,
            scope = coroutineScope,
            pendingFilesRepository = pendingFilesRepository,
            filePicker = filePicker,
            conflictRelay = PeerConflictRelay(),
            fileTransferPreferences = FakeFileTransferPreferences(),
            onPeerChosen = onPeerChosen,
        )
    }

internal fun fakeBannersComponentFactory(
    coroutineScope: CoroutineScope,
    pendingFilesRepository: PendingFilesRepository = PendingFilesRepository(),
    peersRepository: PeersRepository = FakePeersRepository(),
): (ComponentContext) -> BannersComponent =
    { bannersCtx ->
        BannersComponent(
            componentContext = bannersCtx,
            pendingFilesRepository = pendingFilesRepository,
            peersRepository = peersRepository,
            engineRegistry = fakePeerTransferEngineRegistry(coroutineScope),
            conflictRelay = PeerConflictRelay(),
            transferActivityTracker = NoOpTransferActivityTracker,
            ownDeviceType = DeviceType.Android,
            coroutineScope = coroutineScope,
        )
    }

internal fun fakeDeviceNameComponentFactory(scope: CoroutineScope): (ComponentContext) -> DeviceNameComponent =
    { deviceNameCtx ->
        DeviceNameComponent(
            componentContext = deviceNameCtx,
            nameStore = DeviceNameStore(EphemeralDeviceNamePersistence()),
            coroutineScope = scope,
        )
    }
