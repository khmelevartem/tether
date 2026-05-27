package com.tubetoast.tether.di

import com.tubetoast.tether.config.DeviceNamePersistence
import com.tubetoast.tether.config.DeviceNameStore
import com.tubetoast.tether.discovery.MdnsDiscovery
import com.tubetoast.tether.network.DefaultTransferActivityTracker
import com.tubetoast.tether.network.FileClient
import com.tubetoast.tether.network.FileServer
import com.tubetoast.tether.network.TransferActivityTracker
import com.tubetoast.tether.preferences.FileTransferPreferences
import com.tubetoast.tether.preferences.PeerPreferencesStore
import com.tubetoast.tether.presentation.RootComponentFactory
import com.tubetoast.tether.presentation.transfer.PeerTransferRepository
import com.tubetoast.tether.presentation.transfer.PeerTransferRepositoryImpl
import com.tubetoast.tether.security.TrustedDeviceStore
import com.tubetoast.tether.transfer.ReceiveEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow

abstract class AppContainer {
    protected abstract val namePersistence: DeviceNamePersistence
    open val nameStore: DeviceNameStore by lazy { DeviceNameStore(namePersistence) }
    abstract val fileServer: FileServer
    abstract val mdnsDiscovery: MdnsDiscovery
    open val nameRepublisher: DeviceNameRepublisher by lazy { DeviceNameRepublisher(nameStore, mdnsDiscovery) }
    open val transferActivityTracker: TransferActivityTracker = DefaultTransferActivityTracker()
    open val fileClient: FileClient by lazy { FileClient.default(transferActivityTracker) }
    abstract val trustedDeviceStore: TrustedDeviceStore
    open val rootComponentFactory: RootComponentFactory by lazy {
        RootComponentFactory(mdnsDiscovery, peerTransferRepository)
    }
    abstract val peerPreferencesStore: PeerPreferencesStore
    abstract val fileTransferPreferences: FileTransferPreferences
    open val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    open val inboundEvents: MutableSharedFlow<ReceiveEvent> = MutableSharedFlow(extraBufferCapacity = 64)

    open val peerTransferRepository: PeerTransferRepository by lazy {
        PeerTransferRepositoryImpl(
            batchSenderFactory = { error("TODO(#191): wire BatchSender to FileClient in Phase B") },
            inboundEvents = inboundEvents,
            scope = appScope,
        )
    }
}
