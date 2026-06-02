package com.tubetoast.tether.security

import java.security.MessageDigest

internal actual fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)
