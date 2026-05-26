package com.tubetoast.tether.preferences

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileTransferPreferencesTest {
    private val defaultLocation = "/default/path"

    private lateinit var temp: TempDataStore

    @BeforeTest
    fun setUp() {
        temp = TempDataStore()
    }

    @AfterTest
    fun tearDown() {
        temp.tearDown()
    }

    private fun writablePrefs() = DefaultFileTransferPreferences(
        dataStore = temp.dataStore,
        defaultSaveLocation = defaultLocation,
        saveLocationWritable = true,
    )

    private fun readOnlyPrefs() = DefaultFileTransferPreferences(
        dataStore = temp.dataStore,
        defaultSaveLocation = defaultLocation,
        saveLocationWritable = false,
    )

    @Test
    fun `observeLargeSelectionWarning default is true`() = runTest {
        assertTrue(writablePrefs().observeLargeSelectionWarning().first())
    }

    @Test
    fun `setLargeSelectionWarning false then observeLargeSelectionWarning emits false`() = runTest {
        val p = writablePrefs()
        p.setLargeSelectionWarning(false)
        assertFalse(p.observeLargeSelectionWarning().first())
    }

    @Test
    fun `observeSaveLocation default returns constructor default`() = runTest {
        assertEquals(defaultLocation, writablePrefs().observeSaveLocation().first())
    }

    @Test
    fun `setSaveLocation when writable emits new value`() = runTest {
        val p = writablePrefs()
        p.setSaveLocation("/new/path")
        assertEquals("/new/path", p.observeSaveLocation().first())
    }

    @Test
    fun `setSaveLocation when not writable throws UnsupportedOperationException`() = runTest {
        assertFailsWith<UnsupportedOperationException> { readOnlyPrefs().setSaveLocation("/any/path") }
    }
}
