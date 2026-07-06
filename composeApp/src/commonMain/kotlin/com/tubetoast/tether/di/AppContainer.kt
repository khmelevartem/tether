package com.tubetoast.tether.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.tubetoast.tether.config.DeviceNamePersistence
import com.tubetoast.tether.config.DeviceNameStore
import com.tubetoast.tether.discovery.ActiveTransfers
import com.tubetoast.tether.discovery.DefaultSelfAnnouncementProvider
import com.tubetoast.tether.discovery.DeviceNameRepublisher
import com.tubetoast.tether.discovery.DiscoveredDevicesStore
import com.tubetoast.tether.discovery.HealthMonitor
import com.tubetoast.tether.discovery.MdnsDiscovery
import com.tubetoast.tether.discovery.RendezvousAnnouncer
import com.tubetoast.tether.discovery.SelfAnnouncementProvider
import com.tubetoast.tether.identity.DeviceIdentityStore
import com.tubetoast.tether.network.FileClient
import com.tubetoast.tether.network.FileServer
import com.tubetoast.tether.network.PeerFileSender
import com.tubetoast.tether.peer.PeersRepository
import com.tubetoast.tether.preferences.DefaultPeerPreferencesStore
import com.tubetoast.tether.preferences.FileTransferPreferences
import com.tubetoast.tether.preferences.PeerPreferencesStore
import com.tubetoast.tether.presentation.RootComponentFactory
import com.tubetoast.tether.presentation.banners.PeerConflictRelay
import com.tubetoast.tether.protocol.DeviceType
import com.tubetoast.tether.protocol.PeerIdentity
import com.tubetoast.tether.security.DefaultTrustedDeviceStore
import com.tubetoast.tether.security.DeviceKeyPair
import com.tubetoast.tether.security.TrustedDeviceStore
import com.tubetoast.tether.transfer.AutoSendDispatcher
import com.tubetoast.tether.transfer.BatchSender
import com.tubetoast.tether.transfer.ConnectionMonitor
import com.tubetoast.tether.transfer.DefaultTransferActivityTracker
import com.tubetoast.tether.transfer.FilePicker
import com.tubetoast.tether.transfer.InboundEventRouter
import com.tubetoast.tether.transfer.NoOpConnectionMonitor
import com.tubetoast.tether.transfer.PeerTransferEngine
import com.tubetoast.tether.transfer.PeerTransferEngineRegistry
import com.tubetoast.tether.transfer.PendingFilesRepository
import com.tubetoast.tether.transfer.ReconnectionTimeout
import com.tubetoast.tether.transfer.RegistryActiveTransfers
import com.tubetoast.tether.transfer.TransferActivityTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

abstract class AppContainer {
    protected abstract val dataStore: DataStore<Preferences>
    protected abstract val trustedDataStore: DataStore<Preferences>

    protected abstract val namePersistence: DeviceNamePersistence
    protected abstract val deviceKeyPair: DeviceKeyPair
    open val nameStore: DeviceNameStore by lazy { DeviceNameStore(namePersistence) }
    abstract val fileServer: FileServer
    abstract val mdnsDiscovery: MdnsDiscovery
    open val discoveredDevicesStore: DiscoveredDevicesStore by lazy { DiscoveredDevicesStore() }
    open val nameRepublisher: DeviceNameRepublisher by lazy { DeviceNameRepublisher(nameStore, mdnsDiscovery) }
    open val transferActivityTracker: TransferActivityTracker by lazy { DefaultTransferActivityTracker(appScope) }
    open val fileClient: FileClient by lazy { FileClient.default() }
    open val trustedDeviceStore: TrustedDeviceStore by lazy { DefaultTrustedDeviceStore(trustedDataStore) }
    open val peerPreferencesStore: PeerPreferencesStore by lazy { DefaultPeerPreferencesStore(dataStore) }
    abstract val fileTransferPreferences: FileTransferPreferences

    open val deviceIdentityStore: DeviceIdentityStore by lazy { DeviceIdentityStore(deviceKeyPair.publicKey) }
    protected abstract val ownDeviceType: DeviceType

