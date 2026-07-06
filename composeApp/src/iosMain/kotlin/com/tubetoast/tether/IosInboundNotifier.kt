package com.tubetoast.tether

import com.tubetoast.tether.protocol.PeerIdentity
import com.tubetoast.tether.transfer.ReceiveEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter

internal class IosInboundNotifier(
    private val receiveEvents: SharedFlow<Pair<PeerIdentity, ReceiveEvent>>,
    private val scope: CoroutineScope,
) {
    init {
        scope.launch {
            receiveEvents.collect { (_, event) ->
                if (event is ReceiveEvent.BatchCompleted) {
                    scheduleLocalNotification(event.received)
                }
            }
        }
    }

    private fun scheduleLocalNotification(receivedCount: Int) {
        UNUserNotificationCenter
            .currentNotificationCenter()
            .getNotificationSettingsWithCompletionHandler { settings ->
                if (settings == null) return@getNotificationSettingsWithCompletionHandler
                val status = settings.authorizationStatus
                if (status != UNAuthorizationStatusAuthorized &&
                    status != UNAuthorizationStatusProvisional
                ) {
                    return@getNotificationSettingsWithCompletionHandler
                }
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
