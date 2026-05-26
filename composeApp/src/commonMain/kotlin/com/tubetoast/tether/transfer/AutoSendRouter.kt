package com.tubetoast.tether.transfer

sealed interface RoutingDecision {
    data class AutoSend(
        val peer: PeerIdentity,
    ) : RoutingDecision

    data object RequireDeviceListTap : RoutingDecision
}

object AutoSendRouter {
    fun route(
        onlinePaired: List<PeerIdentity>,
        autoSendEnabled: (PeerIdentity) -> Boolean,
    ): RoutingDecision {
        val single = onlinePaired.singleOrNull() ?: return RoutingDecision.RequireDeviceListTap
        return if (autoSendEnabled(single)) RoutingDecision.AutoSend(single) else RoutingDecision.RequireDeviceListTap
    }
}