    open val appScope: CoroutineScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    open val peersRepository: PeersRepository by lazy { PeersRepository(mdnsDiscovery, appScope) }

    open val pendingFilesRepository: PendingFilesRepository by lazy { PendingFilesRepository() }

    open val peerConflictRelay: PeerConflictRelay by lazy { PeerConflictRelay() }

    abstract val filePicker: FilePicker

    open val connectionMonitor: ConnectionMonitor = NoOpConnectionMonitor

    open val peerFileSender: PeerFileSender by lazy { PeerFileSender(fileClient, discoveredDevicesStore) }

    // Factory: BatchSender holds per-transfer state — one instance per concurrent peer transfer.
    @OptIn(kotlin.uuid.ExperimentalUuidApi::class)
    open val batchSenderFactory: (PeerIdentity) -> BatchSender by lazy {
        { peer ->
            val batchId = kotlin.uuid.Uuid
                .random()
                .toString()
            BatchSender(
                sendOne = { source, onProgress -> peerFileSender.send(peer, source, onProgress) },
                beginBatch = { id, totalFiles, totalBytes ->
                    peerFileSender.beginBatch(
                        peer,
                        id,
                        totalFiles,
                        totalBytes,
                    )
                },
                endBatch = { id -> peerFileSender.endBatch(peer, id) },
                batchId = batchId,
                connectionMonitor = connectionMonitor,
                tracker = transferActivityTracker,
            )
        }
    }

    open val selfAnnouncementProvider: SelfAnnouncementProvider by lazy {
        DefaultSelfAnnouncementProvider(nameStore, fileServer, deviceIdentityStore, ownDeviceType)
    }

    open val rendezvousAnnouncer: RendezvousAnnouncer by lazy {
        RendezvousAnnouncer(
            store = discoveredDevicesStore,
            client = fileClient,
            selfAnnouncementProvider = selfAnnouncementProvider,
        )
    }

    open val inboundEventRouter: InboundEventRouter by lazy {
        InboundEventRouter(scope = appScope, inboundEvents = fileServer.events)
    }

    open val peerTransferEngineRegistry: PeerTransferEngineRegistry by lazy {
        PeerTransferEngineRegistry(
            appScope = appScope,
            engineFactory = { peer, engineScope ->
                PeerTransferEngine(
                    peer = peer,
                    batchSenderFactory = { batchSenderFactory(peer) },
                    inboundEvents = inboundEventRouter.eventsFor(peer),
                    reconnectionTimeout = ReconnectionTimeout.DEFAULT,
                    scope = engineScope,
                    peerPreferencesStore = peerPreferencesStore,
                    cancelBatch = { batchId -> peerFileSender.cancelBatch(peer, batchId) },
                )
            },
            engineDispatcher = Dispatchers.Default,
        )
    }

    private val activeTransfers: ActiveTransfers by lazy {
        RegistryActiveTransfers(peerTransferEngineRegistry, appScope)
    }

    open val healthMonitor: HealthMonitor by lazy {
        HealthMonitor(
            store = discoveredDevicesStore,
            fileClient = fileClient,
            activeTransfers = activeTransfers,
            probeDispatcher = Dispatchers.Default,
        )
    }

    open val autoSendDispatcher: AutoSendDispatcher by lazy {
        AutoSendDispatcher(
            peersRepository = peersRepository,
            pendingFilesRepository = pendingFilesRepository,
            peerPreferencesStore = peerPreferencesStore,
            engineRegistry = peerTransferEngineRegistry,
            scope = appScope,
        )
    }

    open val rootComponentFactory: RootComponentFactory by lazy {
        RootComponentFactory(
            peersRepository = peersRepository,
            peerTransferEngineRegistry = peerTransferEngineRegistry,
            pendingFilesRepository = pendingFilesRepository,
            peerConflictRelay = peerConflictRelay,
            filePicker = filePicker,
            fileTransferPreferences = fileTransferPreferences,
            nameStore = nameStore,
            transferActivityTracker = transferActivityTracker,
            ownDeviceType = ownDeviceType,
        )
    }
}
