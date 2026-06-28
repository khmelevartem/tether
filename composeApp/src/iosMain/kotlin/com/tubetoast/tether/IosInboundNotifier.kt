package com.tubetoast.tether

import com.tubetoast.tether.transfer.InboundEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter

internal class IosInboundNotifier(
    private val events: SharedFlow<InboundEvent>,
    private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch {
            events.collect { event ->
                if (event is InboundEvent.ConnectionLost && event.cancelled.not()) {
                    scheduleLocalNotification(event.receivedSoFar)
                }
            }
        }
    }

    private fun scheduleLocalNotification(receivedCount: Int) {
        UNUserNotificationCenter
            .currentNotificationCenter()
            .getNotificationSettingsWithCompletionHandler { settings ->
                if (settings == null) return@getNotificationSettingsWithCompletionHandler
                val content = UNMutableNotificationContent()
                content.setTitle("Tether")
                content.setBody("Received $receivedCount file(s)")
                val request = UNNotificationRequest.requestWithIdentifier(
                    identifier = "tether.batch.received",
                    content = content,
                    trigger = null,
                )
                UNUserNotificationCenter
                    .currentNotificationCenter()
                    .addNotificationRequest(request) { _ -> }
            }
    }
}
