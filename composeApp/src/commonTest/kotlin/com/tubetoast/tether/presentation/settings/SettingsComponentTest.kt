package com.tubetoast.tether.presentation.settings

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.tubetoast.tether.preferences.FakeFileTransferPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsComponentTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildComponent(
        onBack: () -> Unit = {},
    ): SettingsComponent {
        val lifecycle = LifecycleRegistry().also { it.resume() }
        return SettingsComponent(
            componentContext = DefaultComponentContext(lifecycle),
            fileTransferComponentFactory = { ftCtx ->
                FileTransferSettingsComponent(
                    componentContext = ftCtx,
                    preferences = FakeFileTransferPreferences(),
                )
            },
            onBack = onBack,
        )
    }

    @Test
    fun `fileTransfer is constructed`() {
        val component = buildComponent()
        assertNotNull(component.fileTransfer)
    }

    @Test
    fun `onBack invokes the passed lambda`() {
        var called = false
        val component = buildComponent(onBack = { called = true })

        component.onBack()

        assertTrue(called)
    }
}
