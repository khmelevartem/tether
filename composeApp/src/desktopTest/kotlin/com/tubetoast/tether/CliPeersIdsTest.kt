package com.tubetoast.tether

import com.tubetoast.tether.protocol.Device
import kotlin.test.Test
import kotlin.test.assertEquals

class CliPeersIdsTest {
    private fun device(name: String, port: Int = 1234) = Device(name = name, host = "127.0.0.1", port = port)

    @Test
    fun `empty peers yields none`() {
        assertEquals("none", peersIds(emptyList()))
    }

    @Test
    fun `single peer yields its id`() {
        val d = device("alpha")
        assertEquals(d.id, peersIds(listOf(d)))
    }

    @Test
    fun `multiple peers are comma-separated`() {
        val alpha = device("alpha", port = 1)
        val beta = device("beta", port = 2)
        assertEquals("${alpha.id}, ${beta.id}", peersIds(listOf(alpha, beta)))
    }

    @Test
    fun `first emission is always rendered`() {
        val lines = mutableListOf<String>()
        var lastIds: String? = null

        fun emit(peers: List<Device>) {
            val ids = peersIds(peers)
            if (ids != lastIds) {
                lastIds = ids
                lines += "[peers] $ids"
            }
        }

        val alpha = device("alpha")
        emit(listOf(alpha))

        assertEquals(listOf("[peers] ${alpha.id}"), lines)
    }

    @Test
    fun `identical consecutive emission is suppressed`() {
        val lines = mutableListOf<String>()
        var lastIds: String? = null

        fun emit(peers: List<Device>) {
            val ids = peersIds(peers)
            if (ids != lastIds) {
                lastIds = ids
                lines += "[peers] $ids"
            }
        }

        val alpha = device("alpha")
        emit(listOf(alpha))
        emit(listOf(alpha))

        assertEquals(listOf("[peers] ${alpha.id}"), lines)
    }

    @Test
    fun `changed peer set emits again`() {
        val lines = mutableListOf<String>()
        var lastIds: String? = null

        fun emit(peers: List<Device>) {
            val ids = peersIds(peers)
            if (ids != lastIds) {
                lastIds = ids
                lines += "[peers] $ids"
            }
        }

        val alpha = device("alpha", port = 1)
        val beta = device("beta", port = 2)
        emit(listOf(alpha))
        emit(listOf(alpha))
        emit(listOf(alpha, beta))

        assertEquals(listOf("[peers] ${alpha.id}", "[peers] ${alpha.id}, ${beta.id}"), lines)
    }

    @Test
    fun `peers going empty emits none`() {
        val lines = mutableListOf<String>()
        var lastIds: String? = null

        fun emit(peers: List<Device>) {
            val ids = peersIds(peers)
            if (ids != lastIds) {
                lastIds = ids
                lines += "[peers] $ids"
            }
        }

        val alpha = device("alpha")
        emit(listOf(alpha))
        emit(emptyList())

        assertEquals(listOf("[peers] ${alpha.id}", "[peers] none"), lines)
    }
}
