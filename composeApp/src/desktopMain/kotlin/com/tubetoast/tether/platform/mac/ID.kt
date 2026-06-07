// Adapted from FileKit (MIT, github.com/vinceglb/FileKit), whose Foundation bridge derives from IntelliJ Community (Apache-2.0).
package com.tubetoast.tether.platform.mac

import com.sun.jna.NativeLong

internal class ID : NativeLong {
    constructor()
    constructor(peer: Long) : super(peer)

    fun booleanValue(): Boolean = toInt() != 0

    override fun toByte(): Byte = toLong().toByte()

    override fun toShort(): Short = toLong().toShort()

    companion object {
        val NIL: ID = ID(0L)
    }
}
