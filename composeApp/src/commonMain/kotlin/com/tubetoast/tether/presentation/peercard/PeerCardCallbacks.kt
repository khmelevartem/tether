package com.tubetoast.tether.presentation.peercard

data class PeerCardCallbacks(
    val onToggleExpand: () -> Unit,
    val onToggleAutoSend: (Boolean) -> Unit,
    val onShowAutoSendInfo: () -> Unit,
    val onCancel: () -> Unit,
    val onDismiss: () -> Unit,
    val onRetry: () -> Unit,
    val onShowDetails: () -> Unit,
    val onOpenFiles: () -> Unit,
)
