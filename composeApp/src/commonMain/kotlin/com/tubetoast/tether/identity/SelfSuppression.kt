package com.tubetoast.tether.identity

fun isOwnAnnounce(peerFingerprint: String?, ownFingerprint: String): Boolean =
    peerFingerprint != null && peerFingerprint == ownFingerprint
