package com.tubetoast.tether.presentation.banners

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier

@Composable
fun BannersSection(component: BannersComponent, modifier: Modifier = Modifier) {
    val pending by component.pendingSummary.collectAsState()
    val dropFeedback by component.dropFeedback.collectAsState()
    val showForeground by component.showForegroundConstraint.collectAsState()

    Column(modifier = modifier) {
        if (pending != null) {
            PendingOutboundBanner(
                summary = pending!!,
                dropFeedback = dropFeedback,
                onCancel = component::onCancelPending,
            )
        }

        ForegroundConstraintBanner(
            visible = showForeground,
        )
    }
}
