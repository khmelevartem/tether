package com.tubetoast.tether

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.LocalSystemTheme
import androidx.compose.ui.SystemTheme
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.tubetoast.tether.di.DefaultDesktopAppConfig
import com.tubetoast.tether.di.DesktopAppContainer
import com.tubetoast.tether.logging.initTetherLogging
import com.tubetoast.tether.logging.isDebugEnabled
import com.tubetoast.tether.presentation.RootContent
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.skiko.currentSystemTheme
import tether.composeapp.generated.resources.Res
import tether.composeapp.generated.resources.icon
import org.jetbrains.skiko.SystemTheme as SkikoSystemTheme

@OptIn(InternalComposeUiApi::class)
fun main() = runBlocking {
    // macOS: tell the JVM/AWT to follow system appearance so the native title bar tracks
    // light/dark mode. Must be set before any Swing/AWT class loads.
    System.setProperty("apple.awt.application.appearance", "system")
    initTetherLogging(debugEnabled = isDebugEnabled())
    val container = DesktopAppContainer(
        DefaultDesktopAppConfig(port = 0),
    )
    container.nameStore.init()
    val handle = container.startBackendOrFail()
    registerShutdownHook(handle)

    val lifecycle = LifecycleRegistry()
    val component = container.rootComponentFactory.create(DefaultComponentContext(lifecycle))
    lifecycle.resume()

    application {
        // Compose Multiplatform 1.10.3: LocalSystemTheme on Desktop is a staticCompositionLocal
        // captured at startup and never updated. Poll skiko so dark-mode toggles propagate live.
        val systemTheme by produceState(currentSystemTheme.asComposeSystemTheme()) {
            while (true) {
                delay(SYSTEM_THEME_POLL_INTERVAL_MS)
                val next = currentSystemTheme.asComposeSystemTheme()
                if (next != value) value = next
            }
        }
        CompositionLocalProvider(LocalSystemTheme provides systemTheme) {
            Window(
                onCloseRequest = {
                    lifecycle.destroy()
                    exitApplication()
                },
                title = "Tether",
                icon = painterResource(Res.drawable.icon),
            ) {
                RootContent(component)
            }
        }
    }
}

private const val SYSTEM_THEME_POLL_INTERVAL_MS = 500L

@OptIn(InternalComposeUiApi::class)
private fun SkikoSystemTheme.asComposeSystemTheme(): SystemTheme = when (this) {
    SkikoSystemTheme.DARK -> SystemTheme.Dark
    SkikoSystemTheme.LIGHT -> SystemTheme.Light
    SkikoSystemTheme.UNKNOWN -> SystemTheme.Unknown
}
