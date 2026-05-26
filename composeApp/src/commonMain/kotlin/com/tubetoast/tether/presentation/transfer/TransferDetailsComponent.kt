package com.tubetoast.tether.presentation.transfer

import com.arkivanov.decompose.value.Value

interface TransferDetailsComponent {
    val state: Value<PeerTransferState>

    fun onRetryFile(name: String)

    fun onCancelFile(name: String)

    fun onCancelTransfer()

    fun onRetryAll()

    fun onBack()
}
