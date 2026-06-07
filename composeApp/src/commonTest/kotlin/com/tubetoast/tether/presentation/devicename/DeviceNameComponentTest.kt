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
import kotlin.test.assertIs
import kotlin.test.assertNull

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
    fun `whitespace-only draft sets EmptyName error and does not mutate store`() = runTest {
        val persistence = InMemoryDeviceNamePersistence("Original")
        val component = buildComponent(persistence)
        advanceUntilIdle()
        val writesBefore = persistence.writes

        component.onEditClick()
        component.onDraftChange("   ")
        component.onConfirm()
        advanceUntilIdle()

        val editing = assertIs<DeviceNameState.Editing>(component.state.value)
        assertEquals(DeviceNameError.EmptyName, editing.error)
        assertEquals(writesBefore, persistence.writes)
    }

    @Test
    fun `draft over 50 codepoints shows TooLong error and does not mutate store`() = runTest {
        val persistence = InMemoryDeviceNamePersistence("Original")
        val component = buildComponent(persistence)
        advanceUntilIdle()
        val writesBefore = persistence.writes

        component.onEditClick()
        component.onDraftChange("A".repeat(51))
        component.onConfirm()
        advanceUntilIdle()

        val editing = assertIs<DeviceNameState.Editing>(component.state.value)
        assertEquals(DeviceNameError.TooLong, editing.error)
        assertEquals(writesBefore, persistence.writes)
    }

    @Test
    fun `storage write failure stays in Editing with SaveFailed and draft intact`() = runTest {
        val component = buildComponent(
            InMemoryDeviceNamePersistence(stored = "Original", writeError = RuntimeException("disk full")),
        )
        advanceUntilIdle()

        component.onEditClick()
        component.onDraftChange("New Name")
        component.onConfirm()
        advanceUntilIdle()

        val editing = assertIs<DeviceNameState.Editing>(component.state.value)
        assertEquals(DeviceNameError.SaveFailed, editing.error)
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
    fun `entering edit mode sets null error before any confirm attempt`() = runTest {
        val component = buildComponent(InMemoryDeviceNamePersistence("My Device"))
        advanceUntilIdle()

        component.onEditClick()

        val editing = assertIs<DeviceNameState.Editing>(component.state.value)
        assertNull(editing.error)
    }
}
