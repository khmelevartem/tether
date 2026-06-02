package com.tubetoast.tether.security

fun computePinCode(keyA: ByteArray, keyB: ByteArray): Int {
    val len = maxOf(keyA.size, keyB.size)
    val xored = ByteArray(len) { i ->
        val a = keyA.getOrElse(i) { 0.toByte() }.toInt()
        val b = keyB.getOrElse(i) { 0.toByte() }.toInt()
        (a xor b).toByte()
    }
    val hash = sha256(xored)
    val high = hash[0].toInt() and 0xFF
    val low = hash[1].toInt() and 0xFF
    return (high * 256 + low) % 10000
}
