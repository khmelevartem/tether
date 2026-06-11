package com.tubetoast.tether.transfer

import com.tubetoast.tether.protocol.Device

fun Device.toPeerIdentity(): PeerIdentity = PeerIdentity(fingerprint ?: id)
