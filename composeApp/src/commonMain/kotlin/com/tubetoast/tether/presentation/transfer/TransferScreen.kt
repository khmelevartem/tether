package com.tubetoast.tether.presentation.transfer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.subscribeAsState

@Composable
fun TransferScreen(
    component: TransferComponent,
    modifier: Modifier = Modifier,
) {
    val state by component.state.subscribeAsState()

    when (state) {
        is TransferState.Terminal.AllSuccess,
        is TransferState.Terminal.PartialFailure,
        is TransferState.Terminal.AllFailed,
        is TransferState.Terminal.ConnectionErrorSummary,
        TransferState.Terminal.Cancelled,
        -> TransferSummaryScreen(
            state = state as TransferState.Terminal,
            onDone = component::onDone,
            onRetryFile = component::onRetryFile,
            onRetryAll = component::onRetryAll,
            onBack = component::onDone,
            modifier = modifier,
        )

        is TransferState.FolderConfirm -> {
            val folderState = state as TransferState.FolderConfirm
            FolderSendConfirmDialog(
                fileCount = folderState.fileCount,
                totalBytes = folderState.totalBytes,
                onContinue = { component.onFolderConfirm(true) },
                onCancel = { component.onFolderConfirm(false) },
            )
        }

        is TransferState.CancelConfirm -> {
            val snapshot = (state as TransferState.CancelConfirm).snapshot
            TransferProgressScreen(
                state = snapshot,
                peerName = component.peer.name,
                onCancel = component::onCancelClicked,
                onBack = component::onDone,
                onFolderConfirm = component::onFolderConfirm,
                onRetryAll = component::onRetryAll,
                modifier = modifier,
            )
            CancelConfirmDialog(
                onStopTransfer = component::onCancelConfirmed,
                onKeepSending = component::onKeepSending,
            )
        }

        else -> TransferProgressScreen(
            state = state,
            peerName = component.peer.name,
            onCancel = component::onCancelClicked,
            onBack = component::onDone,
            onFolderConfirm = component::onFolderConfirm,
            onRetryAll = component::onRetryAll,
            modifier = modifier,
        )
    }
}
