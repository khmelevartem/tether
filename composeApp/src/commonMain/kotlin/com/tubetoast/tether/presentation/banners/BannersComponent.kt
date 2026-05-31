package com.tubetoast.tether.presentation.banners

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import com.tubetoast.tether.transfer.PendingFilesRepository
import com.tubetoast.tether.transfer.PendingFilesSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BannersComponent(
    componentContext: ComponentContext,
    private val pendingFilesRepository: PendingFilesRepository,
    coroutineScope: CoroutineScope = componentContext.coroutineScope(),
) : ComponentContext by componentContext {
    private val scope = coroutineScope

    val pendingSummary: StateFlow<PendingFilesSummary?> = pendingFilesRepository.summary

    private val _dropFeedback = MutableStateFlow(false)
    val dropFeedback: StateFlow<Boolean> = _dropFeedback

    // TODO(#194): wire visible = true while any transfer is active on iOS (UIApplication foreground state observer)
    private val _showForegroundConstraint = MutableStateFlow(false)
    val showForegroundConstraint: StateFlow<Boolean> = _showForegroundConstraint

    private var dropFeedbackJob: Job? = null

    fun onCancelPending() {
        pendingFilesRepository.clear()
    }

    fun onDropDuringActiveTransfer() {
        dropFeedbackJob?.cancel()
        dropFeedbackJob = scope.launch {
            _dropFeedback.update { true }
            delay(DROP_FEEDBACK_DURATION_MS)
            _dropFeedback.update { false }
        }
    }

    private companion object {
        const val DROP_FEEDBACK_DURATION_MS = 3_000L
    }
}
