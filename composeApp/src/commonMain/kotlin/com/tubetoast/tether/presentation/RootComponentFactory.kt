package com.tubetoast.tether.presentation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.coroutines.withLifecycle
import com.tubetoast.tether.config.DeviceNameStore
import com.tubetoast.tether.peer.PeersRepository
import com.tubetoast.tether.preferences.FileTransferPreferences
import com.tubetoast.tether.presentation.banners.BannersComponent
import com.tubetoast.tether.presentation.banners.PeerConflictRelay
import com.tubetoast.tether.presentation.devicename.DeviceNameComponent
import com.tubetoast.tether.presentation.settings.FileTransferSettingsComponent
import com.tubetoast.tether.presentation.settings.SettingsComponent
import com.tubetoast.tether.presentation.transfer.PeerTransferComponent
import com.tubetoast.tether.protocol.DeviceType
import com.tubetoast.tether.transfer.FilePicker
import com.tubetoast.tether.transfer.PeerTransferEngineRegistry
import com.tubetoast.tether.transfer.PendingFilesRepository
import com.tubetoast.tether.transfer.TransferActivityTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class RootComponentFactory(
    private val peersRepository: PeersRepository,
    private val peerTransferEngineRegistry: PeerTransferEngineRegistry,
    private val pendingFilesRepository: PendingFilesRepository,
    private val peerConflictRelay: PeerConflictRelay,
    private val filePicker: FilePicker,
    private val fileTransferPreferences: FileTransferPreferences,
    private val nameStore: DeviceNameStore,
    private val transferActivityTracker: TransferActivityTracker,
    private val ownDeviceType: DeviceType,
) {
    fun create(componentContext: ComponentContext): RootComponent =
        RootComponent(
            componentContext = componentContext,
            pendingFilesRepository = pendingFilesRepository,
            peerListFactory = { ctx, onShowDetails, onOpenSettings ->
                PeerListComponent(
                    componentContext = ctx,
                    peersRepository = peersRepository,
                    peerTransferComponentFactory = { childCtx, lifecycle, peer, onPeerChosen ->
                        val engine = peerTransferEngineRegistry.engineFor(peer.id)
                        val componentScope = CoroutineScope(Dispatchers.Main.immediate).withLifecycle(lifecycle)
                        PeerTransferComponent(
                            componentContext = childCtx,
                            peer = peer,
                            lifecycleRegistry = lifecycle,
                            engine = engine,
                            scope = componentScope,
                            onShowDetails = onShowDetails,
                            pendingFilesRepository = pendingFilesRepository,
                            filePicker = filePicker,
                            conflictRelay = peerConflictRelay,
                            fileTransferPreferences = fileTransferPreferences,
                            onPeerChosen = onPeerChosen,
                        )
                    },
                    bannersComponentFactory = { bannersCtx ->
                        BannersComponent(
                            componentContext = bannersCtx,
                            pendingFilesRepository = pendingFilesRepository,
                            peersRepository = peersRepository,
                            engineRegistry = peerTransferEngineRegistry,
                            conflictRelay = peerConflictRelay,
                            transferActivityTracker = transferActivityTracker,
                            ownDeviceType = ownDeviceType,
                        )
                    },
                    deviceNameComponentFactory = { deviceNameCtx ->
                        DeviceNameComponent(
                            componentContext = deviceNameCtx,
                            nameStore = nameStore,
                        )
                    },
                    onOpenSettings = onOpenSettings,
                )
            },
            settingsFactory = { ctx, onBack ->
                SettingsComponent(
                    componentContext = ctx,
                    fileTransferComponentFactory = { ftCtx ->
                        FileTransferSettingsComponent(
                            componentContext = ftCtx,
                            preferences = fileTransferPreferences,
                        )
                    },
                    onBack = onBack,
                )
            },
        )
}
