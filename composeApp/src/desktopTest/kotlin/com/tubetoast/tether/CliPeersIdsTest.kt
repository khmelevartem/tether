package com.tubetoast.tether

import com.tubetoast.tether.protocol.Device
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
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
    fun `pipeline deduplicates consecutive identical peer sets`() = runTest {
        val alpha = device("alpha", port = 1)
        val beta = device("beta", port = 2)

        val result = flowOf(
            listOf(alpha),
            listOf(alpha),
            listOf(alpha, beta),
            emptyList(),
        ).map { peersIds(it) }
            .distinctUntilChanged()
            .toList()

        assertEquals(
            listOf(alpha.id, "${alpha.id}, ${beta.id}", "none"),
            result,
        )
    }
}
