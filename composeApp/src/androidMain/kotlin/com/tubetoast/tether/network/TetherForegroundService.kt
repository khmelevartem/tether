package com.tubetoast.tether.network

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.tubetoast.tether.R
import com.tubetoast.tether.di.AppContainerProvider
import com.tubetoast.tether.discovery.MdnsDiscovery
import com.tubetoast.tether.protocol.PeerIdentity
import com.tubetoast.tether.transfer.ReceiveEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.error
import ru.pocketbyte.kydra.log.info
import ru.pocketbyte.kydra.log.warn
import ru.pocketbyte.kydra.log.withMessage
import ru.pocketbyte.kydra.log.wrapper.withTag

private const val NOTIFICATION_ID = 1001
private val log = KydraLog.withTag(default = "FGService")
private const val CHANNEL_ID = "tether_foreground"
internal const val ACTION_STOP = "com.tubetoast.tether.action.STOP"

class TetherForegroundService : LifecycleService() {
    // The "running" prefix marks these as the started, attached instances —
    // distinct from the "candidate" locals fetched from the container before start().
    // @Volatile: written from IO coroutine (lifecycleScope.launch(Dispatchers.IO)),
    // read from main thread in onStartCommand / onDestroy.
    @Volatile private var runningFileServer: FileServer? = null

    @Volatile private var runningMdnsDiscovery: MdnsDiscovery? = null

    // Binder returned from onBind so MainActivity can check if the service is running
    // via bindService(intent, connection, flags=0) without BIND_AUTO_CREATE.
    inner class LocalBinder : Binder()

    override fun onCreate() {
        super.onCreate()
        if (!startForegroundCompat()) return

        val container = (application as AppContainerProvider).container
        val fileServer = container.fileServer
        val mdnsDiscovery = container.mdnsDiscovery

        lifecycleScope.launch(Dispatchers.IO) {
            container.nameStore.init()
            val deviceName = container.nameStore.name.first()

            val port = try {
                fileServer.start()
            } catch (e: CancellationException) {
                // Coroutine cancelled (e.g. user pressed Stop before server finished starting).
                // Ensure the partially-started engine doesn't leak.
                runCatching { fileServer.stop() }
                throw e
            } catch (e: Exception) {
                log.error { e withMessage "FileServer failed to start" }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@launch
            }
            runningFileServer = fileServer
            log.info { "FileServer started on port $port" }

            try {
                mdnsDiscovery.start(deviceName, port)
                container.nameRepublisher.start(lifecycleScope)
                container.rendezvousAnnouncer.start(lifecycleScope)
                runningMdnsDiscovery = mdnsDiscovery
                log.info { "mDNS started: name=$deviceName port=$port" }
            } catch (e: Exception) {
                log.error { e withMessage "mDNS failed to start" }
                fileServer.stop()
                runningFileServer = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@launch
            }

            launch {
                container.inboundEventRouter.receiveEvents.collect { (peer, event) ->
                    handleInboundNotification(peer, event)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            log.info { "Stop requested via notification action" }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return LocalBinder()
    }

    override fun onDestroy() {
        val container = (application as AppContainerProvider).container
        container.transferActivityTracker.releaseAll()
        container.nameRepublisher.stop()
        container.rendezvousAnnouncer.stop()
        runningMdnsDiscovery?.let { mdnsDiscovery ->
            try {
                // lifecycleScope is already cancelled by onDestroy; runBlocking for brief teardown.
                runBlocking { mdnsDiscovery.stop() }
                log.info { "mDNS stopped" }
            } catch (e: Exception) {
                log.warn { "mDNS stop failed: ${e.message}" }
            }
        }
        runningFileServer?.let { fileServer ->
            try {
                fileServer.stop()
                log.info { "FileServer stopped" }
            } catch (e: Exception) {
                log.warn { "FileServer stop failed: ${e.message}" }
            }
        }
        runningMdnsDiscovery = null
        runningFileServer = null
        super.onDestroy()
    }

    /** On false the OS refused FGS promotion; caller must not proceed with server start. */
    private fun startForegroundCompat(): Boolean {
        ensureChannel()
        val notification = buildNotification()
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> startForegroundS(notification)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                true
            }
            else -> {
                startForeground(NOTIFICATION_ID, notification)
                true
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun startForegroundS(notification: Notification): Boolean = try {
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        true
    } catch (e: ForegroundServiceStartNotAllowedException) {
        log.warn {
            "dataSync FGS quota exhausted; aborting start. OS auto-restarts inside the cap window will hit this same path; FGS resumes only after user foregrounds the app."
        }
        stopSelf()
        false
    }

    private fun handleInboundNotification(peer: PeerIdentity, event: ReceiveEvent) {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val text = when (event) {
            is ReceiveEvent.Started -> getString(
                R.string.fg_notification_receiving,
                event.currentFile.ifEmpty { "…" },
                peer.id,
            )
            is ReceiveEvent.Progress -> {
                val pct = event.totalBytes?.let { " (${(event.receivedBytes * 100 / it).toInt()}%)" }.orEmpty()
                getString(R.string.fg_notification_receiving, "${event.name}$pct", peer.id)
            }
            is ReceiveEvent.BatchCompleted -> getString(
                R.string.fg_notification_received,
                event.received,
                peer.id,
            )
            else -> return
        }
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(contentText: String? = null): Notification {
        val stopIntent = Intent(this, TetherForegroundService::class.java).setAction(ACTION_STOP)
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.fg_notification_title))
            .setContentText(contentText ?: getString(R.string.fg_notification_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.fg_notification_stop_action),
                stopPendingIntent,
            ).build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.fg_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        nm.createNotificationChannel(channel)
    }
}
