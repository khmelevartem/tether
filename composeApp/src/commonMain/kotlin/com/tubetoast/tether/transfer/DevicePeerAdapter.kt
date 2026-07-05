package com.tubetoast.tether.transfer
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.protocol.PeerIdentity

fun Device.toPeerIdentity(): PeerIdentity = PeerIdentity(fingerprint ?: id)
