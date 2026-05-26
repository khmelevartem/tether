package com.tubetoast.tether.transfer

sealed interface TransferErrorReason {
    data object NetworkLost : TransferErrorReason

    data object PeerUnreachable : TransferErrorReason

    data object ReceiverWriteFailed : TransferErrorReason

    data object AllFilesFailed : TransferErrorReason

    data object ReceiverSuspended : TransferErrorReason
}
