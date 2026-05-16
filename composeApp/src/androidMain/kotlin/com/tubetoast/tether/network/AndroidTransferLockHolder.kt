package com.tubetoast.tether.network

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager

class AndroidTransferLockHolder(
    context: Context,
) {
    // HIGH_PERF on API 24-28 is the effective protection for screen-off transfers; on API 29+
    // the OS silently substitutes LOW_LATENCY (screen-on/foreground only), so the WifiLock
    // is a no-op on Android 10+. WakeLock below still works on all versions. See knowledge doc.
    @Suppress("DEPRECATION")
    private val wifiLock: WifiManager.WifiLock =
        (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
            .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "tether:transfer")
            .also { it.setReferenceCounted(false) }

    private val wakeLock: PowerManager.WakeLock =
        (context.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tether:transfer")
            .also { it.setReferenceCounted(false) }

    fun acquire() {
        if (!wifiLock.isHeld) wifiLock.acquire()
        // Timeout would bound streaming transfers arbitrarily; release is guaranteed by the tracker.
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
}
