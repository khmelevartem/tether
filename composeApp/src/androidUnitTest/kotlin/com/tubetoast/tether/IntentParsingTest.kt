package com.tubetoast.tether

import android.content.Intent
import android.net.Uri
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class IntentParsingTest {
    private val contentResolver = RuntimeEnvironment.getApplication().contentResolver

    @Test
    fun actionSendWithSingleUriReturnsSingleSource() {
        val uri = Uri.parse("content://com.example/file/1")
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, uri)
        }

        val sources = intent.toFileSources(contentResolver)

        assertEquals(1, sources.size)
    }

    @Test
    fun actionSendMultipleWithUriListReturnsMultipleSources() {
        val uris = arrayListOf(
            Uri.parse("content://com.example/file/1"),
            Uri.parse("content://com.example/file/2"),
            Uri.parse("content://com.example/file/3"),
        )
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }

        val sources = intent.toFileSources(contentResolver)

        assertEquals(3, sources.size)
    }

    @Test
    fun intentWithNoExtrasReturnsEmpty() {
        val intent = Intent(Intent.ACTION_SEND)

        val sources = intent.toFileSources(contentResolver)

        assertTrue(sources.isEmpty())
    }

    @Test
    fun unrelatedActionReturnsEmpty() {
        val intent = Intent(Intent.ACTION_VIEW)

        val sources = intent.toFileSources(contentResolver)

        assertTrue(sources.isEmpty())
    }
}
