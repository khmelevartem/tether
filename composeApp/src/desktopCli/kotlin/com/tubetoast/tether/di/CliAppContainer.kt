package com.tubetoast.tether.di

import com.tubetoast.tether.buildCliBatchSender
import com.tubetoast.tether.network.CliPairingConfirmationHandler
import com.tubetoast.tether.network.HandshakePairing
import com.tubetoast.tether.network.PairingConfirmationHandler
import com.tubetoast.tether.network.PeerPairing
import com.tubetoast.tether.protocol.DeviceType
import com.tubetoast.tether.transfer.BatchSender
import com.tubetoast.tether.transfer.PeerIdentity
import kotlinx.coroutines.flow.first

class CliAppContainer(
    config: DesktopAppConfig,
) : DesktopAppContainer(
        config = config,
        ownDeviceType = DeviceType.Cli,
    ) {
    // One stdin handler drives both sides: server prompt on incoming /pair, client prompt before send.
    private val cliPairingHandler = CliPairingConfirmationHandler()

    override val pairingConfirmationHandler: PairingConfirmationHandler = cliPairingHandler

    override val peerPairing: PeerPairing by lazy {
        HandshakePairing(
            client = httpClient,
            trustedDeviceStore = trustedDeviceStore,
            ownKeyPair = deviceKeyPair,
            ownDeviceName = { nameStore.name.first() },
            confirmationHandler = cliPairingHandler,
        )
    }

    override val batchSenderFactory: (PeerIdentity) -> BatchSender = { peer ->
        buildCliBatchSender(
            peer = peer,
            fileClient = fileClient,
            connectionMonitor = connectionMonitor,
            discoveredDevicesStore = discoveredDevicesStore,
        )
    }
}
