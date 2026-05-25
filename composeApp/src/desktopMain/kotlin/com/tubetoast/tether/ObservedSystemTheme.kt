package com.tubetoast.tether

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.LocalSystemTheme
import androidx.compose.ui.SystemTheme
import kotlinx.coroutines.delay
import org.jetbrains.skiko.currentSystemTheme
import org.jetbrains.skiko.SystemTheme as SkikoSystemTheme

internal const val SYSTEM_THEME_POLL_INTERVAL_MS = 500L

@OptIn(InternalComposeUiApi::class)
@Composable
internal fun ObservedSystemTheme(
    probe: () -> SystemTheme = { currentSystemTheme.asComposeSystemTheme() },
    pollIntervalMs: Long = SYSTEM_THEME_POLL_INTERVAL_MS,
    content: @Composable () -> Unit,
) {
    val currentProbe by rememberUpdatedState(probe)
    val systemTheme by produceState(probe()) {
        while (true) {
            delay(pollIntervalMs)
            val next = currentProbe()
            if (next != value) value = next
        }
    }
    CompositionLocalProvider(LocalSystemTheme provides systemTheme, content = content)
}

@OptIn(InternalComposeUiApi::class)
internal fun SkikoSystemTheme.asComposeSystemTheme(): SystemTheme = when (this) {
    SkikoSystemTheme.DARK -> SystemTheme.Dark
    SkikoSystemTheme.LIGHT -> SystemTheme.Light
    SkikoSystemTheme.UNKNOWN -> SystemTheme.Unknown
}
