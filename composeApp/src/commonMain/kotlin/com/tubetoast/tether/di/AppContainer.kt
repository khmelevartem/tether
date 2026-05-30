package com.tubetoast.tether.di

import com.tubetoast.tether.config.DeviceNamePersistence
import com.tubetoast.tether.config.DeviceNameStore
import com.tubetoast.tether.discovery.DeviceNameRepublisher
import com.tubetoast.tether.discovery.DiscoveredDevicesStore
import com.tubetoast.tether.discovery.MdnsDiscovery
import com.tubetoast.tether.discovery.RendezvousAnnouncer
import com.tubetoast.tether.identity.DeviceIdentityStore
import com.tubetoast.tether.network.DefaultTransferActivityTracker
import com.tubetoast.tether.network.FileClient
import com.tubetoast.tether.network.FileServer
import com.tubetoast.tether.network.TransferActivityTracker
import com.tubetoast.tether.preferences.FileTransferPreferences
import com.tubetoast.tether.preferences.PeerPreferencesStore
import com.tubetoast.tether.presentation.RootComponentFactory
import com.tubetoast.tether.presentation.peer.PeersRepository
import com.tubetoast.tether.presentation.transfer.PendingFilesRepository
import com.tubetoast.tether.protocol.DeviceType
import com.tubetoast.tether.protocol.InfoDto
import com.tubetoast.tether.security.TrustedDeviceStore
import com.tubetoast.tether.transfer.BatchSender
import com.tubetoast.tether.transfer.ConnectionMonitor
import com.tubetoast.tether.transfer.NoOpConnectionMonitor
import com.tubetoast.tether.transfer.PeerUnreachableException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

abstract class AppContainer {
    protected abstract val namePersistence: DeviceNamePersistence
    open val nameStore: DeviceNameStore by lazy { DeviceNameStore(namePersistence) }
    abstract val fileServer: FileServer
    abstract val mdnsDiscovery: MdnsDiscovery
    abstract val discoveredDevicesStore: DiscoveredDevicesStore
    open val nameRepublisher: DeviceNameRepublisher by lazy { DeviceNameRepublisher(nameStore, mdnsDiscovery) }
    open val transferActivityTracker: TransferActivityTracker = DefaultTransferActivityTracker()
    open val fileClient: FileClient by lazy { FileClient.default(transferActivityTracker) }
    abstract val trustedDeviceStore: TrustedDeviceStore
    abstract val peerPreferencesStore: PeerPreferencesStore
    abstract val fileTransferPreferences: FileTransferPreferences

    abstract val deviceIdentityStore: DeviceIdentityStore
    abstract val ownFingerprint: String
    protected abstract val ownDeviceType: DeviceType

    open val appScope: CoroutineScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    open val peersRepository: PeersRepository by lazy { PeersRepository(mdnsDiscovery, appScope) }

    open val pendingFilesRepository: PendingFilesRepository by lazy { PendingFilesRepository() }

    open val connectionMonitor: ConnectionMonitor = NoOpConnectionMonitor

    // Factory: BatchSender holds per-transfer state — one instance per concurrent peer transfer.
    open val batchSenderFactory: () -> BatchSender by lazy {
        {
            BatchSender(
                sendOne = { _, _ -> throw PeerUnreachableException() },
                connectionMonitor = connectionMonitor,
            )
        }
    }

    open val rendezvousAnnouncer: RendezvousAnnouncer by lazy {
        RendezvousAnnouncer(
            store = discoveredDevicesStore,
            client = fileClient,
            ownInfo = {
                InfoDto(
                    alias = nameStore.currentName,
                    fingerprint = ownFingerprint,
                    port = fileServer.port,
                    deviceType = ownDeviceType,
                )
            },
        )
    }

    open val rootComponentFactory: RootComponentFactory by lazy {
        RootComponentFactory(
            peersRepository = peersRepository,
            batchSenderFactory = batchSenderFactory,
            pendingFilesRepository = pendingFilesRepository,
            peerPreferencesStore = peerPreferencesStore,
        )
    }
}
