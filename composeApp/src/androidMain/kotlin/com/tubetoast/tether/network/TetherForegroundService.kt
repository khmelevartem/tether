package com.tubetoast.tether.network

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.tubetoast.tether.R
import com.tubetoast.tether.discovery.MdnsDiscovery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "TetherFGService"
private const val NOTIFICATION_ID = 1001
private const val CHANNEL_ID = "tether_foreground"
internal const val ACTION_STOP = "com.tubetoast.tether.action.STOP"

class TetherForegroundService : LifecycleService() {
    private var server: FileServer? = null
    private var discovery: MdnsDiscovery? = null

    // Binder returned from onBind so MainActivity can check if the service is running
    // via bindService(intent, connection, flags=0) without BIND_AUTO_CREATE.
    inner class LocalBinder : Binder()

    override fun onCreate() {
        super.onCreate()
        startForegroundCompat()

        lifecycleScope.launch(Dispatchers.IO) {
            val downloadsDir = (getExternalFilesDir(null) ?: filesDir).resolve("Tether")
            val srv = FileServer(port = 0, downloadsDir = downloadsDir)
            val port = try {
                srv.start()
            } catch (e: Exception) {
                Log.e(TAG, "FileServer failed to start: ${e.message}", e)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@launch
            }
            server = srv
            Log.i(TAG, "FileServer started on port $port, downloads → ${downloadsDir.absolutePath}")

            val deviceName = "Tether-${Build.MODEL}"
            val disc = MdnsDiscovery()
            try {
                disc.start(deviceName, port)
                discovery = disc
                Log.i(TAG, "mDNS started: name=$deviceName port=$port")
            } catch (e: Exception) {
                Log.e(TAG, "mDNS failed to start: ${e.message}", e)
                srv.stop()
                server = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            Log.i(TAG, "Stop requested via notification action")
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
        try {
            discovery?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "mDNS stop failed: ${e.message}")
        }
        try {
            server?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "FileServer stop failed: ${e.message}")
        }
        discovery = null
        server = null
        super.onDestroy()
    }

    private fun startForegroundCompat() {
        ensureChannel()
        val stopIntent = Intent(this, TetherForegroundService::class.java).setAction(ACTION_STOP)
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification: Notification = NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.fg_notification_title))
            .setContentText(getString(R.string.fg_notification_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.fg_notification_stop_action),
                stopPendingIntent,
            ).build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
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
