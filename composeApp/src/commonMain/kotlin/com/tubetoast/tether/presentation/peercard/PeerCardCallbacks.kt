package com.tubetoast.tether.presentation.peercard

data class PeerCardCallbacks(
    val onToggleExpand: () -> Unit,
    val onToggleAutoSend: (Boolean) -> Unit,
    val onCancel: () -> Unit,
    val onDismiss: () -> Unit,
    val onRetryOutbound: () -> Unit,
    val onShowDetails: () -> Unit,
    val onOpenFiles: () -> Unit,
    /** Called when the card body is tapped while pending files exist. Null means the tap is a no-op. */
    val onClick: (() -> Unit)? = null,
)
