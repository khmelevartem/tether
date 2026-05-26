package com.tubetoast.tether.presentation.transfer

import com.arkivanov.decompose.value.Value
import com.tubetoast.tether.transfer.FileSource
import com.tubetoast.tether.transfer.PeerIdentity

interface PeerTransferComponent {
    val peer: PeerIdentity
    val state: Value<PeerTransferState>

    fun toggleExpanded()

    fun startOutbound(sources: List<FileSource>)

    fun onCancel()

    fun onRetry()

    fun onRetryFile(name: String)

    fun onCancelFile(name: String)

    fun onDismiss()

    fun onShowDetails()
}
