package com.tubetoast.tether.network

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager

class AndroidTransferLockHolder(
    context: Context,
) {
    private val wifiLock: WifiManager.WifiLock = createWifiLock(context)

    private val wakeLock: PowerManager.WakeLock =
        (context.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tether:transfer")
            .also { it.setReferenceCounted(false) }

    fun acquire() {
        if (!wifiLock.isHeld) wifiLock.acquire()
        // Transfer duration is bounded by the tracker (releaseAll on service onDestroy + per-transfer
        // refcount). A WakelockTimeout would force a wrong upper bound on streaming transfers.
        if (!wakeLock.isHeld) {
            @SuppressLint("WakelockTimeout")
            wakeLock.acquire()
        }
    }

    fun release() {
        if (wakeLock.isHeld) wakeLock.release()
        if (wifiLock.isHeld) wifiLock.release()
    }

    internal val isWifiLockHeld: Boolean get() = wifiLock.isHeld
    internal val isWakeLockHeld: Boolean get() = wakeLock.isHeld

    private fun createWifiLock(context: Context): WifiManager.WifiLock {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val mode =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                @Suppress("DEPRECATION")
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
        return wifi.createWifiLock(mode, "tether:transfer").also { it.setReferenceCounted(false) }
    }
}
