package com.tubetoast.tether.di

import com.tubetoast.tether.config.DeviceNamePersistence
import com.tubetoast.tether.config.DeviceNameStore
import com.tubetoast.tether.discovery.DeviceNameRepublisher
import com.tubetoast.tether.discovery.MdnsDiscovery
import com.tubetoast.tether.network.DefaultTransferActivityTracker
import com.tubetoast.tether.network.FileClient
import com.tubetoast.tether.network.FileServer
import com.tubetoast.tether.network.TransferActivityTracker
import com.tubetoast.tether.preferences.FileTransferPreferences
import com.tubetoast.tether.preferences.PeerPreferencesStore
import com.tubetoast.tether.presentation.RootComponentFactory
import com.tubetoast.tether.security.TrustedDeviceStore
import com.tubetoast.tether.transfer.BatchSender
import com.tubetoast.tether.transfer.ConnectionMonitor
import com.tubetoast.tether.transfer.NoOpConnectionMonitor
import com.tubetoast.tether.transfer.PeerUnreachableException

abstract class AppContainer {
    protected abstract val namePersistence: DeviceNamePersistence
    open val nameStore: DeviceNameStore by lazy { DeviceNameStore(namePersistence) }
    abstract val fileServer: FileServer
    abstract val mdnsDiscovery: MdnsDiscovery
    open val nameRepublisher: DeviceNameRepublisher by lazy { DeviceNameRepublisher(nameStore, mdnsDiscovery) }
    open val transferActivityTracker: TransferActivityTracker = DefaultTransferActivityTracker()
    open val fileClient: FileClient by lazy { FileClient.default(transferActivityTracker) }
    abstract val trustedDeviceStore: TrustedDeviceStore
    abstract val peerPreferencesStore: PeerPreferencesStore
    abstract val fileTransferPreferences: FileTransferPreferences

    open val connectionMonitor: ConnectionMonitor = NoOpConnectionMonitor

    open val batchSenderFactory: () -> BatchSender by lazy {
        {
            BatchSender(
                sendOne = { _, _ -> throw PeerUnreachableException() },
                connectionMonitor = connectionMonitor,
            )
        }
    }

    open val rootComponentFactory: RootComponentFactory by lazy {
        RootComponentFactory(
            discovery = mdnsDiscovery,
            batchSenderFactory = batchSenderFactory,
            peerPreferencesStore = peerPreferencesStore,
        )
    }
}
