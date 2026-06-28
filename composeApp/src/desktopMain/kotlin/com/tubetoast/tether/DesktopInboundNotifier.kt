package com.tubetoast.tether

import com.tubetoast.tether.transfer.InboundEvent
import com.tubetoast.tether.transfer.WindowHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.warn
import ru.pocketbyte.kydra.log.wrapper.withTag
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage

private val log = KydraLog.withTag(default = "DesktopInboundNotifier")

internal class DesktopInboundNotifier(
    private val windowHolder: WindowHolder,
    private val events: SharedFlow<InboundEvent>,
    private val scope: CoroutineScope,
) {
    private val trayIcon: TrayIcon? by lazy { tryInstallTrayIcon() }

    fun start() {
        scope.launch {
            events.collect { event ->
                when (event) {
                    is InboundEvent.FileStarted -> bringWindowToFront()
                    is InboundEvent.ConnectionLost -> showTrayNotification(event.receivedSoFar)
                    else -> Unit
                }
            }
        }
    }

    private fun bringWindowToFront() {
        val window = windowHolder.window ?: return
        if (!window.isFocused) {
            window.toFront()
            window.requestFocus()
        }
    }

    private fun showTrayNotification(receivedCount: Int) {
        val icon = trayIcon ?: return
        icon.displayMessage(
            "Tether",
            "Received $receivedCount file(s)",
            TrayIcon.MessageType.INFO,
        )
    }

    private fun tryInstallTrayIcon(): TrayIcon? {
        if (!SystemTray.isSupported()) return null
        return try {
            val tray = SystemTray.getSystemTray()
            // 1x1 transparent image — Tether's real icon isn't accessible as AWT Image here.
            val img = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
            val icon = TrayIcon(img, "Tether")
            tray.add(icon)
            icon
        } catch (e: Exception) {
            log.warn { "system tray unavailable: ${e.message}" }
            null
        }
    }
}
