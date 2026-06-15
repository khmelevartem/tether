package com.tubetoast.tether.presentation.settings

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.tubetoast.tether.preferences.FakeFileTransferPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FileTransferSettingsComponentTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildComponent(
        preferences: FakeFileTransferPreferences = FakeFileTransferPreferences(),
        defaultSaveLocation: String = "",
        showGalleryToggle: Boolean = false,
    ): FileTransferSettingsComponent {
        val lifecycle = LifecycleRegistry().also { it.resume() }
        return FileTransferSettingsComponent(
            componentContext = DefaultComponentContext(lifecycle),
            preferences = preferences,
            defaultSaveLocation = defaultSaveLocation,
            showGalleryToggle = showGalleryToggle,
        )
    }

    @Test
    fun `observeSaveLocation from preferences is reflected in state`() = runTest {
        val preferences = FakeFileTransferPreferences()
        val component = buildComponent(preferences = preferences, defaultSaveLocation = "/downloads")
        advanceUntilIdle()

        preferences.setSaveLocation("/documents")
        advanceUntilIdle()

        assertEquals("/documents", component.state.value.saveLocation)
    }

    @Test
    fun `observeLargeSelectionWarning from preferences is reflected in state`() = runTest {
        val preferences = FakeFileTransferPreferences(largeSelectionWarning = false)
        val component = buildComponent(preferences = preferences)
        advanceUntilIdle()

        assertFalse(component.state.value.largeSelectionWarning)
    }

    @Test
    fun `observeSaveToGallery from preferences is reflected in state`() = runTest {
        val preferences = FakeFileTransferPreferences(saveToGallery = false)
        val component = buildComponent(preferences = preferences)
        advanceUntilIdle()

        assertFalse(component.state.value.saveToGallery)
    }

    @Test
    fun `onSetLargeSelectionWarning false writes to preferences`() = runTest {
        val preferences = FakeFileTransferPreferences(largeSelectionWarning = true)
        val component = buildComponent(preferences = preferences)
        advanceUntilIdle()

        component.onSetLargeSelectionWarning(false)
        advanceUntilIdle()

        assertFalse(component.state.value.largeSelectionWarning)
    }

    @Test
    fun `onSetLargeSelectionWarning true writes to preferences`() = runTest {
        val preferences = FakeFileTransferPreferences(largeSelectionWarning = false)
        val component = buildComponent(preferences = preferences)
        advanceUntilIdle()

        component.onSetLargeSelectionWarning(true)
        advanceUntilIdle()

        assertTrue(component.state.value.largeSelectionWarning)
    }

    @Test
    fun `onSetSaveToGallery false writes to preferences`() = runTest {
        val preferences = FakeFileTransferPreferences(saveToGallery = true)
        val component = buildComponent(preferences = preferences)
        advanceUntilIdle()

        component.onSetSaveToGallery(false)
        advanceUntilIdle()

        assertFalse(component.state.value.saveToGallery)
    }

    @Test
    fun `onSetSaveToGallery true writes to preferences`() = runTest {
        val preferences = FakeFileTransferPreferences(saveToGallery = false)
        val component = buildComponent(preferences = preferences)
        advanceUntilIdle()

        component.onSetSaveToGallery(true)
        advanceUntilIdle()

        assertTrue(component.state.value.saveToGallery)
    }

    @Test
    fun `showGalleryToggle true is reflected in initial state`() {
        val component = buildComponent(showGalleryToggle = true)
        assertTrue(component.state.value.showGalleryToggle)
    }

    @Test
    fun `showGalleryToggle false is reflected in initial state`() {
        val component = buildComponent(showGalleryToggle = false)
        assertFalse(component.state.value.showGalleryToggle)
    }
}
