package com.tubetoast.tether

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.tubetoast.tether.transfer.ShareIntentParser
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.fakes.RoboCursor
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TetherApp::class)
class IntentParsingTest {
    private val contentResolver: ContentResolver = RuntimeEnvironment.getApplication().contentResolver

    @Test
    fun `ACTION_SEND with URI produces single FileSource`() {
        val uri = Uri.parse("content://com.example/file/1")
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, uri)
        }
        val result = ShareIntentParser.parse(intent, contentResolver)
        assertEquals(1, result.size)
    }

    @Test
    fun `ACTION_SEND_MULTIPLE produces one FileSource per URI`() {
        val uris = arrayListOf(
            Uri.parse("content://com.example/file/1"),
            Uri.parse("content://com.example/file/2"),
            Uri.parse("content://com.example/file/3"),
        )
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        val result = ShareIntentParser.parse(intent, contentResolver)
        assertEquals(3, result.size)
    }

    @Test
    fun `ACTION_SEND_MULTIPLE with null EXTRA_STREAM returns emptyList`() {
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE)
        val result = ShareIntentParser.parse(intent, contentResolver)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `ACTION_SEND without EXTRA_STREAM returns emptyList`() {
        val intent = Intent(Intent.ACTION_SEND)
        val result = ShareIntentParser.parse(intent, contentResolver)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `unknown action returns emptyList`() {
        val intent = Intent(Intent.ACTION_VIEW)
        val result = ShareIntentParser.parse(intent, contentResolver)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `display name is derived from ContentResolver query when available`() {
        val uri = Uri.parse("content://com.example.media/images/42")
        val cursor = RoboCursor().apply {
            setColumnNames(listOf(OpenableColumns.DISPLAY_NAME))
            setResults(arrayOf(arrayOf("photo.jpg")))
        }
        Shadows.shadowOf(contentResolver).setCursor(uri, cursor)

        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, uri)
        }
        val result = ShareIntentParser.parse(intent, contentResolver)
        assertEquals(1, result.size)
        assertEquals("photo.jpg", result.first().name)
    }

    @Test
    fun `display name falls back to lastPathSegment when query returns nothing`() {
        val uri = Uri.parse("content://com.example.provider/files/document.pdf")
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, uri)
        }
        val result = ShareIntentParser.parse(intent, contentResolver)
        assertEquals(1, result.size)
        assertEquals("document.pdf", result.first().name)
    }
}
