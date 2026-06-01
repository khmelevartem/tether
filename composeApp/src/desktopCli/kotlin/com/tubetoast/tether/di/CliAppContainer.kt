package com.tubetoast.tether.di

import com.tubetoast.tether.buildCliBatchSender
import com.tubetoast.tether.protocol.DeviceType
import com.tubetoast.tether.transfer.BatchSender
import com.tubetoast.tether.transfer.PeerIdentity

class CliAppContainer(
    config: DesktopAppConfig,
) : DesktopAppContainer(
        config = config,
        ownDeviceType = DeviceType.Cli,
    ) {
    override val batchSenderFactory: (PeerIdentity) -> BatchSender = { peer ->
        buildCliBatchSender(
            peer = peer,
            fileClient = fileClient,
            connectionMonitor = connectionMonitor,
            discoveredDevicesStore = discoveredDevicesStore,
        )
    }
}
