package com.tubetoast.tether.transfer

import io.ktor.utils.io.ByteReadChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OnCloseFileSourceTest {
    @Test
    fun onLastClose_firesExactlyOnce_normalPath() {
        var callCount = 0
        val source = OnCloseFileSource(NoOpFileSource()) { callCount++ }

        source.close()
        source.close()
        source.close()

        assertEquals(1, callCount)
    }

    @Test
    fun onLastClose_firesExactlyOnce_whenInnerThrows() {
        var callCount = 0
        val throwing = object : FileSource {
            override val name = "f"
            override val relativePath = "f"
            override val sizeBytes: Long? = null
            override val materializesLazily = false

            override suspend fun openReadChannel(): ByteReadChannel = ByteReadChannel(ByteArray(0))

            override fun close() = throw RuntimeException("inner close failed")
        }
        val source = OnCloseFileSource(throwing) { callCount++ }

        assertFailsWith<RuntimeException> { source.close() }
        assertEquals(1, callCount)

        // Second close is a no-op — inner and onLastClose are not called again.
        source.close()
        assertEquals(1, callCount)
    }

    private class NoOpFileSource : FileSource {
        override val name = "f"
        override val relativePath = "f"
        override val sizeBytes: Long? = null
        override val materializesLazily = false

        override suspend fun openReadChannel(): ByteReadChannel = ByteReadChannel(ByteArray(0))

        override fun close() = Unit
    }
}
