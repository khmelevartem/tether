package com.tubetoast.tether.transfer

sealed interface RoutingDecision {
    data class AutoSend(
        val peer: PeerIdentity,
    ) : RoutingDecision

    data object RequirePeerListTap : RoutingDecision
}

object AutoSendRouter {
    fun route(
        onlinePaired: List<PeerIdentity>,
        autoSendEnabled: (PeerIdentity) -> Boolean,
    ): RoutingDecision {
        val single = onlinePaired.singleOrNull() ?: return RoutingDecision.RequirePeerListTap
        return if (autoSendEnabled(single)) RoutingDecision.AutoSend(single) else RoutingDecision.RequirePeerListTap
    }
}
