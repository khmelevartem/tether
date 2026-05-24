package com.tubetoast.tether

import androidx.compose.runtime.getValue
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.LocalSystemTheme
import androidx.compose.ui.SystemTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class, InternalComposeUiApi::class)
class ObservedSystemThemeTest {
    @Test
    fun observedThemeFlipsWhenProbeChanges() = runComposeUiTest {
        val probeSource = AtomicReference(SystemTheme.Light)
        val observed = AtomicReference<SystemTheme?>(null)

        setContent {
            ObservedSystemTheme(probe = probeSource::get, pollIntervalMs = 10L) {
                val theme by androidx.compose.runtime.rememberUpdatedState(LocalSystemTheme.current)
                androidx.compose.runtime.SideEffect { observed.set(theme) }
            }
        }

        waitForIdle()
        assertEquals(SystemTheme.Light, observed.get(), "initial theme should match probe")

        probeSource.set(SystemTheme.Dark)
        waitUntil(timeoutMillis = 1_000L) { observed.get() == SystemTheme.Dark }
        assertEquals(SystemTheme.Dark, observed.get(), "theme must flip after probe change")

        probeSource.set(SystemTheme.Light)
        waitUntil(timeoutMillis = 1_000L) { observed.get() == SystemTheme.Light }
        assertEquals(SystemTheme.Light, observed.get(), "theme must flip back")
    }
}
