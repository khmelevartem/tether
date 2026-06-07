package com.tubetoast.tether.presentation.devicename

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.tubetoast.tether.config.DeviceNameStore
import com.tubetoast.tether.config.InMemoryDeviceNamePersistence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceNameComponentTest {
    private suspend fun buildComponent(
        persistence: InMemoryDeviceNamePersistence,
    ): DeviceNameComponent {
        val store = DeviceNameStore(persistence)
        store.init()
        val lifecycle = LifecycleRegistry().also { it.resume() }
        return DeviceNameComponent(
            componentContext = DefaultComponentContext(lifecycle),
            nameStore = store,
            coroutineScope = CoroutineScope(UnconfinedTestDispatcher()),
        )
    }

    @Test
    fun `enter-edit pre-fills current name`() = runTest {
        val component = buildComponent(InMemoryDeviceNamePersistence("My Device"))
        advanceUntilIdle()

        component.onEditClick()

        val editing = assertIs<DeviceNameState.Editing>(component.state.value)
        assertEquals("My Device", editing.draft)
    }

    @Test
    fun `valid confirm returns to Display with new name`() = runTest {
        val component = buildComponent(InMemoryDeviceNamePersistence("Original"))
        advanceUntilIdle()

        component.onEditClick()
        component.onDraftChange("New Name")
        component.onConfirm()
        advanceUntilIdle()

        val display = assertIs<DeviceNameState.Display>(component.state.value)
        assertEquals("New Name", display.name)
    }

    @Test
    fun `whitespace-only draft shows empty-name error and does not mutate store`() = runTest {
        val persistence = InMemoryDeviceNamePersistence("Original")
        val component = buildComponent(persistence)
        advanceUntilIdle()
        val writesBefore = persistence.writes

        component.onEditClick()
        component.onDraftChange("   ")
        component.onConfirm()
        advanceUntilIdle()

        val editing = assertIs<DeviceNameState.Editing>(component.state.value)
        assertEquals("Enter a name.", editing.errorMessage)
        assertFalse(editing.confirmEnabled)
        assertEquals(writesBefore, persistence.writes)
    }

    @Test
    fun `draft over 50 codepoints shows too-long error and does not mutate store`() = runTest {
        val persistence = InMemoryDeviceNamePersistence("Original")
        val component = buildComponent(persistence)
        advanceUntilIdle()
        val writesBefore = persistence.writes

        component.onEditClick()
        component.onDraftChange("A".repeat(51))
        component.onConfirm()
        advanceUntilIdle()

        val editing = assertIs<DeviceNameState.Editing>(component.state.value)
        assertEquals("Use 50 characters or fewer.", editing.errorMessage)
        assertFalse(editing.confirmEnabled)
        assertEquals(writesBefore, persistence.writes)
    }

    @Test
    fun `storage write failure stays in Editing with save-failed message and draft intact`() = runTest {
        val component = buildComponent(
            InMemoryDeviceNamePersistence(stored = "Original", writeError = RuntimeException("disk full")),
        )
        advanceUntilIdle()

        component.onEditClick()
        component.onDraftChange("New Name")
        component.onConfirm()
        advanceUntilIdle()

        val editing = assertIs<DeviceNameState.Editing>(component.state.value)
        assertEquals("Couldn't save the name. Try again.", editing.errorMessage)
        assertEquals("New Name", editing.draft)
    }

    @Test
    fun `cancel after save failure restores committed name in Display`() = runTest {
        val component = buildComponent(
            InMemoryDeviceNamePersistence(stored = "Original", writeError = RuntimeException("disk full")),
        )
        advanceUntilIdle()

        component.onEditClick()
        component.onDraftChange("New Name")
        component.onConfirm()
        advanceUntilIdle()

        component.onCancel()

        val display = assertIs<DeviceNameState.Display>(component.state.value)
        assertEquals("Original", display.name)
    }

    @Test
    fun `cancel discards draft and returns to Display with committed name`() = runTest {
        val component = buildComponent(InMemoryDeviceNamePersistence("Original"))
        advanceUntilIdle()

        component.onEditClick()
        component.onDraftChange("Something Else")
        component.onCancel()

        val display = assertIs<DeviceNameState.Display>(component.state.value)
        assertEquals("Original", display.name)
    }

    @Test
    fun `entering edit mode sets null error and confirm enabled`() = runTest {
        val component = buildComponent(InMemoryDeviceNamePersistence("My Device"))
        advanceUntilIdle()

        component.onEditClick()

        val editing = assertIs<DeviceNameState.Editing>(component.state.value)
        assertNull(editing.errorMessage)
        assertTrue(editing.confirmEnabled)
    }

    @Test
    fun `exactly 50 codepoints confirms successfully`() = runTest {
        val component = buildComponent(InMemoryDeviceNamePersistence("Original"))
        advanceUntilIdle()

        component.onEditClick()
        component.onDraftChange("A".repeat(50))
        component.onConfirm()
        advanceUntilIdle()

        assertIs<DeviceNameState.Display>(component.state.value)
    }

    @Test
    fun `two-cycle confirm pre-fills second edit with new committed name`() = runTest {
        val component = buildComponent(InMemoryDeviceNamePersistence("Original"))
        advanceUntilIdle()

        component.onEditClick()
        component.onDraftChange("First Save")
        component.onConfirm()
        advanceUntilIdle()

        component.onEditClick()

        val editing = assertIs<DeviceNameState.Editing>(component.state.value)
        assertEquals("First Save", editing.draft)
    }

    @Test
    fun `onDraftChange to valid draft clears error and enables confirm`() = runTest {
        val component = buildComponent(InMemoryDeviceNamePersistence("Original"))
        advanceUntilIdle()

        component.onEditClick()
        component.onDraftChange("   ")
        component.onConfirm()
        advanceUntilIdle()

        val editingWithError = assertIs<DeviceNameState.Editing>(component.state.value)
        assertEquals("Enter a name.", editingWithError.errorMessage)

        component.onDraftChange("Valid Name")

        val editingClear = assertIs<DeviceNameState.Editing>(component.state.value)
        assertNull(editingClear.errorMessage)
        assertTrue(editingClear.confirmEnabled)
    }

    @Test
    fun `saveFailed keeps confirm enabled`() = runTest {
        val component = buildComponent(
            InMemoryDeviceNamePersistence(stored = "Original", writeError = RuntimeException("disk full")),
        )
        advanceUntilIdle()

        component.onEditClick()
        component.onDraftChange("New Name")
        component.onConfirm()
        advanceUntilIdle()

        val editing = assertIs<DeviceNameState.Editing>(component.state.value)
        assertEquals("Couldn't save the name. Try again.", editing.errorMessage)
        assertTrue(editing.confirmEnabled)
    }

    @Test
    fun `onDraftChange to valid draft clears saveFailed message`() = runTest {
        val component = buildComponent(
            InMemoryDeviceNamePersistence(stored = "Original", writeError = RuntimeException("disk full")),
        )
        advanceUntilIdle()

        component.onEditClick()
        component.onDraftChange("New Name")
        component.onConfirm()
        advanceUntilIdle()

        component.onDraftChange("Another Name")

        val editing = assertIs<DeviceNameState.Editing>(component.state.value)
        assertNull(editing.errorMessage)
        assertTrue(editing.confirmEnabled)
    }

    @Test
    fun `onDraftChange to invalid draft clears saveFailed message and shows validation error`() = runTest {
        val component = buildComponent(
            InMemoryDeviceNamePersistence(stored = "Original", writeError = RuntimeException("disk full")),
        )
        advanceUntilIdle()

        component.onEditClick()
        component.onDraftChange("New Name")
        component.onConfirm()
        advanceUntilIdle()

        val afterSaveFailed = assertIs<DeviceNameState.Editing>(component.state.value)
        assertEquals("Couldn't save the name. Try again.", afterSaveFailed.errorMessage)

        component.onDraftChange("   ")

        val editing = assertIs<DeviceNameState.Editing>(component.state.value)
        assertEquals("Enter a name.", editing.errorMessage)
        assertFalse(editing.confirmEnabled)
    }

    @Test
    fun `onDraftChange to too-long draft clears saveFailed message and shows too-long error`() = runTest {
        val component = buildComponent(
            InMemoryDeviceNamePersistence(stored = "Original", writeError = RuntimeException("disk full")),
        )
        advanceUntilIdle()

        component.onEditClick()
        component.onDraftChange("New Name")
        component.onConfirm()
        advanceUntilIdle()

        val afterSaveFailed = assertIs<DeviceNameState.Editing>(component.state.value)
        assertEquals("Couldn't save the name. Try again.", afterSaveFailed.errorMessage)

        component.onDraftChange("A".repeat(51))

        val editing = assertIs<DeviceNameState.Editing>(component.state.value)
        assertEquals("Use 50 characters or fewer.", editing.errorMessage)
        assertFalse(editing.confirmEnabled)
    }
}
