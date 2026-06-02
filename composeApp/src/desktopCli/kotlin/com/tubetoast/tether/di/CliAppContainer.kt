package com.tubetoast.tether.di

import com.tubetoast.tether.buildCliBatchSender
import com.tubetoast.tether.network.PairingConfirmationHandler
import com.tubetoast.tether.protocol.DeviceType
import com.tubetoast.tether.transfer.BatchSender
import com.tubetoast.tether.transfer.PeerIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class CliPairingConfirmationHandler(
    private val output: (String) -> Unit = ::println,
    private val input: suspend () -> String? = { withContext(Dispatchers.IO) { readLine() } },
) : PairingConfirmationHandler {
    override suspend fun confirmPairing(pin: Int, remoteName: String): Boolean {
        output("[pair] PIN: ${pin.toString().padStart(4, '0')} — '$remoteName' is requesting to pair")
        output("[pair] Confirm? (y/n): ")
        val line = input()
        return line?.trim()?.lowercase() == "y"
    }
}

class CliAppContainer(
    config: DesktopAppConfig,
) : DesktopAppContainer(
        config = config,
        ownDeviceType = DeviceType.Cli,
    ) {
    override val pairingConfirmationHandler: PairingConfirmationHandler = CliPairingConfirmationHandler()

    override val batchSenderFactory: (PeerIdentity) -> BatchSender = { peer ->
        buildCliBatchSender(
            peer = peer,
            fileClient = fileClient,
            connectionMonitor = connectionMonitor,
            discoveredDevicesStore = discoveredDevicesStore,
        )
    }
}
